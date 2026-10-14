package com.nextcloud.contacts.ui.navigation

import com.nextcloud.contacts.data.local.ContactInput
import com.nextcloud.contacts.platform.ContactsLaunch

data class IncomingContactsRequest(
    val launch: ContactsLaunch,
    val systemUri: android.net.Uri? = null,
)

object ContactEditPrefill {
    var input: ContactInput? = null
}

/** Applied when navigating to the contact list without the list route on the back stack. */
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
