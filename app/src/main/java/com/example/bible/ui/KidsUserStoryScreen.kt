package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.KidsStoryVoice
import com.example.bible.data.KidsUserStoriesStore
import com.example.bible.data.KidsUserStory
import com.example.bible.data.KidsUserStoryBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

private data class StoryDraftBlock(
    val key: String,
    val kind: String,
    val text: String = "",
    val image: String = "",
)

internal fun decodeKidsStoryBitmap(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsUserStoryEditorScreen(
    storyId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = storyId?.let { KidsUserStoriesStore.find(context, it) }
    val draftId = remember { existing?.id ?: KidsUserStoriesStore.newId() }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    val blocks = remember {
        mutableStateListOf<StoryDraftBlock>().apply {
            val source = existing?.blocks.orEmpty()
            if (source.isEmpty()) {
                add(StoryDraftBlock(key = UUID.randomUUID().toString(), kind = KidsUserStoryBlock.TEXT))
            } else {
                source.forEach { block ->
                    add(
                        StoryDraftBlock(
                            key = UUID.randomUUID().toString(),
                            kind = block.kind,
                            text = block.text,
                            image = block.image,
                        ),
                    )
                }
            }
        }
    }
    val addedNow = remember { mutableSetOf<String>() }
    var insertMenuIndex by remember { mutableStateOf<Int?>(null) }
    var insertMenuExpanded by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val rel = KidsUserStoriesStore.copyImage(context, uri)
        if (rel == null) {
            Toast.makeText(context, "Не удалось взять картинку", Toast.LENGTH_SHORT).show()
        } else {
            addedNow += rel
            blocks.add(StoryDraftBlock(key = UUID.randomUUID().toString(), kind = KidsUserStoryBlock.IMAGE, image = rel))
        }
    }

    fun discard() {
        val kept = KidsUserStoriesStore.find(context, draftId)?.blocks?.map { it.image }?.toSet().orEmpty()
        addedNow.filter { it !in kept }.forEach { File(context.filesDir, it).delete() }
        onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (existing == null) "Новая история" else "Изменить историю", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { discard() }) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Соберите сюжет как хотите: текст, потом картинка, снова текст. Порядок на экране будет таким же.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") },
                singleLine = true,
            )
            blocks.forEachIndexed { index, block ->
                val isFirst = index == 0
                val isLast = index == blocks.size - 1
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Кнопки перемещения
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        IconButton(
                            enabled = !isFirst,
                            onClick = {
                                val prev = blocks[index - 1]
                                blocks[index - 1] = block
                                blocks[index] = prev
                            },
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Вверх",
                                tint = if (isFirst) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            enabled = !isLast,
                            onClick = {
                                val next = blocks[index + 1]
                                blocks[index + 1] = block
                                blocks[index] = next
                            },
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Вниз",
                                tint = if (isLast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))

                    // Основной контент блока
                    Column(Modifier.weight(1f)) {
                        if (block.kind == KidsUserStoryBlock.IMAGE) {
                            val file = File(context.filesDir, block.image)
                            val bitmap = remember(block.image) { decodeKidsStoryBitmap(file) }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(10.dp),
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    OutlinedButton(
                                        onClick = { blocks.removeAt(index) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Удалить")
                                    }
                                    DropdownMenu(
                                        expanded = insertMenuExpanded && insertMenuIndex == index,
                                        onDismissRequest = { insertMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Вставить текст") },
                                            onClick = {
                                                blocks.add(index, StoryDraftBlock(key = UUID.randomUUID().toString(), kind = KidsUserStoryBlock.TEXT))
                                                insertMenuExpanded = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Вставить картинку") },
                                            onClick = {
                                                blocks.add(index, StoryDraftBlock(key = UUID.randomUUID().toString(), kind = KidsUserStoryBlock.IMAGE, image = ""))
                                                insertMenuExpanded = false
                                            },
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            insertMenuIndex = index
                                            insertMenuExpanded = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Вставить")
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = block.text,
                                onValueChange = { blocks[index] = block.copy(text = it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (index == 0) "Текст" else "Ещё текст") },
                                minLines = 6,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedButton(
                                    onClick = { blocks.removeAt(index) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Удалить этот текст")
                                }
                                DropdownMenu(
                                    expanded = insertMenuExpanded && insertMenuIndex == index,
                                    onDismissRequest = { insertMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Вставить текст") },
                                        onClick = {
                                            blocks.add(index, StoryDraftBlock(key = UUID.randomUUID().toString(), kind = KidsUserStoryBlock.TEXT))
                                            insertMenuExpanded = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Вставить картинку") },
                                        onClick = {
                                            blocks.add(index, StoryDraftBlock(key = UUID.randomUUID().toString(), kind = KidsUserStoryBlock.IMAGE, image = ""))
                                            insertMenuExpanded = false
                                        },
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        insertMenuIndex = index
                                        insertMenuExpanded = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Вставить")
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        blocks.add(StoryDraftBlock(key = UUID.randomUUID().toString(), kind = KidsUserStoryBlock.TEXT))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ещё текст")
                }
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ещё картинку")
                }
            }
            Button(
                onClick = {
                    val ready = blocks.map {
                        if (it.kind == KidsUserStoryBlock.IMAGE) {
                            KidsUserStoryBlock(KidsUserStoryBlock.IMAGE, image = it.image)
                        } else {
                            KidsUserStoryBlock(KidsUserStoryBlock.TEXT, text = it.text.trim())
                        }
                    }
                    if (title.isBlank() || ready.none { it.text.isNotBlank() || it.image.isNotBlank() }) {
                        Toast.makeText(context, "Нужны название и хотя бы один текст или картинка", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        KidsUserStoriesStore.save(context, KidsUserStory(draftId, title.trim(), ready))
                        val kept = ready.map { it.image }.filter { it.isNotBlank() }.toSet()
                        addedNow.filter { it !in kept }.forEach { File(context.filesDir, it).delete() }
                        addedNow.clear()
                        onSaved(draftId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
            if (existing != null) {
                TextButton(
                    onClick = {
                        scope.launch {
                            KidsUserStoriesStore.delete(context, draftId)
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Удалить историю") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KidsUserStoryScreen(
    story: KidsUserStory,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenStory: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bible = remember(story) { story.toBibleStory() }
    val texts = bible.paragraphs
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val read by repo.storiesRead.collectAsStateWithLifecycle(initialValue = emptySet())
    val others by KidsUserStoriesStore.stories(context).collectAsStateWithLifecycle()
    var speakingIndex by remember(story.id) { mutableIntStateOf(-1) }
    var playbackActive by remember(story.id) { mutableStateOf(false) }
    var parentMode by rememberSaveable(story.id) { mutableStateOf(false) }
    var recordingKey by remember(story.id) { mutableStateOf<String?>(null) }
    var pendingRecordKey by remember(story.id) { mutableStateOf<String?>(null) }
    var pendingImportKey by remember(story.id) { mutableStateOf<String?>(null) }
    var voiceRevision by remember(story.id) { mutableIntStateOf(0) }
    var editGate by remember { mutableStateOf(false) }
    var celebrated by remember(story.id) { mutableStateOf(false) }
    var autoPlayed by remember(story.id) { mutableStateOf(false) }
    val done = story.id in read
    val audio = remember(story.id) { StoryAudio(context.applicationContext) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    audio.onSpeaking = { speakingIndex = it }
    audio.onActive = { playbackActive = it }

    fun hasVoice(key: String) = voiceRevision >= 0 && KidsStoryVoice.has(context, story.id, key)

    fun beginRecord(key: String) {
        if (audio.startRecording(story.id, key)) recordingKey = key
        else Toast.makeText(context, "Не удалось начать запись", Toast.LENGTH_SHORT).show()
    }

    fun endRecord(save: Boolean) {
        val key = recordingKey ?: return
        val ok = audio.stopRecording(story.id, key, save)
        recordingKey = null
        if (!save) return
        voiceRevision++
        if (!ok) Toast.makeText(context, "Запись слишком короткая", Toast.LENGTH_SHORT).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val key = pendingRecordKey
        pendingRecordKey = null
        if (granted && key != null) beginRecord(key)
        else if (!granted) Toast.makeText(context, "Нужен микрофон, чтобы записать голос", Toast.LENGTH_SHORT).show()
    }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = pendingImportKey
        pendingImportKey = null
        if (uri == null || key == null) return@rememberLauncherForActivityResult
        if (KidsStoryVoice.importUri(context, story.id, key, uri)) voiceRevision++
        else Toast.makeText(context, "Не удалось взять файл", Toast.LENGTH_SHORT).show()
    }

    fun requestRecord(key: String) {
        if (playbackActive) audio.stopPlayback()
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> beginRecord(key)
            else -> {
                pendingRecordKey = key
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun import(key: String) {
        pendingImportKey = key
        importAudio.launch(arrayOf("audio/*"))
    }

    DisposableEffect(story.id) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val e = engine ?: return@TextToSpeech
            e.language = Locale.forLanguageTag("ru-RU")
            e.setSpeechRate(0.88f)
            e.setPitch(1.05f)
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { audio.onTtsDone(utteranceId) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { audio.onTtsError() }
                }
            })
            audio.tts = e
            audio.speechRate = 0.88f
        }
        onDispose {
            val key = recordingKey
            if (key != null) audio.stopRecording(story.id, key, save = false)
            audio.release()
        }
    }

    val accent = Color(0xFF7C3AED)
    val next = others.getOrNull(others.indexOfFirst { it.id == story.id } + 1)
    val listState = rememberLazyListState()

    // Определяем индекс элемента в blocks для текстового блока с данным индексом
    fun blockIndexForText(textIndex: Int): Int {
        var currentIndex = 0
        for ((i, block) in story.blocks.withIndex()) {
            if (!block.isImage) {
                if (currentIndex == textIndex) return i
                currentIndex++
            }
        }
        return -1
    }

    // Определяем, какая картинка "принадлежит" текстовому блоку с данным индексом
    // (первая картинка ПОСЛЕ него в списке блоков)
    fun activeImageForText(textIndex: Int): KidsUserStoryBlock? {
        var currentIndex = textIndex
        for (block in story.blocks) {
            if (!block.isImage) {
                if (currentIndex == 0) {
                    // Это наш текстовый блок — ищем первую картинку ПОСЛЕ него
                    for (laterBlock in story.blocks) {
                        if (laterBlock.isImage) return laterBlock
                    }
                    return null
                }
                currentIndex--
            }
        }
        return null
    }

    // Автоскролл к тексту, который сейчас читается
    LaunchedEffect(speakingIndex, playbackActive) {
        if (playbackActive && speakingIndex >= 0) {
            val blockIdx = blockIndexForText(speakingIndex)
            if (blockIdx >= 0 && blockIdx < story.blocks.size) {
                listState.animateScrollToItem(blockIdx)
            }
        }
    }

    // Автоматический старт чтения при открытии истории
    LaunchedEffect(story.id, autoPlayed) {
        if (!autoPlayed && texts.isNotEmpty()) {
            autoPlayed = true
            // Ждём пока TTS инициализируется (обычно 500-1500мс)
            repeat(30) {
                if (audio.tts != null && !playbackActive) {
                    delay(300) // Дополнительная задержка для уверенности
                    audio.playAll(bible, texts)
                    return@LaunchedEffect
                }
                delay(100) // Проверяем каждые 100мс, максимум 3сек
            }
            // Если TTS так и не готов — запускаем по кнопке "Читать вслух"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(story.title, fontWeight = FontWeight.ExtraBold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { editGate = true }) { Text("Изменить") }
                    IconButton(onClick = {
                        if (recordingKey != null) endRecord(save = false)
                        parentMode = !parentMode
                    }) {
                        Icon(
                            if (parentMode) Icons.Filled.Mic else Icons.Filled.MicNone,
                            contentDescription = "Запись голоса родителя",
                            tint = if (parentMode) Color(0xFFDB2777) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent, Color(0xFFEC4899))))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val cover = story.blocks.firstOrNull { it.isImage }?.let { story.imageFile(context, it) }
                    val bitmap = remember(cover?.absolutePath) { cover?.let { decodeKidsStoryBitmap(it) } }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = story.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(story.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text("Своя история", color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .clickable {
                                if (recordingKey != null) {
                                    Toast.makeText(context, "Сначала сохраните запись", Toast.LENGTH_SHORT).show()
                                } else if (playbackActive) {
                                    audio.stopPlayback()
                                } else if (audio.tts == null && !hasVoice(KidsStoryVoice.WHOLE)) {
                                    Toast.makeText(context, "Голос ещё загружается", Toast.LENGTH_SHORT).show()
                                } else {
                                    audio.playAll(bible, texts)
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (playbackActive) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(8.dp))
                        Text(if (playbackActive) "Стоп" else "Читать вслух", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    }
                    if (parentMode) {
                        StoryVoiceRow(
                            saved = hasVoice(KidsStoryVoice.WHOLE),
                            recording = recordingKey == KidsStoryVoice.WHOLE,
                            parentMode = true,
                            accent = Color.White,
                            onRecord = {
                                if (recordingKey == KidsStoryVoice.WHOLE) endRecord(true) else requestRecord(KidsStoryVoice.WHOLE)
                            },
                            onDelete = {
                                KidsStoryVoice.clear(context, story.id, KidsStoryVoice.WHOLE)
                                voiceRevision++
                            },
                            onImport = { import(KidsStoryVoice.WHOLE) },
                        )
                        Text(
                            "Готовая озвучка на всю историю, запись или файл с телефона. Без неё текст читает голос телефона.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            itemsIndexed(story.blocks, key = { index, block -> "$index-${block.kind}-${block.image}-${block.text.take(12)}" }) { index, block ->
                if (block.isImage) {
                    // Показываем все картинки всегда
                    val file = story.imageFile(context, block)
                    val bitmap = remember(block.image) { file?.let { decodeKidsStoryBitmap(it) } }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else {
                    val textIndex = story.blocks.take(index).count { !it.isImage }
                    val key = KidsStoryVoice.paragraphKey(textIndex)
                    val active = speakingIndex == textIndex
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(if (active) 2.dp else 0.dp, if (active) accent else Color.Transparent, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    ) {
                        Text(
                            block.text,
                            fontSize = 21.sp,
                            lineHeight = 30.sp,
                            modifier = Modifier.clickable {
                                if (recordingKey != null) {
                                    Toast.makeText(context, "Сначала сохраните запись", Toast.LENGTH_SHORT).show()
                                } else {
                                    audio.playOne(bible, key, block.text, textIndex)
                                }
                            },
                        )
                        StoryVoiceRow(
                            saved = hasVoice(key),
                            recording = recordingKey == key,
                            parentMode = parentMode,
                            accent = accent,
                            onRecord = { if (recordingKey == key) endRecord(true) else requestRecord(key) },
                            onDelete = {
                                KidsStoryVoice.clear(context, story.id, key)
                                voiceRevision++
                            },
                            onImport = { import(key) },
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (done) {
                                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B)))
                            } else {
                                androidx.compose.ui.graphics.Brush.linearGradient(KidsWarmHoney)
                            },
                        )
                        .clickable(enabled = !done) {
                            scope.launch {
                                if (repo.markStoryRead(story.id)) {
                                    celebrated = true
                                    Toast.makeText(context, "Ура! +1 звезда", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (done) "✅ История прочитана" else "⭐ Я прочитал(а)!",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                    )
                }
            }
            if (celebrated) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { KidsRewardStars() } }
            }
            if (next != null) {
                item {
                    Text(
                        "Дальше: ${next.title}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onOpenStory(next.id) }
                            .padding(14.dp),
                    )
                }
            }
        }
    }
    if (editGate) {
        ParentGateDialog(
            onDismiss = { editGate = false },
            onPassed = {
                editGate = false
                onEdit()
            },
        )
    }
}
