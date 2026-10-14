package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.nextcloud.contacts.data.local.VCardSyntaxExtras

data class VCardSyntaxRowConfig(
    val propertyName: String,
    val propertyNameEditable: Boolean = false,
    val showTypeField: Boolean = true,
    val showAbLabel: Boolean = true,
    val valueLabel: String = "",
    val multilineValue: Boolean = false,
    val showRawValue: Boolean = true,
)

@Composable
fun buildVCardSyntaxRows(
    enabled: Boolean,
    config: VCardSyntaxRowConfig,
    syntax: VCardSyntaxExtras,
    onSyntaxChange: (VCardSyntaxExtras) -> Unit,
    propertyNameOverride: String = config.propertyName,
    onPropertyNameChange: ((String) -> Unit)? = null,
    valueKeyboardOptions: KeyboardOptions = KeyboardOptions.Default,
): List<@Composable () -> Unit> {
    if (!enabled) return emptyList()
    return buildList {
        add {
            FormTextRow(
                value = propertyNameOverride,
                onValueChange = { onPropertyNameChange?.invoke(it) },
                placeholder = stringResource(R.string.syntax_property_name),
                icon = Icons.Default.Code,
                singleLine = true,
            )
        }
        add {
            FormTextRow(
                value = syntax.propertyGroup,
                onValueChange = { onSyntaxChange(syntax.copy(propertyGroup = it)) },
                placeholder = stringResource(R.string.syntax_group_optional),
                icon = Icons.Default.Label,
            )
        }
        if (config.showTypeField) {
            add {
                FormTextRow(
                    value = syntax.typeParam,
                    onValueChange = { onSyntaxChange(syntax.copy(typeParam = it)) },
                    placeholder = stringResource(R.string.syntax_type_optional),
                    icon = Icons.Default.Category,
                )
            }
        }
        syntax.extraParameters.forEachIndexed { paramIndex, (paramName, paramValue) ->
            add {
                FormTextRow(
                    value = paramName,
                    onValueChange = {
                        val params = syntax.extraParameters.toMutableList()
                        if (paramIndex in params.indices) {
                            params[paramIndex] = it to paramValue
                            onSyntaxChange(syntax.copy(extraParameters = params))
                        }
                    },
                    placeholder = stringResource(R.string.syntax_parameter_name),
                    icon = Icons.Default.Tune,
                )
            }
            add {
                FormTextRow(
                    value = paramValue,
                    onValueChange = {
                        val params = syntax.extraParameters.toMutableList()
                        if (paramIndex in params.indices) {
                            params[paramIndex] = paramName to it
                            onSyntaxChange(syntax.copy(extraParameters = params))
                        }
                    },
                    placeholder = stringResource(R.string.syntax_parameter_value),
                    icon = Icons.Default.Tune,
                    onRemove = {
                        val params = syntax.extraParameters.toMutableList()
                        if (paramIndex in params.indices) {
                            params.removeAt(paramIndex)
                            onSyntaxChange(syntax.copy(extraParameters = params))
                        }
                    },
                )
            }
        }
        add {
            FormAddRow(
                label = stringResource(R.string.add_parameter),
                onClick = {
                    onSyntaxChange(syntax.copy(extraParameters = syntax.extraParameters + ("" to "")))
                },
            )
        }
        if (config.showAbLabel) {
            add {
                FormTextRow(
                    value = syntax.abLabel,
                    onValueChange = { onSyntaxChange(syntax.copy(abLabel = it)) },
                    placeholder = stringResource(R.string.syntax_label_ab),
                    icon = Icons.Default.ShortText,
                )
            }
        }
        if (config.showRawValue) {
            add {
                FormTextRow(
                    value = syntax.rawValue,
                    onValueChange = { onSyntaxChange(syntax.copy(rawValue = it)) },
                    placeholder = config.valueLabel.ifBlank { stringResource(R.string.raw_value) },
                    icon = Icons.Default.ShortText,
                    singleLine = !config.multilineValue,
                    minLines = if (config.multilineValue) 2 else 1,
                    keyboardOptions = valueKeyboardOptions,
                )
            }
        }
    }
}

@Composable
fun MutableList<@Composable () -> Unit>.addScalarVCardSyntaxRows(
    enabled: Boolean,
    propertyName: String,
    extras: VCardSyntaxExtras,
    onExtrasChange: (VCardSyntaxExtras) -> Unit,
    showTypeField: Boolean = true,
) {
    addAll(
        buildVCardSyntaxRows(
            enabled = enabled,
            config = VCardSyntaxRowConfig(
                propertyName = propertyName,
                propertyNameEditable = false,
                showTypeField = showTypeField,
            ),
            syntax = extras,
            onSyntaxChange = onExtrasChange,
        ),
    )
}
