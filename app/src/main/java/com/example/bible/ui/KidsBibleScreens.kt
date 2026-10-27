package com.example.bible.ui

import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.CanonBookGroup
import com.example.bible.data.KidsBibleAge
import com.example.bible.data.KidsBibleBookRow
import com.example.bible.data.KidsBibleCatalog
import com.example.bible.data.KidsBibleNarration
import com.example.bible.data.KidsBiblePin
import com.example.bible.data.KidsBiblePinStore
import com.example.bible.data.KidsBibleReading
import kotlinx.coroutines.delay

private val BibleInk = Color(0xFF3B2418)
private val BibleCard = Color(0xFFFFF8F1)
private val BibleLine = Color(0xFFE7D3C1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KidsBibleShelfScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pinSet by remember { mutableStateOf(KidsBiblePinStore.isSet(context)) }
    var settings by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var age by remember { mutableStateOf(KidsBiblePinStore.ageForChild(context)) }
    var maySwitch by remember { mutableStateOf(KidsBiblePinStore.childMaySwitch(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val stillSet = KidsBiblePinStore.isSet(context)
                pinSet = stillSet
                if (!stillSet) settings = false
                age = KidsBiblePinStore.ageForChild(context)
                maySwitch = KidsBiblePinStore.childMaySwitch(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val stories = remember { KidsBibleCatalog.load(context) }
    val shelf = remember(stories) { KidsBibleCatalog.shelf(stories) }
    val read = remember(pinSet, settings) { KidsBiblePinStore.readIds(context) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Детская Библия",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (pinSet && !settings) {
                        IconButton(onClick = { askPin = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки родителя")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !pinSet -> KidsBiblePinSetup(
                modifier = Modifier.padding(padding),
                onSaved = { pinSet = true },
            )
            settings -> KidsBibleParentSettings(
                modifier = Modifier.padding(padding),
                onClose = {
                    settings = false
                    age = KidsBiblePinStore.ageForChild(context)
                    maySwitch = KidsBiblePinStore.childMaySwitch(context)
                },
                onReset = {
                    settings = false
                    pinSet = false
                },
            )
            else -> KidsBibleShelf(
                shelf = shelf,
                read = read,
                age = age,
                maySwitch = maySwitch,
                modifier = Modifier.padding(padding),
                onOpenBook = onOpenBook,
                onPickAge = { picked ->
                    KidsBiblePinStore.setChosenAge(context, picked)
                    age = picked
                },
            )
        }
    }
    if (askPin) {
        KidsBiblePinDialog(
            title = "Код родителя",
            onDismiss = { askPin = false },
            onPassed = {
                askPin = false
                settings = true
            },
            onRecovered = {
                askPin = false
                settings = false
                pinSet = false
            },
        )
    }
}

@Composable
private fun KidsBiblePinSetup(modifier: Modifier, onSaved: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<KidsBiblePin.SetupProblem?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Сначала код родителя",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            color = BibleInk,
        )
        Text(
            "Без кода «Детская Библия» не открывается. Пропустить нельзя. Код из 4–6 цифр защищает настройки. Подсказка нужна, если код забудется: тогда прогресс этого раздела сотрётся, а настройки ребёнку не откроются.",
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = BibleInk,
        )
        PinField("Код", pin) { pin = it }
        PinField("Код ещё раз", repeat) { repeat = it }
        OutlinedTextField(
            value = hint,
            onValueChange = { hint = it },
            label = { Text("Подсказка, например: девичья фамилия бабушки", fontFamily = FontFamily.SansSerif, lineHeight = 18.sp) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Ответ на подсказку", fontFamily = FontFamily.SansSerif, lineHeight = 18.sp) },
            modifier = Modifier.fillMaxWidth(),
        )
        problem?.let {
            Text(
                when (it) {
                    KidsBiblePin.SetupProblem.PIN -> "Код — от 4 до 6 цифр."
                    KidsBiblePin.SetupProblem.MISMATCH -> "Коды не совпали."
                    KidsBiblePin.SetupProblem.HINT -> "Напишите подсказку, по которой вспомните код."
                    KidsBiblePin.SetupProblem.ANSWER -> "Ответ на подсказку слишком короткий."
                },
                color = Color(0xFF9F1239),
                fontFamily = FontFamily.SansSerif,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
        }
        Button(
            onClick = {
                val issue = KidsBiblePin.setupProblem(pin, repeat, hint, answer)
                problem = issue
                if (issue == null) {
                    KidsBiblePinStore.saveNew(context, pin, hint, answer)
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить код и открыть полку", fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() }.take(KidsBiblePin.MAX_LEN)) },
        label = { Text(label, fontFamily = FontFamily.SansSerif, lineHeight = 18.sp) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun KidsBibleShelf(
    shelf: List<KidsBibleBookRow>,
    read: Set<String>,
    age: KidsBibleAge,
    maySwitch: Boolean,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
    onPickAge: (KidsBibleAge) -> Unit,
) {
    val groups = shelf.groupBy { it.book.group }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Книги по порядку, как в Библии. Где история уже есть — можно читать и слушать. Остальные помечены «скоро».",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = BibleInk,
                )
                KidsBibleAgeRow(age = age, enabled = maySwitch, onPick = onPickAge)
            }
        }
        CanonBookGroup.entries.forEach { group ->
            val books = groups[group].orEmpty()
            if (books.isEmpty()) return@forEach
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    KidsBibleCatalog.groupTitle(group),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = BibleInk,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(books, key = { it.book.id }) { row ->
                val done = row.stories.count { it.id in read }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (row.comingSoon) Color(0xFFF3E6D8) else BibleCard)
                        .border(1.dp, BibleLine, RoundedCornerShape(16.dp))
                        .clickable { onOpenBook(row.book.id) }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        row.book.nameRu,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = BibleInk,
                    )
                    Text(
                        if (row.comingSoon) "скоро" else {
                            val n = row.stories.size
                            val kind = if (row.stories.all { it.isOverview }) "обзор" else "$n"
                            if (done > 0) "$kind · $done прочитано" else kind
                        },
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (row.comingSoon) Color(0xFF9A6B45) else Color(0xFF7C5A40),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KidsBibleBookScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
) {
    val context = LocalContext.current
    val stories = remember { KidsBibleCatalog.load(context) }
    val row = remember(stories, bookId) { KidsBibleCatalog.shelf(stories).find { it.book.id == bookId } }
    val read = remember { KidsBiblePinStore.readIds(context) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        row?.book?.nameRu ?: "Книга",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (row == null || row.comingSoon) {
            Column(Modifier.padding(padding).padding(20.dp)) {
                Text(
                    "Скоро",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    color = BibleInk,
                )
                Text(
                    "Эта книга уже на полке, в своём месте. История для неё ещё готовится.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = BibleInk,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.stories.forEach { story ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(BibleCard)
                            .border(1.dp, BibleLine, RoundedCornerShape(18.dp))
                            .clickable { onOpenStory(story.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                story.title,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                color = BibleInk,
                            )
                            Text(
                                story.refs,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFF7C5A40),
                            )
                        }
                        if (story.id in read) {
                            Text("✓", fontSize = 18.sp, lineHeight = 22.sp, color = Color(0xFF3F6B4A))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KidsBibleStoryScreen(storyId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val story = remember { KidsBibleCatalog.load(context).find { it.id == storyId } }
    if (story == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("История") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            Text("Этой истории нет.", modifier = Modifier.padding(padding).padding(20.dp))
        }
        return
    }
    var age by remember { mutableStateOf(KidsBiblePinStore.ageForChild(context)) }
    val maySwitch = remember { KidsBiblePinStore.childMaySwitch(context) }
    val track = story.track(age)
    var activePart by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var audioError by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(story.id, age) {
        val mp = MediaPlayer()
        player = mp
        ready = false
        playing = false
        position = 0
        audioError = false
        activePart = null
        try {
            val family = story.familyAudioAsset(age)
            val hasFamily = runCatching {
                context.assets.openFd(family).use { }
                true
            }.getOrDefault(false)
            val asset = story.narrationAsset(age, if (hasFamily) KidsBibleNarration.Family else KidsBibleNarration.App)
            val afd = context.assets.openFd(asset)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setOnPreparedListener {
                ready = true
                duration = it.duration
                // Скорость здесь не задаём: setPlaybackParams у готового плеера сам запускает звук,
                // а флаг playing остаётся false — шкала стоит. Скорость применяется после start().
            }
            mp.setOnCompletionListener {
                playing = false
                position = duration
                KidsBiblePinStore.markRead(context, story.id)
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            audioError = true
        }
        onDispose {
            player = null
            mp.release()
        }
    }
    LaunchedEffect(ready, player) {
        val mp = player
        while (ready && mp != null && player === mp) {
            val ok = runCatching {
                playing = mp.isPlaying
                position = mp.currentPosition
                duration = mp.duration.coerceAtLeast(duration)
            }.isSuccess
            if (!ok) break
            activePart = track.cues.firstOrNull { position in it.startMs until it.endMs }?.part
            delay(if (playing) 100 else 250)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        story.title,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            KidsBiblePlayerBar(
                playing = playing,
                ready = ready && !audioError,
                position = position,
                duration = duration,
                speed = speed,
                error = audioError,
                onToggle = {
                    val mp = player
                    if (!ready || mp == null) return@KidsBiblePlayerBar
                    if (playing) {
                        mp.pause()
                        playing = false
                    } else {
                        mp.start()
                        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed) }
                        playing = true
                    }
                },
                onSeek = { ms ->
                    val mp = player
                    if (!ready || mp == null) return@KidsBiblePlayerBar
                    mp.seekTo(ms)
                    position = ms
                    activePart = track.cues.firstOrNull { ms in it.startMs until it.endMs }?.part
                },
                onSpeed = { picked ->
                    speed = picked
                    val mp = player
                    // На паузе только запоминаем: setPlaybackParams запустил бы звук.
                    if (ready && mp != null) runCatching {
                        if (mp.isPlaying) mp.playbackParams = mp.playbackParams.setSpeed(picked)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StoryArt(story)
            KidsBibleAgeRow(
                age = age,
                enabled = maySwitch,
                onPick = { picked ->
                    KidsBiblePinStore.setChosenAge(context, picked)
                    age = picked
                },
            )
            Text(
                "${story.refs} · ${story.kindLabel()} · около ${minutesLabel(track.cues)}",
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(0xFF9A4E1C),
            )
            Text(
                story.title,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                color = BibleInk,
            )
            track.paragraphs.forEachIndexed { index, paragraph ->
                val on = activePart == "p$index"
                Text(
                    paragraph,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    color = BibleInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) Color(0xFFFFE7C2) else Color.Transparent)
                        .padding(8.dp),
                )
            }
            VerseCard(story.keyVerse, story.keyVerseRef, activePart == "verse")
            Block("Что это значит?", track.meaning, activePart == "meaning")
            Block("Как это связано с Иисусом", track.christLink, activePart == "christ")
            if (track.questions.isNotEmpty()) {
                Text("Вопросы", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, color = BibleInk)
                track.questions.forEach { q ->
                    Text("• $q", fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp, color = BibleInk)
                }
            }
            Block("Молитва", track.prayer, activePart == "prayer")
            Block("Что мы можем сделать", track.activity, activePart == "do")
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StoryArt(story: KidsBibleReading) {
    val context = LocalContext.current
    val res = remember(story.art) {
        context.resources.getIdentifier(story.art, "drawable", context.packageName)
    }
    val shape = RoundedCornerShape(20.dp)
    if (res != 0) {
        androidx.compose.foundation.Image(
            painter = painterResource(res),
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(shape),
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFFE7C48A), Color(0xFFC46A3A)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Свет над историей",
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun VerseCard(text: String, ref: String, active: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Color(0xFFFFE7C2) else Color(0xFFFFF1E4))
            .padding(14.dp),
    ) {
        Text("Ключевой стих", fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF9A4E1C))
        Text("«$text»", fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 24.sp, color = BibleInk, modifier = Modifier.padding(top = 4.dp))
        Text(ref, fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF7C5A40), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Block(title: String, body: String, active: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0xFFFFE7C2) else Color.Transparent)
            .padding(8.dp),
    ) {
        Text(title, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, color = BibleInk)
        Text(body, fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp, color = BibleInk, modifier = Modifier.padding(top = 4.dp))
    }
}

internal val KIDS_BIBLE_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

internal fun kidsSpeedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
    return text.replace('.', ',') + "×"
}

@Composable
private fun KidsBiblePlayerBar(
    playing: Boolean,
    ready: Boolean,
    position: Int,
    duration: Int,
    speed: Float,
    error: Boolean,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
) {
    var speedMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(BibleCard).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (error) {
            Text(
                "Голос этой истории не открылся.",
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = BibleInk,
            )
            return@Column
        }
        if (!ready) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle, enabled = ready) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Пауза" else "Слушать")
            }
            Slider(
                value = position.coerceIn(0, duration.coerceAtLeast(1)).toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(onClick = { speedMenu = true }, enabled = ready) {
                    Text(kidsSpeedLabel(speed), fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 18.sp)
                }
                androidx.compose.material3.DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    KIDS_BIBLE_SPEEDS.forEach { s ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    kidsSpeedLabel(s),
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = if (s == speed) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                )
                            },
                            onClick = { speedMenu = false; onSpeed(s) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsBiblePinDialog(
    title: String,
    onDismiss: () -> Unit,
    onPassed: () -> Unit,
    onRecovered: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var forgot by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var gate by remember { mutableStateOf(KidsBiblePinStore.gate(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var wrong by remember { mutableStateOf(false) }
    val hint = remember { KidsBiblePinStore.record(context)?.hint.orEmpty() }
    LaunchedEffect(gate.lockedUntilMs) {
        while (KidsBiblePin.locked(gate, System.currentTimeMillis())) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val locked = KidsBiblePin.locked(gate, now)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 24.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!forgot) {
                    PinField("Код", pin) { pin = it }
                    TextButton(onClick = { forgot = true; wrong = false }) {
                        Text("Забыли код?", fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 18.sp)
                    }
                } else {
                    Text(
                        "Подсказка: $hint. Верный ответ стирает код и отметки «прочитано». Настройки ребёнку не откроются.",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = BibleInk,
                    )
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Ответ", fontFamily = FontFamily.SansSerif, lineHeight = 18.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                if (locked) {
                    val left = ((gate.lockedUntilMs - now) / 1000L).coerceAtLeast(1)
                    Text("Слишком много попыток. Подождите $left с.", fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 18.sp)
                } else if (wrong) {
                    Text(
                        if (forgot) "Ответ не подошёл." else "Код не подошёл.",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF9F1239),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !locked,
                onClick = {
                    val record = KidsBiblePinStore.record(context)
                    val stamp = System.currentTimeMillis()
                    if (!forgot && record != null && KidsBiblePin.matchesPin(record, pin)) {
                        KidsBiblePinStore.noteSuccess(context)
                        onPassed()
                    } else if (forgot && record != null && KidsBiblePin.matchesHint(record, answer)) {
                        KidsBiblePinStore.resetSection(context)
                        onRecovered()
                    } else {
                        gate = KidsBiblePinStore.noteFail(context, stamp)
                        now = stamp
                        wrong = true
                        pin = ""
                        answer = ""
                    }
                },
            ) { Text(if (forgot) "Сбросить" else "Открыть", fontFamily = FontFamily.SansSerif) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", fontFamily = FontFamily.SansSerif) }
        },
    )
}

@Composable
private fun KidsBibleParentSettings(modifier: Modifier, onClose: () -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    val hint = remember { KidsBiblePinStore.record(context)?.hint.orEmpty() }
    var answer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Column(
        modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки родителя", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp, color = BibleInk)
        Text(
            "Пилотные истории, картинки и голос уже лежат в приложении. Ребёнок слушает их без сети. Следующие пакеты книг можно будет скачать отдельно, и только после этого кода. Позже вместо голоса приложения можно будет положить запись мамы или папы — отдельная дорожка уже предусмотрена.",
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = BibleInk,
        )
        var defaultAge by remember { mutableStateOf(KidsBiblePinStore.defaultAge(context)) }
        var maySwitch by remember { mutableStateOf(KidsBiblePinStore.childMaySwitch(context)) }
        Text("Возраст, с которого открывается история", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, color = BibleInk)
        KidsBibleAgeRow(
            age = defaultAge,
            enabled = true,
            onPick = {
                KidsBiblePinStore.setDefaultAge(context, it)
                defaultAge = it
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Ребёнок может переключить возраст",
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = BibleInk,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = maySwitch,
                onCheckedChange = {
                    KidsBiblePinStore.setChildMaySwitch(context, it)
                    maySwitch = it
                },
            )
        }
        Text(
            "Подсказка для восстановления: $hint",
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = BibleInk,
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Ответ на подсказку", fontFamily = FontFamily.SansSerif, lineHeight = 18.sp) },
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let {
            Text(it, fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp, color = Color(0xFF9F1239))
        }
        Button(
            onClick = {
                val record = KidsBiblePinStore.record(context)
                if (record != null && KidsBiblePin.matchesHint(record, answer)) {
                    KidsBiblePinStore.resetSection(context)
                    onReset()
                } else {
                    message = "Ответ не подошёл. Код и настройки остаются закрытыми."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сбросить код и прогресс раздела", fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp)
        }
        TextButton(onClick = onClose) {
            Text("Назад к полке", fontFamily = FontFamily.SansSerif)
        }
    }
}

private fun KidsBibleReading.kindLabel(): String = if (isOverview) "обзор" else "история"

private fun minutesLabel(cues: List<com.example.bible.data.KidsBibleCue>): String {
    val sec = cues.lastOrNull()?.endMs?.div(1000) ?: 60
    val min = (sec / 60).coerceAtLeast(1)
    return "$min мин"
}

@Composable
private fun KidsBibleAgeRow(age: KidsBibleAge, enabled: Boolean, onPick: (KidsBibleAge) -> Unit) {
    if (!enabled) {
        Text(
            "Возраст ${age.label}. Сменить его может родитель.",
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = Color(0xFF7C5A40),
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidsBibleAge.entries.forEach { option ->
            val selected = option == age
            if (selected) {
                Button(onClick = { onPick(option) }) {
                    Text(option.label, fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 18.sp)
                }
            } else {
                OutlinedButton(onClick = { onPick(option) }) {
                    Text(option.label, fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}
