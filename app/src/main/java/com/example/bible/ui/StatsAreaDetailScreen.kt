package com.example.bible.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.data.AppSectionUsage
import com.example.bible.data.AppUsageEvents

@Composable
fun StatsAreaDetailScreen(
    area: String,
    sectionUsage: Map<String, Pair<Int, Long>>,
    usageEvents: List<AppUsageEvents.Event>,
    onOpenSection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var band by remember { mutableStateOf<AppSectionUsage.Band?>(null) }
    val rows = remember(sectionUsage, area) { AppSectionUsage.rowsForArea(sectionUsage, area) }
    val areaEvents = remember(usageEvents, area) { AppUsageEvents.eventsForArea(usageEvents, area) }
    val days = remember(areaEvents) { AppUsageEvents.countsByDay(areaEvents, days = 14) }
    val types = remember(areaEvents) { AppUsageEvents.countsByType(areaEvents) }
    val sectionMax = rows.maxOfOrNull { it.opens } ?: 0
    val bands = rows.map { AppSectionUsage.band(it.opens, sectionMax) }
    val visible = rows.filter { band == null || AppSectionUsage.band(it.opens, sectionMax) == band }
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.secondary

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
            ) {
                Column(
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                            ),
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(area, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Все открытия и действия в этом разделе. Нажмите строку, чтобы перейти.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    StatsBarChart(days = days, color = accent)
                    StatsTypeBars(types = types, accent = accent)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BandChip("часто", bands.count { it == AppSectionUsage.Band.OFTEN }, scheme.primary)
                        BandChip("иногда", bands.count { it == AppSectionUsage.Band.SOMETIMES }, scheme.tertiary)
                        BandChip("редко", bands.count { it == AppSectionUsage.Band.RARE }, scheme.onSurfaceVariant)
                        BandChip("пусто", bands.count { it == AppSectionUsage.Band.NEVER }, scheme.outline)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = band == null, onClick = { band = null }, label = { Text("Все") })
                        AppSectionUsage.Band.entries.forEach { item ->
                            FilterChip(
                                selected = band == item,
                                onClick = { band = item },
                                label = { Text(item.label) },
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        Text(
                            "Нет разделов с таким фильтром",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    visible.forEach { row ->
                        val itemBand = AppSectionUsage.band(row.opens, sectionMax)
                        AreaRowCard(
                            title = row.section.title,
                            band = itemBand,
                            fraction = if (sectionMax <= 0) 0f else row.opens.toFloat() / sectionMax,
                            detail = if (row.opens <= 0) {
                                "ещё не открывали"
                            } else {
                                "${row.opens} откр. · ${lastOpenedRu(row.lastAt)}"
                            },
                            onClick = { onOpenSection(row.section.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandChip(label: String, count: Int, color: Color) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            "$label $count",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AreaRowCard(
    title: String,
    band: AppSectionUsage.Band,
    fraction: Float,
    detail: String,
    onClick: () -> Unit,
) {
    val color = when (band) {
        AppSectionUsage.Band.OFTEN -> MaterialTheme.colorScheme.primary
        AppSectionUsage.Band.SOMETIMES -> MaterialTheme.colorScheme.tertiary
        AppSectionUsage.Band.RARE -> MaterialTheme.colorScheme.onSurfaceVariant
        AppSectionUsage.Band.NEVER -> MaterialTheme.colorScheme.outline
    }
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .then(Modifier.clickableSafe(onClick)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    if (band == AppSectionUsage.Band.NEVER) "не открывали" else band.label,
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.14f),
                drawStopIndicator = {},
            )
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.clickableSafe(onClick: () -> Unit): Modifier =
    clickable(onClick = onClick)

private fun lastOpenedRu(timestamp: Long): String {
    if (timestamp <= 0L) return "ещё не открывали"
    val days = ((System.currentTimeMillis() - timestamp) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "сегодня"
        days == 1 -> "вчера"
        else -> "$days дн. назад"
    }
}
