package com.example.bible.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsBibleStory
import com.example.bible.data.KidsStoryVoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private fun KidsBibleStory.brush(): Brush = Brush.linearGradient(gradient.map { Color(it) })

@Composable
private fun KidsStoryIllustration(story: KidsBibleStory, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(kidsStoryImage(story.id)),
        contentDescription = story.title,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsStoriesScreen(
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val read by repo.storiesRead.collectAsStateWithLifecycle(initialValue = emptySet())
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Библейские истории", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4C1D95), Color(0xFF0F766E))))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("📖 Слушай и читай", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Прочитано ${read.count { id -> KidsBibleStories.byId(id) != null }} из ${KidsBibleStories.all.size}. " +
                            "За каждую новую историю — звёздочка ⭐",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 14.sp,
                    )
                }
            }
            items(KidsBibleStories.all, key = { it.id }) { story ->
                val done = story.id in read
                Column(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(story.brush())
                        .clickable { onOpenStory(story.id) }
                        .padding(14.dp),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        KidsStoryIllustration(
                            story,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                        if (done) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Прочитано",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC111827))
                                    .padding(3.dp)
                                    .size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        story.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        maxLines = 2,
                    )
                    Text(story.reference, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsStoryScreen(
    storyId: String,
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
) {
    val story = KidsBibleStories.byId(storyId) ?: KidsBibleStories.all.first()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val read by repo.storiesRead.collectAsStateWithLifecycle(initialValue = emptySet())
    var speakingIndex by remember(story.id) { mutableIntStateOf(-1) }
    var sceneIndex by remember(story.id) { mutableIntStateOf(0) }
    var playbackActive by remember(story.id) { mutableStateOf(false) }
    var parentMode by rememberSaveable(story.id) { mutableStateOf(false) }
    var recordingKey by remember(story.id) { mutableStateOf<String?>(null) }
    var pendingRecordKey by remember(story.id) { mutableStateOf<String?>(null) }
    var voiceRevision by remember(story.id) { mutableIntStateOf(0) }
    val audio = remember(story.id) { StoryAudio(context.applicationContext) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    audio.onSpeaking = { index ->
        speakingIndex = index
        if (index in story.paragraphs.indices) sceneIndex = index
    }
    audio.onActive = { playbackActive = it }

    fun hasVoice(key: String): Boolean = voiceRevision >= 0 && KidsStoryVoice.has(context, story.id, key)

    fun beginRecord(key: String) {
        if (audio.startRecording(story.id, key)) {
            recordingKey = key
        } else {
            Toast.makeText(context, "Не удалось начать запись", Toast.LENGTH_SHORT).show()
        }
    }

    fun endRecord(save: Boolean) {
        val key = recordingKey ?: return
        val ok = audio.stopRecording(story.id, key, save)
        recordingKey = null
        if (!save) return
        voiceRevision++
        if (!ok) Toast.makeText(context, "Запись слишком короткая", Toast.LENGTH_SHORT).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val key = pendingRecordKey
        pendingRecordKey = null
        if (granted && key != null) beginRecord(key)
        else if (!granted) Toast.makeText(context, "Нужен микрофон, чтобы записать голос", Toast.LENGTH_SHORT).show()
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

    DisposableEffect(context, story.id) {
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
        }
        onDispose {
            val key = recordingKey
            if (key != null) audio.stopRecording(story.id, key, save = false)
            audio.release()
        }
    }

    LaunchedEffect(story.id, playbackActive, parentMode, recordingKey) {
        if (playbackActive || parentMode || recordingKey != null) return@LaunchedEffect
        while (true) {
            delay(5200)
            sceneIndex = (sceneIndex + 1) % story.paragraphs.size
        }
    }

    val nextStory = KidsBibleStories.all.getOrNull(KidsBibleStories.all.indexOf(story) + 1)
    val done = story.id in read
    val lessonMode = speakingIndex == story.paragraphs.size

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(story.title, fontWeight = FontWeight.ExtraBold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (recordingKey != null) endRecord(save = false)
                            parentMode = !parentMode
                        },
                    ) {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(story.brush())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KidsStoryStage(
                        story,
                        sceneIndex = sceneIndex,
                        lessonMode = lessonMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        story.paragraphs.indices.forEach { i ->
                            val selected = i == sceneIndex && !lessonMode
                            Box(
                                Modifier
                                    .size(if (selected) 12.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.45f))
                                    .clickable { sceneIndex = i },
                            )
                        }
                    }
                    if (parentMode) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Запишите каждый текст своим голосом — ребёнок услышит вас.",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        story.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(story.reference, color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .clickable {
                                if (recordingKey != null) {
                                    Toast.makeText(context, "Сначала сохраните запись", Toast.LENGTH_SHORT).show()
                                } else if (playbackActive) {
                                    audio.stopPlayback()
                                } else if (audio.tts == null) {
                                    Toast.makeText(context, "Голос ещё загружается", Toast.LENGTH_SHORT).show()
                                } else {
                                    audio.playAll(story)
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = Color(story.gradient.first())
                        Icon(
                            if (playbackActive) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = tint,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (playbackActive) "Стоп" else "Читать вслух",
                            color = tint,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                        )
                    }
                }
            }
            itemsIndexed(story.paragraphs) { index, text ->
                val key = KidsStoryVoice.paragraphKey(index)
                val active = index == speakingIndex
                val followsScene = !playbackActive && recordingKey == null && index == sceneIndex
                val bg by animateColorAsState(
                    when {
                        active -> Color(story.gradient.first()).copy(alpha = 0.18f)
                        followsScene -> Color(story.gradient.first()).copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    label = "para",
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .border(
                            width = if (active || followsScene) 2.dp else 0.dp,
                            color = if (active || followsScene) Color(story.gradient.first()) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(16.dp),
                ) {
                    Text(
                        text,
                        fontSize = 21.sp,
                        lineHeight = 30.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clickable {
                            if (recordingKey != null) {
                                Toast.makeText(context, "Сначала сохраните запись", Toast.LENGTH_SHORT).show()
                            } else {
                                sceneIndex = index
                                audio.playOne(story, key, text, index)
                            }
                        },
                    )
                    StoryVoiceRow(
                        saved = hasVoice(key),
                        recording = recordingKey == key,
                        parentMode = parentMode,
                        accent = Color(story.gradient.first()),
                        onRecord = { if (recordingKey == key) endRecord(save = true) else requestRecord(key) },
                        onDelete = {
                            KidsStoryVoice.file(context, story.id, key).delete()
                            voiceRevision++
                        },
                    )
                }
            }
            item {
                val lessonKey = KidsStoryVoice.LESSON
                val lessonActive = lessonMode
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFFEF3C7))
                        .border(
                            width = if (lessonActive) 2.dp else 0.dp,
                            color = if (lessonActive) Color(0xFFD97706) else Color.Transparent,
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(16.dp),
                ) {
                    Text("💡 Чему учит история", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF92400E))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        story.lesson,
                        fontSize = 19.sp,
                        lineHeight = 27.sp,
                        color = Color(0xFF78350F),
                        modifier = Modifier.clickable {
                            if (recordingKey != null) {
                                Toast.makeText(context, "Сначала сохраните запись", Toast.LENGTH_SHORT).show()
                            } else {
                                audio.playOne(story, lessonKey, story.lesson, story.paragraphs.size)
                            }
                        },
                    )
                    StoryVoiceRow(
                        saved = hasVoice(lessonKey),
                        recording = recordingKey == lessonKey,
                        parentMode = parentMode,
                        accent = Color(0xFFB45309),
                        onRecord = { if (recordingKey == lessonKey) endRecord(save = true) else requestRecord(lessonKey) },
                        onDelete = {
                            KidsStoryVoice.file(context, story.id, lessonKey).delete()
                            voiceRevision++
                        },
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (done) {
                                Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899)))
                            },
                        )
                        .clickable(enabled = !done) {
                            scope.launch {
                                if (repo.markStoryRead(story.id)) {
                                    Toast.makeText(context, "Ура! +1 звезда ⭐", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (done) "✅ История прочитана" else "⭐ Я прочитал(а)!",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                    )
                }
            }
            if (nextStory != null) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(1.dp, Color(nextStory.gradient.first()).copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                            .clickable { onOpenStory(nextStory.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(nextStory.brush()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(nextStory.emoji, fontSize = 26.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Следующая история", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(nextStory.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        }
                        Text("›", fontSize = 30.sp, color = Color(nextStory.gradient.first()))
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryVoiceRow(
    saved: Boolean,
    recording: Boolean,
    parentMode: Boolean,
    accent: Color,
    onRecord: () -> Unit,
    onDelete: () -> Unit,
) {
    if (!parentMode && !saved && !recording) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (recording) {
            Text("Идёт запись…", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else if (saved) {
            Text("Голос родителя", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        if (parentMode) {
            TextButton(onClick = onRecord) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accent,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (recording) "Сохранить" else if (saved) "Заново" else "Записать", color = accent)
            }
            if (saved && !recording) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить запись", tint = accent)
                }
            }
        }
    }
}

private class StoryAudio(private val context: Context) {
    var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private var recorder: MediaRecorder? = null
    private var recordingKey: String? = null
    private var generation = 0
    private var pendingUtterance: String? = null
    private var pendingNext: (() -> Unit)? = null
    var onSpeaking: (Int) -> Unit = {}
    var onActive: (Boolean) -> Unit = {}

    fun onTtsDone(utteranceId: String?) {
        val next = pendingNext
        if (utteranceId != null && utteranceId == pendingUtterance && next != null) {
            pendingUtterance = null
            pendingNext = null
            next()
        }
    }

    fun onTtsError() {
        val next = pendingNext
        pendingUtterance = null
        pendingNext = null
        next?.invoke()
    }

    fun stopPlayback() {
        generation++
        pendingUtterance = null
        pendingNext = null
        tts?.stop()
        player?.let {
            runCatching { it.setOnCompletionListener(null) }
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        onSpeaking(-1)
        onActive(false)
    }

    fun playAll(story: KidsBibleStory) {
        stopPlayback()
        val gen = generation
        onActive(true)
        val steps = buildList {
            add(Triple("title", story.title, -1))
            story.paragraphs.forEachIndexed { index, text ->
                add(Triple(KidsStoryVoice.paragraphKey(index), text, index))
            }
            add(Triple(KidsStoryVoice.LESSON, story.lesson, story.paragraphs.size))
        }
        fun run(index: Int) {
            if (gen != generation) return
            if (index >= steps.size) {
                onSpeaking(-1)
                onActive(false)
                return
            }
            val (key, text, scene) = steps[index]
            playClip(story.id, key, text, scene, gen) { run(index + 1) }
        }
        run(0)
    }

    fun playOne(story: KidsBibleStory, key: String, text: String, scene: Int) {
        stopPlayback()
        val gen = generation
        onActive(true)
        playClip(story.id, key, text, scene, gen) {
            if (gen == generation) {
                onSpeaking(-1)
                onActive(false)
            }
        }
    }

    private fun playClip(
        storyId: String,
        key: String,
        text: String,
        scene: Int,
        gen: Int,
        next: () -> Unit,
    ) {
        if (gen != generation) return
        onSpeaking(scene)
        val file = KidsStoryVoice.file(context, storyId, key)
        if (file.exists() && file.length() > 800L) {
            val mp = MediaPlayer()
            player = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener {
                    if (player == it) {
                        it.release()
                        player = null
                    }
                    if (gen == generation) next()
                }
                mp.setOnErrorListener { failed, _, _ ->
                    if (player == failed) {
                        failed.release()
                        player = null
                    }
                    if (gen == generation) speakOrStop(text, key, gen, next)
                    true
                }
                mp.prepare()
                mp.start()
            } catch (_: Exception) {
                runCatching { mp.release() }
                if (player == mp) player = null
                speakOrStop(text, key, gen, next)
            }
        } else {
            speakOrStop(text, key, gen, next)
        }
    }

    private fun speakOrStop(text: String, key: String, gen: Int, next: () -> Unit) {
        val engine = tts
        if (engine == null || gen != generation) {
            onSpeaking(-1)
            onActive(false)
            return
        }
        val utterance = "v${gen}_$key"
        pendingUtterance = utterance
        pendingNext = next
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance)
    }

    fun startRecording(storyId: String, key: String): Boolean {
        stopPlayback()
        abandonRecorder(storyId)
        val tmp = KidsStoryVoice.tempFile(context, storyId, key)
        if (tmp.exists()) tmp.delete()
        return try {
            // Без явных частоты и битрейта MediaRecorder на этом телефоне пишет ~8 кГц.
            val created = try {
                openRecorder(tmp.absolutePath, MediaRecorder.AudioSource.CAMCORDER)
            } catch (_: Exception) {
                openRecorder(tmp.absolutePath, MediaRecorder.AudioSource.MIC)
            }
            created.start()
            recorder = created
            recordingKey = key
            true
        } catch (_: Exception) {
            recordingKey = null
            false
        }
    }

    private fun openRecorder(path: String, source: Int): MediaRecorder {
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            created.setAudioSource(source)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(48_000)
            created.setAudioEncodingBitRate(128_000)
            created.setOutputFile(path)
            created.prepare()
            return created
        } catch (e: Exception) {
            runCatching { created.release() }
            throw e
        }
    }

    fun stopRecording(storyId: String, key: String, save: Boolean): Boolean {
        val active = recorder
        recorder = null
        recordingKey = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        val tmp = KidsStoryVoice.tempFile(context, storyId, key)
        val dest = KidsStoryVoice.file(context, storyId, key)
        if (!save || !tmp.exists() || tmp.length() <= 800L) {
            if (tmp.exists()) tmp.delete()
            return false
        }
        if (dest.exists()) dest.delete()
        return tmp.renameTo(dest)
    }

    fun release() {
        stopPlayback()
        val active = recorder
        recorder = null
        recordingKey = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun abandonRecorder(storyId: String) {
        val key = recordingKey
        val active = recorder
        recorder = null
        recordingKey = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        if (key != null) KidsStoryVoice.tempFile(context, storyId, key).delete()
    }
}
