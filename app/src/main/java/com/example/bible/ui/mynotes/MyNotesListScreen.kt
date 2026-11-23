package com.example.bible.ui.mynotes

import android.app.Application
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bible.data.mynotes.CheckItem
import com.example.bible.data.mynotes.MyNote
import com.example.bible.data.mynotes.MyNoteKind
import com.example.bible.data.mynotes.MyNoteSort
import com.example.bible.data.mynotes.MyNotesDocument
import com.example.bible.data.mynotes.NotePalette
import com.example.bible.data.mynotes.RecipeLine
import com.example.bible.data.mynotes.SketchPoint
import com.example.bible.data.mynotes.SketchStroke
import com.example.bible.data.mynotes.TaskPriority
import com.example.bible.ui.theme.BibleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyNotesScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: MyNotesViewModel = viewModel(factory = MyNotesViewModel.factory(app))
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MyNote?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun offerUndo(note: MyNote) {
        scope.launch {
            val result = snackbar.showSnackbar(
                message = "Заметка удалена",
                actionLabel = "Вернуть",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) vm.restore(note)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val pending = MyNotesUndo.consume() ?: return@repeatOnLifecycle
            offerUndo(pending)
        }
    }

    MyNotesBrowser(
        loaded = ui.loaded,
        document = ui.document,
        query = query,
        onQuery = { query = it },
        onBack = onBack,
        onOpenNote = onOpenNote,
        onTogglePin = { note -> vm.setPinned(note.id, !note.pinned) },
        onToggleDone = { note -> vm.toggleDone(note.id) },
        onToggleCheck = { note, itemId -> vm.toggleCheck(note.id, itemId) },
        onDelete = { pendingDelete = it },
        onSort = vm::setSort,
        onFilter = vm::setFilter,
        onGrid = vm::setGrid,
        onReorder = vm::reorderNotes,
        onCreateClick = { showCreate = true },
        snackbarHost = { SnackbarHost(snackbar) },
    )

    if (showCreate) {
        ModalBottomSheet(onDismissRequest = { showCreate = false }) {
            Text(
                "Новая заметка",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                "Выберите, что хотите записать",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            MyNoteKind.entries.forEach { kind ->
                ListItem(
                    headlineContent = { Text(kind.titleRu()) },
                    supportingContent = { Text(kind.hintRu()) },
                    leadingContent = {
                        Icon(kindIcon(kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showCreate = false
                        vm.create(kind, onCreate)
                    },
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить заметку?") },
            text = { Text("«${note.displayTitle()}» пропадёт из блокнота. Её можно сразу вернуть.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            vm.remove(note.id)
                            offerUndo(note)
                        }
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyNotesBrowser(
    loaded: Boolean,
    document: MyNotesDocument,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onTogglePin: (MyNote) -> Unit,
    onToggleDone: (MyNote) -> Unit,
    onToggleCheck: (MyNote, String) -> Unit,
    onDelete: (MyNote) -> Unit,
    onSort: (MyNoteSort) -> Unit,
    onFilter: (MyNoteKind?) -> Unit,
    onGrid: (Boolean) -> Unit,
    onReorder: suspend (List<String>) -> Unit,
    onCreateClick: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    val visible = document.visible(query)
    val visibleIds = visible.map { it.id }
    val byId = visible.associateBy { it.id }
    var dragIds by remember { mutableStateOf<List<String>?>(null) }
    var holdIds by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val rows = if (dragIds == null) visible else dragIds!!.mapNotNull { byId[it] }

    LaunchedEffect(visibleIds, holdIds) {
        val hold = holdIds ?: return@LaunchedEffect
        if (visibleIds == hold) {
            dragIds = null
            holdIds = null
        }
    }
    LaunchedEffect(holdIds) {
        if (holdIds == null) return@LaunchedEffect
        delay(900)
        dragIds = null
        holdIds = null
    }

    fun moveRows(fromKey: Any, toKey: Any) {
        val ids = (dragIds ?: visibleIds).toMutableList()
        val fromIndex = ids.indexOf(fromKey)
        val toIndex = ids.indexOf(toKey)
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return
        val fromNote = byId[ids[fromIndex]] ?: return
        val toNote = byId[ids[toIndex]] ?: return
        if (fromNote.pinned != toNote.pinned) return
        ids.add(toIndex, ids.removeAt(fromIndex))
        dragIds = ids
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun finishDrag() {
        val ids = dragIds ?: return
        if (ids == visibleIds) {
            dragIds = null
            return
        }
        holdIds = ids
        scope.launch { onReorder(ids) }
    }

    var sortMenu by remember { mutableStateOf(false) }
    Scaffold(
        snackbarHost = snackbarHost,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Filled.Add, contentDescription = "Новая заметка")
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Мои заметки")
                        Text(
                            "Списки, задачи, рецепты, скетчи",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Поиск по заметкам") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить")
                        }
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = document.filterKind == null,
                    onClick = { onFilter(null) },
                    label = { Text("Все") },
                )
                MyNoteKind.entries.forEach { kind ->
                    FilterChip(
                        selected = document.filterKind == kind,
                        onClick = { onFilter(if (document.filterKind == kind) null else kind) },
                        label = { Text(kind.titleRu()) },
                        leadingIcon = {
                            Icon(kindIcon(kind), contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (!loaded) "Загрузка…" else notesCountRu(rows.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Сортировка: ${document.sort.titleRu()}")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        MyNoteSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.titleRu()) },
                                onClick = {
                                    sortMenu = false
                                    onSort(sort)
                                },
                                trailingIcon = if (document.sort == sort) {
                                    { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { onGrid(!document.grid) }) {
                    Icon(
                        if (document.grid) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                        contentDescription = if (document.grid) "Показать списком" else "Показать сеткой",
                    )
                }
            }
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                rows.isEmpty() -> NotesEmpty(
                    filtered = query.isNotBlank() || document.filterKind != null,
                    modifier = Modifier.fillMaxSize(),
                )
                document.grid -> NotesGrid(
                    rows = rows,
                    onOpenNote = onOpenNote,
                    onTogglePin = onTogglePin,
                    onToggleDone = onToggleDone,
                    onToggleCheck = onToggleCheck,
                    onDelete = onDelete,
                    onMove = ::moveRows,
                    onDragEnd = ::finishDrag,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> NotesColumn(
                    rows = rows,
                    onOpenNote = onOpenNote,
                    onTogglePin = onTogglePin,
                    onToggleDone = onToggleDone,
                    onToggleCheck = onToggleCheck,
                    onDelete = onDelete,
                    onMove = ::moveRows,
                    onDragEnd = ::finishDrag,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun NotesColumn(
    rows: List<MyNote>,
    onOpenNote: (String) -> Unit,
    onTogglePin: (MyNote) -> Unit,
    onToggleDone: (MyNote) -> Unit,
    onToggleCheck: (MyNote, String) -> Unit,
    onDelete: (MyNote) -> Unit,
    onMove: (Any, Any) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.key, to.key)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
    ) {
        items(rows, key = { it.id }) { note ->
            ReorderableItem(reorderable, key = note.id, enabled = rows.size > 1) { dragging ->
                NoteCard(
                    note = note,
                    compact = false,
                    dragging = dragging,
                    handleModifier = Modifier.draggableHandle(
                        enabled = rows.size > 1,
                        onDragStopped = { onDragEnd() },
                    ),
                    onOpen = { onOpenNote(note.id) },
                    onTogglePin = { onTogglePin(note) },
                    onToggleDone = { onToggleDone(note) },
                    onToggleCheck = { onToggleCheck(note, it) },
                    onDelete = { onDelete(note) },
                )
            }
        }
    }
}

@Composable
private fun NotesGrid(
    rows: List<MyNote>,
    onOpenNote: (String) -> Unit,
    onTogglePin: (MyNote) -> Unit,
    onToggleDone: (MyNote) -> Unit,
    onToggleCheck: (MyNote, String) -> Unit,
    onDelete: (MyNote) -> Unit,
    onMove: (Any, Any) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val reorderable = rememberReorderableLazyGridState(gridState) { from, to ->
        onMove(from.key, to.key)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
    ) {
        gridItems(rows, key = { it.id }) { note ->
            ReorderableItem(reorderable, key = note.id, enabled = rows.size > 1) { dragging ->
                NoteCard(
                    note = note,
                    compact = true,
                    dragging = dragging,
                    handleModifier = Modifier.draggableHandle(
                        enabled = rows.size > 1,
                        onDragStopped = { onDragEnd() },
                    ),
                    onOpen = { onOpenNote(note.id) },
                    onTogglePin = { onTogglePin(note) },
                    onToggleDone = { onToggleDone(note) },
                    onToggleCheck = { onToggleCheck(note, it) },
                    onDelete = { onDelete(note) },
                )
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: MyNote,
    compact: Boolean,
    dragging: Boolean,
    handleModifier: Modifier,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleDone: () -> Unit,
    onToggleCheck: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = note.palette.accent(dark)
    val elevation by animateDpAsState(if (dragging) 10.dp else 1.dp, label = "note-card")
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = note.palette.container(dark)),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .padding(start = 10.dp)
                    .width(5.dp)
                    .height(if (compact) 72.dp else 96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent),
            )
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(kindIcon(note.kind), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Text(
                        note.kind.titleRu(),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.padding(start = 6.dp).weight(1f),
                    )
                    if (note.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    note.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (note.kind == MyNoteKind.TASK && note.done) TextDecoration.LineThrough else null,
                )
                if (!compact || note.kind == MyNoteKind.SKETCH) {
                    NotePreview(note, accent, compact, onToggleCheck, onToggleDone)
                }
                Text(
                    noteMeta(note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = if (note.pinned) "Открепить" else "Закрепить",
                            tint = if (note.pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {}, modifier = handleModifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Переместить",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotePreview(
    note: MyNote,
    accent: androidx.compose.ui.graphics.Color,
    compact: Boolean,
    onToggleCheck: (String) -> Unit,
    onToggleDone: () -> Unit,
) {
    when (note.kind) {
        MyNoteKind.TEXT -> if (note.body.isNotBlank()) {
            Text(
                note.body,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (compact) 2 else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        MyNoteKind.CHECKLIST -> {
            val shown = note.checks.take(if (compact) 2 else 4)
            shown.forEach { item ->
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (item.done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = if (item.done) "Снять отметку" else "Отметить",
                        tint = accent,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onToggleCheck(item.id) },
                    )
                    Text(
                        item.text.ifBlank { "Пустой пункт" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            val rest = note.checks.size - shown.size
            if (rest > 0) {
                Text("ещё $rest", style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }
        MyNoteKind.TASK -> {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (note.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (note.done) "Вернуть в работу" else "Отметить выполненной",
                    tint = accent,
                    modifier = Modifier.size(22.dp).clickable { onToggleDone() },
                )
                Text(
                    buildString {
                        append(note.priority.titleRu())
                        note.dueDate?.let { append(" · "); append(formatDueRu(it)) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        MyNoteKind.RECIPE -> {
            val bits = listOfNotNull(
                note.servings.takeIf { it.isNotBlank() }?.let { "$it порц." },
                note.cookTime.takeIf { it.isNotBlank() },
            )
            if (bits.isNotEmpty()) {
                Text(bits.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = accent)
            }
            note.ingredients.take(if (compact) 2 else 3).forEach { line ->
                Text(
                    "• ${line.text}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MyNoteKind.SKETCH -> SketchSurface(
            strokes = note.strokes,
            enabled = false,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(if (compact) 72.dp else 110.dp),
        )
    }
}

private fun noteMeta(note: MyNote): String {
    val whenText = formatDayRu(note.updatedAt).let { if (it.isBlank()) "" else "изменено $it" }
    val extra = when (note.kind) {
        MyNoteKind.CHECKLIST -> {
            val done = note.checks.count { it.done }
            if (note.checks.isEmpty()) "" else "$done из ${note.checks.size}"
        }
        MyNoteKind.TASK -> if (note.done) "готово" else ""
        MyNoteKind.RECIPE -> if (note.steps.isEmpty()) "" else "${note.steps.size} шаг."
        else -> ""
    }
    return listOf(extra, whenText).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun NotesEmpty(filtered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            if (filtered) "Ничего не найдено" else "Пока пусто",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            if (filtered) {
                "Смените фильтр или формулировку поиска."
            } else {
                "Соберите списки дел, задачи, рецепты и скетчи в одном блокноте."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

internal fun previewNotesDocument(): MyNotesDocument = MyNotesDocument(
    notes = listOf(
        MyNote(
            id = "list",
            kind = MyNoteKind.CHECKLIST,
            title = "На неделю",
            palette = NotePalette.SAGE,
            pinned = true,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000,
            order = 0,
            checks = listOf(
                CheckItem("a", "Молоко", done = true),
                CheckItem("b", "Хлеб"),
                CheckItem("c", "Яблоки"),
            ),
        ),
        MyNote(
            id = "task",
            kind = MyNoteKind.TASK,
            title = "Позвонить маме",
            body = "Спросить про воскресенье",
            palette = NotePalette.PEACH,
            createdAt = 1_700_000_200_000,
            updatedAt = 1_700_000_300_000,
            order = 1,
            dueDate = "2026-11-23",
            priority = TaskPriority.HIGH,
        ),
        MyNote(
            id = "recipe",
            kind = MyNoteKind.RECIPE,
            title = "Оладьи",
            palette = NotePalette.SAND,
            createdAt = 1_700_000_400_000,
            updatedAt = 1_700_000_500_000,
            order = 2,
            servings = "4",
            cookTime = "25 мин",
            ingredients = listOf(RecipeLine("i1", "Кефир"), RecipeLine("i2", "Мука"), RecipeLine("i3", "Яйцо")),
            steps = listOf(RecipeLine("s1", "Смешать"), RecipeLine("s2", "Жарить")),
        ),
        MyNote(
            id = "sketch",
            kind = MyNoteKind.SKETCH,
            title = "Горы",
            palette = NotePalette.SKY,
            createdAt = 1_700_000_600_000,
            updatedAt = 1_700_000_700_000,
            order = 3,
            strokes = listOf(
                SketchStroke(
                    argb = 0xFF1D4ED8.toInt(),
                    width = 5.5f,
                    points = listOf(
                        SketchPoint(0.15f, 0.72f),
                        SketchPoint(0.38f, 0.28f),
                        SketchPoint(0.58f, 0.62f),
                        SketchPoint(0.82f, 0.22f),
                    ),
                ),
            ),
        ),
    ),
)

@Preview(name = "Светлая", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun MyNotesPreviewLight() {
    BibleTheme(darkTheme = false) {
        MyNotesBrowser(
            loaded = true,
            document = previewNotesDocument(),
            query = "",
            onQuery = {},
            onBack = {},
            onOpenNote = {},
            onTogglePin = {},
            onToggleDone = {},
            onToggleCheck = { _, _ -> },
            onDelete = {},
            onSort = {},
            onFilter = {},
            onGrid = {},
            onReorder = {},
            onCreateClick = {},
        )
    }
}

@Preview(name = "Тёмная", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun MyNotesPreviewDark() {
    BibleTheme(darkTheme = true) {
        MyNotesBrowser(
            loaded = true,
            document = previewNotesDocument().copy(grid = true),
            query = "",
            onQuery = {},
            onBack = {},
            onOpenNote = {},
            onTogglePin = {},
            onToggleDone = {},
            onToggleCheck = { _, _ -> },
            onDelete = {},
            onSort = {},
            onFilter = {},
            onGrid = {},
            onReorder = {},
            onCreateClick = {},
        )
    }
}
