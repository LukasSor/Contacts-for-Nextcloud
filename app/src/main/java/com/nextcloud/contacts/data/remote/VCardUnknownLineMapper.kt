package com.nextcloud.contacts.data.remote

import com.nextcloud.contacts.data.local.StoredVCardLine
import com.nextcloud.contacts.data.local.VCardUnknownLines
import ezvcard.VCard
import ezvcard.property.Address
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.property.Categories
import ezvcard.property.Deathdate
import ezvcard.property.Email
import ezvcard.property.FormattedName
import ezvcard.property.Gender
import ezvcard.property.Geo
import ezvcard.property.Impp
import ezvcard.property.Language
import ezvcard.property.Nickname
import ezvcard.property.Note
import ezvcard.property.Organization
import ezvcard.property.Photo
import ezvcard.property.RawProperty
import ezvcard.property.Related
import ezvcard.property.Role
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Timezone
import ezvcard.property.Title
import ezvcard.property.Uid
import ezvcard.property.Url
import ezvcard.property.SimpleProperty
import ezvcard.property.VCardProperty

internal object VCardUnknownLineMapper {

    private const val ADR_GEO_X_PROP = "X-ADR-GEO"
    private const val X_ABLABEL_PROP = "X-ABLABEL"

    fun collectFromVCard(vcard: VCard): List<StoredVCardLine> {
        val knownGroups = vcard.properties
            .filter { isKnownMappedProperty(it) && !isAbLabelProperty(it) }
            .mapNotNull { it.group?.trim()?.takeIf { group -> group.isNotBlank() } }
            .toSet()
        return vcard.properties.mapNotNull { property ->
            if (isKnownMappedProperty(property)) return@mapNotNull null
            if (isAbLabelProperty(property)) {
                val group = property.group?.trim().orEmpty()
                if (group.isNotBlank() && group in knownGroups) return@mapNotNull null
            }
            storedLineFromProperty(property)
        }
    }

    fun writeToVCard(vcard: VCard, rawJson: String) {
        val lines = VCardUnknownLines.decode(rawJson)
        if (lines.isEmpty()) return
        val existing = vcard.properties.map { propertyFingerprint(it) }.toMutableSet()
        lines.forEach { line ->
            val fingerprint = line.fingerprint()
            if (fingerprint in existing) return@forEach
            val property = rawPropertyFromStored(line)
            vcard.addProperty(property)
            existing.add(fingerprint)
        }
    }

    private fun storedLineFromProperty(property: VCardProperty): StoredVCardLine? {
        val name = property.vcardPropertyName()
        if (name.isBlank()) return null
        val value = property.vcardPropertyValue()
        val parameters = VCardMapper.allVCardParameters(property)
        if (value.isBlank() && parameters.isEmpty()) return null
        return StoredVCardLine(
            propertyName = name,
            group = property.group?.trim().orEmpty(),
            value = value,
            parameters = parameters,
        )
    }

    private fun rawPropertyFromStored(line: StoredVCardLine): RawProperty {
        val property = RawProperty(line.propertyName, line.value)
        line.group.trim().takeIf { it.isNotBlank() }?.let { property.group = it }
        line.parameters.forEach { (name, value) ->
            if (name.isBlank() || !VCardMapper.parameterAllowedOnVCard4(name)) return@forEach
            if (name.equals("TYPE", ignoreCase = true)) {
                property.addParameter("TYPE", value)
            } else {
                property.setParameter(name, VCardMapper.escapeVCardParameter(value))
            }
        }
        return property
    }

    private fun propertyFingerprint(property: VCardProperty): String =
        VCardUnknownLines.fingerprint(
            group = property.group?.trim().orEmpty(),
            propertyName = property.vcardPropertyName(),
            value = property.vcardPropertyValue(),
            parameters = VCardMapper.allVCardParameters(property),
        )

    private fun isAbLabelProperty(property: VCardProperty): Boolean =
        property.vcardPropertyName().equals(X_ABLABEL_PROP, ignoreCase = true)

    private fun isKnownMappedProperty(property: VCardProperty): Boolean {
        when (property) {
            is Uid -> return true
            is FormattedName -> return true
            is StructuredName -> return true
            is Nickname -> return true
            is Organization -> return true
            is Title -> return true
            is Role -> return true
            is Gender -> return true
            is Birthday -> return true
            is Anniversary -> return true
            is Deathdate -> return true
            is Categories -> return true
            is Note -> return true
            is Timezone -> return true
            is Photo -> return true
            is Telephone -> return true
            is Email -> return true
            is Address -> return true
            is Url -> return true
            is Impp -> return true
            is Language -> return true
            is Related -> return true
            is Geo -> return true
        }
        if (property is RawProperty && VCardMapper.isImportedAsCustomField(property)) return true
        val name = property.vcardPropertyName()
        if (name.isBlank()) return false
        if (name.equals(ADR_GEO_X_PROP, ignoreCase = true)) return true
        if (name.equals("BIRTHPLACE", ignoreCase = true)) return true
        if (name.equals("DEATHPLACE", ignoreCase = true)) return true
        if (name.equals("X-PHONETIC-FIRST-NAME", ignoreCase = true)) return true
        if (name.equals("X-PHONETIC-LAST-NAME", ignoreCase = true)) return true
        if (name.equals("X-MANAGERSNAME", ignoreCase = true)) return true
        if (name.equals("RELATIONSHIP", ignoreCase = true)) return true
        if (name.equals("X-RELATIONSHIP", ignoreCase = true)) return true
        if (name.equals("X-SOCIALPROFILE", ignoreCase = true)) return true
        if (name.equals("CLOUD", ignoreCase = true)) return true
        return false
    }

    private fun VCardProperty.vcardPropertyName(): String = when (this) {
        is RawProperty -> propertyName.orEmpty()
        is Telephone -> "TEL"
        is Email -> "EMAIL"
        is Address -> "ADR"
        is Url -> "URL"
        is Impp -> "IMPP"
        is Language -> "LANG"
        is Related -> "RELATED"
        is Geo -> "GEO"
        is FormattedName -> "FN"
        is StructuredName -> "N"
        is Nickname -> "NICKNAME"
        is Organization -> "ORG"
        is Title -> "TITLE"
        is Role -> "ROLE"
        is Gender -> "GENDER"
        is Birthday -> "BDAY"
        is Anniversary -> "ANNIVERSARY"
        is Deathdate -> "DEATHDATE"
        is Categories -> "CATEGORIES"
        is Note -> "NOTE"
        is Timezone -> "TZ"
        is Photo -> "PHOTO"
        is Uid -> "UID"
        else -> javaClass.simpleName
            ?.replace(Regex("Property$"), "")
            ?.uppercase()
            .orEmpty()
    }

    private fun VCardProperty.vcardPropertyValue(): String = when (this) {
        is Telephone -> text.orEmpty()
        is Address -> listOf(
            poBox.orEmpty(),
            extendedAddress.orEmpty(),
            streetAddress.orEmpty(),
            locality.orEmpty(),
            region.orEmpty(),
            postalCode.orEmpty(),
            country.orEmpty(),
        ).joinToString(";")
        is Geo -> VCardMapper.formatGeoValue(latitude, longitude)
        is StructuredName -> listOf(
            family.orEmpty(),
            given.orEmpty(),
            additionalNames.firstOrNull().orEmpty(),
            prefixes.firstOrNull().orEmpty(),
            suffixes.firstOrNull().orEmpty(),
        ).joinToString(";")
        is Organization -> values.joinToString(";")
        is Categories -> values.joinToString(",")
        is Nickname -> values.joinToString(",")
        is RawProperty -> value.orEmpty()
        is SimpleProperty<*> -> getValue()?.toString().orEmpty()
        else -> ""
    }
}
