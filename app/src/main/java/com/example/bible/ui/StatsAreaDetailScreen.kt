package com.example.bible.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val named = remember(areaEvents) { AppUsageEvents.namedItems(areaEvents) }
    val recentNamed = remember(areaEvents) { AppUsageEvents.recentNamed(areaEvents) }
    val sectionMax = rows.maxOfOrNull { it.opens } ?: 0
    val bands = rows.map { AppSectionUsage.band(it.opens, sectionMax) }
    val visible = rows.filter { band == null || AppSectionUsage.band(it.opens, sectionMax) == band }
    val gradient = statsAreaGradient(area)
    val accent = gradient.first()
    val opens = rows.sumOf { it.opens }
    val listenedMs = AppUsageEvents.listenedMillis(areaEvents)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(gradient))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "РАЗДЕЛ",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(area, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailHeroStat("$opens", "открытий", Modifier.weight(1f))
                    DetailHeroStat("${areaEvents.size}", "событий", Modifier.weight(1f))
                    DetailHeroStat(
                        if (listenedMs > 0L) AppUsageEvents.formatListened(listenedMs) else "${rows.size}",
                        if (listenedMs > 0L) "аудио" else "разделов",
                        Modifier.weight(1f),
                    )
                }
                StatsSparkline(
                    values = days.map { it.count },
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                )
            }
        }
        item(key = "chart") {
            DetailCard(accent) { StatsBarChart(days = days, color = accent) }
        }
        if (types.isNotEmpty()) {
            item(key = "types") {
                DetailCard(accent) { StatsTypeBars(types = types, accent = accent) }
            }
        }
        namedGroup(
            key = "plays",
            title = "Песни и музыка",
            items = named.filter { it.type == AppUsageEvents.TYPE_PLAY },
            accent = accent,
        )
        namedGroup(
            key = "files",
            title = "Открытые файлы",
            items = named.filter { it.type == AppUsageEvents.TYPE_FILE },
            accent = accent,
        )
        namedGroup(
            key = "downloads",
            title = "Скачано и добавлено",
            items = named.filter { it.type == AppUsageEvents.TYPE_DOWNLOAD },
            accent = accent,
        )
        namedGroup(
            key = "deleted",
            title = "Удалено",
            items = named.filter { it.type == AppUsageEvents.TYPE_DELETE },
            accent = accent,
        )
        if (recentNamed.isNotEmpty()) {
            item(key = "journal-title") {
                Text(
                    "Журнал",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.SansSerif,
                )
            }
            itemsIndexed(
                recentNamed,
                key = { index, event -> "journal-$index-${event.timestamp}-${event.type}" },
            ) { _, event ->
                val parsed = AppUsageEvents.parseNamed(event.detail) ?: return@itemsIndexed
                MediaNamedCard(
                    name = parsed.second,
                    kind = parsed.first,
                    meta = "${AppUsageEvents.typeLabel(event.type)} · ${eventWhenRu(event.timestamp)}",
                    accent = accent,
                )
            }
        } else if (area == "Медиа") {
            item(key = "journal-empty") {
                Text(
                    "Файлы, песни, музыка, загрузки и удаления появятся здесь, когда вы откроете, включите, скачаете или удалите их.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "bands") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BandPill("Все", rows.size, accent, selected = band == null) { band = null }
                AppSectionUsage.Band.entries.forEach { item ->
                    BandPill(
                        label = item.label,
                        count = bands.count { it == item },
                        color = bandColor(item, accent),
                        selected = band == item,
                    ) { band = if (band == item) null else item }
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Нет разделов с таким фильтром",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(visible, key = { it.section.id }) { row ->
            val itemBand = AppSectionUsage.band(row.opens, sectionMax)
            AreaRowCard(
                title = row.section.title,
                band = itemBand,
                gradient = gradient,
                fraction = if (sectionMax <= 0) 0f else row.opens.toFloat() / sectionMax,
                opens = row.opens,
                detail = if (row.opens <= 0) "ещё не открывали" else lastOpenedRu(row.lastAt),
                onClick = { onOpenSection(row.section.id) },
            )
        }
    }
}

@Composable
private fun DetailHeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
    }
}

@Composable
private fun DetailCard(accent: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun BandPill(
    label: String,
    count: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color else color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = if (selected) 1f else 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) Color.White else color,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) Color.White.copy(alpha = 0.25f) else color.copy(alpha = 0.18f))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        ) {
            Text("$count", color = if (selected) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun bandColor(band: AppSectionUsage.Band, accent: Color): Color = when (band) {
    AppSectionUsage.Band.OFTEN -> accent
    AppSectionUsage.Band.SOMETIMES -> Color(0xFF0EA5E9)
    AppSectionUsage.Band.RARE -> Color(0xFFF59E0B)
    AppSectionUsage.Band.NEVER -> MaterialTheme.colorScheme.outline
}

@Composable
private fun AreaRowCard(
    title: String,
    band: AppSectionUsage.Band,
    gradient: List<Color>,
    fraction: Float,
    opens: Int,
    detail: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val color = bandColor(band, gradient.first())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    if (opens > 0) Brush.linearGradient(gradient) else Brush.linearGradient(
                        listOf(scheme.outline.copy(alpha = 0.25f), scheme.outline.copy(alpha = 0.12f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (opens > 999) "999+" else "$opens",
                color = if (opens > 0) Color.White else scheme.onSurfaceVariant,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (opens > 99) 13.sp else 17.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    if (band == AppSectionUsage.Band.NEVER) "не открывали" else band.label,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(gradient)),
                )
            }
            Text(detail, fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
    }
}

private fun LazyListScope.namedGroup(
    key: String,
    title: String,
    items: List<AppUsageEvents.NamedItem>,
    accent: Color,
) {
    if (items.isEmpty()) return
    item(key = "$key-title") {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontFamily = FontFamily.SansSerif,
        )
    }
    itemsIndexed(
        items.take(40),
        key = { index, row -> "$key-$index-${row.kind}-${row.name}" },
    ) { _, row ->
        val times = if (row.count == 1) "1 раз" else "${row.count} раз"
        MediaNamedCard(
            name = row.name,
            kind = row.kind,
            meta = "$times · ${eventWhenRu(row.lastAt)}",
            accent = accent,
        )
    }
}

@Composable
private fun MediaNamedCard(
    name: String,
    kind: String,
    meta: String,
    accent: Color,
) {
    val (title, artist) = splitMediaTitle(name)
    DetailCard(accent) {
        Text(
            title,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!artist.isNullOrBlank()) {
            Text(
                artist,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${AppUsageEvents.kindLabel(kind)} · $meta",
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Скобки в классе экранированы: Android ICU не принимает голую `[` внутри `[]`. */
internal val mediaTitleFeatRegex: Regex? = runCatching {
    Regex(
        """(?i)\s*[(\[]?\s*(?:feat\.?|ft\.?|при участии)\s+([^)\]]+?)[)\]]?\s*$""",
    )
}.getOrNull()

/** «Название (feat. Исполнитель)» — название и исполнитель на разных строках. */
internal fun splitMediaTitle(raw: String): Pair<String, String?> {
    val name = raw.trim()
    if (name.isEmpty()) return raw to null
    return try {
        val feat = mediaTitleFeatRegex?.find(name) ?: return name to null
        val artist = feat.groupValues.getOrNull(1).orEmpty().trim().trim('(', ')', '[', ']')
        val title = name.removeRange(feat.range).trim().trim('-', '—', '–', '·', ' ')
        if (title.isEmpty() || artist.isEmpty()) name to null else title to artist
    } catch (_: RuntimeException) {
        name to null
    }
}

private fun eventWhenRu(timestamp: Long): String {
    if (timestamp <= 0L) return "без даты"
    val fmt = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.forLanguageTag("ru"))
    return fmt.format(java.util.Date(timestamp))
}

private fun lastOpenedRu(timestamp: Long): String {
    if (timestamp <= 0L) return "ещё не открывали"
    val days = ((System.currentTimeMillis() - timestamp) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "сегодня"
        days == 1 -> "вчера"
        else -> "$days дн. назад"
    }
}
