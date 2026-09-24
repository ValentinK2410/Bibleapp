package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.data.DailyJournalEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal const val JOURNAL_ZOOM_MONTHS = 0
internal const val JOURNAL_ZOOM_MONTH = 1
internal const val JOURNAL_ZOOM_WEEKS = 2
internal const val JOURNAL_ZOOM_DAYS = 3
internal const val JOURNAL_ZOOM_HOURS = 4

private val journalLocale: Locale = Locale("ru")

internal fun journalZoomTitle(level: Int, focus: LocalDate): String {
    val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", journalLocale)
    return when (level) {
        JOURNAL_ZOOM_MONTHS -> focus.year.toString()
        JOURNAL_ZOOM_MONTH -> focus.format(monthFmt)
        JOURNAL_ZOOM_WEEKS -> {
            val start = focus.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val end = start.plusDays(6)
            "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(DateTimeFormatter.ofPattern("LLLL", journalLocale))}"
        }
        JOURNAL_ZOOM_DAYS -> focus.format(DateTimeFormatter.ofPattern("d MMMM yyyy", journalLocale))
        else -> focus.format(DateTimeFormatter.ofPattern("d MMMM, часы", journalLocale))
    }
}

@Composable
internal fun JournalCalendar(
    entries: List<DailyJournalEntry>,
    zoomLevel: Int,
    onZoomLevel: (Int) -> Unit,
    focusDate: LocalDate,
    onFocusDate: (LocalDate) -> Unit,
    onOpenEntry: (DailyJournalEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byDay = remember(entries) { entries.groupBy { it.dayKey } }
    Box(
        modifier
            .fillMaxSize()
            .journalPinchZoom(
                onZoomIn = { if (zoomLevel < JOURNAL_ZOOM_HOURS) onZoomLevel(zoomLevel + 1) },
                onZoomOut = { if (zoomLevel > JOURNAL_ZOOM_MONTHS) onZoomLevel(zoomLevel - 1) },
            ),
    ) {
        when (zoomLevel) {
            JOURNAL_ZOOM_MONTHS -> MonthsGrid(
                year = focusDate.year,
                byDay = byDay,
                focus = focusDate,
                onMonth = { month ->
                    onFocusDate(month.atDay(1).coerceDay(focusDate.dayOfMonth))
                    onZoomLevel(JOURNAL_ZOOM_MONTH)
                },
            )
            JOURNAL_ZOOM_MONTH -> MonthDaysGrid(
                month = YearMonth.from(focusDate),
                byDay = byDay,
                focus = focusDate,
                onDay = { day ->
                    onFocusDate(day)
                    onZoomLevel(JOURNAL_ZOOM_WEEKS)
                },
            )
            JOURNAL_ZOOM_WEEKS -> WeeksGrid(
                focus = focusDate,
                byDay = byDay,
                onDay = { day ->
                    onFocusDate(day)
                    onZoomLevel(JOURNAL_ZOOM_DAYS)
                },
            )
            JOURNAL_ZOOM_DAYS -> DaysGrid(
                focus = focusDate,
                byDay = byDay,
                onDay = { day ->
                    onFocusDate(day)
                    onZoomLevel(JOURNAL_ZOOM_HOURS)
                },
                onOpenEntry = onOpenEntry,
            )
            else -> HoursGrid(
                focus = focusDate,
                entries = byDay[focusDate.toString()].orEmpty(),
                onOpenEntry = onOpenEntry,
            )
        }
    }
}

private fun LocalDate.coerceDay(day: Int): LocalDate {
    val max = lengthOfMonth()
    return withDayOfMonth(day.coerceIn(1, max))
}

private fun Modifier.journalPinchZoom(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier =
    pointerInput(onZoomIn, onZoomOut) {
        awaitPointerEventScope {
            var accum = 1f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed < 2) {
                    accum = 1f
                    continue
                }
                event.changes.forEach { it.consume() }
                accum *= event.calculateZoom()
                when {
                    accum > 1.18f -> {
                        onZoomIn()
                        accum = 1f
                    }
                    accum < 0.84f -> {
                        onZoomOut()
                        accum = 1f
                    }
                }
            }
        }
    }

@Composable
private fun MonthsGrid(
    year: Int,
    byDay: Map<String, List<DailyJournalEntry>>,
    focus: LocalDate,
    onMonth: (YearMonth) -> Unit,
) {
    val months = remember(year) { (1..12).map { YearMonth.of(year, it) } }
    val fmt = remember { DateTimeFormatter.ofPattern("LLLL", journalLocale) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(months, key = { it.toString() }) { month ->
            val count = byDay.keys.count { it.startsWith(month.toString()) }
            val selected = month.month == focus.month && month.year == focus.year
            CalendarCell(
                selected = selected,
                onClick = { onMonth(month) },
                modifier = Modifier.height(96.dp),
            ) {
                Text(
                    month.atDay(1).format(fmt).replaceFirstChar { it.titlecase(journalLocale) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count > 0) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDaysGrid(
    month: YearMonth,
    byDay: Map<String, List<DailyJournalEntry>>,
    focus: LocalDate,
    onDay: (LocalDate) -> Unit,
) {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = remember(month) { (0 until 42).map { start.plusDays(it.toLong()) } }
    Column(Modifier.fillMaxSize()) {
        WeekdayHeader()
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            gridItems(days, key = { it.toString() }) { day ->
                val inMonth = day.month == month.month
                val count = byDay[day.toString()]?.size ?: 0
                CalendarCell(
                    selected = day == focus,
                    onClick = { onDay(day) },
                    modifier = Modifier.heightIn(min = 52.dp),
                    dimmed = !inMonth,
                ) {
                    Text(
                        "${day.dayOfMonth}",
                        fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                        color = if (inMonth) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        },
                    )
                    if (count > 0) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .height(6.dp)
                                .fillMaxWidth(0.35f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeksGrid(
    focus: LocalDate,
    byDay: Map<String, List<DailyJournalEntry>>,
    onDay: (LocalDate) -> Unit,
) {
    val month = YearMonth.from(focus)
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = remember(month) {
        (0 until 6).map { w -> (0 until 7).map { d -> first.plusDays((w * 7 + d).toLong()) } }
            .filter { week -> week.any { it.month == month.month } }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(weeks, key = { it.first().toString() }) { week ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(6.dp),
            ) {
                Text(
                    "${week.first().dayOfMonth}–${week.last().dayOfMonth}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        val count = byDay[day.toString()]?.size ?: 0
                        CalendarCell(
                            selected = day == focus,
                            onClick = { onDay(day) },
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                            dimmed = day.month != month.month,
                        ) {
                            Text("${day.dayOfMonth}", fontWeight = FontWeight.SemiBold)
                            if (count > 0) {
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaysGrid(
    focus: LocalDate,
    byDay: Map<String, List<DailyJournalEntry>>,
    onDay: (LocalDate) -> Unit,
    onOpenEntry: (DailyJournalEntry) -> Unit,
) {
    val start = focus.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = remember(start) { (0 until 7).map { start.plusDays(it.toLong()) } }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE, d MMM", journalLocale) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days, key = { it.toString() }) { day ->
            val dayEntries = byDay[day.toString()].orEmpty()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (day == focus) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    )
                    .clickable { onDay(day) }
                    .padding(12.dp),
            ) {
                Text(day.format(fmt), fontWeight = FontWeight.SemiBold)
                dayEntries.take(4).forEach { entry ->
                    Text(
                        entry.title.ifBlank { entry.body }.ifBlank { "Запись" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenEntry(entry) }
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HoursGrid(
    focus: LocalDate,
    entries: List<DailyJournalEntry>,
    onOpenEntry: (DailyJournalEntry) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(24) { hour ->
            val hourEntries = entries.filter { entry ->
                Instant.ofEpochMilli(entry.createdAt).atZone(zone).hour == hour
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "%02d:00".format(hour),
                    modifier = Modifier.padding(end = 10.dp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    if (hourEntries.isEmpty()) {
                        Text(
                            focus.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                    } else {
                        hourEntries.forEach { entry ->
                            Text(
                                entry.title.ifBlank { entry.body }.ifBlank { "Запись" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEntry(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val names = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        names.forEach { name ->
            Text(
                name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarCell(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    dimmed -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
