package com.nextcloud.contacts.ui.viewmodel

import android.app.Application
import android.util.TypedValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.device.importDeviceContacts
import com.nextcloud.contacts.data.local.ContactListItem
import com.nextcloud.contacts.sync.SyncManager
import com.nextcloud.contacts.ui.list.ContactListEntry
import com.nextcloud.contacts.ui.list.attachCachedThumbnails
import com.nextcloud.contacts.ui.list.buildContactListEntries
import com.nextcloud.contacts.ui.list.buildFlatPreviewContactListEntries
import com.nextcloud.contacts.ui.list.contactIdsInVisualOrder
import com.nextcloud.contacts.util.AvatarBitmapCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ContactListViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ContactsApp).container
    private val repository = container.contactRepository
    private val syncManager = container.syncManager
    private val listAvatarSizePx: Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            AvatarBitmapCache.LIST_AVATAR_DP.toFloat(),
            application.resources.displayMetrics,
        ).toInt().coerceAtLeast(1)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(ContactListFilterState())
    val filterState: StateFlow<ContactListFilterState> = _filterState.asStateFlow()

    val availableGroups: StateFlow<List<String>> = repository.observeAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** False until the first Room emission. Splash reads this; the empty-list UI does not. */
    private val _isContactsReady = MutableStateFlow(false)
    val isContactsReady: StateFlow<Boolean> = _isContactsReady.asStateFlow()

    /** False while rows are still grouping. The initial empty [listEntries] is not "no contacts". */
    private val _listPresentationReady = MutableStateFlow(false)
    val listPresentationReady: StateFlow<Boolean> = _listPresentationReady.asStateFlow()

    private var extendedWarmJob: Job? = null
    private val extendedWarmScheduled = AtomicBoolean(false)

    /** Empty query is not delayed. A non-empty query waits 150ms. */
    private val debouncedSearchQuery = _searchQuery.debounce { query ->
        if (query.isBlank()) 0L else 150L
    }

    /** Re-subscribes when search changes. Filter and sort do not run a new query. */
    private val contactListRows: StateFlow<List<ContactListItem>> = debouncedSearchQuery
        .flatMapLatest { query -> repository.observeContactList(query) }
        .distinctUntilChanged { a, b -> listSnapshotEquals(a, b) }
        .onEach { rows ->
            _isContactsReady.value = true
            if (rows.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    AvatarBitmapCache.prefetch(
                        photoStore = container.contactPhotoStore,
                        contactIds = rows.take(AvatarBitmapCache.SPLASH_PREFETCH_COUNT).map { it.id },
                        sizePx = listAvatarSizePx,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listEntries: StateFlow<List<ContactListEntry>> = combine(
        contactListRows,
        _filterState,
    ) { contacts, filters ->
        applyContactListFilters(contacts, filters) to filters
    }
        .flatMapLatest { (contacts, filters) ->
            flow {
                if (contacts.isEmpty()) {
                    emit(emptyList())
                    return@flow
                }
                val canFlatPreview = filters.isDefaultSort && !filters.hasActiveFilters
                if (canFlatPreview) {
                    emit(
                        attachCachedThumbnails(
                            entries = buildFlatPreviewContactListEntries(contacts),
                            sizePx = listAvatarSizePx,
                            limit = AvatarBitmapCache.SPLASH_PREFETCH_COUNT,
                        ),
                    )
                }
                var entries = buildContactListEntries(
                    contacts,
                    filters.sortField,
                    filters.sortDirection,
                )
                entries = attachCachedThumbnails(
                    entries = entries,
                    sizePx = listAvatarSizePx,
                )
                emit(entries)
            }
        }
        .flowOn(Dispatchers.IO)
        .onEach { entries ->
            _listPresentationReady.value = true
            if (entries.isNotEmpty() &&
                extendedWarmScheduled.compareAndSet(false, true)
            ) {
                scheduleExtendedAvatarWarm(entries)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            container.contactDatesCalendarCoordinator.nextcloudCalendarFailures.collect {
                _message.value = str(R.string.msg_nextcloud_calendar_failed)
            }
        }
        viewModelScope.launch {
            isContactsReady.filter { it }.first()
            val loginFailure = container.uiNotices.takeLoginSyncFailure()
            if (loginFailure != null) _message.value = loginFailure
            // Cold start syncs once from ContactsApp.
            val startupSyncPlanned = (getApplication() as ContactsApp).awaitStartupSyncPlanned()
            if (
                !startupSyncPlanned &&
                loginFailure == null &&
                container.credentialStore.isLoggedIn() &&
                container.contactRepository.countServerLinked() == 0
            ) {
                _message.value = str(R.string.msg_connected_no_contacts)
            }
        }
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun toggleGroupFilter(group: String) {
        _filterState.update { state ->
            val has = state.selectedGroups.any { it.equals(group, ignoreCase = true) }
            val next = if (has) {
                state.selectedGroups.filterNot { it.equals(group, ignoreCase = true) }.toSet()
            } else {
                state.selectedGroups + group
            }
            state.copy(selectedGroups = next)
        }
    }

    fun setGroupFilterOnly(group: String) {
        _filterState.update { it.copy(selectedGroups = setOf(group)) }
    }

    fun removeGroupFilter(group: String) {
        _filterState.update { state ->
            state.copy(
                selectedGroups = state.selectedGroups.filterNot {
                    it.equals(group, ignoreCase = true)
                }.toSet(),
            )
        }
    }

    fun setSortField(sortField: ContactListSortField) {
        _filterState.update { it.copy(sortField = sortField) }
    }

    fun setSortDirection(sortDirection: ContactListSortDirection) {
        _filterState.update { it.copy(sortDirection = sortDirection) }
    }

    fun resetSortToDefault() {
        _filterState.update {
            it.copy(
                sortField = ContactListSortField.DISPLAY_NAME,
                sortDirection = ContactListSortDirection.ASCENDING,
            )
        }
    }

    fun setShowFavoritesOnly(enabled: Boolean) {
        _filterState.update { it.copy(showFavoritesOnly = enabled) }
    }

    fun setShowHasPhone(enabled: Boolean) {
        _filterState.update { it.copy(showHasPhone = enabled) }
    }

    fun setShowHasEmail(enabled: Boolean) {
        _filterState.update { it.copy(showHasEmail = enabled) }
    }

    fun setShowHasNextcloudAccount(enabled: Boolean) {
        _filterState.update { it.copy(showHasNextcloudAccount = enabled) }
    }

    fun clearFilters() {
        _filterState.value = ContactListFilterState()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = syncManager.refreshFromServer()
            when (result) {
                is SyncManager.SyncResult.Success -> {
                    _message.value = result.warning ?: when {
                        result.pulled > 0 || result.pushed > 0 ->
                            str(R.string.msg_synced_counts, result.pulled, result.pushed)
                        container.contactRepository.countServerLinked() == 0 ->
                            str(R.string.msg_no_contacts_on_nextcloud)
                        else -> str(R.string.msg_up_to_date)
                    }
                }
                is SyncManager.SyncResult.Error ->
                    _message.value = result.message
                is SyncManager.SyncResult.NotLoggedIn ->
                    _message.value = str(R.string.msg_not_connected)
            }
            _isRefreshing.value = false
        }
    }

    fun importFromDevice() {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                _message.value = importDeviceContacts(
                    getApplication(),
                    container.deviceContactsImporter,
                    syncManager,
                )
            } catch (e: SecurityException) {
                _message.value = str(R.string.msg_contacts_permission)
            } catch (e: Exception) {
                _message.value = str(
                    R.string.msg_import_failed,
                    e.message ?: str(R.string.msg_unknown_error),
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    /** Safe from composition. Work runs on [Dispatchers.IO]. */
    fun prefetchAvatarsAhead(contactIds: List<Long>) {
        if (contactIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            AvatarBitmapCache.prefetch(
                photoStore = container.contactPhotoStore,
                contactIds = contactIds,
                sizePx = listAvatarSizePx,
            )
        }
    }

    /** Fills [AvatarBitmapCache] only. Does not re-emit the list. */
    private fun scheduleExtendedAvatarWarm(entries: List<ContactListEntry>) {
        extendedWarmJob?.cancel()
        extendedWarmJob = viewModelScope.launch(Dispatchers.IO) {
            prefetchListAvatars(entries, AvatarBitmapCache.EXTENDED_PREFETCH_COUNT)
        }
    }

    private fun prefetchListAvatars(entries: List<ContactListEntry>, limit: Int) {
        val ids = contactIdsInVisualOrder(entries).take(limit)
        if (ids.isEmpty()) return
        AvatarBitmapCache.prefetch(
            photoStore = container.contactPhotoStore,
            contactIds = ids,
            sizePx = listAvatarSizePx,
        )
    }

    private fun listSnapshotEquals(a: List<ContactListItem>, b: List<ContactListItem>): Boolean {
        if (a === b) return true
        if (a.size != b.size) return false
        for (i in a.indices) {
            val left = a[i]
            val right = b[i]
            if (left.id != right.id ||
                left.isFavorite != right.isFavorite ||
                left.displayName != right.displayName ||
                left.givenName != right.givenName ||
                left.familyName != right.familyName ||
                left.nickname != right.nickname ||
                left.organization != right.organization ||
                left.categories != right.categories ||
                left.birthday != right.birthday ||
                left.hasPhone != right.hasPhone ||
                left.hasEmail != right.hasEmail ||
                left.hasNextcloudAccount != right.hasNextcloudAccount
            ) {
                return false
            }
        }
        return true
    }
}
