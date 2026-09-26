package com.bridgefy.app.ui.screen

import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bridgefy.app.map.OfflineMapManager
import com.bridgefy.app.ui.components.LocationConsentDialog
import com.bridgefy.app.ui.theme.*
import com.bridgefy.app.ui.viewmodel.MeshMapViewModel
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import kotlinx.coroutines.launch

/**
 * Mesh Map Screen — shows live peer locations on a 100% offline MapLibre map.
 * Powered by local MBTiles SQLite tile server (zero internet & zero API keys).
 *
 * Marker types:
 * - Blue circles: live mesh peer locations (from LOCATION_PING)
 * - Red circle: shared location pin (from chat "View on Map" or SOS)
 * - Green circle: user's own GPS position
 * - Neon green line: tactical route heading from user to target
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshMapScreen(
    focusLat: Double? = null,
    focusLon: Double? = null,
    pinType: String? = null,
    viewModel: MeshMapViewModel,
    onBack: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val peers by viewModel.peersWithLocation.collectAsState()
    val selfLocation by viewModel.selfLocation.collectAsState()
    val showConsentDialog by viewModel.showConsentDialog.collectAsState()

    val activeMbtilesName by OfflineMapManager.activeMbtilesName.collectAsState()
    val isImporting by OfflineMapManager.isImporting.collectAsState()
    val importProgress by OfflineMapManager.importProgress.collectAsState()

    // Show consent dialog on first visit
    if (showConsentDialog) {
        LocationConsentDialog(
            onAccept = { viewModel.acceptLocationConsent() },
            onDecline = { viewModel.declineLocationConsent() }
        )
    }

    var mapRef by remember { mutableStateOf<MapboxMap?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    // Setup map style layers helper
    fun setupStyleLayers(style: Style) {
        // Peer markers source (blue)
        if (style.getSource("peers-source") == null) {
            style.addSource(GeoJsonSource("peers-source"))
            style.addLayer(
                CircleLayer("peers-layer", "peers-source").withProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor("#1E88E5"),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#FFFFFF")
                )
            )
        }

        // Shared location pin (red)
        if (style.getSource("shared-pin-source") == null) {
            style.addSource(GeoJsonSource("shared-pin-source"))
            style.addLayer(
                CircleLayer("shared-pin-layer", "shared-pin-source").withProperties(
                    PropertyFactory.circleRadius(12f),
                    PropertyFactory.circleColor("#E53935"),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#FFFFFF")
                )
            )
        }

        // Self marker (green)
        if (style.getSource("self-source") == null) {
            style.addSource(GeoJsonSource("self-source"))
            style.addLayer(
                CircleLayer("self-layer", "self-source").withProperties(
                    PropertyFactory.circleRadius(10f),
                    PropertyFactory.circleColor("#43A047"),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#FFFFFF")
                )
            )
        }

        // Route path (neon green line)
        if (style.getSource("route-source") == null) {
            style.addSource(GeoJsonSource("route-source"))
            style.addLayer(
                LineLayer("route-layer", "route-source").withProperties(
                    PropertyFactory.lineColor("#00E676"),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }

        if (focusLat != null && focusLon != null && pinType != null) {
            val feature = Feature.fromGeometry(Point.fromLngLat(focusLon, focusLat))
            (style.getSource("shared-pin-source") as? GeoJsonSource)
                ?.setGeoJson(FeatureCollection.fromFeature(feature))
        }

        styleReady = true
    }

    // System file picker to import custom .mbtiles file
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val success = OfflineMapManager.importMbtilesFromUri(context, uri)
                if (success) {
                    Toast.makeText(context, "Offline map loaded successfully!", Toast.LENGTH_SHORT).show()
                    // Reload style to fetch newly available tiles from local server
                    mapRef?.setStyle(Style.Builder().fromUri("asset://map_style.json")) { style ->
                        setupStyleLayers(style)
                    }
                } else {
                    Toast.makeText(context, "Failed to load MBTiles file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Lifecycle observer for MapView
    DisposableEffect(lifecycleOwner, mapViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapViewRef?.onStart()
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                Lifecycle.Event.ON_STOP -> mapViewRef?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = MeshNavy,
        bottomBar = {
            if (focusLat == null) {
                bottomBar()
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MeshBlueBright,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Mesh Map",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp
                            )
                            Text(
                                if (activeMbtilesName != null) "Offline Map Active" else "Tactical Radar Mode",
                                color = if (activeMbtilesName != null) MeshGreen else TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (focusLat != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                actions = {
                    // MBTiles Import / Status Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (activeMbtilesName != null) MeshGreen.copy(alpha = 0.2f) else MeshBlueBright.copy(alpha = 0.15f),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (activeMbtilesName != null) Icons.Default.CheckCircle else Icons.Default.Download,
                                contentDescription = "Import MBTiles",
                                tint = if (activeMbtilesName != null) MeshGreen else MeshBlueBright,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (activeMbtilesName != null) "MBTiles Loaded" else "Import .mbtiles",
                                color = if (activeMbtilesName != null) MeshGreen else MeshBlueBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Peer count badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeshBlue.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MeshBlueBright)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${peers.size} peers",
                                color = MeshBlueBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeshNavyLight
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // MapLibre map view (using local tile server, zero API key check)
            AndroidView(
                factory = { ctx ->
                    Mapbox.getInstance(ctx)
                    MapView(ctx).also { mapView ->
                        mapViewRef = mapView
                        mapView.onCreate(null)
                        mapView.getMapAsync { map ->
                            mapRef = map

                            val startTarget = if (focusLat != null && focusLon != null) {
                                LatLng(focusLat, focusLon)
                            } else {
                                LatLng(20.0, 78.0) // Default: India center
                            }
                            val startZoom = if (focusLat != null) 15.0 else 4.0

                            map.cameraPosition = CameraPosition.Builder()
                                .target(startTarget)
                                .zoom(startZoom)
                                .build()

                            map.setStyle(Style.Builder().fromUri("asset://map_style.json")) { style ->
                                setupStyleLayers(style)
                            }
                        }
                    }
                },
                update = { _ ->
                    if (!styleReady) return@AndroidView
                    val map = mapRef ?: return@AndroidView
                    val style = map.style ?: return@AndroidView

                    // Update peer markers
                    val peerFeatures = peers.filter {
                        it.latitude != null && it.longitude != null
                    }.map { peer ->
                        Feature.fromGeometry(
                            Point.fromLngLat(peer.longitude!!, peer.latitude!!)
                        )
                    }
                    (style.getSource("peers-source") as? GeoJsonSource)
                        ?.setGeoJson(FeatureCollection.fromFeatures(peerFeatures))

                    // Update shared/target pin based on whether a peer is live or static fallback
                    var targetLat = focusLat
                    var targetLon = focusLon

                    if (pinType != null && pinType != "SHARED_LOCATION") {
                        val matchingPeer = peers.find { it.deviceId == pinType || it.bridgefyId == pinType || it.alertnetId == pinType }
                        if (matchingPeer != null && matchingPeer.latitude != null && matchingPeer.longitude != null) {
                            targetLat = matchingPeer.latitude
                            targetLon = matchingPeer.longitude
                        }
                    }

                    if (targetLat != null && targetLon != null) {
                        val feature = Feature.fromGeometry(Point.fromLngLat(targetLon, targetLat))
                        (style.getSource("shared-pin-source") as? GeoJsonSource)
                            ?.setGeoJson(FeatureCollection.fromFeature(feature))
                    }

                    // Update self marker and navigation line
                    selfLocation?.let { loc ->
                        val selfFeature = Feature.fromGeometry(
                            Point.fromLngLat(loc.longitude, loc.latitude)
                        )
                        (style.getSource("self-source") as? GeoJsonSource)
                            ?.setGeoJson(FeatureCollection.fromFeature(selfFeature))

                        if (targetLat != null && targetLon != null) {
                            val points = listOf(
                                Point.fromLngLat(loc.longitude, loc.latitude),
                                Point.fromLngLat(targetLon, targetLat)
                            )
                            val lineString = LineString.fromLngLats(points)
                            (style.getSource("route-source") as? GeoJsonSource)
                                ?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Import Progress Banner
            AnimatedVisibility(
                visible = isImporting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeshNavyLight,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MeshBlueBright,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = importProgress ?: "Importing MBTiles...",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Tactical Navigation HUD (Distance & Bearing when target is active)
            val activeTargetLat = focusLat
            val activeTargetLon = focusLon
            val curSelf = selfLocation
            if (activeTargetLat != null && activeTargetLon != null && curSelf != null) {
                val distResults = FloatArray(2)
                Location.distanceBetween(
                    curSelf.latitude,
                    curSelf.longitude,
                    activeTargetLat,
                    activeTargetLon,
                    distResults
                )
                val distM = distResults[0]
                val bearing = ((distResults[1] + 360) % 360).toInt()

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isImporting) 60.dp else 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MeshNavyLight.copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = MeshGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Distance: " + (if (distM < 1000) "%.0fm".format(distM) else "%.1fkm".format(distM / 1000f)),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Bearing: $bearing°",
                            color = MeshBlueBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Legend overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MeshNavyLight.copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    LegendItem(color = MeshGreen, label = "You")
                    LegendItem(color = MeshBlue, label = "Mesh Peers")
                    if (pinType != null) {
                        LegendItem(color = StatusFailed, label = "Target Location")
                    }
                    if (activeMbtilesName == null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "No MBTiles loaded\n(Using Tactical Radar)",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
