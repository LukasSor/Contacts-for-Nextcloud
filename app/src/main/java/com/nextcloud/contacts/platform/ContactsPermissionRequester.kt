package com.nextcloud.contacts.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class ContactsPermissionRequester {
    private var requestHandler: (((Boolean) -> Unit) -> Unit)? = null

    fun register(handler: ((Boolean) -> Unit) -> Unit) {
        requestHandler = handler
    }

    fun unregister() {
        requestHandler = null
    }

    fun requestDeviceExportPermissions(callback: (Boolean) -> Unit) {
        requestHandler?.invoke(callback) ?: callback(false)
    }

    companion object {
        val exportPermissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        )

        fun hasDeviceExportPermissions(context: Context): Boolean =
            exportPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
    }
}
