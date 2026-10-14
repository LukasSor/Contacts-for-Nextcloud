package com.nextcloud.contacts.data.prefs

enum class ContactDatesDestination {
    NONE,
    SYSTEM,
    NEXTCLOUD,
    BOTH,
    ;

    fun includesSystem(): Boolean = this == SYSTEM || this == BOTH

    fun includesNextcloud(): Boolean = this == NEXTCLOUD || this == BOTH

    fun storageValue(): String = when (this) {
        NONE -> "none"
        SYSTEM -> "system"
        NEXTCLOUD -> "nextcloud"
        BOTH -> "both"
    }

    companion object {
        const val BIRTHDAYS_PREFERENCE = "contact_birthdays_destination"
        const val ANNIVERSARIES_PREFERENCE = "contact_anniversaries_destination"
        const val DATES_OF_DEATH_PREFERENCE = "contact_dates_of_death_destination"

        fun fromStorage(value: String?): ContactDatesDestination = when (value) {
            "none" -> NONE
            "system" -> SYSTEM
            "nextcloud" -> NEXTCLOUD
            "both" -> BOTH
            else -> NONE
        }

        /** Fallback when nothing is stored. Not written back. */
        fun resolveForBehavior(
            stored: ContactDatesDestination?,
            nextcloudCalendarAvailable: Boolean?,
        ): ContactDatesDestination {
            if (stored != null) return stored
            return if (nextcloudCalendarAvailable == true) NEXTCLOUD else NONE
        }

        /** While the calendar app is unavailable, Nextcloud becomes none and Both becomes system. */
        fun displaySelection(
            stored: ContactDatesDestination?,
            nextcloudCalendarAvailable: Boolean?,
        ): ContactDatesDestination {
            val preference = resolveForBehavior(stored, nextcloudCalendarAvailable)
            if (nextcloudCalendarAvailable != true) {
                return when (preference) {
                    NEXTCLOUD -> NONE
                    BOTH -> SYSTEM
                    else -> preference
                }
            }
            return preference
        }

        fun includesNextcloudForSync(
            stored: ContactDatesDestination?,
            nextcloudCalendarAvailable: Boolean?,
        ): Boolean {
            return resolveForBehavior(stored, nextcloudCalendarAvailable).includesNextcloud() &&
                nextcloudCalendarAvailable == true
        }

        fun includesSystemForSync(
            stored: ContactDatesDestination?,
            nextcloudCalendarAvailable: Boolean?,
        ): Boolean {
            return resolveForBehavior(stored, nextcloudCalendarAvailable).includesSystem()
        }
    }
}
