package com.fyp.crowdlink.presentation.map

import android.graphics.Canvas
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.R
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import timber.log.Timber
import kotlin.math.absoluteValue
import kotlin.math.cos
import androidx.core.graphics.createBitmap

// OSM raster tile style with glyphs endpoint required for SymbolLayer text rendering
private const val RASTER_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
  "layers": [{
    "id": "osm",
    "type": "raster",
    "source": "osm"
  }]
}
"""

/**
 * MapScreen
 *
 * Renders an offline-capable map using MapLibre with OSM raster tiles. Friend locations
 * received over the BLE mesh are plotted as pins with deterministic per-friend colours.
 * The user's own position is shown with a rotating heading arrow. Tapping a friend pin
 * opens a bottom sheet with compass and chat shortcuts. Tiles for the local area are
 * cached to disk on first load to support offline use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    initialFriendId: String? = null,
    onNavigateToCompass: (String, String) -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val myLocation by viewModel.myLocation.collectAsState()
    val friendPins by viewModel.friendPins.collectAsState()
    val selectedFriendId by viewModel.selectedFriendId.collectAsState()
    val isCachingTiles by viewModel.isCachingTiles.collectAsState()
    val myHeading by viewModel.myHeading.collectAsState()

    val selectedPin = friendPins.firstOrNull { it.friend.deviceId == selectedFriendId }

    var mapboxMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapReady by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // handle deep links from the discovery screen - pre-select a friend on load
    LaunchedEffect(initialFriendId) {
        viewModel.selectFriendOnLoad(initialFriendId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // AndroidView bridges the MapLibre MapView into the Compose hierarchy
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        getMapAsync { map ->
                            mapboxMap = map
                            map.setStyle(Style.Builder().fromJson(RASTER_STYLE_JSON)) { style ->

                                // VectorDrawables have -1 intrinsic dimensions, so explicit size is required
                                val arrowDrawable = AppCompatResources.getDrawable(ctx, R.drawable.ic_location_arrow)
                                if (arrowDrawable != null) {
                                    val size = 96
                                    val arrowBitmap = createBitmap(size, size)
                                    val canvas = Canvas(arrowBitmap)
                                    arrowDrawable.setBounds(0, 0, size, size)
                                    arrowDrawable.draw(canvas)
                                    style.addImage("location-arrow", arrowBitmap)
                                } else {
                                    Timber.tag("MapScreen").e("Failed to load location arrow drawable")
                                }

                                val personDrawable = AppCompatResources.getDrawable(ctx, R.drawable.ic_person_pin)
                                if (personDrawable != null) {
                                    val size = 96
                                    val personBitmap = createBitmap(size, size)
                                    val canvas = Canvas(personBitmap)
                                    personDrawable.setBounds(0, 0, size, size)
                                    personDrawable.draw(canvas)
                                    // SDF mode allows runtime tinting per friend colour
                                    style.addImage("person-pin", personBitmap, true)
                                }

                                // single GeoJSON source drives both the friend and self layers
                                style.addSource(GeoJsonSource("friend-pins-source"))

                                // friend pins layer - filtered to all features except "me"
                                style.addLayer(
                                    SymbolLayer("friend-pins-layer", "friend-pins-source").apply {
                                        setFilter(Expression.neq(Expression.get("type"), Expression.literal("me")))
                                        setProperties(
                                            PropertyFactory.iconImage("person-pin"),
                                            PropertyFactory.iconSize(1.2f),
                                            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                                            PropertyFactory.iconColor(Expression.get("color")),
                                            PropertyFactory.textField(Expression.get("name")),
                                            PropertyFactory.textSize(14f),
                                            PropertyFactory.textColor(Expression.get("color")),
                                            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                                            PropertyFactory.iconAllowOverlap(true),
                                            PropertyFactory.textAllowOverlap(true)
                                        )
                                    }
                                )

                                // self layer - rotating heading arrow, filtered to "me" feature only
                                style.addLayer(
                                    SymbolLayer("my-location-layer", "friend-pins-source").apply {
                                        setFilter(Expression.eq(Expression.get("type"), Expression.literal("me")))
                                        setProperties(
                                            PropertyFactory.iconImage("location-arrow"),
                                            PropertyFactory.iconRotate(Expression.get("bearing")),
                                            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                                            PropertyFactory.iconSize(1.5f),
                                            PropertyFactory.textField(Expression.get("name")),
                                            PropertyFactory.textSize(12f),
                                            PropertyFactory.textColor("#4285F4"),
                                            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                                            PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                                            PropertyFactory.iconAllowOverlap(true),
                                            PropertyFactory.textAllowOverlap(true)
                                        )
                                    }
                                )

                                // on first load, centre on the target friend if deep-linked, otherwise on self
                                val targetLocation = if (initialFriendId != null) {
                                    friendPins.firstOrNull { it.friend.deviceId == initialFriendId }?.location
                                } else {
                                    myLocation
                                }

                                targetLocation?.let { loc ->
                                    map.animateCamera(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(LatLng(loc.latitude, loc.longitude))
                                                .zoom(16.0)
                                                .build()
                                        )
                                    )
                                    // kick off tile caching for a 1km radius around the initial position
                                    cacheTilesForArea(
                                        context = ctx,
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        radiusMeters = 1000.0,
                                        onStart = { viewModel.startTileCaching() },
                                        onComplete = { viewModel.onTileCachingComplete() }
                                    )
                                }
                                mapReady = true
                            }

                            // tap a friend pin to select it and show the bottom sheet
                            map.addOnMapClickListener { point ->
                                val screenPoint = map.projection.toScreenLocation(point)
                                val features = map.queryRenderedFeatures(screenPoint, "friend-pins-layer")
                                if (features.isNotEmpty()) {
                                    val friendId = features[0].getStringProperty("friendId")
                                    viewModel.selectFriend(friendId)
                                } else {
                                    viewModel.selectFriend(null)
                                }
                                true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // rebuild GeoJSON whenever peer locations, self position or heading changes
            LaunchedEffect(friendPins, mapReady, myLocation, myHeading) {
                if (!mapReady) return@LaunchedEffect
                val style = mapboxMap?.style ?: return@LaunchedEffect
                val source = style.getSourceAs<GeoJsonSource>("friend-pins-source") ?: return@LaunchedEffect

                val features = mutableListOf<Feature>()
                // fixed palette - colour assigned deterministically by hashing the device ID
                val palette = listOf("#E53935", "#8E24AA", "#1E88E5", "#00897B", "#F4511E", "#6D4C41", "#039BE5", "#7CB342")

                myLocation?.let { loc ->
                    features.add(
                        Feature.fromGeometry(
                            Point.fromLngLat(loc.longitude, loc.latitude),
                            JsonObject().apply {
                                addProperty("name", "You")
                                addProperty("type", "me")
                                addProperty("bearing", myHeading)
                            }
                        )
                    )
                }

                friendPins.forEach { pin ->
                    val colorIndex = pin.friend.deviceId.hashCode().absoluteValue % palette.size
                    features.add(
                        Feature.fromGeometry(
                            Point.fromLngLat(pin.location.longitude, pin.location.latitude),
                            JsonObject().apply {
                                addProperty("name", pin.friend.displayName)
                                addProperty("type", "friend")
                                addProperty("friendId", pin.friend.deviceId)
                                addProperty("color", palette[colorIndex])
                            }
                        )
                    )
                }

                source.setGeoJson(FeatureCollection.fromFeatures(features))
            }

            // re-centre FAB - floats above the bottom sheet when a pin is selected
            FloatingActionButton(
                onClick = {
                    val loc = myLocation
                    if (loc != null) {
                        mapboxMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16.0)
                        )
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Waiting for GPS fix…") }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = if (selectedPin != null) 160.dp else 0.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My location")
            }

            // pill shown at the top while background tile download is in progress
            if (isCachingTiles) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(text = "Downloading map…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // bottom sheet shown when a friend pin is tapped - shows coordinates and action buttons
            selectedPin?.let { pin ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = pin.friend.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Last seen: ${"%.4f".format(pin.location.latitude)}, ${"%.4f".format(pin.location.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onNavigateToCompass(pin.friend.deviceId, pin.friend.displayName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Explore, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Precision Find")
                            }
                            OutlinedButton(
                                onClick = { onNavigateToChat(pin.friend.deviceId, pin.friend.displayName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Message")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * cacheTilesForArea
 *
 * Downloads OSM raster tiles for a circular region around a given coordinate using the
 * MapLibre offline manager. Zoom levels 15-18 are cached to give usable street-level
 * detail. A glyph cache warm-up request is fired after the download completes so that
 * text labels render correctly without a network connection.
 */
private fun cacheTilesForArea(
    context: android.content.Context,
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
    onStart: () -> Unit,
    onComplete: () -> Unit
) {
    val offlineManager = OfflineManager.getInstance(context)

    // convert radius in metres to lat/lon deltas for the bounding box
    val latDelta = radiusMeters / 111000.0
    val lonDelta = radiusMeters / (111000.0 * cos(Math.toRadians(latitude)))

    val bounds = LatLngBounds.Builder()
        .include(LatLng(latitude + latDelta, longitude + lonDelta))
        .include(LatLng(latitude - latDelta, longitude - lonDelta))
        .build()

    val definition = OfflineTilePyramidRegionDefinition(
        RASTER_STYLE_JSON,
        bounds,
        15.0, // min zoom
        18.0, // max zoom
        context.resources.displayMetrics.density
    )

    val metadata = try {
        JSONObject().apply { put("name", "crowdlink_cache") }.toString().toByteArray()
    } catch (_: Exception) {
        ByteArray(0)
    }

    onStart()

    try {
        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    try {
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                if (status.isComplete) {
                                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    onComplete()

                                    // pre-fetch glyph PBFs so labels render offline
                                    val client = OkHttpClient()
                                    val font = "Open Sans Regular,Arial Unicode MS Regular"
                                    val ranges = listOf("0-255", "256-511", "512-767")

                                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                        ranges.forEach { range ->
                                            try {
                                                val request = Request.Builder()
                                                    .url("https://demotiles.maplibre.org/font/$font/$range.pbf")
                                                    .build()
                                                client.newCall(request).execute().close()
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                            override fun onError(error: OfflineRegionError) {
                                Timber.e("Tile cache error: ${error.message}")
                                onComplete()
                            }
                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                Timber.w("Tile count limit exceeded: $limit")
                                onComplete()
                            }
                        })
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to start download")
                        onComplete()
                    }
                }
                override fun onError(error: String) {
                    Timber.e("Failed to create offline region: $error")
                    onComplete()
                }
            }
        )
    } catch (e: Exception) {
        Timber.e(e, "Offline manager exception")
        onComplete()
    }
}