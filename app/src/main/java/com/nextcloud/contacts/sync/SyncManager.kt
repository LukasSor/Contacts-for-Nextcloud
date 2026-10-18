package com.nextcloud.contacts.sync

import android.content.Context
import android.util.Log
import com.nextcloud.contacts.R
import com.nextcloud.contacts.auth.CredentialStore
import com.nextcloud.contacts.data.ContactRepository
import com.nextcloud.contacts.data.RemoteContactIndex
import com.nextcloud.contacts.data.serverDeleteIsDue
import com.nextcloud.contacts.data.shouldKeepDeletedContactAfterServerGone
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.data.remote.CardDavClient
import com.nextcloud.contacts.data.remote.NextcloudCapabilitiesService
import com.nextcloud.contacts.data.remote.RemoteUrlNormalizer
import com.nextcloud.contacts.data.remote.VCardMapper
import com.nextcloud.contacts.platform.ContactDatesCalendarCoordinator
import com.nextcloud.contacts.platform.SystemContactsExporter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SyncManager(
    private val context: Context,
    private val credentialStore: CredentialStore,
    private val contactRepository: ContactRepository,
    private val userPreferences: UserPreferences,
    private val systemContactsExporter: SystemContactsExporter,
    private val contactDatesCalendarCoordinator: ContactDatesCalendarCoordinator,
) {

    private val singleFlight = SyncSingleFlight()

    sealed class SyncResult {
        data class Success(
            val pulled: Int = 0,
            val pushed: Int = 0,
            val warning: String? = null,
        ) : SyncResult()
        data class Error(val message: String) : SyncResult()
        data object NotLoggedIn : SyncResult()
    }

    private data class PullCount(
        val pulled: Int,
        val failed: Int,
        val favoritesListed: Boolean,
    )

    private enum class ApplyResult { Applied, Unchanged, Failed }

    suspend fun syncAfterLogin(): SyncResult = forceFullPull()

    suspend fun forceFullPull(): SyncResult = singleFlight.coalesce(fullPull = true) {
        userPreferences.setSyncToken(null)
        syncBody()
    }

    suspend fun sync(): SyncResult = singleFlight.coalesce(fullPull = false) {
        syncBody()
    }

    /** Pull-to-refresh and Settings use a full pull only when no row is linked to the server. */
    suspend fun refreshFromServer(): SyncResult =
        if (contactRepository.countServerLinked() == 0) forceFullPull() else sync()

    private suspend fun syncBody(): SyncResult = withContext(Dispatchers.IO) {
        val credentials = credentialStore.getCredentials() ?: return@withContext SyncResult.NotLoggedIn
        try {
            val client = CardDavClient(credentials)
            contactRepository.purgeExpiredBinContacts()
            val index = contactRepository.openRemoteContactIndex(credentials.server)
            val pull = pullRemoteChanges(client, credentials.server, index)
            contactRepository.deduplicateLocalContacts(credentials.server)
            contactRepository.linkPendingUploadsBeforePush()
            val pushed = pushLocalChanges(client, credentials.server)
            if (!pull.favoritesListed) {
                refreshFavorites(client, credentials.server, index)
            }
            runCatching {
                NextcloudCapabilitiesService(credentials).isCalendarAppEnabled()
            }.onSuccess { available ->
                userPreferences.setCachedNextcloudCalendarAvailable(available)
            }
            systemContactsExporter.exportAll()
            val calendarFailed = contactDatesCalendarCoordinator.syncAll()
            val warning = if (calendarFailed) {
                context.getString(R.string.msg_nextcloud_calendar_failed)
            } else {
                null
            }
            if (pull.failed > 0) {
                SyncResult.Error(context.getString(R.string.msg_sync_failed))
            } else {
                if (shouldStampLastSyncTime(pull.failed)) {
                    userPreferences.setLastSyncTime(System.currentTimeMillis())
                }
                SyncResult.Success(pulled = pull.pulled, pushed = pushed, warning = warning)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            SyncResult.Error(e.message ?: context.getString(R.string.msg_sync_failed))
        }
    }

    private suspend fun pushLocalChanges(client: CardDavClient, server: String): Int {
        val pending = contactRepository.getPendingSync()
        val fingerprints = contactRepository.fingerprintIndex()
        val pushAfterMerge = LinkedHashSet<Long>()
        var pushed = 0
        for (contact in pending) {
            if (contact.deleted) {
                // The href is already gone. Leave the bin row alone; do not create the card again.
                if (!serverDeleteIsDue(contact.remoteUrl, contact.deleted)) continue
                val storageKey = contact.remoteUrl ?: continue
                try {
                    client.deleteVCard(storageKey, contact.etag)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // The card is already gone, so the local delete is done.
                    if (!localDeleteNotFoundIsSuccess(httpStatusFromClientError(e), locallyDeleted = true)) {
                        throw e
                    }
                }
                // The card is off the server. The local row stays in the bin until the window ends.
                contactRepository.removeByRemoteUrl(storageKey)
                pushed++
                continue
            }

            if (contact.id in pushAfterMerge) {
                continue
            }

            val details = contactRepository.getContact(contact.id) ?: continue
            if (contact.remoteUrl == null) {
                val match = contactRepository.findInFingerprintIndex(fingerprints, details)
                if (match != null && match.contact.remoteUrl != null && match.contact.id != contact.id) {
                    val keeperId = match.contact.id
                    contactRepository.mergePendingIntoExisting(contact.id, keeperId)
                    fingerprints.remove(contact.id)
                    contactRepository.getContact(keeperId)?.let { fingerprints.putContact(it) }
                    // The pending snapshot is the pre-merge row. Upload the merged keeper once after this pass.
                    pushAfterMerge.add(keeperId)
                    continue
                }
            }

            if (uploadContact(client, server, contact, details)) {
                pushed++
            }
        }
        for (keeperId in pushAfterMerge) {
            val keeper = contactRepository.getContact(keeperId) ?: continue
            if (keeper.contact.deleted) continue
            if (uploadContact(client, server, keeper.contact, keeper)) {
                pushed++
            }
        }
        return pushed
    }

    private suspend fun uploadContact(
        client: CardDavClient,
        server: String,
        contact: ContactEntity,
        details: ContactWithDetails,
    ): Boolean {
        val photoBytes = contactRepository.getPhotoBytes(contact.id)
        // A blank uid used to skip the row. Keep the one we assign so a retry uploads the same card.
        val uid = contactRepository.ensureRemoteUid(contact.id) ?: return false
        val storageKey = contact.remoteUrl ?: client.contactStorageKey(uid)
        val vcard = VCardMapper.toVCard(details, photoBytes)

        val newEtag = try {
            writeVCard(
                client = client,
                creating = contact.remoteUrl == null,
                storageKey = storageKey,
                vcard = vcard,
                etag = contact.etag,
                photoBytes = photoBytes,
                details = details,
            )
        } catch (e: Exception) {
            if (
                contact.remoteUrl == null ||
                !shouldRecreateAfterUpdateNotFound(httpStatusFromClientError(e), contact.deleted)
            ) {
                Log.w(TAG, "Upload rejected for uid=$uid structure=${vcardStructure(vcard)}", e)
                throw e
            }
            // The server card is gone. Keep the local edit and create it again.
            contactRepository.detachMissingServerCard(contact.id)
            writeVCard(
                client = client,
                creating = true,
                storageKey = storageKey,
                vcard = vcard,
                etag = null,
                photoBytes = photoBytes,
                details = details,
            )
        }

        val storedKey = RemoteUrlNormalizer.storageKey(server, storageKey)
        val favoriteError = runCatching {
            client.setFavorite(
                storageKey = storedKey,
                favorite = details.contact.isFavorite,
            )
        }.exceptionOrNull()
        contactRepository.markSynced(
            contact = contact.copy(
                remoteUrl = storedKey,
                remoteUid = uid,
            ),
            etag = newEtag,
            remoteUrl = storedKey,
        )
        // The vCard is on the server. Retry the star unless the server rejected the login.
        if (SyncRetryPolicy.shouldMarkDirtyAfterFavoriteFailure(favoriteError?.message)) {
            contactRepository.markDirty(contact.id)
        }
        return true
    }

    private suspend fun pullRemoteChanges(
        client: CardDavClient,
        server: String,
        index: RemoteContactIndex,
    ): PullCount {
        var syncToken = userPreferences.syncToken.first()
        val serverLinkedCount = contactRepository.countServerLinked()
        if (syncToken != null && serverLinkedCount == 0) {
            userPreferences.setSyncToken(null)
            syncToken = null
        }
        return if (syncToken == null) {
            pullInitialAddressBook(client, server, index)
        } else {
            pullIncrementalChanges(client, server, syncToken, index)
        }
    }

    private suspend fun pullInitialAddressBook(
        client: CardDavClient,
        server: String,
        index: RemoteContactIndex,
    ): PullCount {
        var changes = client.listAddressBookContacts()
        // That PROPFIND already asked for nc:favorite on every card. A second one is redundant.
        val fromPropfind = changes.isNotEmpty()
        val listedToken = if (changes.isEmpty()) {
            val syncResult = client.syncChanges(syncToken = null)
            changes = syncResult.changes.filter { !it.deleted }
            syncResult.syncToken
        } else {
            client.syncChanges(syncToken = null).syncToken
        }
        var pulled = 0
        var failed = 0
        for (change in changes) {
            when (applyRemoteChange(client, server, change, index)) {
                ApplyResult.Applied -> pulled++
                ApplyResult.Unchanged -> Unit
                ApplyResult.Failed -> failed++
            }
        }
        if (
            initialPullFailedToImport(
                changeCount = changes.size,
                pulled = pulled,
                failed = failed,
                serverLinkedCount = contactRepository.countServerLinked(),
            )
        ) {
            error(
                context.resources.getQuantityString(
                    R.plurals.server_contacts_not_imported,
                    changes.size,
                    changes.size,
                ),
            )
        }
        // A failed card has to come back on the next full pull. The token waits until none failed.
        if (shouldStoreSyncToken(failed)) {
            listedToken?.let { userPreferences.setSyncToken(it) }
        }
        val favoritesListed = fromPropfind ||
            changes.isEmpty() ||
            changes.all { it.favorite != null }
        return PullCount(pulled = pulled, failed = failed, favoritesListed = favoritesListed)
    }

    private suspend fun pullIncrementalChanges(
        client: CardDavClient,
        server: String,
        syncToken: String,
        index: RemoteContactIndex,
    ): PullCount {
        val result = client.syncChanges(syncToken)
        var pulled = 0
        var failed = 0
        for (change in result.changes) {
            when (applyRemoteChange(client, server, change, index)) {
                ApplyResult.Applied -> pulled++
                ApplyResult.Unchanged -> Unit
                ApplyResult.Failed -> failed++
            }
        }
        // A failed contact must stay in the next delta. Advancing the token would hide it.
        if (failed == 0) {
            result.syncToken?.let { userPreferences.setSyncToken(it) }
        }
        // A delta does not include favorite-only edits on unchanged cards.
        return PullCount(pulled = pulled, failed = failed, favoritesListed = false)
    }

    private suspend fun forgetMissingServerCard(
        server: String,
        storageKey: String,
        index: RemoteContactIndex,
    ): ApplyResult {
        val existing = contactRepository.findByRemoteReference(server, storageKey, index)
        if (existing != null && existing.deleted) {
            if (shouldKeepDeletedContactAfterServerGone(existing, System.currentTimeMillis())) {
                contactRepository.keepDeletedContactAfterServerGone(existing.id)
            } else {
                contactRepository.purgeDeletedContact(existing.id)
                index.remove(existing.id)
            }
            return ApplyResult.Unchanged
        }
        if (existing != null && shouldSkipRemoteDelete(existing.dirty, existing.deleted)) {
            return ApplyResult.Unchanged
        }
        contactRepository.removeByRemoteUrl(storageKey)
        existing?.let { index.remove(it.id) }
        return ApplyResult.Applied
    }

    private suspend fun refreshFavorites(
        client: CardDavClient,
        server: String,
        index: RemoteContactIndex,
    ) {
        val changes = runCatching { client.listAddressBookContacts() }.getOrElse { return }
        for (change in changes) {
            if (change.deleted) continue
            val existing = contactRepository.findByRemoteReference(server, change.href, index) ?: continue
            if (existing.deleted || existing.dirty) continue
            when (change.favorite) {
                true -> contactRepository.setFavorite(existing.id, true)
                false -> contactRepository.setFavorite(existing.id, false)
                null -> Unit
            }
        }
    }

    private suspend fun applyRemoteChange(
        client: CardDavClient,
        server: String,
        change: CardDavClient.SyncChange,
        index: RemoteContactIndex,
    ): ApplyResult {
        val storageKey = RemoteUrlNormalizer.storageKey(server, change.href)
        if (change.deleted) {
            return forgetMissingServerCard(server, storageKey, index)
        }
        val existing = contactRepository.findByRemoteReference(server, storageKey, index)
        if (existing?.deleted == true) {
            // The server card is still there because this delete has not been uploaded.
            // Leave the bin row in place, and do not bring the contact back to the list.
            return ApplyResult.Unchanged
        }
        if (existing?.dirty == true) {
            return ApplyResult.Unchanged
        }
        if (
            existing != null &&
            !existing.etag.isNullOrBlank() &&
            existing.etag == change.etag
        ) {
            // Favorite is a Nextcloud WebDAV property and can change without a new etag.
            when (change.favorite) {
                true -> contactRepository.setFavorite(existing.id, true)
                false -> contactRepository.setFavorite(existing.id, false)
                null -> Unit
            }
            return ApplyResult.Unchanged
        }
        val vcardText = try {
            client.downloadVCard(storageKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (httpStatusFromClientError(e) == 404) {
                return forgetMissingServerCard(server, storageKey, index)
            }
            return ApplyResult.Failed
        }
        if (vcardText.isBlank()) return ApplyResult.Failed
        return try {
            val parsed = VCardMapper.fromVCard(vcardText, storageKey, change.etag)
            val contact = parsed.contact.copy(
                isFavorite = change.favorite ?: existing?.isFavorite ?: false,
            )
            val upsert = contactRepository.upsertFromServer(
                server = server,
                contact = contact,
                phones = parsed.phones,
                emails = parsed.emails,
                addresses = parsed.addresses,
                urls = parsed.urls,
                imAccounts = parsed.imAccounts,
                socialProfiles = parsed.socialProfiles,
                cloudIds = parsed.cloudIds,
                languages = parsed.languages,
                relatedNames = parsed.relatedNames,
                customFields = parsed.customFields,
                photoBytes = parsed.photoBytes,
                index = index,
                runDeviceIntegrations = runDeviceIntegrationsOnServerPull(),
            )
            val duplicateUrl = upsert.duplicateRemoteUrl
            if (duplicateUrl != null) {
                try {
                    client.deleteVCard(duplicateUrl, etag = null)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // The local card is already saved. A leftover server copy can wait for a later sync.
                    Log.w(TAG, "Duplicate server card was not deleted", e)
                    if (duplicateServerDeleteFailureFailsApply()) {
                        return ApplyResult.Failed
                    }
                }
            }
            ApplyResult.Applied
        } catch (_: Exception) {
            ApplyResult.Failed
        }
    }

    suspend fun syncFavorite(
        contactId: Long,
        favorite: Boolean,
        dirtyBeforeToggle: Boolean = true,
        lastModifiedAtToggle: Long = 0L,
        afterAttempt: suspend (SyncResult) -> Unit = {},
    ): SyncResult = singleFlight.exclude {
        val result = syncFavoriteBody(
            contactId = contactId,
            favorite = favorite,
            dirtyBeforeToggle = dirtyBeforeToggle,
            lastModifiedAtToggle = lastModifiedAtToggle,
        )
        afterAttempt(result)
        result
    }

    private suspend fun syncFavoriteBody(
        contactId: Long,
        favorite: Boolean,
        dirtyBeforeToggle: Boolean,
        lastModifiedAtToggle: Long,
    ): SyncResult =
        withContext(Dispatchers.IO) {
            val credentials = credentialStore.getCredentials() ?: return@withContext SyncResult.NotLoggedIn
            val details = contactRepository.getContact(contactId)
                ?: return@withContext SyncResult.Error(context.getString(R.string.msg_contact_not_found))
            val storageKey = details.contact.remoteUrl
                ?: return@withContext SyncResult.Success()
            val star = favoriteToUpload(
                passedFavorite = favorite,
                storedFavorite = details.contact.isFavorite,
            )
            try {
                val client = CardDavClient(credentials)
                client.setFavorite(storageKey, star)
                val stored = contactRepository.getContact(contactId)?.contact
                if (stored != null && stored.isFavorite != star) {
                    contactRepository.setFavorite(contactId, star)
                }
                val after = contactRepository.getContact(contactId)?.contact
                if (
                    after != null &&
                    shouldClearDirtyAfterFavoriteUpload(
                        wasDirtyBeforeToggle = dirtyBeforeToggle,
                        lastModifiedAtToggle = lastModifiedAtToggle,
                        lastModifiedNow = after.lastModified,
                        storedFavorite = after.isFavorite,
                        uploadedFavorite = star,
                    )
                ) {
                    contactRepository.clearDirtyIfUnchanged(contactId, lastModifiedAtToggle)
                }
                SyncResult.Success()
            } catch (e: Exception) {
                SyncResult.Error(e.message ?: context.getString(R.string.msg_favorite_sync_failed))
            }
        }

    private fun writeVCard(
        client: CardDavClient,
        creating: Boolean,
        storageKey: String,
        vcard: String,
        etag: String?,
        photoBytes: ByteArray?,
        details: ContactWithDetails,
    ): String? {
        return try {
            if (creating) {
                client.uploadVCard(storageKey, vcard)
            } else {
                client.updateVCard(storageKey, vcard, etag)
            }
        } catch (e: Exception) {
            if (photoBytes != null && shouldRetryWithoutPhoto(e)) {
                val slimVcard = VCardMapper.toVCard(details, photoBytes = null)
                if (creating) {
                    client.uploadVCard(storageKey, slimVcard)
                } else {
                    client.updateVCard(storageKey, slimVcard, etag)
                }
            } else {
                throw e
            }
        }
    }

    private fun shouldRetryWithoutPhoto(error: Exception): Boolean {
        val message = error.message.orEmpty()
        return "413" in message || "507" in message || "400" in message
    }

    suspend fun disconnect() {
        systemContactsExporter.clearExported()
        contactRepository.clearAll()
        userPreferences.clearSyncState()
        userPreferences.setPostLoginSetupComplete(false)
        credentialStore.clear()
    }
}

/**
 * The toggle already chose the star. [storedFavorite] may be a stale read after another sync.
 */
internal fun favoriteToUpload(passedFavorite: Boolean, storedFavorite: Boolean): Boolean {
    if (storedFavorite == passedFavorite) return passedFavorite
    return passedFavorite
}

/** A dirty local edit still has to upload. A row already deleted locally can be removed. */
internal fun shouldSkipRemoteDelete(dirty: Boolean, locallyDeleted: Boolean): Boolean =
    dirty && !locallyDeleted

/** The pull still exports the address book and calendar once after every card is stored. */
internal fun runDeviceIntegrationsOnServerPull(): Boolean = false

/**
 * Update 404 means that href is gone. Keep the local edit and create the card.
 * A local delete does not create it again.
 */
internal fun shouldRecreateAfterUpdateNotFound(httpStatus: Int?, locallyDeleted: Boolean): Boolean =
    httpStatus == 404 && !locallyDeleted

/** Delete 404 means the server card is already gone. */
internal fun localDeleteNotFoundIsSuccess(httpStatus: Int?, locallyDeleted: Boolean): Boolean =
    httpStatus == 404 && locallyDeleted

internal fun httpStatusFromClientError(error: Throwable): Int? {
    val message = error.message ?: return null
    return HTTP_STATUS.find(message)?.groupValues?.get(1)?.toIntOrNull()
}

/** The contact is already stored. Losing the extra server copy must not block the sync token. */
internal fun duplicateServerDeleteFailureFailsApply(): Boolean = false

/**
 * The star upload is the only edit when the row was clean before the tap
 * and nothing else has been saved since. Other dirty fields still need a vCard push.
 */
internal fun shouldClearDirtyAfterFavoriteUpload(
    wasDirtyBeforeToggle: Boolean,
    lastModifiedAtToggle: Long,
    lastModifiedNow: Long,
    storedFavorite: Boolean,
    uploadedFavorite: Boolean,
): Boolean {
    if (wasDirtyBeforeToggle) return false
    if (lastModifiedNow != lastModifiedAtToggle) return false
    return storedFavorite == uploadedFavorite
}

private val HTTP_STATUS = Regex("""HTTP (\d{3})""")

internal fun vcardStructure(vcard: String): String = vcard
    .lineSequence()
    .map { it.trim() }
    .filter { line ->
        line.isNotEmpty() &&
            !line.startsWith("BEGIN:", ignoreCase = true) &&
            !line.startsWith("END:", ignoreCase = true) &&
            !line[0].isWhitespace()
    }
    .map { line ->
        val head = line.substringBefore(':')
        val name = head.substringBefore(';')
        val params = head.substringAfter(';', "")
            .split(';')
            .map { it.substringBefore('=').trim() }
            .filter { it.isNotEmpty() }
        if (params.isEmpty()) name else "$name[${params.joinToString(",")}]"
    }
    .joinToString(" ")
    .take(500)

private const val TAG = "SyncManager"

/** Failed pulls and thrown pushes must not look like a sync that just finished. */
internal fun shouldStampLastSyncTime(pullFailed: Int): Boolean = pullFailed == 0

/** A failed card stays out of the token so the next full pull retries it. */
internal fun shouldStoreSyncToken(failed: Int): Boolean = failed == 0

/**
 * Cards we already have are a successful read.
 * The book failed only when every card failed, or nothing was stored and some card failed.
 */
internal fun initialPullFailedToImport(
    changeCount: Int,
    pulled: Int,
    failed: Int,
    serverLinkedCount: Int,
): Boolean {
    if (changeCount == 0) return false
    if (failed == changeCount) return true
    return serverLinkedCount == 0 && pulled == 0 && failed > 0
}
