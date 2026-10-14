package com.nextcloud.contacts

import android.app.Application
import com.nextcloud.contacts.sync.SyncWorker
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ContactsApp : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Sync waits on this. Completed from the first list frame, or from login/setup. */
    private val uiInteractive = CompletableDeferred<Unit>()
    private val uiInteractiveSignalled = AtomicBoolean(false)

    /** True when this process already queued the one startup sync. */
    private val startupSyncPlanned = CompletableDeferred<Boolean>()

    fun markUiInteractive() {
        if (uiInteractiveSignalled.compareAndSet(false, true)) {
            uiInteractive.complete(Unit)
        }
    }

    /** Cold start waits so the list does not start a second sync. False when login already synced. */
    suspend fun awaitStartupSyncPlanned(): Boolean = startupSyncPlanned.await()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            uiInteractive.await()
            SyncWorker.schedule(this@ContactsApp)
        }
        applicationScope.launch {
            uiInteractive.await()
            container.contactDatesCalendarCoordinator.syncAll()
        }
        applicationScope.launch {
            // One automatic sync, and only after a screen is up. The contact list does not start another.
            var planned = false
            try {
                uiInteractive.await()
                val server = container.credentialStore.getCredentials()?.server ?: return@launch
                container.contactRepository.deduplicateLocalContacts(server)
                SyncWorker.syncNow(this@ContactsApp, reportOutcome = true)
                planned = true
            } finally {
                if (!startupSyncPlanned.isCompleted) {
                    startupSyncPlanned.complete(planned)
                }
            }
        }
    }
}
