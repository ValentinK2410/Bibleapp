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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsBibleStory
import com.example.bible.data.KidsUserStoriesStore
import com.example.bible.data.KidsUserStory
import com.example.bible.data.KidsColoringPages
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsSongs
import com.example.bible.data.KidsStoryVoice
import com.example.bible.data.fitsAge
import com.example.bible.data.scenesFor
import com.example.bible.data.testamentLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private fun KidsBibleStory.brush(): Brush = Brush.linearGradient(gradient.map { Color(it) })

@Composable
private fun KidsStoryIllustration(story: KidsBibleStory, modifier: Modifier = Modifier) {
    val image = kidsStoryImage(story.id)
    if (image == 0) {
        KidsStoryPlaceholder(
            emoji = story.emoji,
            gradient = story.gradient.map { Color(it) },
            caption = story.title,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(image),
            contentDescription = story.title,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsStoriesScreen(
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
    onAddStory: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val read by repo.storiesRead.collectAsStateWithLifecycle(initialValue = emptySet())
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val userStories by KidsUserStoriesStore.stories(context).collectAsStateWithLifecycle()
    val mode = family.resolvedMode()
    var filter by rememberSaveable { mutableStateOf("mine") }
    var parentGate by remember { mutableStateOf(false) }
    val readIds = if (family.activeChild() != null) family.activeProgress().stories else read
    val bundled = KidsBibleStories.all.filter { story ->
        val ageOk = filter != "mine" || story.fitsAge(mode)
        val testamentOk = when (filter) {
            "ot" -> story.testament == "OT"
            "nt" -> story.testament == "NT"
            else -> true
        }
        ageOk && testamentOk
    }
    val showUser = filter == "mine" || filter == "all"
    val totalCount = KidsBibleStories.all.size + userStories.size
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                        .background(Brush.linearGradient(KidsNightGradient))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("📖 Слушай и читай", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Прочитано ${readIds.count { id -> KidsBibleStories.byId(id) != null || userStories.any { it.id == id } }} из $totalCount. " +
                            "Сейчас: ${mode.title}. «Для меня» прячет истории другого возраста, «Все» показывает каждую.",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 14.sp,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "mine" to "Для меня",
                        "all" to "Все",
                        "ot" to "ВЗ",
                        "nt" to "НЗ",
                    ).forEach { (key, label) ->
                        Text(
                            label,
                            color = if (filter == key) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (filter == key) Color(0xFF7C3AED) else Color(0xFFF5F3FF))
                                .clickable { filter = key }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Для родителей: добавить историю",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF7C3AED))
                        .clickable { parentGate = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (showUser) {
                items(userStories, key = { it.id }) { story ->
                    UserStoryCard(story, done = story.id in readIds, onClick = { onOpenStory(story.id) })
                }
            }
            if (bundled.isEmpty() && (!showUser || userStories.isEmpty())) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "В этом списке историй нет. Нажмите «Все», чтобы увидеть остальные.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(bundled, key = { it.id }) { story ->
                val done = story.id in readIds
                val cardShape = RoundedCornerShape(24.dp)
                Column(
                    Modifier
                        .kidsLifted(cardShape)
                        .clip(cardShape)
                        .background(story.brush())
                        .clickable { onOpenStory(story.id) }
                        .padding(12.dp),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        KidsStoryIllustration(
                            story,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .kidsPhotoMat(),
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
                        style = KidsTileTitle,
                        maxLines = 2,
                    )
                    Text(
                        listOf(story.reference, story.testamentLabel(), story.age.let {
                            when (it) {
                                "small" -> "3–5"
                                "big" -> "6–10"
                                else -> "3–10"
                            }
                        }).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
    if (parentGate) {
        ParentGateDialog(
            onDismiss = { parentGate = false },
            onPassed = {
                parentGate = false
                onAddStory()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserStoryCard(story: KidsUserStory, done: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cover = story.blocks.firstOrNull { it.isImage }?.let { story.imageFile(context, it) }
    val cardShape = RoundedCornerShape(24.dp)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Column(
        Modifier
            .kidsLifted(cardShape)
            .clip(cardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    showDeleteConfirm = true
                },
            )
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            val bitmap = remember(cover?.absolutePath) { cover?.let { decodeKidsStoryBitmap(it) } }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = story.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .kidsPhotoMat(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                KidsStoryPlaceholder(
                    emoji = "📖",
                    gradient = listOf(Color(0xFF7C3AED), Color(0xFFEC4899)),
                    caption = story.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .kidsPhotoMat(),
                )
            }
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
        Text(story.title, color = Color.White, style = KidsTileTitle, maxLines = 2)
        Text("Своя история", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
    }
    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить историю?", fontWeight = FontWeight.Bold) },
            text = { Text("\"${story.title}\" будет удалена безвозвратно.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            KidsUserStoriesStore.delete(context, story.id)
                        }
                    },
                ) {
                    Text("Удалить", color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsStoryScreen(
    storyId: String,
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenBible: (String, Int, Int) -> Unit = { _, _, _ -> },
    onPlayGame: (String) -> Unit = {},
    onOpenSong: (String) -> Unit = {},
    onOpenColoring: (String) -> Unit = {},
    onEditUserStory: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val userStories by KidsUserStoriesStore.stories(context).collectAsStateWithLifecycle()
    val userStory = userStories.firstOrNull { it.id == storyId }
    if (userStory != null) {
        KidsUserStoryScreen(
            story = userStory,
            onBack = onBack,
            onEdit = { onEditUserStory(userStory.id) },
            onOpenStory = onOpenStory,
        )
        return
    }
    val story = KidsBibleStories.byId(storyId) ?: KidsBibleStories.all.first()
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val read by repo.storiesRead.collectAsStateWithLifecycle(initialValue = emptySet())
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val mode = family.resolvedMode()
    val scenes = story.scenesFor(mode)
    val readIds = if (family.activeChild() != null) family.activeProgress().stories else read
    val noteAt = if (mode == KidsAgeMode.SMALL) story.parentNoteBeforeSceneSmall else story.parentNoteBeforeScene
    var noteSeen by rememberSaveable(story.id) { mutableStateOf(false) }
    var palms by remember(story.id) { mutableIntStateOf(0) }
    var showTips by rememberSaveable(story.id) { mutableStateOf(false) }
    var celebrated by remember(story.id) { mutableStateOf(false) }
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
        if (index in scenes.indices) sceneIndex = index
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

    var pendingImportKey by remember(story.id) { mutableStateOf<String?>(null) }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = pendingImportKey
        pendingImportKey = null
        if (uri == null || key == null) return@rememberLauncherForActivityResult
        if (KidsStoryVoice.importUri(context, story.id, key, uri)) voiceRevision++
        else Toast.makeText(context, "Не удалось взять файл", Toast.LENGTH_SHORT).show()
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
            e.setSpeechRate(audio.speechRate)
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

    val speechRate = if (family.ttsRate > 0f) family.ttsRate else if (mode == KidsAgeMode.SMALL) 0.78f else 0.88f
    LaunchedEffect(speechRate) {
        audio.speechRate = speechRate
        audio.tts?.setSpeechRate(speechRate)
    }
    val scenePause = if (mode == KidsAgeMode.SMALL) 8_000L else 5_200L
    LaunchedEffect(story.id, playbackActive, parentMode, recordingKey, scenePause, scenes.size) {
        if (playbackActive || parentMode || recordingKey != null) return@LaunchedEffect
        val count = scenes.size.coerceAtLeast(1)
        while (true) {
            delay(scenePause)
            sceneIndex = (sceneIndex + 1) % count
        }
    }

    val nextStory = KidsBibleStories.all.getOrNull(KidsBibleStories.all.indexOf(story) + 1)
    val done = story.id in readIds
    val lessonMode = speakingIndex == scenes.size
    val song = KidsSongs.forStory(story.id)
    val coloring = KidsColoringPages.forStory(story.id).firstOrNull()

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
                        .kidsLifted()
                        .clip(KidsCardShape)
                        .background(story.brush())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KidsStoryStage(
                        story,
                        sceneIndex = sceneIndex,
                        lessonMode = lessonMode,
                        sceneCount = scenes.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .kidsPhotoMat(),
                    )
                    if (story.id == "easter" && sceneIndex == 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("🌿".repeat(palms.coerceAtMost(8)), fontSize = 22.sp)
                        Text(
                            "Помаши пальмовой веткой",
                            color = Color(0xFF14532D),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White)
                                .clickable { palms++ }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    if (story.id == "easter" && (lessonMode || sceneIndex == scenes.lastIndex)) {
                        Spacer(Modifier.height(8.dp))
                        Text("🎉 ✨ 🕊️ Христос воскрес!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        scenes.indices.forEach { i ->
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
                                    audio.playAll(story, scenes)
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
                    if (parentMode) {
                        val wholeKey = KidsStoryVoice.WHOLE
                        StoryVoiceRow(
                            saved = hasVoice(wholeKey),
                            recording = recordingKey == wholeKey,
                            parentMode = true,
                            accent = Color.White,
                            onRecord = { if (recordingKey == wholeKey) endRecord(save = true) else requestRecord(wholeKey) },
                            onDelete = {
                                KidsStoryVoice.clear(context, story.id, wholeKey)
                                voiceRevision++
                            },
                            onImport = {
                                pendingImportKey = wholeKey
                                importAudio.launch(arrayOf("audio/*"))
                            },
                        )
                        Text(
                            "Это голос на всю историю. Если его нет, каждую часть читает голос телефона или ваша запись.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (story.parentNote.isNotBlank() && noteAt >= 0 && sceneIndex == noteAt && !noteSeen) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFF7ED))
                            .padding(16.dp),
                    ) {
                        Text("Для родителя, перед этой сценой", fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                        Spacer(Modifier.height(6.dp))
                        Text(story.parentNote, fontSize = 16.sp, lineHeight = 22.sp)
                        TextButton(onClick = { noteSeen = true }) { Text("Можно читать дальше") }
                    }
                }
            }
            itemsIndexed(scenes) { index, text ->
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
                            KidsStoryVoice.clear(context, story.id, key)
                            voiceRevision++
                        },
                        onImport = {
                            pendingImportKey = key
                            importAudio.launch(arrayOf("audio/*"))
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
                                audio.playOne(story, lessonKey, story.lesson, scenes.size)
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
                            KidsStoryVoice.clear(context, story.id, lessonKey)
                            voiceRevision++
                        },
                        onImport = {
                            pendingImportKey = lessonKey
                            importAudio.launch(arrayOf("audio/*"))
                        },
                    )
                }
            }
            if (story.bibleBookId.isNotBlank() && story.bibleChapter > 0) {
                item {
                    StoryLinkRow("Прочитать в Библии", story.reference) {
                        onOpenBible(story.bibleBookId, story.bibleChapter, story.bibleVerse)
                    }
                }
            }
            if (story.quiz.isNotEmpty()) {
                item { StoryLinkRow("Играть", "Вопросы, пазл и пары") { onPlayGame(story.id) } }
            }
            if (song != null) {
                item { StoryLinkRow("Песня", song.title) { onOpenSong(song.id) } }
            }
            if (coloring != null) {
                item { StoryLinkRow("Раскраска", coloring.title) { onOpenColoring(coloring.id) } }
            }
            if (story.parentTips.isNotEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFFECFDF5))
                            .padding(16.dp),
                    ) {
                        Text(
                            "Как обсудить",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF065F46),
                            modifier = Modifier.clickable { showTips = !showTips },
                        )
                        if (showTips) {
                            story.parentTips.forEach { tip ->
                                Spacer(Modifier.height(6.dp))
                                Text("• $tip", color = Color(0xFF064E3B), fontSize = 16.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                }
            }
            if (celebrated) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { KidsRewardStars() }
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
                                Brush.linearGradient(KidsWarmHoney)
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
internal fun StoryVoiceRow(
    saved: Boolean,
    recording: Boolean,
    parentMode: Boolean,
    accent: Color,
    onRecord: () -> Unit,
    onDelete: () -> Unit,
    onImport: (() -> Unit)? = null,
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
            if (onImport != null && !recording) {
                TextButton(onClick = onImport) { Text("С телефона", color = accent) }
            }
            if (saved && !recording) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить запись", tint = accent)
                }
            }
        }
    }
}

@Composable
private fun StoryLinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 28.sp)
    }
}

internal class StoryAudio(private val context: Context) {
    var tts: TextToSpeech? = null
    var speechRate: Float = 0.88f
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

    fun playAll(story: KidsBibleStory, scenes: List<String> = story.paragraphs, includeTitle: Boolean = true) {
        stopPlayback()
        val gen = generation
        onActive(true)
        if (KidsStoryVoice.has(context, story.id, KidsStoryVoice.WHOLE)) {
            playClip(story.id, KidsStoryVoice.WHOLE, story.title, -1, gen) {
                if (gen == generation) {
                    onSpeaking(-1)
                    onActive(false)
                }
            }
            return
        }
        val steps = buildList {
            if (includeTitle) {
                add(Triple("title", story.title, -1))
            }
            scenes.forEachIndexed { index, text ->
                add(Triple(KidsStoryVoice.paragraphKey(index), text, index))
            }
            if (story.lesson.isNotBlank()) {
                add(Triple(KidsStoryVoice.LESSON, story.lesson, scenes.size))
            }
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
        val dest = KidsStoryVoice.recordingDestination(context, storyId, key)
        if (!save || !tmp.exists() || tmp.length() <= 800L) {
            if (tmp.exists()) tmp.delete()
            return false
        }
        KidsStoryVoice.clear(context, storyId, key)
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
