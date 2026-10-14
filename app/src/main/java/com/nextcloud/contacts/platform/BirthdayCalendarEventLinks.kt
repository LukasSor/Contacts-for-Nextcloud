package com.nextcloud.contacts.platform

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

enum class ContactCalendarEventKind(val storageKey: String) {
    BIRTHDAY("birthday"),
    ANNIVERSARY("anniversary"),
    DEATH("death"),
}

private val Context.birthdayCalendarEventLinksStore by preferencesDataStore("birthday_calendar_event_links")

class BirthdayCalendarEventLinks(context: Context) {
    private val dataStore = context.applicationContext.birthdayCalendarEventLinksStore

    suspend fun getEventId(appContactId: Long, kind: ContactCalendarEventKind): Long? {
        val prefs = dataStore.data.first()
        return prefs[eventKey(appContactId, kind)]
    }

    suspend fun getFingerprint(appContactId: Long, kind: ContactCalendarEventKind): String? {
        val prefs = dataStore.data.first()
        return prefs[fingerprintKey(appContactId, kind)]
    }

    suspend fun setLink(
        appContactId: Long,
        kind: ContactCalendarEventKind,
        eventId: Long,
        fingerprint: String,
    ) {
        dataStore.edit { prefs ->
            prefs[eventKey(appContactId, kind)] = eventId
            prefs[fingerprintKey(appContactId, kind)] = fingerprint
        }
    }

    suspend fun removeAppContact(appContactId: Long, kind: ContactCalendarEventKind) {
        dataStore.edit { prefs ->
            prefs.remove(eventKey(appContactId, kind))
            prefs.remove(fingerprintKey(appContactId, kind))
        }
    }

    suspend fun removeAppContactAllKinds(appContactId: Long) {
        ContactCalendarEventKind.entries.forEach { kind ->
            removeAppContact(appContactId, kind)
        }
    }

    suspend fun clearKind(kind: ContactCalendarEventKind) {
        dataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { key ->
                key.name.startsWith("event_${kind.storageKey}_") ||
                    key.name.startsWith("fp_${kind.storageKey}_")
            }
            toRemove.forEach { prefs.remove(it) }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun allAppContactIds(kind: ContactCalendarEventKind): List<Long> {
        val prefs = dataStore.data.first()
        val prefix = "event_${kind.storageKey}_"
        return prefs.asMap().keys
            .filter { it.name.startsWith(prefix) }
            .mapNotNull { it.name.removePrefix(prefix).toLongOrNull() }
    }

    private fun eventKey(appContactId: Long, kind: ContactCalendarEventKind) =
        longPreferencesKey("event_${kind.storageKey}_$appContactId")

    private fun fingerprintKey(appContactId: Long, kind: ContactCalendarEventKind) =
        stringPreferencesKey("fp_${kind.storageKey}_$appContactId")
}
