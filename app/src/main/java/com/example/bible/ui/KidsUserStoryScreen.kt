package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class StorySlide(
    val imageBlock: KidsUserStoryBlock?,
    val textBlock: KidsUserStoryBlock?,
    val imageBlockIndex: Int,
    val textBlockIndex: Int,
    val durationMs: Long,
) {
    val voiceKey: String?
        get() = if (textBlockIndex >= 0) KidsStoryVoice.paragraphKey(textBlockIndex) else null
}

internal fun decodeKidsStoryBitmap(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
}

fun KidsUserStory.buildSlides(): List<StorySlide> {
    val slides = mutableListOf<StorySlide>()
    var curImg: KidsUserStoryBlock? = null
    var curImgIdx = -1
    var imgHasText = false

    fun flushOrphan() {
        if (curImg != null && !imgHasText) {
            slides.add(
                StorySlide(
                    imageBlock = curImg,
                    textBlock = null,
                    imageBlockIndex = curImgIdx,
                    textBlockIndex = -1,
                    durationMs = imageDurations[curImgIdx] ?: KidsUserStory.DEFAULT_IMAGE_DURATION_MS,
                )
            )
        }
        curImg = null
        curImgIdx = -1
        imgHasText = false
    }

    blocks.forEachIndexed { idx, block ->
        if (block.isImage) {
            flushOrphan()
            curImg = block
            curImgIdx = idx
        } else {
            slides.add(
                StorySlide(
                    imageBlock = curImg,
                    textBlock = block,
                    imageBlockIndex = curImgIdx,
                    textBlockIndex = idx,
                    durationMs = imageDurations[curImgIdx] ?: KidsUserStory.DEFAULT_IMAGE_DURATION_MS,
                )
            )
            if (curImg != null) imgHasText = true
        }
    }
    flushOrphan()
    return slides
}

// ======================== Helper Composables ========================

@Composable
private fun StoryHeroCard(
    story: KidsUserStory,
    activeSlide: StorySlide?,
    playbackActive: Boolean,
    onPlayToggle: () -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val gradientColors = story.toBibleStory().gradient.map { Color(it) }
    val brush = Brush.linearGradient(gradientColors)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(brush)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val ib = activeSlide?.imageBlock ?: story.blocks.firstOrNull { it.isImage }
            if (ib != null) {
                val file = story.imageFile(context, ib)
                val bitmap = if (file != null) remember(ib.image) { decodeKidsStoryBitmap(file) } else null
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text("📷", fontSize = 64.sp)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF0F0F0), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📖", fontSize = 64.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            story.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Своя история",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onPlayToggle,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF0288D1),
            ),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                imageVector = if (playbackActive) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (playbackActive) "Остановить" else "Читать вслух",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StoryVoiceRow(
    saved: Boolean,
    recording: Boolean,
    accent: Color,
    onRecord: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            recording -> {
                IconButton(onClick = onRecord) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Остановить и сохранить запись", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Text("Идёт запись… нажмите ■ чтобы сохранить", fontSize = 13.sp, color = Color(0xFFEF4444))
            }
            saved -> {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить голос", tint = Color(0xFFEF4444))
                }
                Text("Голос записан", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                TextButton(onClick = onRecord) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = accent)
                    Spacer(Modifier.width(4.dp))
                    Text("Записать", color = accent, fontSize = 13.sp)
                }
                TextButton(onClick = onImport) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = accent.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text("Вставить", color = accent.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ImageDurationRow(
    durationMs: Long,
    accent: Color,
    onSave: (Long) -> Unit,
) {
    var value by remember(durationMs) { mutableStateOf(durationMs.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Показ:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            placeholder = { Text("${KidsUserStory.DEFAULT_IMAGE_DURATION_MS}") },
        )
        Text("мс", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
            onClick = {
                val ms = value.toLongOrNull()?.coerceIn(KidsUserStory.MIN_IMAGE_DURATION_MS, KidsUserStory.MAX_IMAGE_DURATION_MS) ?: KidsUserStory.DEFAULT_IMAGE_DURATION_MS
                value = ms.toString()
                onSave(ms)
            },
        ) { Text("OK", color = accent) }
    }
}

@Composable
private fun SlideItem(
    slide: StorySlide,
    showImage: Boolean,
    isActive: Boolean,
    accent: Color,
    recordingKey: String?,
    parentMode: Boolean,
    hasVoice: (String) -> Boolean,
    endRecord: (Boolean) -> Unit,
    requestRecord: (String) -> Unit,
    onDeleteVoice: (String) -> Unit,
    onImportVoice: (String) -> Unit,
    onDurationChange: (Long) -> Unit,
    context: android.content.Context,
    story: KidsUserStory,
) {
    val key = slide.voiceKey
    val saved = key != null && hasVoice(key)
    val recording = key != null && recordingKey == key

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Картинка отображается только в начале сцены
        if (showImage && slide.imageBlock != null) {
            val file = story.imageFile(context, slide.imageBlock)
            val bitmap = if (file != null) remember(slide.imageBlock.image) { decodeKidsStoryBitmap(file) } else null
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            if (isActive) 2.dp else 0.dp,
                            if (isActive) accent else Color.Transparent,
                            RoundedCornerShape(18.dp)
                        ),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (slide.textBlock != null) {
            Text(
                slide.textBlock.text,
                fontSize = 20.sp,
                lineHeight = 32.sp,
                color = if (isActive) accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            // Управление голосом в режиме редактирования
            if (parentMode && key != null) {
                StoryVoiceRow(
                    saved = saved,
                    recording = recording,
                    accent = accent,
                    onRecord = { if (recording) endRecord(true) else requestRecord(key) },
                    onDelete = { onDeleteVoice(key) },
                    onImport = { onImportVoice(key) },
                )
            }
        } else {
            // Редактор миллисекунд только для картинок без текста и только в режиме редактирования
            if (parentMode) {
                ImageDurationRow(
                    durationMs = slide.durationMs,
                    accent = accent,
                    onSave = onDurationChange,
                )
            }
        }
    }
}

// ======================== Main Screen ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsUserStoryScreen(
    story: KidsUserStory,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenStory: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slides = remember(story.id, story.imageDurations) { story.buildSlides() }
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val read by repo.storiesRead.collectAsStateWithLifecycle(initialValue = emptySet())
    val others by KidsUserStoriesStore.stories(context).collectAsStateWithLifecycle()

    var currentSlideIndex by remember(story.id) { mutableIntStateOf(-1) }
    var playbackActive by remember(story.id) { mutableStateOf(false) }
    var parentMode by rememberSaveable(story.id) { mutableStateOf(false) }
    var recordingKey by remember(story.id) { mutableStateOf<String?>(null) }
    var pendingRecordKey by remember(story.id) { mutableStateOf<String?>(null) }
    var pendingImportKey by remember(story.id) { mutableStateOf<String?>(null) }
    var voiceRevision by remember(story.id) { mutableIntStateOf(0) }
    var editGate by remember { mutableStateOf(false) }
    var celebrated by remember(story.id) { mutableStateOf(false) }
    var autoPlayed by remember(story.id) { mutableStateOf(false) }

    var playJob by remember { mutableStateOf<Job?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val pendingUtterances = remember { mutableMapOf<String, CompletableDeferred<Unit>>() }

    val done = story.id in read
    val audio = remember(story.id) { StoryAudio(context.applicationContext) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

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

    fun startPlayback() {
        if (playJob?.isActive == true || slides.isEmpty()) return
        playbackActive = true
        playJob = scope.launch {
            for ((index, slide) in slides.withIndex()) {
                currentSlideIndex = index

                val voiceKey = slide.voiceKey
                val hasVoice = voiceKey != null && KidsStoryVoice.has(context, story.id, voiceKey)

                if (hasVoice) {
                    val file = KidsStoryVoice.file(context, story.id, voiceKey!!)
                    suspendCancellableCoroutine<Unit> { cont ->
                        if (file == null || !file.exists()) { cont.resume(Unit); return@suspendCancellableCoroutine }
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                            setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                            prepare()
                            start()
                        }
                        cont.invokeOnCancellation { mediaPlayer?.release(); mediaPlayer = null }
                    }
                } else if (slide.textBlock != null) {
                    val id = UUID.randomUUID().toString()
                    val deferred = CompletableDeferred<Unit>()
                    pendingUtterances[id] = deferred
                    audio.tts?.speak(slide.textBlock.text, TextToSpeech.QUEUE_FLUSH, null, id)
                    deferred.await()
                } else {
                    delay(slide.durationMs)
                }
            }
            playbackActive = false
            currentSlideIndex = -1
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        playbackActive = false
        currentSlideIndex = -1
        mediaPlayer?.release()
        mediaPlayer = null
        audio.tts?.stop()
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
        if (playbackActive) stopPlayback()
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
                    mainHandler.post {
                        audio.onTtsDone(utteranceId)
                        if (utteranceId != null) {
                            pendingUtterances[utteranceId]?.complete(Unit)
                            pendingUtterances.remove(utteranceId)
                        }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        audio.onTtsError()
                        if (utteranceId != null) {
                            pendingUtterances[utteranceId]?.complete(Unit)
                            pendingUtterances.remove(utteranceId)
                        }
                    }
                }
            })
            audio.tts = e
            audio.speechRate = 0.88f
        }
        onDispose {
            val key = recordingKey
            if (key != null) audio.stopRecording(story.id, key, save = false)
            stopPlayback()
            audio.release()
        }
    }

    val accent = Color(0xFF7C3AED)
    val next = others.getOrNull(others.indexOfFirst { it.id == story.id } + 1)

    LaunchedEffect(story.id, autoPlayed) {
        if (!autoPlayed && slides.isNotEmpty()) {
            autoPlayed = true
            repeat(30) {
                if (audio.tts != null && !playbackActive) {
                    delay(500)
                    startPlayback()
                    return@LaunchedEffect
                }
                delay(100)
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val activeSlide: StorySlide? = when {
        playbackActive && currentSlideIndex in slides.indices -> slides[currentSlideIndex]
        currentSlideIndex in slides.indices -> slides[currentSlideIndex]
        else -> slides.firstOrNull { it.imageBlock != null } ?: slides.firstOrNull()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!isLandscape) {
                CenterAlignedTopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = { if (playbackActive) stopPlayback() else startPlayback() }) {
                            Icon(
                                imageVector = if (playbackActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = "Читать",
                                tint = accent,
                            )
                        }
                        if (parentMode) {
                            TextButton(onClick = { editGate = true }) { Text("Изменить") }
                        }
                        IconButton(onClick = {
                            if (recordingKey != null) endRecord(false)
                            parentMode = !parentMode
                        }) {
                            Icon(
                                if (parentMode) Icons.Filled.Mic else Icons.Filled.MicNone,
                                contentDescription = "Режим редактирования",
                                tint = if (parentMode) Color(0xFFDB2777) else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Основной контент (занимает весь экран, без учета topBar)
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Левая часть: карточка (с отступом сверху, чтобы не перекрывать плавающие кнопки)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 60.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        StoryHeroCard(
                            story = story,
                            activeSlide = activeSlide,
                            playbackActive = playbackActive,
                            onPlayToggle = { if (playbackActive) stopPlayback() else startPlayback() },
                            context = context,
                        )
                    }

                    // Правая часть: список
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 60.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        slides.forEachIndexed { index, slide ->
                            val isActive = index == currentSlideIndex && playbackActive
                            val showImage = slide.imageBlock != null && (index == 0 || slides[index - 1].imageBlock?.image != slide.imageBlock.image)
                            SlideItem(
                                slide = slide,
                                showImage = showImage,
                                isActive = isActive,
                                accent = accent,
                                recordingKey = recordingKey,
                                parentMode = parentMode,
                                hasVoice = ::hasVoice,
                                endRecord = { save -> endRecord(save) },
                                requestRecord = { key -> requestRecord(key) },
                                onDeleteVoice = { key -> KidsStoryVoice.clear(context, story.id, key); voiceRevision++ },
                                onImportVoice = { key -> import(key) },
                                onDurationChange = { ms ->
                                    scope.launch {
                                        val updated = story.copy(imageDurations = story.imageDurations.toMutableMap().apply { put(slide.imageBlockIndex, ms) })
                                        KidsUserStoriesStore.save(context, updated)
                                    }
                                },
                                context = context,
                                story = story,
                            )
                        }
                        ReadDoneBlock(done, celebrated, next, onMarkRead = {
                            scope.launch {
                                if (repo.markStoryRead(story.id.toString())) {
                                    celebrated = true
                                    Toast.makeText(context, "Ура! +1 звезда", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, onOpenNext = onOpenStory)
                    }
                }

                // Плавающие кнопки управления для горизонтального режима
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shadowElevation = 4.dp,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (parentMode) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shadowElevation = 4.dp,
                            ) {
                                TextButton(onClick = { editGate = true }) { Text("Изменить") }
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shadowElevation = 4.dp,
                        ) {
                            IconButton(onClick = { if (playbackActive) stopPlayback() else startPlayback() }) {
                                Icon(
                                    imageVector = if (playbackActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = "Читать",
                                    tint = accent,
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shadowElevation = 4.dp,
                        ) {
                            IconButton(onClick = {
                                if (recordingKey != null) endRecord(false)
                                parentMode = !parentMode
                            }) {
                                Icon(
                                    if (parentMode) Icons.Filled.Mic else Icons.Filled.MicNone,
                                    contentDescription = "Режим редактирования",
                                    tint = if (parentMode) Color(0xFFDB2777) else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Портретный режим (без изменений)
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StoryHeroCard(
                    story = story,
                    activeSlide = activeSlide,
                    playbackActive = playbackActive,
                    onPlayToggle = { if (playbackActive) stopPlayback() else startPlayback() },
                    context = context,
                )

                Spacer(Modifier.height(8.dp))

                slides.forEachIndexed { index, slide ->
                    val isActive = index == currentSlideIndex && playbackActive
                    val showImage = slide.imageBlock != null && (index == 0 || slides[index - 1].imageBlock?.image != slide.imageBlock.image)
                    SlideItem(
                        slide = slide,
                        showImage = showImage,
                        isActive = isActive,
                        accent = accent,
                        recordingKey = recordingKey,
                        parentMode = parentMode,
                        hasVoice = ::hasVoice,
                        endRecord = { save -> endRecord(save) },
                        requestRecord = { key -> requestRecord(key) },
                        onDeleteVoice = { key -> KidsStoryVoice.clear(context, story.id, key); voiceRevision++ },
                        onImportVoice = { key -> import(key) },
                        onDurationChange = { ms ->
                            scope.launch {
                                val updated = story.copy(imageDurations = story.imageDurations.toMutableMap().apply { put(slide.imageBlockIndex, ms) })
                                KidsUserStoriesStore.save(context, updated)
                            }
                        },
                        context = context,
                        story = story,
                    )
                }

                ReadDoneBlock(done, celebrated, next, onMarkRead = {
                    scope.launch {
                        if (repo.markStoryRead(story.id.toString())) {
                            celebrated = true
                            Toast.makeText(context, "Ура! +1 звезда", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, onOpenNext = onOpenStory)

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (editGate) {
        ParentGateDialog(
            onDismiss = { editGate = false },
            onPassed = { editGate = false; onEdit() },
        )
    }
}

@Composable
private fun ReadDoneBlock(
    done: Boolean,
    celebrated: Boolean,
    next: KidsUserStory?,
    onMarkRead: () -> Unit,
    onOpenNext: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (done) Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B)))
                    else Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                )
                .clickable(enabled = !done) { onMarkRead() }
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
        if (celebrated) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { KidsRewardStars() }
        }
        if (next != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Дальше: ${next.title}",
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onOpenNext(next.id) }
                    .padding(14.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}