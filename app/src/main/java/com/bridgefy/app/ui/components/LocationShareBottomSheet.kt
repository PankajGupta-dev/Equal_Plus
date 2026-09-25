package com.bridgefy.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.model.LocationSharePayload
import com.bridgefy.app.ui.theme.*
import com.bridgefy.app.ui.viewmodel.LocationShareViewModel

/**
 * Bottom sheet for sharing location in a chat.
 *
 * Shows:
 * - GPS fix status (acquiring/ready/failed)
 * - Coordinate readout with accuracy
 * - Optional user label (e.g. "Meet me here")
 * - Privacy note when mesh broadcasting is off
 * - Send/Cancel buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareBottomSheet(
    onConfirm: (LocationSharePayload) -> Unit,
    onDismiss: () -> Unit,
    viewModel: LocationShareViewModel
) {
    val locationState by viewModel.locationState.collectAsState()
    val isBroadcasting by viewModel.isBroadcastingToMesh.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MeshNavyLight,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MeshBlueBright,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Share Your Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            // Location status
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .animateContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = locationState) {
                        is LocationShareViewModel.LocationState.Acquiring -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MeshBlueBright
                                )
                                Text(
                                    "Acquiring GPS fix…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        is LocationShareViewModel.LocationState.Ready -> {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = MeshGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Location acquired",
                                        color = MeshGreen,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "%.5f, %.5f".format(state.payload.lat, state.payload.lon),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    "±${state.payload.accuracyMeters.toInt()}m accuracy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                        is LocationShareViewModel.LocationState.Failed -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = StatusFailed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Could not get location. Check that GPS is enabled.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusFailed
                                )
                            }
                        }
                    }
                }
            }

            // Optional user label
            var label by remember { mutableStateOf("") }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = { Text("Add a note (optional, e.g. \"Meet me here\")", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedBorderColor = MeshBlue,
                    unfocusedBorderColor = SurfaceDivider,
                    cursorColor = MeshBlue
                )
            )

            // Privacy note when broadcast is off
            if (!isBroadcasting) {
                Surface(
                    color = MeshBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MeshBlueBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Auto location sharing is off. Only this contact " +
                            "will receive your location via this message.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshBlueBright
                        )
                    }
                }
            }

            // Cancel / Send row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary
                    )
                ) { Text("Cancel") }

                Button(
                    onClick = {
                        val state = locationState
                        if (state is LocationShareViewModel.LocationState.Ready) {
                            onConfirm(state.payload.copy(label = label.ifBlank { null }))
                        }
                    },
                    enabled = locationState is LocationShareViewModel.LocationState.Ready,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeshBlue,
                        contentColor = TextPrimary
                    )
                ) {
                    if (locationState is LocationShareViewModel.LocationState.Acquiring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Send Location")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
