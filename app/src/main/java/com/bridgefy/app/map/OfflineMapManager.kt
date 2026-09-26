package com.bridgefy.app.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Manages 100% offline map tiles from MBTiles SQLite databases.
 * Runs an embedded loopback HTTP server (127.0.0.1:8765) serving raster and vector
 * tiles directly to MapLibre with ZERO external network calls and ZERO API keys.
 */
object OfflineMapManager {

    private const val TAG = "OfflineMapManager"
    const val SERVER_PORT = 8765
    const val DEFAULT_MBTILES_FILENAME = "offline_map.mbtiles"

    private val scope = CoroutineScope(Dispatchers.IO)
    private val threadPool = Executors.newFixedThreadPool(4)

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false

    @Volatile
    private var activeDb: SQLiteDatabase? = null

    private val _activeMbtilesName = MutableStateFlow<String?>(null)
    val activeMbtilesName: StateFlow<String?> = _activeMbtilesName.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow<String?>(null)
    val importProgress: StateFlow<String?> = _importProgress.asStateFlow()

    var activeFormat: String = "pbf"
        private set
    var activeCenterLat: Double? = null
        private set
    var activeCenterLon: Double? = null
        private set
    var activeCenterZoom: Double? = null
        private set
    var activeMinZoom: Int = 0
        private set
    var activeMaxZoom: Int = 14
        private set

    /**
     * Initializes the offline tile server and automatically loads any existing MBTiles file.
     */
    fun init(context: Context) {
        startServer()
        scope.launch {
            findAndLoadDefaultMbtiles(context.applicationContext)
        }
    }

    /**
     * Starts the embedded local loopback tile server on 127.0.0.1:8765
     */
    @Synchronized
    private fun startServer() {
        if (isRunning) return
        try {
            val bindAddress = InetAddress.getByName("127.0.0.1")
            serverSocket = ServerSocket(SERVER_PORT, 50, bindAddress)
            isRunning = true
            Log.i(TAG, "Embedded tile server started on http://127.0.0.1:$SERVER_PORT")

            scope.launch {
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        threadPool.execute {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "Socket accept error: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local tile server: ${e.message}", e)
        }
    }

    /**
     * Handles incoming HTTP requests for /tiles/{z}/{x}/{y} and /style.json
     */
    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 3000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val reader = input.bufferedReader()
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                sendResponse(output, 400, "Bad Request", null, null)
                return
            }

            val rawUri = parts[1]
            val cleanPath = rawUri.substringBefore("?").removePrefix("/")

            if (cleanPath == "style.json" || cleanPath == "style") {
                val styleBytes = getStyleJson().toByteArray(Charsets.UTF_8)
                sendResponse(output, 200, "OK", styleBytes, "application/json; charset=utf-8")
                return
            }

            val pathSegments = cleanPath.split("/")

            if (pathSegments.size >= 4 && pathSegments[0] == "tiles") {
                val z = pathSegments[1].toIntOrNull()
                val x = pathSegments[2].toIntOrNull()
                val yStr = pathSegments[3].substringBefore(".")
                val y = yStr.toIntOrNull()

                if (z != null && x != null && y != null) {
                    val tileData = getTile(z, x, y)
                    if (tileData != null && tileData.isNotEmpty()) {
                        val contentType = detectContentType(tileData)
                        val isGzip = tileData.size > 2 && tileData[0] == 0x1F.toByte() && tileData[1] == 0x8B.toByte()
                        sendResponse(output, 200, "OK", tileData, contentType, isGzip)
                        return
                    }
                }
            }

            // Tile not found in active MBTiles database (or no MBTiles loaded yet)
            sendResponse(output, 404, "Not Found", null, null)
        } catch (_: Exception) {
            // Socket closed or timeout
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun sendResponse(
        output: java.io.OutputStream,
        statusCode: Int,
        statusText: String,
        body: ByteArray?,
        contentType: String?,
        isGzip: Boolean = false
    ) {
        val bodySize = body?.size ?: 0
        val headers = StringBuilder()
        headers.append("HTTP/1.1 $statusCode $statusText\r\n")
        headers.append("Content-Length: $bodySize\r\n")
        headers.append("Access-Control-Allow-Origin: *\r\n")
        headers.append("Connection: close\r\n")
        if (contentType != null) {
            headers.append("Content-Type: $contentType\r\n")
        }
        if (isGzip) {
            headers.append("Content-Encoding: gzip\r\n")
        }
        headers.append("\r\n")

        output.write(headers.toString().toByteArray(Charsets.US_ASCII))
        if (body != null && body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    /**
     * Determines whether the byte array is PNG, JPEG, WEBP or PBF vector tile.
     */
    private fun detectContentType(data: ByteArray): String {
        if (data.size >= 8) {
            // PNG signature: 89 50 4E 47 0D 0A 1A 0A
            if (data[0] == 0x89.toByte() && data[1] == 0x50.toByte() && data[2] == 0x4E.toByte()) {
                return "image/png"
            }
            // JPEG signature: FF D8
            if (data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) {
                return "image/jpeg"
            }
            // WEBP signature: RIFF....WEBP
            if (data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() && data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()) {
                return "image/webp"
            }
        }
        return "application/vnd.mapbox-vector-tile"
    }

    /**
     * Queries the active MBTiles SQLite database.
     * Handles both standard TMS inverted Y coordinates (standard MBTiles) and XYZ coordinates.
     */
    fun getTile(z: Int, x: Int, y: Int): ByteArray? {
        val db = activeDb ?: return null
        if (!db.isOpen) return null

        try {
            // Standard MBTiles uses TMS coordinates: tms_y = (1 << z) - 1 - y
            val tmsY = (1 shl z) - 1 - y
            var result = queryTile(db, z, x, tmsY)
            if (result == null) {
                // Fallback for non-standard XYZ MBTiles exports
                result = queryTile(db, z, x, y)
            }
            return result
        } catch (e: Exception) {
            Log.w(TAG, "Error querying tile ($z/$x/$y): ${e.message}")
            return null
        }
    }

    private fun queryTile(db: SQLiteDatabase, z: Int, x: Int, y: Int): ByteArray? {
        val cursor = db.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
            arrayOf(z.toString(), x.toString(), y.toString())
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getBlob(0)
            }
        }
        return null
    }

    /**
     * Dynamically generates the MapLibre style JSON for the currently loaded MBTiles.
     * ZERO external network calls and ZERO API keys required.
     */
    fun getStyleJson(): String {
        return if (activeFormat == "pbf" || activeFormat == "mvt") {
            """
            {
              "version": 8,
              "name": "BridgeFy Offline Vector Map",
              "sources": {
                "local-vector": {
                  "type": "vector",
                  "tiles": [
                    "http://127.0.0.1:$SERVER_PORT/tiles/{z}/{x}/{y}"
                  ],
                  "minzoom": $activeMinZoom,
                  "maxzoom": $activeMaxZoom
                }
              },
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": {
                    "background-color": "#0A1628"
                  }
                },
                {
                  "id": "landuse",
                  "type": "fill",
                  "source": "local-vector",
                  "source-layer": "landuse",
                  "paint": {
                    "fill-color": "#0e1f30"
                  }
                },
                {
                  "id": "water",
                  "type": "fill",
                  "source": "local-vector",
                  "source-layer": "water",
                  "paint": {
                    "fill-color": "#0c2847"
                  }
                },
                {
                  "id": "waterway",
                  "type": "line",
                  "source": "local-vector",
                  "source-layer": "waterway",
                  "paint": {
                    "line-color": "#113860",
                    "line-width": 1.5
                  }
                },
                {
                  "id": "building",
                  "type": "fill",
                  "source": "local-vector",
                  "source-layer": "building",
                  "paint": {
                    "fill-color": "#122035",
                    "fill-outline-color": "#1c3050"
                  }
                },
                {
                  "id": "road",
                  "type": "line",
                  "source": "local-vector",
                  "source-layer": "transportation",
                  "paint": {
                    "line-color": "#1e3d60",
                    "line-width": 1.5
                  }
                },
                {
                  "id": "boundary",
                  "type": "line",
                  "source": "local-vector",
                  "source-layer": "boundary",
                  "paint": {
                    "line-color": "#3b82f6",
                    "line-width": 1.0,
                    "line-dasharray": [2, 2]
                  }
                }
              ]
            }
            """.trimIndent()
        } else {
            """
            {
              "version": 8,
              "name": "BridgeFy Offline Raster Map",
              "sources": {
                "local-tiles": {
                  "type": "raster",
                  "tiles": [
                    "http://127.0.0.1:$SERVER_PORT/tiles/{z}/{x}/{y}"
                  ],
                  "tileSize": 256,
                  "minzoom": $activeMinZoom,
                  "maxzoom": $activeMaxZoom
                }
              },
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": {
                    "background-color": "#0A1628"
                  }
                },
                {
                  "id": "local-raster-tiles",
                  "type": "raster",
                  "source": "local-tiles",
                  "paint": {
                    "raster-opacity": 1.0
                  }
                }
              ]
            }
            """.trimIndent()
        }
    }

    /**
     * Scans storage and assets for an existing MBTiles file and loads it.
     */
    fun findAndLoadDefaultMbtiles(context: Context): Boolean {
        val candidateNames = listOf("world.mbtiles", DEFAULT_MBTILES_FILENAME, "map.mbtiles")

        // 0. Check APK assets for bundled MBTiles
        try {
            val assetList = context.assets.list("") ?: emptyArray()
            val mbtilesAsset = candidateNames.firstOrNull { it in assetList }
                ?: assetList.firstOrNull { it.endsWith(".mbtiles", ignoreCase = true) }

            if (mbtilesAsset != null) {
                val targetFile = File(context.filesDir, mbtilesAsset)
                var needsCopy = !targetFile.exists() || targetFile.length() == 0L
                if (!needsCopy) {
                    val assetFd = try { context.assets.openFd(mbtilesAsset) } catch (_: Exception) { null }
                    if (assetFd != null) {
                        if (targetFile.length() != assetFd.length) {
                            needsCopy = true
                        }
                        assetFd.close()
                    }
                }

                if (needsCopy) {
                    Log.i(TAG, "Extracting bundled asset $mbtilesAsset to ${targetFile.absolutePath}...")
                    _importProgress.value = "Extracting offline map..."
                    context.assets.open(mbtilesAsset).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    _importProgress.value = null
                    Log.i(TAG, "Extracted $mbtilesAsset successfully (${targetFile.length()} bytes)")
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    if (loadMbtilesFile(targetFile)) return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking assets for mbtiles: ${e.message}")
        }

        // 1. Check internal app files directory
        for (name in candidateNames) {
            val internalFile = File(context.filesDir, name)
            if (internalFile.exists() && internalFile.length() > 0) {
                if (loadMbtilesFile(internalFile)) return true
            }
        }

        // 2. Check app external files directory
        val extDir = context.getExternalFilesDir(null)
        if (extDir != null) {
            for (name in candidateNames) {
                val extFile = File(extDir, name)
                if (extFile.exists() && extFile.length() > 0) {
                    if (loadMbtilesFile(extFile)) return true
                }
            }
        }

        // 3. Check public Downloads folder
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists()) {
                for (name in candidateNames) {
                    val dlFile = File(downloadDir, name)
                    if (dlFile.exists() && dlFile.length() > 0) {
                        if (loadMbtilesFile(dlFile)) return true
                    }
                }
            }
        } catch (_: Exception) {}

        Log.d(TAG, "No pre-existing MBTiles file found. Ready in tactical offline mode.")
        return false
    }

    /**
     * Opens an MBTiles SQLite file and sets it as the active database.
     */
    @Synchronized
    fun loadMbtilesFile(file: File): Boolean {
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "Cannot read file: ${file.absolutePath}")
            return false
        }

        try {
            val oldDb = activeDb
            val newDb = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            // Verify tiles table or view exists
            val cursor = newDb.rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type IN ('table', 'view') AND name = 'tiles'",
                null
            )
            val isValid = cursor.use {
                it.moveToFirst() && it.getInt(0) > 0
            }

            if (!isValid) {
                Log.e(TAG, "File ${file.name} does not contain standard MBTiles 'tiles' table/view")
                newDb.close()
                return false
            }

            activeDb = newDb
            oldDb?.close()
            _activeMbtilesName.value = file.name

            // Read metadata to detect format (pbf vector vs png/jpg raster) and center
            try {
                val metaCursor = newDb.rawQuery("SELECT name, value FROM metadata", null)
                metaCursor.use {
                    while (it.moveToNext()) {
                        val name = it.getString(0)
                        val value = it.getString(1)
                        when (name.lowercase()) {
                            "format" -> activeFormat = value.lowercase()
                            "minzoom" -> activeMinZoom = value.toIntOrNull() ?: 0
                            "maxzoom" -> activeMaxZoom = value.toIntOrNull() ?: 14
                            "center" -> {
                                val parts = value.split(",")
                                if (parts.size >= 2) {
                                    activeCenterLon = parts[0].toDoubleOrNull()
                                    activeCenterLat = parts[1].toDoubleOrNull()
                                    if (parts.size >= 3) {
                                        activeCenterZoom = parts[2].toDoubleOrNull()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading metadata: ${e.message}")
            }

            Log.i(TAG, "Successfully loaded MBTiles database: ${file.name} (Format: $activeFormat, ${file.length() / (1024 * 1024)} MB)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open MBTiles database: ${e.message}", e)
            return false
        }
    }

    /**
     * Imports an MBTiles file from a user-selected URI (from the system file picker).
     * Copies it to the app's internal filesDir and loads it immediately.
     */
    suspend fun importMbtilesFromUri(context: Context, uri: Uri, fileName: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            _isImporting.value = true
            _importProgress.value = "Importing offline map..."
            try {
                val targetFile = File(context.filesDir, DEFAULT_MBTILES_FILENAME)
                val tempFile = File(context.filesDir, "$DEFAULT_MBTILES_FILENAME.tmp")

                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _importProgress.value = "Failed to read selected file"
                    return@withContext false
                }

                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            _importProgress.value = "Importing: ${totalRead / (1024 * 1024)} MB"
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)

                val success = loadMbtilesFile(targetFile)
                if (success) {
                    _activeMbtilesName.value = fileName ?: targetFile.name
                    _importProgress.value = "Offline map loaded successfully!"
                } else {
                    _importProgress.value = "Invalid MBTiles file format"
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "Error importing MBTiles from URI: ${e.message}", e)
                _importProgress.value = "Import failed: ${e.localizedMessage}"
                false
            } finally {
                _isImporting.value = false
            }
        }
}
