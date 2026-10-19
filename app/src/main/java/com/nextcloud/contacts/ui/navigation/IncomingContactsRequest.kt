package com.nextcloud.contacts.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nextcloud.contacts.data.local.ContactInput
import com.nextcloud.contacts.platform.ContactsLaunch

data class IncomingContactsRequest(
    val launch: ContactsLaunch,
    val systemUri: android.net.Uri? = null,
)

object ContactEditPrefill {
    var input: ContactInput? = null
}

object ContactListPendingSearch {
    var token by mutableStateOf(0)
        private set
    private var query: String? = null
    private var publishedToken = 0

    fun set(value: String) {
        query = value
        publishedToken = token + 1
        token = publishedToken
    }

    fun consume(forToken: Int): String? {
        if (forToken == 0 || forToken != publishedToken) return null
        val pending = query
        query = null
        publishedToken = 0
        return pending
    }
}

object ContactListPendingGroupFilter {
    private var group: String? = null

    fun set(groupName: String) {
        group = groupName
    }

    fun consume(): String? {
        val pending = group
        group = null
        return pending
    }
}
