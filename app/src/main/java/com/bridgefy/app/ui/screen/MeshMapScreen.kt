package com.bridgefy.app.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

/**
 * Mesh Map Screen — shows live peer locations on an offline MapLibre map.
 *
 * Three marker types:
 * - Blue circles: live mesh peer locations (from LOCATION_PING)
 * - Red circle: shared location pin (from chat "View on Map")
 * - Green circle: user's own GPS position
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
    val peers by viewModel.peersWithLocation.collectAsState()
    val selfLocation by viewModel.selfLocation.collectAsState()
    val showConsentDialog by viewModel.showConsentDialog.collectAsState()

    // Show consent dialog on first visit
    if (showConsentDialog) {
        LocationConsentDialog(
            onAccept = { viewModel.acceptLocationConsent() },
            onDecline = { viewModel.declineLocationConsent() }
        )
    }

    // Store map reference for updates
    var mapRef by remember { mutableStateOf<MapboxMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

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
                        Text(
                            "Mesh Map",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
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
                    // Peer count badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeshBlue.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
            // MapLibre map view
            AndroidView(
                factory = { context ->
                    Mapbox.getInstance(context)
                    MapView(context).also { mapView ->
                        mapView.onCreate(null)
                        mapView.getMapAsync { map ->
                            mapRef = map

                            // Start position — focus on shared pin or world center
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
                                // Peer markers source (blue)
                                style.addSource(GeoJsonSource("peers-source"))
                                style.addLayer(
                                    CircleLayer("peers-layer", "peers-source").withProperties(
                                        PropertyFactory.circleRadius(8f),
                                        PropertyFactory.circleColor("#1E88E5"),
                                        PropertyFactory.circleStrokeWidth(2f),
                                        PropertyFactory.circleStrokeColor("#FFFFFF")
                                    )
                                )

                                // Shared location pin (red)
                                style.addSource(GeoJsonSource("shared-pin-source"))
                                style.addLayer(
                                    CircleLayer("shared-pin-layer", "shared-pin-source").withProperties(
                                        PropertyFactory.circleRadius(12f),
                                        PropertyFactory.circleColor("#E53935"),
                                        PropertyFactory.circleStrokeWidth(2f),
                                        PropertyFactory.circleStrokeColor("#FFFFFF")
                                    )
                                )

                                // Self marker (green)
                                style.addSource(GeoJsonSource("self-source"))
                                style.addLayer(
                                    CircleLayer("self-layer", "self-source").withProperties(
                                        PropertyFactory.circleRadius(10f),
                                        PropertyFactory.circleColor("#43A047"),
                                        PropertyFactory.circleStrokeWidth(2f),
                                        PropertyFactory.circleStrokeColor("#FFFFFF")
                                    )
                                )

                                // Route path (neon green line)
                                style.addSource(GeoJsonSource("route-source"))
                                style.addLayer(
                                    LineLayer("route-layer", "route-source").withProperties(
                                        PropertyFactory.lineColor("#00E676"),
                                        PropertyFactory.lineWidth(5f),
                                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                                    )
                                )

                                // If navigated from "View on Map" CTA or SOS with focus coordinates
                                if (focusLat != null && focusLon != null && pinType != null) {
                                    val feature = Feature.fromGeometry(
                                        Point.fromLngLat(focusLon, focusLat)
                                    )
                                    (style.getSource("shared-pin-source") as? GeoJsonSource)
                                        ?.setGeoJson(FeatureCollection.fromFeature(feature))
                                }

                                styleReady = true
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

                    // Update self marker
                    selfLocation?.let { loc ->
                        val selfFeature = Feature.fromGeometry(
                            Point.fromLngLat(loc.longitude, loc.latitude)
                        )
                        (style.getSource("self-source") as? GeoJsonSource)
                            ?.setGeoJson(FeatureCollection.fromFeature(selfFeature))

                        // Update route path if we have a target
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
