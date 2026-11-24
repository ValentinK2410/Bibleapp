package com.example.bible.ui.housecare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.example.bible.data.housecare.HabitCopyMode
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.WeekDays
import com.example.bible.data.housecare.habitMonthTargets
import com.example.bible.data.housecare.habitMonthWeekdayTargets
import com.example.bible.data.housecare.habitTargetsWithItems
import com.example.bible.data.housecare.habitWeekTargets
import com.example.bible.ui.theme.OutlinedTextField
import java.time.LocalDate

/**
 * Утренние и вечерние привычки. Пока у дня нет своего списка, виден общий.
 * Шаблон и копирование на неделю или месяц заводят отдельный список и не переносят отметки «сделано».
 */
@Composable
internal fun HabitBlocks(
    viewModel: HouseCareViewModel,
    data: HouseCareData,
    placeId: String,
    date: LocalDate,
    collapse: CollapseState,
) {
    val dayKey = date.toString()
    var editing by remember { mutableStateOf<HabitEdit?>(null) }
    var addingKind by remember { mutableStateOf<String?>(null) }
    var savingKind by remember { mutableStateOf<String?>(null) }
    var pickingTemplateKind by remember { mutableStateOf<String?>(null) }
    var pickingMonthKind by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingHabitCopy?>(null) }

    fun copyOrAsk(kind: String, targets: List<LocalDate>, templateId: String?) {
        if (targets.isEmpty()) return
        val filled = data.habitTargetsWithItems(placeId, kind, targets)
        if (filled == 0) {
            if (templateId == null) {
                viewModel.copyDayHabits(placeId, kind, date, targets, HabitCopyMode.REPLACE)
            } else {
                viewModel.applyHabitTemplate(templateId, date, HabitCopyMode.REPLACE)
            }
        } else {
            val label = if (kind == CleaningHabit.KIND_MORNING) "утренние" else "вечерние"
            pending = PendingHabitCopy(
                kind = kind,
                targets = targets,
                templateId = templateId,
                title = "Скопировать $label привычки",
                message = "В $filled ${pluralRu(filled, "дне", "днях", "днях")} уже есть привычки. " +
                    "Заменить списки или добавить только новые пункты? " +
                    "Отметки «сделано» с исходного дня не переносятся. " +
                    "Уже отмеченное в те дни с тем же названием сохранится.",
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(CleaningHabit.KIND_MORNING to "Утренние привычки", CleaningHabit.KIND_EVENING to "Вечерние привычки")
            .forEach { (kind, label) ->
                HabitBlock(
                    kind = kind,
                    title = label,
                    icon = if (kind == CleaningHabit.KIND_MORNING) Icons.Default.WbTwilight else Icons.Default.NightsStay,
                    habits = data.habitsOn(placeId, kind, date),
                    ownsDay = data.ownsHabitDay(placeId, kind, date),
                    collapsed = collapse.collapsed("habit:$kind"),
                    onToggleCollapsed = { collapse.toggle("habit:$kind") },
                    doneOf = { it.isDoneOn(dayKey) },
                    onToggle = { habit -> viewModel.setHabitDone(habit.id, dayKey, !habit.isDoneOn(dayKey)) },
                    onMove = { index, delta -> viewModel.moveHabit(placeId, kind, date, index, index + delta) },
                    onEdit = { editing = HabitEdit(it, kind) },
                    onAdd = { addingKind = kind },
                    onSaveTemplate = { savingKind = kind },
                    onApplyTemplate = { pickingTemplateKind = kind },
                    onCopyWeek = { copyOrAsk(kind, habitWeekTargets(date), null) },
                    onCopyMonth = { pickingMonthKind = kind },
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
                viewModel.addHabit(placeId, kind, name, date)
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

    savingKind?.let { kind ->
        HabitTemplateNameDialog(
            onDismiss = { savingKind = null },
            onSave = { title ->
                viewModel.saveHabitTemplate(placeId, kind, date, title)
                savingKind = null
            },
        )
    }

    pickingTemplateKind?.let { kind ->
        val templates = data.habitTemplates.filter { it.placeId == placeId && it.kind == kind }
        AlertDialog(
            onDismissRequest = { pickingTemplateKind = null },
            title = { Text("Шаблоны") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (templates.isEmpty()) {
                        Text(
                            "Пока нет шаблонов. Набросайте список и сохраните его кнопкой «В шаблон».",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        templates.forEach { template ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        pickingTemplateKind = null
                                        copyOrAsk(kind, listOf(date), template.id)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("${template.name} · ${template.items.size}", modifier = Modifier.fillMaxWidth())
                                }
                                TextButton(onClick = { viewModel.deleteHabitTemplate(template.id) }) {
                                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingTemplateKind = null }) { Text("Закрыть") } },
        )
    }

    pickingMonthKind?.let { kind ->
        val weekday = WeekDays.fullLabel(date.dayOfWeek.value).lowercase()
        val allDays = habitMonthTargets(date)
        val sameWeekday = habitMonthWeekdayTargets(date)
        AlertDialog(
            onDismissRequest = { pickingMonthKind = null },
            title = { Text("На весь месяц") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Куда скопировать список, не включая этот день.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = {
                            pickingMonthKind = null
                            copyOrAsk(kind, allDays, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Все дни месяца · ${allDays.size}") }
                    TextButton(
                        onClick = {
                            pickingMonthKind = null
                            copyOrAsk(kind, sameWeekday, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Только $weekday · ${sameWeekday.size}") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingMonthKind = null }) { Text("Отмена") } },
        )
    }

    pending?.let { request ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(request.title) },
            text = { Text(request.message) },
            confirmButton = {
                TextButton(onClick = {
                    applyPending(viewModel, placeId, date, request, HabitCopyMode.REPLACE)
                    pending = null
                }) { Text("Заменить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        applyPending(viewModel, placeId, date, request, HabitCopyMode.MERGE)
                        pending = null
                    }) { Text("Добавить") }
                    TextButton(onClick = { pending = null }) { Text("Отмена") }
                }
            },
        )
    }
}

private class PendingHabitCopy(
    val kind: String,
    val targets: List<LocalDate>,
    val templateId: String?,
    val title: String,
    val message: String,
)

private fun applyPending(
    viewModel: HouseCareViewModel,
    placeId: String,
    date: LocalDate,
    request: PendingHabitCopy,
    mode: HabitCopyMode,
) {
    val templateId = request.templateId
    if (templateId == null) {
        viewModel.copyDayHabits(placeId, request.kind, date, request.targets, mode)
    } else {
        viewModel.applyHabitTemplate(templateId, date, mode)
    }
}

@Composable
private fun HabitTemplateNameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый шаблон") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название шаблона") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim()) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private class HabitEdit(val habit: CleaningHabit, val kind: String)

@Composable
private fun HabitBlock(
    kind: String,
    title: String,
    icon: ImageVector,
    habits: List<CleaningHabit>,
    ownsDay: Boolean,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    doneOf: (CleaningHabit) -> Boolean,
    onToggle: (CleaningHabit) -> Unit,
    onMove: (index: Int, delta: Int) -> Unit,
    onEdit: (CleaningHabit) -> Unit,
    onAdd: () -> Unit,
    onSaveTemplate: () -> Unit,
    onApplyTemplate: () -> Unit,
    onCopyWeek: () -> Unit,
    onCopyMonth: () -> Unit,
) {
    val isMorning = kind == CleaningHabit.KIND_MORNING
    val containerBg = if (isMorning) Color(0xFFFEF9C3) else Color(0xFF1E293B)
    val borderColor = if (isMorning) Color(0xFFFDE047) else Color(0xFF334155)
    val titleColor = if (isMorning) Color(0xFF713F12) else Color(0xFFF8FAFC)
    val subtitleColor = if (isMorning) Color(0xFF854D0E) else Color(0xFF94A3B8)
    val itemTextColor = if (isMorning) Color(0xFF422006) else Color(0xFFF1F5F9)
    val itemDoneTextColor = if (isMorning) Color(0xFFA16207) else Color(0xFF64748B)
    val iconTint = if (isMorning) Color(0xFFD97706) else Color(0xFF818CF8)
    val badgeBg = if (isMorning) Color(0xFFFEF08A) else Color(0xFF334155)
    val buttonTint = if (isMorning) Color(0xFF854D0E) else Color(0xFFCBD5E1)
    val dividerColor = if (isMorning) Color(0xFFFACC15).copy(alpha = 0.5f) else Color(0xFF334155)
    val actionColors = ButtonDefaults.textButtonColors(
        contentColor = buttonTint,
        disabledContentColor = buttonTint.copy(alpha = 0.38f),
    )
    val done = habits.count { doneOf(it) }
    GroupCard(
        container = containerBg,
        modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggleCollapsed)
                    .padding(vertical = 2.dp),
            ) {
                Icon(
                    if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (collapsed) "Развернуть" else "Свернуть",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
                BadgeCircle(size = 36.dp, container = badgeBg) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                    )
                    Text(
                        if (habits.isEmpty()) {
                            if (ownsDay) "Пока пусто · список этого дня" else "Пока пусто · общий список"
                        } else {
                            val scope = if (ownsDay) "этот день" else "общий список"
                            "$done из ${habits.size} · $scope"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                    )
                }
                if (habits.isNotEmpty()) {
                    HabitCountChip(
                        text = "$done/${habits.size}",
                        tone = pillToneOf(done, habits.size),
                        isMorning = isMorning,
                        fallbackBg = badgeBg,
                        fallbackFg = titleColor,
                    )
                }
            }
            if (!collapsed) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = dividerColor,
                )
                habits.forEachIndexed { index, habit ->
                    val isDone = doneOf(habit)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onToggle(habit) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Checkbox(
                            checked = isDone,
                            onCheckedChange = { onToggle(habit) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = if (isMorning) Color(0xFFD97706) else Color(0xFF818CF8),
                                uncheckedColor = buttonTint,
                                checkmarkColor = Color.White,
                            ),
                        )
                        Text(
                            habit.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium,
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                            color = if (isDone) itemDoneTextColor else itemTextColor,
                            modifier = Modifier.weight(1f),
                        )
                        if (habits.size > 1) {
                            IconButton(
                                onClick = { onMove(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Выше",
                                    tint = buttonTint.copy(alpha = if (index > 0) 1f else 0.38f),
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            IconButton(
                                onClick = { onMove(index, 1) },
                                enabled = index < habits.lastIndex,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Ниже",
                                    tint = buttonTint.copy(alpha = if (index < habits.lastIndex) 1f else 0.38f),
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        IconButton(onClick = { onEdit(habit) }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Изменить или удалить",
                                tint = buttonTint,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
                if (habits.isEmpty()) {
                    Text(
                        "Помолиться, почистить зубы, помыть посуду…",
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(badgeBg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        "Добавить привычку",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = buttonTint,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onSaveTemplate,
                        enabled = habits.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = actionColors,
                    ) {
                        Text("В шаблон")
                    }
                    TextButton(
                        onClick = onApplyTemplate,
                        modifier = Modifier.weight(1f),
                        colors = actionColors,
                    ) {
                        Text("Из шаблона")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onCopyWeek,
                        enabled = habits.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = actionColors,
                    ) {
                        Text("На неделю")
                    }
                    TextButton(
                        onClick = onCopyMonth,
                        enabled = habits.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = actionColors,
                    ) {
                        Text("На месяц")
                    }
                }
            }
        }
    }
}

/** Счётчик «сделано» на карточке привычек: свои цвета, чтобы читался и на жёлтом утре, и на тёмном вечере. */
@Composable
private fun HabitCountChip(
    text: String,
    tone: PillTone,
    isMorning: Boolean,
    fallbackBg: Color,
    fallbackFg: Color,
) {
    val (background, foreground) = when (tone) {
        PillTone.Done -> if (isMorning) {
            Color(0xFFDCFCE7) to Color(0xFF166534)
        } else {
            Color(0xFF14532D) to Color(0xFFBBF7D0)
        }
        PillTone.Pending -> if (isMorning) {
            Color(0xFFFEE2E2) to Color(0xFF991B1B)
        } else {
            Color(0xFF7F1D1D) to Color(0xFFFECACA)
        }
        PillTone.Muted -> fallbackBg to fallbackFg
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1,
        )
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
