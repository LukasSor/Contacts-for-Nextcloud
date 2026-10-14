package com.nextcloud.contacts.data.vcf

import com.nextcloud.contacts.TestApplication
import com.nextcloud.contacts.data.local.AddressEntity
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.PhoneEntity
import com.nextcloud.contacts.data.local.VCardUnknownLines
import com.nextcloud.contacts.data.remote.VCardMapper
import ezvcard.Ezvcard
import org.junit.Assert.assertEquals
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
}
