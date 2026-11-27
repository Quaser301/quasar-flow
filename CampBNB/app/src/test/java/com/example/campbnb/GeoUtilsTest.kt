package com.example.campbnb

import com.example.campbnb.models.Campsite
import com.example.campbnb.models.LatLng
import com.example.campbnb.models.MapPin
import com.example.campbnb.models.campsitesToMapPins
import com.example.campbnb.models.distanceKm
import com.example.campbnb.models.featureIdFromJson
import com.example.campbnb.models.formatLatLng
import com.example.campbnb.models.parseLatLng
import com.example.campbnb.models.pinsWithinRadius
import com.example.campbnb.models.zoomForRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the pure map helpers behind the campsite map view (WOW factor). */
class GeoUtilsTest {

    private val edmonton = LatLng(53.5461, -113.4938)
    private val calgary = LatLng(51.0447, -114.0719)

    @Test
    fun test_parse_reads_latitude_and_longitude() {
        assertEquals(LatLng(53.41, -117.58), parseLatLng("53.41, -117.58"))
        assertEquals(LatLng(53.41, -117.58), parseLatLng("53.41,-117.58"))
    }

    @Test
    fun test_parse_rejects_text_and_out_of_range_values() {
        assertNull(parseLatLng("Banff"))
        assertNull(parseLatLng("91.0, 10.0"))
        assertNull(parseLatLng("10.0, 181.0"))
        assertNull(parseLatLng("NaN, 5"))
        assertNull(parseLatLng("1, 2, 3"))
        assertNull(parseLatLng(""))
    }

    @Test
    fun test_format_uses_a_dot_decimal_and_parses_back() {
        val text = formatLatLng(LatLng(53.41, -117.58))
        assertEquals("53.41000, -117.58000", text)
        assertEquals(LatLng(53.41, -117.58), parseLatLng(text))
    }

    @Test
    fun test_distance_edmonton_to_calgary_is_about_281_km() {
        assertEquals(281.0, distanceKm(edmonton, calgary), 5.0)
    }

    @Test
    fun test_radius_keeps_only_pins_inside_it() {
        val near = MapPin("near", "Near", 53.55, -113.50)
        val far = MapPin("far", "Far", calgary.latitude, calgary.longitude)
        val pins = listOf(near, far)

        assertEquals(listOf(near), pinsWithinRadius(pins, edmonton, 50.0))
        assertEquals(pins, pinsWithinRadius(pins, edmonton, 300.0))
    }

    @Test
    fun test_only_campsites_with_a_readable_location_get_a_pin() {
        val good = Campsite(campName = "Good", geoLocation = "53.4, -117.5").also { it.id = "good" }
        val junk = Campsite(campName = "Junk", geoLocation = "Banff").also { it.id = "junk" }

        val pins = campsitesToMapPins(listOf(good, junk))

        assertEquals(1, pins.size)
        assertEquals("good", pins[0].id)
    }

    @Test
    fun test_feature_id_is_read_from_feature_json() {
        val json = "{\"type\":\"Feature\",\"properties\":{\"id\":\"abc\"},\"geometry\":null}"
        assertEquals("abc", featureIdFromJson(json))
        assertNull(featureIdFromJson("not json"))
    }

    @Test
    fun test_a_bigger_radius_zooms_further_out() {
        assertTrue(zoomForRadius(53.5, 100.0) < zoomForRadius(53.5, 10.0))
    }
}