package com.nextcloud.contacts.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.data.local.ContactWithDetails
import com.nextcloud.contacts.data.remote.CloudUser
import com.nextcloud.contacts.platform.ContactsProviderIntegrations
import com.nextcloud.contacts.platform.MessengerImMapping
import com.nextcloud.contacts.sync.SyncManager
import com.nextcloud.contacts.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ContactDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val contactId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L
    private val container = (application as ContactsApp).container
    private val repository = container.contactRepository

    val contact: StateFlow<ContactWithDetails?> = repository.observeContact(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Local contact id for the linked line manager, when resolvable. */
    val lineManagerContactId: StateFlow<Long?> = contact
        .map { details ->
            val c = details?.contact
            c?.managersUid.orEmpty() to c?.managersName.orEmpty()
        }
        .distinctUntilChanged()
        .mapLatest { (uid, name) ->
            if (uid.isBlank() && name.isBlank()) return@mapLatest null
            repository.findContactIdForManager(uid, name)?.takeIf { it != contactId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleFavorite() {
        viewModelScope.launch {
            val toggled = repository.toggleFavorite(contactId) ?: return@launch
            // Revert before the sync lock opens, so a push cannot upload the star we are undoing.
            container.syncManager.syncFavorite(
                contactId,
                favorite = toggled.isFavorite,
                dirtyBeforeToggle = toggled.dirtyBeforeToggle,
                lastModifiedAtToggle = toggled.lastModified,
            ) { result ->
                if (shouldRevertFavorite(result)) {
                    repository.setFavorite(contactId, isFavorite = !toggled.isFavorite, markDirty = true)
                }
            }
        }
    }

    fun linkInstantMessaging(imType: String, handle: String) {
        viewModelScope.launch {
            val added = repository.linkInstantMessaging(contactId, imType, handle)
            if (added) {
                SyncWorker.syncNow(
                    getApplication(),
                    localSave = detailFieldSaveNotifiesOnUploadFailure(),
                )
            }
        }
    }

    fun dismissMessengerLink(imType: String) {
        viewModelScope.launch {
            container.userPreferences.dismissImLinkSuggestion(
                MessengerImMapping.dismissKey(contactId, imType),
            )
        }
    }

    fun setTalkInviteVerification(userId: String, cloudId: String?) {
        viewModelScope.launch {
            repository.setTalkInviteVerification(contactId, userId, cloudId)
        }
    }

    fun clearTalkInviteVerification() {
        viewModelScope.launch {
            repository.clearTalkInviteVerification(contactId)
        }
    }

    fun setNextcloudProfileLookup(user: CloudUser) {
        viewModelScope.launch {
            repository.setNextcloudProfileLookup(contactId, user)
        }
    }

    fun clearNextcloudProfileLookup(cloudId: String) {
        viewModelScope.launch {
            repository.clearNextcloudProfileLookup(contactId, cloudId)
        }
    }

    fun setProviderIntegrationsCache(phones: List<String>, groups: List<ContactsProviderIntegrations.AppGroup>) {
        viewModelScope.launch {
            repository.setProviderIntegrationsCache(contactId, phones, groups)
        }
    }

    fun saveNotes(notes: String, notesMarkdown: Boolean) {
        viewModelScope.launch {
            repository.updateContactNotes(contactId, notes, notesMarkdown)
            SyncWorker.syncNow(
                getApplication(),
                localSave = detailFieldSaveNotifiesOnUploadFailure(),
            )
        }
    }
}

/** A failed upload of a server-linked star is undone. No account keeps the local star. */
internal fun shouldRevertFavorite(result: SyncManager.SyncResult): Boolean =
    result is SyncManager.SyncResult.Error

/** Notes and messenger links are already stored. The worker posts the upload-failure notice when this is true. */
internal fun detailFieldSaveNotifiesOnUploadFailure(): Boolean = true
