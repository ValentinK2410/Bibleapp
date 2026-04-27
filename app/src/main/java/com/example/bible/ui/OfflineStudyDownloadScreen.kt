package com.example.bible.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.BibleCanon
import com.example.bible.data.OfflineDownloadBookOrder
import com.example.bible.data.StudyBulkDownloader
import com.example.bible.data.TranslationId
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineStudyDownloadScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
) {
    var optChapterCommentaries by remember { mutableStateOf(true) }
    var optVerseStudy by remember { mutableStateOf(true) }
    var optApiCommentary by remember { mutableStateOf(false) }
    var apiTranslation by remember { mutableStateOf(TranslationId.SYNODAL) }

    val progress by viewModel.offlineDownload.collectAsStateWithLifecycle()
    val savedBookOrder by viewModel.offlineDownloadBookOrder.collectAsStateWithLifecycle()
    val bookOrderLocal = remember { mutableStateListOf<String>() }
    LaunchedEffect(savedBookOrder) {
        bookOrderLocal.clear()
        bookOrderLocal.addAll(savedBookOrder)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Предзагрузка для офлайна") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Тексты всех переводов уже в приложении. Здесь можно заранее скачать комментарии и материалы «Изучение» в память телефона — потом они будут доступны без интернета.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.offline_download_skip_cached),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Загрузка может занять много часов и трафика. Лучше Wi‑Fi и зарядка. Не закрывайте приложение и по возможности не блокируйте экран на всё время (система может остановить фон).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            OfflineDownloadBookOrderCard(
                orderList = bookOrderLocal,
                onSetOrder = { viewModel.setOfflineDownloadBookOrder(it) },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = optChapterCommentaries,
                    onCheckedChange = { optChapterCommentaries = it },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Комментарии к главам (studybible.ru)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "~${StudyBulkDownloader.estimateChapterCommentaryRequests()} запросов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = optVerseStudy,
                    onCheckedChange = { optVerseStudy = it },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Сравнение переводов, параллельные ссылки, Стронг (по каждому стиху)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "~${StudyBulkDownloader.estimateVerseStudyRequests()} запросов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = optApiCommentary,
                    onCheckedChange = { optApiCommentary = it },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Комментарии к стихам (внешний API, один выбранный перевод)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "~31 000 запросов на перевод",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (optApiCommentary) {
                Spacer(Modifier.height(8.dp))
                Text("Перевод для API:", style = MaterialTheme.typography.labelMedium)
                TranslationId.entries.forEach { tid ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = apiTranslation == tid,
                            onClick = { apiTranslation = tid },
                        )
                        Text(tid.labelRu, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        viewModel.startOfflineDownload(
                            chapterCommentaries = optChapterCommentaries,
                            verseStudyTools = optVerseStudy,
                            apiCommentary = optApiCommentary,
                            apiTranslation = apiTranslation,
                            bookOrder = bookOrderLocal.toList(),
                        )
                    },
                    enabled = progress?.running != true &&
                        (optChapterCommentaries || optVerseStudy || optApiCommentary),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Начать загрузку")
                }
                OutlinedButton(
                    onClick = { viewModel.cancelOfflineDownload() },
                    enabled = progress?.running == true,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Стоп")
                }
            }

            progress?.let { p ->
                Spacer(Modifier.height(20.dp))
                Text(p.phase, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (p.running && p.total > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { p.current.toFloat() / p.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${p.current} / ${p.total}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (p.detail.isNotBlank()) {
                    Text(
                        p.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!p.running && p.phase == "Готово") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.dismissOfflineDownloadUi() }) {
                        Text("Закрыть сообщение")
                    }
                }
                if (!p.running && (p.phase == "Ошибка" || p.phase == "Отменено")) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.dismissOfflineDownloadUi() }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfflineDownloadBookOrderCard(
    orderList: MutableList<String>,
    onSetOrder: (List<String>) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        orderList.apply { add(to.index, removeAt(from.index)) }
        onSetOrder(orderList.toList())
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun applyPreset(preset: () -> List<String>) {
        val next = preset()
        orderList.clear()
        orderList.addAll(next)
        onSetOrder(next)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Порядок книг",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Книги вверху списка загружаются раньше, внизу — позже. Перетащите за ручку с «⋮⋮».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = { applyPreset { OfflineDownloadBookOrder.presetGospelsFirst() } },
                    label = { Text("Евангелия первыми") },
                )
                AssistChip(
                    onClick = { applyPreset { OfflineDownloadBookOrder.presetNewTestamentFirst() } },
                    label = { Text("НЗ первым") },
                )
                AssistChip(
                    onClick = { applyPreset { OfflineDownloadBookOrder.presetOldTestamentFirst() } },
                    label = { Text("ВЗ первым") },
                )
                AssistChip(
                    onClick = { applyPreset { OfflineDownloadBookOrder.defaultOrder() } },
                    label = { Text("Канон") },
                )
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(orderList, key = { it }) { bookId ->
                    ReorderableItem(reorderableLazyListState, key = bookId) { isDragging ->
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 6.dp else 0.dp,
                            label = "drag",
                        )
                        val label = BibleCanon.byId(bookId)?.abbrRu ?: bookId
                        Surface(
                            tonalElevation = elevation,
                            shadowElevation = elevation,
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                )
                                IconButton(
                                    modifier = Modifier.draggableHandle(),
                                    onClick = {},
                                ) {
                                    Icon(
                                        Icons.Filled.DragHandle,
                                        contentDescription = "Переместить",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
