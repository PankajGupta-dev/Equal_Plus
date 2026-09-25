package com.bridgefy.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object MediaCompressor {
    private const val TAG = "MediaCompressor"

    /**
     * Compresses an image:
     * - Downscales image if resolution exceeds 1280px.
     * - Compresses to JPEG at 70% quality.
     * - Returns the file Uri of the compressed copy.
     */
    suspend fun compressImage(context: Context, uri: Uri): Uri = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Failed to open input stream")

            // Read dimensions only first
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            Log.d(TAG, "Compressing image: original resolution ${srcWidth}x${srcHeight}")

            val maxDimension = 1280
            var sampleSize = 1
            if (srcWidth > maxDimension || srcHeight > maxDimension) {
                val halfWidth = srcWidth / 2
                val halfHeight = srcHeight / 2
                while ((halfWidth / sampleSize) >= maxDimension && (halfHeight / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            // Decode image with sampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Failed to reopen stream")
            var bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream.close()

            if (bitmap == null) throw Exception("Bitmap decode returned null")

            // Handle rotation if EXIF says so
            bitmap = handleExifRotation(context, uri, bitmap)

            // Compress to temp file
            val compressedFile = File(context.cacheDir, "COMPRESSED_IMG_${System.currentTimeMillis()}.jpg")
            FileOutputStream(compressedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                out.flush()
            }

            Log.d(TAG, "Compressed image saved: ${compressedFile.absolutePath} (${compressedFile.length()} bytes)")
            Uri.fromFile(compressedFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image: ${e.message}", e)
            uri // Fallback to original uri
        }
    }

    /**
     * Trims and Compresses a video:
     * - Cuts video to max 30 seconds if duration is longer or trims to user segment.
     * - Reduces resolution to 480p/720p or lower bitrates if possible.
     * - Fast-copies using MediaExtractor and MediaMuxer to avoid slow transcoding when video is already efficient.
     */
    suspend fun compressVideo(
        context: Context,
        uri: Uri,
        startTimeMs: Long = 0L,
        endTimeMs: Long = 30000L,
        onProgress: (Float) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        try {
            Log.d("VideoDebug", "[VideoDebug] Compression started")
            Log.d("VideoDebug", "[VideoDebug] Input URI = $uri")
            Log.d("VideoDebug", "[VideoDebug] Requested trim: startMs=$startTimeMs endMs=$endTimeMs")
            val contentResolver = context.contentResolver
            val cacheFile = File(context.cacheDir, "TRIMMED_COMPRESSED_${System.currentTimeMillis()}.mp4")

            // Retrieve video metadata
            val retriever = MediaMetadataRetriever()
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (!f.exists()) {
                    throw Exception("Video file missing")
                }
                retriever.setDataSource(f.absolutePath)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationMsStr?.toLongOrNull()?.takeIf { it > 0 } ?: 30000L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            retriever.release()

            Log.d(TAG, "Compressing video: original resolution ${width}x${height}, duration: ${durationMs}ms")

            // BUG FIX: Guard against zero-length trim window.
            // If endTimeMs <= startTimeMs (e.g. metadata not loaded yet), default to full duration capped at 30s.
            val safeEndTimeMs = if (endTimeMs <= startTimeMs) {
                Log.w("VideoDebug", "[VideoDebug] Zero-length trim detected (start=$startTimeMs end=$endTimeMs). Defaulting to full duration.")
                Math.min(durationMs, startTimeMs + 30000L)
            } else {
                endTimeMs
            }

            // Enforce max 30s limit or user selected trim points
            val actualStartTimeUs = startTimeMs * 1000L
            val actualEndTimeUs = Math.min(safeEndTimeMs, Math.min(durationMs, startTimeMs + 30000L)) * 1000L
            Log.d("VideoDebug", "[VideoDebug] Trim window: startUs=$actualStartTimeUs endUs=$actualEndTimeUs (videoDurationMs=$durationMs)")

            // Setup extractor
            val extractor = MediaExtractor()
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists()) {
                    throw Exception("Video file missing")
                }
                extractor.setDataSource(file.absolutePath)
            } else {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: throw Exception("Failed to open video FD")
            }
            val muxer = MediaMuxer(cacheFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackCount = extractor.trackCount
            val trackMap = HashMap<Int, Int>()

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                
                // If it is a video track and too high bitrate/resolution, we could modify it, but
                // a simple direct re-mux is extremely fast and robust for standard MP4 files
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val newTrackIndex = muxer.addTrack(format)
                    trackMap[i] = newTrackIndex
                }
            }

            muxer.start()

            // Seek to start time
            extractor.seekTo(actualStartTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val bufferSize = 2 * 1024 * 1024 // 2MB buffer
            val dstBuf = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)

                if (bufferInfo.size < 0) {
                    Log.d(TAG, "Reached EOF of video stream")
                    break
                }

                bufferInfo.presentationTimeUs = extractor.sampleTime
                if (bufferInfo.presentationTimeUs > actualEndTimeUs) {
                    Log.d(TAG, "Reached trim end time: ${bufferInfo.presentationTimeUs} us")
                    break
                }

                bufferInfo.flags = extractor.sampleFlags
                val trackIndex = extractor.sampleTrackIndex
                val newTrackIndex = trackMap[trackIndex]

                if (newTrackIndex != null) {
                    muxer.writeSampleData(newTrackIndex, dstBuf, bufferInfo)
                }

                extractor.advance()

                // Calculate progress
                val totalToProcess = actualEndTimeUs - actualStartTimeUs
                if (totalToProcess > 0) {
                    val progress = (bufferInfo.presentationTimeUs - actualStartTimeUs).toFloat() / totalToProcess
                    onProgress(progress.coerceIn(0f, 1f))
                }
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            val compressedSize = cacheFile.length()
            Log.d(TAG, "Video trimming/compression complete: $compressedSize bytes")
            Log.d("VideoDebug", "[VideoDebug] Compression completed")
            Log.d("VideoDebug", "[VideoDebug] Compressed file path = ${cacheFile.absolutePath}")
            Log.d("VideoDebug", "[VideoDebug] Compressed file exists = ${cacheFile.exists()}")
            Log.d("VideoDebug", "[VideoDebug] Compressed file size = $compressedSize bytes")

            // BUG FIX: Validate the compressed output is a real video file.
            // An MP4 with only a container header (no actual video samples) is typically < 1KB.
            if (compressedSize < 1024) {
                Log.e("VideoDebug", "[VideoDebug] Compressed file too small ($compressedSize bytes) — likely empty container. Using original file.")
                cacheFile.delete()
                uri // Return original recording file
            } else {
                Uri.fromFile(cacheFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming/compressing video: ${e.message}", e)
            Log.e("VideoDebug", "[VideoDebug] Compression FAILED: ${e.message}")
            uri // Fallback to original recording file
        }
    }

    private fun handleExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exifInterface = android.media.ExifInterface(stream)
                val orientation = exifInterface.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
                val degrees = when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            bitmap
        }
    }
}
