package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.AppSectionUsage
import com.example.bible.data.AppUsageEvents
import com.example.bible.data.BibleCoverage
import com.example.bible.data.BibleReadingStats

data class StatsAreaCardModel(
    val area: String,
    val title: String,
    val subtitle: String,
    val primaryValue: String,
    val secondaryValue: String,
    val sparkline: List<Int>,
    val accent: Color,
    val icon: ImageVector,
)

@Composable
fun StatsHubScreen(
    modifier: Modifier = Modifier,
    readKeys: Set<String>,
    listenKeys: Set<String>,
    history: List<com.example.bible.data.HistoryEntry>,
    trace: List<com.example.bible.data.ReadingTraceEntry>,
    copies: List<BibleReadingStats.VerseCopyStat>,
    sectionUsage: Map<String, Pair<Int, Long>>,
    usageEvents: List<AppUsageEvents.Event>,
    initialTrackId: String,
    onOpenArea: (String) -> Unit,
) {
    val trackId = BibleCoverage.trackById(initialTrackId)?.id ?: BibleCoverage.tracks.first().id
    val summary = remember(readKeys, listenKeys, trackId) {
        BibleCoverage.summarize(readKeys, listenKeys).firstOrNull { it.track.id == trackId }
    }
    val stats = remember(trackId, history, trace, copies, listenKeys) {
        BibleReadingStats.build(trackId, history, trace, copies, listenKeys)
    }
    val scheme = MaterialTheme.colorScheme
    val cards = remember(summary, stats, sectionUsage, usageEvents, scheme) {
        buildStatsAreaCards(summary, stats, sectionUsage, usageEvents, scheme.primary, scheme.secondary, scheme.tertiary)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Каждое открытие, чтение, озвучка и копирование сохраняется здесь. Нажмите блок — откроется подробная статистика.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        item {
            OverallPulseCard(
                events = usageEvents,
                sectionUsage = sectionUsage,
                summary = summary,
            )
        }
        items(cards, key = { it.area }) { card ->
            StatsAreaSummaryCard(
                model = card,
                onClick = { onOpenArea(card.area) },
            )
        }
    }
}

@Composable
private fun OverallPulseCard(
    events: List<AppUsageEvents.Event>,
    sectionUsage: Map<String, Pair<Int, Long>>,
    summary: BibleCoverage.TrackSummary?,
) {
    val scheme = MaterialTheme.colorScheme
    val days = remember(events) { AppUsageEvents.countsByDay(events, days = 14) }
    val opens = sectionUsage.values.sumOf { it.first }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(scheme.primary.copy(alpha = 0.14f), scheme.tertiary.copy(alpha = 0.08f)),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Активность за 14 дней", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            StatsSparkline(
                values = days.map { it.count },
                color = scheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("События", events.size.toString(), Modifier.weight(1f))
                MiniStat("Открытия", opens.toString(), Modifier.weight(1f))
                MiniStat(
                    "Глав",
                    "${summary?.readChapters ?: 0}/${summary?.listenChapters ?: 0}",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatsAreaSummaryCard(
    model: StatsAreaCardModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(model.accent.copy(alpha = 0.14f), Color.Transparent),
                    ),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(model.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(model.icon, contentDescription = null, tint = model.accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(model.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        model.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Подробнее",
                    tint = model.accent,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.primaryValue,
                        color = model.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 28.sp,
                    )
                    Text(
                        model.secondaryValue,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatsSparkline(
                    values = model.sparkline,
                    color = model.accent,
                    modifier = Modifier
                        .width(96.dp)
                        .height(40.dp),
                )
            }
        }
    }
}

@Composable
fun StatsSparkline(
    values: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val step = if (values.size <= 1) size.width else size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - (value.toFloat() / max) * size.height * 0.9f - size.height * 0.05f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.9f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        values.forEachIndexed { index, value ->
            val x = index * step
            val barH = (value.toFloat() / max) * size.height * 0.85f
            drawRoundRect(
                color = color.copy(alpha = 0.18f),
                topLeft = Offset(x - 2.dp.toPx(), size.height - barH),
                size = Size(4.dp.toPx(), barH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
fun StatsBarChart(
    days: List<AppUsageEvents.DayCount>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val max = (days.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("События по дням", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .padding(10.dp),
        ) {
            val pad = 8.dp.toPx()
            val chartW = size.width - pad * 2
            val chartH = size.height - pad * 2
            val gap = 4.dp.toPx()
            val barW = if (days.isEmpty()) 0f else (chartW - gap * (days.size - 1)) / days.size
            days.forEachIndexed { index, day ->
                val h = if (day.count <= 0) 2.dp.toPx() else (day.count.toFloat() / max) * chartH
                val x = pad + index * (barW + gap)
                val y = pad + chartH - h
                drawRoundRect(
                    color = if (day.count > 0) color else color.copy(alpha = 0.18f),
                    topLeft = Offset(x, y),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("14 дней назад", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text("сегодня", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatsTypeBars(
    types: List<AppUsageEvents.TypeCount>,
    accent: Color,
) {
    if (types.isEmpty()) return
    val max = types.maxOf { it.count }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Что фиксируется", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        types.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row {
                    Text(item.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("${item.count}", fontWeight = FontWeight.Bold, color = accent)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.count.toFloat() / max)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
            }
        }
    }
}

private fun buildStatsAreaCards(
    summary: BibleCoverage.TrackSummary?,
    stats: BibleReadingStats.Snapshot,
    sectionUsage: Map<String, Pair<Int, Long>>,
    usageEvents: List<AppUsageEvents.Event>,
    primary: Color,
    secondary: Color,
    tertiary: Color,
): List<StatsAreaCardModel> {
    val accents = listOf(primary, tertiary, secondary, primary.copy(alpha = 0.85f), tertiary.copy(alpha = 0.9f))
    return AppSectionUsage.statsTabAreas().mapIndexed { index, area ->
        val areaEvents = AppUsageEvents.eventsForArea(usageEvents, area)
        val spark = AppUsageEvents.countsByDay(areaEvents, days = 14).map { it.count }
        val rows = if (area == AppSectionUsage.BIBLE_AREA) {
            AppSectionUsage.bibleRows(sectionUsage)
        } else {
            AppSectionUsage.rowsForArea(sectionUsage, area)
        }
        val opens = rows.sumOf { it.opens }
        val never = rows.count { it.opens <= 0 }
        val accent = accents[index % accents.size]
        if (area == AppSectionUsage.BIBLE_AREA) {
            val readPct = if (summary == null || summary.totalChapters == 0) {
                0
            } else {
                BibleCoverage.percent(summary.readChapters, summary.totalChapters)
            }
            val listenPct = if (summary == null || summary.totalChapters == 0) {
                0
            } else {
                BibleCoverage.percent(summary.listenChapters, summary.totalChapters)
            }
            StatsAreaCardModel(
                area = area,
                title = "Библия",
                subtitle = "Чтение, озвучка, книги и экраны Библии",
                primaryValue = "$readPct% · $listenPct%",
                secondaryValue = buildString {
                    append("прочитано / прослушано")
                    if (stats.topBooks.isNotEmpty()) append(" · топ: ${stats.topBooks.first().bookName}")
                    append(" · ${stats.daysActive} дн.")
                },
                sparkline = spark,
                accent = accent,
                icon = Icons.AutoMirrored.Filled.MenuBook,
            )
        } else {
            StatsAreaCardModel(
                area = area,
                title = area,
                subtitle = when (area) {
                    "Медиа" -> "Видео, аудио, песни, музыкант"
                    "Жизнь" -> "Карты, путешествия, контакты, библиотека"
                    "Церковь" -> "Участники, учёт, документы"
                    "ИИ" -> "DeepSeek и GigaChat"
                    "Детям" -> "Азбука, игры, природа"
                    "Эксперимент" -> "Камера, датчики, сеть"
                    "Настройки" -> "Сеть, бэкап, загрузки"
                    else -> "Разделы приложения"
                },
                primaryValue = if (opens > 0) "$opens откр." else "пусто",
                secondaryValue = "${rows.size} разделов · не открывали $never",
                sparkline = spark,
                accent = accent,
                icon = iconForArea(area),
            )
        }
    }
}

private fun iconForArea(area: String): ImageVector = when (area) {
    "Медиа" -> Icons.Filled.PermMedia
    "Жизнь" -> Icons.Filled.Explore
    "Церковь" -> Icons.Filled.Church
    "ИИ" -> Icons.Filled.AutoAwesome
    "Детям" -> Icons.Filled.School
    "Эксперимент" -> Icons.Filled.FlashOn
    "Настройки" -> Icons.Filled.Settings
    else -> Icons.Filled.Apps
}
