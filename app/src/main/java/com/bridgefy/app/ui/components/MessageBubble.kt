package com.bridgefy.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.model.DeliveryStatus
import com.bridgefy.app.model.MeshMessage
import com.bridgefy.app.model.MessageType
import com.bridgefy.app.model.TransferProgress
import com.bridgefy.app.model.LocationSharePayload
import com.bridgefy.app.ui.theme.*
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val bubbleJson = Json { ignoreUnknownKeys = true }

/**
 * Chat bubble component with:
 * - Sent/received alignment and coloring
 * - Delivery status icon (⏳ pending, ✓ sent, ✓✓ delivered, ✕ failed)
 * - Hop count badge for relayed messages
 * - Timestamp
 * - Specialized content for IMAGE, VOICE, FILE, and LOCATION_SHARE messages
 */
@Composable
fun MessageBubble(
    message: MeshMessage,
    decryptedText: String,
    isSentByMe: Boolean,
    modifier: Modifier = Modifier,
    // Media support parameters
    mediaFilePath: String? = null,
    transferProgress: TransferProgress? = null,
    isPlayingVoice: Boolean = false,
    isVoicePaused: Boolean = false,
    voiceProgress: Float = 0f,
    onPlayVoice: () -> Unit = {},
    onPauseVoice: () -> Unit = {},
    onResumeVoice: () -> Unit = {},
    onSeekVoice: (Float) -> Unit = {},
    onViewOnMap: ((Double, Double) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null
) {
    val alignment = if (isSentByMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (isSentByMe) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(
                    if (isSentByMe) {
                        Brush.linearGradient(listOf(BubbleSent, BubbleSentLight))
                    } else {
                        Brush.linearGradient(listOf(BubbleReceived, BubbleReceivedLight))
                    }
                )
                .padding(12.dp)
                .animateContentSize()
        ) {
            // ─── Content by message type ──────────────────────
            when (message.type) {
                MessageType.IMAGE -> {
                    val context = LocalContext.current
                    val parts = message.fileName?.split("|")
                    val cleanFileName = parts?.getOrNull(0) ?: message.fileName
                    val caption = parts?.getOrNull(1)

                    ImagePreviewBubble(
                        filePath = mediaFilePath,
                        fileName = cleanFileName,
                        isSentByMe = isSentByMe,
                        transferProgress = transferProgress,
                        onImageClick = {
                            mediaFilePath?.let { path ->
                                if (onImageClick != null) {
                                    onImageClick(path)
                                } else {
                                    val file = File(path)
                                    if (file.exists()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.fromFile(file), "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    )

                    if (!caption.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                MessageType.VIDEO -> {
                    val context = LocalContext.current
                    val parts = message.fileName?.split("|")
                    val cleanFileName = parts?.getOrNull(0) ?: message.fileName
                    val caption = parts?.getOrNull(1)

                    VideoPreviewBubble(
                        filePath = mediaFilePath,
                        fileName = cleanFileName,
                        isSentByMe = isSentByMe,
                        transferProgress = transferProgress,
                        onPlayClick = {
                            mediaFilePath?.let { path ->
                                if (onVideoClick != null) {
                                    onVideoClick(path)
                                } else {
                                    val file = File(path)
                                    if (file.exists()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.fromFile(file), "video/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    )

                    if (!caption.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                MessageType.VOICE -> {
                    // Voice message with waveform
                    VoiceMessageBubble(
                        messageId = message.id,
                        durationText = decryptedText,
                        isPlaying = isPlayingVoice,
                        isPaused = isVoicePaused,
                        progress = voiceProgress,
                        isSentByMe = isSentByMe,
                        onPlay = onPlayVoice,
                        onPause = onPauseVoice,
                        onResume = onResumeVoice,
                        onSeek = onSeekVoice
                    )
                }

                MessageType.FILE -> {
                    // File attachment indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = if (isSentByMe) MeshBlueGlow else MeshBlueBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message.fileName ?: "Attachment",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSentByMe) MeshBlueGlow else MeshBlueBright,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Show transfer progress for files
                    if (transferProgress != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { transferProgress.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MeshBlue,
                            trackColor = MeshBlue.copy(alpha = 0.2f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                MessageType.LOCATION_SHARE -> {
                    // Location share with coordinates and "View on Map" CTA
                    val locPayload = try {
                        bubbleJson.decodeFromString<LocationSharePayload>(message.payload)
                    } catch (_: Exception) { null }

                    if (locPayload != null) {
                        LocationShareBubble(
                            payload = locPayload,
                            isSelf = isSentByMe,
                            onViewOnMap = { lat, lon ->
                                onViewOnMap?.invoke(lat, lon)
                            }
                        )
                    } else {
                        Text(
                            text = "📍 Location (unavailable)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }

                else -> {
                    // Text messages (TEXT, ACK, etc.)
                    Text(
                        text = decryptedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom row: timestamp + hop count + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isSentByMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )

                // Hop count badge (only for relayed messages)
                if (message.hopCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MeshBlue.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${message.hopCount} hop${if (message.hopCount > 1) "s" else ""}",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshBlueBright,
                            fontSize = 9.sp
                        )
                    }
                }

                // Delivery status (sent messages only)
                if (isSentByMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    DeliveryStatusIcon(message.status)
                }
            }
        }
    }
}

@Composable
fun DeliveryStatusIcon(status: DeliveryStatus) {
    val (icon, tint) = when (status) {
        DeliveryStatus.QUEUED -> Icons.Default.Schedule to StatusPending
        DeliveryStatus.SENDING -> Icons.Default.Sync to StatusSending
        DeliveryStatus.SENT -> Icons.Default.Check to StatusSent
        DeliveryStatus.DELIVERED -> Icons.Default.DoneAll to StatusDelivered
        DeliveryStatus.FAILED -> Icons.Default.ErrorOutline to StatusFailed
        DeliveryStatus.EXPIRED -> Icons.Default.TimerOff to StatusPending
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        tint = tint,
        modifier = Modifier.size(14.dp)
    )
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
