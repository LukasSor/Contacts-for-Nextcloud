package com.nextcloud.contacts.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/** Process-wide cache of small app icons for list/detail rows (never load on the main thread). */
object AppIconBitmapCache {
    private const val ICON_PX = 128

    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun get(context: Context, packageName: String): ImageBitmap? {
        val pkg = packageName.trim().takeIf { it.isNotEmpty() } ?: return null
        cache[pkg]?.let { return it }
        if (missing.contains(pkg)) return null

        val pm = context.applicationContext.packageManager
        val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: run {
            missing.add(pkg)
            return null
        }
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
            ?: drawable.toBitmap(ICON_PX, ICON_PX)
        val image = bitmap.asImageBitmap()
        cache[pkg] = image
        return image
    }
}
