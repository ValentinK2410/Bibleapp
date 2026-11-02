package com.example.bible.ui.housecare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.bible.data.housecare.planDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

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
                    subtitle = "Объекты, недельный план, привычки и разделы с фото «до и после»",
                    icon = {
                        Icon(
                            Icons.Filled.CleaningServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    onClick = onOpenCleaning,
                )
            }
            item {
                HubCard(
                    title = "Статистика уборки",
                    subtitle = "Время по неделям, дням недели, комнатам и история отчётов",
                    icon = {
                        Icon(
                            Icons.Filled.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    },
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
    GroupCard(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BadgeCircle(size = 44.dp) {
                icon()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { HouseCareSectionTitle("Мои объекты") }
            if (data.places.isEmpty()) {
                item {
                    Text(
                        "Добавьте недвижимость — дом, квартиру, гараж, дачу или сарай — и её этажность.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            items(data.places, key = { it.id }) { place ->
                val todayRooms = data.zonesOfPlace(place.id, LocalDate.now())
                val isPlaceCleaned = todayRooms.isNotEmpty() && todayRooms.all { data.isZoneCleaned(it) }
                CleaningRow(
                    title = place.name,
                    subtitle = buildString {
                        append(place.address.ifBlank { "Адрес не указан" })
                        append(" · ")
                        append(if (place.floorsCount > 0) floorsLabel(place.floorsCount) else "этажи не указаны")
                    },
                    cleaned = isPlaceCleaned,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.HomeWork,
                            contentDescription = null,
                            tint = if (isPlaceCleaned) cleaningGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                    },
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

/** Куда свайп увёл недели: стрелка повторяет движение пальца, подпись — что за ним пришло. */
private enum class WeekSwipe(val label: String) {
    ToPast("показана прошлая неделя"),
    ToFuture("показана следующая неделя"),
}

/** Дни недели одного объекта уборки и его этажи. */
@Composable
fun PlaceDaysScreen(
    viewModel: HouseCareViewModel,
    placeId: String,
    onBack: () -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    onOpenFloor: (floorId: String, date: LocalDate) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val place = data.placeById(placeId)
    var editingFloor by remember { mutableStateOf<HouseFloor?>(null) }
    var weekOffset by remember { mutableIntStateOf(0) }
    /** Метка о том, куда свайпнул пользователь: живёт полторы секунды, потом снова обычная подсказка. */
    var swipeNote by remember { mutableStateOf<WeekSwipe?>(null) }
    var swipeStamp by remember { mutableIntStateOf(0) }
    LaunchedEffect(swipeStamp) {
        if (swipeStamp == 0) return@LaunchedEffect
        delay(1600)
        swipeNote = null
    }
    val weekStart = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
    val prevWeekStart = weekStart.minusWeeks(1)
    val weekDates = (0 until WeekDays.displayed.size).map { weekStart.plusDays(it.toLong()) }.toSet()
    val copyableDays = (0 until WeekDays.displayed.size).mapNotNull { offset ->
        val toDate = weekStart.plusDays(offset.toLong())
        val fromDate = prevWeekStart.plusDays(offset.toLong())
        val targetZones = data.zonesOfPlace(placeId, toDate)
        val sourceZones = data.zonesOfPlace(placeId, fromDate)
        if (targetZones.isEmpty() && sourceZones.isNotEmpty()) sourceZones.size else null
    }
    val copyableRoomsTotal = copyableDays.sum()
    val swipeThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }

    fun shiftWeek(delta: Int) {
        weekOffset += delta
        swipeNote = if (delta < 0) WeekSwipe.ToPast else WeekSwipe.ToFuture
        swipeStamp += 1
    }

    HouseCareScaffold(
        title = place?.name ?: "Объект",
        onBack = onBack,
        actions = {
            AnimatedVisibility(
                visible = weekOffset != 0,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
            ) {
                IconButton(onClick = { weekOffset = 0 }) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = "Показать текущую неделю",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(swipeThresholdPx) {
                            var dragged = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragged = 0f },
                                onHorizontalDrag = { _, dragAmount -> dragged += dragAmount },
                                onDragEnd = {
                                    when {
                                        dragged > swipeThresholdPx -> shiftWeek(-1)
                                        dragged < -swipeThresholdPx -> shiftWeek(1)
                                    }
                                    dragged = 0f
                                },
                                onDragCancel = { dragged = 0f },
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GroupCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                IconButton(
                                    onClick = { shiftWeek(-1) },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Прошлая неделя",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    val addressText = place?.address?.takeIf { it.isNotBlank() }
                                    if (addressText != null) {
                                        Text(
                                            addressText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        "Неделя ${weekStart.format(weekDayFormatter)} — ${weekStart.plusDays(5).format(weekDayFormatter)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val note = swipeNote
                                    if (note == null) {
                                        Text(
                                            when {
                                                weekOffset == 0 -> "Текущая неделя · свайп или стрелки для перехода"
                                                weekOffset < 0 -> "Архив (${weekOffset} нед.) · домик в шапке вернёт сегодня"
                                                else -> "План (+${weekOffset} нед.) · домик в шапке вернёт сегодня"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (note == WeekSwipe.ToPast) {
                                                    Icons.AutoMirrored.Filled.ArrowForward
                                                } else {
                                                    Icons.AutoMirrored.Filled.ArrowBack
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                note.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { shiftWeek(1) },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Следующая неделя",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    WeekdayTileSection(
                        viewModel = viewModel,
                        placeId = placeId,
                        weekStart = weekStart,
                        onPickDate = onOpenDate,
                    )
                }
            }
            if (copyableRoomsTotal > 0) {
                item {
                    EmptyAddCard(
                        hint = "Скопировать план со всей прошлой недели",
                        subtitle = "$copyableRoomsTotal ${pluralRu(copyableRoomsTotal, "комната", "комнаты", "комнат")} " +
                            "за ${copyableDays.size} ${pluralRu(copyableDays.size, "день", "дня", "дней")} перенесутся в пустые дни этой недели",
                        onClick = { viewModel.copyWeekPlan(placeId, prevWeekStart, weekStart) },
                    )
                }
            }
            item { HouseCareSectionTitle("Этажи") }
            items(data.floorsOf(placeId), key = { it.id }) { floor ->
                val zones = data.zones.filter { it.floorId == floor.id && it.planDate in weekDates }
                val floorCleaned = zones.isNotEmpty() && zones.all { data.isZoneCleaned(it) }
                CleaningRow(
                    title = floor.name,
                    subtitle = if (zones.isEmpty()) {
                        "На этой неделе комнат нет"
                    } else {
                        val sectionsTotal = zones.sumOf { data.tasksOf(it.id).size }
                        "${zones.size} ${pluralRu(zones.size, "комната", "комнаты", "комнат")} " +
                            "на неделе · $sectionsTotal ${pluralRu(sectionsTotal, "раздел", "раздела", "разделов")}"
                    },
                    cleaned = floorCleaned,
                    leadingIcon = {
                        Text(
                            floor.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (floorCleaned) cleaningGreen else MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { onOpenFloor(floor.id, weekStart) },
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
    onOpenFloor: (floorId: String, LocalDate) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val place = data.placeById(placeId)
    val floors = data.floorsOf(placeId)
    val dayOfWeek = date.dayOfWeek.value
    var editingFloor by remember { mutableStateOf<HouseFloor?>(null) }
    var editingZone by remember { mutableStateOf<CleaningZone?>(null) }
    var addRoomFloorId by remember { mutableStateOf<String?>(null) }

    val rooms = data.zonesOfPlace(placeId, date)
    val roomsDone = rooms.count { data.isZoneCleaned(it) }
    // Воскресенье — день отдыха: новую комнату заводим на субботу той же недели.
    val planDate = if (dayOfWeek == DayOfWeek.SUNDAY.value) date.minusDays(1) else date
    val weekDates = (0 until WeekDays.displayed.size).map { planDate.with(DayOfWeek.MONDAY).plusDays(it.toLong()) }
    val previousWeekRooms = data.zonesOfPlace(placeId, planDate.minusWeeks(1))
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
            if (rooms.isEmpty() && previousWeekRooms.isNotEmpty()) {
                item {
                    val from = planDate.minusWeeks(1)
                    EmptyAddCard(
                        hint = "Скопировать план с ${from.format(weekDayFormatter)}",
                        subtitle = "${previousWeekRooms.size} ${pluralRu(previousWeekRooms.size, "комната", "комнаты", "комнат")} " +
                            "и их разделы начнут этот день без отметок об уборке",
                        onClick = { viewModel.copyZonePlan(placeId, from, planDate) },
                    )
                }
            }
            floors.forEach { floor ->
                val zones = data.zonesOf(floor.id, date)
                item(key = floor.id) {
                    FloorDayCard(
                        data = data,
                        floor = floor,
                        zones = zones,
                        collapsed = collapse.collapsed(floor.id),
                        onToggleCollapsed = { collapse.toggle(floor.id) },
                        onOpenFloor = { onOpenFloor(floor.id, date) },
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
            initialDate = planDate,
            weekDates = weekDates,
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
    GroupCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (placeName.isNotBlank()) {
                    Text(
                        placeName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        WeekDays.fullLabel(date.dayOfWeek.value),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isToday) {
                        StatusPill("сегодня", PillTone.Done)
                    }
                }
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
                modifier = Modifier.size(68.dp),
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
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onOpenFloor: () -> Unit,
    onOpenZone: (String) -> Unit,
    onEditFloor: () -> Unit,
    onEditZone: (CleaningZone) -> Unit,
    onAddRoom: () -> Unit,
) {
    val cleaned = zones.count { data.isZoneCleaned(it) }
    GroupCard {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggleCollapsed)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        if (zones.isEmpty()) {
                            "Комнат на этот день нет"
                        } else {
                            "${zones.size} ${pluralRu(zones.size, "комната", "комнаты", "комнат")} · убрано $cleaned/${zones.size}"
                        },
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
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Этаж: название или удалить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onOpenFloor, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.HomeWork,
                        contentDescription = "Все комнаты этажа",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            if (!collapsed) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                zones.forEach { zone ->
                    val (done, total) = data.zoneProgress(zone)
                    val zoneDurationMs = data.tasksOf(zone.id).sumOf { it.active?.durationMs ?: 0L }
                    RoomRow(
                        name = zone.name,
                        subtitle = buildString {
                            append("разделов $done/$total")
                            if (zoneDurationMs > 0L) append(" · ${formatDurationMs(zoneDurationMs)}")
                        },
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
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BadgeCircle(
            size = 34.dp,
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
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Комната: название, день или удалить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

