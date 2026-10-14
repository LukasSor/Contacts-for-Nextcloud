package com.nextcloud.contacts.platform

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.nextcloudCalendarEventLinksStore by preferencesDataStore("nextcloud_calendar_event_links")

class NextcloudCalendarEventLinks(context: Context) {
    private val dataStore = context.applicationContext.nextcloudCalendarEventLinksStore

    suspend fun getCollectionHref(): String? =
        dataStore.data.first()[KEY_COLLECTION_HREF]

    suspend fun setCollectionHref(href: String) {
        dataStore.edit { prefs ->
            prefs[KEY_COLLECTION_HREF] = href
        }
    }

    suspend fun clearCollectionMetadata() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_COLLECTION_HREF)
            prefs.remove(KEY_WE_CREATED_COLLECTION)
        }
    }

    suspend fun getWeCreatedCollection(): Boolean =
        dataStore.data.first()[KEY_WE_CREATED_COLLECTION] ?: false

    suspend fun setWeCreatedCollection(created: Boolean) {
        dataStore.edit { prefs ->
            if (created) {
                prefs[KEY_WE_CREATED_COLLECTION] = true
            } else {
                prefs.remove(KEY_WE_CREATED_COLLECTION)
            }
        }
    }

    suspend fun getEventHref(appContactId: Long, kind: ContactCalendarEventKind): String? =
        dataStore.data.first()[eventHrefKey(appContactId, kind)]

    suspend fun getFingerprint(appContactId: Long, kind: ContactCalendarEventKind): String? =
        dataStore.data.first()[fingerprintKey(appContactId, kind)]

    suspend fun setLink(
        appContactId: Long,
        kind: ContactCalendarEventKind,
        eventHref: String,
        fingerprint: String,
    ) {
        dataStore.edit { prefs ->
            prefs[eventHrefKey(appContactId, kind)] = eventHref
            prefs[fingerprintKey(appContactId, kind)] = fingerprint
        }
    }

    suspend fun removeAppContact(appContactId: Long, kind: ContactCalendarEventKind) {
        dataStore.edit { prefs ->
            prefs.remove(eventHrefKey(appContactId, kind))
            prefs.remove(fingerprintKey(appContactId, kind))
        }
    }

    suspend fun clearKind(kind: ContactCalendarEventKind) {
        dataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { key ->
                key.name.startsWith("nc_href_${kind.storageKey}_") ||
                    key.name.startsWith("nc_fp_${kind.storageKey}_")
            }
            toRemove.forEach { prefs.remove(it) }
        }
    }

    suspend fun clearEventLinks() {
        dataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { key ->
                key.name.startsWith("nc_href_") || key.name.startsWith("nc_fp_")
            }
            toRemove.forEach { prefs.remove(it) }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun allAppContactIds(kind: ContactCalendarEventKind): List<Long> {
        val prefs = dataStore.data.first()
        val prefix = "nc_href_${kind.storageKey}_"
        return prefs.asMap().keys
            .filter { it.name.startsWith(prefix) }
            .mapNotNull { it.name.removePrefix(prefix).toLongOrNull() }
    }

    private fun eventHrefKey(appContactId: Long, kind: ContactCalendarEventKind) =
        stringPreferencesKey("nc_href_${kind.storageKey}_$appContactId")

    private fun fingerprintKey(appContactId: Long, kind: ContactCalendarEventKind) =
        stringPreferencesKey("nc_fp_${kind.storageKey}_$appContactId")

    companion object {
        private val KEY_COLLECTION_HREF = stringPreferencesKey("collection_href")
        private val KEY_WE_CREATED_COLLECTION = booleanPreferencesKey("we_created_collection")
    }
}
