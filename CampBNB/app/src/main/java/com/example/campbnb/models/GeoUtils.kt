package com.example.campbnb.models

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// implemented with assistance from Claude Sonnet 5.5

/*
 * Pure map helpers (WOW factor: campsite map view). Nothing in this file touches Android or
 * the map library, so all of it is covered by plain JVM unit tests (GeoUtilsTest).
 *
 * Campsite.geoLocation stays a String holding "latitude, longitude"; parseLatLng reads it.
 * Anything that does not parse (older free-text listings) simply gets no pin.
 */

/** A point on the Earth in degrees. */
data class LatLng(val latitude: Double, val longitude: Double)

/** One campsite pin on the map. */
data class MapPin(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/** Where the map starts when the device location is unknown (Edmonton). */
val DEFAULT_MAP_CENTER = LatLng(53.5461, -113.4938)

/** GeoJSON with nothing in it. */
const val EMPTY_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"

private const val EARTH_RADIUS_KM = 6371.0088

/**
 * Reads "latitude, longitude" text. Returns null if it is not two valid numbers in range.
 */
fun parseLatLng(text: String): LatLng? {
    val parts = text.split(",")
    if (parts.size != 2) {
        return null
    }
    val latitude = parts[0].trim().toDoubleOrNull()
    val longitude = parts[1].trim().toDoubleOrNull()
    if (latitude == null || longitude == null) {
        return null
    }
    if (latitude.isNaN() || longitude.isNaN()) {
        return null
    }
    if (latitude < -90.0 || latitude > 90.0) {
        return null
    }
    if (longitude < -180.0 || longitude > 180.0) {
        return null
    }
    return LatLng(latitude, longitude)
}

/** Writes a point as "latitude, longitude" with a dot decimal, whatever the device language. */
fun formatLatLng(point: LatLng): String {
    return String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
}

/** Turns every campsite whose geoLocation parses into a map pin. */
fun campsitesToMapPins(campsites: List<Campsite>): List<MapPin> {
    val pins = mutableListOf<MapPin>()
    for (campsite in campsites) {
        val spot = parseLatLng(campsite.geoLocation)
        if (spot != null) {
            pins.add(MapPin(campsite.id, campsite.campName, spot.latitude, spot.longitude))
        }
    }
    return pins
}

/** The pin with this id, or null. */
fun findPin(pins: List<MapPin>, id: String?): MapPin? {
    if (id == null) {
        return null
    }
    for (pin in pins) {
        if (pin.id == id) {
            return pin
        }
    }
    return null
}

/** True when the list holds a pin with this id. */
fun hasPin(pins: List<MapPin>, id: String): Boolean {
    return findPin(pins, id) != null
}

/** Straight-line (great-circle) distance between two points, in kilometres. */
fun distanceKm(from: LatLng, to: LatLng): Double {
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLng = Math.toRadians(to.longitude - from.longitude)
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
}

/** The pins that are no further than [radiusKm] from [center]. */
fun pinsWithinRadius(pins: List<MapPin>, center: LatLng, radiusKm: Double): List<MapPin> {
    val inside = mutableListOf<MapPin>()
    for (pin in pins) {
        val away = distanceKm(center, LatLng(pin.latitude, pin.longitude))
        if (away <= radiusKm) {
            inside.add(pin)
        }
    }
    return inside
}

/** A zoom level at which a circle of [radiusKm] around [latitude] fits a phone screen. */
fun zoomForRadius(latitude: Double, radiusKm: Double): Double {
    val diameterMeters = radiusKm * 2000.0 * 1.25
    val metersPerDp = diameterMeters / 320.0
    val worldMetersPerDp = 78271.52 * cos(Math.toRadians(latitude))
    var zoom = ln(worldMetersPerDp / metersPerDp) / ln(2.0)
    if (zoom < 3.0) {
        zoom = 3.0
    }
    if (zoom > 16.0) {
        zoom = 16.0
    }
    return zoom
}

private fun jsonEscape(text: String): String {
    return text.replace("\\", "\\\\").replace("\"", "\\\"")
}

/** GeoJSON points, each carrying its campsite id as the "id" property. */
fun pinsToGeoJson(pins: List<MapPin>): String {
    val text = StringBuilder()
    text.append("{\"type\":\"FeatureCollection\",\"features\":[")
    for (index in pins.indices) {
        val pin = pins[index]
        if (index > 0) {
            text.append(",")
        }
        text.append("{\"type\":\"Feature\",\"properties\":{\"id\":\"")
        text.append(jsonEscape(pin.id))
        text.append("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
        text.append(pin.longitude)
        text.append(",")
        text.append(pin.latitude)
        text.append("]}}")
    }
    text.append("]}")
    return text.toString()
}

/** GeoJSON holding one point, or nothing when [point] is null. */
fun pointToGeoJson(point: LatLng?): String {
    if (point == null) {
        return EMPTY_GEOJSON
    }
    return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[" +
            point.longitude + "," + point.latitude + "]}}]}"
}

/** GeoJSON polygon approximating a circle of [radiusKm] around [center]. */
fun circleToGeoJson(center: LatLng, radiusKm: Double): String {
    val steps = 64
    val angularDistance = radiusKm / EARTH_RADIUS_KM
    val lat1 = Math.toRadians(center.latitude)
    val lng1 = Math.toRadians(center.longitude)
    val ring = StringBuilder()
    for (step in 0..steps) {
        // the last point reuses bearing 0 so the ring closes exactly
        val bearing = 2.0 * Math.PI * (step % steps) / steps
        val lat2 = asin(
            sin(lat1) * cos(angularDistance) +
                    cos(lat1) * sin(angularDistance) * cos(bearing)
        )
        val lng2 = lng1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )
        if (step > 0) {
            ring.append(",")
        }
        ring.append("[")
        ring.append(Math.toDegrees(lng2))
        ring.append(",")
        ring.append(Math.toDegrees(lat2))
        ring.append("]")
    }
    return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[" +
            ring + "]]}}]}"
}

/** Reads the "id" property out of one GeoJSON feature's text, or null. */
fun featureIdFromJson(json: String): String? {
    try {
        val root = Json.parseToJsonElement(json).jsonObject
        val properties = root["properties"]?.jsonObject
        return properties?.get("id")?.jsonPrimitive?.content
    } catch (error: Exception) {
        return null
    }
}

/** "850 m", "4.3 km" or "128 km". */
fun formatDistance(km: Double): String {
    if (km < 1.0) {
        return String.format(Locale.US, "%d m", Math.round(km * 1000.0))
    }
    if (km < 10.0) {
        return String.format(Locale.US, "%.1f km", km)
    }
    return String.format(Locale.US, "%d km", Math.round(km))
}

/** GeoJSON line between two points, or nothing when either is null. */
fun lineToGeoJson(from: LatLng?, to: LatLng?): String {
    if (from == null || to == null) {
        return EMPTY_GEOJSON
    }
    return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[" +
            from.longitude + "," + from.latitude + "],[" +
            to.longitude + "," + to.latitude + "]]}}]}"
}