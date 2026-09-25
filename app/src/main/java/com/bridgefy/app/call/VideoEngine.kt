package com.bridgefy.app.call

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoEngine(private val context: Context) {
    companion object {
        private const val TAG = "VideoEngine"
        private const val TARGET_WIDTH = 320
        private const val TARGET_HEIGHT = 240
        private const val JPEG_QUALITY = 60
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var playbackJob: Job? = null
    private var lastFrameTime = 0L

    private val _remoteFrame = MutableStateFlow<Bitmap?>(null)
    val remoteFrame: StateFlow<Bitmap?> = _remoteFrame.asStateFlow()

    @Volatile
    var isVideoEnabled: Boolean = true

    fun startCapture(
        lifecycleOwner: LifecycleOwner,
        socket: Socket,
        isFrontCamera: Boolean = true,
        onPreviewReady: (Preview) -> Unit = {}
    ) {
        stopCapture()
        Log.d(TAG, "Starting VideoEngine capture")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                // Local Preview
                val preview = Preview.Builder().build()
                onPreviewReady(preview)

                // Image Analysis for sending
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val outputStream = DataOutputStream(socket.getOutputStream())

                lastFrameTime = 0L
                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        val now = System.currentTimeMillis()
                        if (isVideoEnabled && now - lastFrameTime >= 66L) { // ~15 FPS
                            lastFrameTime = now
                            val jpegBytes = imageProxy.toJpegByteArray(JPEG_QUALITY)
                            if (jpegBytes != null) {
                                synchronized(outputStream) {
                                    outputStream.writeInt(jpegBytes.size)
                                    outputStream.write(jpegBytes)
                                    outputStream.flush()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending video frame: ${e.message}")
                    } finally {
                        imageProxy.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                Log.d(TAG, "Bound CameraX preview and analysis successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build CameraX configuration", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCapture() {
        Log.d(TAG, "Stopping VideoEngine capture")
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind camera provider", e)
        }
        cameraProvider = null
    }

    fun startPlayback(socket: Socket) {
        stopPlayback()
        Log.d(TAG, "Starting VideoEngine playback")
        
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                val inputStream = DataInputStream(socket.getInputStream())
                while (isActive) {
                    val length = inputStream.readInt()
                    if (length <= 0 || length > 1024 * 1024) {
                        Log.w(TAG, "Invalid video frame length: $length")
                        continue
                    }
                    val buffer = ByteArray(length)
                    inputStream.readFully(buffer)

                    val bitmap = BitmapFactory.decodeByteArray(buffer, 0, length)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            val old = _remoteFrame.value
                            _remoteFrame.value = bitmap
                            old?.recycle() // Free native memory immediately!
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video playback stream disconnected: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    _remoteFrame.value = null
                }
            }
        }
    }

    fun stopPlayback() {
        Log.d(TAG, "Stopping VideoEngine playback")
        playbackJob?.cancel()
        playbackJob = null
        _remoteFrame.value = null
    }

    fun stop() {
        stopCapture()
        stopPlayback()
    }

    private fun ImageProxy.toJpegByteArray(quality: Int): ByteArray? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)

        val pixelStride = planes[1].pixelStride
        val rowStride = planes[1].rowStride

        // Fast copy if strides match normal interleaved UV layout
        if (pixelStride == 2 && planes[2].pixelStride == 2 && rowStride == planes[2].rowStride) {
            vBuffer.get(nv21, ySize, vSize)
        } else {
            // Strided/Planar custom UV assembly
            var pos = ySize
            val w = width
            val h = height
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    val vuIdx = row * rowStride + col * pixelStride
                    if (vuIdx < vBuffer.remaining()) {
                        nv21[pos++] = vBuffer.get(vuIdx) // V
                    }
                    if (vuIdx < uBuffer.remaining()) {
                        nv21[pos++] = uBuffer.get(vuIdx) // U
                    }
                }
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, this.width, this.height), quality, out)
        val rawBytes = out.toByteArray()

        try {
            val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
            
            val matrix = Matrix()
            // Downscale to target width/height
            val scaleX = TARGET_WIDTH.toFloat() / original.width
            val scaleY = TARGET_HEIGHT.toFloat() / original.height
            matrix.postScale(scaleX, scaleY)
            
            // Rotate according to sensor rotation degrees
            val rotation = this.imageInfo.rotationDegrees
            if (rotation != 0) {
                matrix.postRotate(rotation.toFloat())
            }
            
            val processed = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            val outProcessed = ByteArrayOutputStream()
            processed.compress(Bitmap.CompressFormat.JPEG, quality, outProcessed)
            
            original.recycle()
            if (processed != original) {
                processed.recycle()
            }
            
            return outProcessed.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate and scale bitmap: ${e.message}")
            return rawBytes
        }
    }
}
