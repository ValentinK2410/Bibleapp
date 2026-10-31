package com.example.bible.ui.housecare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.WeekDays
import com.example.bible.ui.theme.OutlinedTextField
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

internal val cleaningGreen = androidx.compose.ui.graphics.Color(0xFF2E7D32)

/**
 * Сколько разделов комнаты убрано на конкретную дату.
 * За сегодня считает текущий цикл уборки, за другой день — завершённые отчёты той даты.
 */
internal fun HouseCareData.zoneProgressOn(zoneId: String, date: LocalDate): Pair<Int, Int> {
    val zoneTasks = tasksOf(zoneId)
    val today = LocalDate.now()
    val done = if (date == today) {
        zoneTasks.count { it.isCleaned }
    } else {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        zoneTasks.count { task -> task.finishedReports().any { it.dateMs in start..end } }
    }
    return done to zoneTasks.size
}

internal fun HouseCareData.isZoneCleanedOn(zoneId: String, date: LocalDate): Boolean {
    val (done, total) = zoneProgressOn(zoneId, date)
    return total > 0 && done == total
}

/** Сколько привычек блока выполнено на дату. */
internal fun HouseCareData.habitProgressOn(placeId: String, kind: String, date: LocalDate): Pair<Int, Int> {
    val block = habitsOf(placeId, kind)
    val key = date.toString()
    return block.count { it.isDoneOn(key) } to block.size
}

internal fun formatDurationMs(ms: Long): String {
    val minutes = ms / 60_000L
    return when {
        minutes < 1L -> "меньше минуты"
        minutes < 60L -> "$minutes мин"
        else -> {
            val hours = minutes / 60L
            val rest = minutes % 60L
            if (rest == 0L) "$hours ч" else "$hours ч $rest мин"
        }
    }
}

internal fun formatDateMs(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))

internal fun formatTimeMs(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HouseCareScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = { actions() },
            )
        },
        content = content,
    )
}

/**
 * Свёрнутые блоки дня: ключи хранятся в одной строке, чтобы состояние переживало
 * поворот экрана через обычный rememberSaveable.
 */
internal class CollapseState(private val store: MutableState<String>) {

    private fun keys(): MutableSet<String> = store.value.split('|').filter { it.isNotBlank() }.toMutableSet()

    fun collapsed(key: String): Boolean = key in keys()

    fun toggle(key: String) = set(listOf(key), !collapsed(key))

    fun set(keys: List<String>, collapsed: Boolean) {
        val current = keys()
        keys.forEach { if (collapsed) current.add(it) else current.remove(it) }
        store.value = current.joinToString("|")
    }

    fun allCollapsed(keys: List<String>): Boolean = keys.isNotEmpty() && keys.all { it in keys() }
}

@Composable
internal fun rememberCollapseState(): CollapseState {
    val store = rememberSaveable { mutableStateOf("") }
    return remember(store) { CollapseState(store) }
}

@Composable
internal fun HouseCareSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** Карточка «плюс» с подсказкой: создание зоны или раздела, когда список пуст. */
@Composable
internal fun EmptyAddCard(
    hint: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(hint, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Небольшая кнопка-выбор: один вариант из нескольких (день недели, утро/вечер). */
@Composable
internal fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Строка списка: название, подпись и зелёная галочка, если убрано. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CleaningRow(
    title: String,
    subtitle: String,
    cleaned: Boolean,
    showNotCleanedLabel: Boolean = true,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 1.dp,
    onRename: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                cleaned -> Icon(Icons.Default.CheckCircle, contentDescription = "Убрано", tint = cleaningGreen)
                showNotCleanedLabel -> Text(
                    "не убрано",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (onRename != null) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Переименовать")
                }
            }
        }
    }
}

/** Диалог названия: создать или переименовать элемент, при необходимости удалить. */
@Composable
internal fun NameEditDialog(
    title: String,
    initial: String,
    placeholder: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
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

internal enum class PillTone { Done, Pending, Muted }

/** Готово — зелёный, ничего не сделано — красный, частично — нейтральный. */
internal fun pillToneOf(done: Int, total: Int): PillTone = when {
    total <= 0 -> PillTone.Muted
    done >= total -> PillTone.Done
    done <= 0 -> PillTone.Pending
    else -> PillTone.Muted
}

/** Согласование существительного с числом: 1 комната, 2 комнаты, 5 комнат. */
internal fun pluralRu(count: Int, one: String, few: String, many: String): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}

/** Компактная цветная метка статуса: «убрано», «не убрано», «2/5». */
@Composable
internal fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val (container, content) = when (tone) {
        PillTone.Done -> cleaningGreen.copy(alpha = 0.14f) to cleaningGreen
        PillTone.Pending -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        PillTone.Muted -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1,
        )
    }
}

/** Кольцо прогресса с числом в центре. */
@Composable
internal fun ProgressRing(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String = "$done/$total",
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val sweepColor = if (total > 0 && done >= total) cleaningGreen else MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier) {
            val stroke = 6.dp.toPx()
            val radius = size.width.coerceAtMost(size.height) / 2 - stroke / 2
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = arcSize,
                topLeft = topLeft,
            )
            val angle = if (total <= 0) 0f else 360f * done / total
            if (angle > 0f) {
                drawArc(
                    color = sweepColor,
                    startAngle = -90f,
                    sweepAngle = angle,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = arcSize,
                    topLeft = topLeft,
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Плоская строка «плюс» внутри группы: без отдельной карточки. */
@Composable
internal fun AddInlineRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Круглый бейдж для шапки группы: буква этажа или иконка блока. */
@Composable
internal fun BadgeCircle(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    container: androidx.compose.ui.graphics.Color =
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Карточка-группа с мягкой формой: этаж или блок привычек. */
@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}
