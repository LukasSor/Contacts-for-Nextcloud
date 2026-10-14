package com.nextcloud.contacts.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.auth.LoginFlowUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AccountSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ContactsApp).container
    private val coordinator = container.loginFlowCoordinator

    val state: StateFlow<LoginFlowUiState> = coordinator.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoginFlowUiState())

    fun updateServerUrl(url: String) = coordinator.updateServerUrl(url)
    fun startLogin() = coordinator.startLogin()
    fun cancelLogin() = coordinator.cancelLogin()
    fun onLoginUrlHandled() = coordinator.onLoginUrlHandled()

    fun skipSetup() {
        container.userPreferences.setSetupSkipped(true)
    }
}
