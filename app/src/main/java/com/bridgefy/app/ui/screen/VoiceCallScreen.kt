package com.bridgefy.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
