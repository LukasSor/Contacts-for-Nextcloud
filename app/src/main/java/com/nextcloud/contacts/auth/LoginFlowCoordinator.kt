package com.nextcloud.contacts.auth

import android.app.Application
import com.nextcloud.contacts.R
import com.nextcloud.contacts.UiNotices
import com.nextcloud.contacts.data.prefs.UserPreferences
import com.nextcloud.contacts.sync.SyncManager
import com.nextcloud.contacts.util.HttpStatuses
import com.nextcloud.contacts.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginFlowUiState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val loginUrl: String? = null,
    val statusMessage: String? = null,
    val error: String? = null,
    val completed: Boolean = false,
)

class LoginFlowCoordinator(
    private val application: Application,
    private val loginFlowService: LoginFlowService,
    private val sessionStore: LoginSessionStore,
    private val credentialStore: CredentialStore,
    private val userPreferences: UserPreferences,
    private val syncManager: SyncManager,
    private val uiNotices: UiNotices,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    private val _state = MutableStateFlow(LoginFlowUiState())
    val state: StateFlow<LoginFlowUiState> = _state.asStateFlow()

    init {
        // A new process drops a login that was still polling.
        cancelPendingLogin()
    }

    fun updateServerUrl(url: String) {
        _state.value = _state.value.copy(serverUrl = url, error = null)
    }

    fun startLogin() {
        val url = _state.value.serverUrl.trim()
        if (url.isEmpty()) {
            _state.value = _state.value.copy(error = application.getString(R.string.login_enter_url))
            return
        }
        pollJob?.cancel()
        scope.launch {
            try {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    statusMessage = application.getString(R.string.login_connecting),
                    completed = false,
                )
                val session = loginFlowService.startLogin(url)
                sessionStore.save(
                    LoginSessionStore.PendingLoginSession(
                        pollUrl = session.pollUrl,
                        pollToken = session.pollToken,
                        serverUrl = session.resolvedServerUrl,
                        startedAt = System.currentTimeMillis(),
                    ),
                )
                _state.value = _state.value.copy(
                    serverUrl = session.resolvedServerUrl,
                    loginUrl = session.loginUrl,
                    statusMessage = application.getString(R.string.login_sign_in_browser),
                )
                beginPolling(
                    pollUrl = session.pollUrl,
                    pollToken = session.pollToken,
                    userServerUrl = session.resolvedServerUrl,
                )
            } catch (e: Exception) {
                sessionStore.clear()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = friendlyError(e),
                    statusMessage = null,
                )
            }
        }
    }

    fun cancelLogin() {
        cancelPendingLogin()
    }

    fun onLoginUrlHandled() {
        _state.value = _state.value.copy(loginUrl = null)
    }

    private fun cancelPendingLogin() {
        pollJob?.cancel()
        pollJob = null
        val pendingServer = sessionStore.load()?.serverUrl
        sessionStore.clear()
        if (credentialStore.isLoggedIn()) return
        _state.value = LoginFlowUiState(
            serverUrl = pendingServer ?: _state.value.serverUrl,
        )
    }

    private fun beginPolling(pollUrl: String, pollToken: String, userServerUrl: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            try {
                _state.value = _state.value.copy(
                    statusMessage = application.getString(R.string.login_complete_browser),
                )
                val polled = loginFlowService.pollForCredentials(
                    pollUrl = pollUrl,
                    pollToken = pollToken,
                )
                val credentials = ServerUrlNormalizer.credentialsFromPoll(
                    userServerUrl = userServerUrl,
                    pollServer = polled.server,
                    loginName = polled.loginName,
                    appPassword = polled.appPassword,
                )
                credentialStore.saveCredentials(credentials)
                // Done is the only action that marks setup complete. A new login shows it again.
                userPreferences.setPostLoginSetupComplete(false)
                sessionStore.clear()

                _state.value = _state.value.copy(statusMessage = application.getString(R.string.login_syncing))
                val syncFailure = when (val syncResult = syncManager.syncAfterLogin()) {
                    is SyncManager.SyncResult.Error -> application.getString(
                        R.string.login_connected_sync_failed,
                        syncResult.message,
                    )
                    else -> null
                }
                if (syncFailure != null) {
                    uiNotices.reportLoginSyncFailure(syncFailure)
                }
                SyncWorker.schedule(application)
                _state.value = _state.value.copy(
                    isLoading = false,
                    completed = true,
                    statusMessage = syncFailure,
                    error = null,
                )
            } catch (e: Exception) {
                // Cancellation is not a login failure.
                if (e is kotlinx.coroutines.CancellationException) throw e
                sessionStore.clear()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = friendlyError(e),
                    statusMessage = null,
                )
            }
        }
    }

    private fun friendlyError(e: Exception): String {
        val message = e.message.orEmpty()
        val http = HttpStatuses.codeString(message)
        return when {
            message.contains("Enter your Nextcloud URL", ignoreCase = true) ->
                application.getString(R.string.login_enter_url)
            message.contains("Cannot reach Nextcloud", ignoreCase = true) ||
                message.contains("Unable to resolve host", ignoreCase = true) ->
                application.getString(R.string.login_cannot_reach)
            message.contains("Failed to connect", ignoreCase = true) ||
                message.contains("Connection refused", ignoreCase = true) ->
                application.getString(R.string.login_cannot_connect)
            message.contains("timeout", ignoreCase = true) ->
                application.getString(R.string.login_timed_out)
            message.contains("Login timed out", ignoreCase = true) ->
                application.getString(R.string.login_approval_timed_out)
            http != null ->
                application.getString(R.string.login_http_failed, http)
            message.contains("Empty login response", ignoreCase = true) ||
                message.contains("Empty poll response", ignoreCase = true) ->
                application.getString(R.string.login_failed)
            else -> message.ifBlank { application.getString(R.string.login_failed) }
        }
    }
}
