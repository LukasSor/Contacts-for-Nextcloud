package com.nextcloud.contacts.data.vcf

import com.nextcloud.contacts.TestApplication
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactScalarVcardExtras
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import com.nextcloud.contacts.data.remote.VCardMapper
import com.nextcloud.contacts.data.remote.VCardPhotoHelper
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
class VCardPhotoRoundTripTest {

    private val tinyPng = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D,
    )

    @Test
    fun photoUriAndTypeParameterRoundTrip() {
        val scalar = mapOf(
            "PHOTO" to VCardSyntaxExtras(
                typeParam = "JPEG",
                rawValue = "https://example.com/avatar.jpg",
            ),
        )
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Photo Uri",
                givenName = "Photo",
                familyName = "Uri",
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
        assertTrue(vcardText.contains("https://example.com/avatar.jpg"))
        assertTrue(vcardText.contains("TYPE=JPEG"))
        val parsed = Ezvcard.parse(StringReader(vcardText)).first().photos.single()
        assertEquals("https://example.com/avatar.jpg", parsed.url)
    }

    @Test
    fun fileBackedPhotoUsesBytesWhenScalarRawUnchanged() {
        val baseline = VCardPhotoHelper.baselineRawValue(tinyPng, null)
        val scalar = mapOf("PHOTO" to VCardSyntaxExtras(rawValue = baseline))
        val contact = ContactWithDetails(
            contact = ContactEntity(
                displayName = "Photo Bin",
                givenName = "Photo",
                familyName = "Bin",
                vcardScalarExtrasJson = ContactScalarVcardExtras.encode(scalar),
            ),
            phones = emptyList(),
            emails = emptyList(),
            addresses = emptyList(),
            urls = emptyList(),
            imAccounts = emptyList(),
            relatedNames = emptyList(),
        )
        val vcardText = VCardMapper.toVCard(contact, tinyPng)
        val parsed = Ezvcard.parse(StringReader(vcardText)).first().photos.single()
        assertTrue(parsed.data.contentEquals(tinyPng))
    }
}
