package com.nextcloud.contacts.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.R
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ContactsApp
        val result = app.container.syncManager.sync()
        if (inputData.getBoolean(KEY_LOCAL_SAVE, false) && result is SyncManager.SyncResult.Error) {
            app.container.uiNotices.post(
                applicationContext.getString(R.string.msg_saved_locally_upload_failed),
            )
        }
        if (inputData.getBoolean(KEY_REPORT_OUTCOME, false)) {
            reportStartupOutcome(app, result)
        }
        return when (SyncRetryPolicy.workAction(result)) {
            SyncRetryPolicy.WorkAction.SUCCEED -> Result.success()
            SyncRetryPolicy.WorkAction.RETRY -> Result.retry()
            SyncRetryPolicy.WorkAction.FAIL -> Result.failure()
        }
    }

    private suspend fun reportStartupOutcome(app: ContactsApp, result: SyncManager.SyncResult) {
        val notices = app.container.uiNotices
        when (result) {
            is SyncManager.SyncResult.Error -> {
                // A later WorkManager retry should not repeat the same failure snackbar.
                if (runAttemptCount == 0) notices.post(result.message)
            }
            is SyncManager.SyncResult.Success -> {
                val warning = result.warning
                if (warning != null) {
                    notices.post(warning)
                } else if (
                    result.pulled == 0 &&
                    app.container.credentialStore.isLoggedIn() &&
                    app.container.contactRepository.countServerLinked() == 0
                ) {
                    notices.post(applicationContext.getString(R.string.msg_connected_no_contacts))
                }
            }
            is SyncManager.SyncResult.NotLoggedIn -> Unit
        }
    }

    companion object {
        private const val WORK_NAME = "contacts_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(4, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun syncNow(context: Context, localSave: Boolean = false, reportOutcome: Boolean = false) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(
                    androidx.work.workDataOf(
                        KEY_LOCAL_SAVE to localSave,
                        KEY_REPORT_OUTCOME to reportOutcome,
                    ),
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        private const val KEY_LOCAL_SAVE = "local_save"
        private const val KEY_REPORT_OUTCOME = "report_outcome"
    }
}
