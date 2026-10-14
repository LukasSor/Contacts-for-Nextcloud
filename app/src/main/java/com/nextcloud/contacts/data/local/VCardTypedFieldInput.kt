package com.nextcloud.contacts.data.local

/** Editable vCard line metadata shared by built-in multi-value fields and the edit form. */
data class VCardSyntaxExtras(
    val propertyGroup: String = "",
    val typeParam: String = "",
    val extraParameters: List<Pair<String, String>> = emptyList(),
    val abLabel: String = "",
    /** When set, written as the property value as-is (semicolons preserved). */
    val rawValue: String = "",
) {
    fun hasPersistableMetadata(): Boolean =
        propertyGroup.isNotBlank() ||
            typeParam.isNotBlank() ||
            abLabel.isNotBlank() ||
            rawValue.isNotBlank() ||
            extraParameters.any { it.first.isNotBlank() || it.second.isNotBlank() }
}

data class VCardTypedFieldInput(
    val type: String = "",
    val value: String = "",
    val propertyName: String = "",
    val syntax: VCardSyntaxExtras = VCardSyntaxExtras(),
) {
    fun effectivePropertyName(default: String): String =
        propertyName.trim().ifBlank { default }.uppercase()

    /** Type string shown in per-contact vCard free-form edit mode (preserves custom TYPE values). */
    fun displayTypeForFreeForm(): String =
        syntax.typeParam.trim().ifBlank { type }

    companion object {
        fun phone(type: String = "mobile", value: String = "") = VCardTypedFieldInput(
            type = type,
            value = value,
            propertyName = "TEL",
        )

        fun email(type: String = "home", value: String = "") = VCardTypedFieldInput(
            type = type,
            value = value,
            propertyName = "EMAIL",
        )

        fun url(type: String = "", value: String = "") = VCardTypedFieldInput(
            type = type,
            value = value,
            propertyName = "URL",
        )

        fun im(type: String = "signal", value: String = "") = VCardTypedFieldInput(
            type = type,
            value = value,
            propertyName = "IMPP",
        )

        fun social(type: String = "other", value: String = "") = VCardTypedFieldInput(
            type = type,
            value = value,
            propertyName = "X-SOCIALPROFILE",
        )

        fun cloud(type: String = "home", value: String = "") = VCardTypedFieldInput(
            type = type,
            value = value,
            propertyName = "CLOUD",
        )

        fun related(type: String = "contact", value: String = "") = VCardTypedFieldInput(
            type = type,
            value = value,
            propertyName = "RELATED",
        )
    }
}

fun PhoneEntity.toFieldInput(): VCardTypedFieldInput = VCardTypedFieldInput(
    type = type,
    value = value,
    propertyName = "TEL",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun VCardTypedFieldInput.toPhoneEntity(contactId: Long): PhoneEntity = PhoneEntity(
    contactId = contactId,
    type = type.ifBlank { "mobile" },
    value = value,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)

fun EmailEntity.toFieldInput(): VCardTypedFieldInput = VCardTypedFieldInput(
    type = type,
    value = value,
    propertyName = "EMAIL",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun VCardTypedFieldInput.toEmailEntity(contactId: Long): EmailEntity = EmailEntity(
    contactId = contactId,
    type = type.ifBlank { "home" },
    value = value,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)

fun UrlEntity.toFieldInput(): VCardTypedFieldInput = VCardTypedFieldInput(
    type = type,
    value = value,
    propertyName = "URL",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun VCardTypedFieldInput.toUrlEntity(contactId: Long): UrlEntity = UrlEntity(
    contactId = contactId,
    type = type,
    value = value,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)

fun ImEntity.toFieldInput(): VCardTypedFieldInput = VCardTypedFieldInput(
    type = type,
    value = value,
    propertyName = "IMPP",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun VCardTypedFieldInput.toImEntity(contactId: Long): ImEntity = ImEntity(
    contactId = contactId,
    type = type.ifBlank { "signal" },
    value = value,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)

fun SocialProfileEntity.toFieldInput(): VCardTypedFieldInput = VCardTypedFieldInput(
    type = type,
    value = value,
    propertyName = "X-SOCIALPROFILE",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun VCardTypedFieldInput.toSocialProfileEntity(contactId: Long): SocialProfileEntity = SocialProfileEntity(
    contactId = contactId,
    type = type.ifBlank { "other" },
    value = value,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)

fun RelatedEntity.toFieldInput(): VCardTypedFieldInput = VCardTypedFieldInput(
    type = type,
    value = value,
    propertyName = "RELATED",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun VCardTypedFieldInput.toRelatedEntity(contactId: Long): RelatedEntity = RelatedEntity(
    contactId = contactId,
    type = type.ifBlank { "contact" },
    value = value,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)

fun LanguageEntity.toFieldInput(): VCardTypedFieldInput = VCardTypedFieldInput(
    type = "",
    value = value,
    propertyName = "LANG",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun VCardTypedFieldInput.toLanguageEntity(contactId: Long): LanguageEntity = LanguageEntity(
    contactId = contactId,
    value = value,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)

fun CloudIdEntity.toFieldInput(): VCardTypedFieldInput = VCardTypedFieldInput(
    type = type,
    value = value,
    propertyName = "CLOUD",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun VCardTypedFieldInput.toCloudIdEntity(contactId: Long): CloudIdEntity = CloudIdEntity(
    contactId = contactId,
    type = type.ifBlank { "home" },
    value = value,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)

fun Pair<String, String>.toPhoneFieldInput(): VCardTypedFieldInput =
    VCardTypedFieldInput.phone(type = first, value = second)

fun Pair<String, String>.toEmailFieldInput(): VCardTypedFieldInput =
    VCardTypedFieldInput.email(type = first, value = second)

fun Pair<String, String>.toUrlFieldInput(): VCardTypedFieldInput =
    VCardTypedFieldInput.url(type = first, value = second)

fun Pair<String, String>.toImFieldInput(): VCardTypedFieldInput =
    VCardTypedFieldInput.im(type = first, value = second)

fun Pair<String, String>.toSocialFieldInput(): VCardTypedFieldInput =
    VCardTypedFieldInput.social(type = first, value = second)

fun Pair<String, String>.toRelatedFieldInput(): VCardTypedFieldInput =
    VCardTypedFieldInput.related(type = first, value = second)

fun Pair<String, String>.toCloudFieldInput(): VCardTypedFieldInput =
    VCardTypedFieldInput.cloud(type = first, value = second)

fun String.toLanguageFieldInput(): VCardTypedFieldInput =
    VCardTypedFieldInput(type = "", value = this, propertyName = "LANG")

fun AddressEntity.toInput(): AddressInput = AddressInput(
    type = type,
    poBox = poBox,
    extendedAddress = extendedAddress,
    street = street,
    city = city,
    region = region,
    postalCode = postalCode,
    country = country,
    geo = geo,
    propertyName = "ADR",
    syntax = VCardSyntaxExtras(
        propertyGroup = vcardPropertyGroup,
        typeParam = vcardTypeParam.orEmpty(),
        extraParameters = CustomFieldExtras.decode(vcardExtraParametersJson),
        abLabel = vcardAbLabel,
        rawValue = vcardRawValue,
    ),
)

fun AddressInput.toEntity(contactId: Long): AddressEntity = AddressEntity(
    contactId = contactId,
    type = type.ifBlank { "home" },
    poBox = poBox,
    extendedAddress = extendedAddress,
    street = street,
    city = city,
    region = region,
    postalCode = postalCode,
    country = country,
    geo = geo,
    vcardPropertyGroup = syntax.propertyGroup.trim(),
    vcardTypeParam = syntax.typeParam.trim().ifBlank { null },
    vcardExtraParametersJson = CustomFieldExtras.encode(syntax.extraParameters),
    vcardAbLabel = syntax.abLabel.trim(),
    vcardRawValue = syntax.rawValue,
)
