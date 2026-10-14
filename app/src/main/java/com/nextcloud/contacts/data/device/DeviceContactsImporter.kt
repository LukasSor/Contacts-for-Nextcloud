package com.nextcloud.contacts.data.device

import com.nextcloud.contacts.data.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val totalOnDevice: Int,
    val withPhotos: Int,
    val photosImported: Int,
    val photosBackfilled: Int,
)

class DeviceContactsImporter(
    private val reader: DeviceContactsReader,
    private val repository: ContactRepository,
) {
    suspend fun import(): ImportResult = withContext(Dispatchers.IO) {
        val deviceContacts = reader.readAll()
        val result = repository.importContacts(deviceContacts)
        ImportResult(
            imported = result.imported,
            skipped = result.skipped,
            totalOnDevice = deviceContacts.size,
            withPhotos = deviceContacts.count { it.photoBytes != null },
            photosImported = result.photosImported,
            photosBackfilled = result.photosBackfilled,
        )
    }
}
