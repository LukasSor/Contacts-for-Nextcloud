package com.nextcloud.contacts.data

import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.remote.RemoteUrlNormalizer

/**
 * One normalized url/uid map for a sync pass. Equivalent hrefs share a storage key,
 * so a change does not scan every server-linked row.
 */
class RemoteContactIndex private constructor(
    private val byNormalizedUrl: MutableMap<String, ContactEntity>,
    private val byUid: MutableMap<String, ContactEntity>,
    private val keysById: MutableMap<Long, Pair<String?, String?>>,
) {
    fun find(server: String, storageKey: String): ContactEntity? {
        val key = RemoteUrlNormalizer.storageKey(server, storageKey)
        byNormalizedUrl[key]?.let { return it }
        val fileName = RemoteUrlNormalizer.fileName(key)
        val uid = fileName.removeSuffix(".vcf").removeSuffix(".VCF")
        if (uid.isNotBlank()) {
            byUid[uid]?.let { return it }
        }
        return null
    }

    fun put(server: String, contact: ContactEntity) {
        remove(contact.id)
        val raw = contact.remoteUrl
        val urlKey = raw?.let { RemoteUrlNormalizer.storageKey(server, it) }
        if (urlKey != null && urlKey !in byNormalizedUrl) {
            byNormalizedUrl[urlKey] = contact
        }
        val uid = contact.remoteUid?.trim()?.takeIf { it.isNotEmpty() }
        if (uid != null && uid !in byUid) {
            byUid[uid] = contact
        }
        keysById[contact.id] = urlKey to uid
    }

    fun remove(id: Long) {
        val keys = keysById.remove(id) ?: return
        val (urlKey, uid) = keys
        if (urlKey != null && byNormalizedUrl[urlKey]?.id == id) {
            byNormalizedUrl.remove(urlKey)
        }
        if (uid != null && byUid[uid]?.id == id) {
            byUid.remove(uid)
        }
    }

    companion object {
        fun build(server: String, contacts: List<ContactEntity>): RemoteContactIndex {
            val index = RemoteContactIndex(mutableMapOf(), mutableMapOf(), mutableMapOf())
            for (contact in contacts) {
                if (contact.remoteUrl != null) index.put(server, contact)
            }
            return index
        }
    }
}
