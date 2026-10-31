package com.example.bible.ui.housecare

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.housecare.CleaningHabit
import com.example.bible.data.housecare.CleaningZone
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.HouseFloor
import com.example.bible.data.housecare.HousePlace
import com.example.bible.data.housecare.WeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekDayFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val heroDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

/** Главный экран раздела «Попечительная о доме». */
@Composable
fun HouseCareHubScreen(
    onBack: () -> Unit,
    onOpenCleaning: () -> Unit,
    onOpenStats: () -> Unit,
) {
    HouseCareScaffold(title = "Попечительная о доме", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HubCard(
                    title = "Система уборки",
                    subtitle = "Этажи, дни недели, зоны и разделы с фото до и после",
                    icon = { Icon(Icons.Filled.CleaningServices, contentDescription = null) },
                    onClick = onOpenCleaning,
                )
            }
            item {
                HubCard(
                    title = "Статистика уборки",
                    subtitle = "Сколько времени уходит на каждый раздел",
                    icon = { Icon(Icons.Filled.Insights, contentDescription = null) },
                    onClick = onOpenStats,
                )
            }
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon()
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** «Система уборки»: объекты недвижимости. Тап — дни недели, долгое касание — адрес и этажи. */
@Composable
fun CleaningSystemScreen(
    viewModel: HouseCareViewModel,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var editingPlace by remember { mutableStateOf<HousePlace?>(null) }

    HouseCareScaffold(title = "Система уборки", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { HouseCareSectionTitle("Мои объекты") }
            if (data.places.isEmpty()) {
                item {
                    Text(
                        "Добавьте недвижимость — дом, квартиру, гараж, дачу или сарай — и её этажность.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(data.places, key = { it.id }) { place ->
                val floorIds = data.floorsOf(place.id).map { it.id }.toSet()
                val zones = data.zones.filter { it.floorId in floorIds }
                CleaningRow(
                    title = place.name,
                    subtitle = buildString {
                        append(place.address.ifBlank { "Адрес не указан" })
                        append(" · ")
                        append(if (place.floorsCount > 0) floorsLabel(place.floorsCount) else "этажи не указаны")
                    },
                    cleaned = zones.isNotEmpty() && zones.all { data.isZoneCleaned(it.id) },
                    onClick = { onOpenPlace(place.id) },
                    onRename = { editingPlace = place },
                    onLongClick = { editingPlace = place },
                )
            }
            item {
                EmptyAddCard(
                    hint = "Добавить недвижимость",
                    subtitle = "Дом, квартира, гараж, дача, сарай: улица и сколько этажей",
                    onClick = onOpenSettings,
                )
            }
        }
    }

    editingPlace?.let { place ->
        PlaceEditDialog(
            title = place.name,
            initial = place,
            canDelete = true,
            onDismiss = { editingPlace = null },
            onSave = { name, address, floors ->
                viewModel.updatePlace(place.id, name, address, floors)
                editingPlace = null
            },
            onDelete = { viewModel.deletePlace(place.id); editingPlace = null },
        )
    }
}

/** Дни недели одного объекта уборки и его этажи. */
@Composable
fun PlaceDaysScreen(
    viewModel: HouseCareViewModel,
    placeId: String,
    onBack: () -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    onOpenFloor: (String) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val place = data.placeById(placeId)
    var editingFloor by remember { mutableStateOf<HouseFloor?>(null) }
    var weekOffset by remember { mutableIntStateOf(0) }
    val weekStart = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
    val swipeThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }

    HouseCareScaffold(title = place?.name ?: "Объект", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    place?.address?.ifBlank { "Адрес не указан" } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(swipeThresholdPx) {
                            var dragged = 0f
                            detectDragGestures(
                                onDragStart = { dragged = 0f },
                                onDrag = { _, amount -> dragged += amount.x },
                                onDragEnd = {
                                    when {
                                        dragged < -swipeThresholdPx -> weekOffset -= 1
                                        dragged > swipeThresholdPx -> weekOffset += 1
                                    }
                                },
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Неделя ${weekStart.format(weekDayFormatter)} — ${weekStart.plusDays(5).format(weekDayFormatter)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (weekOffset != 0) {
                            TextButton(onClick = { weekOffset = 0 }) { Text("Сегодня") }
                        }
                    }
                    Text(
                        "Смахните по плиткам влево — прошлая неделя, вправо — следующая.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WeekdayTileSection(
                        viewModel = viewModel,
                        placeId = placeId,
                        weekStart = weekStart,
                        onPickDate = onOpenDate,
                    )
                }
            }
            item { HouseCareSectionTitle("Этажи") }
            items(data.floorsOf(placeId), key = { it.id }) { floor ->
                val zones = data.zones.filter { it.floorId == floor.id }
                CleaningRow(
                    title = floor.name,
                    subtitle = if (zones.isEmpty()) {
                        "Комнаты пока не добавлены"
                    } else {
                        "${zones.size} комнат · ${zones.sumOf { data.tasksOf(it.id).size }} разделов"
                    },
                    cleaned = zones.isNotEmpty() && zones.all { data.isZoneCleaned(it.id) },
                    onClick = { onOpenFloor(floor.id) },
                    onRename = { editingFloor = floor },
                    onLongClick = { editingFloor = floor },
                )
            }
            item {
                val nextFloorNumber = data.floorsOf(placeId).size + 1
                EmptyAddCard(
                    hint = "Добавить этаж",
                    subtitle = "Подвал, мансарда, второй этаж — как вам удобно",
                    onClick = { viewModel.addFloor(placeId, "$nextFloorNumber этаж") },
                )
            }
        }
    }

    editingFloor?.let { floor ->
        NameEditDialog(
            title = "Этаж",
            initial = floor.name,
            placeholder = "Название этажа",
            canDelete = true,
            onDismiss = { editingFloor = null },
            onConfirm = { viewModel.renameFloor(floor.id, it); editingFloor = null },
            onDelete = { viewModel.deleteFloor(floor.id); editingFloor = null },
        )
    }
}

/** Конкретный день объекта: привычки утра/вечера и зоны по этажам. */
@Composable
fun CleaningDayScreen(
    viewModel: HouseCareViewModel,
    placeId: String,
    date: LocalDate,
    onBack: () -> Unit,
    onOpenZone: (zoneId: String) -> Unit,
    onOpenFloor: (floorId: String) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val place = data.placeById(placeId)
    val floors = data.floorsOf(placeId)
    val dayOfWeek = date.dayOfWeek.value
    var editingFloor by remember { mutableStateOf<HouseFloor?>(null) }
    var editingZone by remember { mutableStateOf<CleaningZone?>(null) }
    var addRoomFloorId by remember { mutableStateOf<String?>(null) }

    val rooms = data.zonesOfPlace(placeId, if (dayOfWeek == 7) 6 else dayOfWeek)
    val roomsDone = rooms.count { data.isZoneCleanedOn(it.id, date) }
    val collapse = rememberCollapseState()
    val blockKeys = floors.map { it.id } +
        listOf("habit:${CleaningHabit.KIND_MORNING}", "habit:${CleaningHabit.KIND_EVENING}")
    val allCollapsed = collapse.allCollapsed(blockKeys)

    HouseCareScaffold(
        title = listOfNotNull(
            place?.name?.takeIf { it.isNotBlank() },
            WeekDays.fullLabel(dayOfWeek),
        ).joinToString(" · "),
        onBack = onBack,
        actions = {
            IconButton(onClick = { collapse.set(blockKeys, !allCollapsed) }) {
                Icon(
                    if (allCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                    contentDescription = if (allCollapsed) "Развернуть все блоки" else "Свернуть все блоки",
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DayHeroCard(
                    placeName = place?.name.orEmpty(),
                    date = date,
                    roomsDone = roomsDone,
                    roomsTotal = rooms.size,
                    morning = data.habitProgressOn(placeId, CleaningHabit.KIND_MORNING, date),
                    evening = data.habitProgressOn(placeId, CleaningHabit.KIND_EVENING, date),
                )
            }
            item {
                HabitBlocks(
                    viewModel = viewModel,
                    placeId = placeId,
                    habits = data.habits.filter { it.placeId == placeId },
                    date = date,
                    collapse = collapse,
                )
            }
            if (floors.isEmpty()) {
                item {
                    Text(
                        "У объекта пока нет этажей — добавьте первый, и зоны закрепятся за ним.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    EmptyAddCard(
                        hint = "Добавить 1 этаж",
                        subtitle = "Этажность задаётся и в настройках объекта",
                        onClick = { viewModel.addFloor(placeId, "1 этаж") },
                    )
                }
            }
            floors.forEach { floor ->
                val zones = data.zonesOf(floor.id, dayOfWeek)
                item(key = floor.id) {
                    FloorDayCard(
                        data = data,
                        floor = floor,
                        zones = zones,
                        date = date,
                        collapsed = collapse.collapsed(floor.id),
                        onToggleCollapsed = { collapse.toggle(floor.id) },
                        onOpenFloor = { onOpenFloor(floor.id) },
                        onOpenZone = onOpenZone,
                        onEditFloor = { editingFloor = floor },
                        onEditZone = { editingZone = it },
                        onAddRoom = { addRoomFloorId = floor.id },
                    )
                }
            }
        }
    }

    editingFloor?.let { floor ->
        NameEditDialog(
            title = "Этаж",
            initial = floor.name,
            placeholder = "Название этажа",
            canDelete = true,
            onDismiss = { editingFloor = null },
            onConfirm = { viewModel.renameFloor(floor.id, it); editingFloor = null },
            onDelete = { viewModel.deleteFloor(floor.id); editingFloor = null },
        )
    }

    addRoomFloorId?.let { floorId ->
        RoomEditDialog(
            title = "Новая комната",
            initialName = "",
            initialDay = dayOfWeek,
            canDelete = false,
            onDismiss = { addRoomFloorId = null },
            onSave = { name, day ->
                viewModel.addZone(floorId, day, name)
                addRoomFloorId = null
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

/** Шапка дня: число, прогресс по комнатам и счётчики привычек. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayHeroCard(
    placeName: String,
    date: LocalDate,
    roomsDone: Int,
    roomsTotal: Int,
    morning: Pair<Int, Int>,
    evening: Pair<Int, Int>,
) {
    val isToday = date == LocalDate.now()
    GroupCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (placeName.isNotBlank()) {
                    Text(
                        placeName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    WeekDays.fullLabel(date.dayOfWeek.value) + if (isToday) " · сегодня" else "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    date.format(heroDateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 3,
                ) {
                    StatusPill(
                        "комнаты $roomsDone/$roomsTotal",
                        pillToneOf(roomsDone, roomsTotal),
                    )
                    StatusPill("утро ${morning.first}/${morning.second}", pillToneOf(morning.first, morning.second))
                    StatusPill("вечер ${evening.first}/${evening.second}", pillToneOf(evening.first, evening.second))
                }
            }
            ProgressRing(
                done = roomsDone,
                total = roomsTotal,
                modifier = Modifier.size(66.dp),
                label = if (roomsTotal > 0) "$roomsDone/$roomsTotal" else "—",
            )
        }
    }
}

/** Этаж дня одной карточкой: шапка со счётчиком и плоский список комнат. */
@Composable
private fun FloorDayCard(
    data: HouseCareData,
    floor: HouseFloor,
    zones: List<CleaningZone>,
    date: LocalDate,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onOpenFloor: () -> Unit,
    onOpenZone: (String) -> Unit,
    onEditFloor: () -> Unit,
    onEditZone: (CleaningZone) -> Unit,
    onAddRoom: () -> Unit,
) {
    val cleaned = zones.count { data.isZoneCleanedOn(it.id, date) }
    GroupCard {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggleCollapsed),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (collapsed) "Развернуть этаж" else "Свернуть этаж",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                BadgeCircle(size = 36.dp) {
                    Text(
                        floor.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(floor.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${zones.size} ${pluralRu(zones.size, "комната", "комнаты", "комнат")} · убрано $cleaned/${zones.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (pillToneOf(cleaned, zones.size)) {
                            PillTone.Done -> cleaningGreen
                            PillTone.Pending -> MaterialTheme.colorScheme.error
                            PillTone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onEditFloor, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Этаж: название или удалить", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onOpenFloor, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.HomeWork,
                        contentDescription = "Все комнаты этажа",
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            if (!collapsed) {
                if (zones.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                zones.forEach { zone ->
                    val (done, total) = data.zoneProgressOn(zone.id, date)
                    RoomRow(
                        name = zone.name,
                        subtitle = "разделов $done/$total",
                        done = done,
                        total = total,
                        onClick = { onOpenZone(zone.id) },
                        onEdit = { onEditZone(zone) },
                    )
                }
                AddInlineRow(label = "Добавить комнату/участок", onClick = onAddRoom)
            }
        }
    }
}

/** Плоская строка комнаты внутри карточки этажа. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomRow(
    name: String,
    subtitle: String,
    done: Int,
    total: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val isDone = pillToneOf(done, total) == PillTone.Done
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BadgeCircle(
            size = 32.dp,
            container = if (isDone) cleaningGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                if (isDone) Icons.Default.CheckCircle else Icons.Default.CleaningServices,
                contentDescription = null,
                tint = if (isDone) cleaningGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium,
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill("$done/$total", pillToneOf(done, total))
        IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Комната: название, день или удалить", modifier = Modifier.size(18.dp))
        }
    }
}

