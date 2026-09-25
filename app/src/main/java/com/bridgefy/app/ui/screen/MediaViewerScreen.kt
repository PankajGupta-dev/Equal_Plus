package com.bridgefy.app.ui.screen

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bridgefy.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WhatsApp-style Fullscreen Media Viewer.
 * Features:
 * - Immersive dark fullscreen layout
 * - Interactive Image Viewer: Pinch-to-zoom & pan gestures
 * - Interactive Video Viewer: Full-featured Media3 ExoPlayer integration, Custom play/pause overlay, seek bar, time duration stamps
 * - Top action toolbar with mock Download/Share endpoints.
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(
    mediaUri: Uri,
    mediaType: String, // "image" or "video"
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    // Setup ExoPlayer if it is a video
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    if (mediaType == "video") {
        DisposableEffect(mediaUri) {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(mediaUri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        durationMs = player.duration
                    }
                }
            })

            exoPlayer = player

            onDispose {
                player.release()
                exoPlayer = null
            }
        }

        // Live seekbar updates
        LaunchedEffect(isPlaying) {
            while (isPlaying && exoPlayer != null) {
                currentPositionMs = exoPlayer?.currentPosition ?: 0L
                delay(200)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Immersive media render
        if (mediaType == "video" && exoPlayer != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false // Use custom elegant Compose controller overlay
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Video controller overlay
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Play/Pause Overlay controller
                    FilledIconButton(
                        onClick = {
                            isPlaying = !isPlaying
                            exoPlayer?.playWhenReady = isPlaying
                        },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Seekbar row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Slider(
                            value = currentPositionMs.toFloat(),
                            onValueChange = { newVal ->
                                currentPositionMs = newVal.toLong()
                                exoPlayer?.seekTo(currentPositionMs)
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                activeTrackColor = MeshBlueBright,
                                thumbColor = MeshBlueBright
                            )
                        )

                        Text(
                            text = formatTime(durationMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // IMAGE VIEWER with Pinch-To-Zoom and Pan gestures
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                offset += offsetChange
            }

            var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(mediaUri) {
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(mediaUri)?.use { stream ->
                            imageBitmap = BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        Log.e("MediaViewerScreen", "Error loading fullscreen image", e)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = state),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!.asImageBitmap(),
                        contentDescription = "Fullscreen Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                } else {
                    CircularProgressIndicator(color = MeshBlue)
                }
            }
        }

        // Top Toolbar navigation overlays
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Download icon endpoint — saves file to local device gallery using MediaStore
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val resolver = context.contentResolver
                                val extension = if (mediaType == "video") "mp4" else "jpg"
                                val mimeType = if (mediaType == "video") "video/mp4" else "image/jpeg"
                                val fileName = "BridgeFy_${System.currentTimeMillis()}.$extension"

                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        put(MediaStore.MediaColumns.RELATIVE_PATH, if (mediaType == "video") "Movies/BridgeFy" else "Pictures/BridgeFy")
                                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                                    }
                                }

                                val collection = if (mediaType == "video") {
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                } else {
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                }

                                withContext(Dispatchers.IO) {
                                    val uri = resolver.insert(collection, contentValues)
                                    if (uri != null) {
                                        resolver.openInputStream(mediaUri)?.use { input ->
                                            resolver.openOutputStream(uri)?.use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            contentValues.clear()
                                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                            resolver.update(uri, contentValues, null, null)
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        throw Exception("Failed to insert media record")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MediaViewerScreen", "Failed to save media to gallery", e)
                                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White
                    )
                }

                // Mock Share endpoint
                IconButton(
                    onClick = {
                        android.widget.Toast.makeText(context, "Mock Share initiated", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
