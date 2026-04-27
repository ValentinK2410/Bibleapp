package com.example.bible.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.BiblePreferences
import com.example.bible.data.QuranAyahAudioStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherBooksScreen(
    preferences: BiblePreferences,
    onBack: () -> Unit,
    onOpenQuran: () -> Unit,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val scope = rememberCoroutineScope()
    val quranRepo = rememberQuranRepository()
    var quranBulkDownload: QuranBulkAyahDownloadUi? by remember { mutableStateOf(null) }
    var showDownloadAllConfirm: Boolean by remember { mutableStateOf(false) }
    var downloadEntireQuranJob: Job? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose { downloadEntireQuranJob?.cancel() }
    }

    val quranTextScale by preferences.quranReaderTextScale.collectAsStateWithLifecycle(
        initialValue = BiblePreferences.QURAN_READER_TEXT_SCALE_DEFAULT,
    )
    var sliderValue by remember(quranTextScale) { mutableFloatStateOf(quranTextScale) }
    LaunchedEffect(quranTextScale) {
        sliderValue = quranTextScale
    }
    val arabicWordByWord by preferences.quranArabicWordByWordTts.collectAsStateWithLifecycle(initialValue = false)

    val isBulkRunning = quranBulkDownload is QuranBulkAyahDownloadUi.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.other_books_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                onClick = onOpenQuran,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.other_books_quran_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.other_books_quran_card_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Text(
                stringResource(R.string.other_books_reader_settings_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.other_books_quran_text_scale_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(
                            R.string.other_books_quran_text_scale_value,
                            sliderValue * 100f,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            scope.launch {
                                preferences.setQuranReaderTextScale(sliderValue)
                            }
                        },
                        valueRange = BiblePreferences.QURAN_READER_TEXT_SCALE_MIN..BiblePreferences.QURAN_READER_TEXT_SCALE_MAX,
                        steps = 19,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                    Text(
                        stringResource(R.string.other_books_quran_text_scale_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.other_books_quran_word_tts_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.other_books_quran_word_tts_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Switch(
                            checked = arabicWordByWord,
                            onCheckedChange = { v ->
                                scope.launch { preferences.setQuranArabicWordByWordTts(v) }
                            },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    FilledTonalButton(
                        onClick = {
                            if (quranRepo.loadIndex() == null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.quran_assets_missing),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                showDownloadAllConfirm = true
                            }
                        },
                        enabled = !isBulkRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.other_books_quran_download_all_button))
                    }
                }
            }

            Text(
                text = stringResource(R.string.other_books_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDownloadAllConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!isBulkRunning) {
                    showDownloadAllConfirm = false
                }
            },
            title = { Text(stringResource(R.string.other_books_quran_download_all_title)) },
            text = { Text(stringResource(R.string.other_books_quran_download_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadAllConfirm = false
                        val index = quranRepo.loadIndex()
                        if (index == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.quran_assets_missing),
                                Toast.LENGTH_LONG,
                            ).show()
                            return@TextButton
                        }
                        val total = index.sumOf { it.totalVerses }
                        if (total <= 0) {
                            return@TextButton
                        }
                        quranBulkDownload = QuranBulkAyahDownloadUi.Running(0, total, 0)
                        downloadEntireQuranJob = scope.launch {
                            try {
                                val saved = QuranAyahAudioStorage.downloadEntireQuranAlafasyMp3(
                                    appCtx,
                                    quranRepo,
                                ) { p, t, s ->
                                    withContext(Dispatchers.Main) {
                                        quranBulkDownload =
                                            QuranBulkAyahDownloadUi.Running(p, t, s)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    quranBulkDownload = QuranBulkAyahDownloadUi.Finished(saved, total)
                                }
                            } catch (c: CancellationException) {
                                withContext(Dispatchers.Main) { quranBulkDownload = null }
                                throw c
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.other_books_quran_download_all_start))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDownloadAllConfirm = false },
                    enabled = !isBulkRunning,
                ) {
                    Text(stringResource(R.string.other_books_quran_download_all_cancel))
                }
            },
        )
    }

    val bulk = quranBulkDownload
    if (bulk != null) {
        val finished = bulk is QuranBulkAyahDownloadUi.Finished
        AlertDialog(
            onDismissRequest = { if (finished) quranBulkDownload = null },
            properties = DialogProperties(
                dismissOnBackPress = finished,
                dismissOnClickOutside = finished,
            ),
            title = { Text(stringResource(R.string.other_books_quran_download_all_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (bulk) {
                        is QuranBulkAyahDownloadUi.Running -> {
                            val frac = if (bulk.total > 0) {
                                bulk.processed.toFloat() / bulk.total.toFloat()
                            } else {
                                0f
                            }
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(
                                    R.string.quran_download_all_ayahs_processing,
                                    bulk.processed,
                                    bulk.total,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.quran_download_all_ayahs_remaining,
                                    (bulk.total - bulk.processed).coerceAtLeast(0),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.quran_download_all_ayahs_saved_now,
                                    bulk.savedOk,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is QuranBulkAyahDownloadUi.Finished -> {
                            Text(
                                stringResource(
                                    R.string.quran_menu_download_all_ayahs_done,
                                    bulk.savedOk,
                                    bulk.total,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (bulk is QuranBulkAyahDownloadUi.Finished) {
                    TextButton(onClick = { quranBulkDownload = null }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
        )
    }
}
