package com.example.bible.ui.housecare

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.housecare.CleaningTask
import com.example.bible.ui.theme.OutlinedTextField
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private fun suggestedSectionsForZone(zoneName: String): List<String> {
    val lower = zoneName.lowercase()
    return when {
        isFifteenMinuteZoneName(zoneName) -> listOf(
            "Разобрать вещи на местах",
            "Протереть поверхности",
            "Кухонная столешница и раковина",
            "Зеркало и раковина в ванной",
            "Прихожая и обувь",
            "Быстро пропылесосить / пол",
            "Диван, пледы и подушки",
            "Пыль на видных местах",
        )
        "кухн" in lower -> listOf(
            "Плита",
            "Раковина",
            "Столешница",
            "Фасады",
            "Холодильник",
            "Пол",
            "Окно",
            "Подоконник",
        )
        "ванн" in lower || "сануз" in lower || "туал" in lower || "душ" in lower -> listOf(
            "Зеркало",
            "Раковина",
            "Ванна / душ",
            "Унитаз",
            "Кафель",
            "Пол",
            "Полочки",
        )
        else -> listOf(
            "Пол",
            "Пыль",
            "Окно",
            "Подоконник",
            "Зеркало",
            "Дверь",
            "Светильник",
            "Потолок",
            "Батарея",
            "Шкаф",
        )
    }
}

/** Разделы зоны уборки: потолок, подоконник, батарея. Пусто — кнопка «плюс» с подсказкой. */
@Composable
fun CleaningZoneScreen(
    viewModel: HouseCareViewModel,
    zoneId: String,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val zone = data.zoneById(zoneId)
    val tasks = data.tasksOf(zoneId)
    var showAddTask by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<CleaningTask?>(null) }

    val draft = remember { mutableStateListOf<CleaningTask>() }
    val signature = tasks.joinToString("|") { "${it.id}:${it.name}:${it.order}:${it.isCleaned}" }
    LaunchedEffect(zoneId, signature) {
        if (draft.map { it.id } != tasks.map { it.id }) {
            draft.clear()
            draft.addAll(tasks)
        } else {
            // порядок уже совпадает: обновляем содержимое, не пересобирая список
            tasks.forEachIndexed { index, task -> if (draft[index] != task) draft[index] = task }
        }
    }

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = draft.indexOfFirst { it.id == from.key }
        val toIdx = draft.indexOfFirst { it.id == to.key }
        if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
            val moved = draft.removeAt(fromIdx)
            draft.add(toIdx.coerceAtMost(draft.size), moved)
            viewModel.moveTask(zoneId, fromIdx, toIdx)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val doneCount = draft.count { it.isCleaned }
    val totalDurationMs = draft.sumOf { it.active?.durationMs ?: 0L }
    val isFifteenMinRoom = isFifteenMinuteZoneName(zone?.name)

    HouseCareScaffold(
        title = zone?.name ?: "Зона уборки",
        onBack = onBack,
        section = HouseCareSectionId.Zone,
        actions = {
            if (zone != null) {
                IconButton(
                    onClick = {
                        viewModel.deleteZone(zone.id)
                        onBack()
                    },
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Удалить комнату",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (zone != null) {
                val floorName = data.floorById(zone.floorId)?.name.orEmpty()
                HouseCareSectionTitle("$floorName · ${zoneDateLabel(zone)}")
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isFifteenMinRoom) {
                    item(key = "fifteen_min_timer") {
                        FifteenMinuteTimerCard(
                            zoneTitle = zone?.name ?: "15-минутка",
                            tasksDone = doneCount,
                            tasksTotal = draft.size,
                        )
                    }
                }
                if (draft.isNotEmpty()) {
                    item(key = "zone_progress_summary") {
                        GroupCard(
                            container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        "Разделов убрано: $doneCount из ${draft.size}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        if (totalDurationMs > 0L) {
                                            "Общее время по фото: ${formatDurationMs(totalDurationMs)}"
                                        } else {
                                            "Откройте раздел и сделайте фото «До» и «После»"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                ProgressRing(
                                    done = doneCount,
                                    total = draft.size,
                                    modifier = Modifier.size(54.dp),
                                    label = "$doneCount/${draft.size}",
                                )
                            }
                        }
                    }
                }
                if (draft.size > 1) {
                    item(key = "reorder_hint") {
                        Text(
                            "Зажмите раздел пальцем и перетащите вверх или вниз, чтобы изменить порядок. " +
                                "Карандаш — переименовать.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (draft.isEmpty()) {
                    item(key = "empty_add_task") {
                        EmptyAddCard(
                            hint = "Добавьте раздел",
                            subtitle = "Например: потолок, пол, окно, подоконник, батарея",
                            onClick = { showAddTask = true },
                        )
                    }
                }
                items(draft, key = { it.id }) { task ->
                    ReorderableItem(reorderableState, key = task.id) { isDragging ->
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 1.dp,
                            label = "house_care_task_drag",
                        )
                        val report = task.active
                        CleaningRow(
                            title = task.name,
                            subtitle = when {
                                task.isCleaned -> "Убрано ${formatDateMs(report?.dateMs ?: 0L)} · ${formatDurationMs(report?.durationMs ?: 0L)}"
                                report?.beforePhoto != null ->
                                    "Начато ${formatTimeMs(report.beforeAtMs ?: report.dateMs)} — нужно фото после"
                                else -> "Уборка не начата"
                            },
                            cleaned = task.isCleaned,
                            elevation = elevation,
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = draft.size > 1,
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            ),
                            onRename = { editingTask = task },
                            onClick = { onOpenTask(task.id) },
                        )
                    }
                }
                if (draft.isNotEmpty()) {
                    item {
                        EmptyAddCard(
                            hint = "Добавить раздел",
                            subtitle = "Ещё один раздел в эту зону",
                            onClick = { showAddTask = true },
                        )
                    }
                }
            }
        }
    }

    if (showAddTask) {
        val existingNames = draft.map { it.name.trim().lowercase() }.toSet()
        val suggestions = suggestedSectionsForZone(zone?.name.orEmpty())
            .filter { it.lowercase() !in existingNames }
        TaskEditWithTemplatesDialog(
            title = "Новый раздел",
            initial = "",
            placeholder = "Например: подоконник",
            suggestions = suggestions,
            onDismiss = { showAddTask = false },
            onConfirm = { viewModel.addTask(zoneId, it); showAddTask = false },
        )
    }

    editingTask?.let { task ->
        NameEditDialog(
            title = "Раздел",
            initial = task.name,
            placeholder = "Название раздела",
            canDelete = true,
            onDismiss = { editingTask = null },
            onConfirm = { viewModel.renameTask(task.id, it); editingTask = null },
            onDelete = { viewModel.deleteTask(task.id); editingTask = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditWithTemplatesDialog(
    title: String,
    initial: String,
    placeholder: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Быстрый выбор для этой комнаты",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            suggestions.forEach { preset ->
                                ChoiceChip(
                                    label = preset,
                                    selected = text.trim().equals(preset, ignoreCase = true),
                                    onClick = { text = preset },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
