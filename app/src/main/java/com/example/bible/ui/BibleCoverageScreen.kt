package com.example.bible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.data.BibleCoverage
import com.example.bible.data.CanonBookGroup

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
    initialTrackId: String,
    onOpenChapter: (trackId: String, bookId: String, chapter: Int) -> Unit,
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
                        onOpenChapter(track.id, book.book.id, chapter)
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
