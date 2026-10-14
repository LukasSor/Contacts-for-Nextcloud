package com.nextcloud.contacts.platform

import com.nextcloud.contacts.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MapsContactsIntents {

    fun geoUri(location: String): Uri? {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return null
        parseCoordinates(trimmed)?.let { (lat, lon) ->
            return Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        }
        return Uri.parse("geo:0,0?q=${Uri.encode(trimmed)}")
    }

    fun openLocation(context: Context, location: String) {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return
        val uri = geoUri(trimmed) ?: return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.toast_no_maps), Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseCoordinates(value: String): Pair<Double, Double>? {
        val parts = value.split(';', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return lat to lon
    }
}
