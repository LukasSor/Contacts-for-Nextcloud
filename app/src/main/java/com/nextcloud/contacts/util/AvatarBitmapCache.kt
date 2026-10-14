package com.nextcloud.contacts.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nextcloud.contacts.data.local.ContactPhotoStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * List-sized avatars for the process. [peek] does not touch disk.
 * Decode on a background dispatcher.
 */
object AvatarBitmapCache {
    // ~1/8 of available memory, measured in KB of bitmap payloads.
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(1024, 8192)
    private val cache = object : LruCache<String, ImageBitmap>(maxKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            // ImageBitmap width*height*4 / 1024 ≈ KB for ARGB_8888
            return ((value.width * value.height * 4) / 1024).coerceAtLeast(1)
        }
    }

    /** Known-absent keys so scroll does not keep hitting disk for contacts without photos. */
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /**
     * Memory-only peek — never touches disk. Safe on the main thread during composition.
     */
    fun peek(contactId: Long, sizePx: Int): ImageBitmap? {
        return cache.get(key(contactId, sizePx))
    }

    /**
     * Decode [contactIds] into the cache on the calling thread (caller must use a
     * background dispatcher). Skips ids already cached or known-missing.
     */
    fun prefetch(photoStore: ContactPhotoStore, contactIds: Iterable<Long>, sizePx: Int) {
        for (contactId in contactIds) {
            val cacheKey = key(contactId, sizePx)
            if (cache.get(cacheKey) != null || missing.contains(cacheKey)) continue
            getOrDecode(photoStore, contactId, sizePx)
        }
    }

    fun getOrDecode(
        photoStore: ContactPhotoStore,
        contactId: Long,
        sizePx: Int,
    ): ImageBitmap? {
        val cacheKey = key(contactId, sizePx)
        cache.get(cacheKey)?.let { return it }
        if (missing.contains(cacheKey)) return null

        val file = photoStore.listImageFile(contactId)
        if (file == null) {
            missing.add(cacheKey)
            return null
        }
        val bitmap = decodeSampled(file, sizePx)
        if (bitmap == null) {
            missing.add(cacheKey)
            return null
        }
        val image = bitmap.asImageBitmap()
        cache.put(cacheKey, image)
        return image
    }

    fun getOrDecodeBytes(bytes: ByteArray, cacheKey: String, sizePx: Int): ImageBitmap? {
        cache.get(cacheKey)?.let { return it }
        val bitmap = decodeSampledBytes(bytes, sizePx) ?: return null
        val image = bitmap.asImageBitmap()
        cache.put(cacheKey, image)
        return image
    }

    /** Drop cached thumbs after a photo is written or deleted. */
    fun invalidate(contactId: Long) {
        val prefix = "$contactId@"
        synchronized(cache) {
            cache.snapshot().keys
                .filter { it.startsWith(prefix) }
                .forEach { cache.remove(it) }
        }
        missing.removeIf { it.startsWith(prefix) }
    }

    fun invalidateAll() {
        synchronized(cache) { cache.evictAll() }
        missing.clear()
    }

    private fun key(contactId: Long, sizePx: Int) = "$contactId@$sizePx"

    private fun decodeSampled(file: File, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, sizePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun decodeSampledBytes(bytes: ByteArray, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, sizePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) {
            sample *= 2
        }
        return sample
    }

    /** About one screen of rows. Splash waits on this set. */
    const val SPLASH_PREFETCH_COUNT = 24

    /** Warmed after splash. Does not block the first frame. */
    const val EXTENDED_PREFETCH_COUNT = 96

    /** How many ids to decode ahead of the viewport. */
    const val SCROLL_AHEAD_COUNT = 32

    /** Keep in step with the list-row avatar size. */
    const val LIST_AVATAR_DP = 40
}
