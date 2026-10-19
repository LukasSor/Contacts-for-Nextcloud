package com.nextcloud.contacts.platform

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.nextcloud.contacts.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestApplication::class)
class ContactsIntentRouterTest {

    @Test
    fun comapsSearchButton_isPostalAddressPick() {
        val intent = Intent(Intent.ACTION_PICK).setType(
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_TYPE,
        )
        val launch = ContactsIntentRouter.parse(intent)
        assertTrue(launch is ContactsLaunch.PickContact)
        assertEquals(ContactPickKind.POSTAL_ADDRESS, (launch as ContactsLaunch.PickContact).kind)
        assertEquals("vnd.android.cursor.dir/postal-address_v2", intent.type)
    }

    @Test
    fun vcardView_isImport() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://com.example.files/people/ada.vcf"), "text/x-vcard")
        }
        val launch = ContactsIntentRouter.parse(intent)
        assertTrue(launch is ContactsLaunch.ImportVCards)
        assertEquals(1, (launch as ContactsLaunch.ImportVCards).uris.size)
    }

    @Test
    fun octetStreamVcfPath_isImport() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("content://docs/document/primary%3ADownload%2Fada.vcf"),
                "application/octet-stream",
            )
        }
        assertTrue(ContactsIntentRouter.parse(intent) is ContactsLaunch.ImportVCards)
    }

    @Test
    fun plainTextShare_isNotVcardImport() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "hello")
        }
        assertFalse(ContactsIntentRouter.parse(intent) is ContactsLaunch.ImportVCards)
    }

    @Test
    fun telView_showsOrCreatesContact() {
        val launch = ContactsIntentRouter.parse(
            Intent(Intent.ACTION_VIEW, Uri.parse("tel:+15551212")),
        )
        assertTrue(launch is ContactsLaunch.ShowOrCreateContact)
        assertEquals("+15551212", (launch as ContactsLaunch.ShowOrCreateContact).phone)
    }

    @Test
    fun actionCall_isNotHandled() {
        val launch = ContactsIntentRouter.parse(
            Intent(Intent.ACTION_CALL, Uri.parse("tel:1555")),
        )
        assertTrue(launch is ContactsLaunch.Home)
    }
}
