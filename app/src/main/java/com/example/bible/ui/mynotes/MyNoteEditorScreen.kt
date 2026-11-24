@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.example.bible.ui.mynotes

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bible.data.mynotes.CheckItem
import com.example.bible.data.mynotes.MyNote
import com.example.bible.data.mynotes.MyNoteKind
import com.example.bible.data.mynotes.NotePalette
import com.example.bible.data.mynotes.RecipeLine
import com.example.bible.data.mynotes.TaskPriority
import com.example.bible.data.mynotes.hasRecipeText
import com.example.bible.ui.theme.BibleTheme
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MyNoteEditorScreen(
    noteId: String,
    fresh: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: MyNotesViewModel = viewModel(factory = MyNotesViewModel.factory(app))
    var note by remember { mutableStateOf<MyNote?>(null) }
    var missing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var skipSave by remember { mutableStateOf(false) }
    var recipeViewing by remember(noteId) { mutableStateOf(false) }
    var checkedIngredients by remember(noteId) { mutableStateOf(emptySet<String>()) }
    val checks = remember { mutableStateListOf<CheckItem>() }
    val ingredients = remember { mutableStateListOf<RecipeLine>() }
    val steps = remember { mutableStateListOf<RecipeLine>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(noteId) {
        val loaded = vm.load(noteId)
        if (loaded == null) {
            missing = true
        } else {
            note = loaded
            checks.clear()
            checks.addAll(loaded.checks)
            ingredients.clear()
            ingredients.addAll(loaded.ingredients)
            steps.clear()
            steps.addAll(loaded.steps)
            recipeViewing = loaded.hasRecipeText()
            ready = true
        }
    }

    fun current(): MyNote? {
        val base = note ?: return null
        return when (base.kind) {
            MyNoteKind.CHECKLIST -> base.copy(checks = checks.toList())
            MyNoteKind.RECIPE -> base.copy(ingredients = ingredients.toList(), steps = steps.toList())
            else -> base
        }
    }

    LaunchedEffect(note, checks.toList(), ingredients.toList(), steps.toList(), dirty, ready) {
        if (!ready || !dirty || skipSave) return@LaunchedEffect
        delay(320)
        val snapshot = current() ?: return@LaunchedEffect
        vm.save(snapshot)
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            val snapshot = current()
            if (!skipSave && snapshot != null && fresh && snapshot.isBlankContent()) {
                vm.remove(snapshot.id)
            } else if (!skipSave && snapshot != null && dirty) {
                vm.save(snapshot)
            }
            onBack()
        }
    }

    BackHandler(enabled = !closing) { close() }

    val currentNote = note
    when {
        missing -> MissingNote(onBack = onBack)
        currentNote == null -> Scaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> MyNoteEditorContent(
            note = currentNote,
            checks = checks,
            ingredients = ingredients,
            steps = steps,
            onNote = { note = it; dirty = true },
            onDirty = { dirty = true },
            recipeViewing = recipeViewing,
            onToggleRecipeView = { recipeViewing = !recipeViewing },
            checkedIngredientIds = checkedIngredients,
            onToggleIngredient = { id ->
                checkedIngredients = if (id in checkedIngredients) {
                    checkedIngredients - id
                } else {
                    checkedIngredients + id
                }
            },
            onClose = ::close,
            onPin = {
                val next = !currentNote.pinned
                note = currentNote.copy(pinned = next)
                vm.setPinned(currentNote.id, next)
            },
            onDelete = {
                val snapshot = current() ?: return@MyNoteEditorContent
                skipSave = true
                closing = true
                MyNotesUndo.offer(snapshot)
                scope.launch {
                    vm.remove(snapshot.id)
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun MyNoteEditorContent(
    note: MyNote,
    checks: SnapshotStateList<CheckItem>,
    ingredients: SnapshotStateList<RecipeLine>,
    steps: SnapshotStateList<RecipeLine>,
    onNote: (MyNote) -> Unit,
    onDirty: () -> Unit,
    recipeViewing: Boolean = false,
    onToggleRecipeView: () -> Unit = {},
    checkedIngredientIds: Set<String> = emptySet(),
    onToggleIngredient: (String) -> Unit = {},
    onClose: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note.kind.titleRu()) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (note.kind == MyNoteKind.RECIPE) {
                        IconButton(onClick = onToggleRecipeView) {
                            Icon(
                                if (recipeViewing) Icons.Filled.Edit else Icons.Filled.Visibility,
                                contentDescription = if (recipeViewing) "Редактировать" else "Просмотр",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = onPin) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = if (note.pinned) "Открепить" else "Закрепить",
                            tint = if (note.pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        when (note.kind) {
            MyNoteKind.CHECKLIST -> ChecklistEditor(
                note = note,
                checks = checks,
                onNote = onNote,
                onDirty = onDirty,
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            )
            MyNoteKind.RECIPE -> if (recipeViewing) {
                RecipeReader(
                    note = note,
                    ingredients = ingredients,
                    steps = steps,
                    checkedIngredientIds = checkedIngredientIds,
                    onToggleIngredient = onToggleIngredient,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                RecipeEditor(
                    note = note,
                    ingredients = ingredients,
                    steps = steps,
                    onNote = onNote,
                    onDirty = onDirty,
                    modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                )
            }
            MyNoteKind.SKETCH -> SketchEditor(
                note = note,
                onNote = onNote,
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            )
            else -> SimpleEditor(
                note = note,
                onNote = onNote,
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить заметку?") },
            text = { Text("«${note.displayTitle()}» можно вернуть на экране списка.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SimpleEditor(
    note: MyNote,
    onNote: (MyNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleField(note, onNote)
        PaletteRow(note.palette) { onNote(note.copy(palette = it)) }
        if (note.kind == MyNoteKind.TASK) {
            Text("Приоритет", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TaskPriority.entries.forEachIndexed { index, priority ->
                    SegmentedButton(
                        selected = note.priority == priority,
                        onClick = { onNote(note.copy(priority = priority)) },
                        shape = SegmentedButtonDefaults.itemShape(index, TaskPriority.entries.size),
                    ) { Text(priority.titleRu()) }
                }
            }
            DueRow(note, onNote)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Выполнена", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = note.done, onCheckedChange = { onNote(note.copy(done = it)) })
            }
        }
        OutlinedTextField(
            value = note.body,
            onValueChange = { onNote(note.copy(body = it)) },
            modifier = Modifier.fillMaxWidth().revealWhileEditing(),
            label = { Text(if (note.kind == MyNoteKind.TASK) "Заметка к задаче" else "Текст") },
            minLines = if (note.kind == MyNoteKind.TEXT) 8 else 4,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChecklistEditor(
    note: MyNote,
    checks: SnapshotStateList<CheckItem>,
    onNote: (MyNote) -> Unit,
    onDirty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = checks.indexOfFirst { it.id == from.key }
        val toIndex = checks.indexOfFirst { it.id == to.key }
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return@rememberReorderableLazyListState
        checks.add(toIndex, checks.removeAt(fromIndex))
        onDirty()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") { TitleField(note, onNote) }
        item(key = "colors") { PaletteRow(note.palette) { onNote(note.copy(palette = it)) } }
        items(checks, key = { it.id }) { item ->
            ReorderableItem(reorderable, key = item.id, enabled = checks.size > 1) { dragging ->
                CheckRow(
                    item = item,
                    dragging = dragging,
                    handleModifier = Modifier.draggableHandle(enabled = checks.size > 1),
                    onText = { text ->
                        val index = checks.indexOfFirst { it.id == item.id }
                        if (index >= 0) {
                            checks[index] = checks[index].copy(text = text)
                            onDirty()
                        }
                    },
                    onToggle = {
                        val index = checks.indexOfFirst { it.id == item.id }
                        if (index >= 0) {
                            checks[index] = checks[index].copy(done = !checks[index].done)
                            onDirty()
                        }
                    },
                    onRemove = {
                        checks.removeAll { it.id == item.id }
                        onDirty()
                    },
                )
            }
        }
        item(key = "add") {
            TextButton(
                onClick = {
                    checks.add(CheckItem(id = UUID.randomUUID().toString()))
                    onDirty()
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Пункт", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun RecipeEditor(
    note: MyNote,
    ingredients: SnapshotStateList<RecipeLine>,
    steps: SnapshotStateList<RecipeLine>,
    onNote: (MyNote) -> Unit,
    onDirty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        if (moveRecipe(ingredients, from.key, to.key) || moveRecipe(steps, from.key, to.key)) {
            onDirty()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") { TitleField(note, onNote) }
        item(key = "colors") { PaletteRow(note.palette) { onNote(note.copy(palette = it)) } }
        item(key = "meta") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = note.servings,
                    onValueChange = { onNote(note.copy(servings = it)) },
                    modifier = Modifier.weight(1f).revealWhileEditing(),
                    label = { Text("Порции") },
                )
                OutlinedTextField(
                    value = note.cookTime,
                    onValueChange = { onNote(note.copy(cookTime = it)) },
                    modifier = Modifier.weight(1f).revealWhileEditing(),
                    label = { Text("Время") },
                )
            }
        }
        item(key = "ing-title") { Text("Ингредиенты", style = MaterialTheme.typography.titleSmall) }
        items(ingredients, key = { it.id }) { line ->
            ReorderableItem(reorderable, key = line.id, enabled = ingredients.size > 1) { dragging ->
                RecipeRow(
                    text = line.text,
                    prefix = "•",
                    dragging = dragging,
                    handleModifier = Modifier.draggableHandle(enabled = ingredients.size > 1),
                    onText = { text -> replaceLine(ingredients, line.id, text); onDirty() },
                    onRemove = { ingredients.removeAll { it.id == line.id }; onDirty() },
                )
            }
        }
        item(key = "ing-add") {
            TextButton(onClick = {
                ingredients.add(RecipeLine(UUID.randomUUID().toString()))
                onDirty()
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Ингредиент", modifier = Modifier.padding(start = 6.dp))
            }
        }
        item(key = "step-title") { Text("Шаги", style = MaterialTheme.typography.titleSmall) }
        items(steps, key = { it.id }) { line ->
            val number = steps.indexOfFirst { it.id == line.id } + 1
            ReorderableItem(reorderable, key = line.id, enabled = steps.size > 1) { dragging ->
                RecipeRow(
                    text = line.text,
                    prefix = "$number.",
                    dragging = dragging,
                    handleModifier = Modifier.draggableHandle(enabled = steps.size > 1),
                    onText = { text -> replaceLine(steps, line.id, text); onDirty() },
                    onRemove = { steps.removeAll { it.id == line.id }; onDirty() },
                )
            }
        }
        item(key = "step-add") {
            TextButton(onClick = {
                steps.add(RecipeLine(UUID.randomUUID().toString()))
                onDirty()
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Шаг", modifier = Modifier.padding(start = 6.dp))
            }
        }
        item(key = "notes") {
            OutlinedTextField(
                value = note.body,
                onValueChange = { onNote(note.copy(body = it)) },
                modifier = Modifier.fillMaxWidth().revealWhileEditing(),
                label = { Text("Заметки") },
                placeholder = { Text("Подача, замены, совет") },
                minLines = 3,
            )
        }
    }
}

@Composable
private fun SketchEditor(
    note: MyNote,
    onNote: (MyNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ink by remember { mutableStateOf(SketchInks.first()) }
    var width by remember { mutableStateOf(SketchWidths[1].first) }
    var eraser by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TitleField(note, onNote)
        PaletteRow(note.palette) { onNote(note.copy(palette = it)) }
        SketchSurface(
            strokes = note.strokes,
            enabled = true,
            inkArgb = ink,
            widthDp = width,
            eraser = eraser,
            onCommit = { stroke -> onNote(note.copy(strokes = note.strokes + stroke)) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchInks.forEach { color ->
                val selected = !eraser && ink == color
                Box(
                    Modifier
                        .size(if (selected) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(inkColor(color))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { eraser = false; ink = color },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SketchWidths.forEach { (size, label) ->
                FilterChip(
                    selected = !eraser && width == size,
                    onClick = { eraser = false; width = size },
                    label = { Text(label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = eraser,
                onClick = { eraser = !eraser },
                label = { Text(if (eraser) "Ластик" else "Карандаш") },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { if (note.strokes.isNotEmpty()) onNote(note.copy(strokes = note.strokes.dropLast(1))) }, enabled = note.strokes.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Отменить штрих")
            }
            IconButton(onClick = { confirmClear = true }, enabled = note.strokes.isNotEmpty()) {
                Icon(Icons.Filled.CleaningServices, contentDescription = "Очистить холст")
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить рисунок?") },
            text = { Text("Все штрихи этого скетча будут стёрты.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onNote(note.copy(strokes = emptyList())) }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun TitleField(note: MyNote, onNote: (MyNote) -> Unit) {
    OutlinedTextField(
        value = note.title,
        onValueChange = { onNote(note.copy(title = it)) },
        modifier = Modifier.fillMaxWidth().revealWhileEditing(),
        label = { Text("Название") },
        placeholder = { Text(note.kind.hintRu()) },
    )
}

@Composable
private fun PaletteRow(selected: NotePalette, onSelect: (NotePalette) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NotePalette.entries.forEach { palette ->
            val picked = palette == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.container(dark))
                    .border(if (picked) 3.dp else 1.dp, palette.accent(dark), CircleShape)
                    .clickable { onSelect(palette) },
                contentAlignment = Alignment.Center,
            ) {
                if (picked) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(palette.accent(dark)))
                }
            }
        }
    }
}

@Composable
private fun DueRow(note: MyNote, onNote: (MyNote) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = {
            val initial = note.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            android.app.DatePickerDialog(
                context,
                { _, year, month, day ->
                    val iso = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    onNote(note.copy(dueDate = iso))
                },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth,
            ).show()
        }) {
            Text(note.dueDate?.let { "Срок: ${formatDueRu(it)}" } ?: "Добавить срок")
        }
        if (note.dueDate != null) {
            IconButton(onClick = { onNote(note.copy(dueDate = null)) }) {
                Icon(Icons.Filled.Close, contentDescription = "Убрать срок")
            }
        }
    }
}

@Composable
private fun CheckRow(
    item: CheckItem,
    dragging: Boolean,
    handleModifier: Modifier,
    onText: (String) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp, label = "check-drag")
    Surface(shadowElevation = elevation, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 4.dp)) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (item.done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = if (item.done) "Снять отметку" else "Отметить",
                )
            }
            OutlinedTextField(
                value = item.text,
                onValueChange = onText,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp).revealWhileEditing(),
                placeholder = { Text("Пункт списка") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                ),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Удалить пункт")
            }
            IconButton(onClick = {}, modifier = handleModifier) {
                Icon(Icons.Filled.DragHandle, contentDescription = "Переместить пункт")
            }
        }
    }
}

@Composable
private fun RecipeRow(
    text: String,
    prefix: String,
    dragging: Boolean,
    handleModifier: Modifier,
    onText: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp, label = "recipe-drag")
    Surface(shadowElevation = elevation, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 8.dp)) {
            Text(
                prefix,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp, end = 6.dp),
            )
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp).revealWhileEditing(),
                placeholder = { Text("Текст") },
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Удалить строку")
            }
            IconButton(onClick = {}, modifier = handleModifier) {
                Icon(Icons.Filled.DragHandle, contentDescription = "Переместить строку")
            }
        }
    }
}

@Composable
private fun Modifier.revealWhileEditing(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (!focused || imeBottom == 0) return@LaunchedEffect
        delay(80)
        requester.bringIntoView()
    }
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state ->
            if (focused != state.isFocused) focused = state.isFocused
        }
}

private fun moveRecipe(lines: SnapshotStateList<RecipeLine>, fromKey: Any, toKey: Any): Boolean {
    val fromIndex = lines.indexOfFirst { it.id == fromKey }
    val toIndex = lines.indexOfFirst { it.id == toKey }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false
    lines.add(toIndex, lines.removeAt(fromIndex))
    return true
}

private fun replaceLine(lines: SnapshotStateList<RecipeLine>, id: String, text: String) {
    val index = lines.indexOfFirst { it.id == id }
    if (index >= 0) lines[index] = lines[index].copy(text = text)
}

@Composable
private fun MissingNote(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заметка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Заметка не найдена")
        }
    }
}

@Preview(name = "Рецепт", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun RecipeEditorPreview() {
    val recipe = previewNotesDocument().notes.first { it.kind == MyNoteKind.RECIPE }
    val ingredients = remember { mutableStateListOf<RecipeLine>().also { it.addAll(recipe.ingredients) } }
    val steps = remember { mutableStateListOf<RecipeLine>().also { it.addAll(recipe.steps) } }
    BibleTheme(darkTheme = false) {
        MyNoteEditorContent(
            note = recipe,
            checks = remember { mutableStateListOf<CheckItem>() },
            ingredients = ingredients,
            steps = steps,
            onNote = {},
            onDirty = {},
            onClose = {},
            onPin = {},
            onDelete = {},
        )
    }
}
