package com.nextcloud.contacts.data.vcf

import com.nextcloud.contacts.TestApplication
import com.nextcloud.contacts.data.local.AddressEntity
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactScalarVcardExtras
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.EmailEntity
import com.nextcloud.contacts.data.local.PhoneEntity
import com.nextcloud.contacts.data.local.RelatedEntity
import com.nextcloud.contacts.data.local.UrlEntity
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import com.nextcloud.contacts.data.local.VCardUnknownLines
import com.nextcloud.contacts.data.remote.VCardMapper
import ezvcard.Ezvcard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestApplication::class)
class VCardDataSafetyTest {

    @Test
    fun addressGeoRoundTripsThroughVCard() {
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Geo Test",
                givenName = "Geo",
                familyName = "Test",
            ),
            phones = emptyList(),
            emails = emptyList(),
            addresses = listOf(
                AddressEntity(
                    contactId = 0,
                    type = "home",
                    street = "Main Street",
                    city = "Berlin",
                    geo = "52.5200;13.4050",
                ),
            ),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val exported = VCardMapper.toVCard(contact)
        assertTrue(exported.contains("X-ADR-GEO"))
        val parsed = VCardMapper.fromVCardObject(
            Ezvcard.parse(StringReader(exported)).first(),
            "https://example.test/contacts/geo.vcf",
            null,
        )
        assertEquals("52.5200;13.4050", parsed.addresses.single().geo)
        val reExported = VCardMapper.toVCard(
            ContactWithDetails(
                contact = parsed.contact,
                phones = parsed.phones,
                emails = parsed.emails,
                addresses = parsed.addresses,
                urls = parsed.urls,
                imAccounts = parsed.imAccounts,
                relatedNames = parsed.relatedNames,
                customFields = parsed.customFields,
            ),
        )
        assertTrue(reExported.contains("X-ADR-GEO"))
    }

    @Test
    fun unknownPropertySurvivesDoubleRoundTrip() {
        val vcardText = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Unknown Test
            N:Test;Unknown;;;
            X-UNKNOWN-PROP:hello
            END:VCARD
        """.trimIndent()
        val first = VCardMapper.fromVCardObject(
            Ezvcard.parse(StringReader(vcardText)).first(),
            "https://example.test/contacts/unknown.vcf",
            null,
        )
        assertTrue(first.customFields.isEmpty())
        assertTrue(first.phones.isEmpty())
        assertTrue(first.emails.isEmpty())
        assertTrue(VCardUnknownLines.decode(first.contact.vcardUnknownLinesJson).any {
            it.propertyName.equals("X-UNKNOWN-PROP", ignoreCase = true) && it.value == "hello"
        })

        val exported = VCardMapper.toVCard(
            ContactWithDetails(
                contact = first.contact,
                phones = first.phones,
                emails = first.emails,
                addresses = first.addresses,
                urls = first.urls,
                imAccounts = first.imAccounts,
                relatedNames = first.relatedNames,
                customFields = first.customFields,
            ),
        )
        assertTrue(exported.contains("X-UNKNOWN-PROP:hello"))

        val second = VCardMapper.fromVCardObject(
            Ezvcard.parse(StringReader(exported)).first(),
            "https://example.test/contacts/unknown.vcf",
            null,
        )
        assertTrue(VCardUnknownLines.decode(second.contact.vcardUnknownLinesJson).any {
            it.propertyName.equals("X-UNKNOWN-PROP", ignoreCase = true) && it.value == "hello"
        })
        assertTrue(second.customFields.isEmpty())
    }

    @Test
    fun telLineIsNotStoredAsUnknownLeftover() {
        val vcardText = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Tel Test
            N:Test;Tel;;;
            TEL;TYPE=CELL:+491234567
            END:VCARD
        """.trimIndent()
        val parsed = VCardMapper.fromVCardObject(
            Ezvcard.parse(StringReader(vcardText)).first(),
            "https://example.test/contacts/tel.vcf",
            null,
        )
        assertEquals(1, parsed.phones.size)
        assertTrue(VCardUnknownLines.decode(parsed.contact.vcardUnknownLinesJson).none {
            it.propertyName.equals("TEL", ignoreCase = true)
        })
    }

    @Test
    fun clearedFormFieldsAreNotReplacedByImportedRawValues() {
        val scalar = mapOf(
            "FN" to VCardSyntaxExtras(rawValue = "sw Aleks de ww es"),
            "N" to VCardSyntaxExtras(rawValue = "ww;Aleks;de;sw;es"),
            "NICKNAME" to VCardSyntaxExtras(rawValue = "xxd"),
            "NOTE" to VCardSyntaxExtras(
                rawValue = "xdfhhhs # gay",
                extraParameters = listOf("ENCODING" to "QUOTED-PRINTABLE"),
            ),
            "TZ" to VCardSyntaxExtras(rawValue = "Africa/Asmara"),
            "GENDER" to VCardSyntaxExtras(rawValue = "O"),
            "CATEGORIES" to VCardSyntaxExtras(rawValue = "fffff,gg"),
            "BDAY" to VCardSyntaxExtras(rawValue = "1990-01-02"),
            "X-PHONETIC-FIRST-NAME" to VCardSyntaxExtras(rawValue = "fff"),
            "X-PHONETIC-LAST-NAME" to VCardSyntaxExtras(rawValue = "cccc"),
            "RELATIONSHIP" to VCardSyntaxExtras(rawValue = "parent"),
        )
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "sw Aleks de ww es",
                givenName = "Aleks",
                vcardScalarExtrasJson = ContactScalarVcardExtras.encode(scalar),
            ),
            phones = listOf(
                PhoneEntity(contactId = 0, type = "mobile", value = "+43 676 3519070"),
            ),
            emails = listOf(
                EmailEntity(contactId = 0, type = "home", value = "", vcardPropertyGroup = "item1"),
            ),
            addresses = emptyList(),
            urls = listOf(
                UrlEntity(contactId = 0, type = "", value = "", vcardPropertyGroup = "item2"),
            ),
            imAccounts = emptyList(),
            relatedNames = listOf(
                RelatedEntity(contactId = 0, type = "parent", value = ""),
            ),
        )
        val exported = VCardMapper.toVCard(contact)
        assertTrue(exported.contains("FN:Aleks"))
        assertTrue(exported.contains("N:;Aleks;;;"))
        assertTrue(exported.contains("TEL"))
        assertFalse(exported.contains("NICKNAME"))
        assertFalse(exported.contains("NOTE"))
        assertFalse(exported.contains("ENCODING"))
        assertFalse(exported.contains("TZ:"))
        assertFalse(exported.contains("GENDER"))
        assertFalse(exported.contains("CATEGORIES"))
        assertFalse(exported.contains("BDAY"))
        assertFalse(exported.contains("X-PHONETIC"))
        assertFalse(exported.contains("RELATIONSHIP"))
        assertFalse(exported.contains("EMAIL"))
        assertFalse(exported.contains("URL:"))
        assertFalse(exported.contains("RELATED"))
    }

    @Test
    fun v3PhotoEncodingIsNotCopiedOntoV4Upload() {
        val vcardText = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Photo
            N:Photo;;;;
            PHOTO;ENCODING=b;TYPE=JPEG:/9j/AAEC
            END:VCARD
        """.trimIndent()
        val parsed = VCardMapper.fromVCard(
            vcardText,
            "https://example.test/contacts/photo.vcf",
            null,
        )
        val exported = VCardMapper.toVCard(
            ContactWithDetails(
                contact = parsed.contact,
                phones = parsed.phones,
                emails = parsed.emails,
                addresses = parsed.addresses,
                urls = parsed.urls,
                imAccounts = parsed.imAccounts,
                relatedNames = parsed.relatedNames,
                customFields = parsed.customFields,
            ),
            photoBytes = parsed.photoBytes,
        )
        assertTrue(exported.contains("VERSION:4.0"))
        assertTrue(exported.contains("PHOTO"))
        assertFalse(exported.contains("ENCODING"))
    }
}
