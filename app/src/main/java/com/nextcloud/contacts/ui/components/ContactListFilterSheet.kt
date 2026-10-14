package com.nextcloud.contacts.ui.components

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.nextcloud.contacts.ui.viewmodel.ContactListFilterState
import com.nextcloud.contacts.ui.viewmodel.ContactListSortDirection
import com.nextcloud.contacts.ui.viewmodel.ContactListSortField
import com.nextcloud.contacts.ui.viewmodel.label

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactListFilterSheet(
    filterState: ContactListFilterState,
    availableGroups: List<String>,
    onSortFieldSelected: (ContactListSortField) -> Unit,
    onSortDirectionSelected: (ContactListSortDirection) -> Unit,
    onToggleGroup: (String) -> Unit,
    onShowFavoritesOnlyChange: (Boolean) -> Unit,
    onShowHasPhoneChange: (Boolean) -> Unit,
    onShowHasEmailChange: (Boolean) -> Unit,
    onShowHasNextcloudAccountChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val maxScrollHeight = (configuration.screenHeightDp * 0.55f).dp
    val sortFields = listOf(
        ContactListSortField.DISPLAY_NAME,
        ContactListSortField.FIRST_NAME,
        ContactListSortField.LAST_NAME,
        ContactListSortField.NICKNAME,
        ContactListSortField.ORGANIZATION,
        ContactListSortField.BIRTHDAY,
    )
    val directionOptions = listOf(
        ContactListSortDirection.ASCENDING,
        ContactListSortDirection.DESCENDING,
    )
    val clearEnabled = filterState.hasActiveFilters

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.cd_filter_sort),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxScrollHeight)
                    .modalBottomSheetScrollableContent()
                    .verticalScroll(rememberScrollState(), overscrollEffect = null)
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.sort_by),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                sortFields.forEach { field ->
                    SortFieldRow(
                        label = field.label(),
                        selected = filterState.sortField == field,
                        onClick = { onSortFieldSelected(field) },
                    )
                }

                Text(
                    text = stringResource(R.string.sort_order),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    directionOptions.forEachIndexed { index, direction ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = directionOptions.size,
                            ),
                            onClick = { onSortDirectionSelected(direction) },
                            selected = filterState.sortDirection == direction,
                            label = { Text(direction.label()) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.filter_show),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filterState.showFavoritesOnly,
                        onClick = { onShowFavoritesOnlyChange(!filterState.showFavoritesOnly) },
                        label = { Text(stringResource(R.string.section_favorites)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = filterState.showHasPhone,
                        onClick = { onShowHasPhoneChange(!filterState.showHasPhone) },
                        label = { Text(stringResource(R.string.filter_has_phone)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = filterState.showHasEmail,
                        onClick = { onShowHasEmailChange(!filterState.showHasEmail) },
                        label = { Text(stringResource(R.string.filter_has_email)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = filterState.showHasNextcloudAccount,
                        onClick = {
                            onShowHasNextcloudAccountChange(!filterState.showHasNextcloudAccount)
                        },
                        label = { Text(stringResource(R.string.filter_has_nextcloud)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }

                if (availableGroups.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.field_groups),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableGroups.forEach { group ->
                            val selected = filterState.selectedGroups.any {
                                it.equals(group, ignoreCase = true)
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleGroup(group) },
                                label = { Text(group) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onClear,
                enabled = clearEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
}

@Composable
private fun SortFieldRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
