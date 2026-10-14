package com.nextcloud.contacts.ui.util

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberVcfExportDocumentAction(
    onUriReady: (Uri) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/vcard"),
    ) { uri ->
        if (uri != null) onUriReady(uri)
    }
    return remember(launcher) {
        { launcher.launch("contacts-export.vcf") }
    }
}

@Composable
fun rememberVcfImportDocumentAction(
    onTextRead: (String) -> Unit,
    onReadFailed: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                onTextRead(stream.bufferedReader().readText())
            } ?: onReadFailed()
        } catch (_: Exception) {
            onReadFailed()
        }
    }
    return remember(launcher) {
        { launcher.launch(arrayOf("text/vcard", "text/x-vcard", "text/plain", "*/*")) }
    }
}
