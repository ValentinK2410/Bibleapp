package com.example.bible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var zoomLevel by remember { mutableIntStateOf(JOURNAL_ZOOM_MONTHS) }
    var focusDate by remember { mutableStateOf(LocalDate.now()) }
    var focusHour by remember { mutableStateOf<Int?>(null) }
    var showYearPicker by remember { mutableStateOf(false) }

    if (editorEntry != null) {
        DailyJournalEditorScreen(
            initial = editorEntry!!,
            onBack = { editorEntry = null },
            onSave = {
                onSave(it)
                editorEntry = null
            },
            onDelete = {
                onDelete(it.id)
                editorEntry = null
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.daily_journal_title))
                        Text(
                            journalZoomTitle(zoomLevel, focusDate),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showYearPicker = true },
                        )
                    }
                },
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
                    editorEntry = DailyJournalEntry(
                        dayKey = focusDate.toString(),
                        hour = if (zoomLevel == JOURNAL_ZOOM_HOURS) focusHour else null,
                        minute = if (zoomLevel == JOURNAL_ZOOM_HOURS && focusHour != null) 0 else null,
                    )
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.daily_journal_search)) },
                    singleLine = true,
                )
            }
            JournalCalendar(
                entries = if (searchQuery.isBlank()) {
                    entries
                } else {
                    val q = searchQuery.trim().lowercase()
                    entries.filter {
                        it.title.lowercase().contains(q) || it.body.lowercase().contains(q)
                    }
                },
                zoomLevel = zoomLevel,
                onZoomLevel = { zoomLevel = it },
                focusDate = focusDate,
                onFocusDate = {
                    focusDate = it
                    selectedDay = it.toString()
                },
                onOpenEntry = { editorEntry = it },
                onPickHour = { hour ->
                    focusHour = hour
                    editorEntry = DailyJournalEntry(
                        dayKey = focusDate.toString(),
                        hour = hour,
                        minute = 0,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (showYearPicker) {
                YearPickerDialog(
                    selectedYear = focusDate.year,
                    onYear = { year ->
                        val day = focusDate.dayOfMonth.coerceAtMost(java.time.YearMonth.of(year, focusDate.monthValue).lengthOfMonth())
                        focusDate = focusDate.withYear(year).withDayOfMonth(day)
                        showYearPicker = false
                    },
                    onDismiss = { showYearPicker = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearPickerDialog(
    selectedYear: Int,
    onYear: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val nowYear = LocalDate.now().year
    val years = (nowYear - 12)..(nowYear + 12)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_pick_year)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                years.forEach { year ->
                    FilterChip(
                        selected = year == selectedYear,
                        onClick = { onYear(year) },
                        label = { Text(year.toString()) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.song_share_pick_cancel)) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailyJournalCard(
    entry: DailyJournalEntry,
    onOpen: () -> Unit,
    onToggleCheck: (String) -> Unit,
    onTogglePin: () -> Unit,
    onOpenVerse: (bookId: String, chapter: Int, verse: Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(onClick = onOpen, onLongClick = onOpen),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        entry.mood?.let {
                            Text(it.emoji, modifier = Modifier.padding(end = 6.dp))
                        }
                        Text(
                            entry.title.ifBlank { stringResource(R.string.daily_journal_untitled) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (entry.body.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            entry.body.trim().lineSequence().first().take(160),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onTogglePin) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = if (entry.pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    )
                }
            }
            entry.verseLabel?.let { label ->
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        val b = entry.verseBookId ?: return@FilledTonalButton
                        val c = entry.verseChapter ?: return@FilledTonalButton
                        val v = entry.verseVerse ?: return@FilledTonalButton
                        onOpenVerse(b, c, v)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label)
                }
            }
            if (entry.checkItems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(4.dp))
                entry.checkItems.take(8).forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(checked = item.done, onCheckedChange = { onToggleCheck(item.id) })
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DailyJournalEditorScreen(
    initial: DailyJournalEntry,
    onBack: () -> Unit,
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
    var hour by remember(initial.id) { mutableStateOf(initial.hour) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daily_journal_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSave(
                                initial.copy(
                                    title = title.trim(),
                                    body = body.trim(),
                                    mood = mood,
                                    pinned = pinned,
                                    checkItems = checks,
                                    hour = hour,
                                    minute = if (hour != null) (initial.minute ?: 0) else null,
                                    verseBookId = verseBook,
                                    verseChapter = verseCh,
                                    verseVerse = verseVs,
                                    verseLabel = verseLabel.takeIf { it.isNotBlank() },
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.daily_journal_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.daily_journal_field_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(stringResource(R.string.journal_pick_time), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = hour == null,
                    onClick = { hour = null },
                    label = { Text("—") },
                )
                (0..23).forEach { h ->
                    FilterChip(
                        selected = hour == h,
                        onClick = { hour = h },
                        label = { Text("%02d:00".format(h)) },
                    )
                }
            }
            Text(
                stringResource(R.string.daily_journal_mood_label),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JournalMood.entries.forEach { m ->
                    val selected = mood == m
                    Surface(
                        onClick = { mood = if (selected) null else m },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Text(
                            "${m.emoji} ${m.labelRu}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.daily_journal_field_body)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Text(
                stringResource(R.string.daily_journal_tasks_section),
                style = MaterialTheme.typography.labelLarge,
            )
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
            Text(
                stringResource(R.string.daily_journal_verse_section),
                style = MaterialTheme.typography.labelLarge,
            )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        verseLabel,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            verseLabel = ""
                            verseBook = null
                            verseCh = null
                            verseVs = null
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                Text(stringResource(R.string.daily_journal_pin))
            }
            if (initial.title.isNotBlank() || initial.body.isNotBlank() || initial.checkItems.isNotEmpty()) {
                TextButton(
                    onClick = { onDelete(initial) },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(
                        stringResource(R.string.attachment_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
