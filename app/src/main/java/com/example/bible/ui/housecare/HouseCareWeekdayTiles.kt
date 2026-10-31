package com.example.bible.ui.housecare

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bible.data.housecare.CleaningHabit
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.HouseCarePhotoStorage
import com.example.bible.data.housecare.WeekDays
import com.example.bible.data.housecare.WeekdayTileStyle
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun argbColor(argb: Long?): Color? = argb?.let { Color((it and 0xFFFFFFFFL).toInt()) }

private val TilePalette = listOf(
    0xFFEF9A9A, 0xFFFFCC80, 0xFFFFF176, 0xFFA5D6A7,
    0xFF80CBC4, 0xFF90CAF9, 0xFFB39DDB, 0xFFF48FB1,
)

private val TileIcons = listOf("🧹", "🧽", "🪣", "✨", "🌿", "⭐", "🙏", "🏠", "🍳", "🛏️", "🚿", "🕯️")

private const val TILE_PHOTO_KIND = "tile"

internal fun todayDayIndex(): Int = LocalDate.now().dayOfWeek.value

/** Комната дня в плитке: название и сколько разделов убрано из общего числа. */
internal data class TileRoom(val name: String, val sectionsDone: Int, val sectionsTotal: Int) {
    val cleaned: Boolean get() = sectionsTotal > 0 && sectionsDone == sectionsTotal
}

/**
 * Плитки Пн–Сб выбранной недели: два столбика по три ряда, высота — на весь список комнат.
 * В плитке дата с годом, комнаты с прогрессом уборки и счётчик привычек утра и вечера.
 * Сегодняшний день подсвечен, долгое касание открывает настройку плитки.
 */
@Composable
internal fun WeekdayTileSection(
    viewModel: HouseCareViewModel,
    placeId: String,
    weekStart: LocalDate,
    onPickDate: (LocalDate) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var editingDay by remember { mutableStateOf(0) }
    val today = LocalDate.now()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until WeekDays.displayed.size).chunked(2).forEach { rowOffsets ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                rowOffsets.forEach { offset ->
                    val date = weekStart.plusDays(offset.toLong())
                    val day = date.dayOfWeek.value
                    val style = data.tileStyleOf(day)
                    val rooms = data.roomsOfPlaceOn(date, placeId)
                    Box(modifier = Modifier.weight(1f)) {
                        WeekdayTile(
                            date = date,
                            rooms = rooms,
                            morning = data.habitProgressOn(placeId, CleaningHabit.KIND_MORNING, date),
                            evening = data.habitProgressOn(placeId, CleaningHabit.KIND_EVENING, date),
                            isToday = date == today,
                            style = style,
                            photoFile = style.photo?.let { HouseCarePhotoStorage.resolve(context, it) },
                            onClick = { onPickDate(date) },
                            onLongClick = { editingDay = day },
                        )
                    }
                }
            }
        }
    }

    if (editingDay > 0) {
        WeekdayTileStyleDialog(
            viewModel = viewModel,
            day = editingDay,
            onDismiss = { editingDay = 0 },
        )
    }
}

private fun HouseCareData.roomsOfPlaceOn(date: LocalDate, placeId: String): List<TileRoom> {
    if (date.dayOfWeek == DayOfWeek.SUNDAY) return emptyList()
    return zonesOfPlace(placeId, date.dayOfWeek.value).map { zone ->
        val (done, total) = zoneProgressOn(zone.id, date)
        TileRoom(zone.name, done, total)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekdayTile(
    date: LocalDate,
    rooms: List<TileRoom>,
    morning: Pair<Int, Int>,
    evening: Pair<Int, Int>,
    isToday: Boolean,
    style: WeekdayTileStyle,
    photoFile: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val todayColor = MaterialTheme.colorScheme.primary
    val customColor = argbColor(style.colorArgb)
    val container = when {
        style.style == WeekdayTileStyle.STYLE_OUTLINE -> Color.Transparent
        customColor != null && style.style == WeekdayTileStyle.STYLE_SOFT -> customColor.copy(alpha = 0.35f)
        customColor != null -> customColor
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = argbColor(style.borderColorArgb) ?: todayColor
    val width = style.borderWidthDp.dp
    val onColor = if (photoFile != null) Color.White else MaterialTheme.colorScheme.onSurface
    val onMuted = if (photoFile != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val allDone = rooms.isNotEmpty() && rooms.all { it.cleaned }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 140.dp)
            .shadow(
                elevation = if (style.glow || isToday) 10.dp else 1.dp,
                shape = shape,
                ambientColor = borderColor,
                spotColor = borderColor,
            )
            .background(container, shape)
            .then(
                when {
                    isToday -> Modifier.border(2.5.dp, borderColor, shape)
                    width > 0.dp -> Modifier.border(width, borderColor, shape)
                    else -> Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (photoFile != null) {
            AsyncImage(
                model = photoFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f), shape),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (style.icon.isNotBlank()) {
                    Text(style.icon, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    WeekDays.shortLabel(date.dayOfWeek.value),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = onColor,
                )
                if (isToday) {
                    Text(
                        "· сегодня",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (photoFile != null) Color.White else todayColor,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    date.format(tileDateFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = onMuted,
                )
            }
            if (rooms.isEmpty()) {
                Text("нет комнат", style = MaterialTheme.typography.bodySmall, color = onMuted)
            }
            rooms.forEach { room ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (room.cleaned) cleaningGreen else MaterialTheme.colorScheme.error,
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    Text(
                        room.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = onColor,
                    )
                    Text(
                        if (room.sectionsTotal == 0) "—" else "${room.sectionsDone}/${room.sectionsTotal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onMuted,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp),
                color = if (photoFile != null) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant,
            )
            HabitLine("Утренние", morning, onColor, onMuted)
            HabitLine("Вечерние", evening, onColor, onMuted)
            if (allDone) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Всё убрано",
                        tint = cleaningGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitLine(label: String, progress: Pair<Int, Int>, onColor: Color, onMuted: Color) {
    val (done, total) = progress
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    when {
                        total == 0 -> MaterialTheme.colorScheme.outline
                        done == total -> cleaningGreen
                        else -> MaterialTheme.colorScheme.error
                    },
                    RoundedCornerShape(4.dp),
                ),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = onColor)
        Text(
            if (total == 0) "нет" else "$done/$total",
            style = MaterialTheme.typography.labelSmall,
            color = onMuted,
        )
    }
}

private val tileDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Меню настройки плитки дня недели. */
@Composable
private fun WeekdayTileStyleDialog(
    viewModel: HouseCareViewModel,
    day: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val style = data.tileStyleOf(day)
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (file == null) return@rememberLauncherForActivityResult
        if (success && file.exists()) {
            viewModel.updateTileStyle(day) { it.copy(photo = file.name) }
        } else {
            file.delete()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dest = viewModel.createPhotoFile(TILE_PHOTO_KIND)
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                HouseCarePhotoStorage.copyUriToJpegFile(context, uri, dest)
            }
            if (copied) viewModel.updateTileStyle(day) { it.copy(photo = dest.name) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Плитка · ${WeekDays.fullLabel(day)}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Долгое касание плитки — это меню. Короткое открывает день.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StyleRow("Цвет") {
                    Swatch(selected = style.colorArgb == null, label = "нет") {
                        viewModel.updateTileStyle(day) { it.copy(colorArgb = null) }
                    }
                    TilePalette.forEach { argb ->
                        Swatch(selected = style.colorArgb == argb, color = argbColor(argb)) {
                            viewModel.updateTileStyle(day) { it.copy(colorArgb = argb) }
                        }
                    }
                }
                StyleRow("Стиль") {
                    TileChoice(
                        selected = style.style == WeekdayTileStyle.STYLE_FILL,
                        label = "Заполненная",
                    ) { viewModel.updateTileStyle(day) { it.copy(style = WeekdayTileStyle.STYLE_FILL) } }
                    TileChoice(
                        selected = style.style == WeekdayTileStyle.STYLE_SOFT,
                        label = "Мягкая",
                    ) { viewModel.updateTileStyle(day) { it.copy(style = WeekdayTileStyle.STYLE_SOFT) } }
                    TileChoice(
                        selected = style.style == WeekdayTileStyle.STYLE_OUTLINE,
                        label = "Контур",
                    ) { viewModel.updateTileStyle(day) { it.copy(style = WeekdayTileStyle.STYLE_OUTLINE) } }
                }
                StyleRow("Рамка") {
                    listOf(0f, 1f, 2f, 3f, 5f).forEach { dp ->
                        TileChoice(
                            selected = style.borderWidthDp == dp,
                            label = if (dp == 0f) "нет" else "${dp.toInt()} dp",
                        ) { viewModel.updateTileStyle(day) { it.copy(borderWidthDp = dp) } }
                    }
                }
                StyleRow("Цвет рамки") {
                    Swatch(selected = style.borderColorArgb == null, label = "нет") {
                        viewModel.updateTileStyle(day) { it.copy(borderColorArgb = null) }
                    }
                    TilePalette.forEach { argb ->
                        Swatch(selected = style.borderColorArgb == argb, color = argbColor(argb)) {
                            viewModel.updateTileStyle(day) { it.copy(borderColorArgb = argb) }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Свечение", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = style.glow,
                        onCheckedChange = { on -> viewModel.updateTileStyle(day) { it.copy(glow = on) } },
                    )
                }
                StyleRow("Иконка") {
                    TileChoice(selected = style.icon.isBlank(), label = "нет") {
                        viewModel.updateTileStyle(day) { it.copy(icon = "") }
                    }
                    TileIcons.forEach { glyph ->
                        TileChoice(selected = style.icon == glyph, label = glyph) {
                            viewModel.updateTileStyle(day) { it.copy(icon = glyph) }
                        }
                    }
                }
                Text("Фото вместо иконки", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val file = viewModel.createPhotoFile(TILE_PHOTO_KIND)
                        pendingFile = file
                        takePhoto.launch(
                            FileProvider.getUriForFile(context, "${context.packageName}.provider", file),
                        )
                    }) { Text("Снять") }
                    TextButton(onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Из галереи") }
                    if (style.photo != null) {
                        TextButton(onClick = { viewModel.updateTileStyle(day) { it.copy(photo = null) } }) {
                            Text("Убрать фото")
                        }
                    }
                }
                TextButton(onClick = {
                    viewModel.updateTileStyle(day) { WeekdayTileStyle(day = day) }
                }) { Text("Сбросить настройки плитки") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun StyleRow(label: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Swatch(
    selected: Boolean,
    color: Color? = null,
    label: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileChoice(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                shape,
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
