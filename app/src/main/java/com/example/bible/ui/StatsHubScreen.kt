package com.example.bible.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
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

/** Пара цветов градиента раздела: одинаковая на хабе и в подробной статистике. */
internal fun statsAreaGradient(area: String): List<Color> = when (area) {
    AppSectionUsage.BIBLE_AREA -> listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    "Медиа" -> listOf(Color(0xFFEC4899), Color(0xFFF97316))
    "Жизнь" -> listOf(Color(0xFF10B981), Color(0xFF06B6D4))
    "Церковь" -> listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
    "ИИ" -> listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
    "Детям" -> listOf(Color(0xFF22C55E), Color(0xFFA3E635))
    "Эксперимент" -> listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))
    "Настройки" -> listOf(Color(0xFF64748B), Color(0xFF334155))
    else -> listOf(Color(0xFF3B82F6), Color(0xFF14B8A6))
}

private val HeroGradient = listOf(Color(0xFF1E1B4B), Color(0xFF4C1D95), Color(0xFF0F766E))
private val ReadRing = Color(0xFF22D3EE)
private val ListenRing = Color(0xFFF472B6)

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
    val cards = remember(summary, stats, sectionUsage, usageEvents) {
        buildStatsAreaCards(summary, stats, sectionUsage, usageEvents)
    }
    val heat = remember(usageEvents) { AppUsageEvents.countsByDay(usageEvents, days = 28) }
    val opens = sectionUsage.values.sumOf { it.first }
    val listenedMs = remember(usageEvents) { AppUsageEvents.listenedMillis(usageEvents) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            StatsHeroCard(summary = summary, stats = stats, totalEvents = usageEvents.size)
        }
        item(key = "kpi") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KpiPill(Icons.Filled.TouchApp, "Открытия", opens.toString(), Color(0xFF6366F1))
                KpiPill(Icons.Filled.Timer, "Чтение", formatHubDuration(stats.dwellSeconds), Color(0xFF0EA5E9))
                KpiPill(
                    Icons.Filled.Bolt,
                    "Аудио",
                    if (listenedMs > 0) AppUsageEvents.formatListened(listenedMs) else "—",
                    Color(0xFFEC4899),
                )
                KpiPill(Icons.Filled.FormatQuote, "Стихов", stats.uniqueVerses.toString(), Color(0xFF10B981))
                KpiPill(Icons.Filled.ContentCopy, "Копий", stats.copies.toString(), Color(0xFFF59E0B))
            }
        }
        item(key = "heat") {
            ActivityHeatmapCard(days = heat)
        }
        item(key = "areas_title") {
            Text(
                "Разделы",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(cards.chunked(2), key = { row -> row.joinToString("|") { it.area } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { card ->
                    AreaGradientTile(
                        model = card,
                        onClick = { onOpenArea(card.area) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatsHeroCard(
    summary: BibleCoverage.TrackSummary?,
    stats: BibleReadingStats.Snapshot,
    totalEvents: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(HeroGradient)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.05f),
                    radius = size.width * 0.6f,
                ),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.9f, size.height * 0.05f),
            )
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ВАШ ПРОГРЕСС",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        summary?.track?.label ?: "Библия",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                GlassBadge(
                    icon = Icons.Filled.LocalFireDepartment,
                    text = "${stats.currentStreak} дн.",
                    tint = Color(0xFFFDBA74),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ProgressRing(
                    done = summary?.readChapters ?: 0,
                    total = summary?.totalChapters ?: 0,
                    color = ReadRing,
                    label = "прочитано",
                )
                ProgressRing(
                    done = summary?.listenChapters ?: 0,
                    total = summary?.totalChapters ?: 0,
                    color = ListenRing,
                    label = "прослушано",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("${summary?.readChapters ?: 0}", "глав прочитано", Modifier.weight(1f))
                HeroStat("${summary?.listenChapters ?: 0}", "глав прослушано", Modifier.weight(1f))
                HeroStat("${stats.daysActive}", "дней с Библией", Modifier.weight(1f))
            }
            Text(
                "Рекорд серии: ${stats.longestStreak} дн. · всего событий: $totalEvents",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ProgressRing(
    done: Int,
    total: Int,
    color: Color,
    label: String,
    size: Dp = 112.dp,
) {
    val target = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val sweep by animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = tween(durationMillis = 1100),
        label = "ring",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = Color.White.copy(alpha = 0.14f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color)),
                    startAngle = -90f,
                    sweepAngle = (360f * sweep).coerceAtLeast(if (done > 0 && sweep > 0f) 8f else 0f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    statsPercentLabel(done, (sweep * 100).toInt()),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GlassBadge(icon: ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KpiPill(icon: ImageVector, label: String, value: String, color: Color) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1)
            Text(label, color = scheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ActivityHeatmapCard(days: List<AppUsageEvents.DayCount>) {
    val scheme = MaterialTheme.colorScheme
    val max = (days.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val activeDays = days.count { it.count > 0 }
    val heatColor = Color(0xFF8B5CF6)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, heatColor.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Активность", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                Text("последние 4 недели", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "$activeDays/28",
                color = heatColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
            )
        }
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    val level = day.count.toFloat() / max
                    Box(
                        Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (day.count <= 0) {
                                    scheme.onSurface.copy(alpha = 0.06f)
                                } else {
                                    heatColor.copy(alpha = 0.25f + 0.75f * level)
                                },
                            ),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("меньше", color = scheme.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            listOf(0.06f, 0.35f, 0.6f, 0.8f, 1f).forEachIndexed { index, alpha ->
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (index == 0) scheme.onSurface.copy(alpha = alpha) else heatColor.copy(alpha = alpha),
                        ),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("больше", color = scheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AreaGradientTile(
    model: StatsAreaCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = statsAreaGradient(model.area)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(model.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("›", color = Color.White.copy(alpha = 0.8f), fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
        Text(
            model.title,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            model.primaryValue,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            lineHeight = 26.sp,
            maxLines = 1,
        )
        StatsSparkline(
            values = model.sparkline,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
        )
        Text(
            model.secondaryValue,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Плавная линия с заливкой градиентом под ней. */
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
        val points = values.mapIndexed { index, value ->
            Offset(index * step, size.height - (value.toFloat() / max) * size.height * 0.85f - size.height * 0.08f)
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val midX = (prev.x + cur.x) / 2
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(color = color, radius = 3.5.dp.toPx(), center = points.last())
    }
}

@Composable
fun StatsBarChart(
    days: List<AppUsageEvents.DayCount>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val maxValue = days.maxOfOrNull { it.count } ?: 0
    val max = maxValue.coerceAtLeast(1)
    val total = days.sumOf { it.count }
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("События по дням", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                Text("пик: $maxValue в день", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text("$total", color = color, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(color.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            val gap = 5.dp.toPx()
            val barW = if (days.isEmpty()) 0f else (size.width - gap * (days.size - 1)) / days.size
            for (line in 1..3) {
                val y = size.height * line / 4f
                drawLine(
                    color = color.copy(alpha = 0.10f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            days.forEachIndexed { index, day ->
                val h = if (day.count <= 0) 3.dp.toPx() else (day.count.toFloat() / max) * size.height
                val x = index * (barW + gap)
                val y = size.height - h
                if (day.count > 0) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(color, color.copy(alpha = 0.45f)),
                            startY = y,
                            endY = size.height,
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2.2f),
                    )
                } else {
                    drawRoundRect(
                        color = color.copy(alpha = 0.16f),
                        topLeft = Offset(x, y),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2.2f),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${days.size} дней назад", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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
    val total = types.sumOf { it.count }.coerceAtLeast(1)
    val palette = listOf(accent, Color(0xFF0EA5E9), Color(0xFFEC4899), Color(0xFFF59E0B))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Что фиксируется", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape),
        ) {
            types.forEachIndexed { index, item ->
                Box(
                    Modifier
                        .weight(item.count.toFloat().coerceAtLeast(0.01f))
                        .fillMaxSize()
                        .background(palette[index % palette.size]),
                )
            }
        }
        types.forEachIndexed { index, item ->
            val color = palette[index % palette.size]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(8.dp))
                Text(item.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(
                    "${item.count * 100 / total}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text("${item.count}", fontWeight = FontWeight.ExtraBold, color = color)
            }
        }
    }
}

private fun statsPercentLabel(done: Int, percent: Int): String =
    if (done > 0 && percent < 1) "<1%" else "$percent%"

internal fun formatHubDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
        hours > 0 -> "$hours ч"
        minutes > 0 -> "$minutes мин"
        else -> "$seconds с"
    }
}

private fun buildStatsAreaCards(
    summary: BibleCoverage.TrackSummary?,
    stats: BibleReadingStats.Snapshot,
    sectionUsage: Map<String, Pair<Int, Long>>,
    usageEvents: List<AppUsageEvents.Event>,
): List<StatsAreaCardModel> =
    AppSectionUsage.statsTabAreas().map { area ->
        val areaEvents = AppUsageEvents.eventsForArea(usageEvents, area)
        val spark = AppUsageEvents.countsByDay(areaEvents, days = 14).map { it.count }
        val rows = if (area == AppSectionUsage.BIBLE_AREA) {
            AppSectionUsage.bibleRows(sectionUsage)
        } else {
            AppSectionUsage.rowsForArea(sectionUsage, area)
        }
        val opens = rows.sumOf { it.opens }
        val never = rows.count { it.opens <= 0 }
        val accent = statsAreaGradient(area).first()
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
                primaryValue = "${statsPercentLabel(summary?.readChapters ?: 0, readPct)} · " +
                    statsPercentLabel(summary?.listenChapters ?: 0, listenPct),
                secondaryValue = buildString {
                    append("прочитано · прослушано")
                    if (stats.topBooks.isNotEmpty()) append(" · топ: ${stats.topBooks.first().bookName}")
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
                primaryValue = if (opens > 0) "$opens" else "0",
                secondaryValue = "открытий · ${rows.size} разделов · не открывали $never",
                sparkline = spark,
                accent = accent,
                icon = iconForArea(area),
            )
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
