package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleLibrary
import com.example.bible.data.TranslationId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNavigatorSheet(
    library: BibleLibrary,
    translation: TranslationId,
    currentBookId: String,
    onNavigate: (bookId: String, chapter: Int) -> Unit,
    onDismiss: () -> Unit,
    chaptersWithTimemarks: Set<Int> = emptySet(),
    booksWithTimemarks: Set<String> = emptySet(),
    timemarkDotColor: Color = Color.Unspecified,
) {
    val dotColor = if (timemarkDotColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        timemarkDotColor
    }
    var selectedBookId by remember { mutableStateOf(currentBookId) }
    var step by remember { mutableStateOf(if (currentBookId.isNotEmpty()) "chapters" else "books") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 450.dp)
                .padding(bottom = 16.dp),
        ) {
            if (step == "chapters") {
                val canon = BibleCanon.byId(selectedBookId)
                when (val shellState = rememberBookShell(library, translation, selectedBookId)) {
                    BibleBookShellState.Loading -> {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                    is BibleBookShellState.Ready, is BibleBookShellState.Fallback -> {
                        val book = when (shellState) {
                            is BibleBookShellState.Ready -> shellState.book
                            is BibleBookShellState.Fallback -> shellState.book
                            else -> error("unreachable")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { step = "books" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Книги")
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                book.name.ifBlank { canon?.nameRu ?: selectedBookId },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.weight(1f))
                        }
                        HorizontalDivider()
                        if (book.chapters.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                gridItems(book.chapters, key = { it.number }) { ch ->
                                    val hasTimemarks = ch.number in chaptersWithTimemarks
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 44.dp)
                                            .background(
                                                if (hasTimemarks) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                MaterialTheme.shapes.small,
                                            )
                                            .clickable { onNavigate(selectedBookId, ch.number) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("${ch.number}", fontWeight = FontWeight.Bold)
                                        if (hasTimemarks) {
                                            TimemarkPresenceDot(
                                                visible = true,
                                                color = dotColor,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = (-4).dp, y = 4.dp),
                                                size = 6.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Нет данных",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Выберите книгу",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    gridItems(BibleCanon.allBooks, key = { it.id }) { entry ->
                        val color = groupTextColor(entry.group)
                        val hasTimemarks = entry.id in booksWithTimemarks
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .background(
                                    if (entry.id == selectedBookId) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                )
                                .clickable {
                                    selectedBookId = entry.id
                                    step = "chapters"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                entry.abbrRu,
                                color = color,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            if (hasTimemarks) {
                                TimemarkPresenceDot(
                                    visible = true,
                                    color = dotColor,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = (-3).dp, y = (-3).dp),
                                    size = 5.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
