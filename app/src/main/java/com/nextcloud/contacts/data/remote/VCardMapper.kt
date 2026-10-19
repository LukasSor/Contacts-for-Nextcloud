package com.nextcloud.contacts.data.remote

import com.nextcloud.contacts.data.local.AddressEntity
import com.nextcloud.contacts.data.local.AddressInput
import com.nextcloud.contacts.data.local.ContactScalarVcardExtras
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import com.nextcloud.contacts.data.local.VCardUnknownLines
import com.nextcloud.contacts.data.local.VCardTypedFieldInput
import com.nextcloud.contacts.data.local.CloudIdEntity
import com.nextcloud.contacts.data.local.CustomFieldEntity
import com.nextcloud.contacts.data.local.CustomFieldExtras
import com.nextcloud.contacts.data.local.CustomFieldInput
import com.nextcloud.contacts.data.local.DEFAULT_CUSTOM_FIELD_PROPERTY
import com.nextcloud.contacts.data.local.toEntity
import com.nextcloud.contacts.data.local.toFieldInput
import com.nextcloud.contacts.data.local.toInput
import com.nextcloud.contacts.data.local.ContactEntity
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.local.ContactInput
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.local.EmailEntity
import com.nextcloud.contacts.data.local.ImEntity
import com.nextcloud.contacts.data.local.LanguageEntity
import com.nextcloud.contacts.data.local.PhoneEntity
import com.nextcloud.contacts.data.local.RelatedEntity
import com.nextcloud.contacts.data.local.SocialProfileEntity
import com.nextcloud.contacts.data.local.UrlEntity
import com.nextcloud.contacts.util.parseIsoOrBasicLocalDate
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.AddressType
import ezvcard.parameter.EmailType
import ezvcard.parameter.ImageType
import ezvcard.parameter.RelatedType
import ezvcard.parameter.TelephoneType
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
import ezvcard.property.Related
import ezvcard.property.Role
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Timezone
import ezvcard.property.Title
import ezvcard.property.DateOrTimeProperty
import ezvcard.property.Uid
import ezvcard.property.RawProperty
import ezvcard.property.Url
import ezvcard.parameter.VCardParameters
import ezvcard.property.VCardProperty
import ezvcard.util.PartialDate
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.Temporal
import java.util.UUID

internal data class AlignedContactName(
    val displayName: String,
    val scalarExtras: Map<String, VCardSyntaxExtras>,
)

object VCardMapper {

    /** Indexed per-address coordinates for multi-ADR round-trip (see [parseIndexedAddressGeos]). */
    private const val ADR_GEO_X_PROP = "X-ADR-GEO"

    private const val CUSTOM_FIELD_X_PROP = DEFAULT_CUSTOM_FIELD_PROPERTY

    private const val X_ABLABEL_PROP = "X-ABLABEL"

    /** Extended X- properties mapped elsewhere — never imported as custom fields. */
    private val NON_CUSTOM_EXTENDED_X_PROPERTIES = setOf(
        "X-PHONETIC-FIRST-NAME",
        "X-PHONETIC-LAST-NAME",
        "X-MANAGERSNAME",
        "X-RELATIONSHIP",
        "X-SOCIALPROFILE",
        ADR_GEO_X_PROP,
        X_ABLABEL_PROP,
    )

    fun toVCard(contact: ContactWithDetails, photoBytes: ByteArray? = null): String {
        val vcard = VCard()
        val c = contact.contact
        val uid = c.remoteUid ?: UUID.randomUUID().toString()
        vcard.uid = Uid(uid)
        val scalar = ContactScalarVcardExtras.decode(c.vcardScalarExtrasJson)
        writeScalarContactProperties(vcard, c, scalar)
        writePhotoProperty(vcard, scalar["PHOTO"], photoBytes)
        val abLabelGroupsWritten = mutableSetOf<String>()
        contact.phones.forEach { phone ->
            val telValue = phone.vcardRawValue.ifBlank { phone.value }
            if (telValue.isBlank()) return@forEach
            val tel = Telephone(telValue)
            applyPhoneTypes(tel, phone)
            applyStoredVcardSyntax(tel, phone.vcardPropertyGroup, phone.vcardTypeParam, phone.vcardExtraParametersJson)
            vcard.telephoneNumbers.add(tel)
            writeAbLabelIfNeeded(vcard, phone.vcardPropertyGroup, phone.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.emails.forEach { email ->
            val mailValue = email.vcardRawValue.ifBlank { email.value }
            if (mailValue.isBlank()) return@forEach
            val mail = Email(mailValue)
            applyEmailTypes(mail, email)
            applyStoredVcardSyntax(mail, email.vcardPropertyGroup, email.vcardTypeParam, email.vcardExtraParametersJson)
            vcard.emails.add(mail)
            writeAbLabelIfNeeded(vcard, email.vcardPropertyGroup, email.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.addresses.forEach { address ->
            val adr = addressFromEntity(address)
            if (!addressHasComponents(adr)) return@forEach
            applyAddressTypes(adr, address)
            applyStoredVcardSyntax(
                adr,
                address.vcardPropertyGroup,
                address.vcardTypeParam,
                address.vcardExtraParametersJson,
            )
            vcard.addresses.add(adr)
            writeAbLabelIfNeeded(vcard, address.vcardPropertyGroup, address.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.addresses.forEachIndexed { index, address ->
            if (address.geo.isBlank()) return@forEachIndexed
            parseGeo(address.geo)?.let { (lat, lon) ->
                vcard.addGeo(Geo(lat, lon))
                val prop = vcard.addExtendedProperty(ADR_GEO_X_PROP, address.geo)
                prop.setParameter("INDEX", index.toString())
            }
        }
        contact.urls.forEach { url ->
            val linkValue = url.vcardRawValue.ifBlank { url.value }
            if (linkValue.isBlank()) return@forEach
            val link = Url(linkValue)
            applyUrlTypes(link, url)
            applyStoredVcardSyntax(link, url.vcardPropertyGroup, url.vcardTypeParam, url.vcardExtraParametersJson)
            vcard.addUrl(link)
            writeAbLabelIfNeeded(vcard, url.vcardPropertyGroup, url.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.imAccounts.forEach { im ->
            val protocol = im.type.ifBlank { "other" }.lowercase()
            val handle = sanitizeImppHandle(im.vcardRawValue.ifBlank { im.value })
            if (handle.isBlank()) return@forEach
            val impp = buildImpp(protocol, handle) ?: return@forEach
            applyImTypes(impp, im)
            applyStoredVcardSyntax(impp, im.vcardPropertyGroup, im.vcardTypeParam, im.vcardExtraParametersJson)
            vcard.addImpp(impp)
            writeAbLabelIfNeeded(vcard, im.vcardPropertyGroup, im.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.socialProfiles.forEach { social ->
            val value = social.vcardRawValue.ifBlank { social.value }
            if (value.isBlank()) return@forEach
            val property = vcard.addExtendedProperty("X-SOCIALPROFILE", value)
            applySocialTypes(property, social)
            applyStoredVcardSyntax(
                property,
                social.vcardPropertyGroup,
                social.vcardTypeParam,
                social.vcardExtraParametersJson,
            )
            writeAbLabelIfNeeded(vcard, social.vcardPropertyGroup, social.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.cloudIds.forEach { cloud ->
            val value = cloud.vcardRawValue.ifBlank { cloud.value }
            if (value.isBlank()) return@forEach
            val property = vcard.addExtendedProperty("CLOUD", value)
            applyCloudTypes(property, cloud)
            applyStoredVcardSyntax(
                property,
                cloud.vcardPropertyGroup,
                cloud.vcardTypeParam,
                cloud.vcardExtraParametersJson,
            )
            writeAbLabelIfNeeded(vcard, cloud.vcardPropertyGroup, cloud.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.languages.forEach { lang ->
            val value = lang.vcardRawValue.ifBlank { lang.value }
            if (value.isBlank()) return@forEach
            val language = Language(value)
            applyStoredVcardSyntax(
                language,
                lang.vcardPropertyGroup,
                lang.vcardTypeParam,
                lang.vcardExtraParametersJson,
            )
            vcard.addLanguage(language)
            writeAbLabelIfNeeded(vcard, lang.vcardPropertyGroup, lang.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.relatedNames.forEach { related ->
            val value = related.vcardRawValue.ifBlank { related.value }
            if (value.isBlank()) return@forEach
            val rel = Related(value)
            applyRelatedTypes(rel, related)
            applyStoredVcardSyntax(
                rel,
                related.vcardPropertyGroup,
                related.vcardTypeParam,
                related.vcardExtraParametersJson,
            )
            vcard.addRelated(rel)
            writeAbLabelIfNeeded(vcard, related.vcardPropertyGroup, related.vcardAbLabel, abLabelGroupsWritten)
        }
        contact.customFields.forEach { field ->
            writeCustomFieldToVCard(vcard, field)
        }
        VCardUnknownLineMapper.writeToVCard(vcard, c.vcardUnknownLinesJson)
        // Match Nextcloud Contacts web default (vCard 4.0) so LANG/RELATED/GENDER/dates sync.
        val writer = StringWriter()
        Ezvcard.write(vcard).version(VCardVersion.V4_0).go(writer)
        return writer.toString()
    }

    fun parseAllVCards(vcardText: String): List<VCard> =
        Ezvcard.parse(StringReader(vcardText)).all()

    fun exportAllToVcf(
        contacts: List<ContactWithDetails>,
        photoLoader: (Long) -> ByteArray?,
    ): String = buildString {
        contacts.forEach { contact ->
            append(toVCard(contact, photoLoader(contact.contact.id)))
            if (isNotEmpty() && !endsWith("\n")) append('\n')
        }
    }

    fun fromVCard(vcardText: String, remoteUrl: String, etag: String?): ParsedContact {
        val vcard = Ezvcard.parse(StringReader(vcardText)).first()
        return fromVCardObject(vcard, remoteUrl, etag)
    }

    fun fromVCardObject(vcard: VCard, remoteUrl: String, etag: String?): ParsedContact {
        val structured = vcard.structuredName
        val prefix = structured?.prefixes?.firstOrNull().orEmpty()
        val given = structured?.given.orEmpty()
        val middle = structured?.additionalNames?.firstOrNull().orEmpty()
        val family = structured?.family.orEmpty()
        val suffix = structured?.suffixes?.firstOrNull().orEmpty()
        val nickname = vcard.nickname?.values?.firstOrNull().orEmpty()
        val phoneticGiven = vcard.getExtendedProperty("X-PHONETIC-FIRST-NAME")?.value.orEmpty()
        val phoneticFamily = vcard.getExtendedProperty("X-PHONETIC-LAST-NAME")?.value.orEmpty()
        val title = vcard.titles.firstOrNull()?.value.orEmpty()
        val role = vcard.roles.firstOrNull()?.value.orEmpty()
        val organization = vcard.organization?.values?.firstOrNull().orEmpty()
        val gender = vcard.gender?.gender.orEmpty()
        val birthday = formatDateProperty(vcard.birthday)
        val birthplace = vcard.getExtendedProperty("BIRTHPLACE")?.value.orEmpty()
        val anniversary = formatDateProperty(vcard.anniversary)
        val deathDate = formatDateProperty(vcard.deathdate)
        val deathPlace = vcard.getExtendedProperty("DEATHPLACE")?.value.orEmpty()
        val categories = vcard.categories?.values?.joinToString(", ").orEmpty()
        val display = vcard.formattedName?.value
            ?: listOf(prefix, given, middle, family, suffix).filter { it.isNotBlank() }.joinToString(" ")
        val notes = vcard.notes.firstOrNull()?.value.orEmpty()
        val legacyGeo = vcard.geo?.let { formatGeoValue(it.latitude, it.longitude) }.orEmpty()
        val indexedAddressGeos = parseIndexedAddressGeos(vcard)
        val standardAddressGeos = vcard.geos.map { formatGeoValue(it.latitude, it.longitude) }
        val timeZone = vcard.timezone?.text.orEmpty().ifBlank {
            vcard.timezone?.offset?.id.orEmpty()
        }
        val managersProp = vcard.getExtendedProperty("X-MANAGERSNAME")
        val managersName = managersProp?.value.orEmpty()
        val managersUid = managersProp?.getParameter("UID").orEmpty()
        val relationshipToYou = (
            vcard.getExtendedProperty("RELATIONSHIP")?.value
                ?: vcard.getExtendedProperty("X-RELATIONSHIP")?.value
            ).orEmpty().lowercase()
        val uid = vcard.uid?.value ?: remoteUrl.substringAfterLast('/').removeSuffix(".vcf")

        val contact = ContactEntity(
            remoteUrl = remoteUrl,
            remoteUid = uid,
            etag = etag,
            displayName = display.ifBlank { nickname.ifBlank { organization.ifBlank { "Unnamed" } } },
            prefix = prefix,
            givenName = given,
            middleName = middle,
            familyName = family,
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
            geo = "",
            timeZone = timeZone,
            managersName = managersName,
            managersUid = managersUid,
            relationshipToYou = relationshipToYou,
            dirty = false,
            deleted = false,
            vcardScalarExtrasJson = ContactScalarVcardExtras.encode(parseScalarVcardExtras(vcard)),
        )

        val abLabelsByGroup = abLabelsByGroup(vcard)

        val phones = vcard.telephoneNumbers.map { tel ->
            val (pickerType, extraType) = parseTelephoneTypes(tel)
            val syntax = syntaxFromVCardProperty(tel, abLabelsByGroup)
            PhoneEntity(
                contactId = 0,
                type = pickerType,
                value = tel.text,
                vcardPropertyGroup = syntax.propertyGroup,
                vcardTypeParam = extraType,
                vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                vcardAbLabel = syntax.abLabel,
                vcardRawValue = syntax.rawValue,
            )
        }

        val emails = vcard.emails.map { mail ->
            val (pickerType, extraType) = parseEmailTypes(mail)
            val syntax = syntaxFromVCardProperty(mail, abLabelsByGroup)
            EmailEntity(
                contactId = 0,
                type = pickerType,
                value = mail.value,
                vcardPropertyGroup = syntax.propertyGroup,
                vcardTypeParam = extraType,
                vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                vcardAbLabel = syntax.abLabel,
                vcardRawValue = syntax.rawValue,
            )
        }

        val addresses = vcard.addresses.mapIndexed { index, adr ->
            val (pickerType, extraType) = parseAddressTypes(adr)
            val syntax = syntaxFromVCardProperty(adr, abLabelsByGroup)
            AddressEntity(
                contactId = 0,
                type = pickerType,
                poBox = adr.poBox.orEmpty(),
                extendedAddress = adr.extendedAddress.orEmpty(),
                street = adr.streetAddress.orEmpty(),
                city = adr.locality.orEmpty(),
                region = adr.region.orEmpty(),
                postalCode = adr.postalCode.orEmpty(),
                country = adr.country.orEmpty(),
                geo = resolveAddressGeo(
                    index = index,
                    indexedGeos = indexedAddressGeos,
                    standardGeos = standardAddressGeos,
                    legacyContactGeo = legacyGeo,
                ),
                vcardPropertyGroup = syntax.propertyGroup,
                vcardTypeParam = extraType,
                vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                vcardAbLabel = syntax.abLabel,
                vcardRawValue = syntax.rawValue,
            )
        }

        val urls = vcard.urls.map { link ->
            val rawType = link.type.orEmpty()
            val syntax = syntaxFromVCardProperty(link, abLabelsByGroup)
            UrlEntity(
                contactId = 0,
                type = ContactFieldOptions.canonicalUrlType(rawType),
                value = link.value,
                vcardPropertyGroup = syntax.propertyGroup,
                vcardTypeParam = syntax.typeParam.ifBlank { null },
                vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                vcardAbLabel = syntax.abLabel,
                vcardRawValue = syntax.rawValue,
            )
        }

        val imAccounts = vcard.impps.map { im ->
            val typeFromParam = im.parameters.types.firstOrNull()
                ?: im.types.firstOrNull()?.value
            val protocol = (typeFromParam ?: im.protocol.orEmpty())
                .lowercase()
                .ifBlank { "other" }
            val handle = im.handle.orEmpty()
                .ifBlank {
                    val uri = im.uri?.toString().orEmpty()
                    when {
                        uri.contains("://") -> uri
                        uri.contains(':') && uri.substringBefore(':').equals(protocol, true) ->
                            uri.substringAfter(':')
                        else -> uri
                    }
                }
            val syntax = syntaxFromVCardProperty(im, abLabelsByGroup)
            val imTypes = buildList {
                im.types.forEach { add(it.value.uppercase()) }
                im.parameters.types.forEach { if (it.isNotBlank()) add(it.uppercase()) }
            }.distinct()
            val protocolUpper = protocol.uppercase()
            val extraTypes = imTypes.filterNot { it.equals(protocolUpper, ignoreCase = true) }
            ImEntity(
                contactId = 0,
                type = ContactFieldOptions.normalizeType(protocol).ifBlank { protocol },
                value = handle,
                vcardPropertyGroup = syntax.propertyGroup,
                vcardTypeParam = extraTypes.joinToString(",").takeIf { it.isNotBlank() },
                vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                vcardAbLabel = syntax.abLabel,
                vcardRawValue = syntax.rawValue,
            )
        }

        val socialProfiles = parseSocialProfiles(vcard, abLabelsByGroup)
        val cloudIds = parseCloudIds(vcard, abLabelsByGroup)
        val languages = vcard.languages.mapNotNull { lang ->
            val value = lang.value?.trim().orEmpty()
            if (value.isBlank()) return@mapNotNull null
            val syntax = syntaxFromVCardProperty(lang, abLabelsByGroup)
            LanguageEntity(
                contactId = 0,
                value = value,
                vcardPropertyGroup = syntax.propertyGroup,
                vcardTypeParam = lang.parameters.types.joinToString(",").takeIf { it.isNotBlank() },
                vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                vcardAbLabel = syntax.abLabel,
                vcardRawValue = syntax.rawValue,
            )
        }

        val relatedNames = vcard.relations.map { rel ->
            val (pickerType, extraType) = parseRelatedTypes(rel)
            val syntax = syntaxFromVCardProperty(rel, abLabelsByGroup)
            RelatedEntity(
                contactId = 0,
                type = pickerType,
                value = rel.text.orEmpty().ifBlank { rel.uri.orEmpty() },
                vcardPropertyGroup = syntax.propertyGroup,
                vcardTypeParam = extraType,
                vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                vcardAbLabel = syntax.abLabel,
                vcardRawValue = syntax.rawValue,
            )
        }

        val customFields = parseCustomFields(vcard)
        val unknownLinesJson = VCardUnknownLines.encode(VCardUnknownLineMapper.collectFromVCard(vcard))

        val photoBytes = vcard.photos.firstOrNull()?.data

        return ParsedContact(
            contact = contact.copy(vcardUnknownLinesJson = unknownLinesJson),
            phones = phones,
            emails = emails,
            addresses = addresses,
            urls = urls,
            imAccounts = imAccounts,
            socialProfiles = socialProfiles,
            cloudIds = cloudIds,
            languages = languages,
            relatedNames = relatedNames,
            customFields = customFields,
            photoBytes = photoBytes,
        )
    }

    fun inputFromContact(contact: ContactWithDetails): ContactInput = ContactInput(
        prefix = contact.contact.prefix,
        givenName = contact.contact.givenName,
        middleName = contact.contact.middleName,
        familyName = contact.contact.familyName,
        suffix = contact.contact.suffix,
        nickname = contact.contact.nickname,
        phoneticGiven = contact.contact.phoneticGiven,
        phoneticFamily = contact.contact.phoneticFamily,
        organization = contact.contact.organization,
        title = contact.contact.title,
        role = contact.contact.role,
        gender = contact.contact.gender,
        birthday = contact.contact.birthday,
        birthplace = contact.contact.birthplace,
        anniversary = contact.contact.anniversary,
        deathDate = contact.contact.deathDate,
        deathPlace = contact.contact.deathPlace,
        categories = contact.contact.categories,
        notes = contact.contact.notes,
        notesMarkdown = contact.contact.notesMarkdown,
        geo = contact.contact.geo,
        timeZone = contact.contact.timeZone,
        managersName = contact.contact.managersName,
        managersUid = contact.contact.managersUid,
        relationshipToYou = contact.contact.relationshipToYou,
        phones = contact.phones.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.phone()) },
        emails = contact.emails.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.email()) },
        addresses = contact.addresses.map { it.toInput() }.ifEmpty { listOf(AddressInput()) }.let { list ->
            if (contact.contact.geo.isBlank() || list.isEmpty()) list
            else list.toMutableList().also { addresses ->
                if (addresses[0].geo.isBlank()) {
                    addresses[0] = addresses[0].copy(geo = contact.contact.geo)
                }
            }
        },
        urls = contact.urls.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.url()) },
        imAccounts = contact.imAccounts.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.im()) },
        socialProfiles = contact.socialProfiles.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.social()) },
        cloudIds = contact.cloudIds.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.cloud()) },
        languages = contact.languages.map { it.toFieldInput() }.ifEmpty {
            listOf(VCardTypedFieldInput(type = "", value = "", propertyName = "LANG"))
        },
        relatedNames = contact.relatedNames.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.related()) },
        customFields = contact.customFields.map { it.toInput() },
        scalarVcardExtras = ContactScalarVcardExtras.decode(contact.contact.vcardScalarExtrasJson),
        vcardUnknownLinesJson = contact.contact.vcardUnknownLinesJson,
    )

    fun inputFromParsed(parsed: ParsedContact): ContactInput {
        val c = parsed.contact
        return ContactInput(
            prefix = c.prefix,
            givenName = c.givenName,
            middleName = c.middleName,
            familyName = c.familyName,
            suffix = c.suffix,
            nickname = c.nickname,
            phoneticGiven = c.phoneticGiven,
            phoneticFamily = c.phoneticFamily,
            organization = c.organization,
            title = c.title,
            role = c.role,
            gender = c.gender,
            birthday = c.birthday,
            birthplace = c.birthplace,
            anniversary = c.anniversary,
            deathDate = c.deathDate,
            deathPlace = c.deathPlace,
            categories = c.categories,
            notes = c.notes,
            geo = c.geo,
            timeZone = c.timeZone,
            managersName = c.managersName,
            managersUid = c.managersUid,
            relationshipToYou = c.relationshipToYou,
            phones = parsed.phones.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.phone()) },
            emails = parsed.emails.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.email()) },
            addresses = parsed.addresses.map { it.toInput() }.ifEmpty { listOf(AddressInput()) }.let { list ->
                if (c.geo.isBlank() || list.isEmpty()) list
                else list.toMutableList().also { addresses ->
                    if (addresses[0].geo.isBlank()) {
                        addresses[0] = addresses[0].copy(geo = c.geo)
                    }
                }
            },
            urls = parsed.urls.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.url()) },
            imAccounts = parsed.imAccounts.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.im()) },
            socialProfiles = parsed.socialProfiles.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.social()) },
            cloudIds = parsed.cloudIds.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.cloud()) },
            languages = parsed.languages.map { it.toFieldInput() }.ifEmpty {
                listOf(VCardTypedFieldInput(type = "", value = "", propertyName = "LANG"))
            },
            relatedNames = parsed.relatedNames.map { it.toFieldInput() }.ifEmpty { listOf(VCardTypedFieldInput.related()) },
            customFields = parsed.customFields.map { it.toInput() },
            scalarVcardExtras = ContactScalarVcardExtras.decode(c.vcardScalarExtrasJson),
            vcardUnknownLinesJson = c.vcardUnknownLinesJson,
            photoBytes = parsed.photoBytes,
        )
    }

    private fun parseCustomFields(vcard: VCard): List<CustomFieldEntity> {
        val abLabelsByGroup = vcard.extendedProperties
            .filter { it.propertyName.equals(X_ABLABEL_PROP, ignoreCase = true) }
            .mapNotNull { property ->
                val group = property.group?.trim().orEmpty()
                if (group.isBlank()) return@mapNotNull null
                group to property.value.orEmpty()
            }
            .toMap()

        return vcard.extendedProperties
            .filter { isCustomFieldExtendedProperty(it) }
            .mapNotNull { propertyToCustomField(it, abLabelsByGroup) }
    }

    internal fun isImportedAsCustomField(property: RawProperty): Boolean {
        val name = property.propertyName.orEmpty()
        if (name.equals(CUSTOM_FIELD_X_PROP, ignoreCase = true)) return true
        if (!name.startsWith("X-", ignoreCase = true)) return false
        if (NON_CUSTOM_EXTENDED_X_PROPERTIES.any { it.equals(name, ignoreCase = true) }) return false
        val hasLabel = property.getParameter("LABEL")?.let(::unescapeVCardParameter)?.trim()?.isNotBlank() == true ||
            property.parameters.label?.trim()?.isNotBlank() == true
        val hasGroup = property.group?.trim()?.isNotBlank() == true
        val hasType = extractTypeParameter(property)?.isNotBlank() == true
        val hasExtra = extractExtraParameters(property.parameters).isNotEmpty()
        return hasLabel || hasGroup || hasType || hasExtra
    }

    private fun isCustomFieldExtendedProperty(property: RawProperty): Boolean =
        isImportedAsCustomField(property)

    private fun propertyToCustomField(
        property: RawProperty,
        abLabelsByGroup: Map<String, String>,
    ): CustomFieldEntity? {
        val value = property.value.orEmpty()
        var label = property.getParameter("LABEL")?.let(::unescapeVCardParameter)?.trim().orEmpty()
        if (label.isBlank()) {
            label = property.parameters.label?.trim().orEmpty()
        }
        val group = property.group?.trim().orEmpty()
        if (label.isBlank() && group.isNotBlank()) {
            label = abLabelsByGroup[group].orEmpty().trim()
        }
        val typeParam = extractTypeParameter(property)
        val extraParameters = extractExtraParameters(property.parameters)
        val input = CustomFieldInput(
            label = label,
            value = value,
            propertyName = property.propertyName.orEmpty(),
            propertyGroup = group,
            typeParam = typeParam.orEmpty(),
            extraParameters = extraParameters,
        )
        if (!input.hasPersistableContent()) return null
        return input.toEntity(contactId = 0)
    }

    private fun extractTypeParameter(property: RawProperty): String? {
        val fromTypeList = property.parameters.types.firstOrNull()?.trim().orEmpty()
        if (fromTypeList.isNotBlank()) return fromTypeList
        return property.getParameter("TYPE")?.trim()?.takeIf { it.isNotBlank() }
    }

    internal fun allVCardParameters(property: VCardProperty): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        property.parameters.types.forEach { type ->
            if (type.isNotBlank()) result.add("TYPE" to type)
        }
        for (entry in property.parameters) {
            val key = entry.key
            if (key.equals("TYPE", ignoreCase = true)) continue
            if (!parameterAllowedOnVCard4(key)) continue
            for (paramValue in entry.value) {
                result.add(key.uppercase() to paramValue)
            }
        }
        return result
    }

    internal fun extractExtraParameters(parameters: VCardParameters): List<Pair<String, String>> {
        val reserved = setOf("LABEL", "TYPE")
        val result = mutableListOf<Pair<String, String>>()
        for (entry in parameters) {
            val key = entry.key
            if (reserved.any { it.equals(key, ignoreCase = true) }) continue
            if (!parameterAllowedOnVCard4(key)) continue
            for (paramValue in entry.value) {
                result.add(key.uppercase() to paramValue)
            }
        }
        return result
    }

    private fun writeCustomFieldToVCard(vcard: VCard, field: CustomFieldEntity) {
        val input = field.toInput().sanitizePropertyName()
        if (!input.hasPersistableContent()) return

        val propertyName = input.normalizedPropertyName()
        val property = vcard.addExtendedProperty(propertyName, input.value)
        input.propertyGroup.trim().takeIf { it.isNotBlank() }?.let { property.group = it }

        if (input.label.isNotBlank()) {
            property.setParameter("LABEL", escapeVCardParameter(input.label))
        }
        if (input.typeParam.isNotBlank()) {
            property.addParameter("TYPE", input.typeParam)
        }
        input.extraParameters.forEach { (name, value) ->
            if (name.isBlank() || !parameterAllowedOnVCard4(name)) return@forEach
            property.addParameter(name, escapeVCardParameter(value))
        }

        val group = input.propertyGroup.trim()
        if (group.isNotBlank() && input.label.isNotBlank()) {
            val abLabel = vcard.addExtendedProperty(X_ABLABEL_PROP, input.label)
            abLabel.group = group
        }
    }

    /**
     * vCard 4.0 rejects these parameters. Nextcloud's v3 download still sends them
     * (PHOTO ENCODING=b, QUOTED-PRINTABLE notes), and copying them onto the v4 PUT
     * makes sabre return HTTP 415.
     */
    internal fun parameterAllowedOnVCard4(name: String): Boolean {
        val upper = name.trim().uppercase()
        return upper != "ENCODING" && upper != "CHARSET"
    }

    /** Escapes vCard parameter values so `;`, `,`, `\`, and newlines cannot break the line. */
    internal fun escapeVCardParameter(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    internal fun unescapeVCardParameter(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    'n', 'N' -> append('\n')
                    ';' -> append(';')
                    ',' -> append(',')
                    '\\' -> append('\\')
                    else -> {
                        append('\\')
                        append(value[index + 1])
                    }
                }
                index += 2
            } else {
                append(ch)
                index++
            }
        }
    }

    private fun detectImageType(bytes: ByteArray): ImageType = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() -> ImageType.PNG
        else -> ImageType.JPEG
    }

    private fun mapPhoneTypes(type: String): List<TelephoneType> = when (ContactFieldOptions.normalizeType(type)) {
        "mobile" -> listOf(TelephoneType.CELL)
        "home" -> listOf(TelephoneType.HOME, TelephoneType.VOICE)
        "work" -> listOf(TelephoneType.WORK, TelephoneType.VOICE)
        "work_mobile" -> listOf(TelephoneType.WORK, TelephoneType.CELL)
        "home_mobile" -> listOf(TelephoneType.HOME, TelephoneType.CELL)
        "fax" -> listOf(TelephoneType.FAX)
        "fax_home" -> listOf(TelephoneType.HOME, TelephoneType.FAX)
        "fax_work" -> listOf(TelephoneType.WORK, TelephoneType.FAX)
        "pager" -> listOf(TelephoneType.PAGER)
        "work_pager" -> listOf(TelephoneType.WORK, TelephoneType.PAGER)
        "voice" -> listOf(TelephoneType.VOICE)
        "car" -> listOf(TelephoneType.CAR)
        else -> emptyList()
    }

    private fun mapEmailType(type: String): EmailType? = when (ContactFieldOptions.normalizeType(type)) {
        "home" -> EmailType.HOME
        "work" -> EmailType.WORK
        else -> null
    }

    private fun mapAddressType(type: String): AddressType? = when (ContactFieldOptions.normalizeType(type)) {
        "home" -> AddressType.HOME
        "work" -> AddressType.WORK
        else -> null
    }

    private fun mapRelatedType(type: String): RelatedType? = when (ContactFieldOptions.normalizeType(type)) {
        "spouse" -> RelatedType.SPOUSE
        "child" -> RelatedType.CHILD
        "parent" -> RelatedType.PARENT
        "friend" -> RelatedType.FRIEND
        "colleague", "coworker" -> RelatedType.CO_WORKER
        "agent" -> RelatedType.AGENT
        "emergency" -> RelatedType.EMERGENCY
        "relative" -> RelatedType.KIN
        "contact" -> RelatedType.CONTACT
        else -> null
    }

    private fun parseSocialProfiles(
        vcard: VCard,
        abLabelsByGroup: Map<String, String>,
    ): List<SocialProfileEntity> {
        return vcard.extendedProperties
            .filter { it.propertyName.equals("X-SOCIALPROFILE", ignoreCase = true) }
            .mapNotNull { property ->
                val value = property.value?.trim().orEmpty()
                if (value.isBlank()) return@mapNotNull null
                val type = property.parameters.types.firstOrNull()?.lowercase()
                    ?: property.parameters.type?.lowercase()
                    ?: inferSocialType(value)
                val syntax = syntaxFromVCardProperty(property, abLabelsByGroup)
                SocialProfileEntity(
                    contactId = 0,
                    type = type,
                    value = value,
                    vcardPropertyGroup = syntax.propertyGroup,
                    vcardTypeParam = syntax.typeParam.ifBlank { null },
                    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                    vcardAbLabel = syntax.abLabel,
                    vcardRawValue = syntax.rawValue,
                )
            }
    }

    private fun parseCloudIds(
        vcard: VCard,
        abLabelsByGroup: Map<String, String>,
    ): List<CloudIdEntity> {
        return vcard.extendedProperties
            .filter { it.propertyName.equals("CLOUD", ignoreCase = true) }
            .mapNotNull { property ->
                val value = property.value?.trim().orEmpty()
                if (value.isBlank()) return@mapNotNull null
                val type = property.parameters.types.firstOrNull()?.lowercase()
                    ?: property.parameters.type?.lowercase()
                    ?: "home"
                val syntax = syntaxFromVCardProperty(property, abLabelsByGroup)
                CloudIdEntity(
                    contactId = 0,
                    type = ContactFieldOptions.normalizeType(type),
                    value = value,
                    vcardPropertyGroup = syntax.propertyGroup,
                    vcardTypeParam = syntax.typeParam.ifBlank { null },
                    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
                    vcardAbLabel = syntax.abLabel,
                    vcardRawValue = syntax.rawValue,
                )
            }
    }

    private fun parseRelatedTypes(rel: Related): Pair<String, String?> {
        val values = buildList {
            rel.types.forEach { add(it.value.uppercase()) }
            rel.parameters.types.forEach { if (it.isNotBlank()) add(it.uppercase()) }
        }.distinct()
        val normalized = ContactFieldOptions.normalizeType(values.firstOrNull().orEmpty())
        if (ContactFieldOptions.related.any { it.id == normalized }) {
            val mapped = mapRelatedType(normalized)?.value?.uppercase()?.let { setOf(it) } ?: emptySet()
            val extra = values.filterNot { it in mapped }
            return normalized to extra.joinToString(",").takeIf { it.isNotBlank() }
        }
        return "other" to values.joinToString(",").takeIf { it.isNotBlank() }
    }

    private fun inferSocialType(value: String): String {
        val lower = value.lowercase()
        return when {
            "facebook" in lower -> "facebook"
            "github" in lower -> "github"
            "instagram" in lower -> "instagram"
            "linkedin" in lower -> "linkedin"
            "xing" in lower -> "xing"
            "pinterest" in lower -> "pinterest"
            "tumblr" in lower -> "tumblr"
            "twitter" in lower || "x.com" in lower -> "twitter"
            "youtube" in lower -> "youtube"
            "mastodon" in lower -> "mastodon"
            "diaspora" in lower -> "diaspora"
            "whatsapp" in lower || "wa.me" in lower -> "whatsapp"
            "telegram" in lower || "t.me" in lower -> "telegram"
            else -> "other"
        }
    }

    fun formatGeoValue(latitude: Double, longitude: Double): String =
        "$latitude;$longitude"

    private fun parseGeo(value: String): Pair<Double, Double>? {
        val parts = value.split(';', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return lat to lon
    }

    private fun parseIndexedAddressGeos(vcard: VCard): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        vcard.extendedProperties.forEach { prop ->
            if (!prop.propertyName.equals(ADR_GEO_X_PROP, ignoreCase = true)) return@forEach
            val index = prop.getParameter("INDEX")?.toIntOrNull() ?: return@forEach
            val value = prop.value?.trim().orEmpty()
            if (value.isNotBlank()) result[index] = value
        }
        return result
    }

    private fun resolveAddressGeo(
        index: Int,
        indexedGeos: Map<Int, String>,
        standardGeos: List<String>,
        legacyContactGeo: String,
    ): String {
        indexedGeos[index]?.let { return it }
        standardGeos.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { return it }
        if (index == 0 && legacyContactGeo.isNotBlank() && standardGeos.size <= 1) {
            return legacyContactGeo
        }
        return ""
    }

    /** ez-vcard Impp parses handles as URIs; spaces/illegal chars must be cleaned first. */
    private fun sanitizeImppHandle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.contains("://")) return trimmed
        val digits = trimmed.filter { it.isDigit() }
        val phoneFormatted = trimmed.all {
            it.isDigit() || it == '+' || it.isWhitespace() || it == '-' || it == '(' || it == ')'
        }
        if (digits.length >= 7 && phoneFormatted) {
            return if (trimmed.trimStart().startsWith("+")) "+$digits" else digits
        }
        return trimmed.replace(" ", "")
    }

    private fun buildImpp(protocol: String, handle: String): Impp? {
        return when {
            handle.contains("://") ->
                runCatching { Impp(handle) }.getOrElse {
                    runCatching { Impp(protocol, handle.substringAfter("://", handle)) }.getOrNull()
                }
            handle.contains(':') &&
                handle.substringBefore(':').equals(protocol, ignoreCase = true) ->
                runCatching { Impp(handle) }.getOrElse {
                    runCatching { Impp(protocol, handle.substringAfter(':')) }.getOrNull()
                }
            else ->
                runCatching { Impp(handle) }.getOrElse {
                    runCatching { Impp(protocol, handle) }.getOrNull()
                }
        }
    }

    /**
     * Parse app date strings into a Temporal when possible so ez-vcard emits a real BDAY/etc.
     * Returns (temporal, originalText) — temporal preferred for write.
     */
    private fun parseDateProperty(raw: String): Pair<Temporal?, String>? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        parseFlexibleDate(value)?.let { return it to value }
        return null to value
    }

    private fun parseFlexibleDate(value: String): Temporal? {
        parseIsoOrBasicLocalDate(value)?.let { return it }
        val compact = value.filter { it.isDigit() }
        return runCatching {
            when {
                value.matches(Regex("""\d{4}-\d{2}""")) ->
                    YearMonth.parse(value)
                value.matches(Regex("""\d{6}""")) && compact.length == 6 ->
                    YearMonth.of(compact.substring(0, 4).toInt(), compact.substring(4, 6).toInt())
                value.matches(Regex("""\d{4}""")) ->
                    Year.parse(value)
                else -> null
            }
        }.getOrNull()
    }

    private fun formatDateProperty(property: DateOrTimeProperty?): String {
        if (property == null) return ""
        property.text?.trim()?.takeIf { it.isNotEmpty() }?.let { return normalizeDateString(it) }
        property.date?.let { temporal ->
            return when (temporal) {
                is LocalDate -> temporal.toString()
                is YearMonth -> temporal.toString()
                is Year -> temporal.toString()
                else -> temporal.toString().take(10)
            }
        }
        property.partialDate?.let { partial ->
            return formatPartialDate(partial)
        }
        return ""
    }

    private fun formatPartialDate(partial: PartialDate): String {
        val year = partial.year
        val month = partial.month
        val date = partial.date
        return when {
            year != null && month != null && date != null ->
                "%04d-%02d-%02d".format(year, month, date)
            year != null && month != null ->
                "%04d-%02d".format(year, month)
            year != null ->
                "%04d".format(year)
            else -> partial.toISO8601(true).orEmpty()
        }
    }

    private fun normalizeDateString(value: String): String {
        parseFlexibleDate(value)?.let { temporal ->
            return when (temporal) {
                is LocalDate -> temporal.toString()
                is YearMonth -> temporal.toString()
                is Year -> temporal.toString()
                else -> value
            }
        }
        return value
    }

    private fun abLabelsByGroup(vcard: VCard): Map<String, String> =
        vcard.extendedProperties
            .filter { it.propertyName.equals(X_ABLABEL_PROP, ignoreCase = true) }
            .mapNotNull { property ->
                val group = property.group?.trim().orEmpty()
                if (group.isBlank()) return@mapNotNull null
                group to property.value.orEmpty()
            }
            .toMap()

    private fun writeAbLabelIfNeeded(
        vcard: VCard,
        group: String,
        label: String,
        writtenGroups: MutableSet<String>,
    ) {
        val trimmedGroup = group.trim()
        val trimmedLabel = label.trim()
        if (trimmedGroup.isBlank() || trimmedLabel.isBlank() || trimmedGroup in writtenGroups) return
        val abLabel = vcard.addExtendedProperty(X_ABLABEL_PROP, trimmedLabel)
        abLabel.group = trimmedGroup
        writtenGroups.add(trimmedGroup)
    }

    private fun applyStoredVcardSyntax(
        property: VCardProperty,
        group: String,
        typeParam: String?,
        extraParametersJson: String,
    ) {
        group.trim().takeIf { it.isNotBlank() }?.let { property.group = it }
        CustomFieldExtras.decode(extraParametersJson).forEach { (name, value) ->
            if (name.isBlank() || !parameterAllowedOnVCard4(name)) return@forEach
            property.addParameter(name, escapeVCardParameter(value))
        }
    }

    private fun syntaxFromVCardProperty(
        property: VCardProperty,
        abLabelsByGroup: Map<String, String>,
    ): VCardSyntaxExtras {
        val group = property.group?.trim().orEmpty()
        return VCardSyntaxExtras(
            propertyGroup = group,
            extraParameters = extractExtraParameters(property.parameters),
            abLabel = abLabelsByGroup[group].orEmpty().trim(),
        )
    }

    private fun parseScalarVcardExtras(vcard: VCard): Map<String, VCardSyntaxExtras> = buildMap {
        vcard.formattedName?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            put("FN", scalarSyntaxFrom(vcard.formattedName, value))
        }
        vcard.structuredName?.let { structured ->
            put("N", scalarSyntaxFrom(structured, structuredNameToRaw(structured)))
        }
        vcard.nickname?.values?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            vcard.nickname?.let { put("NICKNAME", scalarSyntaxFrom(it, value)) }
        }
        vcard.getExtendedProperty("X-PHONETIC-FIRST-NAME")?.let { property ->
            val value = property.value.orEmpty()
            if (value.isNotBlank()) put("X-PHONETIC-FIRST-NAME", scalarSyntaxFrom(property, value))
        }
        vcard.getExtendedProperty("X-PHONETIC-LAST-NAME")?.let { property ->
            val value = property.value.orEmpty()
            if (value.isNotBlank()) put("X-PHONETIC-LAST-NAME", scalarSyntaxFrom(property, value))
        }
        vcard.organization?.let { org ->
            val value = org.values.joinToString(";")
            if (value.isNotBlank()) put("ORG", scalarSyntaxFrom(org, value))
        }
        vcard.titles.firstOrNull()?.let { title ->
            val value = title.value.orEmpty()
            if (value.isNotBlank()) put("TITLE", scalarSyntaxFrom(title, value))
        }
        vcard.roles.firstOrNull()?.let { role ->
            val value = role.value.orEmpty()
            if (value.isNotBlank()) put("ROLE", scalarSyntaxFrom(role, value))
        }
        vcard.gender?.let { gender ->
            val value = gender.gender.orEmpty()
            if (value.isNotBlank()) put("GENDER", scalarSyntaxFrom(gender, value))
        }
        vcard.birthday?.let { birthday ->
            val value = formatDateProperty(birthday)
            if (value.isNotBlank()) put("BDAY", scalarSyntaxFrom(birthday, value))
        }
        vcard.getExtendedProperty("BIRTHPLACE")?.let { property ->
            val value = property.value.orEmpty()
            if (value.isNotBlank()) put("BIRTHPLACE", scalarSyntaxFrom(property, value))
        }
        vcard.anniversary?.let { anniversary ->
            val value = formatDateProperty(anniversary)
            if (value.isNotBlank()) put("ANNIVERSARY", scalarSyntaxFrom(anniversary, value))
        }
        vcard.deathdate?.let { deathdate ->
            val value = formatDateProperty(deathdate)
            if (value.isNotBlank()) put("DEATHDATE", scalarSyntaxFrom(deathdate, value))
        }
        vcard.getExtendedProperty("DEATHPLACE")?.let { property ->
            val value = property.value.orEmpty()
            if (value.isNotBlank()) put("DEATHPLACE", scalarSyntaxFrom(property, value))
        }
        vcard.categories?.values?.takeIf { it.isNotEmpty() }?.let { values ->
            val property = vcard.categories!!
            put("CATEGORIES", scalarSyntaxFrom(property, values.joinToString(",")))
        }
        vcard.notes.firstOrNull()?.let { note ->
            val value = note.value.orEmpty()
            if (value.isNotBlank()) put("NOTE", scalarSyntaxFrom(note, value))
        }
        vcard.timezone?.let { tz ->
            val value = tz.text.orEmpty().ifBlank { tz.offset?.id.orEmpty() }
            if (value.isNotBlank()) put("TZ", scalarSyntaxFrom(tz, value))
        }
        vcard.getExtendedProperty("X-MANAGERSNAME")?.let { property ->
            val value = property.value.orEmpty()
            if (value.isNotBlank() || property.getParameter("UID")?.isNotBlank() == true) {
                put("X-MANAGERSNAME", scalarSyntaxFrom(property, value))
            }
        }
        vcard.getExtendedProperty("RELATIONSHIP")?.let { property ->
            val value = property.value.orEmpty()
            if (value.isNotBlank()) put("RELATIONSHIP", scalarSyntaxFrom(property, value))
        }
        vcard.photos.firstOrNull()?.let { photo ->
            put("PHOTO", VCardPhotoHelper.photoSyntaxFromParsed(photo))
        }
    }

    internal fun applyPhotoSyntaxExtras(photo: Photo, extras: VCardSyntaxExtras?) {
        applyVcardSyntaxExtras(photo, extras)
        val type = extras?.typeParam?.split(',')?.firstOrNull()?.trim().orEmpty()
        if (type.isNotBlank() && photo.contentType == null) {
            runCatching { ImageType.get(type, null, null) }.getOrNull()?.let { photo.contentType = it }
        }
    }

    internal fun scalarSyntaxFromPhoto(photo: Photo): VCardSyntaxExtras {
        val url = photo.url?.trim().orEmpty()
        val rawValue = url.ifBlank { "" }
        val typeParam = photo.parameters.types.filter { it.isNotBlank() }.joinToString(",")
            .ifBlank { photo.contentType?.mediaType.orEmpty() }
        return VCardSyntaxExtras(
            propertyGroup = photo.group?.trim().orEmpty(),
            typeParam = typeParam,
            extraParameters = extractExtraParameters(photo.parameters),
            rawValue = rawValue,
        )
    }

    private fun writePhotoProperty(
        vcard: VCard,
        extras: VCardSyntaxExtras?,
        photoBytes: ByteArray?,
    ) {
        val raw = extras?.rawValue?.trim().orEmpty()
        val photo = when {
            raw.isNotBlank() -> VCardPhotoHelper.buildPhotoFromRaw(raw, extras)
            photoBytes != null -> VCardPhotoHelper.buildPhoto(photoBytes, extras)
            extras?.hasPersistableMetadata() == true ->
                VCardPhotoHelper.buildPhotoFromRaw("", extras)
            else -> null
        }
        photo?.let { vcard.photos.add(it) }
    }

    private fun writeScalarContactProperties(
        vcard: VCard,
        c: ContactEntity,
        scalar: Map<String, VCardSyntaxExtras>,
    ) {
        fun extras(key: String): VCardSyntaxExtras? = scalar[key]

        // The form edits the contact columns. A raw value imported earlier must not
        // put the old property back after the field was cleared or changed.
        fun raw(key: String, fallback: String): String =
            scalarValueForUpload(key, scalar[key]?.rawValue.orEmpty(), fallback)

        val fromContact = structuredNameFromContact(c)
        val fromContactRaw = structuredNameToRaw(fromContact)
        val nRaw = extras("N")?.rawValue?.trim().orEmpty()
        val nameEditedInForm = nRaw.isNotEmpty() && nRaw != fromContactRaw
        val nameFromParts = listOf(c.prefix, c.givenName, c.middleName, c.familyName, c.suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val fn = if (nameEditedInForm && nameFromParts.isNotBlank()) {
            nameFromParts
        } else {
            raw("FN", c.displayName.ifBlank { nameFromParts })
        }
        if (fn.isNotBlank()) {
            vcard.formattedName = FormattedName(fn).also { applyVcardSyntaxExtras(it, extras("FN")) }
        }

        vcard.structuredName = (
            if (nRaw.isNotEmpty() && !nameEditedInForm) structuredNameFromRaw(nRaw) else fromContact
            ).also { applyVcardSyntaxExtras(it, extras("N")) }

        val nickname = raw("NICKNAME", c.nickname)
        if (nickname.isNotBlank()) {
            vcard.setNickname(nickname)
            vcard.nickname?.let { applyVcardSyntaxExtras(it, extras("NICKNAME")) }
        }

        writeExtendedScalar(vcard, "X-PHONETIC-FIRST-NAME", raw("X-PHONETIC-FIRST-NAME", c.phoneticGiven), extras("X-PHONETIC-FIRST-NAME"))
        writeExtendedScalar(vcard, "X-PHONETIC-LAST-NAME", raw("X-PHONETIC-LAST-NAME", c.phoneticFamily), extras("X-PHONETIC-LAST-NAME"))

        val title = raw("TITLE", c.title)
        if (title.isNotBlank()) {
            vcard.titles.add(Title(title).also { applyVcardSyntaxExtras(it, extras("TITLE")) })
        }
        val role = raw("ROLE", c.role)
        if (role.isNotBlank()) {
            vcard.roles.add(Role(role).also { applyVcardSyntaxExtras(it, extras("ROLE")) })
        }

        val organization = raw("ORG", c.organization)
        if (organization.isNotBlank()) {
            vcard.organization = Organization().apply {
                organization.split(';').map { it.trim() }.filter { it.isNotBlank() }.forEach { values.add(it) }
            }.also { applyVcardSyntaxExtras(it, extras("ORG")) }
        }

        val genderRaw = raw("GENDER", c.gender)
        if (genderRaw.isNotBlank()) {
            vcard.gender = genderFromRaw(genderRaw).also { applyVcardSyntaxExtras(it, extras("GENDER")) }
        }

        writeDateScalar(vcard, raw("BDAY", c.birthday), extras("BDAY")) { temporal, text ->
            vcard.birthday = if (temporal != null) Birthday(temporal) else Birthday(text)
            vcard.birthday
        }
        writeExtendedScalar(vcard, "BIRTHPLACE", raw("BIRTHPLACE", c.birthplace), extras("BIRTHPLACE"))
        writeDateScalar(vcard, raw("ANNIVERSARY", c.anniversary), extras("ANNIVERSARY")) { temporal, text ->
            vcard.anniversary = if (temporal != null) Anniversary(temporal) else Anniversary(text)
            vcard.anniversary
        }
        writeDateScalar(vcard, raw("DEATHDATE", c.deathDate), extras("DEATHDATE")) { temporal, text ->
            vcard.deathdate = if (temporal != null) Deathdate(temporal) else Deathdate(text)
            vcard.deathdate
        }
        writeExtendedScalar(vcard, "DEATHPLACE", raw("DEATHPLACE", c.deathPlace), extras("DEATHPLACE"))

        val categoriesRaw = raw("CATEGORIES", c.categories)
        if (categoriesRaw.isNotBlank()) {
            vcard.categories = Categories().apply {
                values.addAll(categoriesRaw.split(',').map { it.trim() }.filter { it.isNotBlank() })
            }.also { applyVcardSyntaxExtras(it, extras("CATEGORIES")) }
        }

        val notes = raw("NOTE", c.notes)
        if (notes.isNotBlank()) {
            vcard.notes.add(Note(notes).also { applyVcardSyntaxExtras(it, extras("NOTE")) })
        }

        val timeZone = raw("TZ", c.timeZone)
        if (timeZone.isNotBlank()) {
            vcard.timezone = Timezone(timeZone).also { applyVcardSyntaxExtras(it, extras("TZ")) }
        }

        val managersName = raw("X-MANAGERSNAME", c.managersName)
        val managersExtras = extras("X-MANAGERSNAME")
        val managersUid = managersExtras?.extraParameters
            ?.firstOrNull { it.first.equals("UID", ignoreCase = true) }
            ?.second
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: c.managersUid
        if (managersName.isNotBlank() || managersUid.isNotBlank()) {
            val prop = vcard.setExtendedProperty("X-MANAGERSNAME", managersName)
            applyVcardSyntaxExtras(prop, managersExtras)
            if (managersUid.isNotBlank()) {
                prop.setParameter("UID", managersUid)
            }
        }

        val relationship = raw("RELATIONSHIP", c.relationshipToYou)
        if (relationship.isNotBlank()) {
            val value = relationship.lowercase()
            vcard.setExtendedProperty("RELATIONSHIP", value).also { applyVcardSyntaxExtras(it, extras("RELATIONSHIP")) }
            vcard.setExtendedProperty("X-RELATIONSHIP", value.uppercase())
        }
    }

    private inline fun writeDateScalar(
        vcard: VCard,
        rawValue: String,
        extras: VCardSyntaxExtras?,
        assign: (Temporal?, String) -> DateOrTimeProperty?,
    ) {
        if (rawValue.isBlank()) return
        parseDateProperty(rawValue)?.let { (temporal, text) ->
            assign(temporal, text)?.let { property -> applyVcardSyntaxExtras(property, extras) }
        }
    }

    private fun writeExtendedScalar(
        vcard: VCard,
        propertyName: String,
        value: String,
        extras: VCardSyntaxExtras?,
    ) {
        if (value.isBlank()) return
        val property = vcard.setExtendedProperty(propertyName, value)
        applyVcardSyntaxExtras(property, extras)
    }

    /**
     * Form edits live on the contact columns. [storedRaw] is the value captured
     * when the card was downloaded. A blank column means the field was removed.
     * A different column means the form replaced it. ORG and groups keep the
     * stored raw when it is only a formatting difference.
     */
    internal fun scalarValueForUpload(key: String, storedRaw: String, fallback: String): String {
        val stored = storedRaw.trim()
        val edited = fallback.trim()
        if (edited.isEmpty()) return ""
        if (stored.isEmpty() || stored == edited) return stored.ifEmpty { edited }
        if (key == "ORG") {
            val first = stored.split(';').firstOrNull()?.trim().orEmpty()
            if (first == edited) return stored
        }
        if (key == "CATEGORIES" && sameList(stored, edited)) return stored
        return edited
    }

    private fun sameList(stored: String, edited: String): Boolean {
        fun parts(value: String) = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return parts(stored) == parts(edited)
    }

    private fun addressHasComponents(address: Address): Boolean =
        listOf(
            address.poBox,
            address.extendedAddress,
            address.streetAddress,
            address.locality,
            address.region,
            address.postalCode,
            address.country,
        ).any { !it.isNullOrBlank() }

    private fun genderFromRaw(raw: String): Gender = when (raw.uppercase()) {
        "M" -> Gender.male()
        "F" -> Gender.female()
        "O" -> Gender.other()
        "N" -> Gender.none()
        "U" -> Gender.unknown()
        else -> Gender(raw)
    }

    internal fun alignContactDisplayName(
        prefix: String,
        givenName: String,
        middleName: String,
        familyName: String,
        suffix: String,
        nickname: String,
        organization: String,
        storedDisplayName: String,
        scalarExtras: Map<String, VCardSyntaxExtras>,
        previousNickname: String? = null,
        previousOrganization: String? = null,
    ): AlignedContactName {
        val partsName = listOf(prefix, givenName, middleName, familyName, suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val columnRaw = structuredNameRaw(familyName, givenName, middleName, prefix, suffix)
        val nEntry = scalarExtras.entries.firstOrNull { it.key.equals("N", ignoreCase = true) }
        val fnEntry = scalarExtras.entries.firstOrNull { it.key.equals("FN", ignoreCase = true) }
        val nRaw = nEntry?.value?.rawValue?.trim().orEmpty()
        val structuredChanged = nRaw.isNotEmpty() &&
            structuredNameComponents(nRaw) != structuredNameComponents(columnRaw)
        if (!structuredChanged) {
            return alignedNameWhenStructuredUnchanged(
                partsName = partsName,
                nickname = nickname,
                organization = organization,
                storedDisplayName = storedDisplayName,
                scalarExtras = scalarExtras,
                fnEntry = fnEntry,
                previousNickname = previousNickname,
                previousOrganization = previousOrganization,
            )
        }
        val rebuilt = partsName.ifBlank { fallbackDisplayName(nickname, organization) }
        val next = scalarExtras.toMutableMap()
        if (nEntry != null) {
            next[nEntry.key] = nEntry.value.copy(rawValue = columnRaw)
        }
        if (fnEntry != null && fnEntry.value.rawValue.trim() != rebuilt) {
            next[fnEntry.key] = fnEntry.value.copy(rawValue = rebuilt)
        }
        return AlignedContactName(rebuilt, next)
    }

    /**
     * Empty N uses nickname, then organization, then "Unnamed". A custom FN stays
     * when that fallback did not change. List repair has no previous nickname: a
     * different non-blank nickname replaces a stale FN. Phonetic names are not shown.
     */
    private fun alignedNameWhenStructuredUnchanged(
        partsName: String,
        nickname: String,
        organization: String,
        storedDisplayName: String,
        scalarExtras: Map<String, VCardSyntaxExtras>,
        fnEntry: Map.Entry<String, VCardSyntaxExtras>?,
        previousNickname: String?,
        previousOrganization: String?,
    ): AlignedContactName {
        val fnRaw = fnEntry?.value?.rawValue?.trim().orEmpty()
        if (partsName.isNotBlank()) {
            val display = fnRaw.ifBlank { storedDisplayName.trim() }.ifBlank { partsName }
            return AlignedContactName(display, scalarExtras)
        }
        val nick = nickname.trim()
        val org = organization.trim()
        val shown = fnRaw.ifBlank { storedDisplayName.trim() }
        val nicknameChanged = if (previousNickname != null) {
            nick != previousNickname.trim()
        } else {
            nick.isNotBlank() && nick != shown
        }
        val organizationChanged = previousOrganization != null &&
            org != previousOrganization.trim()
        val fallbackChanged = if (nick.isNotBlank()) {
            nicknameChanged
        } else {
            nicknameChanged || organizationChanged
        }
        if (!fallbackChanged) {
            val display = shown.ifBlank { fallbackDisplayName(nick, org) }
            return AlignedContactName(display, scalarExtras)
        }
        val rebuilt = fallbackDisplayName(nick, org)
        return AlignedContactName(rebuilt, withFormattedName(scalarExtras, fnEntry, rebuilt))
    }

    private fun fallbackDisplayName(nickname: String, organization: String): String =
        nickname.trim().ifBlank { organization.trim().ifBlank { "Unnamed" } }

    private fun withFormattedName(
        scalarExtras: Map<String, VCardSyntaxExtras>,
        fnEntry: Map.Entry<String, VCardSyntaxExtras>?,
        value: String,
    ): Map<String, VCardSyntaxExtras> {
        if (fnEntry == null) {
            return scalarExtras + ("FN" to VCardSyntaxExtras(rawValue = value))
        }
        if (fnEntry.value.rawValue.trim() == value) return scalarExtras
        return scalarExtras.toMutableMap().also { next ->
            next[fnEntry.key] = fnEntry.value.copy(rawValue = value)
        }
    }

    private fun structuredNameRaw(
        familyName: String,
        givenName: String,
        middleName: String,
        prefix: String,
        suffix: String,
    ): String = listOf(
        familyName,
        givenName,
        if (middleName.isNotBlank()) middleName else "",
        if (prefix.isNotBlank()) prefix else "",
        if (suffix.isNotBlank()) suffix else "",
    ).joinToString(";")

    private fun structuredNameComponents(raw: String): List<String> {
        val parts = raw.split(';')
        return List(5) { index -> parts.getOrElse(index) { "" }.trim() }
    }

    private fun structuredNameFromContact(c: ContactEntity): StructuredName = StructuredName().apply {
        if (c.prefix.isNotBlank()) prefixes.add(c.prefix)
        given = c.givenName
        if (c.middleName.isNotBlank()) additionalNames.add(c.middleName)
        family = c.familyName
        if (c.suffix.isNotBlank()) suffixes.add(c.suffix)
    }

    private fun structuredNameFromRaw(raw: String): StructuredName {
        val parts = raw.split(';')
        return StructuredName().apply {
            family = parts.getOrElse(0) { "" }
            given = parts.getOrElse(1) { "" }
            val additional = parts.getOrElse(2) { "" }
            if (additional.isNotBlank()) additionalNames.add(additional)
            val prefix = parts.getOrElse(3) { "" }
            if (prefix.isNotBlank()) prefixes.add(prefix)
            val suffix = parts.getOrElse(4) { "" }
            if (suffix.isNotBlank()) suffixes.add(suffix)
        }
    }

    private fun structuredNameToRaw(structured: StructuredName): String = listOf(
        structured.family.orEmpty(),
        structured.given.orEmpty(),
        structured.additionalNames.firstOrNull().orEmpty(),
        structured.prefixes.firstOrNull().orEmpty(),
        structured.suffixes.firstOrNull().orEmpty(),
    ).joinToString(";")

    private fun scalarSyntaxFrom(property: VCardProperty, rawValue: String): VCardSyntaxExtras {
        val typeParam = property.parameters.types.filter { it.isNotBlank() }.joinToString(",")
        return VCardSyntaxExtras(
            propertyGroup = property.group?.trim().orEmpty(),
            typeParam = typeParam,
            extraParameters = extractExtraParameters(property.parameters),
            rawValue = rawValue,
        )
    }

    private fun applyVcardSyntaxExtras(property: VCardProperty, extras: VCardSyntaxExtras?) {
        if (extras == null) return
        extras.propertyGroup.trim().takeIf { it.isNotBlank() }?.let { property.group = it }
        extras.typeParam.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { type ->
            property.addParameter("TYPE", type)
        }
        extras.extraParameters.forEach { (name, value) ->
            if (name.isBlank() || !parameterAllowedOnVCard4(name)) return@forEach
            property.addParameter(name, escapeVCardParameter(value))
        }
    }

    private fun addressFromEntity(address: AddressEntity): Address {
        if (address.vcardRawValue.isNotBlank()) {
            return addressFromRawAdr(address.vcardRawValue)
        }
        return Address().apply {
            poBox = address.poBox
            extendedAddress = address.extendedAddress
            streetAddress = address.street
            locality = address.city
            region = address.region
            postalCode = address.postalCode
            country = address.country
        }
    }

    private fun addressFromRawAdr(raw: String): Address {
        val parts = raw.split(';')
        return Address().apply {
            poBox = parts.getOrElse(0) { "" }
            extendedAddress = parts.getOrElse(1) { "" }
            streetAddress = parts.getOrElse(2) { "" }
            locality = parts.getOrElse(3) { "" }
            region = parts.getOrElse(4) { "" }
            postalCode = parts.getOrElse(5) { "" }
            country = parts.getOrElse(6) { "" }
        }
    }

    private fun applyPhoneTypes(tel: Telephone, phone: PhoneEntity) {
        val typeParamOnly = phone.vcardTypeParam?.trim().orEmpty()
        if (typeParamOnly.isNotBlank()) {
            typeParamOnly.split(',').forEach { token ->
                val type = token.trim()
                if (type.isNotBlank()) tel.addParameter("TYPE", type)
            }
            return
        }
        val normalized = ContactFieldOptions.normalizeType(phone.type)
        val knownPreset = ContactFieldOptions.phones.any { it.id == normalized }
        if (knownPreset) {
            mapPhoneTypes(phone.type).forEach { tel.types.add(it) }
        } else {
            phone.type.trim().takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
                val type = token.trim()
                if (type.isNotBlank()) tel.addParameter("TYPE", type)
            }
        }
    }

    private fun parseTelephoneTypes(tel: Telephone): Pair<String, String?> {
        val values = tel.types.map { it.value.uppercase() }.toMutableList()
        tel.parameters.types.forEach { param ->
            if (param.isNotBlank()) values.add(param.uppercase())
        }
        val distinct = values.distinct()
        val normalized = ContactFieldOptions.normalizeType(distinct.joinToString(","))
        if (ContactFieldOptions.phones.any { it.id == normalized }) {
            val used = mapPhoneTypes(normalized).map { it.value.uppercase() }.toSet()
            val extra = distinct.filterNot { it in used }
            return normalized to extra.joinToString(",").takeIf { it.isNotBlank() }
        }
        val rawType = tel.parameters.types.filter { it.isNotBlank() }.joinToString(",")
            .ifBlank { distinct.joinToString(",") }
        return rawType.ifBlank { "other" } to null
    }

    private fun applyEmailTypes(mail: Email, email: EmailEntity) {
        val normalized = ContactFieldOptions.normalizeType(email.type)
        val knownPreset = ContactFieldOptions.emails.any { it.id == normalized }
        if (knownPreset) {
            mapEmailType(email.type)?.let { mail.types.add(it) }
        } else {
            email.type.trim().takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
                val type = token.trim()
                if (type.isNotBlank()) mail.addParameter("TYPE", type)
            }
        }
        email.vcardTypeParam?.trim()?.takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
            val type = token.trim()
            if (type.isNotBlank()) mail.addParameter("TYPE", type)
        }
    }

    private fun parseEmailTypes(mail: Email): Pair<String, String?> {
        val values = buildList {
            mail.types.forEach { add(it.value.uppercase()) }
            mail.parameters.types.forEach { if (it.isNotBlank()) add(it.uppercase()) }
        }.distinct()
        val normalized = ContactFieldOptions.normalizeType(values.firstOrNull().orEmpty())
        if (ContactFieldOptions.emails.any { it.id == normalized }) {
            val used = mapEmailType(normalized)?.value?.uppercase()?.let { setOf(it) } ?: emptySet()
            val extra = values.filterNot { it in used }
            return normalized to extra.joinToString(",").takeIf { it.isNotBlank() }
        }
        val rawType = mail.parameters.types.filter { it.isNotBlank() }.joinToString(",")
            .ifBlank { values.joinToString(",") }
        return rawType.ifBlank { "home" } to null
    }

    private fun applyAddressTypes(adr: Address, address: AddressEntity) {
        val normalized = ContactFieldOptions.normalizeType(address.type)
        val knownPreset = ContactFieldOptions.addresses.any { it.id == normalized }
        if (knownPreset) {
            mapAddressType(address.type)?.let { adr.types.add(it) }
        } else {
            address.type.trim().takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
                val type = token.trim()
                if (type.isNotBlank()) adr.addParameter("TYPE", type)
            }
        }
        address.vcardTypeParam?.trim()?.takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
            val type = token.trim()
            if (type.isNotBlank()) adr.addParameter("TYPE", type)
        }
    }

    private fun parseAddressTypes(adr: Address): Pair<String, String?> {
        val values = buildList {
            adr.types.forEach { add(it.value.uppercase()) }
            adr.parameters.types.forEach { if (it.isNotBlank()) add(it.uppercase()) }
        }.distinct()
        val normalized = ContactFieldOptions.normalizeType(values.firstOrNull().orEmpty())
        if (ContactFieldOptions.addresses.any { it.id == normalized }) {
            val used = mapAddressType(normalized)?.value?.uppercase()?.let { setOf(it) } ?: emptySet()
            val extra = values.filterNot { it in used }
            return normalized to extra.joinToString(",").takeIf { it.isNotBlank() }
        }
        val rawType = adr.parameters.types.filter { it.isNotBlank() }.joinToString(",")
            .ifBlank { values.joinToString(",") }
        return rawType.ifBlank { "home" } to null
    }

    private fun applyUrlTypes(link: Url, url: UrlEntity) {
        val urlType = url.type.trim()
        if (urlType.isNotBlank() && !urlType.equals("website", ignoreCase = true)) {
            link.type = urlType.uppercase()
        }
        url.vcardTypeParam?.trim()?.takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
            val type = token.trim()
            if (type.isNotBlank()) link.addParameter("TYPE", type)
        }
    }

    private fun applyImTypes(impp: Impp, im: ImEntity) {
        val protocol = im.type.ifBlank { "other" }.uppercase()
        impp.parameters.addType(protocol)
        im.vcardTypeParam?.trim()?.takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
            val type = token.trim()
            if (type.isNotBlank()) impp.addParameter("TYPE", type)
        }
    }

    private fun applySocialTypes(property: RawProperty, social: SocialProfileEntity) {
        property.parameters.addType(social.type.uppercase())
        social.vcardTypeParam?.trim()?.takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
            val type = token.trim()
            if (type.isNotBlank()) property.addParameter("TYPE", type)
        }
    }

    private fun applyCloudTypes(property: RawProperty, cloud: CloudIdEntity) {
        if (cloud.type.isNotBlank()) property.parameters.addType(cloud.type.uppercase())
        cloud.vcardTypeParam?.trim()?.takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
            val type = token.trim()
            if (type.isNotBlank()) property.addParameter("TYPE", type)
        }
    }

    private fun applyRelatedTypes(rel: Related, related: RelatedEntity) {
        mapRelatedType(related.type)?.let { rel.types.add(it) }
            ?: run {
                if (related.type.isNotBlank()) {
                    rel.parameters.addType(related.type.uppercase())
                }
            }
        related.vcardTypeParam?.trim()?.takeIf { it.isNotBlank() }?.split(',')?.forEach { token ->
            val type = token.trim()
            if (type.isNotBlank()) rel.addParameter("TYPE", type)
        }
    }

    data class ParsedContact(
        val contact: ContactEntity,
        val phones: List<PhoneEntity>,
        val emails: List<EmailEntity>,
        val addresses: List<AddressEntity> = emptyList(),
        val urls: List<UrlEntity> = emptyList(),
        val imAccounts: List<ImEntity> = emptyList(),
        val socialProfiles: List<SocialProfileEntity> = emptyList(),
        val cloudIds: List<CloudIdEntity> = emptyList(),
        val languages: List<LanguageEntity> = emptyList(),
        val relatedNames: List<RelatedEntity> = emptyList(),
        val customFields: List<CustomFieldEntity> = emptyList(),
        val photoBytes: ByteArray? = null,
    )
}
