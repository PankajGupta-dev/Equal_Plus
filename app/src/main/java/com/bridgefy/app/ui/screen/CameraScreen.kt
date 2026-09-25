package com.bridgefy.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bridgefy.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onMediaCaptured: (Uri, String) -> Unit, // Uri and type ("image" or "video")
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }



    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        Log.d(TAG, "[CameraDebug] Permission result callback: camera=$cameraGranted, audio=$audioGranted")
        Log.d(TAG, "[CameraDebug] Permission granted = $cameraGranted")
        hasCameraPermission = cameraGranted
        hasAudioPermission = audioGranted
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "[CameraDebug] Camera icon clicked")
        if (!hasCameraPermission || !hasAudioPermission) {
            Log.d(TAG, "[CameraDebug] Requesting permission")
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        } else {
            Log.d(TAG, "[CameraDebug] Permission granted = true")
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MeshNavy),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera permission is required to use this feature.",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshBlue)
                ) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    CameraContent(
        context = context,
        lifecycleOwner = lifecycleOwner,
        hasAudio = hasAudioPermission,
        onMediaCaptured = onMediaCaptured,
        onBack = onBack
    )
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraContent(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    hasAudio: Boolean,
    onMediaCaptured: (Uri, String) -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(5f) }
    
    // CameraX UseCases
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().setFlashMode(flashMode).build() }
    
    // Video Capture setup
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD)) // SD quality is excellent for low-bandwidth mesh sharing
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    
    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableStateOf(0) }

    // Shutter button feedback animations
    val isShutterPressed = remember { mutableStateOf(false) }
    val shutterScale by animateFloatAsState(
        targetValue = if (isShutterPressed.value || isRecordingVideo) 1.25f else 1.0f,
        animationSpec = tween(200),
        label = "shutterScale"
    )

    // Load recent gallery thumbnail
    var galleryThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(Unit) {
        galleryThumbnailUri = getLastMediaThumbnail(context)
    }

    // Custom multiple picker launcher inside camera
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Send the first selected media for preview
            val uri = uris.first()
            val mime = context.contentResolver.getType(uri) ?: ""
            val type = if (mime.startsWith("video/")) "video" else "image"
            onMediaCaptured(uri, type)
        }
    }

    // Dynamic log for CameraX Initialization
    LaunchedEffect(Unit) {
        Log.d(TAG, "[CameraDebug] Initializing CameraX")
    }

    // Bind CameraX Lifecycle (non-blocking)
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                Log.d(TAG, "[CameraDebug] CameraProvider success")
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                
                val preview = Preview.Builder().build().also {
                    Log.d(TAG, "[CameraDebug] Preview attached")
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                Log.d(TAG, "[CameraDebug] Binding use cases")
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
                maxZoomRatio = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 5f
                Log.d(TAG, "[CameraDebug] Camera started successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "[CameraDebug] Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Dynamic Flash update
    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    // Video recording timer with auto-stop limits
    LaunchedEffect(isRecordingVideo) {
        if (isRecordingVideo) {
            recordingDurationSeconds = 0
            while (isRecordingVideo) {
                delay(1000)
                recordingDurationSeconds++
                if (recordingDurationSeconds >= 30) {
                    activeRecording?.stop()
                    activeRecording = null
                    isRecordingVideo = false
                    android.widget.Toast.makeText(context, "Maximum video duration reached (30s)", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full screen Viewfinder
        AndroidView(
            factory = {
                Log.d(TAG, "[CameraDebug] Preview attached")
                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                    }
                }
        )

        // Gradient black shadows for controls visibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )

        // Top Control Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Flash Mode Selector
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }
            ) {
                val icon = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                    else -> Icons.Default.FlashOff
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Flash",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Switch Camera Selector
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Recording duration indicator overlay (Top Center)
        if (isRecordingVideo) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp),
                color = Color.Red.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatDuration(recordingDurationSeconds),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Shutter & Bottom row controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery Picker Button
            var thumbnailBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(galleryThumbnailUri) {
                galleryThumbnailUri?.let { uri ->
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val options = android.graphics.BitmapFactory.Options().apply {
                                    inSampleSize = 4 // Downsample for performance
                                }
                                thumbnailBitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .clickable {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "Gallery",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // WhatsApp Shutter button (tap to capture photo, hold to record video)
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Tap to capture photo
                                isShutterPressed.value = true
                                coroutineScope.launch {
                                    takePhoto(context, imageCapture) { uri ->
                                        isShutterPressed.value = false
                                        onMediaCaptured(uri, "image")
                                    }
                                }
                            },
                            onLongPress = {
                                 // Long press to start video recording
                                 Log.d("VideoDebug", "[VideoDebug] Recording started")
                                 isRecordingVideo = true
                                 val outputFile = File(context.cacheDir, "REC_${System.currentTimeMillis()}.mp4")
                                 val outputOptions = FileOutputOptions.Builder(outputFile).build()
                                 
                                 @SuppressLint("MissingPermission")
                                 val recording = videoCapture.output
                                     .prepareRecording(context, outputOptions)
                                     .apply {
                                         if (hasAudio) withAudioEnabled()
                                     }
                                     .start(ContextCompat.getMainExecutor(context)) { event ->
                                         if (event is VideoRecordEvent.Status) {
                                             val bytes = event.recordingStats.numBytesRecorded
                                             if (bytes >= 15 * 1024 * 1024) { // 15 MB Size Limit
                                                 activeRecording?.stop()
                                                 activeRecording = null
                                                 isRecordingVideo = false
                                                 android.widget.Toast.makeText(context, "Maximum video size reached (15MB)", android.widget.Toast.LENGTH_LONG).show()
                                             }
                                         }
                                         if (event is VideoRecordEvent.Finalize) {
                                             isRecordingVideo = false
                                             if (!event.hasError()) {
                                                 val fileUri = Uri.fromFile(outputFile)
                                                 Log.d("VideoDebug", "[VideoDebug] Recording completed")
                                                 Log.d("VideoDebug", "[VideoDebug] File path = ${outputFile.absolutePath}")
                                                 Log.d("VideoDebug", "[VideoDebug] File exists = ${outputFile.exists()}")
                                                 Log.d("VideoDebug", "[VideoDebug] File size = ${String.format(java.util.Locale.US, "%.1f", outputFile.length() / (1024f * 1024f))}MB")
                                                 Log.d("VideoDebug", "[VideoDebug] MIME type = video/mp4")
                                                 onMediaCaptured(fileUri, "video")
                                             } else {
                                                 Log.e(TAG, "Video capture failed: ${event.error}")
                                             }
                                         }
                                     }
                                activeRecording = recording
                            },
                            onPress = {
                                try {
                                    isShutterPressed.value = true
                                    awaitRelease()
                                } finally {
                                    isShutterPressed.value = false
                                    if (isRecordingVideo) {
                                        activeRecording?.stop()
                                        activeRecording = null
                                        isRecordingVideo = false
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background Shutter Circle
                Box(
                    modifier = Modifier
                        .fillMaxSize(shutterScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .border(4.dp, Color.White, CircleShape)
                )

                // Shutter dot
                Box(
                    modifier = Modifier
                        .size(if (isRecordingVideo) 32.dp else 56.dp)
                        .clip(if (isRecordingVideo) RoundedCornerShape(8.dp) else CircleShape)
                        .background(if (isRecordingVideo) Color.Red else Color.White)
                )
            }

            // Simple Zoom ratio indicator toggle
            Surface(
                onClick = {
                    zoomRatio = if (zoomRatio == 1f) 2f else 1f
                    camera?.cameraControl?.setZoomRatio(zoomRatio)
                },
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${zoomRatio.toInt()}x",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onSuccess: (Uri) -> Unit
) {
    val photoFile = File(context.cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSuccess(Uri.fromFile(photoFile))
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }
        }
    )
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}

private fun getLastMediaThumbnail(context: Context): Uri? {
    try {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        val queryUri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val id = cursor.getLong(idColumn)
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch gallery thumbnail", e)
    }
    return null
}
