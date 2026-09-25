package com.bridgefy.app.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.model.TransferProgress
import com.bridgefy.app.model.TransferState
import com.bridgefy.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Modern in-chat video message card bubble.
 * Features:
 * - Real-time extracted video frame thumbnail using MediaMetadataRetriever (loaded asynchronously)
 * - Duration overlay label (bottom-right)
 * - Semi-transparent modern Play button overlay
 * - Transfer progress overlay indicator
 * - Touch action to play
 */
@Composable
fun VideoPreviewBubble(
    filePath: String?,
    fileName: String?,
    isSentByMe: Boolean,
    transferProgress: TransferProgress?,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {}
) {
    val exists = filePath?.let { File(it).exists() } == true
    var thumbnail by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var durationText by remember(filePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        if (exists && filePath != null) {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(filePath)
                    
                    thumbnail = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    if (durationMs != null) {
                        val totalSeconds = (durationMs / 1000).toInt()
                        val minutes = totalSeconds / 60
                        val seconds = totalSeconds % 60
                        durationText = String.format("%d:%02d", minutes, seconds)
                    }
                    retriever.release()
                } catch (e: Exception) {
                    try {
                        thumbnail = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.MINI_KIND)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlayClick() }
    ) {
        if (exists && thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = fileName ?: "Video Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = fileName ?: "Video File",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        if (transferProgress == null || transferProgress.state == TransferState.COMPLETED) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Video",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        durationText?.let { duration ->
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = duration,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (transferProgress != null &&
            (transferProgress.state == TransferState.SENDING ||
             transferProgress.state == TransferState.RECEIVING)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MeshNavy.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { transferProgress.progress },
                        modifier = Modifier.size(40.dp),
                        color = MeshBlue,
                        trackColor = MeshBlue.copy(alpha = 0.2f),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(transferProgress.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
