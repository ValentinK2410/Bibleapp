package com.example.bible.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.PortableCatalogItem
import com.example.bible.data.PortableContentCatalog
import com.example.bible.data.PortableExchange
import com.example.bible.data.RemovableStorageMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class OpenDocumentTreeWithWritePortable : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: android.content.Context, input: Uri?): Intent {
        return super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
}

@Composable
fun PortableExchangeHost(viewModel: BibleViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageEvent by RemovableStorageMonitor.events.collectAsStateWithLifecycle()
    val installed by viewModel.portableInstalledVersions.collectAsStateWithLifecycle()

    var usbPromptOpen by remember { mutableStateOf(false) }
    var usbLabel by remember { mutableStateOf("") }
    var serverUpdates by remember { mutableStateOf<List<PortableCatalogItem>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var serverChecked by remember { mutableStateOf(false) }

    LaunchedEffect(storageEvent) {
        val ev = storageEvent ?: return@LaunchedEffect
        if (ev.mounted) {
            usbLabel = ev.label
            usbPromptOpen = true
        }
        RemovableStorageMonitor.consumeEvent()
    }

    LaunchedEffect(Unit) {
        if (serverChecked) return@LaunchedEffect
        serverChecked = true
        withContext(Dispatchers.IO) {
            runCatching {
                val catalog = PortableContentCatalog.fetchCatalog()
                PortableContentCatalog.itemsNewerThan(catalog, installed)
            }
        }.onSuccess { newer ->
            if (newer.isNotEmpty()) serverUpdates = newer
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentTreeWithWritePortable(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
        scope.launch {
            busy = context.getString(R.string.portable_export_busy)
            val ok = viewModel.exportPortableUserContentToUsb(treeUri) { done, total ->
                busy = context.getString(R.string.portable_copy_progress, done / 1_000_000, total / 1_000_000)
            }
            busy = null
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.portable_usb_saved_ok)
                else context.getString(R.string.share_app_saved_to_folder_fail),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    if (usbPromptOpen) {
        AlertDialog(
            onDismissRequest = { usbPromptOpen = false },
            title = { Text(stringResource(R.string.portable_usb_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.portable_usb_dialog_body, usbLabel))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.portable_usb_dialog_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        usbPromptOpen = false
                        treeLauncher.launch(null)
                    },
                ) { Text(stringResource(R.string.portable_usb_dialog_export)) }
            },
            dismissButton = {
                TextButton(onClick = { usbPromptOpen = false }) {
                    Text(stringResource(R.string.song_share_pick_cancel))
                }
            },
        )
    }

    serverUpdates?.let { items ->
        AlertDialog(
            onDismissRequest = { serverUpdates = null },
            title = { Text(stringResource(R.string.portable_server_updates_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.portable_server_updates_body))
                    Spacer(Modifier.height(12.dp))
                    items.forEach { item ->
                        Text("• ${item.title} (версия ${item.version})")
                        Text(
                            PortableExchange.formatModuleSummary(item.moduleVersions),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val list = items
                        serverUpdates = null
                        scope.launch {
                            busy = context.getString(R.string.portable_download_busy)
                            for (item in list) {
                                viewModel.downloadPortableCatalogItem(item) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                            busy = null
                        }
                    },
                ) { Text(stringResource(R.string.portable_server_download)) }
            },
            dismissButton = {
                TextButton(onClick = { serverUpdates = null }) {
                    Text(stringResource(R.string.portable_server_later))
                }
            },
        )
    }

    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(msg) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
        )
    }
}
