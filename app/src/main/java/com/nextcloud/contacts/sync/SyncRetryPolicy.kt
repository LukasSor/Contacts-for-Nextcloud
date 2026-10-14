package com.nextcloud.contacts.sync

import com.nextcloud.contacts.util.HttpStatuses

/**
 * WorkManager should back off on network and 5xx. Auth failures stay failed
 * so a logged-out or rejected account does not retry forever.
 */
object SyncRetryPolicy {
    enum class WorkAction { SUCCEED, RETRY, FAIL }

    fun workAction(result: SyncManager.SyncResult): WorkAction = when (result) {
        is SyncManager.SyncResult.Success -> WorkAction.SUCCEED
        is SyncManager.SyncResult.NotLoggedIn -> WorkAction.SUCCEED
        is SyncManager.SyncResult.Error ->
            if (isAuthFailure(result.message)) WorkAction.FAIL else WorkAction.RETRY
    }

    fun isAuthFailure(message: String?): Boolean {
        val text = message.orEmpty()
        val http = HttpStatuses.code(text)
        if (http == 401 || http == 403) return true
        return text.contains("Unauthorized", ignoreCase = true) ||
            text.contains("Not logged in", ignoreCase = true) ||
            text.contains("Login failed", ignoreCase = true)
    }

    /** Transport and 5xx can succeed later. Auth failures are not retried. */
    fun isTransientFailure(message: String?): Boolean {
        if (isAuthFailure(message)) return false
        val http = HttpStatuses.code(message)
        if (http != null) return http == 408 || http == 429 || http >= 500
        return true
    }

    /**
     * The vCard upload already succeeded. Any favorite error except 401/403 stays dirty
     * so a later sync retries the star. Auth failures must not loop forever.
     */
    fun shouldMarkDirtyAfterFavoriteFailure(message: String?): Boolean {
        if (message == null) return false
        return !isAuthFailure(message)
    }
}
