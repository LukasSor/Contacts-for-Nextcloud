package com.nextcloud.contacts.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nextcloud.contacts.util.AvatarBitmapCache
import java.io.File
import java.io.FileOutputStream

class ContactPhotoStore(context: Context) {
    private val directory = File(context.filesDir, "contact_photos").apply { mkdirs() }

    /** Full-resolution photo used by detail / vCard sync. */
    fun photoFile(contactId: Long): File = File(directory, "$contactId.jpg")

    /** Pre-sized list thumbnail (~[LIST_THUMB_PX] px). Prefer this for LazyColumn rows. */
    fun thumbFile(contactId: Long): File = File(directory, "${contactId}_t.jpg")

    fun hasPhoto(contactId: Long): Boolean {
        val file = photoFile(contactId)
        return file.exists() && file.length() > 0L
    }

    fun hasThumb(contactId: Long): Boolean {
        val file = thumbFile(contactId)
        return file.exists() && file.length() > 0L
    }

    /**
     * Best on-disk file for a list avatar. Creates a thumb from the full photo when missing
     * (safe to call from a background thread).
     */
    fun listImageFile(contactId: Long): File? {
        val thumb = thumbFile(contactId)
        if (thumb.exists() && thumb.length() > 0L) return thumb
        val full = photoFile(contactId)
        if (!full.exists() || full.length() == 0L) return null
        return if (writeThumbFromFull(full, thumb)) thumb else full
    }

    fun readPhoto(contactId: Long): ByteArray? {
        val file = photoFile(contactId)
        return if (file.exists()) file.readBytes() else null
    }

    fun savePhoto(contactId: Long, bytes: ByteArray) {
        photoFile(contactId).writeBytes(bytes)
        writeThumbFromBytes(bytes, thumbFile(contactId))
        AvatarBitmapCache.invalidate(contactId)
    }

    fun deletePhoto(contactId: Long) {
        photoFile(contactId).delete()
        thumbFile(contactId).delete()
        AvatarBitmapCache.invalidate(contactId)
    }

    fun clearAll() {
        directory.listFiles()?.forEach { it.delete() }
        AvatarBitmapCache.invalidateAll()
    }

    private fun writeThumbFromBytes(bytes: ByteArray, dest: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, LIST_THUMB_PX)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
        return writeJpeg(bitmap, dest)
    }

    private fun writeThumbFromFull(full: File, dest: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(full.absolutePath, bounds)
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, LIST_THUMB_PX)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(full.absolutePath, opts) ?: return false
        return writeJpeg(bitmap, dest)
    }

    private fun writeJpeg(bitmap: Bitmap, dest: File): Boolean {
        return try {
            FileOutputStream(dest).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) {
            sample *= 2
        }
        return sample
    }

    companion object {
        /** ~2× a 40dp list avatar on xxhdpi. */
        const val LIST_THUMB_PX = 128
    }
}
