package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import com.nextcloud.contacts.ui.util.localizedFieldLabel
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.nextcloud.contacts.ui.util.IdentifierKeyboard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nextcloud.contacts.data.local.ContactFieldOptions
import com.nextcloud.contacts.data.remote.CloudUser
import com.nextcloud.contacts.ui.viewmodel.CloudUserSearchState

@Composable
fun FormCloudIdRow(
    type: String,
    onTypeChange: (String) -> Unit,
    typeOptions: List<ContactFieldOptions.Option>,
    value: String,
    onValueChange: (String) -> Unit,
    examplePlaceholder: String,
    searchState: CloudUserSearchState,
    onOpenPicker: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPickUser: (CloudUser) -> Unit,
    modifier: Modifier = Modifier,
    typePickerTitle: String = stringResource(R.string.picker_cloud_type),
    onRemove: (() -> Unit)? = null,
    valueFocus: FormValueFocus? = null,
    freeFormInputs: Boolean = false,
) {
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
            imageVector = Icons.Default.Cloud,
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
                            text = examplePlaceholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            if (!freeFormInputs && value.isBlank()) {
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

    if (showUserPicker && !freeFormInputs) {
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

@Composable
fun NextcloudUserPickerDialog(
    state: CloudUserSearchState,
    onQueryChange: (String) -> Unit,
    onSelect: (CloudUser) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (state.query.isEmpty() && state.users.isEmpty() && state.error == null && state.canSearch) {
            onQueryChange("")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.pick_nextcloud_user),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                Text(
                    text = stringResource(R.string.example_cloud_id, state.exampleCloudId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    singleLine = true,
                    enabled = state.canSearch,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_users),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { onQueryChange("") }
                                    .padding(4.dp),
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp)
                        .padding(top = 8.dp),
                ) {
                    when {
                        state.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp),
                            )
                        }
                        !state.error.isNullOrBlank() -> {
                            Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                            )
                        }
                        state.users.isEmpty() -> {
                            Text(
                                text = if (state.canSearch) stringResource(R.string.empty_no_users) else stringResource(R.string.sign_in_to_pick_user),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                            )
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.users, key = { it.cloudId }) { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelect(user) }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CloudUserAvatar(
                                            user = user,
                                            authHeader = state.avatarAuthHeader,
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.displayName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = user.cloudId,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = GroupedFormDefaults.RowPadding),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    )
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun CloudUserAvatar(
    user: CloudUser,
    authHeader: String?,
) {
    NextcloudUserAvatar(
        cloudId = user.cloudId,
        displayName = user.displayName,
        avatarUrl = user.avatarUrl,
        authHeader = authHeader,
        size = 40.dp,
    )
}
