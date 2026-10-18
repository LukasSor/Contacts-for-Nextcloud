package com.nextcloud.contacts.ui.navigation

import android.net.Uri

object Routes {
    const val SETUP = "setup"
    const val POST_LOGIN_SETUP = "post_login_setup"
    const val CONTACTS = "contacts"
    const val CONTACT_DETAIL = "contact/{id}"
    const val CONTACT_EDIT = "edit/{id}"
    const val SETTINGS = "settings"
    const val ORGANIZE = "organize"
    const val ORGANIZE_BIN = "organize/bin"
    const val ORGANIZE_IMPORT = "organize/import"
    const val ORGANIZE_CALENDAR = "organize/calendar"
    const val ORGANIZE_MERGE = "organize/merge"
    const val TIME_ZONE_CLOCK = "timezone_clock/{zoneId}"
    const val DATE_COUNTDOWN = "date_countdown/{kind}/{dateRaw}/{contactName}"

    fun contactDetail(id: Long) = "contact/$id"
    fun contactEdit(id: Long) = "edit/$id"
    fun timeZoneClock(zoneId: String) = "timezone_clock/${Uri.encode(zoneId)}"
    fun dateCountdown(kind: String, dateRaw: String, contactName: String) =
        "date_countdown/${Uri.encode(kind)}/${Uri.encode(dateRaw)}/${Uri.encode(contactName)}"
}
