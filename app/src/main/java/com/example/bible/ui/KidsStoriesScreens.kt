package com.example.bible.ui

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.bible.R
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsBibleStory
import kotlinx.coroutines.launch
import java.util.Locale

private fun KidsBibleStory.brush(): Brush = Brush.linearGradient(gradient.map { Color(it) })

private fun kidsStoryImage(id: String): Int = when (id) {
    "creation" -> R.drawable.kids_story_creation
    "noah" -> R.drawable.kids_story_noah
    "abraham" -> R.drawable.kids_story_abraham
    "joseph" -> R.drawable.kids_story_joseph
    "moses-sea" -> R.drawable.kids_story_moses_sea
    "david" -> R.drawable.kids_story_david
    "daniel" -> R.drawable.kids_story_daniel
    "jonah" -> R.drawable.kids_story_jonah
    "nativity" -> R.drawable.kids_story_nativity
    "five-loaves" -> R.drawable.kids_story_five_loaves
    "lost-sheep" -> R.drawable.kids_story_lost_sheep
    else -> R.drawable.kids_story_creation
}

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
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val e = engine ?: return@TextToSpeech
            e.language = Locale.forLanguageTag("ru-RU")
            e.setSpeechRate(0.88f)
            e.setPitch(1.05f)
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val index = utteranceId?.removePrefix("story_")?.toIntOrNull() ?: return
                    mainHandler.post { speakingIndex = index }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "story_end") mainHandler.post { speakingIndex = -1 }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { speakingIndex = -1 }
                }
            })
            tts = e
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts = null
        }
    }

    fun readAloud() {
        val engine = tts ?: return
        engine.stop()
        engine.speak(story.title, TextToSpeech.QUEUE_FLUSH, null, "story_title")
        story.paragraphs.forEachIndexed { index, text ->
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, "story_$index")
        }
        engine.speak(story.lesson, TextToSpeech.QUEUE_ADD, null, "story_end")
    }

    val nextStory = KidsBibleStories.all.getOrNull(KidsBibleStories.all.indexOf(story) + 1)
    val done = story.id in read
    val playing = speakingIndex >= 0

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(story.title, fontWeight = FontWeight.ExtraBold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                    KidsStoryIllustration(
                        story,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(20.dp)),
                    )
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
                                if (playing) {
                                    tts?.stop()
                                    speakingIndex = -1
                                } else {
                                    readAloud()
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = Color(story.gradient.first())
                        Icon(
                            if (playing) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = tint,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (playing) "Стоп" else "Читать вслух",
                            color = tint,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                        )
                    }
                }
            }
            itemsIndexed(story.paragraphs) { index, text ->
                val active = index == speakingIndex
                val bg by animateColorAsState(
                    if (active) Color(story.gradient.first()).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
                    label = "para",
                )
                Text(
                    text,
                    fontSize = 21.sp,
                    lineHeight = 30.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .border(
                            width = if (active) 2.dp else 0.dp,
                            color = if (active) Color(story.gradient.first()) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "story_$index") }
                        .padding(16.dp),
                )
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(16.dp),
                ) {
                    Text("💡 Чему учит история", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF92400E))
                    Spacer(Modifier.height(6.dp))
                    Text(story.lesson, fontSize = 19.sp, lineHeight = 27.sp, color = Color(0xFF78350F))
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
