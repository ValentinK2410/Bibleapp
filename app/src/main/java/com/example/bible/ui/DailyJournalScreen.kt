package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.DailyJournalEntry
import com.example.bible.data.JournalCheckItem
import com.example.bible.data.JournalMood
import java.time.LocalDate
import java.time.ZoneId
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
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val dateTimeFmt = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM HH:mm", Locale("ru")) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru")) }
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
    var allDay by remember(initial.id) { mutableStateOf(initial.allDay || initial.hour == null) }
    var startDay by remember(initial.id) {
        mutableStateOf(runCatching { LocalDate.parse(initial.dayKey) }.getOrElse { LocalDate.now() })
    }
    var startHour by remember(initial.id) { mutableIntStateOf(initial.hour ?: 1) }
    var startMinute by remember(initial.id) { mutableIntStateOf(initial.minute ?: 30) }
    var endDay by remember(initial.id) {
        mutableStateOf(
            runCatching { LocalDate.parse(initial.endDayKey ?: initial.dayKey) }.getOrElse { startDay },
        )
    }
    var endHour by remember(initial.id) { mutableIntStateOf(initial.endHour ?: ((initial.hour ?: 1) + 1).coerceAtMost(23)) }
    var endMinute by remember(initial.id) { mutableIntStateOf(initial.endMinute ?: (initial.minute ?: 30)) }
    var repeat by remember(initial.id) { mutableStateOf(initial.repeat) }
    var reminderOn by remember(initial.id) { mutableStateOf(initial.reminderOn) }
    var reminderMinutes by remember(initial.id) { mutableIntStateOf(initial.reminderMinutes) }
    var location by remember(initial.id) { mutableStateOf(initial.location) }
    var picker by remember { mutableStateOf<String?>(null) }
    val zoneLabel = remember {
        val zone = ZoneId.systemDefault()
        val offset = zone.rules.getOffset(java.time.Instant.now())
        val hours = offset.totalSeconds / 3600
        "GMT ${if (hours >= 0) "+" else ""}$hours:00"
    }

    fun formatWhen(day: LocalDate, hour: Int, minute: Int): String {
        val stamp = day.atTime(hour, minute)
        return if (allDay) stamp.format(dateFmt) else stamp.format(dateTimeFmt)
    }

    fun openDateThenTime(day: LocalDate, hour: Int, minute: Int, onResult: (LocalDate, Int, Int) -> Unit) {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                if (allDay) {
                    onResult(picked, hour, minute)
                } else {
                    android.app.TimePickerDialog(
                        context,
                        { _, h, m -> onResult(picked, h, m) },
                        hour,
                        minute,
                        true,
                    ).show()
                }
            },
            day.year,
            day.monthValue - 1,
            day.dayOfMonth,
        ).show()
    }

    if (picker == "repeat") {
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(stringResource(R.string.journal_event_repeat)) },
            text = {
                Column {
                    listOf(
                        "none" to R.string.journal_event_repeat_none,
                        "daily" to R.string.journal_event_repeat_daily,
                        "weekly" to R.string.journal_event_repeat_weekly,
                        "monthly" to R.string.journal_event_repeat_monthly,
                        "yearly" to R.string.journal_event_repeat_yearly,
                    ).forEach { (code, label) ->
                        TextButton(onClick = { repeat = code; picker = null }) {
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = null }) { Text(stringResource(R.string.song_share_pick_cancel)) }
            },
        )
    }
    if (picker == "remind") {
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(stringResource(R.string.journal_event_reminders)) },
            text = {
                Column {
                    listOf(5, 10, 30, 60, 1440).forEach { minutes ->
                        TextButton(onClick = { reminderMinutes = minutes; picker = null }) {
                            Text(journalReminderLabel(minutes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = null }) { Text(stringResource(R.string.song_share_pick_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial.title.isBlank()) {
                            stringResource(R.string.journal_event_new)
                        } else {
                            stringResource(R.string.journal_event_edit)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onSave(
                                initial.copy(
                                    dayKey = startDay.toString(),
                                    title = title.trim(),
                                    body = body.trim(),
                                    mood = mood,
                                    pinned = pinned,
                                    checkItems = checks,
                                    allDay = allDay,
                                    hour = if (allDay) null else startHour,
                                    minute = if (allDay) null else startMinute,
                                    endDayKey = endDay.toString(),
                                    endHour = if (allDay) null else endHour,
                                    endMinute = if (allDay) null else endMinute,
                                    repeat = repeat,
                                    reminderOn = reminderOn,
                                    reminderMinutes = reminderMinutes,
                                    location = location.trim(),
                                    verseBookId = verseBook,
                                    verseChapter = verseCh,
                                    verseVerse = verseVs,
                                    verseLabel = verseLabel.takeIf { it.isNotBlank() },
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.daily_journal_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(stringResource(R.string.journal_event_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            HorizontalDivider()
            EventSwitchRow(stringResource(R.string.journal_event_all_day), allDay) { allDay = it }
            HorizontalDivider()
            EventValueRow(
                title = stringResource(R.string.journal_event_from),
                value = formatWhen(startDay, startHour, startMinute),
                onClick = {
                    openDateThenTime(startDay, startHour, startMinute) { day, hour, minute ->
                        startDay = day
                        startHour = hour
                        startMinute = minute
                    }
                },
            )
            EventValueRow(
                title = stringResource(R.string.journal_event_to),
                value = formatWhen(endDay, endHour, endMinute),
                onClick = {
                    openDateThenTime(endDay, endHour, endMinute) { day, hour, minute ->
                        endDay = day
                        endHour = hour
                        endMinute = minute
                    }
                },
            )
            HorizontalDivider()
            EventValueRow(
                title = stringResource(R.string.journal_event_repeat),
                value = journalRepeatLabel(repeat),
                onClick = { picker = "repeat" },
            )
            EventValueRow(
                title = stringResource(R.string.journal_event_reminders),
                value = journalReminderLabel(reminderMinutes),
                onClick = { picker = "remind" },
            )
            EventSwitchRow(stringResource(R.string.journal_event_reminders), reminderOn) { on ->
                reminderOn = on
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            HorizontalDivider()
            EventValueRow(
                title = stringResource(R.string.journal_event_account),
                value = stringResource(R.string.journal_event_account_local),
                onClick = {},
            )
            EventValueRow(
                title = stringResource(R.string.journal_event_timezone),
                value = zoneLabel,
                onClick = {},
            )
            HorizontalDivider()
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                placeholder = { Text(stringResource(R.string.journal_event_place)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.daily_journal_field_body)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(120.dp),
            )
            Text(
                stringResource(R.string.daily_journal_tasks_section),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
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
            if (initial.title.isNotBlank() || initial.body.isNotBlank() || initial.location.isNotBlank()) {
                TextButton(onClick = { onDelete(initial) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.attachment_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EventSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun EventValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

private fun journalRepeatLabel(code: String): String = when (code) {
    "daily" -> "Каждый день"
    "weekly" -> "Каждую неделю"
    "monthly" -> "Каждый месяц"
    "yearly" -> "Каждый год"
    else -> "Однократное мероприятие"
}

private fun journalReminderLabel(minutes: Int): String = when (minutes) {
    5 -> "5 мин. до события"
    10 -> "10 мин. до события"
    30 -> "30 мин. до события"
    60 -> "1 ч. до события"
    1440 -> "1 день до события"
    else -> "$minutes мин. до события"
}
