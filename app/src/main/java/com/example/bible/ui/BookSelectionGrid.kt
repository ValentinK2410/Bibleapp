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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
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
    val books = BibleCanon.allBooks
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
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lazyGridItems(
                    items = books,
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
    val books = BibleCanon.allBooks
    val presence = rememberTimemarkPresenceIndex()
    val tabColors = rememberTranslationTabColorsMap()
    var infoBook by remember { mutableStateOf<CanonBookEntry?>(null) }
    Box(modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        lazyColumnItems(books, key = { it.id }) { entry ->
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
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
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) textColor.copy(alpha = 0.55f) else scheme.outline.copy(alpha = 0.45f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = if (selected) 1.5.dp else 1.dp, color = borderColor, shape = shape)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        color = if (selected) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
        tonalElevation = if (selected) 3.dp else 0.dp,
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp, start = 5.dp, end = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.abbrRu,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.nameRu,
                color = textColor.copy(alpha = 0.9f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasAudio) {
                Icon(
                    Icons.Default.Headphones,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            TimemarkPresenceDots(
                translationCodes = timemarkCodes,
                tabColors = tabColors,
                size = 7.dp,
            )
        }
    }
}
