package com.example.bible.ui.housecare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.bible.data.housecare.CleaningHabit
import com.example.bible.ui.theme.OutlinedTextField
import java.time.LocalDate

/**
 * Два блока бытовых привычек — утренние и вечерние. Список общий для объекта и повторяется
 * в каждый день недели: галочка ставится на дату, которую открыл пользователь.
 * Стрелки меняют порядок, карандаш правит, переносит между блоками и удаляет.
 */
@Composable
internal fun HabitBlocks(
    viewModel: HouseCareViewModel,
    placeId: String,
    habits: List<CleaningHabit>,
    date: LocalDate,
    collapse: CollapseState,
) {
    val dayKey = date.toString()
    var editing by remember { mutableStateOf<HabitEdit?>(null) }
    var addingKind by remember { mutableStateOf<String?>(null) }
    // Порядок задаёт поле order: без сортировки список показывался бы в порядке создания.
    val ordered = habits.sortedWith(compareBy({ it.order }, { it.createdAt }))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(CleaningHabit.KIND_MORNING to "Утренние привычки", CleaningHabit.KIND_EVENING to "Вечерние привычки")
            .forEach { (kind, label) ->
                val block = ordered.filter { it.kind == kind }
                HabitBlock(
                    title = label,
                    icon = if (kind == CleaningHabit.KIND_MORNING) Icons.Default.WbTwilight else Icons.Default.NightsStay,
                    habits = block,
                    collapsed = collapse.collapsed("habit:$kind"),
                    onToggleCollapsed = { collapse.toggle("habit:$kind") },
                    doneOf = { it.isDoneOn(dayKey) },
                    onToggle = { habit -> viewModel.setHabitDone(habit.id, dayKey, !habit.isDoneOn(dayKey)) },
                    onMove = { index, delta -> viewModel.moveHabit(placeId, kind, index, index + delta) },
                    onEdit = { editing = HabitEdit(it, kind) },
                    onAdd = { addingKind = kind },
                )
            }
    }

    if (addingKind != null) {
        HabitEditDialog(
            title = "Новая привычка",
            initialName = "",
            initialKind = addingKind!!,
            canDelete = false,
            onDismiss = { addingKind = null },
            onSave = { name, kind ->
                viewModel.addHabit(placeId, kind, name)
                addingKind = null
            },
            onDelete = {},
        )
    }

    editing?.let { current ->
        HabitEditDialog(
            title = current.habit.name,
            initialName = current.habit.name,
            initialKind = current.kind,
            canDelete = true,
            onDismiss = { editing = null },
            onSave = { name, kind ->
                viewModel.updateHabit(current.habit.id, name, kind)
                editing = null
            },
            onDelete = { viewModel.deleteHabit(current.habit.id); editing = null },
        )
    }
}

private class HabitEdit(val habit: CleaningHabit, val kind: String)

@Composable
private fun HabitBlock(
    title: String,
    icon: ImageVector,
    habits: List<CleaningHabit>,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    doneOf: (CleaningHabit) -> Boolean,
    onToggle: (CleaningHabit) -> Unit,
    onMove: (index: Int, delta: Int) -> Unit,
    onEdit: (CleaningHabit) -> Unit,
    onAdd: () -> Unit,
) {
    val done = habits.count { doneOf(it) }
    GroupCard {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggleCollapsed)
                    .padding(bottom = 4.dp),
            ) {
                Icon(
                    if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (collapsed) "Развернуть" else "Свернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                BadgeCircle(size = 36.dp) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (habits.isEmpty()) "Пока пусто" else "$done из ${habits.size} выполнено",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (habits.isNotEmpty()) {
                    StatusPill("$done/${habits.size}", pillToneOf(done, habits.size))
                }
            }
            if (!collapsed) {
                habits.forEachIndexed { index, habit ->
                    val isDone = doneOf(habit)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isDone,
                            onCheckedChange = { onToggle(habit) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = cleaningGreen,
                                checkmarkColor = Color.White,
                            ),
                        )
                        Text(
                            habit.name,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggle(habit) },
                        )
                        if (habits.size > 1) {
                            IconButton(
                                onClick = { onMove(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Выше", modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { onMove(index, 1) },
                                enabled = index < habits.lastIndex,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Ниже", modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = { onEdit(habit) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить или удалить", modifier = Modifier.size(17.dp))
                        }
                    }
                }
                if (habits.isEmpty()) {
                    Text(
                        "Помолиться, почистить зубы, помыть посуду…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                AddInlineRow(label = "Добавить привычку", onClick = onAdd)
            }
        }
    }
}

/** Название привычки и блок: утро или вечер. */
@Composable
private fun HabitEditDialog(
    title: String,
    initialName: String,
    initialKind: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название привычки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Когда",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Утро", selected = kind == CleaningHabit.KIND_MORNING, modifier = Modifier.weight(1f)) {
                        kind = CleaningHabit.KIND_MORNING
                    }
                    ChoiceChip("Вечер", selected = kind == CleaningHabit.KIND_EVENING, modifier = Modifier.weight(1f)) {
                        kind = CleaningHabit.KIND_EVENING
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), kind) }) {
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
