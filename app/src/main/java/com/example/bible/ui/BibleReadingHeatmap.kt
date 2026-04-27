package com.example.bible.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.HistoryEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Сколько раз в каждый день были открыты места Писания (по [HistoryEntry.timestamp]). */
fun readingHistoryToDayCounts(history: List<HistoryEntry>): Map<LocalDate, Int> {
    val zone = ZoneId.systemDefault()
    val map = mutableMapOf<LocalDate, Int>()
    for (e in history) {
        val d = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()
        map[d] = (map[d] ?: 0) + 1
    }
    return map
}

private data class HeatWeekColumn(
    val monthLabel: String?,
    val cells: List<HeatCell?>,
)

private data class HeatCell(
    val date: LocalDate,
    val count: Int,
)

@Composable
fun BibleReadingHeatmapCard(
    activityByDay: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val years = remember(activityByDay, today) {
        val minY = activityByDay.keys.minOfOrNull { it.year } ?: today.year
        val start = maxOf(minY, today.year - 7).coerceAtMost(today.year)
        if (start > today.year) listOf(today.year)
        else (start..today.year).toList().reversed()
    }
    var selectedYear by remember(years) { mutableIntStateOf(years.firstOrNull() ?: today.year) }

    val yearTotal = remember(activityByDay, selectedYear) {
        activityByDay.entries.sumOf { (d, n) -> if (d.year == selectedYear) n else 0 }
    }

    val maxInYear = remember(activityByDay, selectedYear) {
        activityByDay.entries
            .filter { (d, _) -> d.year == selectedYear }
            .maxOfOrNull { it.value }
            ?.coerceAtLeast(1) ?: 1
    }

    val columns = remember(activityByDay, selectedYear) {
        buildHeatColumns(selectedYear, activityByDay, today)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            when {
                selectedYear == today.year ->
                    "$yearTotal открытий с начала года"
                else ->
                    "$yearTotal открытий за $selectedYear год"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                ),
            ) {
                Column(Modifier.padding(10.dp)) {
                    HeatmapGrid(
                        columns = columns,
                        maxInYear = maxInYear,
                    )
                    Spacer(Modifier.height(10.dp))
                    HeatmapLegend()
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                years.forEach { y ->
                    val sel = y == selectedYear
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedYear = y },
                        shape = RoundedCornerShape(8.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ) {
                        Text(
                            "$y",
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(
    columns: List<HeatWeekColumn>,
    maxInYear: Int,
) {
    val cell = 11.dp
    val gap = 3.dp
    val monthH = 16.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        val rowStep = cell + gap
        Column {
            Spacer(Modifier.height(monthH))
            Text(
                "Пн",
                modifier = Modifier.width(18.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(rowStep * 2))
            Text(
                "Ср",
                modifier = Modifier.width(18.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(rowStep * 2))
            Text(
                "Пт",
                modifier = Modifier.width(18.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
        }
        columns.forEach { col ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = gap),
            ) {
                Box(
                    modifier = Modifier
                        .height(monthH)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    col.monthLabel?.let { m ->
                        Text(
                            m,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }
                }
                col.cells.forEach { cellData ->
                    val level = cellData?.let { activityLevel(it.count, maxInYear) } ?: 0
                    Box(
                        modifier = Modifier
                            .padding(bottom = gap)
                            .size(cell)
                            .clip(RoundedCornerShape(2.dp))
                            .background(heatmapColor(level)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Реже",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
        Spacer(Modifier.width(6.dp))
        (0..4).forEach { level ->
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(heatmapColor(level)),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "Чаще",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun heatmapColor(level: Int): Color {
    if (level <= 0) {
        return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val base = MaterialTheme.colorScheme.primary
    return when (level) {
        1 -> base.copy(alpha = 0.28f)
        2 -> base.copy(alpha = 0.45f)
        3 -> base.copy(alpha = 0.65f)
        else -> base.copy(alpha = 0.9f)
    }
}

private fun activityLevel(count: Int, maxInYear: Int): Int {
    if (count <= 0 || maxInYear <= 0) return 0
    val t1 = maxOf(1, (maxInYear + 3) / 4)
    val t2 = maxOf(t1 + 1, (maxInYear + 1) / 2)
    val t3 = maxOf(t2 + 1, (maxInYear * 3 + 3) / 4)
    return when {
        count <= t1 -> 1
        count <= t2 -> 2
        count <= t3 -> 3
        else -> 4
    }
}

private fun buildHeatColumns(
    year: Int,
    counts: Map<LocalDate, Int>,
    today: LocalDate,
): List<HeatWeekColumn> {
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)
    val displayEnd = if (year == today.year) minOf(yearEnd, today) else yearEnd
    if (displayEnd.isBefore(yearStart)) return emptyList()

    val firstMonday = yearStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val ru = Locale.forLanguageTag("ru")

    val columns = mutableListOf<HeatWeekColumn>()
    var weekStart = firstMonday
    while (!weekStart.isAfter(displayEnd)) {
        val cells = (0..6).map { d ->
            val date = weekStart.plusDays(d.toLong())
            when {
                date.isBefore(yearStart) || date.isAfter(displayEnd) -> null
                else -> HeatCell(date, counts[date] ?: 0)
            }
        }
        val monthLabel = cells.mapNotNull { it?.date }
            .firstOrNull { it.dayOfMonth == 1 }
            ?.let { it.month.getDisplayName(TextStyle.SHORT, ru).replaceFirstChar { c -> c.titlecase(ru) } }
        columns.add(HeatWeekColumn(monthLabel, cells))
        weekStart = weekStart.plusWeeks(1)
        if (ChronoUnit.WEEKS.between(firstMonday, weekStart) > 56) break
    }
    return columns
}
