package com.example.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class MapTileType(val lyrsCode: String, val label: String) {
    GOOGLE_ROADMAP("m", "Google Maps Standar"),
    GOOGLE_SATELLITE("y", "Google Maps Satelit"),
    GOOGLE_TERRAIN("p", "Google Maps Medan")
}

data class TileRenderData(
    val bitmap: ImageBitmap,
    val srcOffset: IntOffset,
    val srcSize: IntSize
)

class TileManager(
    private val scope: CoroutineScope,
    context: Context? = null
) {

    private val client: OkHttpClient = run {
        val builder = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (context != null) {
            try {
                val cacheDir = File(context.cacheDir, "google_map_tiles")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                builder.cache(Cache(cacheDir, 80L * 1024 * 1024)) // 80 MB disk cache
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        builder.build()
    }

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 5 // Cache up to 20% of heap for ultra-smooth zoom

    private val memoryCache = object : LruCache<String, ImageBitmap>(cacheSize) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4) / 1024
        }
    }

    private val pendingKeys = mutableSetOf<String>()
    private val concurrencySemaphore = Semaphore(16) // High concurrency for fast downloads

    val tileRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    private var batchNotificationJob: Job? = null

    var currentTileType = MapTileType.GOOGLE_ROADMAP
        set(value) {
            if (field != value) {
                field = value
                notifyBatchUpdate()
            }
        }

    fun getCachedTile(zoom: Int, x: Int, y: Int): ImageBitmap? {
        val key = "${currentTileType.lyrsCode}/$zoom/$x/$y"
        return memoryCache.get(key)
    }

    /**
     * Hierarchical Tile Retrieval for Zero-Delay Zoom:
     * If the exact tile at [zoom] is not ready, instantly scale the parent tile from [zoom - 1]
     * or [zoom - 2] so the user NEVER sees blank gray spaces during zoom in/out!
     */
    fun getTileWithFallback(zoom: Int, x: Int, y: Int): TileRenderData? {
        // 1. Exact match
        val exact = getCachedTile(zoom, x, y)
        if (exact != null) {
            return TileRenderData(exact, IntOffset.Zero, IntSize(exact.width, exact.height))
        }

        // 2. Parent fallback (zoom - 1)
        if (zoom > 1) {
            val parentZoom = zoom - 1
            val parentX = x shr 1
            val parentY = y shr 1
            val parent = getCachedTile(parentZoom, parentX, parentY)
            if (parent != null) {
                val qX = x and 1
                val qY = y and 1
                val halfW = parent.width / 2
                val halfH = parent.height / 2
                return TileRenderData(
                    bitmap = parent,
                    srcOffset = IntOffset(qX * halfW, qY * halfH),
                    srcSize = IntSize(halfW, halfH)
                )
            }
        }

        // 3. Grandparent fallback (zoom - 2)
        if (zoom > 2) {
            val gZoom = zoom - 2
            val gX = x shr 2
            val gY = y shr 2
            val gParent = getCachedTile(gZoom, gX, gY)
            if (gParent != null) {
                val qX = x and 3
                val qY = y and 3
                val quarterW = gParent.width / 4
                val quarterH = gParent.height / 4
                return TileRenderData(
                    bitmap = gParent,
                    srcOffset = IntOffset(qX * quarterW, qY * quarterH),
                    srcSize = IntSize(quarterW, quarterH)
                )
            }
        }

        return null
    }

    fun requestTiles(tiles: List<Triple<Int, Int, Int>>) {
        val toFetch = mutableListOf<Triple<Int, Int, Int>>()
        synchronized(pendingKeys) {
            for (tile in tiles) {
                val key = "${currentTileType.lyrsCode}/${tile.first}/${tile.second}/${tile.third}"
                if (memoryCache.get(key) == null && !pendingKeys.contains(key)) {
                    pendingKeys.add(key)
                    toFetch.add(tile)
                }
            }
        }

        if (toFetch.isEmpty()) return

        for (tile in toFetch) {
            val zoom = tile.first
            val x = tile.second
            val y = tile.third
            val key = "${currentTileType.lyrsCode}/$zoom/$x/$y"

            scope.launch(Dispatchers.IO) {
                val success = concurrencySemaphore.withPermit {
                    fetchTileNetwork(zoom, x, y, key)
                }
                if (success) {
                    notifyBatchUpdate()
                }
            }
        }
    }

    private fun notifyBatchUpdate() {
        if (batchNotificationJob?.isActive == true) return
        batchNotificationJob = scope.launch {
            delay(16) // Fast 16ms refresh (60fps)
            tileRefreshTrigger.tryEmit(Unit)
        }
    }

    private fun fetchTileNetwork(zoom: Int, x: Int, y: Int, key: String): Boolean {
        try {
            // Authentic Google Maps tile server URL with scale=2 for retina HD clarity
            val sub = ((x + y) % 4).coerceIn(0, 3)
            val lyrs = currentTileType.lyrsCode
            val googleUrl = "https://mt$sub.google.com/vt/lyrs=$lyrs&x=$x&y=$y&z=$zoom&hl=id&scale=2"

            val request = Request.Builder()
                .url(googleUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            memoryCache.put(key, bitmap.asImageBitmap())
                            return true
                        }
                    }
                }
            }

            // Fallback: OpenStreetMap if Google tile fails
            val osmUrl = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
            val osmReq = Request.Builder()
                .url(osmUrl)
                .header("User-Agent", "FakeGpsApp/1.4 (Android Map Client)")
                .build()

            client.newCall(osmReq).execute().use { osmResp ->
                if (osmResp.isSuccessful) {
                    val bytes = osmResp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            memoryCache.put(key, bitmap.asImageBitmap())
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("TileManager", "Failed to load tile $key: ${e.message}")
        } finally {
            synchronized(pendingKeys) {
                pendingKeys.remove(key)
            }
        }
        return false
    }
}
