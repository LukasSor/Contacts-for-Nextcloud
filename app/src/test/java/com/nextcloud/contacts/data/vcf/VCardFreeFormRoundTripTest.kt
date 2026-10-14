package com.nextcloud.contacts.data.vcf

import com.nextcloud.contacts.TestApplication
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactScalarVcardExtras
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.PhoneEntity
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
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
class VCardFreeFormRoundTripTest {

    private fun roundTripContact(contact: ContactWithDetails): ContactWithDetails {
        val vcardText = VCardMapper.toVCard(contact)
        val vcard = Ezvcard.parse(StringReader(vcardText)).first()
        return VCardMapper.fromVCardObject(vcard, "https://example.test/contacts/freeform.vcf", null)
            .let { parsed ->
                ContactWithDetails(
                    contact = parsed.contact,
                    phones = parsed.phones,
                    emails = parsed.emails,
                    addresses = parsed.addresses,
                    urls = parsed.urls,
                    imAccounts = parsed.imAccounts,
                    relatedNames = parsed.relatedNames,
                    customFields = parsed.customFields,
                )
            }
    }

    @Test
    fun customPhoneTypeRoundTrips() {
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Type Test",
                givenName = "Type",
                familyName = "Test",
            ),
            phones = listOf(
                PhoneEntity(
                    contactId = 0,
                    type = "SATELLITE",
                    value = "+15551234567",
                ),
            ),
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("TYPE=SATELLITE"))
        val parsed = roundTripContact(contact)
        assertEquals("SATELLITE", parsed.phones.single().type)
        assertEquals("+15551234567", parsed.phones.single().value)
    }

    @Test
    fun phoneTypeParameterOnlyRoundTrips() {
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Param Type",
                givenName = "Param",
                familyName = "Type",
            ),
            phones = listOf(
                PhoneEntity(
                    contactId = 0,
                    type = "mobile",
                    value = "+15557654321",
                    vcardTypeParam = "SATELLITE",
                ),
            ),
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("TYPE=SATELLITE"))
        assertTrue(!vcardText.contains("TYPE=CELL") || vcardText.contains("TYPE=SATELLITE"))
        val parsed = roundTripContact(contact)
        val phone = parsed.phones.single()
        assertTrue(
            phone.type.equals("SATELLITE", ignoreCase = true) ||
                phone.vcardTypeParam.equals("SATELLITE", ignoreCase = true),
        )
        assertEquals("+15557654321", phone.value)
    }

    @Test
    fun orgWithExtraParameterRoundTrips() {
        val scalar = mapOf(
            "ORG" to VCardSyntaxExtras(
                rawValue = "Acme Inc",
                extraParameters = listOf("X-FOO" to "bar"),
            ),
        )
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Org Test",
                givenName = "Org",
                familyName = "Test",
                organization = "Acme Inc",
                vcardScalarExtrasJson = ContactScalarVcardExtras.encode(scalar),
            ),
            phones = emptyList(),
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("X-FOO=bar"))
        val parsed = roundTripContact(contact)
        assertEquals("Acme Inc", parsed.contact.organization)
        val parsedScalar = ContactScalarVcardExtras.decode(parsed.contact.vcardScalarExtrasJson)
        assertEquals("bar", parsedScalar["ORG"]?.extraParameters?.single()?.second)
    }

    @Test
    fun rawStructuredNameRoundTrips() {
        val nRaw = "Family;Given;Middle;Prefix;Suffix"
        val scalar = mapOf("N" to VCardSyntaxExtras(rawValue = nRaw))
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Name Test",
                givenName = "Given",
                familyName = "Family",
                middleName = "Middle",
                prefix = "Prefix",
                suffix = "Suffix",
                vcardScalarExtrasJson = ContactScalarVcardExtras.encode(scalar),
            ),
            phones = emptyList(),
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val parsed = roundTripContact(contact)
        assertEquals("Family", parsed.contact.familyName)
        assertEquals("Given", parsed.contact.givenName)
        assertEquals("Middle", parsed.contact.middleName)
        assertEquals("Prefix", parsed.contact.prefix)
        assertEquals("Suffix", parsed.contact.suffix)
        val parsedScalar = ContactScalarVcardExtras.decode(parsed.contact.vcardScalarExtrasJson)
        assertEquals(nRaw, parsedScalar["N"]?.rawValue)
    }

    @Test
    fun rawBirthdayStringRoundTrips() {
        val rawBirthday = "--02-29"
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Birthday Test",
                givenName = "Birthday",
                familyName = "Test",
                birthday = rawBirthday,
            ),
            phones = emptyList(),
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val parsed = roundTripContact(contact)
        assertEquals(rawBirthday, parsed.contact.birthday)
    }

    @Test
    fun birthdayWithParameterRoundTrips() {
        val rawBirthday = "not-a-standard-date"
        val scalar = mapOf(
            "BDAY" to VCardSyntaxExtras(
                rawValue = rawBirthday,
                typeParam = "TEXT",
            ),
        )
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Birthday Param",
                givenName = "Birthday",
                familyName = "Param",
                birthday = rawBirthday,
                vcardScalarExtrasJson = ContactScalarVcardExtras.encode(scalar),
            ),
            phones = emptyList(),
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("TYPE=TEXT"))
        val parsed = roundTripContact(contact)
        assertEquals(rawBirthday, parsed.contact.birthday)
        val parsedScalar = ContactScalarVcardExtras.decode(parsed.contact.vcardScalarExtrasJson)
        assertEquals("TEXT", parsedScalar["BDAY"]?.typeParam)
    }

    @Test
    fun rawTimezoneStringRoundTrips() {
        val tz = "X-Custom/Not-A-Real-Zone"
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "TZ Test",
                givenName = "TZ",
                familyName = "Test",
                timeZone = tz,
            ),
            phones = emptyList(),
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains(tz))
        val parsed = roundTripContact(contact)
        assertEquals(tz, parsed.contact.timeZone)
    }
}
