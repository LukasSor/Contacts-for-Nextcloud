package com.nextcloud.contacts.data

import androidx.room.withTransaction
import com.nextcloud.contacts.data.local.AddressEntity
import com.nextcloud.contacts.data.local.CloudIdEntity
import com.nextcloud.contacts.data.local.ContactCategories
import com.nextcloud.contacts.data.local.ContactDao
import com.nextcloud.contacts.data.local.CachedNextcloudProfile
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactsDatabase
import com.nextcloud.contacts.data.local.NextcloudProfileLookupCache
import com.nextcloud.contacts.data.local.ProviderIntegrationsCache
import com.nextcloud.contacts.data.remote.CloudUser
import com.nextcloud.contacts.data.local.ContactInput
import com.nextcloud.contacts.data.local.ContactScalarVcardExtras
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import com.nextcloud.contacts.data.local.VCardUnknownLines
import com.nextcloud.contacts.data.local.toCloudIdEntity
import com.nextcloud.contacts.data.local.toEmailEntity
import com.nextcloud.contacts.data.local.toEntity
import com.nextcloud.contacts.data.local.toImEntity
import com.nextcloud.contacts.data.local.toLanguageEntity
import com.nextcloud.contacts.data.local.toPhoneEntity
import com.nextcloud.contacts.data.local.toRelatedEntity
import com.nextcloud.contacts.data.local.toSocialProfileEntity
import com.nextcloud.contacts.data.local.toUrlEntity
import com.nextcloud.contacts.data.local.ContactListItem
import com.nextcloud.contacts.data.local.LineManagerListRow
import com.nextcloud.contacts.data.local.ContactListNextcloudHint
import com.nextcloud.contacts.data.local.CachedWebsiteLogo
import com.nextcloud.contacts.data.local.ContactPhotoStore
import com.nextcloud.contacts.data.local.WebsiteLogoCache
import com.nextcloud.contacts.data.local.WebsiteLogoStore
import com.nextcloud.contacts.data.remote.WebsiteFaviconFetcher
import com.nextcloud.contacts.data.remote.WebsiteFaviconFetchResult
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.CustomFieldEntity
import com.nextcloud.contacts.data.local.customFieldMergeKey
import com.nextcloud.contacts.data.local.toEntity
import com.nextcloud.contacts.data.local.EmailEntity
import com.nextcloud.contacts.data.local.ImEntity
import com.nextcloud.contacts.data.local.LanguageEntity
import com.nextcloud.contacts.data.local.PhoneEntity
import com.nextcloud.contacts.data.local.RelatedEntity
import com.nextcloud.contacts.data.local.SocialProfileEntity
import com.nextcloud.contacts.ui.components.NextcloudProfileCardSupport
import com.nextcloud.contacts.data.local.UrlEntity
import com.nextcloud.contacts.data.remote.RemoteUrlNormalizer
import com.nextcloud.contacts.data.remote.VCardMapper
import com.nextcloud.contacts.platform.ContactsProviderIntegrations
import com.nextcloud.contacts.platform.MessagingContactsIntents
import com.nextcloud.contacts.platform.MessengerImMapping
import com.nextcloud.contacts.platform.ContactDatesCalendarCoordinator
import com.nextcloud.contacts.platform.SystemContactsExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ContactRepository(
    private val database: ContactsDatabase,
    private val contactDao: ContactDao,
    private val photoStore: ContactPhotoStore,
    private val websiteLogoStore: WebsiteLogoStore,
    private val websiteFaviconFetcher: WebsiteFaviconFetcher,
    private val systemContactsExporter: SystemContactsExporter,
    private val contactDatesCalendarCoordinator: ContactDatesCalendarCoordinator,
) {

    private val displayNameAlignment = Mutex()
    private val displayNamesAligned = AtomicBoolean(false)

    private suspend fun syncDeviceIntegrations(contactId: Long) {
        systemContactsExporter.exportContact(contactId)
        contactDatesCalendarCoordinator.syncContact(contactId)
    }

    private suspend fun removeDeviceIntegrations(contactId: Long) {
        systemContactsExporter.deleteContact(contactId)
        contactDatesCalendarCoordinator.removeContactEvent(contactId)
    }

    fun observeContacts(query: String): Flow<List<ContactWithDetails>> {
        val trimmed = query.trim()
        val rows = if (trimmed.isEmpty()) {
            contactDao.observeAll()
        } else {
            contactDao.search(trimmed)
        }
        return rows.onStart { alignStaleDisplayNames() }
    }

    /** Lightweight list rows (no phones/emails/… joins) for the main LazyColumn. */
    fun observeContactList(query: String): Flow<List<ContactListItem>> {
        val trimmed = query.trim()
        val items = if (trimmed.isEmpty()) {
            contactDao.observeList()
        } else {
            contactDao.searchList(trimmed)
        }
        // Hints query scans social/im/cloud tables; seed empty so the list flow is not blocked.
        return combine(
            items.onStart { alignStaleDisplayNames() },
            contactDao.observeNextcloudListHints().onStart { emit(emptyList()) },
        ) { list, hints ->
            applyNextcloudListFlags(list, hints)
        }
    }

    private fun applyNextcloudListFlags(
        items: List<ContactListItem>,
        hints: List<ContactListNextcloudHint>,
    ): List<ContactListItem> {
        if (hints.isEmpty()) return items
        val grouped = hints.groupBy { it.contactId }
        return items.map { item ->
            val contactHints = grouped[item.id].orEmpty()
            if (contactHints.isEmpty()) {
                item
            } else {
                val social = contactHints.filter { it.kind == "social" }.map {
                    SocialProfileEntity(contactId = item.id, type = it.type, value = it.value)
                }
                val ims = contactHints.filter { it.kind == "im" }.map {
                    ImEntity(contactId = item.id, type = it.type, value = it.value)
                }
                val clouds = contactHints.filter { it.kind == "cloud" }.map {
                    CloudIdEntity(contactId = item.id, type = it.type, value = it.value)
                }
                val hasNextcloud = NextcloudProfileCardSupport.collectPresences(
                    socialProfiles = social,
                    imAccounts = ims,
                    cloudIds = clouds,
                ).isNotEmpty()
                item.copy(hasNextcloudAccount = hasNextcloud)
            }
        }
    }

    fun observeAllGroups(): Flow<List<String>> =
        contactDao.observeContactCategories().map { rows ->
            ContactCategories.distinctSorted(
                rows.flatMap { raw -> ContactCategories.parse(raw) },
            )
        }

    fun observeContact(id: Long): Flow<ContactWithDetails?> =
        contactDao.observeById(id).onStart { alignStaleDisplayNames() }

    suspend fun getContact(id: Long): ContactWithDetails? {
        alignStaleDisplayNames()
        return contactDao.getById(id)
    }

    private suspend fun alignStaleDisplayNames() {
        if (displayNamesAligned.get()) return
        displayNameAlignment.withLock {
            if (displayNamesAligned.get()) return
            val updates = contactDao.getActiveContacts().mapNotNull { contact ->
                val extras = ContactScalarVcardExtras.decode(contact.vcardScalarExtrasJson)
                val aligned = VCardMapper.alignContactDisplayName(
                    prefix = contact.prefix,
                    givenName = contact.givenName,
                    middleName = contact.middleName,
                    familyName = contact.familyName,
                    suffix = contact.suffix,
                    nickname = contact.nickname,
                    organization = contact.organization,
                    storedDisplayName = contact.displayName,
                    scalarExtras = extras,
                )
                val nameChanged = aligned.displayName != contact.displayName
                val extrasChanged = scalarRawChanged(extras, aligned.scalarExtras)
                if (!nameChanged && !extrasChanged) {
                    null
                } else {
                    contact.copy(
                        displayName = aligned.displayName,
                        vcardScalarExtrasJson = if (extrasChanged) {
                            ContactScalarVcardExtras.encode(aligned.scalarExtras)
                        } else {
                            contact.vcardScalarExtrasJson
                        },
                    )
                }
            }
            if (updates.isNotEmpty()) {
                database.withTransaction {
                    contactDao.updateContacts(updates)
                }
            }
            displayNamesAligned.set(true)
        }
    }

    private fun scalarRawChanged(
        current: Map<String, VCardSyntaxExtras>,
        aligned: Map<String, VCardSyntaxExtras>,
    ): Boolean {
        fun raw(map: Map<String, VCardSyntaxExtras>, key: String) =
            map.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.rawValue
        return raw(current, "N") != raw(aligned, "N") || raw(current, "FN") != raw(aligned, "FN")
    }

    /** UID first. A display name is used only when exactly one contact has it. */
    suspend fun findContactIdForManager(uid: String?, displayName: String): Long? {
        val trimmedUid = uid?.trim().orEmpty()
        if (trimmedUid.isNotBlank()) {
            contactDao.getByRemoteUid(trimmedUid)?.id?.let { return it }
        }
        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) return null
        val ids = contactDao.findIdsByDisplayName(trimmedName)
        if (ids.size == 1) return ids.first()
        return null
    }

    fun observeLineManagerCandidates(): Flow<List<LineManagerListRow>> =
        contactDao.observeLineManagerCandidates()

    suspend fun toggleFavorite(id: Long): FavoriteToggle? {
        val existing = contactDao.getById(id)?.contact ?: return null
        val updated = contactAfterFavoriteToggle(existing, System.currentTimeMillis())
        contactDao.updateContact(updated)
        return FavoriteToggle(
            isFavorite = updated.isFavorite,
            dirtyBeforeToggle = existing.dirty,
            lastModified = updated.lastModified,
        )
    }

    /** Inserts the row and marks the contact dirty. False when that type and handle already exist. */
    suspend fun linkInstantMessaging(contactId: Long, type: String, handle: String): Boolean {
        val details = contactDao.getById(contactId) ?: return false
        val trimmedHandle = normalizeImHandle(handle)
        if (trimmedHandle.isBlank()) return false
        val imType = type.ifBlank { "other" }
        if (MessengerImMapping.alreadyLinked(details.imAccounts, imType, trimmedHandle)) {
            return false
        }
        contactDao.insertImAccounts(
            listOf(ImEntity(contactId = contactId, type = imType, value = trimmedHandle)),
        )
        contactDao.updateContact(
            details.contact.copy(dirty = true, lastModified = System.currentTimeMillis()),
        )
        syncDeviceIntegrations(contactId)
        return true
    }

    private fun normalizeImHandle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val digits = trimmed.filter { it.isDigit() }
        val phoneFormatted = trimmed.all {
            it.isDigit() || it == '+' || it.isWhitespace() || it == '-' || it == '(' || it == ')'
        }
        if (digits.length >= 7 && phoneFormatted) {
            return if (trimmed.trimStart().startsWith("+")) "+$digits" else digits
        }
        return trimmed
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean, markDirty: Boolean = false) {
        val existing = contactDao.getById(id)?.contact ?: return
        val updated = contactWithFavorite(
            contact = existing,
            isFavorite = isFavorite,
            markDirty = markDirty,
            now = System.currentTimeMillis(),
        )
        if (updated == existing) return
        contactDao.updateContact(updated)
    }

    suspend fun setTalkInviteVerification(contactId: Long, userId: String, cloudId: String?) {
        val existing = contactDao.getById(contactId)?.contact ?: return
        val trimmedUser = userId.trim()
        if (trimmedUser.isBlank()) return
        val trimmedCloud = cloudId?.trim()?.takeIf { it.isNotBlank() }
        if (
            existing.talkInviteVerifiedUserId.equals(trimmedUser, ignoreCase = true) &&
            existing.talkInviteVerifiedCloudId.orEmpty().equals(trimmedCloud.orEmpty(), ignoreCase = true)
        ) {
            return
        }
        contactDao.updateContact(
            existing.copy(
                talkInviteVerifiedUserId = trimmedUser,
                talkInviteVerifiedCloudId = trimmedCloud,
            ),
        )
    }

    suspend fun clearTalkInviteVerification(contactId: Long) {
        val existing = contactDao.getById(contactId)?.contact ?: return
        if (existing.talkInviteVerifiedUserId.isNullOrBlank()) return
        contactDao.updateContact(
            existing.copy(
                talkInviteVerifiedUserId = null,
                talkInviteVerifiedCloudId = null,
            ),
        )
    }

    suspend fun setNextcloudProfileLookup(contactId: Long, user: CloudUser) {
        val existing = contactDao.getById(contactId)?.contact ?: return
        val profile = CachedNextcloudProfile.fromCloudUser(user)
        val cloudKey = profile.cloudId
        if (cloudKey.isBlank()) return
        val cached = NextcloudProfileLookupCache.get(existing.nextcloudProfileLookupCache, cloudKey)
        if (
            cached?.displayName == profile.displayName &&
            cached.avatarUrl == profile.avatarUrl &&
            cached.userId.equals(profile.userId, ignoreCase = true) &&
            cached.cloudId.equals(profile.cloudId, ignoreCase = true)
        ) {
            return
        }
        contactDao.updateContact(
            existing.copy(
                nextcloudProfileLookupCache = NextcloudProfileLookupCache.withEntry(
                    existing.nextcloudProfileLookupCache,
                    cloudKey,
                    profile,
                ),
            ),
        )
    }

    suspend fun clearNextcloudProfileLookup(contactId: Long, cloudId: String) {
        val existing = contactDao.getById(contactId)?.contact ?: return
        val updated = NextcloudProfileLookupCache.withoutKey(existing.nextcloudProfileLookupCache, cloudId)
        if (updated == existing.nextcloudProfileLookupCache) return
        contactDao.updateContact(existing.copy(nextcloudProfileLookupCache = updated))
    }

    suspend fun setProviderIntegrationsCache(
        contactId: Long,
        phones: List<String>,
        groups: List<ContactsProviderIntegrations.AppGroup>,
    ) {
        val existing = contactDao.getById(contactId)?.contact ?: return
        val updated = ProviderIntegrationsCache.withGroups(
            existing.providerIntegrationsCache,
            phones,
            groups,
        )
        if (updated == existing.providerIntegrationsCache) return
        contactDao.updateContact(existing.copy(providerIntegrationsCache = updated))
        ContactsProviderIntegrations.cacheAppGroups(phones, groups)
    }

    suspend fun refreshWebsiteLogo(contactId: Long, websiteUrl: String): CachedWebsiteLogo? {
        val existing = contactDao.getById(contactId)?.contact ?: return null
        val websiteKey = WebsiteLogoCache.normalizeWebsiteKey(websiteUrl)
        if (websiteKey.isBlank()) return null
        val cached = WebsiteLogoCache.get(existing.websiteLogoCache, websiteUrl)
        val result = websiteFaviconFetcher.fetch(
            websiteUrl = websiteUrl,
            cachedIconSourceUrl = cached?.iconSourceUrl,
            etag = cached?.etag,
            lastModified = cached?.lastModified,
        )
        when (result) {
            WebsiteFaviconFetchResult.NotModified -> return cached?.takeIf {
                websiteLogoStore.hasLogo(it.fileName)
            }
            WebsiteFaviconFetchResult.Failed -> return cached?.takeIf {
                websiteLogoStore.hasLogo(it.fileName)
            }
            is WebsiteFaviconFetchResult.Updated -> {
                val fileName = websiteLogoStore.saveLogo(contactId, websiteKey, result.bytes)
                    ?: return cached?.takeIf { websiteLogoStore.hasLogo(it.fileName) }
                val updatedLogo = CachedWebsiteLogo(
                    websiteUrl = websiteUrl.trim(),
                    iconSourceUrl = result.iconSourceUrl,
                    fileName = fileName,
                    etag = result.etag,
                    lastModified = result.lastModified,
                )
                val previousFile = cached?.fileName?.takeIf { it.isNotBlank() && it != fileName }
                if (
                    cached?.iconSourceUrl == updatedLogo.iconSourceUrl &&
                    cached.etag == updatedLogo.etag &&
                    cached.lastModified == updatedLogo.lastModified &&
                    cached.fileName == updatedLogo.fileName &&
                    websiteLogoStore.hasLogo(updatedLogo.fileName)
                ) {
                    return updatedLogo
                }
                contactDao.updateContact(
                    existing.copy(
                        websiteLogoCache = WebsiteLogoCache.withEntry(
                            existing.websiteLogoCache,
                            websiteUrl,
                            updatedLogo,
                        ),
                    ),
                )
                if (previousFile != null) websiteLogoStore.deleteLogo(previousFile)
                return updatedLogo
            }
        }
    }

    fun readWebsiteLogoBytes(fileName: String): ByteArray? = websiteLogoStore.readLogoBytes(fileName)

    fun hasWebsiteLogo(fileName: String): Boolean = websiteLogoStore.hasLogo(fileName)

    fun getPhotoBytes(contactId: Long): ByteArray? = photoStore.readPhoto(contactId)

    fun hasPhoto(contactId: Long): Boolean = photoStore.hasPhoto(contactId)

    suspend fun allCategories(): List<String> {
        val names = contactDao.getAllWithDetails().flatMap { details ->
            ContactCategories.parse(details.contact.categories)
        }
        return ContactCategories.distinctSorted(names)
    }

    suspend fun createContact(input: ContactInput, editFieldSyntaxEnabled: Boolean = false): Long =
        createContactWithRemoteUid(input, UUID.randomUUID().toString(), editFieldSyntaxEnabled)

    private suspend fun createContactWithRemoteUid(
        input: ContactInput,
        remoteUid: String,
        editFieldSyntaxEnabled: Boolean = false,
    ): Long {
        val contact = contactEntityFromInput(
            input = input,
            remoteUid = remoteUid,
            dirty = true,
            editFieldSyntaxEnabled = editFieldSyntaxEnabled,
        )
        val id = contactDao.insertContact(contact)
        saveDetails(id, input)
        savePhoto(id, input)
        syncDeviceIntegrations(id)
        return id
    }

    suspend fun updateContact(
        id: Long,
        input: ContactInput,
        editFieldSyntaxEnabled: Boolean? = null,
    ) {
        val existing = contactDao.getById(id)?.contact ?: return
        contactDao.updateContact(
            contactEntityFromInput(
                input = input,
                existing = existing,
                dirty = true,
                editFieldSyntaxEnabled = editFieldSyntaxEnabled ?: existing.editFieldSyntaxEnabled,
            ),
        )
        saveDetails(id, input)
        savePhoto(id, input)
        syncDeviceIntegrations(id)
    }

    suspend fun deleteContact(id: Long) {
        val existing = contactDao.getById(id)?.contact ?: return
        if (existing.remoteUrl != null) {
            contactDao.updateContact(
                existing.copy(
                    deleted = true,
                    dirty = true,
                    // lastModified is the delete time. The bin window starts here, not at sync.
                    lastModified = System.currentTimeMillis(),
                ),
            )
            removeDeviceIntegrations(id)
        } else {
            contactDao.deleteContact(id)
            photoStore.deletePhoto(id)
            removeDeviceIntegrations(id)
        }
    }

    fun observeDeletedContacts(): Flow<List<ContactEntity>> = contactDao.observeDeleted()

    /** Puts a soft-deleted contact back. Dirty so the next sync uploads it. */
    suspend fun restoreContact(id: Long) {
        val existing = contactDao.getById(id)?.contact ?: return
        if (!existing.deleted) return
        contactDao.updateContact(
            existing.copy(
                deleted = false,
                dirty = true,
                lastModified = System.currentTimeMillis(),
            ),
        )
        syncDeviceIntegrations(id)
    }

    suspend fun getPendingSync(): List<ContactEntity> = contactDao.getPendingSync()

    suspend fun hasContacts(): Boolean = contactDao.countActive() > 0

    suspend fun countServerLinked(): Int = contactDao.countServerLinked()

    suspend fun getByRemoteUrl(remoteUrl: String): ContactEntity? = contactDao.getByRemoteUrl(remoteUrl)

    /**
     * Stored hrefs are normalized once per sync. [index] answers later changes
     * from that map so equivalent URLs do not scan the address book again.
     */
    suspend fun openRemoteContactIndex(server: String): RemoteContactIndex {
        // Bin rows stay linked so a pull can see them and leave them alone.
        val contacts = contactDao.getAllServerLinkedContacts() +
            contactDao.getDeletedServerLinkedContacts()
        val updates = ArrayList<ContactEntity>()
        val normalized = ArrayList<ContactEntity>(contacts.size)
        for (contact in contacts) {
            val raw = contact.remoteUrl ?: continue
            val key = RemoteUrlNormalizer.storageKey(server, raw)
            if (key == raw) {
                normalized.add(contact)
            } else {
                val updated = contact.copy(remoteUrl = key)
                updates.add(updated)
                normalized.add(updated)
            }
        }
        if (updates.isNotEmpty()) {
            database.withTransaction {
                contactDao.updateContacts(updates)
            }
        }
        return RemoteContactIndex.build(server, normalized)
    }

    suspend fun findByRemoteReference(
        server: String,
        storageKey: String,
        index: RemoteContactIndex? = null,
    ): ContactEntity? {
        if (index != null) {
            index.find(server, storageKey)?.let { return it }
        }
        val key = RemoteUrlNormalizer.storageKey(server, storageKey)
        contactDao.getByRemoteUrl(key)?.let { return it }
        val fileName = RemoteUrlNormalizer.fileName(key)
        contactDao.findAllByRemoteFileName(fileName).forEach { candidate ->
            if (RemoteUrlNormalizer.same(server, candidate.remoteUrl, key)) {
                return candidate
            }
        }
        val uid = fileName.removeSuffix(".vcf").removeSuffix(".VCF")
        if (uid.isNotBlank()) {
            contactDao.getByRemoteUid(uid)?.let { return it }
            contactDao.findDeletedByRemoteUid(uid)?.let { return it }
        }
        contactDao.findDeletedByRemoteFileName(fileName).forEach { candidate ->
            if (RemoteUrlNormalizer.same(server, candidate.remoteUrl, key)) {
                return candidate
            }
        }
        return null
    }

    suspend fun upsertFromServer(
        server: String,
        contact: ContactEntity,
        phones: List<PhoneEntity>,
        emails: List<EmailEntity>,
        addresses: List<AddressEntity> = emptyList(),
        urls: List<UrlEntity> = emptyList(),
        imAccounts: List<ImEntity> = emptyList(),
        socialProfiles: List<SocialProfileEntity> = emptyList(),
        cloudIds: List<CloudIdEntity> = emptyList(),
        languages: List<LanguageEntity> = emptyList(),
        relatedNames: List<RelatedEntity> = emptyList(),
        customFields: List<CustomFieldEntity> = emptyList(),
        photoBytes: ByteArray? = null,
        index: RemoteContactIndex? = null,
        runDeviceIntegrations: Boolean = true,
    ): UpsertResult {
        val written = database.withTransaction {
            writeServerContact(
                server = server,
                contact = contact,
                phones = phones,
                emails = emails,
                addresses = addresses,
                urls = urls,
                imAccounts = imAccounts,
                socialProfiles = socialProfiles,
                cloudIds = cloudIds,
                languages = languages,
                relatedNames = relatedNames,
                customFields = customFields,
                photoBytes = photoBytes,
                index = index,
            )
        }
        when (written.photoAction) {
            ServerPhotoAction.Save -> photoBytes?.let { photoStore.savePhoto(written.contactId, it) }
            ServerPhotoAction.Delete -> photoStore.deletePhoto(written.contactId)
            ServerPhotoAction.Keep -> Unit
        }
        index?.put(server, written.stored)
        // A full sync writes the phone book and calendar once at the end. A lone upsert still does.
        if (runDeviceIntegrations) {
            syncDeviceIntegrations(written.contactId)
        }
        return UpsertResult(
            contactId = written.contactId,
            duplicateRemoteUrl = written.duplicateRemoteUrl,
        )
    }

    private suspend fun writeServerContact(
        server: String,
        contact: ContactEntity,
        phones: List<PhoneEntity>,
        emails: List<EmailEntity>,
        addresses: List<AddressEntity>,
        urls: List<UrlEntity>,
        imAccounts: List<ImEntity>,
        socialProfiles: List<SocialProfileEntity>,
        cloudIds: List<CloudIdEntity>,
        languages: List<LanguageEntity>,
        relatedNames: List<RelatedEntity>,
        customFields: List<CustomFieldEntity>,
        photoBytes: ByteArray?,
        index: RemoteContactIndex?,
    ): ServerContactWrite {
        val storageKey = contact.remoteUrl?.let { RemoteUrlNormalizer.storageKey(server, it) }
        val normalizedContact = if (storageKey != null) contact.copy(remoteUrl = storageKey) else contact
        var byUrl = storageKey?.let { findByRemoteReference(server, it, index) }
        val remoteUid = normalizedContact.remoteUid?.trim()?.takeIf { it.isNotEmpty() }
        var byUid = remoteUid?.let { contactDao.getByRemoteUid(it) }
        if (byUrl != null && byUid != null && byUrl.id != byUid.id) {
            val keeper = pickServerLinkedKeeper(byUrl, byUid)
            val loserId = if (keeper.id == byUrl.id) byUid.id else byUrl.id
            absorbServerDuplicate(loserId, keeper.id)
            index?.remove(loserId)
            byUrl = keeper
            byUid = keeper
        }
        val existing = byUrl ?: byUid
        val isDuplicateServerCopy = existing != null &&
            storageKey != null &&
            existing.remoteUrl != null &&
            !RemoteUrlNormalizer.same(server, existing.remoteUrl, storageKey)

        val stored = if (existing != null) {
            normalizedContact.copy(
                id = existing.id,
                remoteUrl = when {
                    isDuplicateServerCopy -> existing.remoteUrl
                    storageKey != null -> storageKey
                    else -> existing.remoteUrl
                },
                remoteUid = existing.remoteUid ?: normalizedContact.remoteUid,
                isFavorite = normalizedContact.isFavorite,
                talkInviteVerifiedUserId = existing.talkInviteVerifiedUserId,
                talkInviteVerifiedCloudId = existing.talkInviteVerifiedCloudId,
                nextcloudProfileLookupCache = existing.nextcloudProfileLookupCache,
                websiteLogoCache = existing.websiteLogoCache,
                providerIntegrationsCache = existing.providerIntegrationsCache,
                notesMarkdown = existing.notesMarkdown,
                editFieldSyntaxEnabled = existing.editFieldSyntaxEnabled,
                dirty = false,
                deleted = false,
            )
        } else {
            normalizedContact.copy(dirty = false, deleted = false)
        }
        val contactId = if (existing != null) {
            contactDao.updateContact(stored)
            existing.id
        } else {
            contactDao.insertContact(stored)
        }
        val storedWithId = stored.copy(id = contactId)
        contactDao.deletePhones(contactId)
        contactDao.deleteEmails(contactId)
        contactDao.deleteAddresses(contactId)
        contactDao.deleteUrls(contactId)
        contactDao.deleteImAccounts(contactId)
        contactDao.deleteSocialProfiles(contactId)
        contactDao.deleteCloudIds(contactId)
        contactDao.deleteLanguages(contactId)
        contactDao.deleteRelatedNames(contactId)
        contactDao.deleteCustomFields(contactId)
        if (phones.isNotEmpty()) {
            contactDao.insertPhones(phones.map { it.copy(contactId = contactId) })
        }
        if (emails.isNotEmpty()) {
            contactDao.insertEmails(emails.map { it.copy(contactId = contactId) })
        }
        if (addresses.isNotEmpty()) {
            contactDao.insertAddresses(addresses.map { it.copy(contactId = contactId) })
        }
        if (urls.isNotEmpty()) {
            contactDao.insertUrls(urls.map { it.copy(contactId = contactId) })
        }
        if (imAccounts.isNotEmpty()) {
            contactDao.insertImAccounts(imAccounts.map { it.copy(contactId = contactId) })
        }
        if (socialProfiles.isNotEmpty()) {
            contactDao.insertSocialProfiles(socialProfiles.map { it.copy(contactId = contactId) })
        }
        if (cloudIds.isNotEmpty()) {
            contactDao.insertCloudIds(cloudIds.map { it.copy(contactId = contactId) })
        }
        if (languages.isNotEmpty()) {
            contactDao.insertLanguages(languages.map { it.copy(contactId = contactId) })
        }
        if (relatedNames.isNotEmpty()) {
            contactDao.insertRelatedNames(relatedNames.map { it.copy(contactId = contactId) })
        }
        if (customFields.isNotEmpty()) {
            contactDao.insertCustomFields(customFields.map { it.copy(contactId = contactId) })
        }
        // A clean row tracks the server card. A dirty row may still have a photo that has not uploaded.
        val photoAction = when {
            photoBytes != null -> ServerPhotoAction.Save
            existing == null || !existing.dirty -> ServerPhotoAction.Delete
            else -> ServerPhotoAction.Keep
        }
        return ServerContactWrite(
            contactId = contactId,
            duplicateRemoteUrl = if (isDuplicateServerCopy) storageKey else null,
            photoAction = photoAction,
            stored = storedWithId,
        )
    }

    private data class ServerContactWrite(
        val contactId: Long,
        val duplicateRemoteUrl: String?,
        val photoAction: ServerPhotoAction,
        val stored: ContactEntity,
    )

    private enum class ServerPhotoAction { Save, Delete, Keep }

    data class UpsertResult(
        val contactId: Long,
        val duplicateRemoteUrl: String?,
    )

    suspend fun loadDuplicateGroups(): List<ContactDuplicates.ValueGroup> {
        val contacts = contactDao.listFingerprintContacts()
        val activeIds = contacts.mapTo(HashSet()) { it.id }
        val phones = HashMap<Long, MutableList<String>>()
        for (row in contactDao.listPhoneValues()) {
            if (row.contactId !in activeIds) continue
            phones.getOrPut(row.contactId) { mutableListOf() }.add(row.value)
        }
        val emails = HashMap<Long, MutableList<String>>()
        for (row in contactDao.listEmailValues()) {
            if (row.contactId !in activeIds) continue
            emails.getOrPut(row.contactId) { mutableListOf() }.add(row.value)
        }
        return ContactDuplicates.findValues(
            names = contacts.associate { it.id to it.displayName },
            phonesByContact = phones,
            emailsByContact = emails,
        )
    }

    /**
     * Keeps one contact, copies fields the way a sync merge does, then deletes
     * the others through [deleteContact] so server cards are removed on sync.
     * [keeperId] is the contact the merge screen put on the left. Without it,
     * the server card, then the newer edit, then the lower id is kept.
     */
    suspend fun mergeContacts(
        contactIds: List<Long>,
        keeperId: Long? = null,
        fieldsFromDuplicate: Set<MergeSingletonField> = emptySet(),
    ) {
        val ids = contactIds.distinct()
        if (ids.size < 2) return
        val loaded = ids.mapNotNull { contactDao.getById(it) }.filter { !it.contact.deleted }
        if (loaded.size < 2) return
        val chosenKeeper = keeperId?.takeIf { id -> loaded.any { it.contact.id == id } }
        val keeper = chosenKeeper ?: ContactDuplicates.pickMergeKeeperId(
            loaded.map { details ->
                ContactDuplicates.MergeCandidate(
                    id = details.contact.id,
                    hasRemote = !details.contact.remoteUrl.isNullOrBlank(),
                    lastModified = details.contact.lastModified,
                )
            },
        )
        val losers = loaded.filter { it.contact.id != keeper }
        val picks = if (losers.size == 1) fieldsFromDuplicate else emptySet()
        for (loser in losers) {
            val fresh = contactDao.getById(loser.contact.id) ?: continue
            if (fresh.contact.deleted) continue
            if (!applyDuplicateFields(
                    fresh,
                    keeper,
                    markDirty = true,
                    fieldsFromDuplicate = picks,
                    keepProfile = true,
                    keepFormat = true,
                )
            ) continue
            deleteContact(fresh.contact.id)
        }
        syncDeviceIntegrations(keeper)
    }

    suspend fun fingerprintIndex(): FingerprintIndex {
        val phones = contactDao.listPhoneValues().groupBy({ it.contactId }, { it.value })
        val emails = contactDao.listEmailValues().groupBy({ it.contactId }, { it.value })
        return FingerprintIndex.build(
            contacts = contactDao.listFingerprintContacts(),
            phonesByContact = phones,
            emailsByContact = emails,
        )
    }

    /**
     * Push drops a dirty row that has no uid. Assign one and keep it for the next attempt.
     */
    suspend fun ensureRemoteUid(contactId: Long): String? {
        val existing = contactDao.getById(contactId)?.contact ?: return null
        val uid = remoteUidForPush(existing.remoteUid, UUID.randomUUID().toString())
        if (uid != existing.remoteUid) {
            contactDao.updateContact(existing.copy(remoteUid = uid))
        }
        return uid
    }

    suspend fun findInFingerprintIndex(
        index: FingerprintIndex,
        details: ContactWithDetails,
    ): ContactWithDetails? {
        val id = index.find(details) ?: return null
        return contactDao.getById(id)
    }

    /**
     * Merges a local-only pending contact into an existing server-linked contact,
     * then removes the duplicate local row.
     */
    suspend fun mergePendingIntoExisting(pendingId: Long, existingId: Long) {
        if (pendingId == existingId) return
        val pendingDetails = contactDao.getById(pendingId) ?: return
        val existingDetails = contactDao.getById(existingId) ?: return
        val pending = pendingDetails.contact
        val existing = existingDetails.contact
        // A newer dirty pending row is the edit. Otherwise keep the keeper's non-blank scalars.
        val pendingWins = pending.dirty && pending.lastModified > existing.lastModified
        contactDao.updateContact(mergePendingScalars(existing, pending, pendingWins))
        mergeDuplicateDetails(pendingDetails, existingId)
        if (photoStore.hasPhoto(pendingId)) {
            val pendingPhoto = photoStore.readPhoto(pendingId)
            if (pendingPhoto != null && (pendingWins || !photoStore.hasPhoto(existingId))) {
                photoStore.savePhoto(existingId, pendingPhoto)
            }
        }
        removeDeviceIntegrations(pendingId)
        photoStore.deletePhoto(pendingId)
        contactDao.deleteContact(pendingId)
        syncDeviceIntegrations(existingId)
    }

    private fun mergePendingScalars(
        existing: ContactEntity,
        pending: ContactEntity,
        pendingWins: Boolean,
    ): ContactEntity {
        fun text(current: String, incoming: String): String =
            if (pendingWins) incoming else current.ifBlank { incoming }
        return existing.copy(
            displayName = text(existing.displayName, pending.displayName),
            prefix = text(existing.prefix, pending.prefix),
            givenName = text(existing.givenName, pending.givenName),
            middleName = text(existing.middleName, pending.middleName),
            familyName = text(existing.familyName, pending.familyName),
            suffix = text(existing.suffix, pending.suffix),
            nickname = text(existing.nickname, pending.nickname),
            phoneticGiven = text(existing.phoneticGiven, pending.phoneticGiven),
            phoneticFamily = text(existing.phoneticFamily, pending.phoneticFamily),
            organization = text(existing.organization, pending.organization),
            title = text(existing.title, pending.title),
            role = text(existing.role, pending.role),
            gender = text(existing.gender, pending.gender),
            birthday = text(existing.birthday, pending.birthday),
            birthplace = text(existing.birthplace, pending.birthplace),
            anniversary = text(existing.anniversary, pending.anniversary),
            deathDate = text(existing.deathDate, pending.deathDate),
            deathPlace = text(existing.deathPlace, pending.deathPlace),
            categories = text(existing.categories, pending.categories),
            notes = text(existing.notes, pending.notes),
            notesMarkdown = if (pendingWins) {
                pending.notesMarkdown
            } else {
                existing.notesMarkdown || pending.notesMarkdown
            },
            editFieldSyntaxEnabled = existing.editFieldSyntaxEnabled || pending.editFieldSyntaxEnabled,
            geo = text(existing.geo, pending.geo),
            timeZone = text(existing.timeZone, pending.timeZone),
            managersName = text(existing.managersName, pending.managersName),
            managersUid = text(existing.managersUid, pending.managersUid),
            relationshipToYou = text(existing.relationshipToYou, pending.relationshipToYou),
            vcardScalarExtrasJson = text(existing.vcardScalarExtrasJson, pending.vcardScalarExtrasJson),
            vcardUnknownLinesJson = text(existing.vcardUnknownLinesJson, pending.vcardUnknownLinesJson),
            isFavorite = existing.isFavorite || pending.isFavorite,
            talkInviteVerifiedUserId = existing.talkInviteVerifiedUserId ?: pending.talkInviteVerifiedUserId,
            talkInviteVerifiedCloudId = existing.talkInviteVerifiedCloudId ?: pending.talkInviteVerifiedCloudId,
            nextcloudProfileLookupCache = NextcloudProfileLookupCache.merge(
                existing.nextcloudProfileLookupCache,
                pending.nextcloudProfileLookupCache,
            ),
            websiteLogoCache = WebsiteLogoCache.merge(
                existing.websiteLogoCache,
                pending.websiteLogoCache,
            ),
            providerIntegrationsCache = ProviderIntegrationsCache.merge(
                existing.providerIntegrationsCache,
                pending.providerIntegrationsCache,
            ),
            dirty = true,
            lastModified = System.currentTimeMillis(),
        )
    }

    suspend fun linkPendingUploadsBeforePush() {
        val index = fingerprintIndex()
        val pending = contactDao.getPendingSync().filter { !it.deleted && it.remoteUrl == null }
        for (contact in pending) {
            val details = contactDao.getById(contact.id) ?: continue
            val matchId = index.find(details) ?: continue
            if (matchId == contact.id) continue
            val match = contactDao.getById(matchId) ?: continue
            if (match.contact.remoteUrl == null) continue
            mergePendingIntoExisting(contact.id, match.contact.id)
            index.remove(contact.id)
            contactDao.getById(match.contact.id)?.let { index.putContact(it) }
        }
    }

    suspend fun markDirty(id: Long) {
        contactDao.markDirty(id)
    }

    /** The server card is gone. Local fields and dirty stay so the next push can create it. */
    suspend fun detachMissingServerCard(id: Long) {
        val existing = contactDao.getById(id)?.contact ?: return
        contactDao.updateContact(contactAfterServerCardMissing(existing))
    }

    suspend fun clearDirtyIfUnchanged(id: Long, lastModified: Long) {
        contactDao.clearDirtyIfLastModified(id, lastModified)
    }

    suspend fun deduplicateLocalContacts(server: String) {
        normalizeStoredRemoteUrls(server)
        deduplicateByRemoteIdentity(server)
    }

    private suspend fun normalizeStoredRemoteUrls(server: String) {
        for (contact in contactDao.getAllServerLinkedContacts()) {
            val raw = contact.remoteUrl ?: continue
            val normalized = RemoteUrlNormalizer.storageKey(server, raw)
            if (normalized != raw) {
                contactDao.updateContact(contact.copy(remoteUrl = normalized))
            }
        }
    }

    private suspend fun deduplicateByRemoteIdentity(server: String) {
        // Export reads the full book after push. Duplicates only exist on rows the server already has.
        val serverLinked = contactDao.getServerLinkedWithDetails().toMutableList()
        fun replace(updated: ContactWithDetails) {
            val index = serverLinked.indexOfFirst { it.contact.id == updated.contact.id }
            if (index >= 0) serverLinked[index] = updated
        }
        fun drop(id: Long) {
            serverLinked.removeAll { it.contact.id == id }
        }
        suspend fun mergeGroup(group: List<ContactWithDetails>) {
            val keeper = group.maxByOrNull { serverLinkedScore(it) } ?: return
            for (duplicate in group) {
                if (duplicate.contact.id == keeper.contact.id) continue
                absorbServerDuplicate(duplicate.contact.id, keeper.contact.id)
                drop(duplicate.contact.id)
                contactDao.getById(keeper.contact.id)?.let { replace(it) }
            }
        }
        val byUid = serverLinked
            .mapNotNull { details ->
                details.contact.remoteUid?.trim()?.takeIf { it.isNotEmpty() }?.let { it.lowercase() to details }
            }
            .groupBy({ it.first }, { it.second })
        for ((_, group) in byUid) {
            if (group.size < 2) continue
            mergeGroup(group)
        }
        val byUrl = serverLinked.groupBy { details ->
            RemoteUrlNormalizer.storageKey(server, details.contact.remoteUrl!!)
        }
        for ((_, group) in byUrl) {
            if (group.size < 2) continue
            mergeGroup(group)
        }
    }

    private fun serverLinkedScore(details: ContactWithDetails): Int {
        val contact = details.contact
        var score = 0
        if (!contact.etag.isNullOrBlank()) score += 20
        if (!contact.remoteUid.isNullOrBlank()) score += 10
        score += details.phones.size + details.emails.size + details.addresses.size
        if (!contact.talkInviteVerifiedUserId.isNullOrBlank()) score += 15
        if (photoStore.hasPhoto(contact.id)) score += 5
        score += (contact.lastModified / 1_000).toInt()
        return score
    }

    private fun pickServerLinkedKeeper(a: ContactEntity, b: ContactEntity): ContactEntity {
        fun entityScore(c: ContactEntity): Int {
            var score = 0
            if (!c.etag.isNullOrBlank()) score += 20
            if (!c.remoteUid.isNullOrBlank()) score += 10
            if (!c.talkInviteVerifiedUserId.isNullOrBlank()) score += 15
            score += (c.lastModified / 1_000).toInt()
            return score
        }
        return if (entityScore(b) > entityScore(a)) b else a
    }

    private suspend fun absorbServerDuplicate(loserId: Long, winnerId: Long) {
        if (loserId == winnerId) return
        val loserDetails = contactDao.getById(loserId) ?: return
        if (!applyDuplicateFields(loserDetails, winnerId, markDirty = false)) return
        removeDeviceIntegrations(loserId)
        photoStore.deletePhoto(loserId)
        contactDao.deleteContact(loserId)
        syncDeviceIntegrations(winnerId)
    }

    /**
     * Copies list fields onto the keeper and applies singleton choices.
     * Does not delete the extra contact.
     * [fieldsFromDuplicate] selects differing singleton values from [loserDetails].
     * Blanks are filled from the other contact. Unset choices keep the keeper.
     * The merge screen sets [keepProfile] and [keepFormat] so the left contact's
     * photo, nickname, phonetic names, and display format stay put. A chosen
     * display name still brings that contact's name parts.
     */
    private suspend fun applyDuplicateFields(
        loserDetails: ContactWithDetails,
        winnerId: Long,
        markDirty: Boolean,
        fieldsFromDuplicate: Set<MergeSingletonField> = emptySet(),
        keepProfile: Boolean = false,
        keepFormat: Boolean = false,
    ): Boolean {
        if (loserDetails.contact.id == winnerId) return false
        val winnerDetails = contactDao.getById(winnerId) ?: return false
        val loser = loserDetails.contact
        val winner = winnerDetails.contact
        val mergedFields = ContactMerge.mergeScalars(
            keeper = winner,
            duplicate = loser,
            fromDuplicate = fieldsFromDuplicate,
            keeperPhotoDigest = photoStore.photoDigest(winnerId),
            duplicatePhotoDigest = photoStore.photoDigest(loser.id),
            keepProfile = keepProfile,
            keepFormat = keepFormat,
        )
        val mergedWinner = mergedFields.contact.copy(
            talkInviteVerifiedUserId = winner.talkInviteVerifiedUserId ?: loser.talkInviteVerifiedUserId,
            talkInviteVerifiedCloudId = winner.talkInviteVerifiedCloudId ?: loser.talkInviteVerifiedCloudId,
            nextcloudProfileLookupCache = NextcloudProfileLookupCache.merge(
                winner.nextcloudProfileLookupCache,
                loser.nextcloudProfileLookupCache,
            ),
            websiteLogoCache = WebsiteLogoCache.merge(
                winner.websiteLogoCache,
                loser.websiteLogoCache,
            ),
            providerIntegrationsCache = ProviderIntegrationsCache.merge(
                winner.providerIntegrationsCache,
                loser.providerIntegrationsCache,
            ),
            isFavorite = winner.isFavorite || loser.isFavorite,
            remoteUid = winner.remoteUid?.takeIf { it.isNotBlank() } ?: loser.remoteUid,
            etag = winner.etag?.takeIf { it.isNotBlank() } ?: loser.etag,
            editFieldSyntaxEnabled = if (keepFormat) {
                winner.editFieldSyntaxEnabled
            } else {
                winner.editFieldSyntaxEnabled || loser.editFieldSyntaxEnabled
            },
            vcardUnknownLinesJson = mergeUnknownLines(winner.vcardUnknownLinesJson, loser.vcardUnknownLinesJson),
            dirty = if (markDirty) true else winner.dirty,
            lastModified = if (markDirty) System.currentTimeMillis() else winner.lastModified,
        )
        contactDao.updateContact(mergedWinner)
        mergeDuplicateDetails(loserDetails, winnerId)
        if (mergedFields.copyPhotoFromDuplicate) {
            photoStore.readPhoto(loser.id)?.let { photoStore.savePhoto(winnerId, it) }
        }
        return true
    }

    private suspend fun mergeDuplicateDetails(loser: ContactWithDetails, winnerId: Long) {
        val winner = contactDao.getById(winnerId) ?: return
        val existingPhones = winner.phones.map { normalizePhoneKey(it.value) }.toSet()
        val newPhones = loser.phones.filter { normalizePhoneKey(it.value) !in existingPhones }
        if (newPhones.isNotEmpty()) {
            contactDao.insertPhones(newPhones.map { it.copy(contactId = winnerId) })
        }
        val existingEmails = winner.emails.map { it.value.lowercase().trim() }.toSet()
        val newEmails = loser.emails.filter { it.value.lowercase().trim() !in existingEmails }
        if (newEmails.isNotEmpty()) {
            contactDao.insertEmails(newEmails.map { it.copy(contactId = winnerId) })
        }
        val existingAddresses = winner.addresses.map { addressFingerprint(it) }.toSet()
        val newAddresses = loser.addresses.filter { addressFingerprint(it) !in existingAddresses }
        if (newAddresses.isNotEmpty()) {
            contactDao.insertAddresses(newAddresses.map { it.copy(contactId = winnerId) })
        }
        val existingUrls = winner.urls.map { it.value.lowercase().trim() }.toSet()
        val newUrls = loser.urls.filter { it.value.lowercase().trim() !in existingUrls }
        if (newUrls.isNotEmpty()) {
            contactDao.insertUrls(newUrls.map { it.copy(contactId = winnerId) })
        }
        val existingIm = winner.imAccounts.map { imKey(it.type, it.value) }.toSet()
        val newIm = loser.imAccounts.filter { imKey(it.type, it.value) !in existingIm }
        if (newIm.isNotEmpty()) {
            contactDao.insertImAccounts(newIm.map { it.copy(contactId = winnerId) })
        }
        val existingSocial = winner.socialProfiles.map { imKey(it.type, it.value) }.toSet()
        val newSocial = loser.socialProfiles.filter { imKey(it.type, it.value) !in existingSocial }
        if (newSocial.isNotEmpty()) {
            contactDao.insertSocialProfiles(newSocial.map { it.copy(contactId = winnerId) })
        }
        val existingCloud = winner.cloudIds.map { imKey(it.type, it.value) }.toSet()
        val newCloud = loser.cloudIds.filter { imKey(it.type, it.value) !in existingCloud }
        if (newCloud.isNotEmpty()) {
            contactDao.insertCloudIds(newCloud.map { it.copy(contactId = winnerId) })
        }
        val existingLang = winner.languages.map { it.value.lowercase().trim() }.toSet()
        val newLang = loser.languages.filter { it.value.lowercase().trim() !in existingLang }
        if (newLang.isNotEmpty()) {
            contactDao.insertLanguages(newLang.map { it.copy(contactId = winnerId) })
        }
        val existingRelated = winner.relatedNames.map { imKey(it.type, it.value) }.toSet()
        val newRelated = loser.relatedNames.filter { imKey(it.type, it.value) !in existingRelated }
        if (newRelated.isNotEmpty()) {
            contactDao.insertRelatedNames(newRelated.map { it.copy(contactId = winnerId) })
        }
        val existingCustom = winner.customFields.map { customFieldMergeKey(it) }.toSet()
        val newCustom = loser.customFields.filter { customFieldMergeKey(it) !in existingCustom }
        if (newCustom.isNotEmpty()) {
            contactDao.insertCustomFields(newCustom.map { it.copy(contactId = winnerId) })
        }
    }

    private fun normalizePhoneKey(value: String): String =
        value.filter { it.isDigit() || it == '+' }

    private fun imKey(type: String, value: String): String =
        "${type.lowercase().trim()}::${value.lowercase().trim()}"


    private fun addressFingerprint(address: AddressEntity): String = listOf(
        address.type,
        address.poBox,
        address.extendedAddress,
        address.street,
        address.city,
        address.region,
        address.postalCode,
        address.country,
    ).joinToString("|") { it.lowercase().trim() }

    suspend fun markSynced(contact: ContactEntity, etag: String?, remoteUrl: String?) {
        contactDao.updateContact(
            contact.copy(
                etag = etag,
                remoteUrl = remoteUrl ?: contact.remoteUrl,
                dirty = false,
                deleted = false,
            ),
        )
    }

    suspend fun removeByRemoteUrl(remoteUrl: String, now: Long = System.currentTimeMillis()) {
        val existing = contactDao.getByRemoteUrl(remoteUrl)
            ?: contactDao.findByRemoteFileName(RemoteUrlNormalizer.fileName(remoteUrl))
            ?: return
        if (shouldKeepDeletedContactAfterServerGone(existing, now)) {
            contactDao.updateContact(contactAfterServerDeleteKept(existing))
            return
        }
        purgeDeletedContact(existing.id)
    }

    /** The server card is gone and the bin window is still open. Href and etag go; the row stays. */
    suspend fun keepDeletedContactAfterServerGone(id: Long) {
        val existing = contactDao.getById(id)?.contact ?: return
        if (!existing.deleted) return
        contactDao.updateContact(contactAfterServerDeleteKept(existing))
    }

    /**
     * Drops soft-deleted rows whose server card is already gone and whose delete is older than the bin window.
     * A delete that has not reached the server yet still has a remote URL and is left alone.
     */
    suspend fun purgeExpiredBinContacts(now: Long = System.currentTimeMillis()) {
        val deletedAtOrBefore = now - BIN_RETENTION_MS
        for (contact in contactDao.getBinContactsReadyToPurge(deletedAtOrBefore)) {
            if (contact.remoteUrl != null) continue
            purgeDeletedContact(contact.id)
        }
    }

    suspend fun purgeDeletedContact(id: Long) {
        // Export and calendar links are keyed by the local id, so clear them before the row is gone.
        removeDeviceIntegrations(id)
        photoStore.deletePhoto(id)
        contactDao.deleteContact(id)
    }

    suspend fun clearAll() {
        contactDao.deleteAll()
        photoStore.clearAll()
    }

    data class VcfExportResult(
        val vcardText: String,
        val contactCount: Int,
    )

    data class VcfImportResult(
        val imported: Int,
        val updated: Int,
        val failed: Int,
        val contactIds: List<Long> = emptyList(),
    )

    suspend fun exportAllToVcf(): VcfExportResult {
        val contacts = contactDao.getAllWithDetails()
        val vcardText = VCardMapper.exportAllToVcf(contacts) { id -> getPhotoBytes(id) }
        return VcfExportResult(vcardText = vcardText, contactCount = contacts.size)
    }

    suspend fun importFromVcf(vcardText: String): VcfImportResult {
        val trimmed = vcardText.trim()
        if (trimmed.isEmpty()) {
            return VcfImportResult(imported = 0, updated = 0, failed = 0)
        }
        val vcards = try {
            VCardMapper.parseAllVCards(trimmed)
        } catch (_: Exception) {
            return VcfImportResult(imported = 0, updated = 0, failed = 1)
        }
        if (vcards.isEmpty()) {
            return VcfImportResult(imported = 0, updated = 0, failed = 0)
        }
        var imported = 0
        var updated = 0
        var failed = 0
        val contactIds = ArrayList<Long>()
        val index = fingerprintIndex()
        for (vcard in vcards) {
            try {
                val placeholderUrl = "vcf-import:${vcard.uid?.value ?: UUID.randomUUID()}"
                val parsed = VCardMapper.fromVCardObject(vcard, placeholderUrl, etag = null)
                val input = VCardMapper.inputFromParsed(parsed)
                if (!input.hasImportableContent()) {
                    failed++
                    continue
                }
                val phones = input.phones.map { it.value }
                val emails = input.emails.map { it.value }
                val remoteUid = parsed.contact.remoteUid?.trim()?.takeIf { it.isNotEmpty() }
                val existingId = remoteUid?.let { contactDao.getByRemoteUid(it)?.id }
                    ?: index.find(input.displayName, phones, emails)
                if (existingId != null) {
                    updateContact(existingId, input)
                    if (remoteUid != null) {
                        contactDao.getById(existingId)?.contact?.let { existing ->
                            if (existing.remoteUid.isNullOrBlank()) {
                                contactDao.updateContact(existing.copy(remoteUid = remoteUid, dirty = true))
                            }
                        }
                    }
                    index.put(existingId, input.displayName, phones, emails)
                    contactIds.add(existingId)
                    updated++
                } else {
                    val id = createContactWithRemoteUid(input, remoteUid ?: UUID.randomUUID().toString())
                    index.put(id, input.displayName, phones, emails)
                    contactIds.add(id)
                    imported++
                }
            } catch (_: Exception) {
                failed++
            }
        }
        return VcfImportResult(
            imported = imported,
            updated = updated,
            failed = failed,
            contactIds = contactIds,
        )
    }

    suspend fun findContactIdByPhone(raw: String): Long? {
        val digits = MessagingContactsIntents.digitsOnly(raw)
        if (digits.isEmpty()) return null
        return contactDao.listPhoneValues().firstOrNull { row ->
            MessagingContactsIntents.phonesMatch(digits, MessagingContactsIntents.digitsOnly(row.value))
        }?.contactId
    }

    suspend fun findContactIdByEmail(raw: String): Long? {
        val email = raw.trim()
        if (email.isEmpty()) return null
        return contactDao.listEmailValues().firstOrNull { row ->
            row.value.trim().equals(email, ignoreCase = true)
        }?.contactId
    }

    data class BulkImportResult(
        val imported: Int,
        val skipped: Int,
        val photosImported: Int,
        val photosBackfilled: Int,
    )

    suspend fun importContacts(inputs: List<ContactInput>): BulkImportResult {
        // Name, phones, and emails decide a match. Other rows stay until that one contact is refreshed.
        val rows = contactDao.listImportMatchContacts()
        val phonesById = contactDao.listPhoneValues()
            .groupBy({ it.contactId }, { it.value })
            .toMutableMap()
        val emailsById = contactDao.listEmailValues()
            .groupBy({ it.contactId }, { it.value })
            .toMutableMap()
        val namesById = rows.associate { it.id to it.displayName }.toMutableMap()
        val fingerprints = LinkedHashMap<String, Long>(rows.size)
        for (row in rows) {
            fingerprints[ContactFingerprint.from(
                displayName = row.displayName,
                phones = phonesById[row.id].orEmpty(),
                emails = emailsById[row.id].orEmpty(),
            )] = row.id
        }
        var imported = 0
        var skipped = 0
        var photosImported = 0
        var photosBackfilled = 0
        for (input in inputs) {
            val fingerprint = ContactFingerprint.from(input)
            val existingId = matchDeviceImport(
                input = input,
                namesById = namesById,
                phonesById = phonesById,
                emailsById = emailsById,
                fingerprints = fingerprints,
            )
            if (existingId != null) {
                refreshDeviceImport(existingId, input)
                if (input.photoBytes != null && !photoStore.hasPhoto(existingId)) {
                    photoStore.savePhoto(existingId, input.photoBytes)
                    contactDao.getById(existingId)?.contact?.let { contact ->
                        contactDao.updateContact(
                            contact.copy(
                                dirty = true,
                                lastModified = System.currentTimeMillis(),
                            ),
                        )
                    }
                    photosBackfilled++
                }
                contactDao.getById(existingId)?.let { updated ->
                    namesById[existingId] = updated.contact.displayName
                    phonesById[existingId] = updated.phones.map { it.value }
                    emailsById[existingId] = updated.emails.map { it.value }
                    fingerprints.entries.removeAll { it.value == existingId }
                    fingerprints[ContactFingerprint.from(updated)] = existingId
                }
                skipped++
                continue
            }
            val id = createContact(input)
            if (input.photoBytes != null) photosImported++
            contactDao.getById(id)?.let { created ->
                namesById[id] = created.contact.displayName
                phonesById[id] = created.phones.map { it.value }
                emailsById[id] = created.emails.map { it.value }
            }
            fingerprints[fingerprint] = id
            imported++
        }
        return BulkImportResult(
            imported = imported,
            skipped = skipped,
            photosImported = photosImported,
            photosBackfilled = photosBackfilled,
        )
    }

    /**
     * Fingerprint first. The same display name plus a shared phone or email still
     * matches, so a changed device phone updates that contact instead of inserting another.
     */
    private fun matchDeviceImport(
        input: ContactInput,
        namesById: Map<Long, String>,
        phonesById: Map<Long, List<String>>,
        emailsById: Map<Long, List<String>>,
        fingerprints: Map<String, Long>,
    ): Long? {
        fingerprints[ContactFingerprint.from(input)]?.let { return it }
        val name = input.displayName.trim()
        if (name.isBlank() || name.equals("Unnamed", ignoreCase = true)) return null
        val phoneKeys = input.phones.map { normalizePhoneKey(it.value) }.filter { it.isNotBlank() }.toSet()
        val emailKeys = input.emails.map { it.value.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
        if (phoneKeys.isEmpty() && emailKeys.isEmpty()) return null
        val overlapped = namesById.filter { (id, displayName) ->
            displayName.equals(name, ignoreCase = true) &&
                (
                    phonesById[id].orEmpty().any { normalizePhoneKey(it) in phoneKeys } ||
                        emailsById[id].orEmpty().any { it.lowercase().trim() in emailKeys }
                    )
        }
        if (overlapped.size == 1) return overlapped.keys.first()
        return null
    }

    /** Device fields refresh. Server-only notes, extras, unknown lines, and custom fields stay. */
    private suspend fun refreshDeviceImport(existingId: Long, input: ContactInput) {
        val details = contactDao.getById(existingId) ?: return
        val contact = details.contact
        val givenName = input.givenName.ifBlank { contact.givenName }
        val familyName = input.familyName.ifBlank { contact.familyName }
        val organization = input.organization.ifBlank { contact.organization }
        val notes = if (input.notes.isNotBlank()) input.notes else contact.notes
        val namesChanged = givenName != contact.givenName || familyName != contact.familyName
        val changed = namesChanged ||
            organization != contact.organization ||
            notes != contact.notes
        var updated = contact
        if (changed) {
            updated = contact.copy(
                givenName = givenName,
                familyName = familyName,
                organization = organization,
                notes = notes,
            )
            if (namesChanged) {
                updated = updated.copy(displayName = displayNameFromParts(updated))
            }
        }
        val phonesChanged = unionDevicePhones(existingId, details.phones, input.phones)
        val emailsChanged = unionDeviceEmails(existingId, details.emails, input.emails)
        if (changed || phonesChanged || emailsChanged) {
            contactDao.updateContact(
                updated.copy(dirty = true, lastModified = System.currentTimeMillis()),
            )
        }
    }

    private fun displayNameFromParts(contact: ContactEntity): String =
        listOf(contact.prefix, contact.givenName, contact.middleName, contact.familyName, contact.suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { contact.nickname.ifBlank { contact.organization.ifBlank { "Unnamed" } } }

    private suspend fun unionDevicePhones(
        contactId: Long,
        existing: List<PhoneEntity>,
        incoming: List<com.nextcloud.contacts.data.local.VCardTypedFieldInput>,
    ): Boolean {
        val incomingByKey = linkedMapOf<String, com.nextcloud.contacts.data.local.VCardTypedFieldInput>()
        for (field in incoming) {
            if (field.value.isBlank()) continue
            val key = normalizePhoneKey(field.value)
            if (key.isBlank() || key in incomingByKey) continue
            incomingByKey[key] = field
        }
        val merged = mutableListOf<PhoneEntity>()
        val seen = mutableSetOf<String>()
        var changed = false
        for (phone in existing) {
            val key = normalizePhoneKey(phone.value)
            val device = incomingByKey[key]
            if (device != null) {
                val type = device.type.ifBlank { phone.type }
                if (phone.value != device.value || phone.type != type) changed = true
                merged.add(phone.copy(value = device.value, type = type))
            } else {
                merged.add(phone)
            }
            if (key.isNotBlank()) seen.add(key)
        }
        for ((key, device) in incomingByKey) {
            if (key in seen) continue
            changed = true
            merged.add(device.toPhoneEntity(contactId))
        }
        if (!changed) return false
        contactDao.deletePhones(contactId)
        if (merged.isNotEmpty()) {
            contactDao.insertPhones(merged.map { it.copy(id = 0, contactId = contactId) })
        }
        return true
    }

    private suspend fun unionDeviceEmails(
        contactId: Long,
        existing: List<EmailEntity>,
        incoming: List<com.nextcloud.contacts.data.local.VCardTypedFieldInput>,
    ): Boolean {
        val incomingByKey = linkedMapOf<String, com.nextcloud.contacts.data.local.VCardTypedFieldInput>()
        for (field in incoming) {
            val key = field.value.lowercase().trim()
            if (key.isBlank() || key in incomingByKey) continue
            incomingByKey[key] = field
        }
        val merged = mutableListOf<EmailEntity>()
        val seen = mutableSetOf<String>()
        var changed = false
        for (email in existing) {
            val key = email.value.lowercase().trim()
            val device = incomingByKey[key]
            if (device != null) {
                val type = device.type.ifBlank { email.type }
                if (email.value != device.value || email.type != type) changed = true
                merged.add(email.copy(value = device.value, type = type))
            } else {
                merged.add(email)
            }
            if (key.isNotBlank()) seen.add(key)
        }
        for ((key, device) in incomingByKey) {
            if (key in seen) continue
            changed = true
            merged.add(device.toEmailEntity(contactId))
        }
        if (!changed) return false
        contactDao.deleteEmails(contactId)
        if (merged.isNotEmpty()) {
            contactDao.insertEmails(merged.map { it.copy(id = 0, contactId = contactId) })
        }
        return true
    }

    private fun mergeUnknownLines(winnerRaw: String, loserRaw: String): String {
        val winner = VCardUnknownLines.decode(winnerRaw)
        val loser = VCardUnknownLines.decode(loserRaw)
        if (loser.isEmpty()) return winnerRaw
        if (winner.isEmpty()) return loserRaw
        val seen = winner.map { it.fingerprint() }.toMutableSet()
        val merged = winner.toMutableList()
        for (line in loser) {
            if (seen.add(line.fingerprint())) merged.add(line)
        }
        return VCardUnknownLines.encode(merged)
    }

    private suspend fun saveDetails(contactId: Long, input: ContactInput) {
        contactDao.deletePhones(contactId)
        contactDao.deleteEmails(contactId)
        contactDao.deleteAddresses(contactId)
        contactDao.deleteUrls(contactId)
        contactDao.deleteImAccounts(contactId)
        contactDao.deleteSocialProfiles(contactId)
        contactDao.deleteCloudIds(contactId)
        contactDao.deleteLanguages(contactId)
        contactDao.deleteRelatedNames(contactId)
        contactDao.deleteCustomFields(contactId)

        val phones = input.phones
            .filter { it.value.isNotBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toPhoneEntity(contactId) }
        val emails = input.emails
            .filter { it.value.isNotBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toEmailEntity(contactId) }
        val addresses = input.addresses
            .filter { !it.isBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toEntity(contactId) }
        val urls = input.urls
            .filter { it.value.isNotBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toUrlEntity(contactId) }
        val imAccounts = input.imAccounts
            .filter { it.value.isNotBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toImEntity(contactId) }
        val socialProfiles = input.socialProfiles
            .filter { it.value.isNotBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toSocialProfileEntity(contactId) }
        val cloudIds = input.cloudIds
            .filter { it.value.isNotBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toCloudIdEntity(contactId) }
        val languages = input.languages
            .filter { it.value.isNotBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toLanguageEntity(contactId) }
        val relatedNames = input.relatedNames
            .filter { it.value.isNotBlank() || it.syntax.hasPersistableMetadata() }
            .map { it.toRelatedEntity(contactId) }
        val customFields = input.customFields
            .filter { it.hasPersistableContent() }
            .map { it.sanitizePropertyName().toEntity(contactId) }

        if (phones.isNotEmpty()) contactDao.insertPhones(phones)
        if (emails.isNotEmpty()) contactDao.insertEmails(emails)
        if (addresses.isNotEmpty()) contactDao.insertAddresses(addresses)
        if (urls.isNotEmpty()) contactDao.insertUrls(urls)
        if (imAccounts.isNotEmpty()) contactDao.insertImAccounts(imAccounts)
        if (socialProfiles.isNotEmpty()) contactDao.insertSocialProfiles(socialProfiles)
        if (cloudIds.isNotEmpty()) contactDao.insertCloudIds(cloudIds)
        if (languages.isNotEmpty()) contactDao.insertLanguages(languages)
        if (relatedNames.isNotEmpty()) contactDao.insertRelatedNames(relatedNames)
        if (customFields.isNotEmpty()) contactDao.insertCustomFields(customFields)
    }

    /**
     * Stores the per-contact vCard editor flag without marking the contact dirty
     * and without changing lastModified, so sync does not upload it.
     */
    suspend fun setEditFieldSyntaxEnabled(id: Long, enabled: Boolean) {
        contactDao.setEditFieldSyntaxEnabled(id, enabled)
    }

    private fun contactEntityFromInput(
        input: ContactInput,
        existing: ContactEntity? = null,
        remoteUid: String? = existing?.remoteUid,
        dirty: Boolean = existing?.dirty ?: false,
        editFieldSyntaxEnabled: Boolean = existing?.editFieldSyntaxEnabled ?: false,
    ): ContactEntity {
        val base = existing ?: ContactEntity(remoteUid = remoteUid)
        val aligned = VCardMapper.alignContactDisplayName(
            prefix = input.prefix,
            givenName = input.givenName,
            middleName = input.middleName,
            familyName = input.familyName,
            suffix = input.suffix,
            nickname = input.nickname,
            organization = input.organization,
            storedDisplayName = input.displayName,
            scalarExtras = input.scalarVcardExtras,
            previousNickname = existing?.nickname,
            previousOrganization = existing?.organization,
        )
        return base.copy(
            displayName = aligned.displayName,
            prefix = input.prefix,
            givenName = input.givenName,
            middleName = input.middleName,
            familyName = input.familyName,
            suffix = input.suffix,
            nickname = input.nickname,
            phoneticGiven = input.phoneticGiven,
            phoneticFamily = input.phoneticFamily,
            organization = input.organization,
            title = input.title,
            role = input.role,
            gender = input.gender,
            birthday = input.birthday,
            birthplace = input.birthplace,
            anniversary = input.anniversary,
            deathDate = input.deathDate,
            deathPlace = input.deathPlace,
            categories = input.categories,
            notes = input.notes,
            notesMarkdown = input.notesMarkdown,
            geo = input.geo,
            timeZone = input.timeZone,
            managersName = input.managersName,
            managersUid = input.managersUid,
            relationshipToYou = input.relationshipToYou,
            vcardScalarExtrasJson = ContactScalarVcardExtras.encode(aligned.scalarExtras),
            vcardUnknownLinesJson = input.vcardUnknownLinesJson.ifBlank {
                existing?.vcardUnknownLinesJson.orEmpty()
            },
            editFieldSyntaxEnabled = editFieldSyntaxEnabled,
            lastModified = System.currentTimeMillis(),
            dirty = dirty,
        )
    }

    suspend fun updateContactNotes(id: Long, notes: String, notesMarkdown: Boolean) {
        val contact = contactDao.getById(id) ?: return
        val input = VCardMapper.inputFromContact(contact).copy(
            notes = notes,
            notesMarkdown = notesMarkdown,
        )
        updateContact(id, input)
    }

    private fun savePhoto(contactId: Long, input: ContactInput) {
        when {
            input.removePhoto -> photoStore.deletePhoto(contactId)
            input.photoBytes != null -> photoStore.savePhoto(contactId, input.photoBytes)
            else -> {
                val raw = input.scalarVcardExtras["PHOTO"]?.rawValue?.trim().orEmpty()
                if (raw.isNotBlank() && com.nextcloud.contacts.data.remote.VCardPhotoHelper.looksLikeUri(raw)) {
                    photoStore.deletePhoto(contactId)
                }
            }
        }
    }
}

data class FavoriteToggle(
    val isFavorite: Boolean,
    val dirtyBeforeToggle: Boolean,
    val lastModified: Long,
)

/** Update 404. The href and etag go; the local edit stays dirty so a later push creates the card. */
internal fun contactAfterServerCardMissing(contact: ContactEntity): ContactEntity =
    contact.copy(remoteUrl = null, etag = null)

/** Local star edit. Dirty so a sync that overlaps, or a process death, cannot drop it. */
internal fun contactAfterFavoriteToggle(contact: ContactEntity, now: Long): ContactEntity =
    contact.copy(
        isFavorite = !contact.isFavorite,
        dirty = true,
        lastModified = now,
    )

/**
 * [markDirty] is the failed-upload revert: the star on screen stays the one a later push sends.
 * Remote favorite updates leave the dirty flag alone.
 */
internal fun contactWithFavorite(
    contact: ContactEntity,
    isFavorite: Boolean,
    markDirty: Boolean,
    now: Long,
): ContactEntity = contact.copy(
    isFavorite = isFavorite,
    dirty = if (markDirty) true else contact.dirty,
    lastModified = if (markDirty) now else contact.lastModified,
)

/** A uid already on the row stays. A blank one is filled with [assigned] and then kept. */
internal fun remoteUidForPush(existing: String?, assigned: String): String {
    val trimmed = existing?.trim().orEmpty()
    return trimmed.ifEmpty { assigned }
}
