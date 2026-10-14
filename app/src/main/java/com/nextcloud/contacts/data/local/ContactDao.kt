package com.nextcloud.contacts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Transaction
    @Query("SELECT * FROM contacts WHERE deleted = 0 ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactWithDetails>>

    @Query(
        """
        SELECT id, displayName, givenName, familyName, nickname, organization,
               isFavorite, categories, birthday,
               EXISTS(
                   SELECT 1 FROM phones p
                   WHERE p.contactId = contacts.id AND TRIM(p.value) != ''
               ) AS hasPhone,
               EXISTS(
                   SELECT 1 FROM emails e
                   WHERE e.contactId = contacts.id AND TRIM(e.value) != ''
               ) AS hasEmail,
               0 AS hasNextcloudAccount
        FROM contacts
        WHERE deleted = 0
        ORDER BY displayName COLLATE NOCASE ASC
        """,
    )
    fun observeList(): Flow<List<ContactListItem>>

    @Query(
        """
        SELECT contactId, 'social' AS kind, type, value FROM social_profiles
        WHERE TRIM(value) != ''
        UNION ALL
        SELECT contactId, 'im' AS kind, type, value FROM im_accounts
        WHERE TRIM(value) != ''
        UNION ALL
        SELECT contactId, 'cloud' AS kind, type, value FROM cloud_ids
        WHERE TRIM(value) != ''
        """,
    )
    fun observeNextcloudListHints(): Flow<List<ContactListNextcloudHint>>

    @Query("SELECT categories FROM contacts WHERE deleted = 0")
    fun observeContactCategories(): Flow<List<String>>

    @Transaction
    @Query(
        """
        SELECT * FROM contacts
        WHERE deleted = 0 AND (
            displayName LIKE :query || '%' COLLATE NOCASE OR
            givenName LIKE :query || '%' COLLATE NOCASE OR
            familyName LIKE :query || '%' COLLATE NOCASE OR
            nickname LIKE :query || '%' COLLATE NOCASE OR
            organization LIKE :query || '%' COLLATE NOCASE OR
            title LIKE :query || '%' COLLATE NOCASE
        )
        ORDER BY displayName COLLATE NOCASE ASC
        """,
    )
    fun search(query: String): Flow<List<ContactWithDetails>>

    @Query(
        """
        SELECT id, displayName, givenName, familyName, nickname, organization,
               isFavorite, categories, birthday,
               EXISTS(
                   SELECT 1 FROM phones p
                   WHERE p.contactId = contacts.id AND TRIM(p.value) != ''
               ) AS hasPhone,
               EXISTS(
                   SELECT 1 FROM emails e
                   WHERE e.contactId = contacts.id AND TRIM(e.value) != ''
               ) AS hasEmail,
               0 AS hasNextcloudAccount
        FROM contacts
        WHERE deleted = 0 AND (
            displayName LIKE :query || '%' COLLATE NOCASE OR
            givenName LIKE :query || '%' COLLATE NOCASE OR
            familyName LIKE :query || '%' COLLATE NOCASE OR
            nickname LIKE :query || '%' COLLATE NOCASE OR
            organization LIKE :query || '%' COLLATE NOCASE OR
            title LIKE :query || '%' COLLATE NOCASE
        )
        ORDER BY displayName COLLATE NOCASE ASC
        """,
    )
    fun searchList(query: String): Flow<List<ContactListItem>>

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ContactWithDetails?>

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ContactWithDetails?

    @Transaction
    @Query("SELECT * FROM contacts WHERE deleted = 0")
    suspend fun getAllWithDetails(): List<ContactWithDetails>

    @Transaction
    @Query("SELECT * FROM contacts WHERE deleted = 0 AND remoteUrl IS NOT NULL")
    suspend fun getServerLinkedWithDetails(): List<ContactWithDetails>

    @Query("SELECT COUNT(*) FROM contacts WHERE deleted = 0 AND remoteUrl IS NOT NULL")
    suspend fun countServerLinked(): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE deleted = 0")
    suspend fun countActive(): Int

    @Query("SELECT * FROM contacts WHERE dirty = 1 OR deleted = 1")
    suspend fun getPendingSync(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE remoteUrl = :remoteUrl LIMIT 1")
    suspend fun getByRemoteUrl(remoteUrl: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE remoteUrl LIKE '%' || :fileName LIMIT 1")
    suspend fun findByRemoteFileName(fileName: String): ContactEntity?

    @Query(
        """
        SELECT * FROM contacts
        WHERE deleted = 0 AND remoteUrl LIKE '%' || :fileName
        """,
    )
    suspend fun findAllByRemoteFileName(fileName: String): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE deleted = 0 AND remoteUrl IS NOT NULL")
    suspend fun getAllServerLinkedContacts(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE remoteUid = :uid AND deleted = 0 LIMIT 1")
    suspend fun getByRemoteUid(uid: String): ContactEntity?

    @Query(
        """
        SELECT id FROM contacts
        WHERE deleted = 0 AND displayName = :name COLLATE NOCASE
        """,
    )
    suspend fun findIdsByDisplayName(name: String): List<Long>

    @Query(
        """
        SELECT c.id AS id, c.remoteUid AS uid, c.displayName AS displayName,
               c.organization AS organization,
               (
                   SELECT e.value FROM emails e
                   WHERE e.contactId = c.id AND TRIM(e.value) != ''
                   ORDER BY e.id ASC
                   LIMIT 1
               ) AS email
        FROM contacts c
        WHERE c.deleted = 0
          AND c.remoteUid IS NOT NULL
          AND TRIM(c.remoteUid) != ''
        """,
    )
    fun observeLineManagerCandidates(): Flow<List<LineManagerListRow>>

    @Query(
        """
        SELECT id, displayName FROM contacts
        WHERE deleted = 0
        ORDER BY id ASC
        """,
    )
    suspend fun listFingerprintContacts(): List<FingerprintContactRow>

    /** Same rows as a full contact load, without the child tables import matching does not use. */
    @Query("SELECT id, displayName FROM contacts WHERE deleted = 0")
    suspend fun listImportMatchContacts(): List<FingerprintContactRow>

    @Query("SELECT contactId, value FROM phones")
    suspend fun listPhoneValues(): List<ContactIdValue>

    @Query("SELECT contactId, value FROM emails")
    suspend fun listEmailValues(): List<ContactIdValue>

    /** Favorite push failed after the vCard was stored. Keeps etag; retries on a later sync. */
    @Query("UPDATE contacts SET dirty = 1 WHERE id = :id")
    suspend fun markDirty(id: Long)

    /** Star-only edit. A later save moves [lastModified], so this leaves that row dirty. */
    @Query("UPDATE contacts SET dirty = 0 WHERE id = :id AND lastModified = :lastModified")
    suspend fun clearDirtyIfLastModified(id: Long, lastModified: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Update
    suspend fun updateContacts(contacts: List<ContactEntity>)

    /** Local-only flag. Does not touch dirty or lastModified, so sync does not upload it. */
    @Query("UPDATE contacts SET editFieldSyntaxEnabled = :enabled WHERE id = :id")
    suspend fun setEditFieldSyntaxEnabled(id: Long, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhones(phones: List<PhoneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddresses(addresses: List<AddressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrls(urls: List<UrlEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImAccounts(accounts: List<ImEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSocialProfiles(profiles: List<SocialProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudIds(ids: List<CloudIdEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguages(languages: List<LanguageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelatedNames(related: List<RelatedEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFields(fields: List<CustomFieldEntity>)

    @Query("DELETE FROM phones WHERE contactId = :contactId")
    suspend fun deletePhones(contactId: Long)

    @Query("DELETE FROM emails WHERE contactId = :contactId")
    suspend fun deleteEmails(contactId: Long)

    @Query("DELETE FROM addresses WHERE contactId = :contactId")
    suspend fun deleteAddresses(contactId: Long)

    @Query("DELETE FROM urls WHERE contactId = :contactId")
    suspend fun deleteUrls(contactId: Long)

    @Query("DELETE FROM im_accounts WHERE contactId = :contactId")
    suspend fun deleteImAccounts(contactId: Long)

    @Query("DELETE FROM social_profiles WHERE contactId = :contactId")
    suspend fun deleteSocialProfiles(contactId: Long)

    @Query("DELETE FROM cloud_ids WHERE contactId = :contactId")
    suspend fun deleteCloudIds(contactId: Long)

    @Query("DELETE FROM languages WHERE contactId = :contactId")
    suspend fun deleteLanguages(contactId: Long)

    @Query("DELETE FROM related_names WHERE contactId = :contactId")
    suspend fun deleteRelatedNames(contactId: Long)

    @Query("DELETE FROM custom_fields WHERE contactId = :contactId")
    suspend fun deleteCustomFields(contactId: Long)

    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContact(contactId: Long)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}
