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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.housecare.CleaningReport
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.WeekDays
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Сколько времени уходит на уборку: по неделям, дням недели и зонам. */
@Composable
fun CleaningStatsScreen(
    viewModel: HouseCareViewModel,
    onBack: () -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val reports = data.reportViews()
    val today = LocalDate.now()
    val weekStart = today.with(java.time.DayOfWeek.MONDAY).periodStartMs()
    val monthStart = today.withDayOfMonth(1).periodStartMs()

    HouseCareScaffold(title = "Статистика уборки", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (reports.isEmpty()) {
                item {
                    Text(
                        "Пока нет завершённых уборок. Сфотографируйте раздел «после» — и время появится здесь.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TotalCard("За неделю", reports.sumWeek(weekStart), Modifier.weight(1f))
                        TotalCard("За месяц", reports.sumWeek(monthStart), Modifier.weight(1f))
                        TotalCard("Всего", reports.sumOf { it.report.durationMs }, Modifier.weight(1f))
                    }
                }
                item { HouseCareSectionTitle("По дням недели (30 дней)") }
                val sinceMs = today.minusDays(29).periodStartMs()
                (1..7).forEach { day ->
                    val dayReports = reports.filter {
                        it.report.dateMs >= sinceMs && weekDayOf(it.report.dateMs) == day
                    }
                    if (dayReports.isNotEmpty()) {
                        item {
                            StatLine(
                                title = WeekDays.fullLabel(day),
                                value = "${dayReports.size} уборок · ${formatDurationMs(dayReports.sumOf { it.report.durationMs })}",
                            )
                        }
                    }
                }
                item { HouseCareSectionTitle("По зонам") }
                items(reports.groupBy { it.zone }.toList().sortedByDescending { (_, list) -> list.sumOf { it.report.durationMs } }, key = { it.first }) { (zone, list) ->
                    StatLine(
                        title = zone,
                        value = "${list.size} раз · ${formatDurationMs(list.sumOf { it.report.durationMs })}",
                    )
                }
                item { HouseCareSectionTitle("Последние уборки") }
                items(reports.take(30), key = { it.report.id }) { view ->
                    StatLine(
                        title = "${view.floor} · ${view.zone} · ${view.task}",
                        value = "${formatDateMs(view.report.dateMs)} · ${formatDurationMs(view.report.durationMs)}",
                    )
                }
            }
        }
    }
}

private data class ReportView(
    val report: CleaningReport,
    val task: String,
    val zone: String,
    val floor: String,
)

private fun HouseCareData.reportViews(): List<ReportView> =
    tasks.flatMap { task ->
        val zone = zoneById(task.zoneId) ?: return@flatMap emptyList()
        val floor = floorById(zone.floorId)?.name ?: ""
        task.finishedReports().map { ReportView(it, task.name, zone.name, floor) }
    }.sortedByDescending { it.report.dateMs }

private fun List<ReportView>.sumWeek(fromMs: Long): Long =
    filter { it.report.dateMs >= fromMs }.sumOf { it.report.durationMs }

private fun LocalDate.periodStartMs(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun weekDayOf(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).dayOfWeek.value

@Composable
private fun TotalCard(label: String, durationMs: Long, modifier: Modifier = Modifier) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDurationMs(durationMs), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StatLine(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
