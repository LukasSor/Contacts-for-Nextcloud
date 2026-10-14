package com.nextcloud.contacts.auth

import android.content.Context
import androidx.core.content.edit

class LoginSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(session: PendingLoginSession) {
        prefs.edit {
            putString(KEY_POLL_URL, session.pollUrl)
            putString(KEY_POLL_TOKEN, session.pollToken)
            putString(KEY_SERVER_URL, session.serverUrl)
            putLong(KEY_STARTED_AT, session.startedAt)
        }
    }

    fun load(): PendingLoginSession? {
        val pollUrl = prefs.getString(KEY_POLL_URL, null) ?: return null
        val pollToken = prefs.getString(KEY_POLL_TOKEN, null) ?: return null
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        return PendingLoginSession(
            pollUrl = pollUrl,
            pollToken = pollToken,
            serverUrl = serverUrl,
            startedAt = prefs.getLong(KEY_STARTED_AT, 0L),
        )
    }

    fun clear() {
        prefs.edit { clear() }
    }

    data class PendingLoginSession(
        val pollUrl: String,
        val pollToken: String,
        val serverUrl: String,
        val startedAt: Long,
    )

    companion object {
        private const val PREFS_NAME = "login_session"
        private const val KEY_POLL_URL = "poll_url"
        private const val KEY_POLL_TOKEN = "poll_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_STARTED_AT = "started_at"
    }
}
