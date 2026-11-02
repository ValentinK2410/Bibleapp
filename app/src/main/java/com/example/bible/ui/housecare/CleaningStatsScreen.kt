package com.example.bible.ui.housecare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.housecare.CleaningReport
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.HouseCarePhotoStorage
import com.example.bible.data.housecare.WeekDays
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class RoomSortMode { ByFrequency, ByDuration }

/** Сколько времени уходит на уборку: по неделям, дням недели, частоте комнат и разделам. */
@Composable
fun CleaningStatsScreen(
    viewModel: HouseCareViewModel,
    onBack: () -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var selectedPlaceId by remember { mutableStateOf<String?>(null) }
    var roomSort by remember { mutableStateOf(RoomSortMode.ByFrequency) }
    var viewingReport by remember { mutableStateOf<ReportView?>(null) }

    val allReports = data.reportViews()
    val reports = if (selectedPlaceId == null) {
        allReports
    } else {
        allReports.filter { it.placeId == selectedPlaceId }
    }

    val today = LocalDate.now()
    val weekStart = today.with(java.time.DayOfWeek.MONDAY).periodStartMs()
    val monthStart = today.withDayOfMonth(1).periodStartMs()

    HouseCareScaffold(title = "Статистика уборки", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (data.places.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChoiceChip(
                            label = "Все объекты",
                            selected = selectedPlaceId == null,
                            onClick = { selectedPlaceId = null },
                        )
                        data.places.forEach { place ->
                            ChoiceChip(
                                label = place.name,
                                selected = selectedPlaceId == place.id,
                                onClick = { selectedPlaceId = place.id },
                            )
                        }
                    }
                }
            }

            if (reports.isEmpty()) {
                item {
                    GroupCard {
                        Text(
                            "Пока нет завершённых уборок. Сфотографируйте раздел «до» и «после» — и здесь появятся хронометраж недели, рейтинг самых убираемых комнат и фото-отчёты.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                val weekReports = reports.filter { it.report.dateMs >= weekStart }
                val monthReports = reports.filter { it.report.dateMs >= monthStart }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TotalCard(
                            label = "За неделю",
                            durationMs = weekReports.sumOf { it.report.durationMs },
                            count = weekReports.size,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        TotalCard(
                            label = "За месяц",
                            durationMs = monthReports.sumOf { it.report.durationMs },
                            count = monthReports.size,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        TotalCard(
                            label = "Всего",
                            durationMs = reports.sumOf { it.report.durationMs },
                            count = reports.size,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                // Активность по дням недели за последние 30 дней
                val sinceMs = today.minusDays(29).periodStartMs()
                val recentReports = reports.filter { it.report.dateMs >= sinceMs }
                if (recentReports.isNotEmpty()) {
                    item {
                        WeekdayActivityCard(recentReports = recentReports)
                    }
                }

                // Рейтинг комнат: какие убираются чаще и дольше
                val roomStats = reports
                    .groupBy { it.zone to it.floor }
                    .map { (key, list) ->
                        val totalMs = list.sumOf { it.report.durationMs }
                        val avgMs = if (list.isNotEmpty()) totalMs / list.size else 0L
                        RoomStatItem(
                            zoneName = key.first,
                            floorName = key.second,
                            count = list.size,
                            totalDurationMs = totalMs,
                            avgDurationMs = avgMs,
                        )
                    }
                    .let { list ->
                        when (roomSort) {
                            RoomSortMode.ByFrequency -> list.sortedWith(
                                compareByDescending<RoomStatItem> { it.count }.thenByDescending { it.totalDurationMs },
                            )
                            RoomSortMode.ByDuration -> list.sortedWith(
                                compareByDescending<RoomStatItem> { it.totalDurationMs }.thenByDescending { it.count },
                            )
                        }
                    }

                item {
                    RoomRankingCard(
                        rooms = roomStats,
                        sortMode = roomSort,
                        onSortChange = { roomSort = it },
                    )
                }

                // Самые трудоёмкие разделы
                val taskStats = reports
                    .groupBy { "${it.zone} · ${it.task}" }
                    .map { (label, list) ->
                        val totalMs = list.sumOf { it.report.durationMs }
                        val avgMs = if (list.isNotEmpty()) totalMs / list.size else 0L
                        Triple(label, list.size, totalMs to avgMs)
                    }
                    .sortedByDescending { (_, _, times) -> times.first }
                    .take(8)

                if (taskStats.isNotEmpty()) {
                    item { HouseCareSectionTitle("Самые трудоёмкие разделы") }
                    items(taskStats, key = { it.first }) { (label, count, times) ->
                        val (totalMs, avgMs) = times
                        StatLine(
                            title = label,
                            subtitle = "$count ${pluralRu(count, "уборка", "уборки", "уборок")} · в среднем ${formatDurationMs(avgMs)}",
                            value = formatDurationMs(totalMs),
                        )
                    }
                }

                item { HouseCareSectionTitle("Последние уборки и фото") }
                items(reports.take(30), key = { it.report.id }) { view ->
                    ReportLogRow(
                        view = view,
                        onOpenPhotos = { viewingReport = view },
                    )
                }
            }
        }
    }

    viewingReport?.let { view ->
        ReportPhotosDialog(
            report = view.report,
            title = "${view.zone} · ${view.task}",
            onDismiss = { viewingReport = null },
        )
    }
}

private data class ReportView(
    val report: CleaningReport,
    val task: String,
    val zone: String,
    val floor: String,
    val placeId: String,
)

private data class RoomStatItem(
    val zoneName: String,
    val floorName: String,
    val count: Int,
    val totalDurationMs: Long,
    val avgDurationMs: Long,
)

private fun HouseCareData.reportViews(): List<ReportView> =
    tasks.flatMap { task ->
        val zone = zoneById(task.zoneId) ?: return@flatMap emptyList()
        val floorObj = floorById(zone.floorId)
        val floor = floorObj?.name ?: ""
        val placeId = floorObj?.placeId ?: ""
        task.finishedReports().map { ReportView(it, task.name, zone.name, floor, placeId) }
    }.sortedByDescending { it.report.dateMs }

private fun LocalDate.periodStartMs(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun weekDayOf(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).dayOfWeek.value

@Composable
private fun TotalCard(
    label: String,
    durationMs: Long,
    count: Int,
    modifier: Modifier = Modifier,
) {
    GroupCard(
        modifier = modifier,
        container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                formatDurationMs(durationMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$count ${pluralRu(count, "уборка", "уборки", "уборок")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekdayActivityCard(recentReports: List<ReportView>) {
    val byDay = (1..7).map { day ->
        val list = recentReports.filter { weekDayOf(it.report.dateMs) == day }
        Triple(day, list.size, list.sumOf { it.report.durationMs })
    }.filter { (_, count, _) -> count > 0 }

    val maxDuration = byDay.maxOfOrNull { it.third }?.coerceAtLeast(1L) ?: 1L
    val bestDay = byDay.maxByOrNull { it.third }?.first

    GroupCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "По дням недели (за 30 дней)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            byDay.forEach { (day, count, durationMs) ->
                val isBest = day == bestDay && byDay.size > 1
                val fraction = (durationMs.toFloat() / maxDuration.toFloat()).coerceIn(0.08f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            WeekDays.fullLabel(day),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isBest) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        if (isBest) {
                            StatusPill("самый активный", PillTone.Done)
                        }
                        Text(
                            "$count ${pluralRu(count, "раз", "раза", "раз")} · ${formatDurationMs(durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isBest) cleaningGreen else MaterialTheme.colorScheme.primary,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomRankingCard(
    rooms: List<RoomStatItem>,
    sortMode: RoomSortMode,
    onSortChange: (RoomSortMode) -> Unit,
) {
    val maxCount = rooms.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val maxDuration = rooms.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L

    GroupCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Рейтинг комнат",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Какие комнаты убираются чаще и сколько времени требуют",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    label = "По частоте уборок",
                    selected = sortMode == RoomSortMode.ByFrequency,
                    modifier = Modifier.weight(1f),
                    onClick = { onSortChange(RoomSortMode.ByFrequency) },
                )
                ChoiceChip(
                    label = "По общему времени",
                    selected = sortMode == RoomSortMode.ByDuration,
                    modifier = Modifier.weight(1f),
                    onClick = { onSortChange(RoomSortMode.ByDuration) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            rooms.forEachIndexed { index, item ->
                val fraction = when (sortMode) {
                    RoomSortMode.ByFrequency -> (item.count.toFloat() / maxCount.toFloat()).coerceIn(0.08f, 1f)
                    RoomSortMode.ByDuration -> (item.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0.08f, 1f)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BadgeCircle(
                            size = 26.dp,
                            container = if (index == 0) {
                                cleaningGreen.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            },
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 0) cleaningGreen else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.zoneName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    if (item.floorName.isNotBlank()) {
                                        append(item.floorName)
                                        append(" · ")
                                    }
                                    append("${item.count} ${pluralRu(item.count, "раз", "раза", "раз")}")
                                    append(" · в среднем ${formatDurationMs(item.avgDurationMs)}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(
                            formatDurationMs(item.totalDurationMs),
                            if (index == 0) PillTone.Done else PillTone.Muted,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (index == 0) cleaningGreen else MaterialTheme.colorScheme.primary,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(title: String, subtitle: String, value: String) {
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(value, PillTone.Done)
        }
    }
}

@Composable
private fun ReportLogRow(
    view: ReportView,
    onOpenPhotos: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasPhotos = HouseCarePhotoStorage.resolve(context, view.report.beforePhoto) != null ||
        HouseCarePhotoStorage.resolve(context, view.report.afterPhoto) != null
    GroupCard(
        modifier = if (hasPhotos) {
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onOpenPhotos)
        } else {
            Modifier
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReportThumbnailsPair(report = view.report, onClick = onOpenPhotos)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOfNotNull(
                        view.floor.takeIf { it.isNotBlank() },
                        view.zone,
                        view.task,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(formatDateMs(view.report.dateMs))
                        val times = listOfNotNull(
                            view.report.beforeAtMs?.let { "до ${formatTimeMs(it)}" },
                            view.report.afterAtMs?.let { "после ${formatTimeMs(it)}" },
                        ).joinToString(" · ")
                        if (times.isNotBlank()) {
                            append(" · ")
                            append(times)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(formatDurationMs(view.report.durationMs), PillTone.Done)
        }
    }
}
