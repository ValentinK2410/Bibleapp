package com.example.bible.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.ui.text.buildAnnotatedString
import com.example.bible.data.NoteJournalEntry
import com.example.bible.data.NoteScriptureLinks
import com.example.bible.data.ParsedScriptureNavigation
import com.example.bible.data.ScriptureLinkRange
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
    embeddedBible: (@Composable (Modifier, NoteBibleNavigation?, () -> Unit) -> Unit)? = null,
) {
    var isViewMode by remember(initialNote.id) {
        mutableStateOf(initialNote.title.isNotBlank() || initialNote.body.isNotBlank())
    }
    var bibleNavRequest by remember { mutableStateOf<NoteBibleNavigation?>(null) }
    var lastNavLinkKey by remember { mutableStateOf<String?>(null) }
    var lastNavShowFullChapter by remember { mutableStateOf(false) }
    var lastAudioLinkKey by remember { mutableStateOf<String?>(null) }
    var lastAudioSegmentIndex by remember { mutableIntStateOf(-1) }
    fun navigateEmbeddedBible(parsed: ParsedScriptureNavigation) {
        if (embeddedBible == null || parsed.verses.isEmpty()) return
        if (parsed.audio != null) {
            val key = NoteScriptureLinks.formatNavigationAnnotation(
                parsed.bookId,
                parsed.chapter,
                parsed.verses,
                parsed.audio,
            )
            val segmentCount = NoteScriptureLinks.segmentCountForNavigation(parsed).coerceAtLeast(1)
            val segmentIndex = if (
                lastAudioLinkKey == key &&
                com.example.bible.data.BibleAudioPlayer.state.value.isPlaying
            ) {
                (lastAudioSegmentIndex + 1) % segmentCount
            } else {
                0
            }
            lastAudioLinkKey = key
            lastAudioSegmentIndex = segmentIndex
            bibleNavRequest = NoteBibleNavigation(
                bookId = parsed.bookId,
                chapter = parsed.chapter,
                verses = parsed.verses,
                showFullChapter = NoteScriptureLinks.showFullChapterForAudio(parsed, 0),
                playAudio = true,
                translationCode = parsed.audio.translationCode,
                audioPlayMode = parsed.audio.mode,
                audioSegmentSpec = parsed.audio.segmentSpec,
                audioSegmentIndex = segmentIndex,
            )
            return
        }
        val key = NoteScriptureLinks.formatNavigationAnnotation(parsed.bookId, parsed.chapter, parsed.verses)
        val showFullChapter = if (lastNavLinkKey == key) !lastNavShowFullChapter else false
        lastNavLinkKey = key
        lastNavShowFullChapter = showFullChapter
        bibleNavRequest = NoteBibleNavigation(
            bookId = parsed.bookId,
            chapter = parsed.chapter,
            verses = parsed.verses,
            showFullChapter = showFullChapter,
        )
    }
    var title by remember(initialNote.id) { mutableStateOf(initialNote.title) }
    var textFieldValue by remember(initialNote.id) {
        mutableStateOf(TextFieldValue(initialNote.body, TextRange(initialNote.body.length)))
    }
    var spans by remember(initialNote.id) { mutableStateOf(initialNote.spans.toMutableList()) }
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
    var showColorPicker by remember { mutableStateOf(false) }
    var kindMenuExpanded by remember { mutableStateOf(false) }
    var verseCardExpanded by remember { mutableStateOf(false) }
    var journalExpanded by remember { mutableStateOf(false) }

    fun currentFormatRange(): Pair<Int, Int>? {
        val text = textFieldValue.text
        val sel = textFieldValue.selection
        if (!sel.collapsed && sel.min < sel.max) return sel.min to sel.max
        val cursor = sel.min.coerceIn(0, text.length)
        var start = cursor
        var end = cursor
        fun Char.isWordPart(): Boolean = isLetterOrDigit() || this == '-'
        while (start > 0 && text[start - 1].isWordPart()) start--
        while (end < text.length && text[end].isWordPart()) end++
        if (end <= start) return null
        return start to end
    }

    fun hasActiveFormat(
        bold: Boolean = isBold,
        italic: Boolean = isItalic,
        underline: Boolean = isUnderline,
        fontSize: Int = currentFontSize,
        colorArgb: Int = currentColorArgb,
        bgColorArgb: Int = currentBgColorArgb,
    ): Boolean = bold || italic || underline || fontSize != 16 || colorArgb != 0 || bgColorArgb != 0

    fun applyFormatToSelection(
        bold: Boolean = isBold,
        italic: Boolean = isItalic,
        underline: Boolean = isUnderline,
        fontSize: Int = currentFontSize,
        colorArgb: Int = currentColorArgb,
        bgColorArgb: Int = currentBgColorArgb,
    ) {
        val range = currentFormatRange() ?: return
        val start = range.first
        val end = range.second
        val updated = spans.toMutableList()
        updated.removeAll { it.start < end && it.end > start }
        if (hasActiveFormat(bold, italic, underline, fontSize, colorArgb, bgColorArgb)) {
            updated.add(
                NoteSpan(
                    start = start,
                    end = end,
                    bold = bold,
                    italic = italic,
                    underline = underline,
                    fontSize = fontSize,
                    colorArgb = colorArgb,
                    bgColorArgb = bgColorArgb,
                ),
            )
        }
        spans = updated
        if (textFieldValue.selection.collapsed) {
            textFieldValue = textFieldValue.copy(selection = TextRange(start, end))
        }
    }

    fun applyTypingFormat(start: Int, end: Int) {
        if (end <= start || !hasActiveFormat()) return
        val updated = spans.toMutableList()
        val merge = updated.lastOrNull { span ->
            span.end == start &&
                span.bold == isBold &&
                span.italic == isItalic &&
                span.underline == isUnderline &&
                span.fontSize == currentFontSize &&
                span.colorArgb == currentColorArgb &&
                span.bgColorArgb == currentBgColorArgb
        }
        if (merge != null) {
            updated.remove(merge)
            updated.add(merge.copy(end = end))
        } else {
            updated.add(
                NoteSpan(
                    start = start,
                    end = end,
                    bold = isBold,
                    italic = isItalic,
                    underline = isUnderline,
                    fontSize = currentFontSize,
                    colorArgb = currentColorArgb,
                    bgColorArgb = currentBgColorArgb,
                ),
            )
        }
        spans = updated
    }

    fun syncFormatFromCursor(value: TextFieldValue) {
        val textLen = value.text.length
        if (textLen == 0) {
            isBold = false
            isItalic = false
            isUnderline = false
            currentFontSize = 16
            currentColorArgb = 0
            currentBgColorArgb = 0
            return
        }
        val lookAt = if (value.selection.collapsed) {
            (value.selection.min - 1).coerceAtLeast(0)
        } else {
            value.selection.min
        }.coerceIn(0, textLen - 1)
        val hit = spans.lastOrNull { lookAt >= it.start && lookAt < it.end && it.end <= textLen }
        if (hit != null) {
            isBold = hit.bold
            isItalic = hit.italic
            isUnderline = hit.underline
            currentFontSize = hit.fontSize
            currentColorArgb = hit.colorArgb
            currentBgColorArgb = hit.bgColorArgb
        } else {
            isBold = false
            isItalic = false
            isUnderline = false
            currentFontSize = 16
            currentColorArgb = 0
            currentBgColorArgb = 0
        }
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

    val spanVisualTransformation = remember(spans, textFieldValue.text.length) {
        val textLen = textFieldValue.text.length
        NoteSpanVisualTransformation(
            spans.filter { it.start >= 0 && it.end <= textLen && it.start < it.end },
        )
    }

    LaunchedEffect(isViewMode) {
        if (!isViewMode) {
            val textLen = textFieldValue.text.length
            spans = spans.filter { it.start >= 0 && it.end <= textLen && it.start < it.end }.toMutableList()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when {
                            isViewMode -> title.ifBlank { initialNote.title }.ifBlank { stringResource(R.string.note_view_title) }
                            initialNote.title.isEmpty() && initialNote.body.isEmpty() -> stringResource(R.string.note_new_title)
                            else -> stringResource(R.string.note_edit_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isViewMode) {
                        IconButton(onClick = { isViewMode = false }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.note_switch_to_edit))
                        }
                    } else {
                        IconButton(onClick = { isViewMode = true }) {
                            Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.note_switch_to_view))
                        }
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
                    }
                },
            )
        },
    ) { padding ->
        val noteEditorBody: @Composable (Modifier) -> Unit = { noteModifier ->
            val viewScrollState = rememberScrollState()
            val verseRefBlock: @Composable () -> Unit = {
                if (initialNote.hasVerseRef()) {
                    val vLabel = initialNote.displayVerseLabel().orEmpty()
                    val tCode = initialNote.verseTranslationCode.orEmpty()
                    val tLabel = remember(tCode) { TranslationId.fromCode(tCode).labelRu }
                    val linkedRef = initialNote.verseRefOrNull()
                    val snap = initialNote.verseTextSnapshot?.trim().orEmpty()
                    val showDetails = isViewMode || verseCardExpanded
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .then(
                                when {
                                    isViewMode && linkedRef != null && embeddedBible != null ->
                                        Modifier.clickable {
                                            navigateEmbeddedBible(
                                                ParsedScriptureNavigation(
                                                    bookId = linkedRef.bookId,
                                                    chapter = linkedRef.chapter,
                                                    verses = setOf(linkedRef.verse),
                                                ),
                                            )
                                        }
                                    !isViewMode -> Modifier.clickable { verseCardExpanded = !verseCardExpanded }
                                    else -> Modifier
                                },
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (vLabel.isNotEmpty()) "Стих: $vLabel" else "Стих",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!isViewMode) {
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        contentDescription = if (verseCardExpanded) "Свернуть стих" else "Показать стих",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(if (verseCardExpanded) 180f else 0f),
                                    )
                                }
                            }
                            if (showDetails) {
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
                }
            }

            Column(
                modifier = noteModifier.fillMaxWidth(),
            ) {
                key(isViewMode) {
                if (isViewMode) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(viewScrollState),
                    ) {
                        verseRefBlock()
                        NoteViewSection(
                            title = title.ifBlank { initialNote.title },
                            body = textFieldValue.text.ifBlank { initialNote.body },
                            spans = spans.ifEmpty { initialNote.spans },
                            kind = kind,
                            customKindLabel = customKindLabel,
                            journalEntries = journalEntries.ifEmpty { initialNote.journalEntries },
                            dateFormat = dateFormat,
                            bibleLinksEnabled = embeddedBible != null,
                            onScriptureLinkClick = ::navigateEmbeddedBible,
                        )
                    }
                } else {
                    val editorFieldColors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    )
                    val editKindLabel = when (kind) {
                        UserNoteKind.CUSTOM -> customKindLabel.trim().ifBlank { kind.labelRu() }
                        else -> kind.labelRu()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        verseRefBlock()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                AssistChip(
                                    onClick = { kindMenuExpanded = true },
                                    label = {
                                        Text(
                                            editKindLabel,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.ExpandMore,
                                            contentDescription = "Тип записи",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    ),
                                )
                                DropdownMenu(
                                    expanded = kindMenuExpanded,
                                    onDismissRequest = { kindMenuExpanded = false },
                                ) {
                                    listOf(
                                        UserNoteKind.NOTE,
                                        UserNoteKind.QUESTION,
                                        UserNoteKind.ANSWER,
                                        UserNoteKind.REFLECTION,
                                    ).forEach { k ->
                                        DropdownMenuItem(
                                            text = { Text(k.labelRu()) },
                                            onClick = {
                                                kind = k
                                                customKindLabel = ""
                                                if (k != UserNoteKind.ANSWER) linkedQuestionId = null
                                                kindMenuExpanded = false
                                            },
                                            leadingIcon = if (kind == k) {
                                                { Icon(Icons.Default.Check, contentDescription = null) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                    if (customKindChips.isNotEmpty()) {
                                        HorizontalDivider()
                                    }
                                    customKindChips.forEach { chipLabel ->
                                        val selected = kind == UserNoteKind.CUSTOM &&
                                            customKindLabel.trim().equals(chipLabel.trim(), ignoreCase = true)
                                        DropdownMenuItem(
                                            text = { Text(chipLabel) },
                                            onClick = {
                                                kind = UserNoteKind.CUSTOM
                                                customKindLabel = chipLabel
                                                linkedQuestionId = null
                                                kindMenuExpanded = false
                                            },
                                            leadingIcon = if (selected) {
                                                { Icon(Icons.Default.Check, contentDescription = null) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Добавить тип…") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        onClick = {
                                            kindMenuExpanded = false
                                            newKindDraft = ""
                                            showAddKindDialog = true
                                        },
                                    )
                                }
                            }
                            BasicTextField(
                                value = title,
                                onValueChange = { title = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
                                decorationBox = { inner ->
                                    Box {
                                        if (title.isEmpty()) {
                                            Text(
                                                "Название",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        if (kind == UserNoteKind.ANSWER && linkCandidates.isNotEmpty()) {
                            val selected = linkCandidates.find { it.id == linkedQuestionId }
                            val displayLink = selected?.let { s ->
                                s.title.ifBlank { s.previewText().take(60) }
                            } ?: "Связать с вопросом"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                            ) {
                                AssistChip(
                                    onClick = { linkMenuExpanded = true },
                                    label = {
                                        Text(
                                            displayLink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = linkMenuExpanded)
                                    },
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        ) {
                            if (journalEntries.isNotEmpty()) {
                                TextButton(onClick = { journalExpanded = !journalExpanded }) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Хронология (${journalEntries.size})",
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(start = 2.dp)
                                            .size(18.dp)
                                            .rotate(if (journalExpanded) 180f else 0f),
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    val t = textFieldValue.text.trim()
                                    if (t.isEmpty()) return@TextButton
                                    journalEntries = (journalEntries + NoteJournalEntry(text = t)).toMutableList()
                                    textFieldValue = TextFieldValue("")
                                    spans = mutableListOf()
                                    journalExpanded = true
                                },
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text("В хронологию", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        AnimatedVisibility(visible = journalExpanded && journalEntries.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState()),
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
                        TextField(
                            value = textFieldValue,
                            onValueChange = { newValue ->
                                val oldLen = textFieldValue.text.length
                                val newLen = newValue.text.length
                                if (newLen != oldLen) {
                                    val diff = newLen - oldLen
                                    val changePos = newValue.selection.start - (if (diff > 0) diff else 0)
                                    spans = adjustSpansAfterEdit(spans, changePos, diff)
                                    if (diff > 0) applyTypingFormat(changePos, changePos + diff)
                                    textFieldValue = newValue
                                    if (diff < 0) syncFormatFromCursor(newValue)
                                } else {
                                    textFieldValue = newValue
                                    syncFormatFromCursor(newValue)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                lineHeight = 26.sp,
                            ),
                            placeholder = {
                                Text(
                                    "Мысли, вопросы, ответы, личное понимание…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    fontSize = 17.sp,
                                )
                            },
                            visualTransformation = spanVisualTransformation,
                            colors = editorFieldColors,
                        )
                        Surface(tonalElevation = 2.dp) {
                            FormatToolbar(
                                isBold = isBold,
                                onBoldChange = {
                                    isBold = it
                                    applyFormatToSelection(bold = it)
                                },
                                isItalic = isItalic,
                                onItalicChange = {
                                    isItalic = it
                                    applyFormatToSelection(italic = it)
                                },
                                isUnderline = isUnderline,
                                onUnderlineChange = {
                                    isUnderline = it
                                    applyFormatToSelection(underline = it)
                                },
                                onBulletList = { insertListPrefix(false) },
                                onNumberedList = { insertListPrefix(true) },
                                currentFontSize = currentFontSize,
                                showFontSizePicker = showFontSizePicker,
                                onFontSizeClick = {
                                    showFontSizePicker = !showFontSizePicker
                                    if (showFontSizePicker) showColorPicker = false
                                },
                                onFontSizeSelect = { size ->
                                    currentFontSize = size
                                    applyFormatToSelection(fontSize = size)
                                },
                                currentColorArgb = currentColorArgb,
                                currentBgColorArgb = currentBgColorArgb,
                                colorMode = colorMode,
                                showColorPicker = showColorPicker,
                                onToggleColorPicker = {
                                    showColorPicker = !showColorPicker
                                    if (showColorPicker) showFontSizePicker = false
                                },
                                onColorModeChange = { colorMode = it },
                                onColorSelect = {
                                    currentColorArgb = it
                                    applyFormatToSelection(colorArgb = it)
                                },
                                onBgColorSelect = {
                                    currentBgColorArgb = it
                                    applyFormatToSelection(bgColorArgb = it)
                                },
                            )
                        }
                    }
                }
                }
            }
        }

        if (embeddedBible == null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding(),
            ) {
                noteEditorBody(Modifier.weight(1f))
            }
        } else {
            var splitFraction by remember { mutableFloatStateOf(0.5f) }
            var resizeMode by remember { mutableStateOf(false) }
            var dragFraction by remember { mutableFloatStateOf(0.5f) }
            var contentHeightPx by remember { mutableIntStateOf(1) }
            val density = LocalDensity.current
            val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
            val notePaneFraction = splitFraction.coerceIn(0.25f, 0.75f)
            LaunchedEffect(keyboardOpen) {
                if (keyboardOpen) resizeMode = false
            }
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .onSizeChanged { contentHeightPx = it.height },
            ) {
                Column(Modifier.fillMaxSize()) {
                    noteEditorBody(
                        Modifier
                            .weight(if (keyboardOpen) 1f else notePaneFraction)
                            .fillMaxWidth(),
                    )
                    if (!keyboardOpen) {
                        VerticalSplitHandle(
                            onDragDeltaPx = { dy ->
                                if (contentHeightPx > 0) {
                                    splitFraction = (splitFraction + dy / contentHeightPx).coerceIn(0.25f, 0.75f)
                                }
                            },
                            onLongPress = {
                                dragFraction = splitFraction
                                resizeMode = true
                            },
                        )
                        Box(
                            modifier = Modifier
                                .weight((1f - notePaneFraction).coerceAtLeast(0.01f))
                                .fillMaxWidth(),
                        ) {
                            embeddedBible(Modifier.fillMaxSize(), bibleNavRequest) {
                                bibleNavRequest = null
                            }
                        }
                    }
                }
                if (resizeMode) {
                    ResizeOverlay(
                        fraction = dragFraction,
                        totalHeightPx = contentHeightPx,
                        onFractionChange = { dragFraction = it.coerceIn(0.25f, 0.75f) },
                        onApply = {
                            splitFraction = dragFraction
                            resizeMode = false
                        },
                        onReset = {
                            dragFraction = 0.5f
                            splitFraction = 0.5f
                            resizeMode = false
                        },
                        onDismiss = { resizeMode = false },
                    )
                }
            }
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

private const val SCRIPTURE_LINK_TAG = "scripture_link"

@Composable
private fun NoteViewSection(
    title: String,
    body: String,
    spans: List<NoteSpan>,
    kind: UserNoteKind,
    customKindLabel: String,
    journalEntries: List<NoteJournalEntry>,
    dateFormat: java.text.SimpleDateFormat,
    bibleLinksEnabled: Boolean,
    onScriptureLinkClick: (ParsedScriptureNavigation) -> Unit,
) {
    val kindLabel = remember(kind, customKindLabel) {
        when (kind) {
            UserNoteKind.CUSTOM -> customKindLabel.trim().ifBlank { UserNoteKind.CUSTOM.labelRu() }
            else -> kind.labelRu()
        }
    }
    Text(
        text = kindLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
    if (title.isNotBlank()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
    if (body.isNotBlank()) {
        val linkColor = MaterialTheme.colorScheme.primary
        val audioLinkColor = MaterialTheme.colorScheme.tertiary
        val scriptureLinks = remember(body, bibleLinksEnabled) {
            if (bibleLinksEnabled) NoteScriptureLinks.findInText(body) else emptyList()
        }
        val annotated = remember(body, spans, scriptureLinks, linkColor, audioLinkColor) {
            buildNoteViewAnnotatedString(
                text = body,
                spans = spans,
                scriptureLinks = scriptureLinks,
                linkColor = linkColor,
                audioLinkColor = audioLinkColor,
            )
        }
        ClickableText(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            onClick = { offset ->
                annotated.getStringAnnotations(SCRIPTURE_LINK_TAG, offset, offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        NoteScriptureLinks.parseNavigationAnnotation(annotation.item)
                            ?.let(onScriptureLinkClick)
                    }
            },
        )
        if (bibleLinksEnabled && scriptureLinks.isNotEmpty()) {
            Text(
                text = stringResource(R.string.note_view_scripture_links_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            )
        }
    } else if (title.isBlank()) {
        Text(
            text = stringResource(R.string.note_view_empty),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
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
                    NoteViewPlainOrLinkedText(
                        text = entry.text,
                        bibleLinksEnabled = bibleLinksEnabled,
                        onScriptureLinkClick = onScriptureLinkClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteViewPlainOrLinkedText(
    text: String,
    bibleLinksEnabled: Boolean,
    onScriptureLinkClick: (ParsedScriptureNavigation) -> Unit,
) {
    val scriptureLinks = remember(text, bibleLinksEnabled) {
        if (bibleLinksEnabled) NoteScriptureLinks.findInText(text) else emptyList()
    }
    if (scriptureLinks.isEmpty()) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val audioLinkColor = MaterialTheme.colorScheme.tertiary
    val annotated = remember(text, scriptureLinks, linkColor, audioLinkColor) {
        buildNoteViewAnnotatedString(
            text = text,
            spans = emptyList(),
            scriptureLinks = scriptureLinks,
            linkColor = linkColor,
            audioLinkColor = audioLinkColor,
        )
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        onClick = { offset ->
            annotated.getStringAnnotations(SCRIPTURE_LINK_TAG, offset, offset)
                .firstOrNull()
                ?.let { annotation ->
                    NoteScriptureLinks.parseNavigationAnnotation(annotation.item)
                        ?.let(onScriptureLinkClick)
                }
        },
    )
}

private fun buildNoteViewAnnotatedString(
    text: String,
    spans: List<NoteSpan>,
    scriptureLinks: List<ScriptureLinkRange>,
    linkColor: Color,
    audioLinkColor: Color = linkColor,
): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        for (span in spans) {
            if (span.start >= text.length || span.end > text.length || span.start >= span.end) continue
            addStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = if (span.underline) TextDecoration.Underline else null,
                    fontSize = if (span.fontSize != 16) span.fontSize.sp else TextUnit.Unspecified,
                    color = if (span.colorArgb != 0) Color(span.colorArgb) else Color.Unspecified,
                    background = if (span.bgColorArgb != 0) Color(span.bgColorArgb) else Color.Unspecified,
                ),
                span.start,
                span.end.coerceAtMost(text.length),
            )
        }
        for (link in scriptureLinks) {
            val linkColorEffective = if (link.isAudioLink) audioLinkColor else linkColor
            addStyle(
                SpanStyle(
                    color = linkColorEffective,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold,
                ),
                link.start,
                link.end.coerceAtMost(text.length),
            )
            addStringAnnotation(
                tag = SCRIPTURE_LINK_TAG,
                annotation = NoteScriptureLinks.formatNavigationAnnotation(
                    bookId = link.bookId,
                    chapter = link.chapter,
                    verses = link.verses,
                    audio = link.translationCode?.let { code ->
                        com.example.bible.data.ScriptureAudioNavigation(
                            mode = link.audioPlayMode,
                            translationCode = code,
                            segmentSpec = link.segmentSpec,
                        )
                    },
                ),
                start = link.start,
                end = link.end.coerceAtMost(text.length),
            )
        }
    }
}

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
    showFontSizePicker: Boolean,
    onFontSizeClick: () -> Unit,
    onFontSizeSelect: (Int) -> Unit,
    currentColorArgb: Int,
    currentBgColorArgb: Int,
    colorMode: ColorPickerMode,
    showColorPicker: Boolean,
    onToggleColorPicker: () -> Unit,
    onColorModeChange: (ColorPickerMode) -> Unit,
    onColorSelect: (Int) -> Unit,
    onBgColorSelect: (Int) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        AnimatedVisibility(visible = showFontSizePicker) {
            FontSizePicker(
                currentSize = currentFontSize,
                onSizeSelect = onFontSizeSelect,
            )
        }
        AnimatedVisibility(visible = showColorPicker) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val textModeActive = colorMode == ColorPickerMode.TEXT
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (textModeActive) primary else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onColorModeChange(ColorPickerMode.TEXT) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FormatColorText,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (textModeActive) MaterialTheme.colorScheme.onPrimary else muted,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Текст",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (textModeActive) MaterialTheme.colorScheme.onPrimary else muted,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (!textModeActive) primary else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onColorModeChange(ColorPickerMode.BACKGROUND) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FormatColorFill,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (!textModeActive) MaterialTheme.colorScheme.onPrimary else muted,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Фон",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!textModeActive) MaterialTheme.colorScheme.onPrimary else muted,
                            )
                        }
                    }
                }
                val colors = if (colorMode == ColorPickerMode.TEXT) textColors else bgColors
                val selectedArgb = if (colorMode == ColorPickerMode.TEXT) currentColorArgb else currentBgColorArgb
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    colors.forEach { color ->
                        val argb = color.toArgb()
                        val isNone = (color == Color.White && colorMode == ColorPickerMode.TEXT) ||
                            (color == Color.Transparent && colorMode == ColorPickerMode.BACKGROUND)
                        val isSelected = if (isNone) selectedArgb == 0 else selectedArgb == argb
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .then(
                                    if (color == Color.Transparent) {
                                        Modifier.background(Color(0xFF333333))
                                    } else {
                                        Modifier.background(color)
                                    },
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, primary, CircleShape)
                                    } else {
                                        Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    },
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconToggleButton(
                checked = isBold,
                onCheckedChange = onBoldChange,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.FormatBold,
                    contentDescription = "Жирный",
                    tint = if (isBold) primary else muted,
                )
            }
            IconToggleButton(
                checked = isItalic,
                onCheckedChange = onItalicChange,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.FormatItalic,
                    contentDescription = "Курсив",
                    tint = if (isItalic) primary else muted,
                )
            }
            IconToggleButton(
                checked = isUnderline,
                onCheckedChange = onUnderlineChange,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.FormatUnderlined,
                    contentDescription = "Подчёркнутый",
                    tint = if (isUnderline) primary else muted,
                )
            }
            IconButton(onClick = onBulletList, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.FormatListBulleted,
                    contentDescription = "Маркированный список",
                    tint = muted,
                )
            }
            IconButton(onClick = onNumberedList, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.FormatListNumbered,
                    contentDescription = "Нумерованный список",
                    tint = muted,
                )
            }
            IconToggleButton(
                checked = showFontSizePicker,
                onCheckedChange = { onFontSizeClick() },
                modifier = Modifier.size(40.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FormatSize,
                        contentDescription = "Размер шрифта",
                        modifier = Modifier.size(18.dp),
                        tint = if (showFontSizePicker) primary else muted,
                    )
                    Text(
                        "$currentFontSize",
                        fontSize = 9.sp,
                        color = if (showFontSizePicker) primary else muted,
                    )
                }
            }
            IconToggleButton(
                checked = showColorPicker,
                onCheckedChange = { onToggleColorPicker() },
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Icon(
                        if (colorMode == ColorPickerMode.BACKGROUND) {
                            Icons.Default.FormatColorFill
                        } else {
                            Icons.Default.FormatColorText
                        },
                        contentDescription = "Цвет",
                        tint = if (showColorPicker) primary else muted,
                    )
                    val swatch = when {
                        colorMode == ColorPickerMode.BACKGROUND && currentBgColorArgb != 0 ->
                            Color(currentBgColorArgb)
                        currentColorArgb != 0 -> Color(currentColorArgb)
                        else -> null
                    }
                    if (swatch != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
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
