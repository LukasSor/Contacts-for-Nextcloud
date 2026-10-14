package com.nextcloud.contacts.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getCredentials(): AccountCredentials? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val loginName = prefs.getString(KEY_LOGIN, null) ?: return null
        val appPassword = prefs.getString(KEY_PASSWORD, null) ?: return null
        return AccountCredentials(server, loginName, appPassword)
    }

    fun saveCredentials(credentials: AccountCredentials) {
        prefs.edit()
            .putString(KEY_SERVER, credentials.server.trimEnd('/'))
            .putString(KEY_LOGIN, credentials.loginName)
            .putString(KEY_PASSWORD, credentials.appPassword)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getCredentials() != null

    companion object {
        private const val FILE_NAME = "account_credentials"
        private const val KEY_SERVER = "server"
        private const val KEY_LOGIN = "login_name"
        private const val KEY_PASSWORD = "app_password"
    }
}
