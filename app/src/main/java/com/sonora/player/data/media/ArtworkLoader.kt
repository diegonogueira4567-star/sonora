package com.sonora.player.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Loads album art embedded in audio files (or MediaStore thumbnails) with a memory cache.
 * No network access and no copies of the audio files are made.
 */
class ArtworkLoader(private val context: Context, private val metadataReader: MetadataReader) {

    private val cache = object : LruCache<String, Bitmap>(cacheBytes(context)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val permits = Semaphore(4)

    fun cached(uri: String, sizePx: Int): Bitmap? = cache.get(key(uri, sizePx))

    fun isKnownMissing(uri: String): Boolean = uri in missing

    suspend fun load(uri: String, sizePx: Int): Bitmap? {
        cache.get(key(uri, sizePx))?.let { return it }
        if (uri in missing) return null
        return withContext(Dispatchers.IO) { permits.withPermit { loadBlocking(uri, sizePx) } }
    }

    /** Synchronous variant for the notification/widget bitmap loaders (already off the main thread). */
    fun loadBlocking(uri: String, sizePx: Int): Bitmap? {
        val k = key(uri, sizePx)
        cache.get(k)?.let { return it }
        if (uri in missing) return null
        val bitmap = runCatching { decode(Uri.parse(uri), bucket(sizePx)) }.getOrNull()
        if (bitmap == null) missing += uri else cache.put(k, bitmap)
        return bitmap
    }

    fun invalidate() {
        missing.clear()
        cache.evictAll()
    }

    private fun decode(uri: Uri, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.authority == "media") {
            runCatching { return context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }
        }
        val bytes = metadataReader.embeddedPicture(uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Requests are grouped in three sizes so one cover is decoded at most three times. */
    private fun key(uri: String, sizePx: Int) = "${bucket(sizePx)}@$uri"

    private companion object {
        fun bucket(sizePx: Int): Int = when {
            sizePx <= 200 -> 200
            sizePx <= 400 -> 400
            else -> 800
        }

        /** 1/8 of the heap (max 48 MB), 1/16 on low-RAM devices. */
        fun cacheBytes(context: Context): Int {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val share = if (am.isLowRamDevice || am.memoryClass <= 128) 16 else 8
            return (Runtime.getRuntime().maxMemory() / share).coerceAtMost(48L * 1024 * 1024).toInt()
        }
    }
}
