package com.example.bible.ui.housecare

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.housecare.CleaningTask
import com.example.bible.data.housecare.WeekDays
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
        val moved = draft.removeAt(from.index)
        draft.add(to.index.coerceAtMost(draft.size), moved)
        viewModel.moveTask(zoneId, from.index, to.index)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    HouseCareScaffold(title = zone?.name ?: "Зона уборки", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (zone != null) {
                val floorName = data.floorById(zone.floorId)?.name.orEmpty()
                HouseCareSectionTitle("$floorName · ${WeekDays.fullLabel(zone.dayOfWeek)}")
            }
            if (draft.size > 1) {
                Text(
                    "Зажмите раздел пальцем и перетащите вверх или вниз, чтобы изменить порядок. " +
                        "Карандаш — переименовать.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (draft.isEmpty()) {
                    item {
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
        NameEditDialog(
            title = "Новый раздел",
            initial = "",
            placeholder = "Например: подоконник",
            canDelete = false,
            onDismiss = { showAddTask = false },
            onConfirm = { viewModel.addTask(zoneId, it); showAddTask = false },
            onDelete = {},
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
