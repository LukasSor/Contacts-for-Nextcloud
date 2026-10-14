package com.nextcloud.contacts.ui.screens

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.nextcloud.contacts.data.local.AddressInput
import com.nextcloud.contacts.data.local.CustomFieldInput
import com.nextcloud.contacts.data.local.VCardSyntaxExtras
import com.nextcloud.contacts.data.local.VCardTypedFieldInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import com.nextcloud.contacts.ui.components.FormAddRow
import com.nextcloud.contacts.ui.components.FormTextRow
import com.nextcloud.contacts.ui.components.VCardSyntaxRowConfig
import com.nextcloud.contacts.ui.components.buildVCardSyntaxRows
import com.nextcloud.contacts.ui.util.IdentifierKeyboard
import com.nextcloud.contacts.data.remote.VCardPhotoHelper
import com.nextcloud.contacts.ui.viewmodel.ContactEditState

internal object ScalarVCardProperty {
    const val FN = "FN"
    const val N = "N"
    const val NICKNAME = "NICKNAME"
    const val PHONETIC_GIVEN = "X-PHONETIC-FIRST-NAME"
    const val PHONETIC_FAMILY = "X-PHONETIC-LAST-NAME"
    const val ORG = "ORG"
    const val TITLE = "TITLE"
    const val ROLE = "ROLE"
    const val GENDER = "GENDER"
    const val BDAY = "BDAY"
    const val BIRTHPLACE = "BIRTHPLACE"
    const val ANNIVERSARY = "ANNIVERSARY"
    const val DEATHDATE = "DEATHDATE"
    const val DEATHPLACE = "DEATHPLACE"
    const val CATEGORIES = "CATEGORIES"
    const val NOTE = "NOTE"
    const val TZ = "TZ"
    const val MANAGERS = "X-MANAGERSNAME"
    const val RELATIONSHIP = "RELATIONSHIP"
    const val PHOTO = "PHOTO"
}

internal fun ContactEditState.scalarVcardExtra(property: String): VCardSyntaxExtras =
    scalarVcardExtras[property.trim().uppercase()] ?: VCardSyntaxExtras()

internal fun ContactEditState.structuredNameRawValue(): String {
    scalarVcardExtra(ScalarVCardProperty.N).rawValue.trim().takeIf { it.isNotEmpty() }?.let { return it }
    return listOf(familyName, givenName, middleName, prefix, suffix).joinToString(";")
}

internal fun ContactEditState.formattedNameRawValue(): String =
    scalarVcardExtra(ScalarVCardProperty.FN).rawValue.trim().ifBlank { displayName }

internal fun ContactEditState.effectiveScalarSyntax(
    property: String,
    fallbackRaw: String,
): VCardSyntaxExtras {
    val stored = scalarVcardExtra(property)
    return if (stored.rawValue.isBlank() && fallbackRaw.isNotBlank()) {
        stored.copy(rawValue = fallbackRaw)
    } else {
        stored
    }
}

internal fun ContactEditState.effectivePhotoSyntax(storedPhotoBytes: ByteArray? = null): VCardSyntaxExtras {
    if (removePhoto) return VCardSyntaxExtras()
    val stored = scalarVcardExtra(ScalarVCardProperty.PHOTO)
    if (stored.rawValue.isNotBlank() || stored.hasPersistableMetadata()) {
        return stored
    }
    val bytes = photoBytes ?: storedPhotoBytes
    if (bytes != null) {
        val baseline = photoVcardBaselineRaw ?: VCardPhotoHelper.baselineRawValue(bytes, stored)
        return stored.copy(rawValue = baseline)
    }
    return stored
}

internal fun ContactEditState.photoPreviewBytes(storedPhotoBytes: ByteArray? = null): ByteArray? {
    if (removePhoto) return null
    val syntax = effectivePhotoSyntax(storedPhotoBytes)
    val fallback = photoBytes ?: storedPhotoBytes
    return VCardPhotoHelper.previewBytes(syntax.rawValue, fallback)
}

internal fun ContactEditState.lineManagerSyntax(): VCardSyntaxExtras {
    val stored = scalarVcardExtra(ScalarVCardProperty.MANAGERS)
    val params = stored.extraParameters.toMutableList()
    if (managersUid.isNotBlank() && params.none { it.first.equals("UID", ignoreCase = true) }) {
        params.add(0, "UID" to managersUid)
    }
    val raw = stored.rawValue.trim().ifBlank { managersName }
    return stored.copy(extraParameters = params, rawValue = raw)
}

@Composable
internal fun MutableList<@Composable () -> Unit>.addScalarVCardBlock(
    fieldSyntaxEnabled: Boolean,
    propertyName: String,
    syntax: VCardSyntaxExtras,
    onSyntaxChange: (VCardSyntaxExtras) -> Unit,
    showTypeField: Boolean = false,
    showAbLabel: Boolean = false,
    multilineValue: Boolean = false,
    valueLabel: String = stringResource(R.string.raw_value),
) {
    if (!fieldSyntaxEnabled) return
    addScalarFieldSyntaxRows(
        fieldSyntaxEnabled = true,
        propertyName = propertyName,
        syntax = syntax,
        onSyntaxChange = onSyntaxChange,
        showTypeField = showTypeField,
        showAbLabel = showAbLabel,
        multilineValue = multilineValue,
        valueLabel = valueLabel,
    )
}

@Composable
internal fun MutableList<@Composable () -> Unit>.addTypedFieldVCardBlock(
    fieldSyntaxEnabled: Boolean,
    field: VCardTypedFieldInput,
    defaultPropertyName: String,
    onFieldChange: (VCardTypedFieldInput) -> Unit,
    onRemove: (() -> Unit)? = null,
    multilineValue: Boolean = false,
    valueLabel: String = stringResource(R.string.raw_value),
    showAbLabel: Boolean = true,
) {
    if (!fieldSyntaxEnabled) return
    val syntax = field.syntax.let { current ->
        if (current.rawValue.isBlank() && field.value.isNotBlank()) {
            current.copy(rawValue = field.value, typeParam = current.typeParam.ifBlank { field.type })
        } else if (current.typeParam.isBlank() && field.type.isNotBlank()) {
            current.copy(typeParam = field.type)
        } else {
            current
        }
    }
    buildVCardSyntaxRows(
        enabled = true,
        config = VCardSyntaxRowConfig(
            propertyName = defaultPropertyName,
            showTypeField = true,
            showAbLabel = showAbLabel,
            multilineValue = multilineValue,
            valueLabel = valueLabel,
        ),
        syntax = syntax,
        onSyntaxChange = { updated ->
            onFieldChange(
                field.copy(
                    value = updated.rawValue,
                    type = updated.typeParam.ifBlank { field.type },
                    syntax = updated,
                ),
            )
        },
        propertyNameOverride = field.effectivePropertyName(defaultPropertyName),
        onPropertyNameChange = null,
        valueKeyboardOptions = IdentifierKeyboard.forVCardProperty(
            propertyName = field.effectivePropertyName(defaultPropertyName),
            type = field.displayTypeForFreeForm(),
            value = syntax.rawValue.ifBlank { field.value },
        ),
    ).forEach { row -> add(row) }
    if (onRemove != null) {
        add {
            FormAddRow(
                label = stringResource(R.string.action_remove),
                onClick = onRemove,
            )
        }
    }
}

@Composable
internal fun MutableList<@Composable () -> Unit>.addAddressVCardBlock(
    fieldSyntaxEnabled: Boolean,
    address: AddressInput,
    onAddressChange: (AddressInput) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    if (!fieldSyntaxEnabled) return
    val syntax = address.syntax.let { current ->
        val raw = address.rawAdrValue()
        if (current.rawValue.isBlank() && raw.isNotBlank()) {
            current.copy(rawValue = raw, typeParam = current.typeParam.ifBlank { address.type })
        } else if (current.typeParam.isBlank() && address.type.isNotBlank()) {
            current.copy(typeParam = address.type)
        } else {
            current
        }
    }
    buildVCardSyntaxRows(
        enabled = true,
        config = VCardSyntaxRowConfig(
            propertyName = "ADR",
            showTypeField = true,
            showAbLabel = true,
            multilineValue = true,
            valueLabel = stringResource(R.string.adr_value_raw),
        ),
        syntax = syntax,
        onSyntaxChange = { updated ->
            onAddressChange(
                address.copy(
                    type = updated.typeParam.ifBlank { address.type },
                    syntax = updated,
                ),
            )
        },
    ).forEach { row -> add(row) }
    add {
        FormTextRow(
            value = address.geo,
            onValueChange = { text ->
                onAddressChange(address.copy(geo = text))
            },
            placeholder = stringResource(R.string.field_coordinates),
            icon = Icons.Default.MyLocation,
        )
    }
    if (onRemove != null) {
        add {
            FormAddRow(
                label = stringResource(R.string.action_remove),
                onClick = onRemove,
            )
        }
    }
}

@Composable
internal fun MutableList<@Composable () -> Unit>.addCustomFieldVCardBlock(
    fieldSyntaxEnabled: Boolean,
    field: CustomFieldInput,
    onFieldChange: (CustomFieldInput) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    if (!fieldSyntaxEnabled) return
    val syntax = VCardSyntaxExtras(
        propertyGroup = field.propertyGroup,
        typeParam = field.typeParam,
        extraParameters = field.extraParameters,
        abLabel = field.label,
        rawValue = field.value,
    )
    buildVCardSyntaxRows(
        enabled = true,
        config = VCardSyntaxRowConfig(
            propertyName = field.normalizedPropertyName(),
            propertyNameEditable = true,
            showTypeField = true,
            showAbLabel = true,
            multilineValue = true,
        ),
        syntax = syntax,
        onSyntaxChange = { updated ->
            onFieldChange(
                field.copy(
                    propertyName = field.propertyName.ifBlank { field.normalizedPropertyName() },
                    propertyGroup = updated.propertyGroup,
                    typeParam = updated.typeParam,
                    extraParameters = updated.extraParameters,
                    label = updated.abLabel.ifBlank { field.label },
                    value = updated.rawValue,
                ),
            )
        },
        propertyNameOverride = field.propertyName,
        onPropertyNameChange = { name ->
            onFieldChange(field.copy(propertyName = name.trim().uppercase()))
        },
    ).forEach { row -> add(row) }
    if (onRemove != null) {
        add {
            FormAddRow(
                label = stringResource(R.string.action_remove),
                onClick = onRemove,
            )
        }
    }
}

@Composable
internal fun MutableList<@Composable () -> Unit>.addScalarFieldSyntaxRows(
    fieldSyntaxEnabled: Boolean,
    propertyName: String,
    syntax: VCardSyntaxExtras,
    onSyntaxChange: (VCardSyntaxExtras) -> Unit,
    showTypeField: Boolean = true,
    showAbLabel: Boolean = true,
    multilineValue: Boolean = false,
    valueLabel: String = stringResource(R.string.raw_value),
) {
    buildVCardSyntaxRows(
        enabled = fieldSyntaxEnabled,
        config = VCardSyntaxRowConfig(
            propertyName = propertyName,
            showTypeField = showTypeField,
            showAbLabel = showAbLabel,
            multilineValue = multilineValue,
            valueLabel = valueLabel,
        ),
        syntax = syntax,
        onSyntaxChange = onSyntaxChange,
    ).forEach { row -> add(row) }
}

@Composable
internal fun MutableList<@Composable () -> Unit>.addAddressSyntaxRows(
    fieldSyntaxEnabled: Boolean,
    address: AddressInput,
    onSyntaxChange: (VCardSyntaxExtras) -> Unit,
) {
    addAddressVCardBlock(
        fieldSyntaxEnabled = fieldSyntaxEnabled,
        address = address,
        onAddressChange = { onSyntaxChange(it.syntax) },
    )
}

@Composable
internal fun MutableList<@Composable () -> Unit>.addCustomFieldSyntaxRows(
    fieldSyntaxEnabled: Boolean,
    field: CustomFieldInput,
    onPropertyNameChange: (String) -> Unit,
    onSyntaxChange: (VCardSyntaxExtras) -> Unit,
) {
    addCustomFieldVCardBlock(
        fieldSyntaxEnabled = fieldSyntaxEnabled,
        field = field,
        onFieldChange = { updated ->
            onPropertyNameChange(updated.propertyName)
            onSyntaxChange(
                VCardSyntaxExtras(
                    propertyGroup = updated.propertyGroup,
                    typeParam = updated.typeParam,
                    extraParameters = updated.extraParameters,
                    abLabel = updated.label,
                    rawValue = updated.value,
                ),
            )
        },
    )
}
