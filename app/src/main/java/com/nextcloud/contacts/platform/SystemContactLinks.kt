package com.nextcloud.contacts.platform

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.systemContactLinksStore by preferencesDataStore("system_contact_links")

class SystemContactLinks(context: Context) {
    private val dataStore = context.applicationContext.systemContactLinksStore

    suspend fun getRawContactId(appContactId: Long): Long? {
        val prefs = dataStore.data.first()
        return prefs[rawKey(appContactId)]
    }

    suspend fun getAppContactId(rawContactId: Long): Long? {
        val prefs = dataStore.data.first()
        return prefs[reverseKey(rawContactId)]
    }

    suspend fun setLink(appContactId: Long, rawContactId: Long) {
        dataStore.edit { prefs ->
            prefs[rawKey(appContactId)] = rawContactId
            prefs[reverseKey(rawContactId)] = appContactId
        }
    }

    suspend fun removeAppContact(appContactId: Long) {
        dataStore.edit { prefs ->
            val rawId = prefs[rawKey(appContactId)]
            prefs.remove(rawKey(appContactId))
            if (rawId != null) {
                prefs.remove(reverseKey(rawId))
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun allAppContactIds(): List<Long> {
        val prefs = dataStore.data.first()
        return prefs.asMap().keys
            .filter { it.name.startsWith("app_") }
            .mapNotNull { it.name.removePrefix("app_").toLongOrNull() }
    }

    private fun rawKey(appContactId: Long) = longPreferencesKey("app_$appContactId")

    private fun reverseKey(rawContactId: Long) = longPreferencesKey("raw_$rawContactId")
}
