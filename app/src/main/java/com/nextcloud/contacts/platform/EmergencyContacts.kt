package com.nextcloud.contacts.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri

/**
 * Android keeps emergency contacts in the platform emergency app.
 * A normal app is not allowed to write that list. Reading is attempted
 * only against the system provider and is skipped when the query is denied.
 */
object EmergencyContacts {
    private const val SAFETY_HUB_EDIT_CONTACTS = "com.google.android.apps.safetyhub.EDIT_CONTACTS"
    private const val SAFETY_HUB_CONTACTS_ENTRY = "com.google.android.apps.safetyhub.CONTACTS_APP_ENTRY"
    private const val SETTINGS_EDIT_EMERGENCY_INFO = "android.settings.EDIT_EMERGENCY_INFO"
    private const val EMERGENCY_EDIT_EMERGENCY_INFO = "android.emergency.EDIT_EMERGENCY_INFO"
    private const val EMERGENCY_PACKAGE = "com.android.emergency"
    private const val EMERGENCY_EDIT_ACTIVITY = "com.android.emergency.edit.EditInfoActivity"

    private val readUris = listOf(
        "content://com.android.emergency.provider/emergency_contacts",
        "content://com.android.emergency/emergency_contacts",
    )

    private val nameColumns = listOf("name", "display_name", "displayName", "contact_name", "title")

    /** Null when this app cannot read the system list. An empty list is a real empty read. */
    fun readNames(context: Context): List<String>? {
        for (raw in readUris) {
            val names = queryNames(context, Uri.parse(raw))
            if (names != null) return names
        }
        return null
    }

    /** No public API lets a normal app insert into the system emergency list. */
    fun canWrite(): Boolean = false

    /**
     * Opens the system emergency-contacts editor.
     * Contacts-specific actions come first. Medical-info-only activities are skipped
     * when the class name tells them apart. The activity is resolved before it is started.
     */
    fun openEditor(context: Context): Boolean {
        val packageManager = context.packageManager
        for (candidate in editorIntents()) {
            val component = resolveEditor(packageManager, candidate) ?: continue
            val launch = Intent(candidate).setComponent(component)
            try {
                context.startActivity(launch)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
        return false
    }

    private fun editorIntents(): List<Intent> = listOf(
        actionIntent(SAFETY_HUB_EDIT_CONTACTS),
        actionIntent(SAFETY_HUB_CONTACTS_ENTRY),
        actionIntent(SETTINGS_EDIT_EMERGENCY_INFO),
        actionIntent(EMERGENCY_EDIT_EMERGENCY_INFO),
        Intent(Intent.ACTION_EDIT).setClassName(EMERGENCY_PACKAGE, EMERGENCY_EDIT_ACTIVITY),
        Intent(SETTINGS_EDIT_EMERGENCY_INFO).setClassName(EMERGENCY_PACKAGE, EMERGENCY_EDIT_ACTIVITY),
    )

    private fun actionIntent(action: String): Intent =
        Intent(action).addCategory(Intent.CATEGORY_DEFAULT)

    private fun resolveEditor(packageManager: PackageManager, intent: Intent): ComponentName? {
        val explicit = intent.component
        if (explicit != null) {
            val info = activityInfo(packageManager, explicit) ?: return null
            if (!info.exported || !info.enabled || isMedicalOnly(info)) return null
            return explicit
        }
        val matches = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val chosen = matches
            .map { it.activityInfo }
            .filter { it.exported && it.enabled && !isMedicalOnly(it) }
            .minWithOrNull(compareByDescending { prefersContacts(it) })
            ?: return null
        return ComponentName(chosen.packageName, chosen.name)
    }

    private fun activityInfo(packageManager: PackageManager, component: ComponentName): ActivityInfo? {
        return try {
            packageManager.getActivityInfo(component, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun prefersContacts(info: ActivityInfo): Boolean {
        val identity = activityIdentity(info)
        return identity.contains("emergencycontact") || identity.contains("editcontact")
    }

    private fun isMedicalOnly(info: ActivityInfo): Boolean {
        val identity = activityIdentity(info)
        return identity.contains("medical") && !prefersContacts(info)
    }

    private fun activityIdentity(info: ActivityInfo): String {
        return "${info.name} ${info.targetActivity.orEmpty()}".lowercase()
    }

    private fun queryNames(context: Context, uri: Uri): List<String>? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                namesFrom(cursor)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun namesFrom(cursor: Cursor): List<String>? {
        val nameIndex = nameColumns.firstNotNullOfOrNull { column ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0) index else null
        } ?: return null
        return buildList {
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                if (name.isNotEmpty()) add(name)
            }
        }
    }
}
