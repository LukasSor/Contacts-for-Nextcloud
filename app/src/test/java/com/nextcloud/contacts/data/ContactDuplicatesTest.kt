package com.nextcloud.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDuplicatesTest {

    @Test
    fun samePhoneGroupsDespiteFormatting() {
        val groups = ContactDuplicates.find(
            names = mapOf(1L to "Ada Lovelace", 2L to "Grace Hopper"),
            phonesByContact = mapOf(
                1L to listOf("+49 170 1234567"),
                2L to listOf("491701234567"),
            ),
            emailsByContact = emptyMap(),
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].contactIds)
        assertEquals(setOf(ContactDuplicates.Match.PHONE), groups[0].matches)
    }

    @Test
    fun sameEmailIsCaseInsensitive() {
        val groups = ContactDuplicates.find(
            names = mapOf(4L to "Ada", 9L to "Grace"),
            phonesByContact = emptyMap(),
            emailsByContact = mapOf(
                4L to listOf("Ada@Example.com"),
                9L to listOf("ada@example.com"),
            ),
        )
        assertEquals(listOf(4L, 9L), groups.single().contactIds)
        assertEquals(setOf(ContactDuplicates.Match.EMAIL), groups.single().matches)
    }

    @Test
    fun sameFullDisplayNameMatches() {
        val groups = ContactDuplicates.find(
            names = mapOf(1L to "Ada Lovelace", 2L to "ada lovelace"),
            phonesByContact = emptyMap(),
            emailsByContact = emptyMap(),
        )
        assertEquals(setOf(ContactDuplicates.Match.NAME), groups.single().matches)
    }

    @Test
    fun sharedFirstNameOnlyDoesNotMatch() {
        val groups = ContactDuplicates.find(
            names = mapOf(1L to "Ada Lovelace", 2L to "Ada Wong"),
            phonesByContact = emptyMap(),
            emailsByContact = emptyMap(),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun blankNameAndBlankPhoneDoNotMatch() {
        val groups = ContactDuplicates.find(
            names = mapOf(1L to "  ", 2L to ""),
            phonesByContact = mapOf(1L to listOf(" + "), 2L to listOf("")),
            emailsByContact = mapOf(1L to listOf("  "), 2L to listOf("")),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun samePeopleOnPhoneAndEmailAreOneGroup() {
        val groups = ContactDuplicates.find(
            names = mapOf(1L to "Ada", 2L to "Grace"),
            phonesByContact = mapOf(1L to listOf("5550100"), 2L to listOf("555-0100")),
            emailsByContact = mapOf(1L to listOf("a@b.c"), 2L to listOf("a@b.c")),
        )
        assertEquals(1, groups.size)
        assertEquals(
            setOf(ContactDuplicates.Match.PHONE, ContactDuplicates.Match.EMAIL),
            groups.single().matches,
        )
    }

    @Test
    fun differentPairsStaySeparate() {
        val groups = ContactDuplicates.find(
            names = mapOf(1L to "Ada", 2L to "Grace", 3L to "Lin"),
            phonesByContact = mapOf(1L to listOf("111"), 2L to listOf("111")),
            emailsByContact = mapOf(2L to listOf("g@x.y"), 3L to listOf("g@x.y")),
        )
        assertEquals(2, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].contactIds)
        assertEquals(listOf(2L, 3L), groups[1].contactIds)
    }

    @Test
    fun contactsOutsideTheActiveSetAreIgnored() {
        val groups = ContactDuplicates.find(
            names = mapOf(1L to "Ada"),
            phonesByContact = mapOf(1L to listOf("555"), 99L to listOf("555")),
            emailsByContact = emptyMap(),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun valueGroupsSplitPhoneEmailAndName() {
        val groups = ContactDuplicates.findValues(
            names = mapOf(1L to "Ada Lovelace", 2L to "Ada Lovelace"),
            phonesByContact = mapOf(1L to listOf("+1 555"), 2L to listOf("1555")),
            emailsByContact = mapOf(1L to listOf("A@B.c"), 2L to listOf("a@b.c")),
        )
        assertEquals(
            setOf(
                ContactDuplicates.Match.PHONE,
                ContactDuplicates.Match.EMAIL,
                ContactDuplicates.Match.NAME,
            ),
            groups.map { it.match }.toSet(),
        )
        val phone = groups.single { it.match == ContactDuplicates.Match.PHONE }
        assertEquals(listOf(1L, 2L), phone.contactIds)
        assertEquals("+1 555", phone.value)
        assertEquals("A@B.c", groups.single { it.match == ContactDuplicates.Match.EMAIL }.value)
        assertEquals("Ada Lovelace", groups.single { it.match == ContactDuplicates.Match.NAME }.value)
    }

    @Test
    fun valueGroupsKeepEveryContactOnOneSharedPhone() {
        val groups = ContactDuplicates.findValues(
            names = mapOf(4L to "A", 2L to "B", 9L to "C"),
            phonesByContact = mapOf(4L to listOf("111"), 2L to listOf("111"), 9L to listOf("111")),
            emailsByContact = emptyMap(),
        )
        assertEquals(listOf(2L, 4L, 9L), groups.single().contactIds)
        assertEquals(ContactDuplicates.Match.PHONE, groups.single().match)
    }

    @Test
    fun twoSharedPhonesStaySeparateValueGroups() {
        val groups = ContactDuplicates.findValues(
            names = mapOf(1L to "A", 2L to "B"),
            phonesByContact = mapOf(1L to listOf("111", "222"), 2L to listOf("222", "111")),
            emailsByContact = emptyMap(),
        )
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.match == ContactDuplicates.Match.PHONE })
        assertEquals(setOf("111", "222"), groups.map { it.value }.toSet())
    }

    @Test
    fun valueGroupsIgnoreSharedFirstNameOnly() {
        val groups = ContactDuplicates.findValues(
            names = mapOf(1L to "Ada Lovelace", 2L to "Ada Wong"),
            phonesByContact = emptyMap(),
            emailsByContact = emptyMap(),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun keeperPrefersServerCardThenNewerThenLowerId() {
        val localNewer = ContactDuplicates.MergeCandidate(1L, hasRemote = false, lastModified = 50)
        val serverOlder = ContactDuplicates.MergeCandidate(2L, hasRemote = true, lastModified = 10)
        assertEquals(2L, ContactDuplicates.pickMergeKeeperId(listOf(localNewer, serverOlder)))

        val serverLow = ContactDuplicates.MergeCandidate(3L, hasRemote = true, lastModified = 10)
        val serverHighNewer = ContactDuplicates.MergeCandidate(8L, hasRemote = true, lastModified = 11)
        assertEquals(8L, ContactDuplicates.pickMergeKeeperId(listOf(serverLow, serverHighNewer)))

        val tieLow = ContactDuplicates.MergeCandidate(3L, hasRemote = true, lastModified = 10)
        val tieHigh = ContactDuplicates.MergeCandidate(8L, hasRemote = true, lastModified = 10)
        assertEquals(3L, ContactDuplicates.pickMergeKeeperId(listOf(tieHigh, tieLow)))
    }
}
