package com.example.bible.ui

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.example.bible.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleImageLibrary
import com.example.bible.data.BibleLibrary
import com.example.bible.data.BibleUserImage
import com.example.bible.data.TimemarkAttachment
import com.example.bible.data.TimemarkCue
import com.example.bible.data.TimemarkProject
import com.example.bible.data.TimemarkStore
import com.example.bible.data.TranslationId
import com.example.bible.data.localAudioFile
import com.example.bible.data.narratorForTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "TimemarkEditor"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimemarkEditorScreen(
    library: BibleLibrary,
    translation: TranslationId,
    narratorId: String,
    onBack: () -> Unit,
    mediaLibraryImages: List<BibleUserImage> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf<String?>(null) }
    var bookId by remember { mutableStateOf(BibleCanon.allBooks.first().id) }
    var chapterNum by remember { mutableIntStateOf(1) }
    var translationPick by remember { mutableStateOf(translation) }
    var audioPath by remember { mutableStateOf<String?>(null) }

    val cues = remember { mutableStateListOf<TimemarkCue>() }
    var cuesVersion by remember { mutableIntStateOf(0) }
    fun refreshCues() {
        cuesVersion++
    }

    var selectedVerse by remember { mutableIntStateOf(1) }
    var noteDraft by remember { mutableStateOf("") }
    var pendingImagePath by remember { mutableStateOf<String?>(null) }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    val canon = BibleCanon.byId(bookId)
    val book = library.getBook(translationPick, bookId)
    val verses = book?.chapters?.find { it.number == chapterNum }?.verses.orEmpty()

    LaunchedEffect(bookId, chapterNum, verses) {
        if (verses.isNotEmpty()) {
            selectedVerse = verses.first().number
        }
    }

    val audioPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "timemark_audio").apply { mkdirs() }
                val dest = File(dir, "tm_${System.currentTimeMillis()}.mp3")
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    dest.outputStream().use { out -> inp.copyTo(out) }
                }
                withContext(Dispatchers.Main) {
                    audioPath = dest.absolutePath
                    Toast.makeText(context, context.getString(R.string.timemark_toast_audio_selected), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "audio copy", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.timemark_toast_audio_copy_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val imagePickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "timemark_attachments").apply { mkdirs() }
                val ext = context.contentResolver.getType(uri)
                    ?.substringAfterLast('/')?.take(4) ?: "jpg"
                val dest = File(dir, "img_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    dest.outputStream().use { out -> inp.copyTo(out) }
                }
                withContext(Dispatchers.Main) {
                    pendingImagePath = dest.absolutePath
                    Toast.makeText(context, context.getString(R.string.timemark_toast_image_for_mark), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "image copy", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.timemark_toast_image_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var showImageSourceSheet by remember { mutableStateOf(false) }
    var showMediaLibPicker by remember { mutableStateOf(false) }
    val bibleImageLib = remember { BibleImageLibrary(context) }

    if (showImageSourceSheet) {
        AttachFileSourceSheet(
            onDismiss = { showImageSourceSheet = false },
            onPickDeviceFile = { imagePickLauncher.launch("image/*") },
            onPickFromMediaLibrary = { showMediaLibPicker = true },
        )
    }
    if (showMediaLibPicker) {
        MediaLibraryImagePickerSheet(
            images = mediaLibraryImages,
            onDismiss = { showMediaLibPicker = false },
            onSelect = { img ->
                scope.launch(Dispatchers.IO) {
                    val r = bibleImageLib.copyToTimemarkPath(img)
                    withContext(Dispatchers.Main) {
                        r.onSuccess { path ->
                            pendingImagePath = path
                            Toast.makeText(
                                context,
                                context.getString(R.string.timemark_toast_image_for_mark),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }.onFailure { e ->
                            Toast.makeText(
                                context,
                                e.message ?: context.getString(R.string.timemark_toast_image_save_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            },
        )
    }

    DisposableEffect(audioPath) {
        val path = audioPath
        if (path.isNullOrBlank()) {
            player?.release()
            player = null
            positionMs = 0L
            durationMs = 0L
            isPlaying = false
            onDispose { }
        } else {
            val mp = MediaPlayer()
            var prepared = false
            try {
                mp.setDataSource(path)
                mp.prepare()
                mp.setOnCompletionListener {
                    isPlaying = false
                    positionMs = mp.duration.toLong()
                }
                player = mp
                durationMs = mp.duration.toLong()
                positionMs = 0L
                prepared = true
            } catch (e: Exception) {
                Log.e(TAG, "prepare", e)
                mp.release()
                Toast.makeText(context, context.getString(R.string.timemark_toast_audio_open_failed), Toast.LENGTH_SHORT).show()
            }
            onDispose {
                if (prepared) {
                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }
                    player = null
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(250)
            player?.let { p ->
                if (p.isPlaying) {
                    positionMs = p.currentPosition.toLong()
                }
            }
        }
    }

    var showLoadDialog by remember { mutableStateOf(false) }
    var loadDialogRefresh by remember { mutableIntStateOf(0) }
    var showDeleteChapterDialog by remember { mutableStateOf(false) }
    var showDeleteBookDialog by remember { mutableStateOf(false) }

    fun addMarkCue(verseNum: Int) {
        val path = audioPath
        if (path.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.timemark_toast_pick_audio), Toast.LENGTH_SHORT).show()
            return
        }
        if (verses.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.timemark_toast_no_chapter_text), Toast.LENGTH_SHORT).show()
            return
        }
        val atts = buildList {
            pendingImagePath?.let { p ->
                add(TimemarkAttachment(kind = "image", path = p))
            }
        }
        val note = noteDraft.trim().takeIf { it.isNotEmpty() }
        cues.add(
            TimemarkCue(
                timeMs = positionMs,
                verseStart = verseNum,
                verseEnd = null,
                note = note,
                attachments = atts,
            ),
        )
        pendingImagePath = null
        noteDraft = ""
        selectedVerse = verseNum
        refreshCues()
        Toast.makeText(context, context.getString(R.string.timemark_toast_mark_added), Toast.LENGTH_SHORT).show()
    }

    val loadDialogRows = remember(showLoadDialog, context, loadDialogRefresh) {
        if (!showLoadDialog) emptyList()
        else TimemarkStore.listProjectFiles(context).map { f ->
            val id = f.nameWithoutExtension
            Triple(id, f, TimemarkStore.load(context, id))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.timemark_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { showLoadDialog = true }) {
                        Text(stringResource(R.string.timemark_editor_open))
                    }
                    IconButton(
                        onClick = {
                            val path = audioPath
                            if (path.isNullOrBlank()) {
                                Toast.makeText(context, context.getString(R.string.timemark_toast_pick_audio), Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val newId = projectId ?: UUID.randomUUID().toString()
                            val p = TimemarkProject(
                                id = newId,
                                translationCode = translationPick.code,
                                bookId = bookId,
                                chapter = chapterNum,
                                title = title.ifBlank { context.getString(R.string.timemark_untitled_project) },
                                audioFilePath = path,
                                cues = cues.sortedBy { it.timeMs },
                            )
                            TimemarkStore.save(context, p)
                            projectId = newId
                            Toast.makeText(context, context.getString(R.string.timemark_toast_saved), Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.timemark_editor_save_cd))
                    }
                },
            )
        },
    ) { padding ->
        val formScrollState = rememberScrollState()
        val density = LocalDensity.current
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) {
            val maxH = maxHeight
            val minVersesH = 96.dp
            val reservedBottom = 128.dp
            val maxVersesH = (maxH - reservedBottom).coerceAtLeast(minVersesH)

            var versesPaneHeight by remember { mutableStateOf(0.dp) }
            LaunchedEffect(maxH) {
                versesPaneHeight = if (versesPaneHeight == 0.dp) {
                    (maxH * 0.36f).coerceIn(minVersesH, maxVersesH)
                } else {
                    versesPaneHeight.coerceIn(minVersesH, maxVersesH)
                }
            }

            Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(formScrollState),
            ) {
                Text(
                    stringResource(R.string.timemark_editor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.timemark_project_name)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            var bookMenuOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = bookMenuOpen,
                onExpandedChange = { bookMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = BibleCanon.byId(bookId)?.nameRu ?: bookId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.timemark_book)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookMenuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = bookMenuOpen,
                    onDismissRequest = { bookMenuOpen = false },
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    BibleCanon.allBooks.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.nameRu) },
                            onClick = {
                                bookId = b.id
                                chapterNum = 1
                                bookMenuOpen = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.timemark_chapter_label), style = MaterialTheme.typography.labelMedium)
                val maxCh = canon?.chapters ?: 1
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (1..maxCh).forEach { ch ->
                        FilterChip(
                            selected = chapterNum == ch,
                            onClick = { chapterNum = ch },
                            label = { Text("$ch") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            var transMenuOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = transMenuOpen,
                onExpandedChange = { transMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = translationPick.labelRu,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.timemark_translation)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transMenuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = transMenuOpen,
                    onDismissRequest = { transMenuOpen = false },
                ) {
                    TranslationId.entries.forEach { tid ->
                        DropdownMenuItem(
                            text = { Text(tid.labelRu) },
                            onClick = {
                                translationPick = tid
                                transMenuOpen = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.timemark_audio_section), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val narrator = narratorForTranslation(translationPick, narratorId)
            val downloaded = remember(bookId, chapterNum, narrator.id, narratorId) {
                localAudioFile(context, narrator.id, bookId, chapterNum)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { audioPickLauncher.launch("audio/*") }) {
                    Text(stringResource(R.string.timemark_pick_file))
                }
                if (downloaded.exists() && downloaded.length() > 1024) {
                    TextButton(onClick = { audioPath = downloaded.absolutePath }) {
                        Text(stringResource(R.string.timemark_downloaded_narration))
                    }
                }
            }
            Text(
                audioPath?.let { File(it).name } ?: stringResource(R.string.timemark_audio_not_chosen),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        val p = player ?: return@IconButton
                        if (p.isPlaying) {
                            p.pause()
                            isPlaying = false
                        } else {
                            p.start()
                            isPlaying = true
                        }
                    },
                    enabled = player != null,
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (durationMs > 0) {
                        Slider(
                            value = positionMs.toFloat(),
                            onValueChange = { v ->
                                player?.seekTo(v.toInt())
                                positionMs = v.toLong()
                            },
                            valueRange = 0f..durationMs.toFloat(),
                        )
                    }
                    Text(
                        "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { showDeleteChapterDialog = true }) {
                    Text(stringResource(R.string.timemark_delete_chapter), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { showDeleteBookDialog = true }) {
                    Text(stringResource(R.string.timemark_delete_book), style = MaterialTheme.typography.labelMedium)
                }
            }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.timemark_verses_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (verses.isEmpty()) {
                Text(
                    stringResource(R.string.timemark_no_text_for_book),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .height(versesPaneHeight)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(verses, key = { it.number }) { verse ->
                        val bg = if (selectedVerse == verse.number) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .clickable { addMarkCue(verse.number) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${verse.number}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                verse.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                TimemarkVersesHeightSplitter(
                    onDragDeltaPx = { dragPx ->
                        versesPaneHeight = with(density) {
                            (versesPaneHeight + dragPx.toDp()).coerceIn(minVersesH, maxVersesH)
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.timemark_note_hint)) },
                minLines = 2,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showImageSourceSheet = true }) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.timemark_image_for_mark))
                }
                pendingImagePath?.let {
                    Text(File(it).name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.timemark_marks_hint), style = MaterialTheme.typography.labelSmall)

            val sortedCues = remember(cuesVersion) { cues.sortedBy { it.timeMs } }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(sortedCues.size, key = { i -> "${sortedCues[i].timeMs}-${sortedCues[i].verseStart}-$i" }) { idx ->
                    val cue = sortedCues[idx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${formatMs(cue.timeMs)} — ст. ${cue.verseStart}${cue.note?.let { ": $it" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (cue.attachments.isNotEmpty()) {
                                Text(
                                    cue.attachments.joinToString {
                                        if (it.kind == "image") {
                                            context.getString(R.string.timemark_attachment_image)
                                        } else {
                                            context.getString(R.string.timemark_attachment_text)
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = {
                            cues.remove(cue)
                            refreshCues()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.timemark_delete_cd))
                        }
                    }
                }
            }
            }
        }
        }
    }

    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text(stringResource(R.string.timemark_saved_projects_title)) },
            text = {
                Column {
                    if (loadDialogRows.isEmpty()) {
                        Text(stringResource(R.string.timemark_no_saved_projects))
                    } else {
                        loadDialogRows.forEach { (id, _, preview) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        val loaded = preview ?: TimemarkStore.load(context, id) ?: return@TextButton
                                        projectId = loaded.id
                                        title = loaded.title
                                        bookId = loaded.bookId
                                        chapterNum = loaded.chapter
                                        translationPick = TranslationId.fromCode(loaded.translationCode)
                                        audioPath = loaded.audioFilePath
                                        cues.clear()
                                        cues.addAll(loaded.cues)
                                        refreshCues()
                                        showLoadDialog = false
                                        Toast.makeText(context, context.getString(R.string.timemark_toast_loaded), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    val line = preview?.let { p ->
                                        context.getString(
                                            R.string.timemark_saved_project_line,
                                            p.title,
                                            BibleCanon.byId(p.bookId)?.nameRu ?: p.bookId,
                                            p.chapter,
                                        )
                                    } ?: id
                                    Text(line, maxLines = 2)
                                }
                                IconButton(
                                    onClick = {
                                        TimemarkStore.delete(context, id)
                                        if (projectId == id) {
                                            projectId = null
                                            cues.clear()
                                            refreshCues()
                                        }
                                        loadDialogRefresh++
                                        Toast.makeText(context, context.getString(R.string.timemark_toast_project_deleted), Toast.LENGTH_SHORT).show()
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.timemark_delete_saved_project_cd),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadDialog = false }) { Text(stringResource(R.string.timemark_close)) }
            },
        )
    }

    if (showDeleteChapterDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChapterDialog = false },
            title = { Text(stringResource(R.string.timemark_delete_chapter_title)) },
            text = { Text(stringResource(R.string.timemark_delete_chapter_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = TimemarkStore.deleteAllProjectsForChapter(
                            context,
                            translationPick.code,
                            bookId,
                            chapterNum,
                        )
                        val pid = projectId
                        if (pid != null && TimemarkStore.load(context, pid) == null) {
                            projectId = null
                            cues.clear()
                            refreshCues()
                        }
                        showDeleteChapterDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.timemark_toast_projects_deleted, n),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(stringResource(R.string.timemark_delete_chapter), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChapterDialog = false }) {
                    Text(stringResource(R.string.timemark_close))
                }
            },
        )
    }

    if (showDeleteBookDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBookDialog = false },
            title = { Text(stringResource(R.string.timemark_delete_book_title)) },
            text = { Text(stringResource(R.string.timemark_delete_book_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = TimemarkStore.deleteAllProjectsForBook(context, bookId)
                        val pid = projectId
                        if (pid != null && TimemarkStore.load(context, pid) == null) {
                            projectId = null
                            cues.clear()
                            refreshCues()
                        }
                        showDeleteBookDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.timemark_toast_projects_deleted, n),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(stringResource(R.string.timemark_delete_book), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBookDialog = false }) {
                    Text(stringResource(R.string.timemark_close))
                }
            },
        )
    }
}

@Composable
private fun TimemarkVersesHeightSplitter(
    onDragDeltaPx: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.timemark_verses_splitter_cd)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics { contentDescription = desc }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    onDragDeltaPx(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.88f),
            thickness = 3.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}
