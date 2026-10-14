package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import com.nextcloud.contacts.ui.util.localizedFieldLabel
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.remote.PlaceSuggestion
import com.nextcloud.contacts.ui.viewmodel.PlaceAutocompleteState
import com.nextcloud.contacts.ui.viewmodel.PlaceAutocompleteTarget

private const val MaxVisiblePlaceSuggestions = 4

@Composable
fun FormAddressFreeFormRow(
    type: String,
    onTypeChange: (String) -> Unit,
    rawAdrValue: String,
    onRawAdrValueChange: (String) -> Unit,
    typeOptions: List<ContactFieldOptions.Option>,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    FormTypedTextRow(
        type = type,
        onTypeChange = onTypeChange,
        typeOptions = typeOptions,
        value = rawAdrValue,
        onValueChange = onRawAdrValueChange,
        placeholder = stringResource(R.string.adr_value_raw),
        icon = Icons.Default.LocationOn,
        typePickerTitle = stringResource(R.string.picker_address_type),
        onRemove = onRemove,
        freeFormInputs = true,
        modifier = modifier,
        valueSingleLine = false,
    )
}

@Composable
fun FormAddressStreetAutocompleteRow(
    type: String,
    onTypeChange: (String) -> Unit,
    typeOptions: List<ContactFieldOptions.Option>,
    value: String,
    onValueChange: (String) -> Unit,
    addressIndex: Int,
    autocomplete: PlaceAutocompleteState,
    onQueryChange: (Int, String) -> Unit,
    onPickSuggestion: (Int, PlaceSuggestion) -> Unit,
    onDismissSuggestions: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    typePickerTitle: String = stringResource(R.string.picker_address_type),
    onRemove: (() -> Unit)? = null,
) {
    var showTypePicker by remember { mutableStateOf(false) }
    val view = LocalView.current
    val isActive = autocomplete.target == PlaceAutocompleteTarget.AddressStreet(addressIndex)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = GroupedFormDefaults.RowPadding,
                    vertical = 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.clickable { showTypePicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = localizedFieldLabel(typeOptions, type),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.padding(top = 4.dp))
                BasicTextField(
                    value = value,
                    onValueChange = {
                        onValueChange(it)
                        onQueryChange(addressIndex, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && isActive) {
                                view.post { onDismissSuggestions() }
                            }
                        },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            }
            if (onRemove != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.RemoveCircleOutline,
                    contentDescription = stringResource(R.string.action_remove),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onRemove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlaceAutocompleteSuggestions(
            state = autocomplete,
            isActive = isActive,
            onSelect = { onPickSuggestion(addressIndex, it) },
        )
    }

    if (showTypePicker) {
        FormOptionPickerDialog(
            title = typePickerTitle,
            options = typeOptions,
            selectedId = type,
            onSelect = {
                onTypeChange(it)
                showTypePicker = false
            },
            onDismiss = { showTypePicker = false },
        )
    }
}

@Composable
fun FormPlaceTextAutocompleteRow(
    value: String,
    onValueChange: (String) -> Unit,
    target: PlaceAutocompleteTarget,
    autocomplete: PlaceAutocompleteState,
    onQueryChange: (PlaceAutocompleteTarget, String) -> Unit,
    onPickSuggestion: (PlaceAutocompleteTarget, PlaceSuggestion) -> Unit,
    onDismissSuggestions: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    valueFocus: FormValueFocus? = null,
) {
    val view = LocalView.current
    val isActive = autocomplete.target == target

    Column(
        modifier = modifier
            .formBringIntoView(valueFocus)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = GroupedFormDefaults.RowPadding,
                    vertical = 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            BasicTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    onQueryChange(target, it)
                },
                modifier = Modifier
                    .weight(1f)
                    .formValueFocusRequester(valueFocus)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && isActive) {
                            view.post { onDismissSuggestions() }
                        }
                    },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
        PlaceAutocompleteSuggestions(
            state = autocomplete,
            isActive = isActive,
            onSelect = { onPickSuggestion(target, it) },
        )
    }
}

private val PlaceSuggestionTextIndent = 38.dp // icon (22) + gap (16), matches [FormTextRow]

private fun PlaceSuggestion.labelLines(): Pair<String, String?> {
    val parts = label.split(" — ", limit = 2)
    return if (parts.size == 2) {
        parts[0].trim() to parts[1].trim().takeIf { it.isNotEmpty() }
    } else {
        label.trim() to null
    }
}

@Composable
fun PlaceAutocompleteSuggestions(
    state: PlaceAutocompleteState,
    isActive: Boolean,
    onSelect: (PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.enabled || !isActive || state.hideSuggestions) return

    val showPanel = state.isLoading ||
        !state.error.isNullOrBlank() ||
        state.suggestions.isNotEmpty() ||
        (state.query.trim().length >= 2 && !state.isLoading)
    if (!showPanel) return

    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = GroupedFormDefaults.RowPadding),
            thickness = 1.dp,
            color = dividerColor,
        )
        when {
            state.isLoading -> {
                PlaceAutocompleteStatusRow {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.searching_places),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            !state.error.isNullOrBlank() -> {
                PlaceAutocompleteStatusRow {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            state.suggestions.isEmpty() -> {
                PlaceAutocompleteStatusRow {
                    Text(
                        text = stringResource(R.string.empty_no_places),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                state.suggestions.take(MaxVisiblePlaceSuggestions).forEachIndexed { index, suggestion ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = GroupedFormDefaults.RowPadding),
                            thickness = 1.dp,
                            color = dividerColor,
                        )
                    }
                    val (primary, secondary) = suggestion.labelLines()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(suggestion) }
                            .padding(
                                horizontal = GroupedFormDefaults.RowPadding,
                                vertical = 14.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width(PlaceSuggestionTextIndent))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = primary,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (secondary != null) {
                                Text(
                                    text = secondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceAutocompleteStatusRow(
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = GroupedFormDefaults.RowPadding,
                vertical = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(PlaceSuggestionTextIndent))
        content()
    }
}
