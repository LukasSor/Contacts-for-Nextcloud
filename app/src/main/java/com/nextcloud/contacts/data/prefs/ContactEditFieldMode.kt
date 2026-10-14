package com.nextcloud.contacts.data.prefs

enum class ContactEditFieldMode {
    SIMPLE,
    WORK,
    FULL,
}

fun ContactEditFieldMode.includesWorkFields(): Boolean = this != ContactEditFieldMode.SIMPLE

fun ContactEditFieldMode.includesFullFields(): Boolean = this == ContactEditFieldMode.FULL
