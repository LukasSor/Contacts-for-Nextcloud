package com.nextcloud.contacts.data

import com.nextcloud.contacts.TestApplication
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactScalarVcardExtras
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestApplication::class)
class ContactMergeTest {
    @Test
    fun differingOrganizationStaysWithKeeperUntilChosen() {
        val merged = ContactMerge.mergeScalars(
            keeper = contact(organization = "Acme"),
            duplicate = contact(organization = "Globex"),
            fromDuplicate = emptySet(),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Acme", merged.contact.organization)

        val picked = ContactMerge.mergeScalars(
            keeper = contact(organization = "Acme", title = "Engineer"),
            duplicate = contact(organization = "Globex", title = "Founder"),
            fromDuplicate = setOf(MergeSingletonField.ORGANIZATION),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Globex", picked.contact.organization)
        assertEquals("Engineer", picked.contact.title)
    }

    @Test
    fun blankSingletonIsFilledWithoutAChoice() {
        val filled = ContactMerge.mergeScalars(
            keeper = contact(organization = ""),
            duplicate = contact(organization = "Globex"),
            fromDuplicate = emptySet(),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Globex", filled.contact.organization)

        val keeperWins = ContactMerge.mergeScalars(
            keeper = contact(organization = "Acme"),
            duplicate = contact(organization = ""),
            fromDuplicate = setOf(MergeSingletonField.ORGANIZATION),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Acme", keeperWins.contact.organization)
    }

    @Test
    fun sameTextKeepsTheKeeperSpelling() {
        val merged = ContactMerge.mergeScalars(
            keeper = contact(organization = "Acme"),
            duplicate = contact(organization = "Acme "),
            fromDuplicate = setOf(MergeSingletonField.ORGANIZATION),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Acme", merged.contact.organization)
    }

    @Test
    fun notesMarkdownFollowsTheChosenNote() {
        val picked = ContactMerge.mergeScalars(
            keeper = contact(notes = "Left", notesMarkdown = false),
            duplicate = contact(notes = "Right", notesMarkdown = true),
            fromDuplicate = setOf(MergeSingletonField.NOTES),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Right", picked.contact.notes)
        assertTrue(picked.contact.notesMarkdown)

        val sameText = ContactMerge.mergeScalars(
            keeper = contact(notes = "Same", notesMarkdown = false),
            duplicate = contact(notes = "Same", notesMarkdown = true),
            fromDuplicate = emptySet(),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertTrue(sameText.contact.notesMarkdown)
    }

    @Test
    fun lineManagerMovesAsOneValue() {
        val keeper = contact(managersName = "Ada", managersUid = "")
        val duplicate = contact(managersName = "Grace", managersUid = "uid-1")
        val kept = ContactMerge.mergeScalars(
            keeper = keeper,
            duplicate = duplicate,
            fromDuplicate = emptySet(),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Ada", kept.contact.managersName)
        assertEquals("", kept.contact.managersUid)

        val picked = ContactMerge.mergeScalars(
            keeper = keeper,
            duplicate = duplicate,
            fromDuplicate = setOf(MergeSingletonField.LINE_MANAGER),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Grace", picked.contact.managersName)
        assertEquals("uid-1", picked.contact.managersUid)

        val filled = ContactMerge.mergeScalars(
            keeper = contact(),
            duplicate = duplicate,
            fromDuplicate = emptySet(),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Grace", filled.contact.managersName)
        assertEquals("uid-1", filled.contact.managersUid)
    }

    @Test
    fun groupsAreCombinedWithoutDuplicates() {
        val merged = ContactMerge.mergeScalars(
            keeper = contact(categories = "Friends, Work"),
            duplicate = contact(categories = "work, Family"),
            fromDuplicate = emptySet(),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Friends, Work, Family", merged.contact.categories)
    }

    @Test
    fun mixedNamePartsKeepEachChosenColumn() {
        val merged = ContactMerge.mergeScalars(
            keeper = contact(displayName = "Ada Lovelace", givenName = "Ada"),
            duplicate = contact(displayName = "Grace", familyName = "Lovelace"),
            fromDuplicate = emptySet(),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
        )
        assertEquals("Ada", merged.contact.givenName)
        assertEquals("Lovelace", merged.contact.familyName)
        assertEquals("Ada Lovelace", merged.contact.displayName)
    }

    @Test
    fun photoIsCopiedOnlyWhenChosenOrMissing() {
        assertFalse(ContactMerge.copyPhotoFromDuplicate(emptySet(), "aaa", "bbb"))
        assertTrue(
            ContactMerge.copyPhotoFromDuplicate(setOf(MergeSingletonField.PHOTO), "aaa", "bbb"),
        )
        assertTrue(ContactMerge.copyPhotoFromDuplicate(emptySet(), null, "bbb"))
        assertFalse(ContactMerge.copyPhotoFromDuplicate(setOf(MergeSingletonField.PHOTO), "aaa", null))
        assertFalse(ContactMerge.copyPhotoFromDuplicate(setOf(MergeSingletonField.PHOTO), "aaa", "aaa"))
    }

    @Test
    fun conflictsRequireBothSidesToDiffer() {
        val left = listOf(
            MergeScalarSnapshot(MergeSingletonField.ORGANIZATION, value = "Acme", compareKey = "Acme"),
            MergeScalarSnapshot(MergeSingletonField.TITLE, value = "Engineer", compareKey = "Engineer"),
            MergeScalarSnapshot(MergeSingletonField.PHOTO, compareKey = "left-photo"),
        )
        val right = listOf(
            MergeScalarSnapshot(MergeSingletonField.ORGANIZATION, value = "Globex", compareKey = "Globex"),
            MergeScalarSnapshot(MergeSingletonField.NICKNAME, value = "Ace", compareKey = "Ace"),
            MergeScalarSnapshot(MergeSingletonField.PHOTO, compareKey = "left-photo"),
        )
        val conflicts = ContactMerge.singletonConflicts(left, right)
        assertEquals(listOf(MergeSingletonField.ORGANIZATION), conflicts.map { it.field })
        assertEquals(
            listOf(MergeSingletonField.TITLE),
            ContactMerge.scalarsOnlyOn(left, right).map { it.field },
        )
        assertEquals(
            emptyList<MergeSingletonField>(),
            ContactMerge.scalarsOnlyOn(right, left).map { it.field },
        )
    }

    @Test
    fun profileFieldsAreNotOfferedAsChoices() {
        val left = listOf(
            MergeScalarSnapshot(MergeSingletonField.DISPLAY_NAME, value = "Ada", compareKey = "Ada"),
            MergeScalarSnapshot(MergeSingletonField.NICKNAME, value = "A", compareKey = "A"),
            MergeScalarSnapshot(MergeSingletonField.PHOTO, compareKey = "left-photo"),
            MergeScalarSnapshot(MergeSingletonField.ORGANIZATION, value = "Acme", compareKey = "Acme"),
        )
        val right = listOf(
            MergeScalarSnapshot(MergeSingletonField.DISPLAY_NAME, value = "Grace", compareKey = "Grace"),
            MergeScalarSnapshot(MergeSingletonField.NICKNAME, value = "G", compareKey = "G"),
            MergeScalarSnapshot(MergeSingletonField.PHOTO, compareKey = "right-photo"),
            MergeScalarSnapshot(MergeSingletonField.ORGANIZATION, value = "Globex", compareKey = "Globex"),
            MergeScalarSnapshot(MergeSingletonField.TITLE, value = "Founder", compareKey = "Founder"),
        )
        assertEquals(
            listOf(MergeSingletonField.DISPLAY_NAME, MergeSingletonField.ORGANIZATION),
            ContactMerge.singletonConflicts(left, right).map { it.field },
        )
        assertEquals(
            listOf(MergeSingletonField.TITLE),
            ContactMerge.scalarsOnlyOn(right, left).map { it.field },
        )
    }

    @Test
    fun nameRowAppearsOnlyWhenVisibleNamesDiffer() {
        val left = listOf(
            MergeScalarSnapshot(MergeSingletonField.DISPLAY_NAME, value = "Ada", compareKey = "Ada"),
            MergeScalarSnapshot(MergeSingletonField.ORGANIZATION, value = "Acme", compareKey = "Acme"),
        )
        val sameName = listOf(
            MergeScalarSnapshot(MergeSingletonField.DISPLAY_NAME, value = "Ada", compareKey = "Ada"),
            MergeScalarSnapshot(MergeSingletonField.PREFIX, value = "Dr", compareKey = "Dr"),
            MergeScalarSnapshot(MergeSingletonField.NICKNAME, value = "A", compareKey = "A"),
            MergeScalarSnapshot(MergeSingletonField.PHONETIC_GIVEN, value = "AY-da", compareKey = "AY-da"),
            MergeScalarSnapshot(MergeSingletonField.PHOTO, compareKey = "right-photo"),
            MergeScalarSnapshot(MergeSingletonField.ORGANIZATION, value = "Globex", compareKey = "Globex"),
        )
        assertEquals(
            listOf(MergeSingletonField.ORGANIZATION),
            ContactMerge.singletonConflicts(left, sameName).map { it.field },
        )
        val otherName = sameName.map {
            if (it.field == MergeSingletonField.DISPLAY_NAME) it.copy(value = "Grace", compareKey = "Grace") else it
        }
        assertEquals(
            listOf(MergeSingletonField.DISPLAY_NAME, MergeSingletonField.ORGANIZATION),
            ContactMerge.singletonConflicts(left, otherName).map { it.field },
        )
        assertFalse(ContactMerge.canKeepFromOther(MergeSingletonField.PHOTO))
        assertFalse(ContactMerge.canKeepFromOther(MergeSingletonField.NICKNAME))
        assertFalse(ContactMerge.canKeepFromOther(MergeSingletonField.PREFIX))
        assertFalse(ContactMerge.canKeepFromOther(MergeSingletonField.PHONETIC_GIVEN))
        assertTrue(ContactMerge.canKeepFromOther(MergeSingletonField.DISPLAY_NAME))
        assertTrue(ContactMerge.canKeepFromOther(MergeSingletonField.ORGANIZATION))
    }

    @Test
    fun chosenNameBringsItsPartsAndLeavesPhotoAndNickname() {
        val keeperExtras = ContactScalarVcardExtras.encode(
            mapOf(
                "FN" to VCardSyntaxExtras(typeParam = "home", rawValue = "Dr Ada Lovelace"),
                "N" to VCardSyntaxExtras(typeParam = "home", rawValue = "Lovelace;Ada;Augusta;Dr;Esq"),
                "NICKNAME" to VCardSyntaxExtras(rawValue = "Addy"),
            ),
        )
        val duplicateExtras = ContactScalarVcardExtras.encode(
            mapOf(
                "FN" to VCardSyntaxExtras(typeParam = "work", rawValue = "Grace Hopper"),
                "N" to VCardSyntaxExtras(typeParam = "work", propertyGroup = "item1", rawValue = "Hopper;Grace;;;"),
                "NICKNAME" to VCardSyntaxExtras(rawValue = "Amazing"),
            ),
        )
        val merged = ContactMerge.mergeScalars(
            keeper = contact(
                displayName = "Dr Ada Lovelace",
                prefix = "Dr",
                givenName = "Ada",
                middleName = "Augusta",
                familyName = "Lovelace",
                suffix = "Esq",
                nickname = "Addy",
                phoneticGiven = "AY-da",
                phoneticFamily = "LUV",
                vcardScalarExtrasJson = keeperExtras,
            ),
            duplicate = contact(
                displayName = "Grace Hopper",
                givenName = "Grace",
                familyName = "Hopper",
                nickname = "Amazing",
                phoneticGiven = "GRAYS",
                phoneticFamily = "HOP",
                vcardScalarExtrasJson = duplicateExtras,
            ),
            fromDuplicate = setOf(
                MergeSingletonField.DISPLAY_NAME,
                MergeSingletonField.NICKNAME,
                MergeSingletonField.PHONETIC_GIVEN,
                MergeSingletonField.PHONETIC_FAMILY,
                MergeSingletonField.PHOTO,
            ),
            keeperPhotoDigest = "left-photo",
            duplicatePhotoDigest = "right-photo",
            keepProfile = true,
            keepFormat = true,
        )
        assertEquals("Grace Hopper", merged.contact.displayName)
        assertEquals("", merged.contact.prefix)
        assertEquals("Grace", merged.contact.givenName)
        assertEquals("", merged.contact.middleName)
        assertEquals("Hopper", merged.contact.familyName)
        assertEquals("", merged.contact.suffix)
        assertEquals("Addy", merged.contact.nickname)
        assertEquals("AY-da", merged.contact.phoneticGiven)
        assertEquals("LUV", merged.contact.phoneticFamily)
        assertFalse(merged.copyPhotoFromDuplicate)
        val extras = ContactScalarVcardExtras.decode(merged.contact.vcardScalarExtrasJson)
        assertEquals("home", extras["FN"]?.typeParam)
        assertEquals("Grace Hopper", extras["FN"]?.rawValue)
        assertEquals("home", extras["N"]?.typeParam)
        assertEquals("", extras["N"]?.propertyGroup)
        assertEquals("Hopper;Grace;;;", extras["N"]?.rawValue)
        assertEquals("Addy", extras["NICKNAME"]?.rawValue)
    }

    @Test
    fun sameVisibleNameDoesNotReplaceNameParts() {
        val merged = ContactMerge.mergeScalars(
            keeper = contact(displayName = "Ada", givenName = "Ada", familyName = "Lovelace", prefix = "Dr"),
            duplicate = contact(displayName = "Ada", givenName = "Grace", familyName = "Hopper"),
            fromDuplicate = setOf(MergeSingletonField.DISPLAY_NAME, MergeSingletonField.GIVEN_NAME),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = null,
            keepProfile = true,
        )
        assertEquals("Ada", merged.contact.displayName)
        assertEquals("Dr", merged.contact.prefix)
        assertEquals("Ada", merged.contact.givenName)
        assertEquals("Lovelace", merged.contact.familyName)
    }

    @Test
    fun mergeScreenKeepsProfileAndFormattingFromTheLeftContact() {
        val keeperExtras = ContactScalarVcardExtras.encode(
            mapOf(
                "NOTE" to VCardSyntaxExtras(
                    typeParam = "home",
                    rawValue = "Left",
                ),
                "ORG" to VCardSyntaxExtras(
                    typeParam = "work",
                    rawValue = "Acme",
                ),
            ),
        )
        val duplicateExtras = ContactScalarVcardExtras.encode(
            mapOf(
                "NOTE" to VCardSyntaxExtras(
                    typeParam = "work",
                    rawValue = "Right",
                ),
                "ORG" to VCardSyntaxExtras(
                    typeParam = "home",
                    propertyGroup = "item1",
                    rawValue = "Globex;Sales",
                ),
                "NICKNAME" to VCardSyntaxExtras(rawValue = "Amazing"),
            ),
        )
        val merged = ContactMerge.mergeScalars(
            keeper = contact(
                displayName = "Ada",
                givenName = "Ada",
                notes = "Left",
                notesMarkdown = true,
                organization = "Acme",
                vcardScalarExtrasJson = keeperExtras,
            ),
            duplicate = contact(
                displayName = "Grace",
                givenName = "Grace",
                familyName = "Hopper",
                nickname = "Amazing",
                notes = "Right",
                notesMarkdown = false,
                organization = "Globex",
                vcardScalarExtrasJson = duplicateExtras,
            ),
            fromDuplicate = setOf(
                MergeSingletonField.GIVEN_NAME,
                MergeSingletonField.FAMILY_NAME,
                MergeSingletonField.NICKNAME,
                MergeSingletonField.PHOTO,
                MergeSingletonField.NOTES,
                MergeSingletonField.ORGANIZATION,
            ),
            keeperPhotoDigest = null,
            duplicatePhotoDigest = "right-photo",
            keepProfile = true,
            keepFormat = true,
        )
        assertEquals("Ada", merged.contact.displayName)
        assertEquals("Ada", merged.contact.givenName)
        assertEquals("", merged.contact.familyName)
        assertEquals("", merged.contact.nickname)
        assertFalse(merged.copyPhotoFromDuplicate)
        assertEquals("Right", merged.contact.notes)
        assertTrue(merged.contact.notesMarkdown)
        assertEquals("Globex", merged.contact.organization)
        val extras = ContactScalarVcardExtras.decode(merged.contact.vcardScalarExtrasJson)
        assertEquals("home", extras["NOTE"]?.typeParam)
        assertEquals("Right", extras["NOTE"]?.rawValue)
        assertEquals("work", extras["ORG"]?.typeParam)
        assertEquals("", extras["ORG"]?.propertyGroup)
        assertEquals("Globex;Sales", extras["ORG"]?.rawValue)
        assertEquals(null, extras["NICKNAME"])
    }

    private fun contact(
        displayName: String = "",
        prefix: String = "",
        givenName: String = "",
        middleName: String = "",
        familyName: String = "",
        suffix: String = "",
        nickname: String = "",
        phoneticGiven: String = "",
        phoneticFamily: String = "",
        organization: String = "",
        title: String = "",
        notes: String = "",
        notesMarkdown: Boolean = false,
        categories: String = "",
        managersName: String = "",
        managersUid: String = "",
        vcardScalarExtrasJson: String = "",
    ) = ContactEntity(
        displayName = displayName,
        prefix = prefix,
        givenName = givenName,
        middleName = middleName,
        familyName = familyName,
        suffix = suffix,
        nickname = nickname,
        phoneticGiven = phoneticGiven,
        phoneticFamily = phoneticFamily,
        organization = organization,
        title = title,
        notes = notes,
        notesMarkdown = notesMarkdown,
        categories = categories,
        managersName = managersName,
        managersUid = managersUid,
        vcardScalarExtrasJson = vcardScalarExtrasJson,
    )
}
