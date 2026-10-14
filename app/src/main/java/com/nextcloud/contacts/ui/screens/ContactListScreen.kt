package com.nextcloud.contacts.ui.screens

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.ui.components.AppSnackbarHost
import com.nextcloud.contacts.ui.components.ObserveUiNotices
import com.nextcloud.contacts.ui.components.ContactAvatar
import com.nextcloud.contacts.ui.components.ContactListFilterSheet
import com.nextcloud.contacts.ui.components.ContactListScrollbar
import com.nextcloud.contacts.ui.components.ContactListScrollbarEndPadding
import com.nextcloud.contacts.ui.list.ContactListEntry
import com.nextcloud.contacts.ui.list.buildAlphabetIndexModel
import com.nextcloud.contacts.ui.list.contactIdsInVisualOrder
import com.nextcloud.contacts.ui.list.lazyIndexToContactOrdinal
import com.nextcloud.contacts.ui.util.rememberDeviceContactsImportAction
import com.nextcloud.contacts.ui.viewmodel.ContactListSortField
import com.nextcloud.contacts.ui.navigation.ContactListPendingGroupFilter
import com.nextcloud.contacts.ui.viewmodel.ContactListViewModel
import com.nextcloud.contacts.ui.viewmodel.contactBirthdayRowLabel
import com.nextcloud.contacts.ui.viewmodel.sortChipLabel
import com.nextcloud.contacts.util.AvatarBitmapCache
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val SearchResultsShape = RoundedCornerShape(24.dp)
private val GroupCornerOuter = 24.dp
private val GroupCornerInner = 4.dp
private val LetterGroupSpacing = 20.dp

/** Fixed height so a fling does not remeasure every row. */
private val ContactRowHeight = 64.dp

// One shape instance per position. Allocating per row shows up while flinging.
private val ShapeSingle = RoundedCornerShape(GroupCornerOuter)
private val ShapeFirst = RoundedCornerShape(
    topStart = GroupCornerOuter,
    topEnd = GroupCornerOuter,
    bottomStart = GroupCornerInner,
    bottomEnd = GroupCornerInner,
)
private val ShapeLast = RoundedCornerShape(
    topStart = GroupCornerInner,
    topEnd = GroupCornerInner,
    bottomStart = GroupCornerOuter,
    bottomEnd = GroupCornerOuter,
)
private val ShapeMiddle = RoundedCornerShape(GroupCornerInner)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactListScreen(
    onContactClick: (Long) -> Unit,
    onAddContact: () -> Unit,
    onOpenSettings: () -> Unit,
    isPickerMode: Boolean = false,
    onContactsReady: () -> Unit = {},
    viewModel: ContactListViewModel = viewModel(),
) {
    val listEntries by viewModel.listEntries.collectAsState()
    val isContactsReady by viewModel.isContactsReady.collectAsState()
    val listPresentationReady by viewModel.listPresentationReady.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val availableGroups by viewModel.availableGroups.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }
    var filterSheetVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val message by viewModel.message.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importFromDevice = rememberDeviceContactsImportAction(
        onGranted = viewModel::importFromDevice,
        onDenied = { viewModel.showMessage(context.getString(R.string.msg_contacts_permission)) },
    )
    val container = (context.applicationContext as ContactsApp).container
    val showScrollLetters by container.userPreferences.showScrollLetters.collectAsState(initial = true)

    if (isContactsReady) {
        SideEffect { onContactsReady() }
    }

    LaunchedEffect(Unit) {
        ContactListPendingGroupFilter.consume()?.let { group ->
            viewModel.setGroupFilterOnly(group)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    ObserveUiNotices { viewModel.showMessage(it) }

    val dismissSearch: () -> Unit = {
        when {
            searchQuery.isNotEmpty() -> viewModel.updateSearch("")
            searchExpanded -> {
                searchExpanded = false
                focusManager.clearFocus()
            }
        }
    }

    BackHandler(enabled = searchQuery.isNotEmpty() || searchExpanded) {
        dismissSearch()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPickerMode) stringResource(R.string.screen_choose_contact) else stringResource(R.string.screen_contacts)) },
                actions = {
                    if (!isPickerMode) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isPickerMode) {
                FloatingActionButton(onClick = onAddContact) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_contact))
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = SearchResultsShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearch,
                    onSearch = {},
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    placeholder = { Text(stringResource(R.string.search_contacts)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { filterSheetVisible = true }) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.cd_filter_sort),
                                    )
                                    if (filterState.hasActiveFilters) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(8.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape,
                                                ),
                                        )
                                    }
                                }
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearch("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.cd_clear_search),
                                    )
                                }
                            }
                        }
                    },
                )
            }

            if (filterState.hasActiveFilters) {
                ActiveFilterChipsRow(
                    selectedGroups = filterState.selectedGroups.toList()
                        .sortedWith(String.CASE_INSENSITIVE_ORDER),
                    sortChipLabel = if (filterState.isDefaultSort) null else filterState.sortChipLabel(),
                    showFavoritesOnly = filterState.showFavoritesOnly,
                    showHasPhone = filterState.showHasPhone,
                    showHasEmail = filterState.showHasEmail,
                    showHasNextcloudAccount = filterState.showHasNextcloudAccount,
                    onRemoveGroup = viewModel::removeGroupFilter,
                    onClearSort = viewModel::resetSortToDefault,
                    onClearShowFavorites = { viewModel.setShowFavoritesOnly(false) },
                    onClearShowPhone = { viewModel.setShowHasPhone(false) },
                    onClearShowEmail = { viewModel.setShowHasEmail(false) },
                    onClearShowNextcloud = { viewModel.setShowHasNextcloudAccount(false) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (filterSheetVisible) {
                ContactListFilterSheet(
                    filterState = filterState,
                    availableGroups = availableGroups,
                    onSortFieldSelected = viewModel::setSortField,
                    onSortDirectionSelected = viewModel::setSortDirection,
                    onToggleGroup = viewModel::toggleGroupFilter,
                    onShowFavoritesOnlyChange = viewModel::setShowFavoritesOnly,
                    onShowHasPhoneChange = viewModel::setShowHasPhone,
                    onShowHasEmailChange = viewModel::setShowHasEmail,
                    onShowHasNextcloudAccountChange = viewModel::setShowHasNextcloudAccount,
                    onClear = viewModel::clearFilters,
                    onDismiss = { filterSheetVisible = false },
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f),
            ) {
                ContactList(
                    entries = listEntries,
                    listPresentationReady = listPresentationReady,
                    searchQuery = searchQuery,
                    filtersActive = filterState.hasActiveFilters,
                    sortField = filterState.sortField,
                    showScrollLetters = showScrollLetters,
                    onContactClick = onContactClick,
                    onImportFromDevice = importFromDevice,
                    isImporting = isImporting,
                    onPrefetchAhead = viewModel::prefetchAvatarsAhead,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ActiveFilterChipsRow(
    selectedGroups: List<String>,
    sortChipLabel: String?,
    showFavoritesOnly: Boolean,
    showHasPhone: Boolean,
    showHasEmail: Boolean,
    showHasNextcloudAccount: Boolean,
    onRemoveGroup: (String) -> Unit,
    onClearSort: () -> Unit,
    onClearShowFavorites: () -> Unit,
    onClearShowPhone: () -> Unit,
    onClearShowEmail: () -> Unit,
    onClearShowNextcloud: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sortChipLabel != null) {
            item(key = "sort_chip") {
                SmallRemovableFilterChip(
                    label = sortChipLabel,
                    onRemove = onClearSort,
                )
            }
        }
        if (showFavoritesOnly) {
            item(key = "show_favorites") {
                SmallRemovableFilterChip(label = stringResource(R.string.section_favorites), onRemove = onClearShowFavorites)
            }
        }
        if (showHasPhone) {
            item(key = "show_phone") {
                SmallRemovableFilterChip(label = stringResource(R.string.field_phone), onRemove = onClearShowPhone)
            }
        }
        if (showHasEmail) {
            item(key = "show_email") {
                SmallRemovableFilterChip(label = stringResource(R.string.chip_email), onRemove = onClearShowEmail)
            }
        }
        if (showHasNextcloudAccount) {
            item(key = "show_nextcloud") {
                SmallRemovableFilterChip(label = "Nextcloud", onRemove = onClearShowNextcloud)
            }
        }
        items(selectedGroups, key = { "group_$it" }) { group ->
            SmallRemovableFilterChip(
                label = group,
                onRemove = { onRemoveGroup(group) },
            )
        }
    }
}

@Composable
private fun SmallRemovableFilterChip(
    label: String,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_item, label),
                modifier = Modifier.size(16.dp),
            )
        },
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactList(
    entries: List<ContactListEntry>,
    listPresentationReady: Boolean,
    searchQuery: String,
    filtersActive: Boolean = false,
    sortField: ContactListSortField = ContactListSortField.DISPLAY_NAME,
    showScrollLetters: Boolean = true,
    onContactClick: (Long) -> Unit,
    onImportFromDevice: (() -> Unit)? = null,
    isImporting: Boolean = false,
    onPrefetchAhead: (List<Long>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listModifier = modifier.fillMaxSize()
    // Default nested prefetch is smaller than the avatar warm window.
    val listState = rememberLazyListState(
        prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 12) },
    )
    val contactIds = remember(entries) { contactIdsInVisualOrder(entries) }
    val contactOrdinalByLazyIndex = remember(entries) { lazyIndexToContactOrdinal(entries) }

    LaunchedEffect(listState, contactIds, contactOrdinalByLazyIndex, onPrefetchAhead) {
        if (contactIds.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible
        }
            .map { lastVisible ->
                val index = lastVisible.coerceIn(0, contactOrdinalByLazyIndex.lastIndex)
                contactOrdinalByLazyIndex[index]
            }
            .distinctUntilChanged()
            .collect { lastContactOrdinal ->
                if (lastContactOrdinal < 0) return@collect
                val from = (lastContactOrdinal + 1).coerceAtMost(contactIds.lastIndex)
                if (from > contactIds.lastIndex) return@collect
                val to = (from + AvatarBitmapCache.SCROLL_AHEAD_COUNT)
                    .coerceAtMost(contactIds.size)
                if (from < to) {
                    onPrefetchAhead(contactIds.subList(from, to))
                }
            }
    }

    // Blank until grouping finishes. Empty entries here are not "no contacts".
    if (!listPresentationReady) {
        Box(modifier = listModifier)
    } else if (entries.isEmpty()) {
        Box(
            modifier = listModifier
                .fillMaxWidth()
                .padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (searchQuery.isBlank() && !filtersActive && onImportFromDevice != null) {
                    OutlinedButton(
                        onClick = onImportFromDevice,
                        enabled = !isImporting,
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        } else {
                            Text(stringResource(R.string.import_from_device))
                        }
                    }
                } else {
                    Text(
                        text = when {
                            filtersActive -> stringResource(R.string.empty_no_filter_matches)
                            searchQuery.isBlank() -> stringResource(R.string.empty_no_contacts_yet)
                            else -> stringResource(R.string.empty_no_matches)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        val sectionIndex = remember(entries) { buildAlphabetIndexModel(entries) }

        Box(modifier = listModifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Room for the thumb. Letters are an overlay, not a column.
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = ContactListScrollbarEndPadding,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
            ) {
                items(
                    items = entries,
                    key = { it.key },
                    contentType = { it.contentType },
                ) { entry ->
                    when (entry) {
                        is ContactListEntry.FavoritesHeader -> {
                            FavoritesHeader(
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        is ContactListEntry.LetterHeader -> {
                            LetterHeader(
                                letter = entry.letter,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        is ContactListEntry.ContactRow -> {
                            val isLastInGroup = entry.indexInGroup == entry.groupSize - 1
                            val birthdayLabel = if (sortField == ContactListSortField.BIRTHDAY) {
                                contactBirthdayRowLabel(entry.contact.birthday)
                            } else {
                                null
                            }
                            GroupedContactRow(
                                displayName = entry.contact.displayName,
                                contactId = entry.contact.id,
                                thumbnail = entry.thumbnail,
                                secondaryLine = birthdayLabel,
                                indexInGroup = entry.indexInGroup,
                                groupSize = entry.groupSize,
                                onClick = { onContactClick(entry.contact.id) },
                                modifier = Modifier.padding(
                                    bottom = if (isLastInGroup) LetterGroupSpacing else 4.dp,
                                ),
                            )
                        }
                    }
                }
            }

            ContactListScrollbar(
                listState = listState,
                sectionModel = sectionIndex,
                showScrollLetters = showScrollLetters,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FavoritesHeader(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.section_favorites),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

@Composable
private fun LetterHeader(letter: String, modifier: Modifier = Modifier) {
    Text(
        text = letter,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp),
    )
}

/** One lazy item per contact. No elevation — a shadow repaints for the whole fling. */
@Composable
private fun GroupedContactRow(
    displayName: String,
    contactId: Long,
    thumbnail: ImageBitmap?,
    secondaryLine: String? = null,
    indexInGroup: Int,
    groupSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = groupItemShape(index = indexInGroup, count = groupSize),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ContactRowHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(
                contactId = contactId,
                displayName = displayName,
                size = 40.dp,
                thumbnail = thumbnail,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (secondaryLine != null) {
                    Text(
                        text = secondaryLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun groupItemShape(index: Int, count: Int): Shape {
    return when {
        count == 1 -> ShapeSingle
        index == 0 -> ShapeFirst
        index == count - 1 -> ShapeLast
        else -> ShapeMiddle
    }
}
