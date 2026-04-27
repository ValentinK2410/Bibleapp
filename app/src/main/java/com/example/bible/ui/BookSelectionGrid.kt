package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.BibleCanon
import com.example.bible.data.CanonBookEntry
import com.example.bible.data.CanonBookGroup

enum class BookLayoutMode {
    GRID,
    LIST,
}

@Composable
fun groupTextColor(group: CanonBookGroup): Color {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        when (group) {
            CanonBookGroup.PENTATEUCH -> Color(0xFFB8C5E8)
            CanonBookGroup.HISTORY -> Color(0xFFD4A574)
            CanonBookGroup.WISDOM -> Color(0xFF7BCB8A)
            CanonBookGroup.MAJOR_PROPHETS -> Color(0xFFE8A0B0)
            CanonBookGroup.MINOR_PROPHETS -> Color(0xFF9AAD6E)
            CanonBookGroup.GOSPELS -> Color(0xFFE8A060)
            CanonBookGroup.ACTS -> Color(0xFF5EC4E0)
            CanonBookGroup.GENERAL_EPISTLES -> Color(0xFF5DD65D)
            CanonBookGroup.PAULINE -> Color(0xFFE8D860)
            CanonBookGroup.HEBREWS -> Color(0xFFF5F5F5)
            CanonBookGroup.REVELATION -> Color(0xFFC06060)
        }
    } else {
        when (group) {
            CanonBookGroup.PENTATEUCH -> Color(0xFF2E4A8F)
            CanonBookGroup.HISTORY -> Color(0xFF8B5E2F)
            CanonBookGroup.WISDOM -> Color(0xFF2D7A3E)
            CanonBookGroup.MAJOR_PROPHETS -> Color(0xFFA33050)
            CanonBookGroup.MINOR_PROPHETS -> Color(0xFF556B2F)
            CanonBookGroup.GOSPELS -> Color(0xFFB86E1A)
            CanonBookGroup.ACTS -> Color(0xFF1A7A8F)
            CanonBookGroup.GENERAL_EPISTLES -> Color(0xFF2D8F2D)
            CanonBookGroup.PAULINE -> Color(0xFF8F7A1A)
            CanonBookGroup.HEBREWS -> Color(0xFF444444)
            CanonBookGroup.REVELATION -> Color(0xFF9A2020)
        }
    }
}

@Composable
fun BookSelectionContent(
    layoutMode: BookLayoutMode,
    modifier: Modifier = Modifier,
    booksWithAudio: Set<String> = emptySet(),
    onBookClick: (String) -> Unit,
) {
    when (layoutMode) {
        BookLayoutMode.GRID -> BookSelectionGrid(modifier = modifier, booksWithAudio = booksWithAudio, onBookClick = onBookClick)
        BookLayoutMode.LIST -> BookSelectionList(modifier = modifier, booksWithAudio = booksWithAudio, onBookClick = onBookClick)
    }
}

@Composable
fun BookSelectionGrid(
    modifier: Modifier = Modifier,
    booksWithAudio: Set<String> = emptySet(),
    onBookClick: (String) -> Unit,
) {
    val books = BibleCanon.allBooks
    var selectedId by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = modifier
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
                    onClick = {
                        selectedId = entry.id
                        onBookClick(entry.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun BookSelectionList(
    modifier: Modifier = Modifier,
    booksWithAudio: Set<String> = emptySet(),
    onBookClick: (String) -> Unit,
) {
    val books = BibleCanon.allBooks
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        lazyColumnItems(books, key = { it.id }) { entry ->
            val textColor = groupTextColor(entry.group)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBookClick(entry.id) }
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
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
}

@Composable
private fun BookCell(
    entry: CanonBookEntry,
    selected: Boolean,
    hasAudio: Boolean = false,
    onClick: () -> Unit,
) {
    val textColor = groupTextColor(entry.group)
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected)
            MaterialTheme.colorScheme.surfaceContainerHighest
        else
            MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (selected) 4.dp else 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = entry.abbrRu,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.nameRu,
                    color = textColor.copy(alpha = 0.65f),
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasAudio) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(width = 12.dp, height = 10.dp),
                    )
                }
            }
        }
    }
}
