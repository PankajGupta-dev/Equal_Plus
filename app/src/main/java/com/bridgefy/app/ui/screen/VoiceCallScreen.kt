package com.bridgefy.app.ui.screen

import android.graphics.Bitmap
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.bridgefy.app.call.CallState
import com.bridgefy.app.ui.theme.*

@Composable
fun VoiceCallScreen(
    peerName: String,
    callState: CallState,
    callDuration: Long,
    isMuted: Boolean,
    isSpeaker: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onEndCall: () -> Unit,
    isVideoCall: Boolean = false,
    remoteVideoFrame: Bitmap? = null,
    isLocalVideoEnabled: Boolean = true,
    isFrontCamera: Boolean = true,
    onLocalVideoToggle: () -> Unit = {},
    onCameraFacingToggle: () -> Unit = {},
    startCameraCapture: (LifecycleOwner, (Preview) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // Format duration to MM:SS
    val durationText = remember(callDuration) {
        val minutes = callDuration / 60
        val seconds = callDuration % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    val stateText = when (callState) {
        CallState.IDLE -> "Idle"
        CallState.CALLING -> "Calling..."
        CallState.RINGING -> "Ringing..."
        CallState.CONNECTED -> durationText
        CallState.ENDED -> "Call Ended"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MeshNavy),
        contentAlignment = Alignment.Center
    ) {
        if (isVideoCall) {
            // Fullscreen remote video stream
            if (remoteVideoFrame != null) {
                Image(
                    bitmap = remoteVideoFrame.asImageBitmap(),
                    contentDescription = "Remote video stream",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Remote video loading placeholder
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MeshNavyLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MeshBlue,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = peerName,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (callState == CallState.CONNECTED) "Waiting for video feed..." else stateText,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            // Top Status Bar Overlay (Peer name & Duration)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = peerName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = stateText,
                    color = if (callState == CallState.ENDED) StatusFailed else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Picture-in-Picture (PiP) Floating Local Camera Preview Card
            if (callState == CallState.CONNECTED || callState == CallState.CALLING) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 112.dp, end = 24.dp)
                        .size(width = 100.dp, height = 140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, MeshBlue, RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    if (isLocalVideoEnabled) {
                        CameraPreview(
                            isFrontCamera = isFrontCamera,
                            startCameraCapture = startCameraCapture,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = "Camera Muted",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // Overlay Controls at bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(24.dp)
            ) {
                // Toggles Row (Mute mic, Mute cam, Swap cam, Speaker)
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Mute Mic
                    FilledIconButton(
                        onClick = onMuteToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (isMuted) MeshNavy else Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute mic"
                        )
                    }

                    // Mute Camera
                    FilledIconButton(
                        onClick = onLocalVideoToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (!isLocalVideoEnabled) Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (!isLocalVideoEnabled) MeshNavy else Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocalVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle video"
                        )
                    }

                    // Swap Camera
                    val lifecycleOwner = LocalLifecycleOwner.current
                    FilledIconButton(
                        onClick = { onCameraFacingToggle() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Swap camera"
                        )
                    }

                    // Speaker
                    FilledIconButton(
                        onClick = onSpeakerToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isSpeaker) Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (isSpeaker) MeshNavy else Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "Speakerphone"
                        )
                    }
                }

                // End call button
                LargeFloatingActionButton(
                    onClick = onEndCall,
                    containerColor = StatusFailed,
                    contentColor = TextPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else {
            // Voice-only call UI layout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 64.dp, horizontal = 32.dp)
            ) {
                // Upper section: Avatar & Name
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar circle
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MeshBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MeshBlue,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Text(
                        text = peerName.ifEmpty { "Mesh Peer" },
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stateText,
                        color = if (callState == CallState.ENDED) StatusFailed else TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Lower section: Controls
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Toggles row (Mute, Speaker)
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Mute button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = onMuteToggle,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isMuted) TextPrimary else SurfaceCard,
                                    contentColor = if (isMuted) MeshNavy else TextPrimary
                                ),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute"
                                )
                            }
                            Text(
                                text = "Mute",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        // Speaker button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = onSpeakerToggle,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isSpeaker) TextPrimary else SurfaceCard,
                                    contentColor = if (isSpeaker) MeshNavy else TextPrimary
                                ),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                    contentDescription = "Speaker"
                                )
                            }
                            Text(
                                text = "Speaker",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // End call button
                    LargeFloatingActionButton(
                        onClick = onEndCall,
                        containerColor = StatusFailed,
                        contentColor = TextPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    isFrontCamera: Boolean,
    startCameraCapture: (LifecycleOwner, (Preview) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(isFrontCamera) {
        startCameraCapture(lifecycleOwner) { preview ->
            preview.setSurfaceProvider(previewView.surfaceProvider)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
