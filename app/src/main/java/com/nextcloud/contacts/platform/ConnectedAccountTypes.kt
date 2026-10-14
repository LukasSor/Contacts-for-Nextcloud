package com.nextcloud.contacts.platform

enum class MessagingAction(val sortOrder: Int) {
    Message(0),
    Voice(1),
    Video(2),
}

object KnownContactAccountTypes {
    const val NEXTCLOUD = "com.nextcloud.contacts"
    const val GOOGLE = "com.google"
    const val DEVICE = "com.android.contacts"
    const val SIM = "com.android.sim"
}
