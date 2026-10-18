package com.nextcloud.contacts.data

import androidx.annotation.StringRes
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.local.ContactCategories
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactScalarVcardExtras
import com.nextcloud.contacts.data.local.VCardSyntaxExtras

/**
 * One contact column, or the photo file. These cannot be stored twice,
 * so a merge keeps one side when the two values differ.
 *
 * [profile] fields stay with the contact on the left, except the display name.
 * When the visible names differ, the merge screen offers that name as one row.
 * Choosing it also takes that contact's prefix, first, middle, last, and suffix.
 * Nickname, phonetic names, and the photo stay with the left contact.
 */
enum class MergeSingletonField(
    @StringRes val labelRes: Int,
    val propertyKey: String?,
    val profile: Boolean = false,
) {
    DISPLAY_NAME(R.string.field_name, "FN", profile = true),
    PREFIX(R.string.field_prefix, null, profile = true),
    GIVEN_NAME(R.string.field_first_name, null, profile = true),
    MIDDLE_NAME(R.string.field_middle_name, null, profile = true),
    FAMILY_NAME(R.string.field_last_name, null, profile = true),
    SUFFIX(R.string.field_suffix, null, profile = true),
    NICKNAME(R.string.field_nickname, "NICKNAME", profile = true),
    PHONETIC_GIVEN(R.string.field_phonetic_first, "X-PHONETIC-FIRST-NAME", profile = true),
    PHONETIC_FAMILY(R.string.field_phonetic_last, "X-PHONETIC-LAST-NAME", profile = true),
    ORGANIZATION(R.string.field_organization, "ORG"),
    TITLE(R.string.field_job_title, "TITLE"),
    ROLE(R.string.field_role, "ROLE"),
    GENDER(R.string.field_gender, "GENDER"),
    BIRTHDAY(R.string.field_birthday, "BDAY"),
    BIRTHPLACE(R.string.field_place_of_birth, "BIRTHPLACE"),
    ANNIVERSARY(R.string.field_anniversary, "ANNIVERSARY"),
    DEATH_DATE(R.string.field_date_of_death, "DEATHDATE"),
    DEATH_PLACE(R.string.field_place_of_death, "DEATHPLACE"),
    NOTES(R.string.field_note, "NOTE"),
    GEO(R.string.field_coordinates, null),
    TIME_ZONE(R.string.field_time_zone, "TZ"),
    LINE_MANAGER(R.string.field_line_manager, "X-MANAGERSNAME"),
    RELATIONSHIP(R.string.field_relationship, "RELATIONSHIP"),
    PHOTO(R.string.field_photo, "PHOTO", profile = true),
}

/** One singleton as shown on a side of the merge screen. */
data class MergeScalarSnapshot(
    val field: MergeSingletonField,
    @StringRes val valueRes: Int = 0,
    val value: String = "",
    val compareKey: String = value,
)

data class MergeScalarConflict(
    val field: MergeSingletonField,
    val left: MergeScalarSnapshot,
    val right: MergeScalarSnapshot,
)

data class MergedContactFields(
    val contact: ContactEntity,
    val copyPhotoFromDuplicate: Boolean,
)

/**
 * Picks singleton columns for a user merge and fills blanks from the other contact.
 * List fields are combined by the repository, not here.
 * [fromDuplicate] names fields whose differing value comes from the contact being merged in.
 * With [keepProfile], the photo, nickname, and phonetic names stay with the keeper.
 * A chosen display name still brings that contact's name parts. Display-format flags stay too.
 */
object ContactMerge {
    /** Fields the merge screen can keep from the contact on the right. */
    fun canKeepFromOther(field: MergeSingletonField): Boolean =
        field == MergeSingletonField.DISPLAY_NAME || !field.profile

    fun lineManagerKey(name: String, uid: String): String {
        val trimmedName = name.trim()
        val trimmedUid = uid.trim()
        if (trimmedName.isEmpty() && trimmedUid.isEmpty()) return ""
        return "$trimmedName\u0000$trimmedUid"
    }

    fun singletonConflicts(
        left: List<MergeScalarSnapshot>,
        right: List<MergeScalarSnapshot>,
    ): List<MergeScalarConflict> {
        val leftByField = left.associateBy { it.field }
        val rightByField = right.associateBy { it.field }
        return MergeSingletonField.entries.mapNotNull { field ->
            if (field.profile && field != MergeSingletonField.DISPLAY_NAME) return@mapNotNull null
            val leftValue = leftByField[field] ?: return@mapNotNull null
            val rightValue = rightByField[field] ?: return@mapNotNull null
            if (leftValue.compareKey.isBlank() || rightValue.compareKey.isBlank()) return@mapNotNull null
            if (leftValue.compareKey == rightValue.compareKey) return@mapNotNull null
            MergeScalarConflict(field, leftValue, rightValue)
        }
    }

    /** Singletons present on [side] and empty on [other]. Profile stays with the keeper. */
    fun scalarsOnlyOn(
        side: List<MergeScalarSnapshot>,
        other: List<MergeScalarSnapshot>,
    ): List<MergeScalarSnapshot> {
        val otherByField = other.associateBy { it.field }
        return MergeSingletonField.entries.mapNotNull { field ->
            if (field.profile) return@mapNotNull null
            val snapshot = side.find { it.field == field } ?: return@mapNotNull null
            if (snapshot.compareKey.isBlank()) return@mapNotNull null
            val opposite = otherByField[field]
            if (opposite != null && opposite.compareKey.isNotBlank()) return@mapNotNull null
            snapshot
        }
    }

    fun copyPhotoFromDuplicate(
        fromDuplicate: Set<MergeSingletonField>,
        keeperPhotoDigest: String?,
        duplicatePhotoDigest: String?,
        keepProfile: Boolean = false,
    ): Boolean {
        if (keepProfile) return false
        return usesDuplicate(
            MergeSingletonField.PHOTO,
            keeperPhotoDigest.orEmpty(),
            duplicatePhotoDigest.orEmpty(),
            fromDuplicate,
            keepProfile = false,
        )
    }

    fun mergeScalars(
        keeper: ContactEntity,
        duplicate: ContactEntity,
        fromDuplicate: Set<MergeSingletonField>,
        keeperPhotoDigest: String?,
        duplicatePhotoDigest: String?,
        keepProfile: Boolean = false,
        keepFormat: Boolean = false,
    ): MergedContactFields {
        val takeName = structuredNameFromDuplicate(keeper, duplicate, fromDuplicate, keepProfile)
        fun structured(keeperValue: String, duplicateValue: String, field: MergeSingletonField): String =
            if (takeName) duplicateValue else pick(field, keeperValue, duplicateValue, fromDuplicate, keepProfile)
        val displayName = structured(keeper.displayName, duplicate.displayName, MergeSingletonField.DISPLAY_NAME)
        val prefix = structured(keeper.prefix, duplicate.prefix, MergeSingletonField.PREFIX)
        val givenName = structured(keeper.givenName, duplicate.givenName, MergeSingletonField.GIVEN_NAME)
        val middleName = structured(keeper.middleName, duplicate.middleName, MergeSingletonField.MIDDLE_NAME)
        val familyName = structured(keeper.familyName, duplicate.familyName, MergeSingletonField.FAMILY_NAME)
        val suffix = structured(keeper.suffix, duplicate.suffix, MergeSingletonField.SUFFIX)
        val nickname = pick(MergeSingletonField.NICKNAME, keeper.nickname, duplicate.nickname, fromDuplicate, keepProfile)
        val phoneticGiven = pick(
            MergeSingletonField.PHONETIC_GIVEN,
            keeper.phoneticGiven,
            duplicate.phoneticGiven,
            fromDuplicate,
            keepProfile,
        )
        val phoneticFamily = pick(
            MergeSingletonField.PHONETIC_FAMILY,
            keeper.phoneticFamily,
            duplicate.phoneticFamily,
            fromDuplicate,
            keepProfile,
        )
        val organization = pick(MergeSingletonField.ORGANIZATION, keeper.organization, duplicate.organization, fromDuplicate, keepProfile)
        val title = pick(MergeSingletonField.TITLE, keeper.title, duplicate.title, fromDuplicate, keepProfile)
        val role = pick(MergeSingletonField.ROLE, keeper.role, duplicate.role, fromDuplicate, keepProfile)
        val gender = pick(MergeSingletonField.GENDER, keeper.gender, duplicate.gender, fromDuplicate, keepProfile)
        val birthday = pick(MergeSingletonField.BIRTHDAY, keeper.birthday, duplicate.birthday, fromDuplicate, keepProfile)
        val birthplace = pick(MergeSingletonField.BIRTHPLACE, keeper.birthplace, duplicate.birthplace, fromDuplicate, keepProfile)
        val anniversary = pick(MergeSingletonField.ANNIVERSARY, keeper.anniversary, duplicate.anniversary, fromDuplicate, keepProfile)
        val deathDate = pick(MergeSingletonField.DEATH_DATE, keeper.deathDate, duplicate.deathDate, fromDuplicate, keepProfile)
        val deathPlace = pick(MergeSingletonField.DEATH_PLACE, keeper.deathPlace, duplicate.deathPlace, fromDuplicate, keepProfile)
        val notes = pick(MergeSingletonField.NOTES, keeper.notes, duplicate.notes, fromDuplicate, keepProfile)
        val notesFromDuplicate = usesDuplicate(
            MergeSingletonField.NOTES,
            keeper.notes,
            duplicate.notes,
            fromDuplicate,
            keepProfile,
        )
        val notesMarkdown = when {
            keepFormat -> keeper.notesMarkdown
            notesFromDuplicate -> duplicate.notesMarkdown
            keeper.notes.trim() == duplicate.notes.trim() && duplicate.notes.isNotBlank() ->
                keeper.notesMarkdown || duplicate.notesMarkdown
            else -> keeper.notesMarkdown
        }
        val geo = pick(MergeSingletonField.GEO, keeper.geo, duplicate.geo, fromDuplicate, keepProfile)
        val timeZone = pick(MergeSingletonField.TIME_ZONE, keeper.timeZone, duplicate.timeZone, fromDuplicate, keepProfile)
        val managerFromDuplicate = usesDuplicate(
            MergeSingletonField.LINE_MANAGER,
            lineManagerKey(keeper.managersName, keeper.managersUid),
            lineManagerKey(duplicate.managersName, duplicate.managersUid),
            fromDuplicate,
            keepProfile,
        )
        val relationship = pick(
            MergeSingletonField.RELATIONSHIP,
            keeper.relationshipToYou,
            duplicate.relationshipToYou,
            fromDuplicate,
            keepProfile,
        )
        val categories = unionCategories(keeper.categories, duplicate.categories)
        val copyPhoto = copyPhotoFromDuplicate(
            fromDuplicate,
            keeperPhotoDigest,
            duplicatePhotoDigest,
            keepProfile,
        )
        val merged = keeper.copy(
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
            role = role,
            gender = gender,
            birthday = birthday,
            birthplace = birthplace,
            anniversary = anniversary,
            deathDate = deathDate,
            deathPlace = deathPlace,
            categories = categories,
            notes = notes,
            notesMarkdown = notesMarkdown,
            geo = geo,
            timeZone = timeZone,
            managersName = if (managerFromDuplicate) duplicate.managersName else keeper.managersName,
            managersUid = if (managerFromDuplicate) duplicate.managersUid else keeper.managersUid,
            relationshipToYou = relationship,
        )
        return MergedContactFields(
            contact = merged.copy(
                vcardScalarExtrasJson = mergeScalarExtras(
                    keeper = keeper,
                    duplicate = duplicate,
                    merged = merged,
                    fromDuplicate = fromDuplicate,
                    copyPhotoFromDuplicate = copyPhoto,
                    managerFromDuplicate = managerFromDuplicate,
                    keepProfile = keepProfile,
                    keepFormat = keepFormat,
                    takeStructuredName = takeName,
                ),
            ),
            copyPhotoFromDuplicate = copyPhoto,
        )
    }

    private fun pick(
        field: MergeSingletonField,
        keeper: String,
        duplicate: String,
        fromDuplicate: Set<MergeSingletonField>,
        keepProfile: Boolean,
    ): String = if (usesDuplicate(field, keeper, duplicate, fromDuplicate, keepProfile)) duplicate else keeper

    private fun usesDuplicate(
        field: MergeSingletonField,
        keeper: String,
        duplicate: String,
        fromDuplicate: Set<MergeSingletonField>,
        keepProfile: Boolean,
    ): Boolean {
        if (keepProfile && field.profile) return false
        val left = keeper.trim()
        val right = duplicate.trim()
        if (right.isEmpty()) return false
        if (left.isEmpty()) return true
        if (sameText(field, left, right)) return false
        return field in fromDuplicate
    }

    /**
     * The merge screen chose the other contact's visible name.
     * The stored display name and its name parts move together, including blanks.
     */
    private fun structuredNameFromDuplicate(
        keeper: ContactEntity,
        duplicate: ContactEntity,
        fromDuplicate: Set<MergeSingletonField>,
        keepProfile: Boolean,
    ): Boolean {
        if (!keepProfile || MergeSingletonField.DISPLAY_NAME !in fromDuplicate) return false
        return keeper.displayName.trim() != duplicate.displayName.trim()
    }

    private fun sameText(field: MergeSingletonField, left: String, right: String): Boolean {
        if (field == MergeSingletonField.GENDER || field == MergeSingletonField.RELATIONSHIP) {
            return left.equals(right, ignoreCase = true)
        }
        return left == right
    }

    private fun unionCategories(keeper: String, duplicate: String): String {
        val seen = linkedMapOf<String, String>()
        for (name in ContactCategories.parse(keeper) + ContactCategories.parse(duplicate)) {
            val key = name.lowercase()
            if (key !in seen) seen[key] = name
        }
        return ContactCategories.join(seen.values.toList())
    }

    private fun mergeScalarExtras(
        keeper: ContactEntity,
        duplicate: ContactEntity,
        merged: ContactEntity,
        fromDuplicate: Set<MergeSingletonField>,
        copyPhotoFromDuplicate: Boolean,
        managerFromDuplicate: Boolean,
        keepProfile: Boolean,
        keepFormat: Boolean,
        takeStructuredName: Boolean,
    ): String {
        val winnerExtras = ContactScalarVcardExtras.decode(keeper.vcardScalarExtrasJson)
        val loserExtras = ContactScalarVcardExtras.decode(duplicate.vcardScalarExtrasJson)
        val result = winnerExtras.toMutableMap()
        for ((key, value) in loserExtras) {
            if (key !in OWNED_EXTRA_KEYS && key !in result) result[key] = value
        }
        fun put(key: String, fromLoser: Boolean, transform: (VCardSyntaxExtras) -> VCardSyntaxExtras = { it }) {
            val chosen = (if (fromLoser) loserExtras else winnerExtras)[key]
            val extras = if (keepFormat && fromLoser && key !in VALUE_WITH_OWN_EXTRAS) {
                valueWithKeeperFormat(winnerExtras[key], chosen)
            } else {
                chosen
            }
            if (extras == null) result.remove(key) else result[key] = transform(extras)
        }
        if (takeStructuredName) {
            put("N", fromLoser = true) { alignedNameExtras(it, merged) }
        } else {
            val nameVotes = listOf(
                MergeSingletonField.PREFIX to (keeper.prefix to duplicate.prefix),
                MergeSingletonField.GIVEN_NAME to (keeper.givenName to duplicate.givenName),
                MergeSingletonField.MIDDLE_NAME to (keeper.middleName to duplicate.middleName),
                MergeSingletonField.FAMILY_NAME to (keeper.familyName to duplicate.familyName),
                MergeSingletonField.SUFFIX to (keeper.suffix to duplicate.suffix),
            ).mapNotNull { (field, pair) ->
                val (left, right) = pair
                if (left.isBlank() && right.isBlank()) null
                else usesDuplicate(field, left, right, fromDuplicate, keepProfile)
            }
            when {
                nameVotes.isEmpty() || nameVotes.none { it } ->
                    put("N", fromLoser = false) { alignedNameExtras(it, merged) }
                nameVotes.all { it } ->
                    put("N", fromLoser = true) { alignedNameExtras(it, merged) }
                else -> {
                    val base = winnerExtras["N"] ?: loserExtras["N"]
                    if (base == null) result.remove("N") else result["N"] = alignedNameExtras(base, merged)
                }
            }
        }
        val formattedNameFromDuplicate = takeStructuredName || usesDuplicate(
            MergeSingletonField.DISPLAY_NAME,
            keeper.displayName,
            duplicate.displayName,
            fromDuplicate,
            keepProfile,
        )
        put("FN", formattedNameFromDuplicate) {
            alignedFormattedName(it, merged.displayName)
        }
        put(
            "NICKNAME",
            usesDuplicate(MergeSingletonField.NICKNAME, keeper.nickname, duplicate.nickname, fromDuplicate, keepProfile),
        )
        put(
            "X-PHONETIC-FIRST-NAME",
            usesDuplicate(MergeSingletonField.PHONETIC_GIVEN, keeper.phoneticGiven, duplicate.phoneticGiven, fromDuplicate, keepProfile),
        )
        put(
            "X-PHONETIC-LAST-NAME",
            usesDuplicate(MergeSingletonField.PHONETIC_FAMILY, keeper.phoneticFamily, duplicate.phoneticFamily, fromDuplicate, keepProfile),
        )
        put(
            "ORG",
            usesDuplicate(MergeSingletonField.ORGANIZATION, keeper.organization, duplicate.organization, fromDuplicate, keepProfile),
        )
        put("TITLE", usesDuplicate(MergeSingletonField.TITLE, keeper.title, duplicate.title, fromDuplicate, keepProfile))
        put("ROLE", usesDuplicate(MergeSingletonField.ROLE, keeper.role, duplicate.role, fromDuplicate, keepProfile))
        put("GENDER", usesDuplicate(MergeSingletonField.GENDER, keeper.gender, duplicate.gender, fromDuplicate, keepProfile))
        put("BDAY", usesDuplicate(MergeSingletonField.BIRTHDAY, keeper.birthday, duplicate.birthday, fromDuplicate, keepProfile))
        put(
            "BIRTHPLACE",
            usesDuplicate(MergeSingletonField.BIRTHPLACE, keeper.birthplace, duplicate.birthplace, fromDuplicate, keepProfile),
        )
        put(
            "ANNIVERSARY",
            usesDuplicate(MergeSingletonField.ANNIVERSARY, keeper.anniversary, duplicate.anniversary, fromDuplicate, keepProfile),
        )
        put(
            "DEATHDATE",
            usesDuplicate(MergeSingletonField.DEATH_DATE, keeper.deathDate, duplicate.deathDate, fromDuplicate, keepProfile),
        )
        put(
            "DEATHPLACE",
            usesDuplicate(MergeSingletonField.DEATH_PLACE, keeper.deathPlace, duplicate.deathPlace, fromDuplicate, keepProfile),
        )
        put("NOTE", usesDuplicate(MergeSingletonField.NOTES, keeper.notes, duplicate.notes, fromDuplicate, keepProfile))
        put("TZ", usesDuplicate(MergeSingletonField.TIME_ZONE, keeper.timeZone, duplicate.timeZone, fromDuplicate, keepProfile))
        put("X-MANAGERSNAME", managerFromDuplicate)
        put(
            "RELATIONSHIP",
            usesDuplicate(
                MergeSingletonField.RELATIONSHIP,
                keeper.relationshipToYou,
                duplicate.relationshipToYou,
                fromDuplicate,
                keepProfile,
            ),
        )
        put("PHOTO", copyPhotoFromDuplicate)
        putCategories(result, winnerExtras, loserExtras, keeper.categories, duplicate.categories, merged.categories)
        return ContactScalarVcardExtras.encode(result)
    }

    private fun putCategories(
        result: MutableMap<String, VCardSyntaxExtras>,
        winnerExtras: Map<String, VCardSyntaxExtras>,
        loserExtras: Map<String, VCardSyntaxExtras>,
        keeperCategories: String,
        duplicateCategories: String,
        mergedCategories: String,
    ) {
        val mergedKeys = categoryKeys(mergedCategories)
        when {
            categoryKeys(keeperCategories) == mergedKeys -> {
                val extras = winnerExtras["CATEGORIES"]
                if (extras == null) result.remove("CATEGORIES") else result["CATEGORIES"] = extras
            }
            categoryKeys(duplicateCategories) == mergedKeys -> {
                val extras = loserExtras["CATEGORIES"]
                if (extras == null) result.remove("CATEGORIES") else result["CATEGORIES"] = extras
            }
            else -> {
                val base = winnerExtras["CATEGORIES"] ?: loserExtras["CATEGORIES"]
                if (base == null) result.remove("CATEGORIES")
                else result["CATEGORIES"] = base.copy(rawValue = mergedCategories)
            }
        }
    }

    private fun categoryKeys(raw: String): List<String> =
        ContactCategories.parse(raw).map { it.lowercase() }

    private fun structuredNameRaw(contact: ContactEntity): String = listOf(
        contact.familyName.trim(),
        contact.givenName.trim(),
        contact.middleName.trim(),
        contact.prefix.trim(),
        contact.suffix.trim(),
    ).joinToString(";")

    private fun nameComponents(raw: String): List<String> {
        val parts = raw.split(';')
        return List(5) { index -> parts.getOrElse(index) { "" }.trim() }
    }

    private fun alignedNameExtras(extras: VCardSyntaxExtras, contact: ContactEntity): VCardSyntaxExtras {
        val raw = structuredNameRaw(contact)
        if (extras.rawValue.isBlank()) return extras
        if (nameComponents(extras.rawValue) == nameComponents(raw)) return extras
        return extras.copy(rawValue = raw)
    }

    /**
     * The chosen side supplies the stored value. Group, type, and other parameters
     * stay with the keeper, including the note Markdown flag handled beside this.
     * A blank keeper format does not pick up the other contact's format.
     */
    private fun valueWithKeeperFormat(
        keeper: VCardSyntaxExtras?,
        chosen: VCardSyntaxExtras?,
    ): VCardSyntaxExtras? {
        val format = keeper ?: VCardSyntaxExtras()
        val raw = chosen?.rawValue?.takeIf { it.isNotBlank() } ?: format.rawValue
        val combined = format.copy(rawValue = raw)
        return combined.takeIf { it.hasPersistableMetadata() }
    }

    private fun alignedFormattedName(extras: VCardSyntaxExtras, displayName: String): VCardSyntaxExtras {
        val raw = extras.rawValue.trim()
        val name = displayName.trim()
        if (raw.isEmpty() || raw == name) return extras
        return extras.copy(rawValue = name)
    }

    /** These extras are part of the value, not a display format. */
    private val VALUE_WITH_OWN_EXTRAS = setOf("PHOTO", "X-MANAGERSNAME")

    private val OWNED_EXTRA_KEYS = setOf(
        "FN",
        "N",
        "NICKNAME",
        "X-PHONETIC-FIRST-NAME",
        "X-PHONETIC-LAST-NAME",
        "ORG",
        "TITLE",
        "ROLE",
        "GENDER",
        "BDAY",
        "BIRTHPLACE",
        "ANNIVERSARY",
        "DEATHDATE",
        "DEATHPLACE",
        "CATEGORIES",
        "NOTE",
        "TZ",
        "X-MANAGERSNAME",
        "RELATIONSHIP",
        "PHOTO",
    )
}
