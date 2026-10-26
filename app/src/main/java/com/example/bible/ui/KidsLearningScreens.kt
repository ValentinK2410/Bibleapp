package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsHero
import com.example.bible.data.KidsHeroes
import com.example.bible.data.KidsMemoryVerses
import com.example.bible.data.KidsPrayer
import com.example.bible.data.KidsPrayers
import com.example.bible.data.KidsSong
import com.example.bible.data.KidsSongs
import com.example.bible.data.KidsStoryVoice
import com.example.bible.data.KidsValueLesson
import com.example.bible.data.KidsValues
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun KidsPrayersScreen(onBack: () -> Unit, onOpenBible: (String, Int, Int) -> Unit) {
    var open by remember { mutableStateOf<KidsPrayer?>(null) }
    if (open != null) {
        KidsPrayerDetail(open!!, onBack = { open = null }, onOpenBible = onOpenBible)
        return
    }
    KidsSectionScaffold("Молитвы", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Короткие молитвы своими словами. «Отче наш» — по Синодальному переводу. Свою молитву можно записать голосом.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(KidsPrayers.all, key = { it.id }) { prayer ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(prayer.gradient.map { Color(it) }))
                        .clickable { open = prayer }
                        .padding(16.dp),
                ) {
                    Text("${prayer.emoji}  ${prayer.title}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(prayer.time, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun KidsPrayerDetail(
    prayer: KidsPrayer,
    onBack: () -> Unit,
    onOpenBible: (String, Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val speak = rememberKidsSpeaker(if (family.resolvedMode() == KidsAgeMode.SMALL) 0.8f else 0.92f)
    var together by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { PrayerRecorder(context.applicationContext) }
    val voiceFile = KidsStoryVoice.file(context, "prayer_${family.bucketId()}", prayer.id)
    var hasVoice by remember(voiceFile.path) { mutableStateOf(voiceFile.exists() && voiceFile.length() > 800L) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recording = recorder.start(voiceFile)
        } else {
            Toast.makeText(context, "Нужен микрофон, чтобы записать молитву", Toast.LENGTH_SHORT).show()
        }
    }
    val phrase = prayer.lines[index.coerceIn(0, prayer.lines.lastIndex)]
    KidsSectionScaffold(prayer.title, onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val art = kidsPrayerImage(prayer.id)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .kidsLifted()
                        .clip(KidsCardShape)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), KidsCardShape),
                ) {
                    if (art != 0) {
                        Image(
                            painter = painterResource(art),
                            contentDescription = prayer.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(prayer.gradient.map { Color(it) })),
                        )
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color(0xCC1F2937))
                            .padding(16.dp),
                    ) {
                        Text(phrase.text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                        if (phrase.hint.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(phrase.hint, color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        speak(phrase.text)
                        if (!together && index < prayer.lines.lastIndex) index++
                    }) { Text(if (together) "Слушать" else "Дальше") }
                    if (together) {
                        Button(onClick = { if (index < prayer.lines.lastIndex) index++ }) { Text("Повторил") }
                    }
                    TextButton(onClick = { together = !together }) {
                        Text(if (together) "Обычный режим" else "Молимся вместе")
                    }
                }
            }
            item {
                Button(onClick = {
                    index = 0
                    speak(prayer.fullText)
                }) { Text("Прочитать всю молитву") }
            }
            if (prayer.id == "evening") {
                item {
                    Button(onClick = { scope.launch { KidsFamilyStore.markEveningPrayer(context) } }) {
                        Text("Мы помолились вечером")
                    }
                }
            }
            if (prayer.scripture) {
                item {
                    TextButton(onClick = { onOpenBible("matthew", 6, 9) }) { Text("Открыть в Библии · Мф 6:9") }
                }
            }
            item {
                Text("Моя молитва", fontWeight = FontWeight.Bold)
                Text("Запись хранится только на этом телефоне.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Row {
                    Button(onClick = {
                        if (recording) {
                            recorder.stop(save = true)
                            recording = false
                            hasVoice = voiceFile.exists()
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            recording = recorder.start(voiceFile)
                        } else {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) { Text(if (recording) "Сохранить" else "Записать голосом") }
                    if (hasVoice) {
                        TextButton(onClick = { speak("Запись сохранена на этом телефоне.") }) {
                            Text("Запись сохранена")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KidsSongsScreen(onBack: () -> Unit, initialId: String? = null) {
    var open by remember { mutableStateOf(initialId?.let { KidsSongs.byId(it) }) }
    if (open != null) {
        KidsSongDetail(open!!, onBack = { open = null })
        return
    }
    KidsSectionScaffold("Песни", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Короткие песни для семьи. Мелодии пока нет — строки читает голос телефона.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(KidsSongs.all, key = { it.id }) { song ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFDBEAFE))
                        .clickable { open = song }
                        .padding(16.dp),
                ) {
                    Text("${song.emoji}  ${song.title}", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(song.theme, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun KidsSongDetail(song: KidsSong, onBack: () -> Unit) {
    val context = LocalContext.current
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val young = family.resolvedMode() == KidsAgeMode.SMALL
    val speak = rememberKidsSpeaker(0.9f)
    val scope = rememberCoroutineScope()
    var line by remember { mutableIntStateOf(-1) }
    val lines = if (young) song.chorus else song.lines
    KidsSectionScaffold(song.title, onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("${song.emoji} ${song.title}", fontWeight = FontWeight.Black, fontSize = 28.sp)
                Text(
                    "Мелодии пока нет — строки читает голос телефона.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        lines.forEachIndexed { index, item ->
                            line = index
                            speak(item.text)
                            delay(900L + item.text.length * 70L)
                        }
                        line = -1
                    }
                }) { Text(if (young) "Петь припев" else "Петь") }
            }
            items(lines.size) { index ->
                val item = lines[index]
                val active = index == line
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) Color(0xFFFDE68A) else Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .clickable {
                            line = index
                            speak(item.text)
                        }
                        .padding(14.dp),
                ) {
                    Text(item.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (item.motion.isNotBlank()) Text("Движение: ${item.motion}", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun KidsHeroesScreen(onBack: () -> Unit, onOpenStory: (String) -> Unit, onOpenGenealogy: () -> Unit) {
    val context = LocalContext.current
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val read = family.activeProgress().stories
    var open by remember { mutableStateOf<KidsHero?>(null) }
    if (open != null) {
        val hero = open!!
        val unlocked = hero.storyIds.any { it in read }
        KidsSectionScaffold(hero.name, { open = null }) { padding ->
            LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val art = kidsHeroImage(hero.id)
                    if (art != 0) {
                        Image(
                            painter = painterResource(art),
                            contentDescription = hero.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .kidsPhotoMat(),
                        )
                    } else {
                        Text(hero.emoji, fontSize = 64.sp, lineHeight = 72.sp)
                    }
                    Text(if (unlocked) hero.who else "Карточка откроется после истории", fontSize = 18.sp)
                    if (!unlocked) {
                        HeroUnlockStoryButton(hero, onOpenStory)
                    }
                }
                if (unlocked) {
                    items(hero.facts) { Text("• $it", fontSize = 18.sp) }
                    item { Text("Учит: ${hero.lesson}", fontWeight = FontWeight.Bold) }
                    items(hero.storyIds) { id ->
                        val title = KidsBibleStories.byId(id)?.title ?: "История"
                        TextButton(onClick = { onOpenStory(id) }) { Text(title) }
                    }
                    if (family.resolvedMode() == KidsAgeMode.BIG && hero.genealogyId != null) {
                        item { Button(onClick = onOpenGenealogy) { Text("Семья героя") } }
                    }
                }
            }
        }
        return
    }
    KidsSectionScaffold("Герои Библии", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Собери коллекцию: карточка открывается, когда прочитана хотя бы одна её история.") }
            items(KidsHeroes.all, key = { it.id }) { hero ->
                val unlocked = hero.storyIds.any { it in read }
                val heroShape = RoundedCornerShape(24.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .kidsLifted(heroShape)
                        .clip(heroShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(hero.gradient.map { Color(it) }))
                        .clickable { open = hero }
                        .padding(14.dp),
                ) {
                    val art = kidsHeroImage(hero.id)
                    if (art != 0 && unlocked) {
                        Image(
                            painter = painterResource(art),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .kidsPhotoMat(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("${if (unlocked) "" else "🔒 "}${hero.name}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(if (unlocked) hero.who else "Ещё закрыто", color = Color.White.copy(alpha = 0.9f))
                    if (!unlocked) {
                        HeroUnlockStoryButton(hero, onOpenStory, onDark = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroUnlockStoryButton(
    hero: KidsHero,
    onOpenStory: (String) -> Unit,
    onDark: Boolean = false,
) {
    val storyId = hero.storyIds.firstOrNull { KidsBibleStories.byId(it) != null } ?: return
    val title = KidsBibleStories.byId(storyId)?.title ?: return
    TextButton(onClick = { onOpenStory(storyId) }) {
        Text(
            "Читать «$title»",
            color = if (onDark) Color.White else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun KidsValuesScreen(onBack: () -> Unit, onOpenStory: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<KidsValueLesson?>(null) }
    if (open != null) {
        val lesson = open!!
        val done = lesson.id in family.activeProgress().valuesDone
        KidsSectionScaffold(lesson.title, { open = null }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(lesson.emoji, fontSize = 48.sp)
                Text(lesson.question, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Задание на день: ${lesson.task}", fontSize = 18.sp)
                lesson.storyIds.forEach { id ->
                    val title = KidsBibleStories.byId(id)?.title
                    TextButton(onClick = { onOpenStory(id) }) {
                        Text(if (title != null) "История: $title" else "Открыть историю")
                    }
                }
                lesson.verseId?.let { id ->
                    KidsMemoryVerses.byId(id)?.let { verse ->
                        Text("${verse.reference}: ${verse.gameText}")
                    }
                }
                Button(
                    onClick = { scope.launch { KidsFamilyStore.recordValue(context, lesson.id, lesson.title) } },
                    enabled = !done,
                ) { Text(if (done) "Родитель уже отметил" else "Родитель: задание сделано ⭐") }
            }
        }
        return
    }
    KidsSectionScaffold("Ценности", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Короткая история, вопрос и дело на день. Особую звезду отмечает родитель.") }
            items(KidsValues.all, key = { it.id }) { lesson ->
                val done = lesson.id in family.activeProgress().valuesDone
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(lesson.gradient.map { Color(it) }))
                        .clickable { open = lesson }
                        .padding(16.dp),
                ) {
                    Text("${lesson.emoji}  ${lesson.title}${if (done) "  ✅" else ""}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(lesson.question, color = Color.White.copy(alpha = 0.92f))
                }
            }
        }
    }
}

private class PrayerRecorder(private val context: android.content.Context) {
    private var recorder: MediaRecorder? = null
    private var tmp: File? = null
    private var dest: File? = null

    fun start(target: File): Boolean {
        stop(save = false)
        val file = File(target.absolutePath + ".tmp")
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        return try {
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setOutputFile(file.absolutePath)
            created.prepare()
            created.start()
            recorder = created
            tmp = file
            dest = target
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop(save: Boolean) {
        val active = recorder
        recorder = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        val source = tmp
        val target = dest
        tmp = null
        if (!save || source == null || target == null || !source.exists() || source.length() <= 800L) {
            source?.delete()
            return
        }
        if (target.exists()) target.delete()
        source.renameTo(target)
    }
}
