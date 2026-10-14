package com.nextcloud.contacts.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteUrl: String? = null,
    val remoteUid: String? = null,
    val etag: String? = null,
    val displayName: String = "",
    val prefix: String = "",
    val givenName: String = "",
    val middleName: String = "",
    val familyName: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val phoneticGiven: String = "",
    val phoneticFamily: String = "",
    val organization: String = "",
    val title: String = "",
    val role: String = "",
    val gender: String = "",
    val birthday: String = "",
    val birthplace: String = "",
    val anniversary: String = "",
    val deathDate: String = "",
    val deathPlace: String = "",
    val categories: String = "",
    val notes: String = "",
    /** Local-only: when true, [notes] is shown and edited as Markdown. */
    val notesMarkdown: Boolean = false,
    val geo: String = "",
    val timeZone: String = "",
    val managersName: String = "",
    /** vCard UID of the linked line manager (X-MANAGERSNAME;UID=…). */
    val managersUid: String = "",
    val relationshipToYou: String = "",
    val isFavorite: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    /** Local Nextcloud user id confirmed for 1:1 Talk on the signed-in server. */
    val talkInviteVerifiedUserId: String? = null,
    /** Canonical cloud id at verification time (when known). */
    val talkInviteVerifiedCloudId: String? = null,
    /** JSON map of canonical cloud id → cached sharees profile (display name, avatar). */
    val nextcloudProfileLookupCache: String = "",
    /** JSON map of normalized website URL → cached favicon file + HTTP validators. */
    val websiteLogoCache: String = "",
    /** JSON snapshot of Connected-apps discovery for this contact's phone numbers. */
    val providerIntegrationsCache: String = "",
    /** JSON map of vCard property → syntax extras for scalar fields (NOTE, N, …). */
    val vcardScalarExtrasJson: String = "",
    /** Local only. Shows property, group, and parameter rows. Never written to the vCard. */
    val editFieldSyntaxEnabled: Boolean = false,
    /** JSON array of vCard property lines not mapped into the app model (sync round-trip). */
    val vcardUnknownLinesJson: String = "",
)

@Entity(
    tableName = "phones",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class PhoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val type: String = "mobile",
    val value: String,
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "emails",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class EmailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val type: String = "home",
    val value: String,
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "addresses",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class AddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val type: String = "home",
    val poBox: String = "",
    val extendedAddress: String = "",
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
    /** WGS-84 coordinates as `latitude;longitude` (matches vCard GEO). */
    val geo: String = "",
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    /** Full ADR value line when edited in field-syntax mode. */
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "urls",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class UrlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val type: String = "home",
    val value: String,
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "im_accounts",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class ImEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val type: String = "signal",
    val value: String,
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "social_profiles",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class SocialProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val type: String = "other",
    val value: String,
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "cloud_ids",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class CloudIdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val type: String = "home",
    val value: String,
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "languages",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class LanguageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val value: String,
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "related_names",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class RelatedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val type: String = "contact",
    val value: String,
    val vcardPropertyGroup: String = "",
    val vcardTypeParam: String? = null,
    val vcardExtraParametersJson: String = "",
    val vcardAbLabel: String = "",
    val vcardRawValue: String = "",
)

@Entity(
    tableName = "custom_fields",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("contactId")],
)
data class CustomFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val label: String,
    val value: String,
    val propertyName: String = DEFAULT_CUSTOM_FIELD_PROPERTY,
    val propertyGroup: String? = null,
    val typeParam: String? = null,
    val extraParametersJson: String = "",
)

/**
 * Lightweight row for the main contact list — no relation joins.
 * Room maps columns by name from the contacts table.
 */
@Immutable
data class ContactListItem(
    val id: Long,
    val displayName: String,
    val givenName: String = "",
    val familyName: String = "",
    val nickname: String = "",
    val organization: String = "",
    val isFavorite: Boolean,
    val categories: String = "",
    val birthday: String = "",
    val hasPhone: Boolean = false,
    val hasEmail: Boolean = false,
    val hasNextcloudAccount: Boolean = false,
)

/** Line-manager picker row. */
data class LineManagerListRow(
    val id: Long,
    val uid: String,
    val displayName: String,
    val organization: String,
    val email: String?,
)

data class FingerprintContactRow(
    val id: Long,
    val displayName: String,
)

data class ContactIdValue(
    val contactId: Long,
    val value: String,
)

/** Lightweight relation rows for Nextcloud list filtering (no full contact join). */
data class ContactListNextcloudHint(
    val contactId: Long,
    val kind: String,
    val type: String,
    val value: String,
)

data class ContactWithDetails(
    @Embedded val contact: ContactEntity,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val phones: List<PhoneEntity>,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val emails: List<EmailEntity>,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val addresses: List<AddressEntity>,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val urls: List<UrlEntity>,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val imAccounts: List<ImEntity>,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val socialProfiles: List<SocialProfileEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val cloudIds: List<CloudIdEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val languages: List<LanguageEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val relatedNames: List<RelatedEntity>,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val customFields: List<CustomFieldEntity> = emptyList(),
)

data class AddressInput(
    val type: String = "home",
    val poBox: String = "",
    val extendedAddress: String = "",
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
    val geo: String = "",
    val propertyName: String = "ADR",
    val syntax: VCardSyntaxExtras = VCardSyntaxExtras(),
) {
    fun isBlank(): Boolean =
        geo.isBlank() &&
            poBox.isBlank() && extendedAddress.isBlank() && street.isBlank() &&
            city.isBlank() && region.isBlank() && postalCode.isBlank() && country.isBlank()

    fun formatted(): String = listOf(street, extendedAddress, city, region, postalCode, country, poBox)
        .filter { it.isNotBlank() }
        .joinToString(", ")

    /** Semicolon-separated ADR value for vCard free-form editing. */
    fun rawAdrValue(): String {
        syntax.rawValue.trim().takeIf { it.isNotEmpty() }?.let { return it }
        return listOf(poBox, extendedAddress, street, city, region, postalCode, country).joinToString(";")
    }

    fun displayTypeForFreeForm(): String =
        syntax.typeParam.trim().ifBlank { type }

    /** Postal block lines for contact detail (street through country). */
    fun detailPostalLines(poBoxPrefix: String = "PO Box"): List<String> = buildList {
        street.trim().takeIf { it.isNotBlank() }?.let { add(it) }
        extendedAddress.trim().takeIf { it.isNotBlank() }?.let { add(it) }
        poBox.trim().takeIf { it.isNotBlank() }?.let { box ->
            add(if (box.contains("box", ignoreCase = true)) box else "$poBoxPrefix $box")
        }
        detailLocalityLine()?.let { add(it) }
        country.trim().takeIf { it.isNotBlank() }?.let { add(it) }
    }

    fun detailPostalCopyText(poBoxPrefix: String = "PO Box"): String =
        detailPostalLines(poBoxPrefix).joinToString("\n")

    /** Human-readable coordinates for detail display and copy (e.g. `48.1, 11.5`). */
    fun detailCoordinatesDisplayText(): String? {
        val trimmed = geo.trim()
        if (trimmed.isEmpty()) return null
        return formatDetailGeoDisplay(trimmed) ?: trimmed
    }

    private fun formatDetailGeoDisplay(value: String): String? {
        val parts = value.split(';', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return "${formatDetailCoordinateComponent(lat)}, ${formatDetailCoordinateComponent(lon)}"
    }

    private fun formatDetailCoordinateComponent(value: Double): String {
        val text = value.toString()
        return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
    }

    private fun detailLocalityLine(): String? {
        val cityPart = city.trim()
        val regionPart = region.trim()
        val postalPart = postalCode.trim()
        if (cityPart.isEmpty() && regionPart.isEmpty() && postalPart.isEmpty()) return null
        return buildString {
            when {
                postalPart.isNotBlank() && cityPart.isNotBlank() -> {
                    append(postalPart)
                    append(' ')
                    append(cityPart)
                }
                cityPart.isNotBlank() -> append(cityPart)
                postalPart.isNotBlank() -> append(postalPart)
            }
            if (regionPart.isNotBlank()) {
                if (isNotEmpty()) append(", ")
                append(regionPart)
            }
        }.takeIf { it.isNotBlank() }
    }
}

data class ContactInput(
    val prefix: String = "",
    val givenName: String = "",
    val middleName: String = "",
    val familyName: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val phoneticGiven: String = "",
    val phoneticFamily: String = "",
    val organization: String = "",
    val title: String = "",
    val role: String = "",
    val gender: String = "",
    val birthday: String = "",
    val birthplace: String = "",
    val anniversary: String = "",
    val deathDate: String = "",
    val deathPlace: String = "",
    val categories: String = "",
    val notes: String = "",
    val notesMarkdown: Boolean = false,
    val geo: String = "",
    val timeZone: String = "",
    val managersName: String = "",
    val managersUid: String = "",
    val relationshipToYou: String = "",
    val phones: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.phone()),
    val emails: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.email()),
    val addresses: List<AddressInput> = listOf(AddressInput()),
    val urls: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.url()),
    val imAccounts: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.im()),
    val socialProfiles: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.social()),
    val cloudIds: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.cloud()),
    val languages: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput(type = "", value = "", propertyName = "LANG")),
    val relatedNames: List<VCardTypedFieldInput> = listOf(VCardTypedFieldInput.related()),
    val customFields: List<CustomFieldInput> = emptyList(),
    val scalarVcardExtras: Map<String, VCardSyntaxExtras> = emptyMap(),
    val vcardUnknownLinesJson: String = "",
    val photoBytes: ByteArray? = null,
    val removePhoto: Boolean = false,
) {
    val displayName: String
        get() = listOf(prefix, givenName, middleName, familyName, suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { nickname.ifBlank { organization.ifBlank { "Unnamed" } } }

    fun hasImportableContent(): Boolean {
        val hasName = givenName.isNotBlank() ||
            familyName.isNotBlank() ||
            nickname.isNotBlank() ||
            organization.isNotBlank()
        val hasPhone = phones.any { it.value.isNotBlank() }
        val hasEmail = emails.any { it.value.isNotBlank() }
        return hasName || hasPhone || hasEmail
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactInput) return false
        return prefix == other.prefix &&
            givenName == other.givenName &&
            middleName == other.middleName &&
            familyName == other.familyName &&
            suffix == other.suffix &&
            nickname == other.nickname &&
            phoneticGiven == other.phoneticGiven &&
            phoneticFamily == other.phoneticFamily &&
            organization == other.organization &&
            title == other.title &&
            role == other.role &&
            gender == other.gender &&
            birthday == other.birthday &&
            birthplace == other.birthplace &&
            anniversary == other.anniversary &&
            deathDate == other.deathDate &&
            deathPlace == other.deathPlace &&
            categories == other.categories &&
            notes == other.notes &&
            notesMarkdown == other.notesMarkdown &&
            geo == other.geo &&
            timeZone == other.timeZone &&
            managersName == other.managersName &&
            managersUid == other.managersUid &&
            relationshipToYou == other.relationshipToYou &&
            phones == other.phones &&
            emails == other.emails &&
            addresses == other.addresses &&
            urls == other.urls &&
            imAccounts == other.imAccounts &&
            socialProfiles == other.socialProfiles &&
            cloudIds == other.cloudIds &&
            languages == other.languages &&
            relatedNames == other.relatedNames &&
            customFields == other.customFields &&
            scalarVcardExtras == other.scalarVcardExtras &&
            vcardUnknownLinesJson == other.vcardUnknownLinesJson &&
            removePhoto == other.removePhoto &&
            (photoBytes == null && other.photoBytes == null ||
                photoBytes != null && other.photoBytes != null && photoBytes.contentEquals(other.photoBytes))
    }

    override fun hashCode(): Int {
        var result = prefix.hashCode()
        result = 31 * result + givenName.hashCode()
        result = 31 * result + middleName.hashCode()
        result = 31 * result + familyName.hashCode()
        result = 31 * result + suffix.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + phoneticGiven.hashCode()
        result = 31 * result + phoneticFamily.hashCode()
        result = 31 * result + organization.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + gender.hashCode()
        result = 31 * result + birthday.hashCode()
        result = 31 * result + birthplace.hashCode()
        result = 31 * result + anniversary.hashCode()
        result = 31 * result + deathDate.hashCode()
        result = 31 * result + deathPlace.hashCode()
        result = 31 * result + categories.hashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + notesMarkdown.hashCode()
        result = 31 * result + geo.hashCode()
        result = 31 * result + timeZone.hashCode()
        result = 31 * result + managersName.hashCode()
        result = 31 * result + managersUid.hashCode()
        result = 31 * result + relationshipToYou.hashCode()
        result = 31 * result + phones.hashCode()
        result = 31 * result + emails.hashCode()
        result = 31 * result + addresses.hashCode()
        result = 31 * result + urls.hashCode()
        result = 31 * result + imAccounts.hashCode()
        result = 31 * result + socialProfiles.hashCode()
        result = 31 * result + cloudIds.hashCode()
        result = 31 * result + languages.hashCode()
        result = 31 * result + relatedNames.hashCode()
        result = 31 * result + customFields.hashCode()
        result = 31 * result + scalarVcardExtras.hashCode()
        result = 31 * result + vcardUnknownLinesJson.hashCode()
        result = 31 * result + removePhoto.hashCode()
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        return result
    }
}
