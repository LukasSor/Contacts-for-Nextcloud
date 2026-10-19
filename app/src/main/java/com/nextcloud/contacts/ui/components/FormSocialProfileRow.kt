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
import com.nextcloud.contacts.ui.util.IdentifierKeyboard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveCircleOutline
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.remote.CloudUser
import com.nextcloud.contacts.ui.viewmodel.CloudUserSearchState

@Composable
fun FormSocialProfileRow(
    type: String,
    onTypeChange: (String) -> Unit,
    typeOptions: List<ContactFieldOptions.Option>,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    nextcloudExamplePlaceholder: String,
    searchState: CloudUserSearchState,
    onOpenPicker: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPickUser: (CloudUser) -> Unit,
    modifier: Modifier = Modifier,
    typePickerTitle: String = stringResource(R.string.field_social_network),
    onRemove: (() -> Unit)? = null,
    valueFocus: FormValueFocus? = null,
    freeFormInputs: Boolean = false,
) {
    val isNextcloud = !freeFormInputs && type.equals("nextcloud", ignoreCase = true)
    var showTypePicker by remember { mutableStateOf(false) }
    var showUserPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .formBringIntoView(valueFocus)
            .fillMaxWidth()
            .padding(
                horizontal = GroupedFormDefaults.RowPadding,
                vertical = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Public,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (freeFormInputs) {
                BasicTextField(
                    value = type,
                    onValueChange = onTypeChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (type.isEmpty()) {
                            Text(
                                text = stringResource(R.string.label_type),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            } else {
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
            }
            Spacer(modifier = Modifier.padding(top = 4.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .formValueFocusRequester(valueFocus),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                keyboardOptions = IdentifierKeyboard.uri,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = if (isNextcloud) nextcloudExamplePlaceholder else placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            if (isNextcloud && value.isBlank() && searchState.canSearch) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            onOpenPicker()
                            showUserPicker = true
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pick_from_nextcloud),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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

    if (showTypePicker && !freeFormInputs) {
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

    if (showUserPicker && !freeFormInputs && searchState.canSearch) {
        NextcloudUserPickerDialog(
            state = searchState,
            onQueryChange = onSearchQueryChange,
            onSelect = { user ->
                onPickUser(user)
                showUserPicker = false
            },
            onDismiss = { showUserPicker = false },
        )
    }
}
