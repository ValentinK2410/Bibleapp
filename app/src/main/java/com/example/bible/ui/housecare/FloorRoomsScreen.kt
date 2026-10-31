package com.example.bible.ui.housecare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.housecare.CleaningZone
import com.example.bible.data.housecare.WeekDays
import com.example.bible.ui.theme.OutlinedTextField

/**
 * Комнаты и участки уборки одного этажа — без привязки к открытому дню.
 * Здесь можно завести «Кухню» или «Зал» в любой момент и выбрать, в какой день их убирают.
 */
@Composable
fun FloorRoomsScreen(
    viewModel: HouseCareViewModel,
    floorId: String,
    onBack: () -> Unit,
    onOpenZone: (String) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val floor = data.floorById(floorId)
    var editingZone by remember { mutableStateOf<CleaningZone?>(null) }
    var showAddZone by remember { mutableStateOf(false) }
    val zones = data.zones.filter { it.floorId == floorId }

    HouseCareScaffold(title = floor?.name ?: "Этаж", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Комнаты и участки этого этажа. День недели показывает, когда их убирают.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                EmptyAddCard(
                    hint = "Добавить комнату или участок",
                    subtitle = "Кухня, зал, санузел… и день, когда убираем",
                    onClick = { showAddZone = true },
                )
            }
            WeekDays.displayed.forEach { day ->
                val ofDay = zones.filter { it.dayOfWeek == day }
                if (ofDay.isNotEmpty()) {
                    item { HouseCareSectionTitle(WeekDays.fullLabel(day)) }
                    items(ofDay, key = { it.id }) { zone ->
                        val zoneTasks = data.tasksOf(zone.id)
                        CleaningRow(
                            title = zone.name,
                            subtitle = "разделов ${zoneTasks.count { it.isCleaned }}/${zoneTasks.size}",
                            cleaned = data.isZoneCleaned(zone.id),
                            onClick = { onOpenZone(zone.id) },
                            onRename = { editingZone = zone },
                            onLongClick = { editingZone = zone },
                        )
                    }
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
            initialDay = todayDayIndex(),
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
            initialDay = zone.dayOfWeek,
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

/** Название + день недели для комнаты уборки. */
@Composable
internal fun RoomEditDialog(
    title: String,
    initialName: String,
    initialDay: Int,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, day: Int) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var day by remember { mutableIntStateOf(initialDay) }
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
                Text(
                    "День уборки",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekDays.displayed.forEach { candidate ->
                        ChoiceChip(
                            label = WeekDays.shortLabel(candidate),
                            selected = candidate == day,
                            modifier = Modifier.weight(1f),
                            onClick = { day = candidate },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), day) }) {
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
