package com.example.bible.ui.housecare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.housecare.CleaningZone
import com.example.bible.data.housecare.WeekDays
import com.example.bible.data.housecare.planDate
import com.example.bible.ui.theme.OutlinedTextField
import java.time.DayOfWeek
import java.time.LocalDate

private val roomNameTemplates = listOf(
    "15-минутка",
    "Кухня",
    "Гостиная",
    "Спальня",
    "Детская",
    "Ванная",
    "Санузел",
    "Прихожая",
    "Коридор",
    "Кабинет",
    "Балкон",
)

/**
 * Комнаты и участки одного этажа на выбранную неделю.
 * Каждая комната привязана к дате, поэтому соседняя неделя начинается заново.
 */
@Composable
fun FloorRoomsScreen(
    viewModel: HouseCareViewModel,
    floorId: String,
    date: LocalDate,
    onBack: () -> Unit,
    onOpenZone: (String) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val floor = data.floorById(floorId)
    var editingZone by remember { mutableStateOf<CleaningZone?>(null) }
    var showAddZone by remember { mutableStateOf(false) }
    val weekStart = date.with(DayOfWeek.MONDAY)
    val weekDates = (0 until WeekDays.displayed.size).map { weekStart.plusDays(it.toLong()) }
    val zones = data.zones.filter { it.floorId == floorId }
    val targetDate = if (date.dayOfWeek == DayOfWeek.SUNDAY) date.minusDays(1) else date

    HouseCareScaffold(
        title = floor?.name ?: "Этаж",
        onBack = onBack,
        section = HouseCareSectionId.Floor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Комнаты этой недели: ${dayWithDate(weekStart)} — ${dayWithDate(weekStart.plusDays(5))}. " +
                        "План привязан к дате, на следующей неделе он свой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                EmptyAddCard(
                    hint = "Добавить комнату или участок",
                    subtitle = "15-минутка, кухня, зал, санузел… и день, когда убираем",
                    onClick = { showAddZone = true },
                )
            }
            weekDates.forEach { day ->
                val ofDay = zones.filter { it.planDate == day }
                if (ofDay.isNotEmpty()) {
                    item { HouseCareSectionTitle(dayWithDate(day)) }
                    items(ofDay, key = { it.id }) { zone ->
                        val (done, total) = data.zoneProgress(zone)
                        val zoneDurationMs = data.tasksOf(zone.id).sumOf { it.active?.durationMs ?: 0L }
                        CleaningRow(
                            title = zone.name,
                            subtitle = buildString {
                                append("разделов $done/$total")
                                if (zoneDurationMs > 0L) append(" · ${formatDurationMs(zoneDurationMs)}")
                            },
                            cleaned = data.isZoneCleaned(zone),
                            onClick = { onOpenZone(zone.id) },
                            onRename = { editingZone = zone },
                            onLongClick = { editingZone = zone },
                        )
                    }
                }
            }
            val outsideWeek = zones.size - zones.count { it.planDate in weekDates }
            if (outsideWeek > 0) {
                item {
                    Text(
                        "Ещё $outsideWeek ${pluralRu(outsideWeek, "комната", "комнаты", "комнат")} с другой недели — " +
                            "откройте нужный день в плитках недели.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (zones.isEmpty()) {
                item {
                    Text(
                        "Пока пусто — добавьте первую комнату.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAddZone) {
        RoomEditDialog(
            title = "Новая комната",
            initialName = "",
            initialDate = date,
            weekDates = weekDates,
            canDelete = false,
            onDismiss = { showAddZone = false },
            onSave = { name, day ->
                viewModel.addZone(floorId, day, name)
                showAddZone = false
            },
            onDelete = {},
        )
    }

    editingZone?.let { zone ->
        RoomEditDialog(
            title = zone.name,
            initialName = zone.name,
            initialDate = zone.planDate,
            weekDates = weekDates,
            canDelete = true,
            onDismiss = { editingZone = null },
            onSave = { name, day ->
                viewModel.updateZone(zone.id, name, day)
                editingZone = null
            },
            onDelete = { viewModel.deleteZone(zone.id); editingZone = null },
        )
    }
}

/** Название + шаблоны + день недели для комнаты уборки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomEditDialog(
    title: String,
    initialName: String,
    initialDate: LocalDate,
    weekDates: List<LocalDate>,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, date: LocalDate) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var picked by remember { mutableStateOf(weekDates.firstOrNull { it == initialDate } ?: weekDates.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Комната или участок") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Быстрый выбор",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        roomNameTemplates.forEach { preset ->
                            ChoiceChip(
                                label = preset,
                                selected = name.trim().equals(preset, ignoreCase = true),
                                onClick = { name = preset },
                            )
                        }
                    }
                }
                Text(
                    "Убираем в этот день",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                weekDates.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { candidate ->
                            ChoiceChip(
                                label = "${WeekDays.shortLabel(candidate.dayOfWeek.value)} ${candidate.dayOfMonth}",
                                selected = candidate == picked,
                                modifier = Modifier.weight(1f),
                                onClick = { picked = candidate },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), picked) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            if (canDelete) {
                TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
