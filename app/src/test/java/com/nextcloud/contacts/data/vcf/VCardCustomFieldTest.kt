package com.nextcloud.contacts.data.vcf

import com.nextcloud.contacts.TestApplication
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.CustomFieldEntity
import com.nextcloud.contacts.data.local.CustomFieldExtras
import com.nextcloud.contacts.data.local.PhoneEntity
import com.nextcloud.contacts.data.local.toFieldInput
import com.nextcloud.contacts.data.local.CustomFieldInput
import com.nextcloud.contacts.data.local.toEntity
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
class VCardCustomFieldTest {

    private fun roundTrip(contact: ContactWithDetails): List<CustomFieldInput> {
        val vcardText = VCardMapper.toVCard(contact)
        val vcard = Ezvcard.parse(StringReader(vcardText)).first()
        val parsed = VCardMapper.fromVCardObject(vcard, "https://example.test/contacts/test.vcf", null)
        return VCardMapper.inputFromParsed(parsed).customFields
    }

    @Test
    fun customFieldsRoundTripThroughVCard() {
        val contact = baseContact(
            customFields = listOf(
                CustomFieldEntity(contactId = 0, label = "Shoe size", value = "42"),
                CustomFieldEntity(contactId = 0, label = "Line;comma\nbreak", value = "a,b"),
            ),
        )

        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("X-NCCONTACTS-FIELD"))
        assertTrue(vcardText.contains("LABEL="))

        assertEquals(
            listOf(
                CustomFieldInput(label = "Shoe size", value = "42"),
                CustomFieldInput(label = "Line;comma\nbreak", value = "a,b"),
            ),
            roundTrip(contact),
        )
    }

    @Test
    fun customFieldWithTypeRoundTrips() {
        val contact = baseContact(
            customFields = listOf(
                CustomFieldEntity(
                    contactId = 0,
                    label = "Mobile alt",
                    value = "+491234567",
                    typeParam = "CELL",
                ),
            ),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("TYPE=CELL"))
        assertEquals("CELL", roundTrip(contact).single().typeParam)
    }

    @Test
    fun customFieldWithExtraParameterRoundTrips() {
        val contact = baseContact(
            customFields = listOf(
                CustomFieldEntity(
                    contactId = 0,
                    label = "Rank",
                    value = "1",
                    extraParametersJson = CustomFieldExtras.encode(listOf("FOO" to "bar")),
                ),
            ),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("FOO=bar"))
        assertEquals(listOf("FOO" to "bar"), roundTrip(contact).single().extraParameters)
    }

    @Test
    fun customFieldWithGroupAndAbLabelRoundTrips() {
        val grouped = baseContact(
            customFields = listOf(
                CustomFieldEntity(
                    contactId = 0,
                    label = "Second phone",
                    value = "+491234567",
                    propertyGroup = "NEXTCLOUD1",
                    typeParam = "CELL",
                    propertyName = "X-SECOND-PHONE",
                ),
            ),
        )
        val vcardText = VCardMapper.toVCard(grouped)
        assertTrue(vcardText.contains("NEXTCLOUD1.X-SECOND-PHONE"))
        assertTrue(vcardText.contains("NEXTCLOUD1.X-ABLABEL:Second phone"))

        val field = roundTrip(grouped).single()
        assertEquals("Second phone", field.label)
        assertEquals("NEXTCLOUD1", field.propertyGroup)
        assertEquals("CELL", field.typeParam)
        assertEquals("X-SECOND-PHONE", field.propertyName)
    }

    @Test
    fun semicolonStructuredValueRoundTripsUnchanged() {
        val structured = ";;Main Street;Berlin;;10115;Germany"
        val contact = baseContact(
            customFields = listOf(
                CustomFieldInput(
                    label = "Home address copy",
                    value = structured,
                    propertyName = "X-ADR-COPY",
                ).toEntity(contactId = 0),
            ),
        )
        assertEquals(structured, roundTrip(contact).single().value)
    }

    @Test
    fun builtInTelLineIsNotImportedAsCustomField() {
        val vcardText = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Phone Test
            N:Test;Phone;;;
            TEL;TYPE=CELL:+491234567
            END:VCARD
        """.trimIndent()
        val vcard = Ezvcard.parse(StringReader(vcardText)).first()
        val parsed = VCardMapper.fromVCardObject(vcard, "https://example.test/contacts/tel.vcf", null)
        assertTrue(parsed.customFields.isEmpty())
        assertEquals(1, parsed.phones.size)
    }

    @Test
    fun phoneWithGroupTypeAndAbLabelRoundTripsOnPhoneField() {
        val contact = baseContact(
            phones = listOf(
                PhoneEntity(
                    contactId = 0,
                    type = "mobile",
                    value = "+491234567",
                    vcardPropertyGroup = "NEXTCLOUD1",
                    vcardTypeParam = "CELL",
                    vcardAbLabel = "Second phone",
                ),
            ),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("NEXTCLOUD1.TEL"))
        assertTrue(vcardText.contains("NEXTCLOUD1.X-ABLABEL:Second phone"))

        val vcard = Ezvcard.parse(StringReader(vcardText)).first()
        val parsed = VCardMapper.fromVCardObject(vcard, "https://example.test/contacts/tel2.vcf", null)
        assertTrue(parsed.customFields.isEmpty())
        assertEquals(1, parsed.phones.size)
        val phone = parsed.phones.single()
        assertEquals("NEXTCLOUD1", phone.vcardPropertyGroup)
        assertEquals("Second phone", phone.vcardAbLabel)
        assertEquals("+491234567", phone.value)
    }

    @Test
    fun phoneWithExtraParameterRoundTrips() {
        val contact = baseContact(
            phones = listOf(
                PhoneEntity(
                    contactId = 0,
                    type = "mobile",
                    value = "+491234567",
                    vcardExtraParametersJson = CustomFieldExtras.encode(listOf("FOO" to "bar")),
                ),
            ),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("FOO=bar"))
        val vcard = Ezvcard.parse(StringReader(vcardText)).first()
        val parsed = VCardMapper.fromVCardObject(vcard, "https://example.test/contacts/tel3.vcf", null)
        val phone = parsed.phones.single()
        assertEquals(listOf("FOO" to "bar"), phone.toFieldInput().syntax.extraParameters)
    }

    @Test
    fun legacySimpleLineStillWritesNcContactsFieldWithLabel() {
        val contact = baseContact(
            customFields = listOf(
                CustomFieldEntity(contactId = 0, label = "Shoe size", value = "42"),
            ),
        )
        val vcardText = VCardMapper.toVCard(contact)
        assertTrue(vcardText.contains("X-NCCONTACTS-FIELD;LABEL=Shoe size:42"))
    }

    private fun baseContact(
        customFields: List<CustomFieldEntity> = emptyList(),
        phones: List<PhoneEntity> = emptyList(),
    ): ContactWithDetails =
        ContactWithDetails(
            contact = ContactEntity(
                displayName = "Custom Field Test",
                givenName = "Custom",
                familyName = "Field",
            ),
            phones = phones,
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
            customFields = customFields,
        )
}
