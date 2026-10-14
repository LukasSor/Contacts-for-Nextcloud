package com.nextcloud.contacts.data.device

import android.content.Context
import com.nextcloud.contacts.R
import com.nextcloud.contacts.sync.SyncManager

fun formatImportMessage(context: Context, result: ImportResult): String {
    val resources = context.resources
    val photoNote = when {
        result.photosImported > 0 && result.photosBackfilled > 0 ->
            context.getString(
                R.string.import_photos_new_and_existing,
                result.photosImported,
                result.photosBackfilled,
            )
        result.photosImported > 0 -> resources.getQuantityString(
            R.plurals.import_with_photos,
            result.photosImported,
            result.photosImported,
        )
        result.photosBackfilled > 0 -> resources.getQuantityString(
            R.plurals.import_photos_added_existing,
            result.photosBackfilled,
            result.photosBackfilled,
        )
        result.withPhotos > 0 && result.imported == 0 && result.skipped > 0 ->
            resources.getQuantityString(
                R.plurals.import_photos_found_device,
                result.withPhotos,
                result.withPhotos,
            )
        else -> ""
    }
    return when {
        result.totalOnDevice == 0 -> context.getString(R.string.msg_no_contacts_on_device)
        result.imported == 0 && result.photosBackfilled == 0 ->
            resources.getQuantityString(
                R.plurals.all_already_imported,
                result.totalOnDevice,
                result.totalOnDevice,
            ) + photoNote
        result.imported == 0 ->
            resources.getQuantityString(
                R.plurals.updated_contact_photos,
                result.photosBackfilled,
                result.photosBackfilled,
            ) + photoNote
        result.skipped > 0 ->
            resources.getQuantityString(
                R.plurals.imported_contacts_skipped,
                result.imported,
                result.imported,
                result.skipped,
            ) + photoNote
        else -> resources.getQuantityString(
            R.plurals.imported_contacts,
            result.imported,
            result.imported,
        ) + photoNote
    }
}

/** Same import-then-upload text for the contact list and Settings. */
suspend fun importDeviceContacts(
    context: Context,
    importer: DeviceContactsImporter,
    syncManager: SyncManager,
): String {
    val base = formatImportMessage(context, importer.import())
    return when (val syncResult = syncManager.sync()) {
        is SyncManager.SyncResult.Error ->
            "$base\n${context.getString(R.string.msg_upload_failed, syncResult.message)}"
        is SyncManager.SyncResult.Success -> {
            val extra = buildList {
                if (syncResult.pushed > 0) {
                    add(
                        context.resources.getQuantityString(
                            R.plurals.uploaded_to_nextcloud,
                            syncResult.pushed,
                            syncResult.pushed,
                        ),
                    )
                }
                syncResult.warning?.let { add(it) }
            }
            if (extra.isEmpty()) base else "$base\n${extra.joinToString("\n")}"
        }
        is SyncManager.SyncResult.NotLoggedIn -> base
    }
}
