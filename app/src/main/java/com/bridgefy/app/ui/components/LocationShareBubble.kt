package com.bridgefy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bridgefy.app.model.LocationSharePayload
import com.bridgefy.app.ui.theme.*
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource

/**
 * Chat bubble for LOCATION_SHARE messages.
 *
 * Renders:
 * - Location pin icon + label or "Shared Location"
 * - Live mini MapLibre vector map showing coordinates (non-interactive, lightweight)
 * - Coordinate readout with accuracy
 * - Relative timestamp
 * - "View on Map" CTA button
 */
@Composable
fun LocationShareBubble(
    payload: LocationSharePayload,
    isSelf: Boolean,
    onViewOnMap: (lat: Double, lon: Double) -> Unit
) {
    val bubbleGradient = if (isSelf) {
        Brush.linearGradient(listOf(BubbleSent, BubbleSentLight))
    } else {
        Brush.linearGradient(listOf(BubbleReceived, BubbleReceivedLight))
    }

    Column(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isSelf) 16.dp else 4.dp,
                    bottomEnd = if (isSelf) 4.dp else 16.dp
                )
            )
            .background(bubbleGradient)
            .padding(12.dp)
    ) {
        // Location header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (isSelf) MeshBlueGlow else MeshBlueBright,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = payload.label ?: "Shared Location",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(8.dp))

        // actual offline MapLibre preview card
        AndroidView(
            factory = { context ->
                Mapbox.getInstance(context)
                MapView(context).also { mapView ->
                    mapView.onCreate(null)
                    mapView.getMapAsync { map ->
                        // Disable all interactions to act purely as a preview
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        map.uiSettings.isZoomGesturesEnabled = false
                        map.uiSettings.isScrollGesturesEnabled = false
                        map.uiSettings.isTiltGesturesEnabled = false
                        map.uiSettings.isRotateGesturesEnabled = false

                        val target = LatLng(payload.lat, payload.lon)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(target)
                            .zoom(13.0)
                            .build()

                        map.setStyle(Style.Builder().fromUri("asset://map_style.json")) { style ->
                            style.addSource(GeoJsonSource("mini-pin-source"))
                            style.addLayer(
                                CircleLayer("mini-pin-layer", "mini-pin-source").withProperties(
                                    PropertyFactory.circleRadius(6f),
                                    PropertyFactory.circleColor("#EF4444"), // red marker
                                    PropertyFactory.circleStrokeWidth(1.5f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF")
                                )
                            )
                            val feature = Feature.fromGeometry(Point.fromLngLat(payload.lon, payload.lat))
                            (style.getSource("mini-pin-source") as? GeoJsonSource)
                                ?.setGeoJson(FeatureCollection.fromFeature(feature))
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.height(8.dp))

        // Coordinates
        Text(
            text = "%.5f, %.5f".format(payload.lat, payload.lon),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        // Accuracy + relative time
        Text(
            text = "±${payload.accuracyMeters.toInt()}m  •  ${formatRelativeTime(payload.timestampEpochSec)}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )

        Spacer(Modifier.height(10.dp))

        // View on Map CTA
        OutlinedButton(
            onClick = { onViewOnMap(payload.lat, payload.lon) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MeshBlueBright
            )
        ) {
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("View on Map", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Format an epoch-second timestamp as a relative time string.
 */
fun formatRelativeTime(epochSec: Long): String {
    val ageSeconds = (System.currentTimeMillis() / 1000) - epochSec
    return when {
        ageSeconds < 60 -> "Just now"
        ageSeconds < 3600 -> "${ageSeconds / 60}m ago"
        ageSeconds < 86400 -> "${ageSeconds / 3600}h ago"
        else -> "${ageSeconds / 86400}d ago"
    }
}
