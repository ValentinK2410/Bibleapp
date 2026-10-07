package com.example.bible.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.AppSectionUsage
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleCoverage
import com.example.bible.data.BibleReadingStats
import com.example.bible.data.CanonBookGroup
import com.example.bible.data.HistoryEntry
import com.example.bible.data.ReadingTraceEntry

private enum class CoverageFilter(val label: String) {
    ALL("Все"),
    GAPS("Есть пробелы"),
    UNREAD("Не прочитано"),
    UNHEARD("Не прослушано"),
}

@Composable
fun BibleCoverageScreen(
    modifier: Modifier = Modifier,
    readKeys: Set<String>,
    listenKeys: Set<String>,
    history: List<HistoryEntry> = emptyList(),
    trace: List<ReadingTraceEntry> = emptyList(),
    copies: List<BibleReadingStats.VerseCopyStat> = emptyList(),
    sectionUsage: Map<String, Pair<Int, Long>> = emptyMap(),
    initialTrackId: String,
    onOpenSection: (String) -> Unit = {},
    onOpenChapter: (trackId: String, bookId: String, chapter: Int, verse: Int) -> Unit,
    onSetBookRead: (trackId: String, bookId: String, chapterCount: Int, marked: Boolean) -> Unit,
    onSetBookListened: (trackId: String, bookId: String, chapterCount: Int, marked: Boolean) -> Unit,
) {
    val summaries = remember(readKeys, listenKeys) {
        BibleCoverage.summarize(readKeys, listenKeys)
    }
    var trackId by remember(initialTrackId) {
        mutableStateOf(
            BibleCoverage.trackById(initialTrackId)?.id ?: BibleCoverage.tracks.first().id,
        )
    }
    var filter by remember { mutableStateOf(CoverageFilter.GAPS) }
    var actionsFor by remember { mutableStateOf<BibleCoverage.BookProgress?>(null) }
    val track = BibleCoverage.trackById(trackId) ?: BibleCoverage.tracks.first()
    val progress = remember(track.id, readKeys, listenKeys) {
        BibleCoverage.progressForTrack(track.id, readKeys, listenKeys)
    }
    val visible = remember(progress, filter, track.id) {
        progress.filter { book ->
            when (filter) {
                CoverageFilter.ALL -> true
                CoverageFilter.UNREAD -> track.hasRead && !book.readComplete
                CoverageFilter.UNHEARD -> track.hasListen && !book.listenComplete
                CoverageFilter.GAPS ->
                    (track.hasRead && !book.readComplete) || (track.hasListen && !book.listenComplete)
            }
        }
    }
    val summary = summaries.firstOrNull { it.track.id == track.id }
    val stats = remember(track.id, history, trace, copies, listenKeys) {
        BibleReadingStats.build(track.id, history, trace, copies, listenKeys)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Книга — открытая глава. Наушники — озвучка дошла до конца главы. Цифры — главы в этом переводе.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "По переводам",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    summaries.forEach { row ->
                        CoverageSummaryRow(row)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BibleCoverage.tracks.forEach { item ->
                    FilterChip(
                        selected = item.id == track.id,
                        onClick = { trackId = item.id },
                        label = { Text(item.shortLabel) },
                    )
                }
            }
        }
        item {
            Text(
                track.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (summary != null) {
                if (track.hasRead) {
                    Text(
                        "Прочитано ${summary.readChapters} из ${summary.totalChapters} глав, книг целиком ${summary.booksRead} из ${summary.bookCount}. Осталось ${summary.totalChapters - summary.readChapters}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (track.hasListen) {
                    Text(
                        "Прослушано ${summary.listenChapters} из ${summary.totalChapters} глав, книг целиком ${summary.booksListened} из ${summary.bookCount}. Осталось ${summary.totalChapters - summary.listenChapters}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!track.hasListen && track.hasRead) {
                    Text(
                        "Озвучка подстрочника идёт дорожками «Иврит» и «Греческий».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            ReadingListenStatsPanel(
                track = track,
                summary = summary,
                stats = stats,
                onOpen = { bookId, chapter, verse ->
                    onOpenChapter(track.id, bookId, chapter, verse)
                },
            )
        }
        item {
            AppSectionsStatsPanel(
                sectionUsage = sectionUsage,
                onOpenSection = onOpenSection,
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoverageFilter.entries.forEach { item ->
                    if (item == CoverageFilter.UNREAD && !track.hasRead) return@forEach
                    if (item == CoverageFilter.UNHEARD && !track.hasListen) return@forEach
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        item {
            Text(
                "Нажатие открывает первую непройденную главу. Удержание отмечает книгу целиком или снимает отметки.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    if (filter == CoverageFilter.ALL) "Нет книг" else "В этом списке пусто — всё из фильтра уже закрыто.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(visible, key = { it.book.id }) { book ->
                CoverageBookCard(
                    book = book,
                    showRead = track.hasRead,
                    showListen = track.hasListen,
                    onClick = {
                        val openRead = when {
                            track.hasRead && !book.readComplete -> true
                            track.hasListen && !book.listenComplete -> false
                            else -> track.hasRead
                        }
                        val marked = BibleCoverage.chaptersMarked(
                            keys = if (openRead) readKeys else listenKeys,
                            trackId = track.id,
                            bookId = book.book.id,
                        )
                        val chapter = book.firstMissing(marked, wantRead = openRead)
                        onOpenChapter(track.id, book.book.id, chapter, 0)
                    },
                    onLongClick = { actionsFor = book },
                )
            }
        }
    }

    actionsFor?.let { book ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            title = { Text(book.book.nameRu) },
            text = {
                Column {
                    if (track.hasRead) {
                        TextButton(onClick = {
                            onSetBookRead(track.id, book.book.id, book.book.chapters, true)
                            actionsFor = null
                        }) { Text("Отметить все главы прочитанными") }
                        TextButton(onClick = {
                            onSetBookRead(track.id, book.book.id, book.book.chapters, false)
                            actionsFor = null
                        }) { Text("Снять отметки чтения") }
                    }
                    if (track.hasListen) {
                        TextButton(onClick = {
                            onSetBookListened(track.id, book.book.id, book.book.chapters, true)
                            actionsFor = null
                        }) { Text("Отметить все главы прослушанными") }
                        TextButton(onClick = {
                            onSetBookListened(track.id, book.book.id, book.book.chapters, false)
                            actionsFor = null
                        }) { Text("Снять отметки прослушивания") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionsFor = null }) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun CoverageSummaryRow(row: BibleCoverage.TrackSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(row.track.shortLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        if (row.track.hasRead) {
            CoverageMeter(
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Прочитано", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                fraction = row.readFraction,
                label = "${row.readChapters}/${row.totalChapters}",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (row.track.hasListen) {
            CoverageMeter(
                icon = { Icon(Icons.Filled.Headphones, contentDescription = "Прослушано", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary) },
                fraction = row.listenFraction,
                label = "${row.listenChapters}/${row.totalChapters}",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun CoverageMeter(
    icon: @Composable () -> Unit,
    fraction: Float,
    label: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(6.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(72.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverageBookCard(
    book: BibleCoverage.BookProgress,
    showRead: Boolean,
    showListen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    book.book.nameRu,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    canonGroupShort(book.book.group),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showRead) {
                Spacer(Modifier.height(6.dp))
                CoverageMeter(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Прочитано",
                            modifier = Modifier.size(18.dp),
                            tint = if (book.readComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    fraction = book.read.toFloat() / book.book.chapters,
                    label = "${book.read}/${book.book.chapters}",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (showListen) {
                Spacer(Modifier.height(4.dp))
                CoverageMeter(
                    icon = {
                        Icon(
                            Icons.Filled.Headphones,
                            contentDescription = "Прослушано",
                            modifier = Modifier.size(18.dp),
                            tint = if (book.listenComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    fraction = book.listened.toFloat() / book.book.chapters,
                    label = "${book.listened}/${book.book.chapters}",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ReadingListenStatsPanel(
    track: BibleCoverage.Track,
    summary: BibleCoverage.TrackSummary?,
    stats: BibleReadingStats.Snapshot,
    onOpen: (bookId: String, chapter: Int, verse: Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val readColor = scheme.primary
    val listenColor = scheme.tertiary
    val empty = stats.uniqueVerses == 0 && stats.visits == 0 && stats.copies == 0 &&
        stats.topListenedBooks.isEmpty() && (summary?.readChapters ?: 0) == 0 &&
        (summary?.listenChapters ?: 0) == 0
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(readColor.copy(alpha = 0.10f), listenColor.copy(alpha = 0.05f), Color.Transparent),
                    ),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsPanelHeader(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                accent = readColor,
                title = "Чтение и озвучка",
                subtitle = track.label,
            )
            if (empty) {
                Text(
                    "Откройте главу или дослушайте озвучку — здесь появятся проценты, любимые книги и разделы канона.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (summary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (track.hasRead) {
                        HeroProgressTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = "Прочитано",
                            done = summary.readChapters,
                            total = summary.totalChapters,
                            booksDone = summary.booksRead,
                            bookCount = summary.bookCount,
                            color = readColor,
                        )
                    }
                    if (track.hasListen) {
                        HeroProgressTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.GraphicEq,
                            title = "Прослушано",
                            done = summary.listenChapters,
                            total = summary.totalChapters,
                            booksDone = summary.booksListened,
                            bookCount = summary.bookCount,
                            color = listenColor,
                        )
                    }
                }
            }
            if (track.hasRead) {
                val metrics = buildList {
                    if (stats.daysActive > 0) add("Дней" to stats.daysActive.toString())
                    if (stats.currentStreak > 0) add("Серия" to "${stats.currentStreak} дн.")
                    if (stats.longestStreak > 1) add("Рекорд" to "${stats.longestStreak} дн.")
                    if (stats.uniqueVerses > 0) add("Стихов" to stats.uniqueVerses.toString())
                    if (stats.uniqueChapters > 0) add("Глав" to stats.uniqueChapters.toString())
                    if (stats.uniqueBooks > 0) add("Книг" to stats.uniqueBooks.toString())
                    if (stats.visits > 0) add("Открытий" to stats.visits.toString())
                    if (stats.dwellSeconds > 0) add("Время" to formatStatsDuration(stats.dwellSeconds))
                    if (stats.copies > 0) add("Копий" to stats.copies.toString())
                    if (stats.visitsLast7Days > 0 || stats.dwellLast7Days > 0) {
                        val week = buildList {
                            if (stats.visitsLast7Days > 0) add("${stats.visitsLast7Days} откр.")
                            if (stats.dwellLast7Days > 0) add(formatStatsDuration(stats.dwellLast7Days))
                        }.joinToString(" · ")
                        add("7 дней" to week)
                    }
                }
                if (metrics.isNotEmpty()) {
                    StatsSectionLabel("Обзор чтения")
                    metrics.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, value) ->
                                MetricChip(Modifier.weight(1f), label, value, readColor)
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                TestamentSplitBar(
                    oldSeconds = stats.oldTestamentSeconds,
                    newSeconds = stats.newTestamentSeconds,
                )
                if (stats.topTools.isNotEmpty()) {
                    Text(
                        "Инструменты: " + stats.topTools.joinToString(" · ") { "${it.first} ${it.second}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                RankBlock("Самые читаемые книги", stats.topBooks, onOpen, readColor) { rankDetail(it) }
                RankBlock("Самые читаемые главы", stats.topChapters, onOpen, readColor) { rankDetail(it) }
                RankBlock("Самые читаемые стихи", stats.topVerses, onOpen, readColor) { rankDetail(it) }
                RankBlock("Дольше всего на стихе", stats.topByTime, onOpen, readColor) { rankDetail(it) }
                RankBlock("Чаще всего копируют", stats.topCopied, onOpen, readColor) { rankDetail(it) }
            }
            if (track.hasListen || stats.topListenedBooks.isNotEmpty()) {
                RankBlock("Больше всего прослушанных глав", stats.topListenedBooks, onOpen, listenColor) {
                    "${it.uniqueVerses} гл."
                }
            }
            CanonGroupsBoard(
                groups = stats.groups,
                onOpenGroup = { group ->
                    val bookId = BibleCanon.allBooks.firstOrNull { it.group == group }?.id
                    if (bookId != null) onOpen(bookId, 1, 0)
                },
            )
        }
    }
}

@Composable
private fun AppSectionsStatsPanel(
    sectionUsage: Map<String, Pair<Int, Long>>,
    onOpenSection: (String) -> Unit,
) {
    var band by remember { mutableStateOf<AppSectionUsage.Band?>(null) }
    val sections = remember(sectionUsage) { AppSectionUsage.nonBibleRows(sectionUsage) }
    val sectionMax = sections.maxOfOrNull { it.opens } ?: 0
    val bands = sections.map { AppSectionUsage.band(it.opens, sectionMax) }
    val visible = sections.filter { band == null || AppSectionUsage.band(it.opens, sectionMax) == band }
    val byArea = remember(visible) {
        val order = AppSectionUsage.areaOrder()
        visible.groupBy { it.section.area }
            .toList()
            .sortedBy { (area, _) -> order.indexOf(area).let { if (it < 0) 99 else it } }
    }
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.secondary
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                    ),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsPanelHeader(
                icon = Icons.Filled.Apps,
                accent = accent,
                title = "Разделы приложения",
                subtitle = "Без Библии — медиа, детям, церковь и остальное",
            )
            Text(
                "Сверху то, куда заходите часто. Внизу — то, что почти не открывали.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BandSummaryChip("часто", bands.count { it == AppSectionUsage.Band.OFTEN }, scheme.primary)
                BandSummaryChip("иногда", bands.count { it == AppSectionUsage.Band.SOMETIMES }, scheme.tertiary)
                BandSummaryChip("редко", bands.count { it == AppSectionUsage.Band.RARE }, scheme.onSurfaceVariant)
                BandSummaryChip("пусто", bands.count { it == AppSectionUsage.Band.NEVER }, scheme.outline)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
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
            byArea.forEach { (area, rows) ->
                StatsSectionLabel(area)
                rows.forEach { row ->
                    val itemBand = AppSectionUsage.band(row.opens, sectionMax)
                    UsageMeterCard(
                        title = row.section.title,
                        band = itemBand,
                        fraction = if (sectionMax <= 0) 0f else row.opens.toFloat() / sectionMax,
                        detail = if (row.opens <= 0) {
                            "ещё не открывали"
                        } else {
                            openingsLabel(row.opens) + " · " + lastOpenedLabel(row.lastAt)
                        },
                        neverLabel = "не открывали",
                        onClick = { onOpenSection(row.section.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CanonGroupsBoard(
    groups: List<BibleReadingStats.GroupStats>,
    onOpenGroup: (CanonBookGroup) -> Unit,
) {
    if (groups.isEmpty()) return
    var band by remember { mutableStateOf<AppSectionUsage.Band?>(null) }
    val groupMax = groups.maxOfOrNull { it.activity } ?: 0
    val visible = groups.filter { band == null || AppSectionUsage.band(it.activity, groupMax) == band }
    StatsSectionLabel("Разделы канона")
    Text(
        "Пятикнижие, Евангелия и остальные — что читают и слушают чаще.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
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
    visible.forEach { group ->
        val itemBand = AppSectionUsage.band(group.activity, groupMax)
        UsageMeterCard(
            title = group.title,
            band = itemBand,
            fraction = if (groupMax <= 0) 0f else group.activity.toFloat() / groupMax,
            detail = groupDetail(group),
            neverLabel = "не читали",
            onClick = { onOpenGroup(group.group) },
        )
    }
}

@Composable
private fun StatsPanelHeader(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun HeroProgressTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    done: Int,
    total: Int,
    booksDone: Int,
    bookCount: Int,
    color: Color,
) {
    val pct = BibleCoverage.percent(done, total)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(
                "$pct%",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 30.sp,
            )
            LinearProgressIndicator(
                progress = { if (total <= 0) 0f else done.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.14f),
                drawStopIndicator = {},
            )
            Text(
                "$done из $total глав",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "книг целиком $booksDone из $bookCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricChip(modifier: Modifier, label: String, value: String, accent: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TestamentSplitBar(oldSeconds: Int, newSeconds: Int) {
    val total = oldSeconds + newSeconds
    if (total <= 0) return
    val oldPct = oldSeconds * 100 / total
    val newPct = 100 - oldPct
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatsSectionLabel("Ветхий и Новый Завет")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant),
        ) {
            if (oldPct > 0) {
                Box(
                    Modifier
                        .weight(oldPct.toFloat().coerceAtLeast(1f))
                        .fillMaxSize()
                        .background(scheme.primary),
                )
            }
            if (newPct > 0) {
                Box(
                    Modifier
                        .weight(newPct.toFloat().coerceAtLeast(1f))
                        .fillMaxSize()
                        .background(scheme.tertiary),
                )
            }
        }
        Row {
            Text("ВЗ $oldPct%", color = scheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("НЗ $newPct%", color = scheme.tertiary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BandSummaryChip(label: String, count: Int, color: Color) {
    Surface(
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
private fun UsageMeterCard(
    title: String,
    band: AppSectionUsage.Band,
    fraction: Float,
    detail: String,
    neverLabel: String,
    onClick: () -> Unit,
) {
    val color = when (band) {
        AppSectionUsage.Band.OFTEN -> MaterialTheme.colorScheme.primary
        AppSectionUsage.Band.SOMETIMES -> MaterialTheme.colorScheme.tertiary
        AppSectionUsage.Band.RARE -> MaterialTheme.colorScheme.onSurfaceVariant
        AppSectionUsage.Band.NEVER -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (band == AppSectionUsage.Band.NEVER) neverLabel else band.label,
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.14f),
                drawStopIndicator = {},
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun groupDetail(group: BibleReadingStats.GroupStats): String {
    if (group.activity <= 0) return "не читали и не слушали · 0/${group.totalChapters} глав"
    return buildList {
        if (group.visits > 0) add(openingsLabel(group.visits))
        if (group.dwellSeconds > 0) add(formatStatsDuration(group.dwellSeconds))
        if (group.uniqueVerses > 0) add("${group.uniqueVerses} стих.")
        if (group.copies > 0) add(copiesLabel(group.copies))
        add("глав ${group.chaptersOpened}/${group.totalChapters}")
        if (group.listenedChapters > 0) add("прослушано ${group.listenedChapters}/${group.totalChapters}")
        add("книг ${group.booksTouched}/${group.bookCount}")
    }.joinToString(" · ")
}

private fun lastOpenedLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "ещё не открывали"
    val days = ((System.currentTimeMillis() - timestamp) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "сегодня"
        days == 1 -> "вчера"
        else -> "$days дн. назад"
    }
}

@Composable
private fun RankBlock(
    title: String,
    rows: List<BibleReadingStats.PassageRank>,
    onOpen: (bookId: String, chapter: Int, verse: Int) -> Unit,
    accent: Color,
    detail: (BibleReadingStats.PassageRank) -> String,
) {
    if (rows.isEmpty()) return
    StatsSectionLabel(title)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { index, rank ->
            val place = index + 1
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onOpen(rank.bookId, if (rank.chapter > 0) rank.chapter else 1, rank.verse)
                    },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = if (place <= 3) 0.22f else 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$place",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            passageTitle(rank),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            detail(rank),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun passageTitle(rank: BibleReadingStats.PassageRank): String = when {
    rank.verse > 0 -> "${rank.bookName} ${rank.chapter}:${rank.verse}"
    rank.chapter > 0 -> "${rank.bookName}, гл. ${rank.chapter}"
    else -> rank.bookName
}

private fun rankDetail(rank: BibleReadingStats.PassageRank): String = buildList {
    if (rank.visits > 0) add(openingsLabel(rank.visits))
    if (rank.dwellSeconds > 0) add(formatStatsDuration(rank.dwellSeconds))
    if (rank.copies > 0) add(copiesLabel(rank.copies))
    if (rank.verse == 0 && rank.chapter == 0 && rank.uniqueVerses > 0) add("${rank.uniqueVerses} стих.")
    if (rank.verse == 0 && rank.chapter > 0 && rank.uniqueVerses > 0) add("${rank.uniqueVerses} стих.")
}.joinToString(" · ").ifBlank { "открывали" }

private fun testamentLine(oldSeconds: Int, newSeconds: Int): String? {
    val total = oldSeconds + newSeconds
    if (total <= 0) return null
    val oldPercent = oldSeconds * 100 / total
    val newPercent = 100 - oldPercent
    return "Ветхий Завет $oldPercent% · Новый Завет $newPercent%"
}

private fun formatStatsDuration(seconds: Int): String {
    if (seconds < 60) return "$seconds с"
    val minutes = seconds / 60
    if (minutes < 60) return "$minutes мин"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours ч" else "$hours ч $rest мин"
}

private fun openingsLabel(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "открытий"
        count % 10 == 1 -> "открытие"
        count % 10 in 2..4 -> "открытия"
        else -> "открытий"
    }
    return "$count $word"
}

private fun copiesLabel(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "копий"
        count % 10 == 1 -> "копия"
        count % 10 in 2..4 -> "копии"
        else -> "копий"
    }
    return "$count $word"
}

private fun canonGroupShort(group: CanonBookGroup): String = when (group) {
    CanonBookGroup.PENTATEUCH -> "Пятикнижие"
    CanonBookGroup.HISTORY -> "История"
    CanonBookGroup.WISDOM -> "Учительные"
    CanonBookGroup.MAJOR_PROPHETS -> "Пророки"
    CanonBookGroup.MINOR_PROPHETS -> "Малые пророки"
    CanonBookGroup.GOSPELS -> "Евангелия"
    CanonBookGroup.ACTS -> "Деяния"
    CanonBookGroup.GENERAL_EPISTLES -> "Соборные"
    CanonBookGroup.PAULINE -> "Павел"
    CanonBookGroup.HEBREWS -> "Евреям"
    CanonBookGroup.REVELATION -> "Откровение"
}
