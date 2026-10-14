package com.nextcloud.contacts.ui.screens

import com.nextcloud.contacts.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextcloud.contacts.ui.util.IdentifierKeyboard
import com.nextcloud.contacts.ui.viewmodel.AccountSetupViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountSetupScreen(
    onConnected: () -> Unit,
    onSkip: () -> Unit,
    viewModel: AccountSetupViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(state.completed) {
        if (state.completed) onConnected()
    }

    LaunchedEffect(state.loginUrl) {
        val url = state.loginUrl ?: return@LaunchedEffect
        val customTabs = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
        customTabs.launchUrl(context, android.net.Uri.parse(url))
        viewModel.onLoginUrlHandled()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sign_in),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.sign_in_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::updateServerUrl,
                label = { Text(stringResource(R.string.label_nextcloud_url)) },
                placeholder = { Text(stringResource(R.string.placeholder_server)) },
                singleLine = true,
                keyboardOptions = IdentifierKeyboard.uri,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
            )
            state.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
            state.statusMessage?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedContent(
                targetState = state.isLoading,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "setupActions",
            ) { loading ->
                if (loading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        OutlinedButton(
                            onClick = viewModel::cancelLogin,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            ContainedLoadingIndicator(
                                modifier = Modifier.size(70.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.skipSetup()
                                onSkip()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_skip))
                        }
                        Button(
                            onClick = viewModel::startLogin,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.action_connect))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
