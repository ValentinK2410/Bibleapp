package com.example.bible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.bible.data.BiblePreferences
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
) {
    val codes = remember(translationCodes) { orderedTimemarkTranslationCodes(translationCodes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (codes.isEmpty()) {
                    Text(
                        "Нет таймкодов озвучки ни в одном переводе.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Переводы с озвучкой (таймкоды):",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    for (code in codes) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(timemarkIndicatorColor(tabColors[code])),
                            )
                            Text(
                                translationLabelRu(code),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ОК") }
        },
    )
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
    onBookClick: (String) -> Unit,
    onBookLongPress: (CanonBookEntry) -> Unit = {},
) {
    when (layoutMode) {
        BookLayoutMode.GRID -> BookSelectionGrid(
            modifier = modifier,
            booksWithAudio = booksWithAudio,
            onBookClick = onBookClick,
            onBookLongPress = onBookLongPress,
        )
        BookLayoutMode.LIST -> BookSelectionList(
            modifier = modifier,
            booksWithAudio = booksWithAudio,
            onBookClick = onBookClick,
            onBookLongPress = onBookLongPress,
        )
    }
}

@Composable
fun BookSelectionGrid(
    modifier: Modifier = Modifier,
    booksWithAudio: Set<String> = emptySet(),
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
                TimemarkTranslationsDialog(
                    title = entry.nameRu,
                    subtitle = entry.abbrRu,
                    translationCodes = presence.forBook(entry.id),
                    tabColors = tabColors,
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
            TimemarkTranslationsDialog(
                title = entry.nameRu,
                subtitle = entry.abbrRu,
                translationCodes = presence.forBook(entry.id),
                tabColors = tabColors,
                onDismiss = { infoBook = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(
    entry: CanonBookEntry,
    selected: Boolean,
    hasAudio: Boolean = false,
    timemarkCodes: Set<String> = emptySet(),
    tabColors: Map<String, Int> = emptyMap(),
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val textColor = groupTextColor(entry.group)
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) {
        textColor.copy(alpha = 0.75f)
    } else {
        textColor.copy(alpha = 0.22f)
    }
    val tintBrush = Brush.verticalGradient(
        colors = if (selected) {
            listOf(textColor.copy(alpha = 0.26f), textColor.copy(alpha = 0.12f))
        } else {
            listOf(textColor.copy(alpha = 0.12f), textColor.copy(alpha = 0.04f))
        },
    )
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
        color = scheme.surfaceContainerLow,
        shadowElevation = if (selected) 3.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .background(tintBrush)
                .padding(top = 8.dp, bottom = 5.dp, start = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = entry.abbrRu,
                color = textColor,
                fontSize = 15.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.nameRu,
                color = textColor.copy(alpha = 0.78f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (hasAudio) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(13.dp),
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
