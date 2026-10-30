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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.example.bible.ui.KidsWarmHoney
import com.example.bible.ui.KidsRewardStars
import androidx.compose.ui.platform.LocalContext
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

/** Слайд истории: картинка + связанный текст/звук */
data class StorySlide(
    val imageBlock: KidsUserStoryBlock?,
    val textBlock: KidsUserStoryBlock?,
    val durationMs: Long, // сколько показывать слайд (по умолчанию 2000мс)
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

/** Создаём массив слайдов из блоков истории */
fun KidsUserStory.buildSlides(): List<StorySlide> {
    val slides = mutableListOf<StorySlide>()
    var currentImage: KidsUserStoryBlock? = null
    
    for (block in blocks) {
        if (block.isImage) {
            currentImage = block
        } else {
            // Текст - создаём слайд с текущей картинкой
            slides.add(StorySlide(
                imageBlock = currentImage,
                textBlock = block,
                durationMs = 2000L // по умолчанию 2 секунды
            ))
        }
    }
    
    return slides
}

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
    val slides = remember(story.id) { story.buildSlides() }
    val texts = slides.mapNotNull { it.textBlock?.text }
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
    val done = story.id in read
    val audio = remember(story.id) { StoryAudio(context.applicationContext) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    
    audio.onSpeaking = { index ->
        currentSlideIndex = index
    }
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
    
    // Автоматический старт чтения при открытии истории
    LaunchedEffect(story.id, autoPlayed) {
        if (!autoPlayed && texts.isNotEmpty()) {
            autoPlayed = true
            // Ждём пока TTS инициализируется
            repeat(30) {
                if (audio.tts != null && !playbackActive) {
                    delay(500)
                    audio.playAll(slides, texts)
                    return@LaunchedEffect
                }
                delay(100)
            }
        }
    }

    // Определяем ориентацию экрана
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Заголовок с кнопкой воспроизведения
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent, Color(0xFFEC4899))))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                            } else if (audio.tts == null && slides.isEmpty()) {
                                Toast.makeText(context, "Голос ещё загружается", Toast.LENGTH_SHORT).show()
                            } else {
                                audio.playAll(slides, texts)
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (playbackActive) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = accent)
                    Spacer(Modifier.width(8.dp))
                    Text(if (playbackActive) "Стоп" else "Читать вслух", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }

            // Слайды с картинками и текстом
            slides.forEachIndexed { index, slide ->
                val isActive = index == currentSlideIndex && playbackActive
                
                if (isLandscape) {
                    // Горизонтальный режим: картинка слева, текст справа
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Картинка слева
                        if (slide.imageBlock != null) {
                            val file = story.imageFile(context, slide.imageBlock)
                            val bitmap = remember(slide.imageBlock.image) { file?.let { decodeKidsStoryBitmap(it) } }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(2.dp, if (isActive) accent else Color.Transparent, RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        
                        Spacer(Modifier.width(12.dp))
                        
                        // Текст справа
                        if (slide.textBlock != null) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isActive) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(14.dp),
                            ) {
                                Text(
                                    slide.textBlock.text,
                                    fontSize = 18.sp,
                                    lineHeight = 26.sp,
                                    color = if (isActive) accent else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                } else {
                    // Вертикальный режим: картинка сверху, текст снизу
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isActive) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Картинка сверху
                        slide.imageBlock?.let { ib ->
                            val file = story.imageFile(context, ib)
                            val bitmap = if (file != null) {
                                remember(ib.image) { decodeKidsStoryBitmap(file) }
                            } else {
                                null
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(4f / 3f)
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        
                        // Текст снизу
                        slide.textBlock?.let { block ->
                            Text(
                                block.text,
                                fontSize = 20.sp,
                                lineHeight = 28.sp,
                                color = if (isActive) accent else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Кнопка "Я прочитал"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (done) {
                            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B)))
                        } else {
                            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                        },
                    )
                    .clickable(enabled = !done) {
                        scope.launch {
                            if (repo.markStoryRead(story.id.toString())) {
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
            if (celebrated) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { KidsRewardStars() }
            }
            if (next != null) {
                Text(
                    "Дальше: ${next.title}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onOpenStory(next.id) }
                        .padding(14.dp),
                    textAlign = TextAlign.Center,
                )
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
