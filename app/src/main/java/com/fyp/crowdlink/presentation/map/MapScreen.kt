package com.fyp.crowdlink.presentation.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
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
import kotlinx.coroutines.launch
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
  "layers": [{
    "id": "osm",
    "type": "raster",
    "source": "osm"
  }]
}
"""

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
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle initial friend selection from deep link
    LaunchedEffect(initialFriendId) {
        viewModel.selectFriendOnLoad(initialFriendId)
    }

    // Initialise MapLibre — must be called before MapView is created
    // MapLibre requires a token string but accepts empty string for OSM
    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // Map view
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        getMapAsync { map ->
                            mapboxMap = map
                            map.setStyle(
                                Style.Builder().fromJson(RASTER_STYLE_JSON)
                            ) { style ->
                                // Load the arrow drawable and add it to the style
                                val arrowDrawable = AppCompatResources.getDrawable(ctx, R.drawable.ic_location_arrow)
                                if (arrowDrawable != null) {
                                    val arrowBitmap = Bitmap.createBitmap(
                                        arrowDrawable.intrinsicWidth,
                                        arrowDrawable.intrinsicHeight,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = Canvas(arrowBitmap)
                                    arrowDrawable.setBounds(0, 0, canvas.width, canvas.height)
                                    arrowDrawable.draw(canvas)
                                    style.addImage("location-arrow", arrowBitmap)
                                } else {
                                    Log.e("MapScreen", "Failed to load location arrow drawable")
                                }

                                // Add a GeoJsonSource for friend pins
                                style.addSource(GeoJsonSource("friend-pins-source"))

                                // Friend pins layer
                                style.addLayer(
                                    SymbolLayer("friend-pins-layer", "friend-pins-source").apply {
                                        setFilter(Expression.neq(Expression.get("type"), Expression.literal("me")))
                                        setProperties(
                                            PropertyFactory.textField(Expression.get("name")),
                                            PropertyFactory.textSize(14f),
                                            PropertyFactory.textColor("#FF5722"),
                                            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                                            PropertyFactory.iconAllowOverlap(true),
                                            PropertyFactory.textAllowOverlap(true)
                                        )
                                    }
                                )

                                // My location layer — separate layer with rotating arrow icon
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

                                // Centre on user location or selected friend
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
                                    // Auto-cache tiles around current location
                                    cacheTilesForArea(
                                        context = ctx,
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        radiusMeters = 1000.0,
                                        onStart = { viewModel.startTileCaching() },
                                        onComplete = { viewModel.onTileCachingComplete() }
                                    )
                                }
                            }

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

            // Update pins whenever friendPins changes
            LaunchedEffect(friendPins, mapboxMap, myLocation, myHeading) {
                val style = mapboxMap?.style ?: return@LaunchedEffect
                val source = style.getSourceAs<GeoJsonSource>("friend-pins-source") ?: return@LaunchedEffect

                val features = mutableListOf<Feature>()

                // My location pin with heading
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
                    features.add(
                        Feature.fromGeometry(
                            Point.fromLngLat(pin.location.longitude, pin.location.latitude),
                            JsonObject().apply {
                                addProperty("name", pin.friend.displayName)
                                addProperty("type", "friend")
                                addProperty("friendId", pin.friend.deviceId)
                            }
                        )
                    )
                }

                source.setGeoJson(FeatureCollection.fromFeatures(features))
            }

            // My location FAB
            FloatingActionButton(
                onClick = {
                    val loc = myLocation
                    if (loc != null) {
                        mapboxMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude),
                                16.0
                            )
                        )
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Waiting for GPS fix…")
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = if (selectedPin != null) 160.dp else 0.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My location")
            }

            // Tile caching indicator
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
                        Text(
                            text = "Downloading map…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Selected friend bottom sheet
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
                                onClick = {
                                    onNavigateToCompass(
                                        pin.friend.deviceId,
                                        pin.friend.displayName
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Explore, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Precision Find")
                            }
                            OutlinedButton(
                                onClick = {
                                    onNavigateToChat(
                                        pin.friend.deviceId,
                                        pin.friend.displayName
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null)
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
 * Caches OpenStreetMap tiles for a circular area around the given coordinates
 * at zoom levels 15-18 using MapLibre's offline manager.
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

    // Calculate bounding box from centre + radius
    val latDelta = radiusMeters / 111000.0
    val lonDelta = radiusMeters / (111000.0 * Math.cos(Math.toRadians(latitude)))

    val bounds = LatLngBounds.Builder()
        .include(LatLng(latitude + latDelta, longitude + lonDelta))
        .include(LatLng(latitude - latDelta, longitude - lonDelta))
        .build()

    val definition = OfflineTilePyramidRegionDefinition(
        RASTER_STYLE_JSON,
        bounds,
        15.0,   // min zoom
        18.0,   // max zoom
        context.resources.displayMetrics.density
    )

    val metadata = try {
        val json = JSONObject().apply { put("name", "crowdlink_cache") }
        json.toString().toByteArray()
    } catch (e: Exception) {
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
