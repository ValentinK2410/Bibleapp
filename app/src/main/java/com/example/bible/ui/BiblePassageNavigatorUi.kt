package com.example.bible.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.BibleCanon
import com.example.bible.data.BiblePassageResolve
import com.example.bible.data.CanonBookEntry

enum class PassagePickerStep {
    BOOK,
    CHAPTER,
    VERSE,
}

@Composable
fun BibleHomePassageBar(
    onOpenChapters: (bookId: String) -> Unit,
    onOpenVerseGrid: (bookId: String, chapter: Int) -> Unit,
    onOpenReader: (bookId: String, chapter: Int, verse: Int) -> Unit,
    maxVersesInChapter: (bookId: String, chapter: Int) -> Int = { _, _ -> 80 },
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    var quickInput by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { showPicker = true },
                label = { Text(stringResource(R.string.passage_open_menu)) },
                leadingIcon = {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.height(18.dp))
                },
            )
            AssistChip(
                onClick = { showPicker = true },
                label = { Text(stringResource(R.string.passage_open_verse)) },
                leadingIcon = {
                    Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.height(18.dp))
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = quickInput,
                onValueChange = {
                    quickInput = it
                    parseError = false
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.passage_quick_hint)) },
                isError = parseError,
            )
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = {
                    val r = BiblePassageResolve.resolve(quickInput)
                    if (r == null) {
                        parseError = true
                    } else {
                        onOpenReader(r.bookId, r.chapter, r.verse)
                        quickInput = ""
                    }
                },
            ) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.passage_go))
            }
        }
    }

    if (showPicker) {
        BiblePassagePickerDialog(
            onDismiss = { showPicker = false },
            onBookOnly = { bookId ->
                showPicker = false
                onOpenChapters(bookId)
            },
            onChapter = { bookId, chapter ->
                showPicker = false
                onOpenVerseGrid(bookId, chapter)
            },
            onVerse = { bookId, chapter, verse ->
                showPicker = false
                onOpenReader(bookId, chapter, verse)
            },
            maxVersesInChapter = maxVersesInChapter,
        )
    }
}

@Composable
fun BiblePassagePickerDialog(
    onDismiss: () -> Unit,
    onBookOnly: (String) -> Unit,
    onChapter: (bookId: String, chapter: Int) -> Unit,
    onVerse: (bookId: String, chapter: Int, verse: Int) -> Unit,
    maxVersesInChapter: (bookId: String, chapter: Int) -> Int = { _, _ -> 80 },
    initialBookId: String? = null,
) {
    var step by remember(initialBookId) {
        mutableStateOf(
            if (initialBookId != null) PassagePickerStep.CHAPTER else PassagePickerStep.BOOK,
        )
    }
    var selectedBook by remember(initialBookId) {
        mutableStateOf(initialBookId?.let { BibleCanon.byId(it) })
    }
    var selectedChapter by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    PassagePickerStep.BOOK -> stringResource(R.string.passage_pick_book)
                    PassagePickerStep.CHAPTER -> stringResource(
                        R.string.passage_pick_chapter,
                        selectedBook?.abbrRu.orEmpty(),
                    )
                    PassagePickerStep.VERSE -> stringResource(
                        R.string.passage_pick_verse,
                        selectedBook?.abbrRu.orEmpty(),
                        selectedChapter,
                    )
                },
            )
        },
        text = {
            when (step) {
                PassagePickerStep.BOOK -> {
                    LazyColumn(
                        modifier = Modifier.height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(BibleCanon.allBooks, key = { it.id }) { book ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedBook = book
                                        selectedChapter = 1
                                        step = PassagePickerStep.CHAPTER
                                    },
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(book.abbrRu, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        book.nameRu,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                PassagePickerStep.CHAPTER -> {
                    val book = selectedBook ?: return@AlertDialog
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(52.dp),
                        modifier = Modifier.height(320.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items((1..book.chapters).toList()) { ch ->
                            FilterChip(
                                selected = ch == selectedChapter,
                                onClick = {
                                    selectedChapter = ch
                                    step = PassagePickerStep.VERSE
                                },
                                label = { Text("$ch") },
                            )
                        }
                    }
                }
                PassagePickerStep.VERSE -> {
                    val book = selectedBook ?: return@AlertDialog
                    val maxV = remember(book.id, selectedChapter) {
                        maxVersesInChapter(book.id, selectedChapter).coerceIn(1, 176)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(44.dp),
                        modifier = Modifier.height(320.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items((1..maxV).toList()) { v ->
                            FilterChip(
                                selected = false,
                                onClick = { onVerse(book.id, selectedChapter, v) },
                                label = { Text("$v") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                PassagePickerStep.BOOK -> {}
                PassagePickerStep.CHAPTER -> {
                    TextButton(
                        onClick = {
                            selectedBook?.let { onBookOnly(it.id) }
                        },
                    ) { Text(stringResource(R.string.passage_all_chapters)) }
                }
                PassagePickerStep.VERSE -> {
                    TextButton(
                        onClick = {
                            selectedBook?.let { onChapter(it.id, selectedChapter) }
                        },
                    ) { Text(stringResource(R.string.passage_chapter_grid)) }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    when (step) {
                        PassagePickerStep.BOOK -> onDismiss()
                        PassagePickerStep.CHAPTER -> step = PassagePickerStep.BOOK
                        PassagePickerStep.VERSE -> step = PassagePickerStep.CHAPTER
                    }
                },
            ) {
                Text(
                    if (step == PassagePickerStep.BOOK) {
                        stringResource(R.string.song_share_pick_cancel)
                    } else {
                        stringResource(R.string.back)
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookOpenActionsDialog(
    book: CanonBookEntry,
    onDismiss: () -> Unit,
    onChapters: () -> Unit,
    onPickVerse: () -> Unit,
    onReadStart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.nameRu) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = onChapters, label = { Text(stringResource(R.string.passage_action_chapters)) })
                AssistChip(onClick = onPickVerse, label = { Text(stringResource(R.string.passage_action_pick_verse)) })
                AssistChip(onClick = onReadStart, label = { Text(stringResource(R.string.passage_action_read)) })
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.song_share_pick_cancel))
            }
        },
    )
}
