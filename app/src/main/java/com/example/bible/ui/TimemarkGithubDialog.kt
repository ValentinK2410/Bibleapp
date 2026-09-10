package com.example.bible.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.BibleCanon
import com.example.bible.data.TimemarkGithubCatalog
import com.example.bible.data.TimemarkGithubDownloadResult
import com.example.bible.data.TimemarkGithubIndex
import com.example.bible.data.TimemarkGithubItem
import com.example.bible.data.TranslationId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TimemarkGithubDialog(
    translationCode: String,
    bookId: String,
    chapter: Int,
    onDismiss: () -> Unit,
    onImported: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf<TimemarkGithubIndex?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    fun reload() {
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { TimemarkGithubCatalog.fetchCatalog() }
            }
            loading = false
            result.onSuccess { index = it }
                .onFailure {
                    error = it.message ?: context.getString(R.string.timemark_github_load_failed)
                }
        }
    }

    LaunchedEffect(Unit) { reload() }

    fun download(items: List<TimemarkGithubItem>) {
        if (items.isEmpty()) {
            Toast.makeText(context, R.string.timemark_github_none_for_filter, Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                TimemarkGithubCatalog.downloadItems(context, items)
            }
            busy = false
            when (result) {
                is TimemarkGithubDownloadResult.Ok -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.timemark_github_ok, result.imported),
                        Toast.LENGTH_LONG,
                    ).show()
                    onImported()
                    onDismiss()
                }
                is TimemarkGithubDownloadResult.Err -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val items = index?.items.orEmpty()
    val chapterItems = items.filter {
        it.translationCode.equals(translationCode, ignoreCase = true) &&
            it.bookId.equals(bookId, ignoreCase = true) &&
            it.chapter == chapter
    }
    val bookItems = items.filter {
        it.bookId.equals(bookId, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.timemark_github_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.timemark_github_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { reload() }, enabled = !busy) {
                            Text(stringResource(R.string.timemark_github_retry))
                        }
                    }
                    items.isEmpty() -> {
                        Text(stringResource(R.string.timemark_github_empty))
                    }
                    else -> {
                        val installedIds = remember(index) {
                            items.filter { TimemarkGithubCatalog.isInstalled(context, it) }
                                .map { it.id }
                                .toSet()
                        }
                        if (!index?.updatedAt.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.timemark_github_updated, index!!.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items.take(40).forEach { item ->
                            val installed = item.id in installedIds
                            val bookName = BibleCanon.byId(item.bookId)?.nameRu ?: item.bookId
                            val tr = TranslationId.fromCode(item.translationCode).shortLabel
                            Text(
                                buildString {
                                    append(item.title.ifBlank { "$bookName ${item.chapter}" })
                                    append(" · ")
                                    append(tr)
                                    append(" · ")
                                    append(context.getString(R.string.timemark_github_cues, item.cueCount))
                                    if (installed) {
                                        append(" · ")
                                        append(context.getString(R.string.timemark_github_installed))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                        if (items.size > 40) {
                            Text(
                                stringResource(R.string.timemark_github_more, items.size - 40),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = { download(chapterItems) },
                    enabled = !loading && !busy && chapterItems.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.timemark_github_download_chapter))
                }
                TextButton(
                    onClick = { download(bookItems) },
                    enabled = !loading && !busy && bookItems.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.timemark_github_download_book))
                }
                TextButton(
                    onClick = { download(items) },
                    enabled = !loading && !busy && items.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.timemark_github_download_all))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.timemark_close))
            }
        },
    )
}
