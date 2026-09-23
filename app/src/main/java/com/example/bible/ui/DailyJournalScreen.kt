package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.DailyJournalEntry
import com.example.bible.data.JournalCheckItem
import com.example.bible.data.JournalMood
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyJournalScreen(
    entries: List<DailyJournalEntry>,
    onBack: () -> Unit,
    onSave: (DailyJournalEntry) -> Unit,
    onDelete: (String) -> Unit,
    onOpenVerse: (bookId: String, chapter: Int, verse: Int) -> Unit,
) {
    var selectedDay by remember { mutableStateOf(DailyJournalEntry.todayKey()) }
    var editorEntry by remember { mutableStateOf<DailyJournalEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val dayFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale("ru")) }
    val weekDays = remember {
        val today = LocalDate.now()
        (6 downTo 0).map { today.minusDays(it.toLong()) }
    }

    val filtered = remember(entries, selectedDay, searchQuery) {
        val q = searchQuery.trim().lowercase()
        entries.filter { e ->
            (q.isEmpty() || e.title.lowercase().contains(q) || e.body.lowercase().contains(q)) &&
                (q.isNotEmpty() || e.dayKey == selectedDay)
        }.sortedWith(
            compareByDescending<DailyJournalEntry> { it.pinned }
                .thenByDescending { it.updatedAt },
        )
    }

    if (editorEntry != null) {
        DailyJournalEditorDialog(
            initial = editorEntry!!,
            onDismiss = { editorEntry = null },
            onSave = {
                onSave(it)
                editorEntry = null
            },
            onDelete = {
                onDelete(it.id)
                editorEntry = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daily_journal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editorEntry = DailyJournalEntry(dayKey = selectedDay)
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.daily_journal_new))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.daily_journal_search)) },
                    singleLine = true,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                weekDays.forEach { date ->
                    val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val selected = key == selectedDay
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedDay = key
                            searchQuery = ""
                        },
                        label = {
                            Text(
                                if (key == DailyJournalEntry.todayKey()) {
                                    stringResource(R.string.daily_journal_today)
                                } else {
                                    date.format(dayFormatter)
                                },
                            )
                        },
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    DailyJournalCard(
                        entry = entry,
                        onClick = { editorEntry = entry },
                        onToggleCheck = { itemId ->
                            val updated = entry.copy(
                                checkItems = entry.checkItems.map { c ->
                                    if (c.id == itemId) c.copy(done = !c.done) else c
                                },
                                updatedAt = System.currentTimeMillis(),
                            )
                            onSave(updated)
                        },
                        onOpenVerse = onOpenVerse,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyJournalCard(
    entry: DailyJournalEntry,
    onClick: () -> Unit,
    onToggleCheck: (String) -> Unit,
    onOpenVerse: (bookId: String, chapter: Int, verse: Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.mood?.let { Text(it.emoji, modifier = Modifier.padding(end = 8.dp)) }
                Text(
                    entry.title.ifBlank { stringResource(R.string.daily_journal_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (entry.pinned) {
                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (entry.body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.body.trim().lineSequence().first().take(200),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entry.verseLabel?.let { label ->
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val b = entry.verseBookId ?: return@clickable
                        val c = entry.verseChapter ?: return@clickable
                        val v = entry.verseVerse ?: return@clickable
                        onOpenVerse(b, c, v)
                    },
                )
            }
            if (entry.checkItems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                entry.checkItems.take(5).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.done, onCheckedChange = { onToggleCheck(item.id) })
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyJournalEditorDialog(
    initial: DailyJournalEntry,
    onDismiss: () -> Unit,
    onSave: (DailyJournalEntry) -> Unit,
    onDelete: (DailyJournalEntry) -> Unit,
) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var body by remember(initial.id) { mutableStateOf(initial.body) }
    var mood by remember(initial.id) { mutableStateOf(initial.mood) }
    var pinned by remember(initial.id) { mutableStateOf(initial.pinned) }
    var verseLabel by remember(initial.id) { mutableStateOf(initial.verseLabel.orEmpty()) }
    var verseBook by remember(initial.id) { mutableStateOf(initial.verseBookId) }
    var verseCh by remember(initial.id) { mutableStateOf(initial.verseChapter) }
    var verseVs by remember(initial.id) { mutableStateOf(initial.verseVerse) }
    var checks by remember(initial.id) { mutableStateOf(initial.checkItems) }
    var newCheck by remember { mutableStateOf("") }
    var refInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.daily_journal_edit)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.daily_journal_field_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JournalMood.entries.forEach { m ->
                        FilterChip(
                            selected = mood == m,
                            onClick = { mood = if (mood == m) null else m },
                            label = { Text("${m.emoji} ${m.labelRu}") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.daily_journal_field_body)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = refInput,
                        onValueChange = { refInput = it },
                        label = { Text(stringResource(R.string.passage_quick_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            com.example.bible.data.BiblePassageResolve.resolve(refInput)?.let { r ->
                                verseBook = r.bookId
                                verseCh = r.chapter
                                verseVs = r.verse
                                verseLabel = r.label
                            }
                        },
                    ) { Text(stringResource(R.string.daily_journal_add_verse)) }
                }
                if (verseLabel.isNotBlank()) {
                    Text(verseLabel, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCheck,
                        onValueChange = { newCheck = it },
                        label = { Text(stringResource(R.string.daily_journal_new_task)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            val t = newCheck.trim()
                            if (t.isNotEmpty()) {
                                checks = checks + JournalCheckItem(text = t)
                                newCheck = ""
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
                checks.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = c.done,
                            onCheckedChange = {
                                checks = checks.map { if (it.id == c.id) it.copy(done = !it.done) else it }
                            },
                        )
                        Text(c.text, modifier = Modifier.weight(1f))
                        IconButton(onClick = { checks = checks.filter { it.id != c.id } }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text(stringResource(R.string.daily_journal_pin))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            body = body.trim(),
                            mood = mood,
                            pinned = pinned,
                            checkItems = checks,
                            verseBookId = verseBook,
                            verseChapter = verseCh,
                            verseVerse = verseVs,
                            verseLabel = verseLabel.takeIf { it.isNotBlank() },
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
            ) { Icon(Icons.Default.Check, contentDescription = null) }
        },
        dismissButton = {
            Row {
                if (initial.title.isNotBlank() || initial.body.isNotBlank()) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text(stringResource(R.string.attachment_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.song_share_pick_cancel))
                }
            }
        },
    )
}
