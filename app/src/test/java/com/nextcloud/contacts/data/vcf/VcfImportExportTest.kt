package com.nextcloud.contacts.data.vcf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.contacts.TestApplication
import com.nextcloud.contacts.data.ContactRepository
import com.nextcloud.contacts.data.RemoteContactIndex
import com.nextcloud.contacts.data.local.AddressInput
import com.nextcloud.contacts.data.local.ContactDao
import com.nextcloud.contacts.data.local.ContactInput
import com.nextcloud.contacts.data.local.ContactScalarVcardExtras
import com.nextcloud.contacts.data.local.CustomFieldEntity
import com.nextcloud.contacts.data.local.StoredVCardLine
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import com.nextcloud.contacts.data.local.VCardTypedFieldInput
import com.nextcloud.contacts.data.local.VCardUnknownLines
import com.nextcloud.contacts.data.local.ContactPhotoStore
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.ContactsDatabase
import com.nextcloud.contacts.data.local.WebsiteLogoStore
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.data.remote.WebsiteFaviconFetcher
import com.nextcloud.contacts.platform.BirthdayCalendarEventLinks
import com.nextcloud.contacts.platform.BirthdayCalendarSync
import com.nextcloud.contacts.platform.ContactCalendarEventKind
import com.nextcloud.contacts.platform.ContactDatesCalendarCoordinator
import com.nextcloud.contacts.platform.NextcloudCalendarEventLinks
import com.nextcloud.contacts.platform.NextcloudContactDatesCalendarSync
import com.nextcloud.contacts.platform.SystemContactLinks
import com.nextcloud.contacts.platform.SystemContactsExporter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestApplication::class)
class VcfImportExportTest {

    private lateinit var context: Context
    private lateinit var database: ContactsDatabase
    private lateinit var dao: ContactDao
    private lateinit var repository: ContactRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ContactsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.contactDao()
        repository = buildRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun vcfRoundTripImportUpdatesWithoutDuplicates() = runTest {
        insertSampleContact(
            givenName = "Alice",
            familyName = "Example",
            phone = "+1 555 0199",
            remoteUid = UUID.randomUUID().toString(),
        )
        insertSampleContact(
            givenName = "Bob",
            familyName = "Builder",
            phone = "+1 555 0100",
            remoteUid = UUID.randomUUID().toString(),
        )

        val export = repository.exportAllToVcf()
        assertEquals(2, export.contactCount)
        assertTrue(export.vcardText.contains("BEGIN:VCARD"))
        assertTrue(export.vcardText.contains("Alice"))
        assertTrue(export.vcardText.contains("Bob"))

        val importDb = Room.inMemoryDatabaseBuilder(context, ContactsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val importDao = importDb.contactDao()
        val importRepo = buildRepository(importDb)
        try {
            val firstImport = importRepo.importFromVcf(export.vcardText)
            assertEquals(2, firstImport.imported)
            assertEquals(0, firstImport.updated)
            assertEquals(0, firstImport.failed)

            val alice = contactByFingerprint(
                repo = importRepo,
                contactDao = importDao,
                displayName = "Alice Example",
                phones = listOf("+15550199"),
                emails = listOf("alice@example.com"),
            )
            assertNotNull(alice)
            assertEquals("Alice", alice!!.contact.givenName)
            assertEquals("Example Corp", alice.contact.organization)
            assertEquals("1990-05-15", alice.contact.birthday)
            assertEquals("Friends", alice.contact.categories)
            assertEquals("Remember to call back", alice.contact.notes)
            assertEquals("alice@example.com", alice.emails.first().value)
            assertEquals("Main St", alice.addresses.first().street)
            assertTrue(alice.addresses.first().geo.contains("52.5"))

            val secondImport = importRepo.importFromVcf(export.vcardText)
            assertEquals(0, secondImport.imported)
            assertEquals(2, secondImport.updated)
            assertEquals(0, secondImport.failed)
            assertEquals(2, importDao.countActive())

            importRepo.createContact(
                ContactInput(
                    givenName = "Unrelated",
                    familyName = "Person",
                    phones = listOf(VCardTypedFieldInput.phone(type = "mobile", value = "+1 555 9999")),
                ),
            )
            val thirdImport = importRepo.importFromVcf(export.vcardText)
            assertEquals(0, thirdImport.imported)
            assertEquals(2, thirdImport.updated)
            assertEquals(0, thirdImport.failed)
            assertEquals(3, importDao.countActive())
            assertNotNull(
                contactByFingerprint(
                    repo = importRepo,
                    contactDao = importDao,
                    displayName = "Unrelated Person",
                    phones = listOf("+15559999"),
                    emails = emptyList(),
                ),
            )
        } finally {
            importDb.close()
        }
    }

    @Test
    fun mergePendingKeepsRicherKeeperPhonesAndEmails() = runTest {
        val keeperId = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                organization = "Keeper Org",
                notes = "keeper notes",
                phones = listOf(
                    VCardTypedFieldInput.phone(value = "+1 555 0101"),
                    VCardTypedFieldInput.phone(value = "+1 555 0102"),
                ),
                emails = listOf(
                    VCardTypedFieldInput.email(value = "ada@example.com"),
                    VCardTypedFieldInput.email(value = "ada.work@example.com"),
                ),
            ),
        )
        val keeper = dao.getById(keeperId)!!.contact
        dao.updateContact(
            keeper.copy(
                remoteUrl = "https://cloud.example/remote.php/dav/addressbooks/users/a/contacts/ada.vcf",
                dirty = false,
                lastModified = 1_000L,
            ),
        )
        val pendingId = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                organization = "Pending Org",
                notes = "",
                phones = listOf(VCardTypedFieldInput.phone(value = "+1 555 0101")),
                emails = listOf(VCardTypedFieldInput.email(value = "ada@example.com")),
            ),
        )
        val pending = dao.getById(pendingId)!!.contact
        dao.updateContact(pending.copy(remoteUrl = null, dirty = true, lastModified = 5_000L))

        repository.mergePendingIntoExisting(pendingId, keeperId)

        val merged = dao.getById(keeperId)
        assertNotNull(merged)
        assertEquals("Pending Org", merged!!.contact.organization)
        assertEquals("", merged.contact.notes)
        assertEquals(
            setOf("+1 555 0101", "+1 555 0102"),
            merged.phones.map { it.value }.toSet(),
        )
        assertEquals(
            setOf("ada@example.com", "ada.work@example.com"),
            merged.emails.map { it.value }.toSet(),
        )
        assertTrue(merged.contact.dirty)
        assertNull(dao.getById(pendingId))
    }

    @Test
    fun olderPendingDoesNotBlankKeeperScalars() = runTest {
        val keeperId = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                organization = "Keeper Org",
                notes = "keeper notes",
                phones = listOf(
                    VCardTypedFieldInput.phone(value = "+1 555 0101"),
                    VCardTypedFieldInput.phone(value = "+1 555 0102"),
                ),
                emails = listOf(VCardTypedFieldInput.email(value = "ada@example.com")),
            ),
        )
        val keeper = dao.getById(keeperId)!!.contact
        dao.updateContact(
            keeper.copy(
                remoteUrl = "https://cloud.example/ada.vcf",
                dirty = false,
                lastModified = 5_000L,
            ),
        )
        val pendingId = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                organization = "",
                notes = "",
                phones = listOf(VCardTypedFieldInput.phone(value = "+1 555 0199")),
                emails = listOf(VCardTypedFieldInput.email(value = "new@example.com")),
            ),
        )
        val pending = dao.getById(pendingId)!!.contact
        dao.updateContact(pending.copy(dirty = true, lastModified = 100L))

        repository.mergePendingIntoExisting(pendingId, keeperId)

        val merged = dao.getById(keeperId)!!
        assertEquals("Keeper Org", merged.contact.organization)
        assertEquals("keeper notes", merged.contact.notes)
        assertEquals(
            setOf("+1 555 0101", "+1 555 0102", "+1 555 0199"),
            merged.phones.map { it.value }.toSet(),
        )
        assertEquals(
            setOf("ada@example.com", "new@example.com"),
            merged.emails.map { it.value }.toSet(),
        )
    }

    @Test
    fun linkPendingUploadsKeepsKeeperAddress() = runTest {
        val keeperId = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                phones = listOf(VCardTypedFieldInput.phone(value = "+1 555 0101")),
                emails = listOf(VCardTypedFieldInput.email(value = "ada@example.com")),
                addresses = listOf(
                    AddressInput(type = "home", street = "Keeper St", city = "Berlin"),
                ),
            ),
        )
        val keeper = dao.getById(keeperId)!!.contact
        dao.updateContact(
            keeper.copy(
                remoteUrl = "https://cloud.example/ada.vcf",
                dirty = false,
                lastModified = 1_000L,
            ),
        )
        val pendingId = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                phones = listOf(VCardTypedFieldInput.phone(value = "+1 555 0101")),
                emails = listOf(VCardTypedFieldInput.email(value = "ada@example.com")),
                addresses = emptyList(),
            ),
        )
        val pending = dao.getById(pendingId)!!.contact
        dao.updateContact(pending.copy(remoteUrl = null, dirty = true, lastModified = 5_000L))

        repository.linkPendingUploadsBeforePush()

        assertEquals(1, dao.countActive())
        val merged = dao.getById(keeperId)
        assertNotNull(merged)
        assertEquals("Keeper St", merged!!.addresses.single().street)
        assertNull(dao.getById(pendingId))
    }

    @Test
    fun ambiguousDisplayNameDoesNotLinkManager() = runTest {
        val firstId = repository.createContact(ContactInput(givenName = "Sam", familyName = "Smith"))
        val secondId = repository.createContact(ContactInput(givenName = "Sam", familyName = "Smith"))
        val onlyId = repository.createContact(ContactInput(givenName = "Only", familyName = "Person"))
        val first = dao.getById(firstId)!!.contact
        dao.updateContact(first.copy(remoteUid = "uid-sam-1"))

        assertNull(repository.findContactIdForManager(null, "Sam Smith"))
        assertEquals(onlyId, repository.findContactIdForManager(null, "Only Person"))
        assertEquals(firstId, repository.findContactIdForManager("uid-sam-1", "Sam Smith"))
        assertTrue(secondId != firstId)
    }

    @Test
    fun equivalentRemoteUrlResolvesFromTheSyncMap() = runTest {
        val id = repository.createContact(ContactInput(givenName = "Ada", familyName = "Lovelace"))
        val storedUrl = "/remote.php/dav/addressbooks/users/a/contacts/ada%20lovelace.vcf"
        val contact = dao.getById(id)!!.contact
        dao.updateContact(contact.copy(remoteUrl = storedUrl, remoteUid = "uid-ada"))
        val index = RemoteContactIndex.build(
            "https://cloud.example",
            listOf(dao.getById(id)!!.contact),
        )
        val found = repository.findByRemoteReference(
            server = "https://cloud.example",
            storageKey = "https://cloud.example/remote.php/dav/addressbooks/users/a/contacts/ada%20lovelace.vcf",
            index = index,
        )
        assertEquals(id, found?.id)
    }

    @Test
    fun cleanServerContactDropsPhotoWhenVCardHasNone() = runTest {
        val server = "https://cloud.example"
        val url = "/remote.php/dav/addressbooks/users/a/contacts/photo.vcf"
        val id = repository.createContact(ContactInput(givenName = "Ada", familyName = "Photo"))
        val row = dao.getById(id)!!.contact.copy(remoteUrl = url, remoteUid = "photo-uid", dirty = false)
        dao.updateContact(row)
        repository.upsertFromServer(
            server = server,
            contact = row,
            phones = emptyList(),
            emails = emptyList(),
            photoBytes = byteArrayOf(1, 2, 3),
        )
        assertTrue(repository.hasPhoto(id))

        dao.updateContact(dao.getById(id)!!.contact.copy(dirty = true))
        repository.upsertFromServer(
            server = server,
            contact = row.copy(dirty = true),
            phones = emptyList(),
            emails = emptyList(),
            photoBytes = null,
        )
        assertTrue(repository.hasPhoto(id))

        dao.updateContact(dao.getById(id)!!.contact.copy(dirty = false))
        repository.upsertFromServer(
            server = server,
            contact = row.copy(dirty = false),
            phones = emptyList(),
            emails = emptyList(),
            photoBytes = null,
        )
        assertTrue(!repository.hasPhoto(id))
    }

    @Test
    fun absorbServerDuplicateKeepsLoserScalarExtrasAndUnknownLines() = runTest {
        val winnerId = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                phones = listOf(VCardTypedFieldInput.phone(value = "+1 555 0101")),
            ),
        )
        val loserId = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                phones = listOf(VCardTypedFieldInput.phone(value = "+1 555 0102")),
            ),
        )
        val winnerExtras = ContactScalarVcardExtras.encode(
            mapOf("NOTE" to VCardSyntaxExtras(rawValue = "winner-raw")),
        )
        val loserExtras = ContactScalarVcardExtras.encode(
            mapOf("TITLE" to VCardSyntaxExtras(rawValue = "loser-raw")),
        )
        dao.updateContact(
            dao.getById(winnerId)!!.contact.copy(
                remoteUrl = "/remote.php/dav/addressbooks/users/a/contacts/ada-a.vcf",
                remoteUid = "same-uid",
                etag = "etag-winner",
                vcardScalarExtrasJson = winnerExtras,
                vcardUnknownLinesJson = VCardUnknownLines.encode(
                    listOf(StoredVCardLine(propertyName = "X-WIN", value = "keep-winner")),
                ),
            ),
        )
        dao.updateContact(
            dao.getById(loserId)!!.contact.copy(
                remoteUrl = "/remote.php/dav/addressbooks/users/a/contacts/ada-b.vcf",
                remoteUid = "same-uid",
                vcardScalarExtrasJson = loserExtras,
                vcardUnknownLinesJson = VCardUnknownLines.encode(
                    listOf(StoredVCardLine(propertyName = "X-LOSE", value = "keep-loser")),
                ),
            ),
        )

        repository.deduplicateLocalContacts("https://cloud.example")

        assertEquals(1, dao.countActive())
        assertNull(dao.getById(loserId))
        val kept = dao.getById(winnerId)!!.contact
        assertTrue(kept.vcardScalarExtrasJson.contains("winner-raw"))
        assertTrue(kept.vcardScalarExtrasJson.contains("loser-raw"))
        assertTrue(kept.vcardUnknownLinesJson.contains("X-WIN"))
        assertTrue(kept.vcardUnknownLinesJson.contains("X-LOSE"))
    }

    @Test
    fun deviceReimportUpdatesPhoneWithoutDroppingServerOnlyData() = runTest {
        val id = repository.createContact(
            ContactInput(
                givenName = "Ada",
                familyName = "Lovelace",
                notes = "server note",
                geo = "52.5;13.4",
                phones = listOf(VCardTypedFieldInput.phone(value = "+1 555-0101")),
                emails = listOf(VCardTypedFieldInput.email(value = "ada@example.com")),
            ),
        )
        val extras = ContactScalarVcardExtras.encode(
            mapOf("NOTE" to VCardSyntaxExtras(rawValue = "server-extra")),
        )
        val unknown = VCardUnknownLines.encode(
            listOf(StoredVCardLine(propertyName = "X-SERVER", value = "keep-me")),
        )
        dao.updateContact(
            dao.getById(id)!!.contact.copy(
                vcardScalarExtrasJson = extras,
                vcardUnknownLinesJson = unknown,
                geo = "52.5;13.4",
                notes = "server note",
                dirty = false,
            ),
        )
        dao.insertCustomFields(
            listOf(CustomFieldEntity(contactId = id, label = "Badge", value = "Gold")),
        )

        val result = repository.importContacts(
            listOf(
                ContactInput(
                    givenName = "Ada",
                    familyName = "Lovelace",
                    organization = "Analytical Engines",
                    notes = "",
                    phones = listOf(VCardTypedFieldInput.phone(value = "+1 555-0199")),
                    emails = listOf(VCardTypedFieldInput.email(value = "ada@example.com")),
                ),
            ),
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(1, dao.countActive())
        val updated = dao.getById(id)!!
        assertEquals(
            setOf("+1 555-0101", "+1 555-0199"),
            updated.phones.map { it.value }.toSet(),
        )
        assertEquals("ada@example.com", updated.emails.single().value)
        assertEquals("server note", updated.contact.notes)
        assertEquals("52.5;13.4", updated.contact.geo)
        assertEquals("Analytical Engines", updated.contact.organization)
        assertTrue(updated.contact.vcardScalarExtrasJson.contains("server-extra"))
        assertTrue(updated.contact.vcardUnknownLinesJson.contains("X-SERVER"))
        assertEquals("Gold", updated.customFields.single().value)
        assertTrue(updated.contact.dirty)
    }

    @Test
    fun removeByRemoteUrlClearsDeviceLinksBeforeTheRowIsGone() = runTest {
        val adaId = repository.createContact(
            ContactInput(givenName = "Ada", familyName = "Lovelace"),
        )
        val bobId = repository.createContact(
            ContactInput(givenName = "Bob", familyName = "Builder"),
        )
        val adaUrl = "https://cloud.example/remote.php/dav/addressbooks/users/a/contacts/ada.vcf"
        val bobUrl = "https://cloud.example/remote.php/dav/addressbooks/users/a/contacts/bob.vcf"
        dao.updateContact(dao.getById(adaId)!!.contact.copy(remoteUrl = adaUrl, remoteUid = "uid-ada"))
        dao.updateContact(dao.getById(bobId)!!.contact.copy(remoteUrl = bobUrl, remoteUid = "uid-bob"))

        val systemLinks = SystemContactLinks(context)
        val birthdayLinks = BirthdayCalendarEventLinks(context)
        val nextcloudLinks = NextcloudCalendarEventLinks(context)
        systemLinks.clear()
        birthdayLinks.clear()
        nextcloudLinks.clear()
        systemLinks.setLink(adaId, 42L)
        systemLinks.setLink(bobId, 43L)
        birthdayLinks.setLink(adaId, ContactCalendarEventKind.BIRTHDAY, 7L, "ada-bday")
        birthdayLinks.setLink(bobId, ContactCalendarEventKind.BIRTHDAY, 8L, "bob-bday")
        nextcloudLinks.setLink(
            adaId,
            ContactCalendarEventKind.ANNIVERSARY,
            "https://cloud.example/ada.ics",
            "ada-ann",
        )
        nextcloudLinks.setLink(
            bobId,
            ContactCalendarEventKind.ANNIVERSARY,
            "https://cloud.example/bob.ics",
            "bob-ann",
        )

        repository.removeByRemoteUrl(adaUrl)

        assertNull(dao.getById(adaId))
        assertNotNull(dao.getById(bobId))
        assertNull(systemLinks.getRawContactId(adaId))
        assertEquals(43L, systemLinks.getRawContactId(bobId))
        assertNull(birthdayLinks.getEventId(adaId, ContactCalendarEventKind.BIRTHDAY))
        assertEquals(8L, birthdayLinks.getEventId(bobId, ContactCalendarEventKind.BIRTHDAY))
        assertNull(nextcloudLinks.getEventHref(adaId, ContactCalendarEventKind.ANNIVERSARY))
        assertEquals(
            "https://cloud.example/bob.ics",
            nextcloudLinks.getEventHref(bobId, ContactCalendarEventKind.ANNIVERSARY),
        )
        systemLinks.clear()
        birthdayLinks.clear()
        nextcloudLinks.clear()
    }

    @Test
    fun blankRemoteUidIsAssignedOnce() = runTest {
        val blankId = repository.createContact(
            ContactInput(givenName = "Ada", familyName = "Lovelace"),
        )
        dao.updateContact(dao.getById(blankId)!!.contact.copy(remoteUid = "   ", dirty = true))
        val first = repository.ensureRemoteUid(blankId)
        val second = repository.ensureRemoteUid(blankId)
        assertFalse(first.isNullOrBlank())
        assertEquals(first, second)
        assertEquals(first, dao.getById(blankId)!!.contact.remoteUid)

        val keptId = repository.createContact(
            ContactInput(givenName = "Grace", familyName = "Hopper"),
        )
        dao.updateContact(dao.getById(keptId)!!.contact.copy(remoteUid = "uid-keep", dirty = true))
        assertEquals("uid-keep", repository.ensureRemoteUid(keptId))
        assertEquals("uid-keep", dao.getById(keptId)!!.contact.remoteUid)
    }

    private suspend fun contactByFingerprint(
        repo: ContactRepository,
        contactDao: ContactDao,
        displayName: String,
        phones: List<String>,
        emails: List<String>,
    ): ContactWithDetails? {
        val id = repo.fingerprintIndex().find(displayName, phones, emails) ?: return null
        return contactDao.getById(id)
    }

    private suspend fun insertSampleContact(
        givenName: String,
        familyName: String,
        phone: String,
        remoteUid: String,
    ) {
        val id = repository.createContact(
            sampleInput(givenName = givenName, familyName = familyName, phone = phone),
        )
        val existing = dao.getById(id)?.contact ?: return
        dao.updateContact(existing.copy(remoteUid = remoteUid))
    }

    private fun sampleInput(
        givenName: String,
        familyName: String,
        phone: String,
    ): ContactInput = ContactInput(
        givenName = givenName,
        familyName = familyName,
        organization = "Example Corp",
        birthday = "1990-05-15",
        categories = "Friends",
        notes = "Remember to call back",
        phones = listOf(VCardTypedFieldInput.phone(value = phone)),
        emails = listOf(VCardTypedFieldInput.email(value = "${givenName.lowercase()}@example.com")),
        addresses = listOf(
            AddressInput(
                type = "home",
                street = "Main St",
                city = "Berlin",
                postalCode = "10115",
                country = "Germany",
                geo = "52.5200;13.4050",
            ),
        ),
    )

    private fun buildRepository(db: ContactsDatabase): ContactRepository {
        val userPreferences = UserPreferences(context)
        val photoStore = ContactPhotoStore(context)
        val websiteLogoStore = WebsiteLogoStore(context)
        val systemContactsExporter = SystemContactsExporter(
            context,
            db.contactDao(),
            photoStore,
            SystemContactLinks(context),
            userPreferences,
        )
        val birthdayCalendarSync = BirthdayCalendarSync(
            context,
            db.contactDao(),
            BirthdayCalendarEventLinks(context),
            userPreferences,
        )
        val contactDatesCalendarCoordinator = ContactDatesCalendarCoordinator(
            birthdayCalendarSync,
            NextcloudContactDatesCalendarSync(
                context,
                db.contactDao(),
                NextcloudCalendarEventLinks(context),
                userPreferences,
                { null },
            ),
            userPreferences,
        )
        return ContactRepository(
            db,
            db.contactDao(),
            photoStore,
            websiteLogoStore,
            WebsiteFaviconFetcher(),
            systemContactsExporter,
            contactDatesCalendarCoordinator,
        )
    }
}
