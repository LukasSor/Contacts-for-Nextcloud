package com.nextcloud.contacts.data.remote

import android.util.Base64
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import ezvcard.parameter.ImageType
import ezvcard.property.Photo

/** PHOTO property encoding for vCard free-form edit and export. */
object VCardPhotoHelper {

    fun looksLikeUri(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true) ||
            trimmed.startsWith("cid:", ignoreCase = true)
    }

    /** Value shown in the editor and written when the user has not replaced file-backed photos. */
    fun baselineRawValue(bytes: ByteArray, extras: VCardSyntaxExtras?): String =
        serializedPhotoValue(buildPhoto(bytes, extras))

    fun serializedPhotoValue(photo: Photo): String {
        photo.url?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val data = photo.data ?: return ""
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    fun buildPhoto(bytes: ByteArray, extras: VCardSyntaxExtras?): Photo {
        val imageType = imageTypeFromExtras(extras) ?: detectImageType(bytes)
        return Photo(bytes, imageType).also { VCardMapper.applyPhotoSyntaxExtras(it, extras) }
    }

    fun buildPhotoFromRaw(raw: String, extras: VCardSyntaxExtras?): Photo? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return if (extras?.hasPersistableMetadata() == true) {
                Photo("", imageTypeFromExtras(extras) ?: ImageType.JPEG).also {
                    VCardMapper.applyPhotoSyntaxExtras(it, extras)
                }
            } else {
                null
            }
        }
        if (looksLikeUri(trimmed)) {
            val type = imageTypeFromExtras(extras) ?: ImageType.JPEG
            return Photo(trimmed, type).also { VCardMapper.applyPhotoSyntaxExtras(it, extras) }
        }
        val bytes = decodeBinary(trimmed) ?: return null
        return buildPhoto(bytes, extras)
    }

    fun decodeBinary(raw: String): ByteArray? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || looksLikeUri(trimmed)) return null
        return runCatching {
            Base64.decode(trimmed, Base64.DEFAULT)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun previewBytes(raw: String, fallbackBytes: ByteArray?): ByteArray? {
        decodeBinary(raw)?.let { return it }
        if (raw.isBlank() && fallbackBytes != null) return fallbackBytes
        return null
    }

    fun photoSyntaxFromParsed(photo: Photo): VCardSyntaxExtras =
        VCardMapper.scalarSyntaxFromPhoto(photo)

    private fun imageTypeFromExtras(extras: VCardSyntaxExtras?): ImageType? {
        val type = extras?.typeParam?.split(',')?.firstOrNull()?.trim().orEmpty()
        if (type.isBlank()) return null
        return runCatching { ImageType.get(type, null, null) }.getOrNull()
            ?: ImageType.all().firstOrNull { it.mediaType.equals(type, ignoreCase = true) }
    }

    private fun detectImageType(bytes: ByteArray): ImageType = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> ImageType.JPEG
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
        ) -> ImageType.PNG
        bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> ImageType.GIF
        else -> ImageType.JPEG
    }
}
