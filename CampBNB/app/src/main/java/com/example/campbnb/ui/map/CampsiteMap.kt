package com.example.campbnb.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.campbnb.models.DEFAULT_MAP_CENTER
import com.example.campbnb.models.EMPTY_GEOJSON
import com.example.campbnb.models.LatLng
import com.example.campbnb.models.MapPin
import com.example.campbnb.models.circleToGeoJson
import com.example.campbnb.models.distanceKm
import com.example.campbnb.models.findPin
import com.example.campbnb.models.formatDistance
import com.example.campbnb.models.formatLatLng
import com.example.campbnb.models.lineToGeoJson
import com.example.campbnb.models.pinsToGeoJson
import com.example.campbnb.models.pinsWithinRadius
import com.example.campbnb.models.pointToGeoJson
import com.example.campbnb.models.zoomForRadius
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/*
 * The campsite map (WOW factor). OpenFreeMap vector tiles drawn by MapLibre: no API key.
 * Map data (c) OpenStreetMap contributors; the map shows its own attribution.
 *
 * Three composables:
 *  - PinMap: the shared map. Draws a radius ring, a line to the selected pin, campsite pins,
 *    one highlighted pin and the user's dot, with zoom buttons. Reports pin and map taps.
 *  - CampsiteMapScreen: the screen used from Explore (radius + pins near me) and from the
 *    details page (one campsite highlighted among the others).
 *  - LocationPickerScreen: lets an owner drop a pin when registering a campsite.
 */

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private val PinGreen = Color(0xFF2E7D32)
private val PinHighlight = Color(0xFFE65100)
private val UserBlue = Color(0xFF1E88E5)

private val RADIUS_CHOICES_KM = listOf(10, 25, 50, 100, 250)

/** The GeoJSON text of every layer, rebuilt whenever the inputs change. */
private data class MapJson(
    val ring: String,
    val link: String,
    val pins: String,
    val highlight: String,
    val user: String
)

/**
 * The shared map. The camera glides to [cameraCenter] / [cameraZoom] whenever they change;
 * otherwise the user pans and zooms freely (gestures or the + / - buttons).
 *
 * @param pins The campsite pins to draw (green).
 * @param highlightedId The pin drawn larger and orange, or null.
 * @param userLocation The blue "you are here" dot, or null.
 * @param ringCenter Centre of the radius ring, or null for no ring.
 * @param ringKm Radius of the ring in kilometres.
 * @param linkTo If set, a line is drawn from [ringCenter] to this point.
 * @param onPinClick Called with the campsite id of a tapped pin.
 * @param onMapTap Called with the tapped point; return true if the tap was used.
 */
@Composable
fun PinMap(
    cameraCenter: LatLng,
    cameraZoom: Double,
    pins: List<MapPin>,
    highlightedId: String?,
    userLocation: LatLng?,
    ringCenter: LatLng?,
    ringKm: Double,
    onPinClick: (String) -> Unit,
    onMapTap: (LatLng) -> Boolean,
    modifier: Modifier = Modifier,
    linkTo: LatLng? = null
) {
    var ringJson = EMPTY_GEOJSON
    if (ringCenter != null) {
        ringJson = circleToGeoJson(ringCenter, ringKm)
    }
    val highlightPin = findPin(pins, highlightedId)
    var highlightList = emptyList<MapPin>()
    if (highlightPin != null) {
        highlightList = listOf(highlightPin)
    }

    // State objects, so the map's layers always read the newest values.
    val jsonState = rememberUpdatedState(
        MapJson(
            ring = ringJson,
            link = lineToGeoJson(ringCenter, linkTo),
            pins = pinsToGeoJson(pins),
            highlight = pinsToGeoJson(highlightList),
            user = pointToGeoJson(userLocation)
        )
    )
    val pinClickState = rememberUpdatedState(onPinClick)
    val mapTapState = rememberUpdatedState(onMapTap)

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
        initialCameraPosition = CameraPosition(
            center = Position(
                longitude = cameraCenter.longitude,
                latitude = cameraCenter.latitude
            ),
            zoom = cameraZoom
        )
    ) {
        val json = jsonState.value
        val ringSource = rememberGeoJsonSource(GeoJsonData.JsonString(json.ring))
        val linkSource = rememberGeoJsonSource(GeoJsonData.JsonString(json.link))
        val pinSource = rememberGeoJsonSource(GeoJsonData.JsonString(json.pins))
        val highlightSource = rememberGeoJsonSource(GeoJsonData.JsonString(json.highlight))
        val userSource = rememberGeoJsonSource(GeoJsonData.JsonString(json.user))

        FillLayer(
            id = "radius-fill",
            source = ringSource,
            color = const(UserBlue),
            opacity = const(0.12f)
        )
        LineLayer(
            id = "radius-line",
            source = ringSource,
            color = const(UserBlue),
            width = const(2.dp)
        )
        LineLayer(
            id = "link-line",
            source = linkSource,
            color = const(PinHighlight),
            width = const(3.dp)
        )
        CircleLayer(
            id = "pins",
            source = pinSource,
            radius = const(9.dp),
            color = const(PinGreen),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            hitPadding = 12.dp,
            onClick = { features ->
                if (features.isNotEmpty()) {
                    val id = features[0].properties?.get("id")?.jsonPrimitive?.content
                    if (id != null) {
                        pinClickState.value(id)
                    }
                }
                ClickResult.Consume
            }
        )
        CircleLayer(
            id = "pin-highlight",
            source = highlightSource,
            radius = const(13.dp),
            color = const(PinHighlight),
            strokeColor = const(Color.White),
            strokeWidth = const(3.dp)
        )
        CircleLayer(
            id = "user-dot",
            source = userSource,
            radius = const(8.dp),
            color = const(UserBlue),
            strokeColor = const(Color.White),
            strokeWidth = const(3.dp)
        )
    }

    LaunchedEffect(cameraCenter, cameraZoom) {
        mapState.animateCamera(
            update = CameraUpdate(
                center = Position(
                    longitude = cameraCenter.longitude,
                    latitude = cameraCenter.latitude
                ),
                zoom = cameraZoom
            )
        )
    }

    val scope = rememberCoroutineScope()

    Box(modifier) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            interactions = MapInteractions {
                camera {
                    rotate { enabled = false }
                    pitch { enabled = false }
                }
                callbacks {
                    click {
                        onEvent { event ->
                            val position = event.position
                            var result = ClickResult.Pass
                            if (position != null) {
                                val used = mapTapState.value(
                                    LatLng(position.latitude, position.longitude)
                                )
                                if (used) {
                                    result = ClickResult.Consume
                                }
                            }
                            result
                        }
                    }
                }
            }
        )

        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                modifier = Modifier.testTag("zoom_in"),
                onClick = {
                    scope.launch {
                        mapState.animateCamera(
                            update = CameraUpdate(zoom = mapState.cameraPosition.zoom + 1.0)
                        )
                    }
                }
            ) {
                Text("+")
            }
            SmallFloatingActionButton(
                modifier = Modifier.testTag("zoom_out"),
                onClick = {
                    scope.launch {
                        mapState.animateCamera(
                            update = CameraUpdate(zoom = mapState.cameraPosition.zoom - 1.0)
                        )
                    }
                }
            ) {
                Text("−")
            }
        }
    }
}

/**
 * Full-screen campsite map.
 *
 * With [focusId] null (opened from Explore) it asks for the device location, shows the user's
 * dot, a radius ring the user picks, and only the campsites inside that ring. Tapping a pin
 * highlights it, draws a line to it from the user's dot and shows a card with the distance;
 * if [onOpenCampsite] is given the card has a "View details" button.
 *
 * With [focusId] set (opened from a campsite's details) it centres on that campsite, shows
 * every pin, and highlights the campsite.
 *
 * @param pins Every campsite that has a readable location.
 * @param focusId The campsite to centre on, or null for the radius view.
 * @param onClose Called to leave the map.
 * @param onOpenCampsite Called with a campsite id when "View details" is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteMapScreen(
    pins: List<MapPin>,
    focusId: String?,
    onClose: () -> Unit,
    onOpenCampsite: ((String) -> Unit)? = null
) {
    var radiusKm by rememberSaveable { mutableStateOf(25) }
    var userPos by remember { mutableStateOf<LatLng?>(null) }
    var locationNote by remember { mutableStateOf("") }
    var settingLocation by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    val exploring = focusId == null
    val focusPin = findPin(pins, focusId)

    val requestLocation = rememberLocationRequester { found ->
        if (found == null) {
            locationNote = "Couldn't get your location, so Edmonton is used instead. " +
                    "Tap \"Set my location\" to place yourself."
        } else {
            userPos = found
            locationNote = ""
        }
    }

    LaunchedEffect(exploring) {
        if (exploring) {
            requestLocation()
        }
    }

    BackHandler { onClose() }

    val searchCenter = userPos ?: DEFAULT_MAP_CENTER

    var shownPins = pins
    if (exploring) {
        shownPins = pinsWithinRadius(pins, searchCenter, radiusKm.toDouble())
    }

    var cameraCenter = searchCenter
    var cameraZoom = zoomForRadius(searchCenter.latitude, radiusKm.toDouble())
    if (focusPin != null) {
        cameraCenter = LatLng(focusPin.latitude, focusPin.longitude)
        cameraZoom = 11.0
    }

    val cardPin = findPin(shownPins, selectedId) ?: focusPin

    var linkTarget: LatLng? = null
    var distanceText: String? = null
    if (exploring && cardPin != null) {
        val target = LatLng(cardPin.latitude, cardPin.longitude)
        linkTarget = target
        distanceText = formatDistance(distanceKm(searchCenter, target)) + " away"
    }

    Box(Modifier.fillMaxSize()) {
        PinMap(
            modifier = Modifier.fillMaxSize().testTag("campsite_map"),
            cameraCenter = cameraCenter,
            cameraZoom = cameraZoom,
            pins = shownPins,
            highlightedId = selectedId ?: focusId,
            userLocation = if (exploring) userPos else null,
            ringCenter = if (exploring) searchCenter else null,
            ringKm = radiusKm.toDouble(),
            linkTo = linkTarget,
            onPinClick = { id -> selectedId = id },
            onMapTap = { tapped ->
                if (exploring && settingLocation) {
                    userPos = tapped
                    settingLocation = false
                    locationNote = ""
                    true
                } else {
                    false
                }
            }
        )

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        modifier = Modifier.testTag("map_close"),
                        onClick = onClose
                    ) {
                        Text("Back")
                    }
                    Text(
                        text = if (exploring) "Campsites near me" else "Campsite location",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (exploring) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (choice in RADIUS_CHOICES_KM) {
                            FilterChip(
                                modifier = Modifier.testTag("radius_$choice"),
                                selected = radiusKm == choice,
                                onClick = { radiusKm = choice },
                                label = { Text("$choice km") }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.testTag("map_count"),
                            text = "${shownPins.size} campsites within $radiusKm km"
                        )
                        TextButton(
                            modifier = Modifier.testTag("map_set_location"),
                            onClick = { settingLocation = true }
                        ) {
                            Text("Set my location")
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (settingLocation) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 4.dp) {
                    Text(
                        modifier = Modifier.padding(12.dp),
                        text = "Tap the map to set your location."
                    )
                }
            } else if (locationNote.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 4.dp) {
                    Text(modifier = Modifier.padding(12.dp), text = locationNote)
                }
            }

            if (cardPin != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("pin_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = cardPin.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (distanceText != null) {
                                Text(
                                    modifier = Modifier.testTag("pin_distance"),
                                    text = distanceText
                                )
                            }
                        }
                        if (onOpenCampsite != null) {
                            Button(
                                modifier = Modifier.testTag("pin_open_details"),
                                onClick = { onOpenCampsite(cardPin.id) }
                            ) {
                                Text("View details")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen map where an owner drops a pin for their campsite (tap the map, or use the
 * device location), then confirms. The map pans and zooms freely.
 *
 * @param initial The pin to start from (an existing campsite's location), or null.
 * @param onPicked Called with the chosen point when the owner confirms.
 * @param onCancel Called when the owner backs out without choosing.
 */
@Composable
fun LocationPickerScreen(
    initial: LatLng?,
    onPicked: (LatLng) -> Unit,
    onCancel: () -> Unit
) {
    var picked by remember { mutableStateOf(initial) }
    var cameraCenter by remember { mutableStateOf(initial ?: DEFAULT_MAP_CENTER) }
    var cameraZoom by remember { mutableStateOf(if (initial != null) 12.0 else 6.0) }
    var note by remember { mutableStateOf("") }

    val requestLocation = rememberLocationRequester { found ->
        if (found == null) {
            note = "Couldn't get your location. Tap the map to place the pin instead."
        } else {
            picked = found
            cameraCenter = found
            cameraZoom = 13.0
            note = ""
        }
    }

    BackHandler { onCancel() }

    val current = picked
    var pickedPins = emptyList<MapPin>()
    if (current != null) {
        pickedPins = listOf(MapPin("picked", "Campsite", current.latitude, current.longitude))
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            PinMap(
                modifier = Modifier.fillMaxSize().testTag("picker_map"),
                cameraCenter = cameraCenter,
                cameraZoom = cameraZoom,
                pins = pickedPins,
                highlightedId = if (current != null) "picked" else null,
                userLocation = null,
                ringCenter = null,
                ringKm = 0.0,
                onPinClick = { },
                onMapTap = { tapped ->
                    picked = tapped
                    true
                }
            )
        }

        Surface(shadowElevation = 8.dp, color = Color.White) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    modifier = Modifier.testTag("picker_status"),
                    text = if (current != null) {
                        "Pin: " + formatLatLng(current)
                    } else {
                        "Tap the map to drop a pin on your campsite."
                    }
                )
                if (note.isNotEmpty()) {
                    Text(note)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        modifier = Modifier.testTag("picker_cancel"),
                        onClick = onCancel
                    ) {
                        Text("Cancel")
                    }
                    TextButton(
                        modifier = Modifier.testTag("picker_use_my_location"),
                        onClick = { requestLocation() }
                    ) {
                        Text("Use my location")
                    }
                    Button(
                        modifier = Modifier.testTag("picker_confirm"),
                        enabled = current != null,
                        onClick = {
                            if (current != null) {
                                onPicked(current)
                            }
                        }
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}