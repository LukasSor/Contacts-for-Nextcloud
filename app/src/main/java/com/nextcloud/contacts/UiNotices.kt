package com.nextcloud.contacts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local messages for screens that outlive the job which produced them. */
class UiNotices {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** First login sync failed. The contact list shows this instead of an empty success. */
    @Volatile
    var loginSyncFailure: String? = null
        private set

    fun post(text: String) {
        _message.value = text
    }

    fun consume() {
        _message.value = null
    }

    fun reportLoginSyncFailure(text: String) {
        loginSyncFailure = text
    }

    fun takeLoginSyncFailure(): String? {
        val value = loginSyncFailure
        loginSyncFailure = null
        return value
    }
}
