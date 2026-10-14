package com.nextcloud.contacts.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ContactEntity::class,
        PhoneEntity::class,
        EmailEntity::class,
        AddressEntity::class,
        UrlEntity::class,
        ImEntity::class,
        SocialProfileEntity::class,
        CloudIdEntity::class,
        LanguageEntity::class,
        RelatedEntity::class,
        CustomFieldEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ContactsDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}
