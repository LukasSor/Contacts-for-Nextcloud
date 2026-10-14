package com.nextcloud.contacts.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CalendarPermissionRequester {
    private var requestHandler: (((Boolean) -> Unit) -> Unit)? = null

    fun register(handler: ((Boolean) -> Unit) -> Unit) {
        requestHandler = handler
    }

    fun unregister() {
        requestHandler = null
    }

    fun requestCalendarPermissions(callback: (Boolean) -> Unit) {
        requestHandler?.invoke(callback) ?: callback(false)
    }

    companion object {
        val calendarPermissions = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
        )

        fun hasCalendarPermissions(context: Context): Boolean =
            calendarPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }

        /** True when denied and the system will not show the permission dialog again. */
        fun isCalendarPermissionPermanentlyDenied(activity: Activity): Boolean {
            if (hasCalendarPermissions(activity)) return false
            return calendarPermissions.none { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
        }

        fun openAppPermissionSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
