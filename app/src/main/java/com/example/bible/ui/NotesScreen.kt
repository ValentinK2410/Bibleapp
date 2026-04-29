package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.bible.R
import com.example.bible.data.NoteJournalEntry
import com.example.bible.data.NoteSpan
import com.example.bible.data.TranslationId
import com.example.bible.data.UserNote
import com.example.bible.data.UserNoteKind
import com.example.bible.data.previewText
import com.example.bible.data.kindDisplayShort
import com.example.bible.data.labelRu

private enum class NotesListFilter {
    ALL,
    LINKED_VERSE,
    QUESTIONS,
    REFLECTIONS,
    WITH_JOURNAL,
}

private fun UserNote.matchesFilter(f: NotesListFilter): Boolean = when (f) {
    NotesListFilter.ALL -> true
    NotesListFilter.LINKED_VERSE -> hasVerseRef()
    NotesListFilter.QUESTIONS -> kind == UserNoteKind.QUESTION
    NotesListFilter.REFLECTIONS -> kind == UserNoteKind.REFLECTION
    NotesListFilter.WITH_JOURNAL -> journalEntries.isNotEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    notes: List<UserNote>,
    onCreateNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onBack: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<UserNote?>(null) }
    var listFilter by remember { mutableStateOf(NotesListFilter.ALL) }
    val sorted = remember(notes, listFilter) {
        notes.filter { it.matchesFilter(listFilter) }.sortedByDescending { it.updatedAt }
    }
    val dateFormat = remember {
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Заметки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNote) {
                Icon(Icons.Default.Add, contentDescription = "Новая заметка")
            }
        },
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Заметок пока нет.\nНажмите + чтобы создать.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        NotesListFilter.ALL to "Все",
                        NotesListFilter.LINKED_VERSE to "К стиху",
                        NotesListFilter.QUESTIONS to "Вопросы",
                        NotesListFilter.REFLECTIONS to "Размышления",
                        NotesListFilter.WITH_JOURNAL to "Хронология",
                    ).forEach { (f, label) ->
                        FilterChip(
                            selected = listFilter == f,
                            onClick = { listFilter = f },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                items(sorted, key = { it.id }) { note ->
                    NoteListItem(
                        note = note,
                        dateFormat = dateFormat,
                        onClick = { onOpenNote(note.id) },
                        onDelete = { deleteTarget = note },
                    )
                    HorizontalDivider()
                }
                }
            }
        }
    }

    deleteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить заметку?") },
            text = { Text("\"${note.title.ifEmpty { "Без названия" }}\" будет удалена безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteNote(note.id)
                    deleteTarget = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun NoteListItem(
    note: UserNote,
    dateFormat: java.text.SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifEmpty { "Без названия" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = buildString {
                    append(note.kindDisplayShort())
                    note.displayVerseLabel()?.let { append(" · "); append(it) }
                    if (note.journalEntries.isNotEmpty()) {
                        append(" · хронология: ")
                        append(note.journalEntries.size)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            val pv = note.previewText()
            if (pv.isNotEmpty()) {
                Text(
                    text = pv.take(120),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = dateFormat.format(java.util.Date(note.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val textColors = listOf(
    Color.White,
    Color(0xFFFF5252), Color(0xFFFF1744), Color(0xFFD50000),
    Color(0xFFFF9800), Color(0xFFFF6D00), Color(0xFFFFAB40),
    Color(0xFFFFEB3B), Color(0xFFFFD600), Color(0xFFFFF176),
    Color(0xFF4CAF50), Color(0xFF00E676), Color(0xFF1B5E20),
    Color(0xFF2196F3), Color(0xFF448AFF), Color(0xFF0D47A1),
    Color(0xFF9C27B0), Color(0xFFE040FB), Color(0xFF7B1FA2),
    Color(0xFF00BCD4), Color(0xFF00E5FF), Color(0xFF006064),
    Color(0xFFE91E63), Color(0xFFFF80AB), Color(0xFF880E4F),
    Color(0xFF8BC34A), Color(0xFFCCFF90), Color(0xFF33691E),
    Color(0xFF607D8B), Color(0xFF90A4AE), Color(0xFF37474F),
)

private val bgColors = listOf(
    Color.Transparent,
    Color(0x40FF5252), Color(0x40FF9800), Color(0x40FFEB3B),
    Color(0x404CAF50), Color(0x402196F3), Color(0x409C27B0),
    Color(0x4000BCD4), Color(0x40E91E63), Color(0x408BC34A),
    Color(0x80FF5252), Color(0x80FF9800), Color(0x80FFEB3B),
    Color(0x804CAF50), Color(0x802196F3), Color(0x809C27B0),
    Color(0x8000BCD4), Color(0x80E91E63), Color(0x808BC34A),
    Color(0xFFFF5252), Color(0xFFFF9800), Color(0xFFFFEB3B),
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0),
    Color(0xFF00BCD4), Color(0xFFE91E63), Color(0xFF8BC34A),
)

private val fontSizeOptions = listOf(12, 14, 16, 18, 20, 24, 28, 32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    initialNote: UserNote,
    allNotes: List<UserNote> = emptyList(),
    customKinds: List<String> = emptyList(),
    onAddCustomKind: (String) -> Unit = {},
    onSave: (UserNote) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf(initialNote.title) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialNote.body, TextRange(initialNote.body.length)))
    }
    var spans by remember { mutableStateOf(initialNote.spans.toMutableList()) }
    var kind by remember(initialNote.id) { mutableStateOf(initialNote.kind) }
    var customKindLabel by remember(initialNote.id) {
        mutableStateOf(initialNote.customKindLabel.orEmpty())
    }
    var showAddKindDialog by remember { mutableStateOf(false) }
    var newKindDraft by remember { mutableStateOf("") }
    val customKindChips = remember(customKinds, initialNote.id, initialNote.customKindLabel) {
        val fromNote = initialNote.customKindLabel?.trim()?.takeIf { it.isNotEmpty() }
        val merged = customKinds.toMutableList()
        if (fromNote != null && merged.none { it.equals(fromNote, ignoreCase = true) }) {
            merged.add(0, fromNote)
        }
        merged.distinct()
    }
    var linkedQuestionId by remember(initialNote.id) { mutableStateOf(initialNote.linkedQuestionId) }
    var journalEntries by remember(initialNote.id) {
        mutableStateOf(initialNote.journalEntries.toMutableList())
    }
    var linkMenuExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember {
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
    }

    val linkCandidates = remember(allNotes, initialNote.id, initialNote.verseBookId, initialNote.verseChapter, initialNote.verseVerse) {
        allNotes.filter { n ->
            n.id != initialNote.id &&
                n.kind == UserNoteKind.QUESTION &&
                n.verseBookId == initialNote.verseBookId &&
                n.verseChapter == initialNote.verseChapter &&
                n.verseVerse == initialNote.verseVerse
        }
    }

    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var isUnderline by remember { mutableStateOf(false) }
    var currentFontSize by remember { mutableIntStateOf(16) }
    var currentColorArgb by remember { mutableIntStateOf(0) }
    var currentBgColorArgb by remember { mutableIntStateOf(0) }
    var showFontSizePicker by remember { mutableStateOf(false) }
    var colorMode by remember { mutableStateOf(ColorPickerMode.TEXT) }

    fun applyFormatToSelection() {
        val sel = textFieldValue.selection
        if (sel.collapsed) return
        val start = sel.min
        val end = sel.max
        if (start >= end) return

        val newSpan = NoteSpan(
            start = start,
            end = end,
            bold = isBold,
            italic = isItalic,
            underline = isUnderline,
            fontSize = currentFontSize,
            colorArgb = currentColorArgb,
            bgColorArgb = currentBgColorArgb,
        )
        val updated = spans.toMutableList()
        updated.removeAll { it.start < end && it.end > start }
        updated.add(newSpan)
        spans = updated
    }

    fun insertListPrefix(numbered: Boolean) {
        val fullText = textFieldValue.text
        val cursor = textFieldValue.selection.min.coerceIn(0, fullText.length)
        val insertion =
            if (numbered) buildNumberedInsertion(fullText, cursor) else buildBulletInsertion(fullText, cursor)
        val newText = fullText.substring(0, cursor) + insertion + fullText.substring(cursor)
        val newCursor = cursor + insertion.length
        spans = adjustSpansAfterEdit(spans, cursor, insertion.length)
        textFieldValue = TextFieldValue(newText, TextRange(newCursor))
    }

    val spanVisualTransformation = remember(spans) {
        NoteSpanVisualTransformation(spans)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (initialNote.title.isEmpty() && initialNote.body.isEmpty()) "Новая заметка" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val trimmedCustom = customKindLabel.trim()
                        val resolvedKind = when {
                            kind == UserNoteKind.CUSTOM && trimmedCustom.isEmpty() -> UserNoteKind.NOTE
                            kind == UserNoteKind.CUSTOM -> UserNoteKind.CUSTOM
                            else -> kind
                        }
                        val note = initialNote.copy(
                            title = title,
                            body = textFieldValue.text,
                            spans = spans.filter { it.end <= textFieldValue.text.length },
                            updatedAt = System.currentTimeMillis(),
                            kind = resolvedKind,
                            customKindLabel = if (resolvedKind == UserNoteKind.CUSTOM) trimmedCustom else null,
                            linkedQuestionId = if (kind == UserNoteKind.ANSWER) linkedQuestionId else null,
                            journalEntries = journalEntries.toList(),
                            verseTextSnapshot = initialNote.verseTextSnapshot,
                        )
                        onSave(note)
                        onBack()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (initialNote.hasVerseRef()) {
                    val vLabel = initialNote.displayVerseLabel().orEmpty()
                    val tCode = initialNote.verseTranslationCode.orEmpty()
                    val tLabel = remember(tCode) { TranslationId.fromCode(tCode).labelRu }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = if (vLabel.isNotEmpty()) "Стих: $vLabel" else "Стих",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.note_verse_all_translations_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (tCode.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.note_verse_snapshot_translation, tLabel),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            val snap = initialNote.verseTextSnapshot?.trim().orEmpty()
                            if (snap.isNotEmpty()) {
                                Text(
                                    text = snap,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            } else {
                                Text(
                                    text = "Текст стиха не сохранён.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Тип записи",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        UserNoteKind.NOTE,
                        UserNoteKind.QUESTION,
                        UserNoteKind.ANSWER,
                        UserNoteKind.REFLECTION,
                    ).forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                customKindLabel = ""
                                if (k != UserNoteKind.ANSWER) linkedQuestionId = null
                            },
                            label = { Text(k.labelRu(), style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                    customKindChips.forEach { chipLabel ->
                        FilterChip(
                            selected = kind == UserNoteKind.CUSTOM &&
                                customKindLabel.trim().equals(chipLabel.trim(), ignoreCase = true),
                            onClick = {
                                kind = UserNoteKind.CUSTOM
                                customKindLabel = chipLabel
                                linkedQuestionId = null
                            },
                            label = { Text(chipLabel, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                    IconButton(
                        onClick = {
                            newKindDraft = ""
                            showAddKindDialog = true
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить тип записи")
                    }
                }
                if (kind == UserNoteKind.ANSWER && linkCandidates.isNotEmpty()) {
                    val selected = linkCandidates.find { it.id == linkedQuestionId }
                    val displayLink = selected?.let { s ->
                        s.title.ifBlank { s.previewText().take(60) }
                    } ?: "Связать с вопросом (необязательно)"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        OutlinedTextField(
                            value = displayLink,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = linkMenuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { linkMenuExpanded = true },
                            label = { Text("Вопрос к этому стиху") },
                        )
                        DropdownMenu(
                            expanded = linkMenuExpanded,
                            onDismissRequest = { linkMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 320.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text("— не выбран —") },
                                onClick = {
                                    linkedQuestionId = null
                                    linkMenuExpanded = false
                                },
                            )
                            linkCandidates.forEach { q ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            q.title.ifBlank { q.previewText().take(48) },
                                            maxLines = 2,
                                        )
                                    },
                                    onClick = {
                                        linkedQuestionId = q.id
                                        linkMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Название заметки") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                )

                HorizontalDivider()

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val oldLen = textFieldValue.text.length
                        val newLen = newValue.text.length
                        if (newLen != oldLen) {
                            val diff = newLen - oldLen
                            val changePos = newValue.selection.start - (if (diff > 0) diff else 0)
                            spans = adjustSpansAfterEdit(spans, changePos, diff)
                        }
                        textFieldValue = newValue
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                    ),
                    placeholder = {
                        Text(
                            "Мысли, вопросы, ответы, личное понимание…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                        )
                    },
                    visualTransformation = spanVisualTransformation,
                )
                if (journalEntries.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "Как думал раньше",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        journalEntries.sortedBy { it.createdAt }.forEach { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(8.dp),
                            ) {
                                Text(
                                    dateFormat.format(java.util.Date(entry.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        val t = textFieldValue.text.trim()
                        if (t.isEmpty()) return@TextButton
                        journalEntries = (journalEntries + NoteJournalEntry(text = t)).toMutableList()
                        textFieldValue = TextFieldValue("")
                        spans = mutableListOf()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Зафиксировать текст в хронологии и очистить поле", modifier = Modifier.padding(start = 8.dp))
                }
            }

            HorizontalDivider()

            if (showFontSizePicker) {
                FontSizePicker(
                    currentSize = currentFontSize,
                    onSizeSelect = {
                        currentFontSize = it
                        showFontSizePicker = false
                    },
                )
            }

            FormatToolbar(
                isBold = isBold,
                onBoldChange = { isBold = it },
                isItalic = isItalic,
                onItalicChange = { isItalic = it },
                isUnderline = isUnderline,
                onUnderlineChange = { isUnderline = it },
                onBulletList = { insertListPrefix(false) },
                onNumberedList = { insertListPrefix(true) },
                currentFontSize = currentFontSize,
                onFontSizeClick = { showFontSizePicker = !showFontSizePicker },
                currentColorArgb = currentColorArgb,
                currentBgColorArgb = currentBgColorArgb,
                colorMode = colorMode,
                onColorModeChange = { colorMode = it },
                onColorSelect = { currentColorArgb = it },
                onBgColorSelect = { currentBgColorArgb = it },
                onApplyFormat = { applyFormatToSelection() },
            )
        }
    }
    if (showAddKindDialog) {
        AlertDialog(
            onDismissRequest = { showAddKindDialog = false },
            title = { Text("Новый тип записи") },
            text = {
                OutlinedTextField(
                    value = newKindDraft,
                    onValueChange = { newKindDraft = it },
                    label = { Text("Название типа") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = newKindDraft.trim().take(48)
                        if (t.isNotEmpty()) {
                            onAddCustomKind(t)
                            customKindLabel = t
                            kind = UserNoteKind.CUSTOM
                            linkedQuestionId = null
                        }
                        showAddKindDialog = false
                    },
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKindDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

private enum class ColorPickerMode { TEXT, BACKGROUND }

@Composable
private fun FormatToolbar(
    isBold: Boolean,
    onBoldChange: (Boolean) -> Unit,
    isItalic: Boolean,
    onItalicChange: (Boolean) -> Unit,
    isUnderline: Boolean,
    onUnderlineChange: (Boolean) -> Unit,
    onBulletList: () -> Unit,
    onNumberedList: () -> Unit,
    currentFontSize: Int,
    onFontSizeClick: () -> Unit,
    currentColorArgb: Int,
    currentBgColorArgb: Int,
    colorMode: ColorPickerMode,
    onColorModeChange: (ColorPickerMode) -> Unit,
    onColorSelect: (Int) -> Unit,
    onBgColorSelect: (Int) -> Unit,
    onApplyFormat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconToggleButton(
                checked = isBold,
                onCheckedChange = { onBoldChange(it) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.FormatBold,
                    contentDescription = "Жирный",
                    tint = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconToggleButton(
                checked = isItalic,
                onCheckedChange = { onItalicChange(it) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.FormatItalic,
                    contentDescription = "Курсив",
                    tint = if (isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconToggleButton(
                checked = isUnderline,
                onCheckedChange = { onUnderlineChange(it) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.FormatUnderlined,
                    contentDescription = "Подчёркнутый",
                    tint = if (isUnderline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onBulletList, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.FormatListBulleted,
                    contentDescription = "Маркированный список",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNumberedList, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.FormatListNumbered,
                    contentDescription = "Нумерованный список",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable(onClick = onFontSizeClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FormatSize,
                        contentDescription = "Размер шрифта",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("$currentFontSize", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onApplyFormat, modifier = Modifier.height(36.dp)) {
                Text("Применить", fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val textModeActive = colorMode == ColorPickerMode.TEXT
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (textModeActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onColorModeChange(ColorPickerMode.TEXT) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "A Текст",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (textModeActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (!textModeActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onColorModeChange(ColorPickerMode.BACKGROUND) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "▮ Фон",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!textModeActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val colors = if (colorMode == ColorPickerMode.TEXT) textColors else bgColors
        val selectedArgb = if (colorMode == ColorPickerMode.TEXT) currentColorArgb else currentBgColorArgb
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            colors.forEach { color ->
                val argb = color.toArgb()
                val isNone = (color == Color.White && colorMode == ColorPickerMode.TEXT) ||
                    (color == Color.Transparent && colorMode == ColorPickerMode.BACKGROUND)
                val isSelected = if (isNone) selectedArgb == 0 else selectedArgb == argb
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .then(
                            if (color == Color.Transparent)
                                Modifier.background(Color(0xFF333333))
                            else
                                Modifier.background(color)
                        )
                        .then(
                            if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        .clickable {
                            val value = if (isNone) 0 else argb
                            if (colorMode == ColorPickerMode.TEXT) onColorSelect(value)
                            else onBgColorSelect(value)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isNone) {
                        Text("✕", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }
        }
    }
}

@Composable
private fun FontSizePicker(
    currentSize: Int,
    onSizeSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fontSizeOptions.forEach { size ->
            val isSelected = size == currentSize
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onSizeSelect(size) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$size",
                    fontSize = size.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildBulletInsertion(fullText: String, cursor: Int): String {
    val pos = cursor.coerceIn(0, fullText.length)
    val atLineStart = pos == 0 || fullText[pos - 1] == '\n'
    return if (atLineStart) "• " else "\n• "
}

private fun buildNumberedInsertion(fullText: String, cursor: Int): String {
    val pos = cursor.coerceIn(0, fullText.length)
    val before = fullText.substring(0, pos)
    val lastNl = before.lastIndexOf('\n')
    val prevLine = before.substring(lastNl + 1)
    val trimmedPrev = prevLine.trimStart()
    val match = Regex("^(\\d+)\\.\\s").find(trimmedPrev)
    val nextNum = match?.groupValues?.get(1)?.toIntOrNull()?.plus(1) ?: 1
    val prefix = "$nextNum. "
    val atLineStart = pos == 0 || fullText.getOrNull(pos - 1) == '\n'
    return if (atLineStart) prefix else "\n$prefix"
}

private fun adjustSpansAfterEdit(
    spans: List<NoteSpan>,
    changePos: Int,
    diff: Int,
): MutableList<NoteSpan> {
    val result = mutableListOf<NoteSpan>()
    for (span in spans) {
        when {
            span.end <= changePos -> result.add(span)
            span.start >= changePos + (if (diff < 0) -diff else 0) -> {
                result.add(span.copy(start = span.start + diff, end = span.end + diff))
            }
            diff > 0 && span.start <= changePos && span.end >= changePos -> {
                result.add(span.copy(end = span.end + diff))
            }
            diff < 0 -> {
                val delStart = changePos
                val delEnd = changePos - diff
                val newStart = span.start.coerceAtMost(delStart)
                val newEnd = (span.end + diff).coerceAtLeast(newStart)
                if (newEnd > newStart) {
                    result.add(span.copy(start = newStart, end = newEnd))
                }
            }
            else -> result.add(span)
        }
    }
    return result
}

private class NoteSpanVisualTransformation(
    private val spans: List<NoteSpan>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        for (span in spans) {
            if (span.start >= text.length || span.end > text.length || span.start >= span.end) continue
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.underline) TextDecoration.Underline else null,
                fontSize = if (span.fontSize != 16) span.fontSize.sp else TextUnit.Unspecified,
                color = if (span.colorArgb != 0) Color(span.colorArgb) else Color.Unspecified,
                background = if (span.bgColorArgb != 0) Color(span.bgColorArgb) else Color.Unspecified,
            )
            builder.addStyle(style, span.start, span.end.coerceAtMost(text.length))
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
