package com.bridgefy.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bridgefy.app.model.TransferProgress
import com.bridgefy.app.model.TransferState
import com.bridgefy.app.ui.theme.*
import java.io.File

/**
 * Inline image preview inside a chat bubble.
 *
 * - Loads thumbnail with downsampled BitmapFactory (memory efficient)
 * - Shows transfer progress overlay while sending/receiving
 * - Tap for future full-screen viewing
 */
@Composable
fun ImagePreviewBubble(
    filePath: String?,
    fileName: String?,
    isSentByMe: Boolean,
    transferProgress: TransferProgress?,
    modifier: Modifier = Modifier,
    onImageClick: (() -> Unit)? = null
) {
    val imageFile = filePath?.let { File(it) }
    val exists = imageFile?.exists() == true

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onImageClick != null) { onImageClick?.invoke() }
    ) {
        if (exists) {
            // Load downsampled bitmap for memory efficiency
            val bitmap = remember(filePath) {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(filePath, options)

                    // Calculate inSampleSize for ~300px wide thumbnail
                    val targetWidth = 300
                    var sampleSize = 1
                    if (options.outWidth > targetWidth) {
                        sampleSize = options.outWidth / targetWidth
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    BitmapFactory.decodeFile(filePath, decodeOptions)
                } catch (e: Exception) {
                    null
                }
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = fileName ?: "Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Bitmap decode failed
                ImagePlaceholder(fileName)
            }
        } else {
            // File not found or not yet received
            ImagePlaceholder(fileName)
        }

        // Transfer progress overlay
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

@Composable
private fun ImagePlaceholder(fileName: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(SurfaceCard),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = fileName ?: "Image",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
