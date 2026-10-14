package com.nextcloud.contacts.data.prefs

enum class CalendarEventDescriptionMode {
    DEFAULT,
    NOTES,
    DETAILS,
    ;

    fun toStorageValue(): String = when (this) {
        DEFAULT -> "default"
        NOTES -> "notes"
        DETAILS -> "details"
    }

    companion object {
        fun fromStorage(value: String?): CalendarEventDescriptionMode = when (value) {
            "notes" -> NOTES
            "details" -> DETAILS
            else -> DEFAULT
        }
    }
}
