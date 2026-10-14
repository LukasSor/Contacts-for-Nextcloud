package com.nextcloud.contacts.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nextcloud.contacts.ContactsApp

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shows a sync notice that arrived after the screen which started the sync was left. */
@Composable
fun ObserveUiNotices(onMessage: suspend (String) -> Unit) {
    val context = LocalContext.current
    val notices = (context.applicationContext as ContactsApp).container.uiNotices
    val pending by notices.message.collectAsState()
    LaunchedEffect(pending) {
        val text = pending ?: return@LaunchedEffect
        onMessage(text)
        notices.consume()
    }
}
