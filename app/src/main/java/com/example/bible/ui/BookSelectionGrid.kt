package com.example.bible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleCoverage
import com.example.bible.data.BiblePreferences
import com.example.bible.data.PassageActivity
import com.example.bible.data.CanonBookEntry
import com.example.bible.data.CanonBookGroup
import com.example.bible.data.TimemarkPresenceIndex
import com.example.bible.data.TimemarkStore
import com.example.bible.data.TranslationId

enum class BookLayoutMode {
    GRID,
    LIST,
}

@Composable
fun timemarkIndicatorColor(highlightArgb: Int?): Color =
    highlightArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

fun coverageMarkColor(trackId: String, tabColors: Map<String, Int>): Color =
    Color(BibleCoverage.markColorArgb(trackId, tabColors))

fun orderedTimemarkTranslationCodes(codes: Set<String>): List<String> {
    if (codes.isEmpty()) return emptyList()
    val byLower = codes.associateBy { it.lowercase() }
    val known = TranslationId.entries.mapNotNull { byLower[it.code.lowercase()] }
    val rest = codes.filter { code ->
        TranslationId.entries.none { it.code.equals(code, ignoreCase = true) }
    }.sorted()
    return known + rest
}

fun translationLabelRu(code: String): String =
    TranslationId.entries.find { it.code.equals(code, ignoreCase = true) }?.labelRu ?: code

@Composable
fun TimemarkTranslationsDialog(
    title: String,
    translationCodes: Set<String>,
    tabColors: Map<String, Int>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    readLines: List<String> = emptyList(),
    listenLines: List<String> = emptyList(),
    accent: Color? = null,
) {
    val codes = remember(translationCodes) { orderedTimemarkTranslationCodes(translationCodes) }
    val scheme = MaterialTheme.colorScheme
    val accentColor = accent ?: scheme.primary
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = shape,
            color = scheme.surface,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor.copy(alpha = 0.34f), accentColor.copy(alpha = 0.08f)),
                            ),
                        )
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface.copy(alpha = 0.72f),
                        )
                    }
                }
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PassageVisitBlock(
                        title = "Таймкоды",
                        tint = scheme.secondary,
                        icon = Icons.Filled.GraphicEq,
                        lines = codes.map { translationLabelRu(it) },
                        emptyText = "Нет таймкодов озвучки",
                        leadingColors = codes.map { timemarkIndicatorColor(tabColors[it]) },
                    )
                    PassageVisitBlock(
                        title = "Прочитано",
                        tint = scheme.primary,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        lines = readLines,
                        emptyText = "Ещё не читали",
                    )
                    PassageVisitBlock(
                        title = "Прослушано",
                        tint = scheme.tertiary,
                        icon = Icons.Filled.Headphones,
                        lines = listenLines,
                        emptyText = "Ещё не слушали",
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = if (accentColor.luminance() > 0.55f) Color.Black else Color.White,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("ОК")
                    }
                }
            }
        }
    }
}

@Composable
private fun PassageVisitBlock(
    title: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    lines: List<String>,
    emptyText: String,
    leadingColors: List<Color> = emptyList(),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
        }
        if (lines.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        } else {
            lines.forEachIndexed { index, line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surface.copy(alpha = 0.86f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val dot = leadingColors.getOrNull(index) ?: tint
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dot),
                    )
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun TimemarkPresenceDot(
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 7.dp,
) {
    if (!visible) return
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimemarkPresenceDots(
    translationCodes: Set<String>,
    tabColors: Map<String, Int>,
    modifier: Modifier = Modifier,
    size: Dp = 7.dp,
) {
    val codes = remember(translationCodes) { orderedTimemarkTranslationCodes(translationCodes) }
    if (codes.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        maxItemsInEachRow = 4,
    ) {
        val outline = MaterialTheme.colorScheme.surface
        for (code in codes) {
            Box(
                Modifier
                    .size(size)
                    .border(0.6.dp, outline, CircleShape)
                    .clip(CircleShape)
                    .background(timemarkIndicatorColor(tabColors[code])),
            )
        }
    }
}

@Composable
fun rememberTimemarkCatalogTick(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
fun rememberTranslationTabColorsMap(): Map<String, Int> {
    val context = LocalContext.current
    val prefs = remember(context.applicationContext) { BiblePreferences(context.applicationContext) }
    val colors by prefs.translationTabColors.collectAsStateWithLifecycle(emptyMap())
    return colors
}

@Composable
fun rememberTimemarkPresenceIndex(): TimemarkPresenceIndex {
    val context = LocalContext.current
    val tick = rememberTimemarkCatalogTick()
    return remember(tick) { TimemarkStore.presenceIndex(context) }
}

@Composable
fun groupTextColor(group: CanonBookGroup): Color {
    val darkUi = MaterialTheme.colorScheme.background.luminance() < 0.45f
    return if (darkUi) {
        when (group) {
            CanonBookGroup.PENTATEUCH -> Color(0xFF9DB8FF)
            CanonBookGroup.HISTORY -> Color(0xFFE8B080)
            CanonBookGroup.WISDOM -> Color(0xFF7FE095)
            CanonBookGroup.MAJOR_PROPHETS -> Color(0xFFFF9DB5)
            CanonBookGroup.MINOR_PROPHETS -> Color(0xFFB8CF80)
            CanonBookGroup.GOSPELS -> Color(0xFFFFB366)
            CanonBookGroup.ACTS -> Color(0xFF6ADCF5)
            CanonBookGroup.GENERAL_EPISTLES -> Color(0xFF7AEE7A)
            CanonBookGroup.PAULINE -> Color(0xFFFFEB80)
            CanonBookGroup.HEBREWS -> Color(0xFFF0F0F0)
            CanonBookGroup.REVELATION -> Color(0xFFFF8080)
        }
    } else {
        when (group) {
            CanonBookGroup.PENTATEUCH -> Color(0xFF152E66)
            CanonBookGroup.HISTORY -> Color(0xFF6B4018)
            CanonBookGroup.WISDOM -> Color(0xFF1B5C28)
            CanonBookGroup.MAJOR_PROPHETS -> Color(0xFF7A1838)
            CanonBookGroup.MINOR_PROPHETS -> Color(0xFF3D4F18)
            CanonBookGroup.GOSPELS -> Color(0xFF8A4E08)
            CanonBookGroup.ACTS -> Color(0xFF0E5666)
            CanonBookGroup.GENERAL_EPISTLES -> Color(0xFF1A661A)
            CanonBookGroup.PAULINE -> Color(0xFF665508)
            CanonBookGroup.HEBREWS -> Color(0xFF2A2A2A)
            CanonBookGroup.REVELATION -> Color(0xFF7A1010)
        }
    }
}

/** Название раздела канона для заголовка секции. */
fun canonGroupTitleRu(group: CanonBookGroup): String = when (group) {
    CanonBookGroup.PENTATEUCH -> "Пятикнижие"
    CanonBookGroup.HISTORY -> "Исторические книги"
    CanonBookGroup.WISDOM -> "Учительные книги"
    CanonBookGroup.MAJOR_PROPHETS -> "Большие пророки"
    CanonBookGroup.MINOR_PROPHETS -> "Малые пророки"
    CanonBookGroup.GOSPELS -> "Евангелия"
    CanonBookGroup.ACTS -> "Деяния апостолов"
    CanonBookGroup.GENERAL_EPISTLES -> "Соборные послания"
    CanonBookGroup.PAULINE -> "Послания Павла"
    CanonBookGroup.HEBREWS -> "Послание к Евреям"
    CanonBookGroup.REVELATION -> "Откровение"
}

/** Подряд идущие книги одного раздела канона. */
data class CanonBookSection(
    val group: CanonBookGroup,
    val books: List<CanonBookEntry>,
) {
    val isOldTestament: Boolean get() = BibleCanon.isOldTestament(books.first().id)
}

/** Канон, разбитый на секции в порядке сетки. */
val canonBookSections: List<CanonBookSection> by lazy {
    buildList {
        for (entry in BibleCanon.allBooks) {
            val last = lastOrNull()
            if (last != null && last.group == entry.group) {
                set(lastIndex, last.copy(books = last.books + entry))
            } else {
                add(CanonBookSection(entry.group, listOf(entry)))
            }
        }
    }
}

/** Заголовок завета над первой его секцией. */
@Composable
private fun TestamentHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = color.copy(alpha = 0.25f),
        )
        Text(
            text = title.uppercase(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            fontFamily = FontFamily.SansSerif,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = color.copy(alpha = 0.25f),
        )
    }
}

/** Компактный заголовок раздела канона в цвете группы. */
@Composable
private fun CanonGroupHeader(
    section: CanonBookSection,
    modifier: Modifier = Modifier,
) {
    val accent = groupTextColor(section.group)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text = canonGroupTitleRu(section.group),
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = section.books.size.toString(),
            color = accent.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = accent.copy(alpha = 0.2f),
        )
    }
}

/** Крупное название книги после долгого нажатия на плитке. */
@Composable
fun BookPickerPreviewBanner(
    entry: CanonBookEntry,
    modifier: Modifier = Modifier,
) {
    val textColor = groupTextColor(entry.group)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = entry.abbrRu,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            Text(
                text = entry.nameRu,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BookSelectionContent(
    layoutMode: BookLayoutMode,
    modifier: Modifier = Modifier,
    booksWithAudio: Set<String> = emptySet(),
    coverageByBook: Map<String, BibleCoverage.BookTileCoverage> = emptyMap(),
    readProgressColor: Color? = null,
    translation: TranslationId? = null,
    passageActivity: String = "",
    readVerseKeys: Set<String> = emptySet(),
    listenVerseKeys: Set<String> = emptySet(),
    onBookClick: (String) -> Unit,
    onBookLongPress: (CanonBookEntry) -> Unit = {},
) {
    when (layoutMode) {
        BookLayoutMode.GRID -> BookSelectionGrid(
            modifier = modifier,
            booksWithAudio = booksWithAudio,
            coverageByBook = coverageByBook,
            readProgressColor = readProgressColor,
            translation = translation,
            passageActivity = passageActivity,
            readVerseKeys = readVerseKeys,
            listenVerseKeys = listenVerseKeys,
            onBookClick = onBookClick,
            onBookLongPress = onBookLongPress,
        )
        BookLayoutMode.LIST -> BookSelectionList(
            modifier = modifier,
            booksWithAudio = booksWithAudio,
            coverageByBook = coverageByBook,
            readProgressColor = readProgressColor,
            translation = translation,
            passageActivity = passageActivity,
            readVerseKeys = readVerseKeys,
            listenVerseKeys = listenVerseKeys,
            onBookClick = onBookClick,
            onBookLongPress = onBookLongPress,
        )
    }
}

@Composable
fun BookSelectionGrid(
    modifier: Modifier = Modifier,
    booksWithAudio: Set<String> = emptySet(),
    coverageByBook: Map<String, BibleCoverage.BookTileCoverage> = emptyMap(),
    readProgressColor: Color? = null,
    translation: TranslationId? = null,
    passageActivity: String = "",
    readVerseKeys: Set<String> = emptySet(),
    listenVerseKeys: Set<String> = emptySet(),
    onBookClick: (String) -> Unit,
    onBookLongPress: (CanonBookEntry) -> Unit = {},
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var infoBook by remember { mutableStateOf<CanonBookEntry?>(null) }
    val presence = rememberTimemarkPresenceIndex()
    val tabColors = rememberTranslationTabColorsMap()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                canonBookSections.forEachIndexed { sectionIndex, section ->
                    val firstOfTestament = sectionIndex == 0 ||
                        canonBookSections[sectionIndex - 1].isOldTestament != section.isOldTestament
                    if (firstOfTestament) {
                        item(
                            key = "testament_${section.group.name}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            TestamentHeader(
                                title = if (section.isOldTestament) "Ветхий Завет" else "Новый Завет",
                            )
                        }
                    }
                    item(
                        key = "group_${section.group.name}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        CanonGroupHeader(section = section)
                    }
                    lazyGridItems(
                        items = section.books,
                        key = { it.id },
                    ) { entry ->
                        BookCell(
                            entry = entry,
                            selected = selectedId == entry.id,
                            hasAudio = entry.id in booksWithAudio,
                            readChapters = coverageByBook[entry.id]?.readChapters ?: 0,
                            listenedChapters = coverageByBook[entry.id]?.listenedChapters ?: 0,
                            listenedVerses = coverageByBook[entry.id]?.listenedVerses ?: 0,
                            listenedOpenChapters = coverageByBook[entry.id]?.listenedOpenChapters ?: 0,
                            listenRepeats = listenRepeatCount(passageActivity, translation, entry.id),
                            readProgressColor = readProgressColor,
                            timemarkCodes = presence.forBook(entry.id),
                            tabColors = tabColors,
                            onClick = {
                                selectedId = entry.id
                                onBookClick(entry.id)
                            },
                            onLongClick = {
                                infoBook = entry
                                onBookLongPress(entry)
                            },
                        )
                    }
                }
            }
            infoBook?.let { entry ->
                val visits = remember(passageActivity, readVerseKeys, listenVerseKeys, translation, entry.id) {
                    passageVisitLines(passageActivity, readVerseKeys, listenVerseKeys, translation, entry.id)
                }
                TimemarkTranslationsDialog(
                    title = entry.nameRu,
                    subtitle = entry.abbrRu,
                    translationCodes = presence.forBook(entry.id),
                    tabColors = tabColors,
                    readLines = visits.first,
                    listenLines = visits.second,
                    accent = groupTextColor(entry.group),
                    onDismiss = { infoBook = null },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookSelectionList(
    modifier: Modifier = Modifier,
    booksWithAudio: Set<String> = emptySet(),
    coverageByBook: Map<String, BibleCoverage.BookTileCoverage> = emptyMap(),
    readProgressColor: Color? = null,
    translation: TranslationId? = null,
    passageActivity: String = "",
    readVerseKeys: Set<String> = emptySet(),
    listenVerseKeys: Set<String> = emptySet(),
    onBookClick: (String) -> Unit,
    onBookLongPress: (CanonBookEntry) -> Unit = {},
) {
    val presence = rememberTimemarkPresenceIndex()
    val tabColors = rememberTranslationTabColorsMap()
    var infoBook by remember { mutableStateOf<CanonBookEntry?>(null) }
    Box(modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        canonBookSections.forEachIndexed { sectionIndex, section ->
            val firstOfTestament = sectionIndex == 0 ||
                canonBookSections[sectionIndex - 1].isOldTestament != section.isOldTestament
            if (firstOfTestament) {
                item(key = "testament_${section.group.name}") {
                    TestamentHeader(
                        title = if (section.isOldTestament) "Ветхий Завет" else "Новый Завет",
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            }
            stickyHeader(key = "group_${section.group.name}") {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CanonGroupHeader(
                        section = section,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            }
            lazyColumnItems(section.books, key = { it.id }) { entry ->
                val textColor = groupTextColor(entry.group)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onBookClick(entry.id) },
                            onLongClick = {
                                infoBook = entry
                                onBookLongPress(entry)
                            },
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(textColor.copy(alpha = 0.65f)),
                    )
                    Text(
                        text = entry.abbrRu,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp),
                    )
                    Text(
                        text = entry.nameRu,
                        color = textColor.copy(alpha = 0.88f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CoverageMiniMarks(
                        read = coverageByBook[entry.id]?.readChapters ?: 0,
                        listened = coverageByBook[entry.id]?.listenedChapters ?: 0,
                        listenedVerses = coverageByBook[entry.id]?.listenedVerses ?: 0,
                        listenedOpenChapters = coverageByBook[entry.id]?.listenedOpenChapters ?: 0,
                        chapters = entry.chapters,
                        readColor = readProgressColor,
                        compact = false,
                    )
                    Text(
                        text = "${entry.chapters} гл.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                    )
                    TimemarkPresenceDots(
                        translationCodes = presence.forBook(entry.id),
                        tabColors = tabColors,
                    )
                    if (entry.id in booksWithAudio) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 32.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                )
            }
        }
    }
        infoBook?.let { entry ->
            val visits = remember(passageActivity, readVerseKeys, listenVerseKeys, translation, entry.id) {
                passageVisitLines(passageActivity, readVerseKeys, listenVerseKeys, translation, entry.id)
            }
            TimemarkTranslationsDialog(
                title = entry.nameRu,
                subtitle = entry.abbrRu,
                translationCodes = presence.forBook(entry.id),
                tabColors = tabColors,
                    readLines = visits.first,
                    listenLines = visits.second,
                    accent = groupTextColor(entry.group),
                    onDismiss = { infoBook = null },
                )
        }
    }
}

private fun listenRepeatCount(
    activity: String,
    translation: TranslationId?,
    bookId: String,
): Int {
    if (translation == null || activity.isBlank()) return 0
    return PassageActivity.maxCount(
        activity,
        PassageActivity.LISTEN,
        BibleCoverage.listenTrackFor(translation, bookId),
        bookId,
    )
}

private fun passageVisitLines(
    activity: String,
    readKeys: Set<String>,
    listenKeys: Set<String>,
    translation: TranslationId?,
    bookId: String,
): Pair<List<String>, List<String>> {
    if (translation == null) return emptyList<String>() to emptyList()
    val readTrack = translation.code
    val listenTrack = BibleCoverage.listenTrackFor(translation, bookId)
    val read = PassageActivity.linesForBook(
        activity,
        PassageActivity.READ,
        readTrack,
        bookId,
        BibleCoverage.versesInBook(readKeys, readTrack, bookId),
    )
    val listen = PassageActivity.linesForBook(
        activity,
        PassageActivity.LISTEN,
        listenTrack,
        bookId,
        BibleCoverage.versesInBook(listenKeys, listenTrack, bookId),
    )
    return read to listen
}

@Composable
private fun CoverageMiniMarks(
    read: Int,
    listened: Int,
    chapters: Int,
    readColor: Color? = null,
    compact: Boolean = true,
    listenedVerses: Int = 0,
    listenedOpenChapters: Int = 0,
    listenRepeats: Int = 0,
) {
    val readTint = readColor ?: MaterialTheme.colorScheme.primary
    val listenTint = MaterialTheme.colorScheme.tertiary
    val listenCoverage = BibleCoverage.BookTileCoverage(
        listenedChapters = listened,
        listenedVerses = listenedVerses,
        listenedOpenChapters = listenedOpenChapters,
    )
    Column(
        modifier = Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier.width(88.dp)),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp),
    ) {
        CoverageMiniBar(
            done = read,
            total = chapters,
            color = readTint,
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = "Прочитано",
        )
        CoverageMiniBar(
            done = listened,
            total = chapters,
            color = listenTint,
            icon = Icons.Filled.GraphicEq,
            label = "Прослушано",
            fractionOverride = if (listenedVerses > 0 || listenedOpenChapters > 0) {
                BibleCoverage.listenFill(chapters, listenCoverage)
            } else {
                null
            },
            caption = listenTileCaption(listened, chapters, listenedVerses, compact, listenRepeats),
        )
    }
}

private fun listenTileCaption(
    chaptersDone: Int,
    chapters: Int,
    verses: Int,
    compact: Boolean,
    repeats: Int = 0,
): String? {
    val repeat = if (repeats > 1) "·${repeats}×" else ""
    if (verses > 0 && chapters > 0 && chaptersDone < chapters) {
        return if (compact) "${verses}ст$repeat" else "$chaptersDone/$chapters · ${verses}ст$repeat"
    }
    if (repeat.isNotEmpty() && chapters > 0 && chaptersDone >= chapters) {
        return "${BibleCoverage.percent(chaptersDone, chapters)}$repeat"
    }
    return null
}

@Composable
private fun CoverageMiniBar(
    done: Int,
    total: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    fractionOverride: Float? = null,
    caption: String? = null,
) {
    val fraction = fractionOverride ?: if (total <= 0) 0f else done.coerceAtLeast(0).toFloat() / total
    val pct = BibleCoverage.percent(done, total)
    val active = done > 0 || caption != null
    val tint = if (active) color else color.copy(alpha = 0.72f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = if (caption != null) "$label $caption, глав $done из $total" else "$label $pct%",
            tint = tint,
            modifier = Modifier.size(11.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 3.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.34f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(tint),
            )
        }
        Text(
            text = caption ?: "$pct",
            color = if (active) color else tint,
            fontSize = 9.sp,
            fontWeight = if (pct == 100 || caption != null) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(
    entry: CanonBookEntry,
    selected: Boolean,
    hasAudio: Boolean = false,
    readChapters: Int = 0,
    listenedChapters: Int = 0,
    listenedVerses: Int = 0,
    listenedOpenChapters: Int = 0,
    listenRepeats: Int = 0,
    readProgressColor: Color? = null,
    timemarkCodes: Set<String> = emptySet(),
    tabColors: Map<String, Int> = emptyMap(),
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val textColor = groupTextColor(entry.group)
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (selected) textColor else textColor.copy(alpha = 0.28f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .border(width = if (selected) 1.5.dp else 1.dp, color = borderColor, shape = shape),
        shape = shape,
        color = scheme.surface,
        shadowElevation = if (selected) 4.dp else 1.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                textColor.copy(alpha = 0.35f),
                                textColor,
                                textColor.copy(alpha = 0.35f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                textColor.copy(alpha = if (selected) 0.16f else 0.08f),
                                scheme.surface,
                            ),
                        ),
                    )
                    .padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.abbrRu,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CoverageMiniMarks(
                    read = readChapters,
                    listened = listenedChapters,
                    listenedVerses = listenedVerses,
                    listenedOpenChapters = listenedOpenChapters,
                    listenRepeats = listenRepeats,
                    chapters = entry.chapters,
                    readColor = readProgressColor,
                )
                if (hasAudio || timemarkCodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.height(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (hasAudio) {
                            Icon(
                                Icons.Default.Headphones,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp),
                            )
                            if (timemarkCodes.isNotEmpty()) Spacer(Modifier.width(4.dp))
                        }
                        TimemarkPresenceDots(
                            translationCodes = timemarkCodes,
                            tabColors = tabColors,
                            size = 6.dp,
                        )
                    }
                }
            }
        }
    }
}
