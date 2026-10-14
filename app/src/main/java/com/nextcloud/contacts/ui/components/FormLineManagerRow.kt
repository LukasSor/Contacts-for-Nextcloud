package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nextcloud.contacts.ui.viewmodel.LineManagerCandidate

@Composable
fun FormLineManagerRow(
    displayName: String,
    managersUid: String,
    candidates: List<LineManagerCandidate>,
    onPick: (LineManagerCandidate) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    valueFocus: FormValueFocus? = null,
    freeFormInputs: Boolean = false,
    onDisplayNameChange: ((String) -> Unit)? = null,
    onManagersUidChange: ((String) -> Unit)? = null,
) {
    if (freeFormInputs) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            FormTextRow(
                value = displayName,
                onValueChange = { onDisplayNameChange?.invoke(it) },
                placeholder = stringResource(R.string.line_manager_name),
                icon = Icons.Default.SupervisorAccount,
                valueFocus = valueFocus,
            )
            FormTextRow(
                value = managersUid,
                onValueChange = { onManagersUidChange?.invoke(it) },
                placeholder = stringResource(R.string.line_manager_uid),
                icon = Icons.Default.SupervisorAccount,
                onRemove = if (displayName.isNotBlank() || managersUid.isNotBlank()) onClear else null,
            )
        }
        return
    }
    var showPicker by remember { mutableStateOf(false) }
    val hasValue = displayName.isNotBlank()
    val selected = remember(displayName, managersUid, candidates) {
        resolveLineManagerCandidate(candidates, managersUid, displayName)
    }

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
        Row(
            modifier = Modifier
                .weight(1f)
                .formValueFocusRequester(valueFocus)
                .focusable()
                .clickable { showPicker = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasValue) {
                ContactAvatar(
                    contactId = selected?.id ?: 0L,
                    displayName = displayName,
                    size = 40.dp,
                    loadStoredPhoto = selected != null,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SupervisorAccount,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = if (hasValue) displayName else stringResource(R.string.field_line_manager),
                style = MaterialTheme.typography.bodyLarge,
                color = if (hasValue) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasValue) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.RemoveCircleOutline,
                contentDescription = stringResource(R.string.cd_clear_line_manager),
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onClear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    LaunchedEffect(valueFocus?.requester) {
        if (valueFocus != null && !hasValue) {
            showPicker = true
        }
    }

    if (showPicker) {
        LineManagerPickerDialog(
            candidates = candidates,
            selectedDisplayName = displayName,
            selectedUid = managersUid,
            onSelect = {
                onPick(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
fun LineManagerPickerDialog(
    candidates: List<LineManagerCandidate>,
    selectedDisplayName: String,
    selectedUid: String,
    onSelect: (LineManagerCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(candidates, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            candidates
        } else {
            candidates.filter { candidate ->
                candidate.displayName.contains(q, ignoreCase = true) ||
                    candidate.subtitle.contains(q, ignoreCase = true)
            }
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
                    text = stringResource(R.string.field_line_manager),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.action_search),
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
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { query = "" }
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
                        candidates.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.empty_no_contacts),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                            )
                        }
                        filtered.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.empty_no_contacts_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                            )
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filtered, key = { it.uid }) { candidate ->
                                    val selected = when {
                                        selectedUid.isNotBlank() ->
                                            selectedUid.equals(candidate.uid, ignoreCase = true)
                                        else -> selectedDisplayName.equals(
                                            candidate.displayName,
                                            ignoreCase = true,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelect(candidate) }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ContactAvatar(
                                            contactId = candidate.id,
                                            displayName = candidate.displayName,
                                            size = 40.dp,
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.displayName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (candidate.subtitle.isNotBlank()) {
                                                Text(
                                                    text = candidate.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            horizontal = GroupedFormDefaults.RowPadding,
                                        ),
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

private fun resolveLineManagerCandidate(
    candidates: List<LineManagerCandidate>,
    managersUid: String,
    displayName: String,
): LineManagerCandidate? {
    if (managersUid.isNotBlank()) {
        candidates.firstOrNull { it.uid.equals(managersUid, ignoreCase = true) }?.let { return it }
    }
    if (displayName.isBlank()) return null
    return candidates.firstOrNull { it.displayName.equals(displayName, ignoreCase = true) }
}
