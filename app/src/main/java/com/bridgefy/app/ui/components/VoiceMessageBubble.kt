package com.bridgefy.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.media.PlayerState
import com.bridgefy.app.ui.theme.*

/**
 * Voice message bubble with play/pause button, waveform visualization,
 * seekable progress bar, and duration label.
 */
@Composable
fun VoiceMessageBubble(
    messageId: String,
    durationText: String,
    isPlaying: Boolean,
    isPaused: Boolean,
    progress: Float,
    isSentByMe: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onSeek: (Float) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        FilledIconButton(
            onClick = {
                when {
                    isPlaying -> onPause()
                    isPaused -> onResume()
                    else -> onPlay()
                }
            },
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isSentByMe) MeshBlue else MeshBlueBright,
                contentColor = TextPrimary
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Waveform / Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Background waveform bars
                WaveformBars(
                    progress = progress,
                    isPlaying = isPlaying,
                    isSentByMe = isSentByMe
                )

                // Seekable slider overlay
                Slider(
                    value = progress,
                    onValueChange = onSeek,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isSentByMe) MeshBlue else MeshBlueBright,
                        activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }

            // Duration
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Animated waveform bars that visualize voice message audio.
 */
@Composable
private fun WaveformBars(
    progress: Float,
    isPlaying: Boolean,
    isSentByMe: Boolean
) {
    // Generate fixed pseudo-random bar heights (deterministic for consistent look)
    val barHeights = remember {
        listOf(0.3f, 0.6f, 0.8f, 0.4f, 0.9f, 0.5f, 0.7f, 0.3f, 0.6f, 0.8f,
               0.4f, 0.7f, 0.5f, 0.9f, 0.3f, 0.6f, 0.8f, 0.4f, 0.7f, 0.5f,
               0.6f, 0.3f, 0.8f, 0.5f, 0.7f, 0.4f, 0.9f, 0.6f, 0.3f, 0.8f)
    }

    val activeColor = if (isSentByMe) MeshBlue else MeshBlueBright
    val inactiveColor = activeColor.copy(alpha = 0.25f)

    // Subtle pulse animation when playing
    val pulseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveformPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barHeights.forEachIndexed { index, height ->
            val barProgress = index.toFloat() / barHeights.size
            val isActive = barProgress <= progress
            val scale = if (isPlaying && isActive) pulseScale else 1f

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height * scale)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isActive) activeColor else inactiveColor)
            )
        }
    }
}

/**
 * Format milliseconds as "M:SS" duration string.
 */
fun formatVoiceDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
