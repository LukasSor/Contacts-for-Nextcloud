package com.nextcloud.contacts.data.prefs

object ConnectedAppsAccess {
    /** Linked apps follow READ_CONTACTS. There is no stored on/off choice. */
    fun discoveryEnabled(readContactsGranted: Boolean): Boolean = readContactsGranted
}
