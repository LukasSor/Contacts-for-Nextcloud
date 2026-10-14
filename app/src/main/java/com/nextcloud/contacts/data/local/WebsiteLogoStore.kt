package com.nextcloud.contacts.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class WebsiteLogoStore(context: Context) {
    private val directory = File(context.filesDir, "website_logos").apply { mkdirs() }

    fun logoFile(fileName: String): File = File(directory, fileName)

    fun hasLogo(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        val file = logoFile(fileName)
        return file.exists() && file.length() > 0L
    }

    fun readLogoBytes(fileName: String): ByteArray? {
        if (fileName.isBlank()) return null
        val file = logoFile(fileName)
        return if (file.exists() && file.length() > 0L) file.readBytes() else null
    }

    /**
     * Decode and persist a row-sized favicon (~[ROW_LOGO_PX] px). Returns the stored file name.
     */
    fun saveLogo(contactId: Long, websiteKey: String, bytes: ByteArray): String? {
        val fileName = fileNameFor(contactId, websiteKey)
        val dest = logoFile(fileName)
        val bitmap = decodeForRow(bytes) ?: return null
        return try {
            FileOutputStream(dest).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
            }
            fileName
        } catch (_: Exception) {
            null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun deleteLogo(fileName: String) {
        if (fileName.isBlank()) return
        logoFile(fileName).delete()
    }

    fun fileNameFor(contactId: Long, websiteKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$contactId|$websiteKey".encodeToByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "${contactId}_$digest.png"
    }

    private fun decodeForRow(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, ROW_LOGO_PX)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val maxDim = maxOf(decoded.width, decoded.height)
        if (maxDim <= ROW_LOGO_PX) return decoded
        val scale = ROW_LOGO_PX.toFloat() / maxDim.toFloat()
        val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded && !decoded.isRecycled) decoded.recycle()
        return scaled
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
        /** ~2× a 22dp row icon on xxhdpi. */
        const val ROW_LOGO_PX = 88
    }
}
