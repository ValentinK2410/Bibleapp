package com.example.bible.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clipToBounds
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
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsStoryQuiz
import com.example.bible.data.kidsStarsForRun
import kotlinx.coroutines.launch

@Composable
fun KidsStoryGameScreen(storyId: String, onBack: () -> Unit) {
    val story = KidsBibleStories.byId(storyId) ?: KidsBibleStories.all.first()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val mode = family.resolvedMode()
    var screen by remember { mutableStateOf("menu") }
    var errors by remember { mutableIntStateOf(0) }
    var hints by remember { mutableIntStateOf(0) }
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    fun finish() {
        val stars = kidsStarsForRun(errors, hints)
        scope.launch {
            val prev = KidsFamilyStore.peek(context).activeProgress().miniStars[story.id] ?: 0
            KidsFamilyStore.recordMiniStars(context, story.id, stars)
            val gained = (stars - prev).coerceAtLeast(0)
            if (gained > 0) repo.addPoints(gained * 10)
        }
        screen = "done"
    }
    KidsSectionScaffold("Игра: ${story.title}", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (screen) {
                "menu" -> {
                    Text("${story.emoji} Закрепи историю", fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Button(onClick = { screen = "next" }, modifier = Modifier.fillMaxWidth()) { Text("Что было дальше?") }
                    Button(onClick = { screen = "who" }, modifier = Modifier.fillMaxWidth()) { Text("Кто это?") }
                    Button(onClick = { screen = "puzzle" }, modifier = Modifier.fillMaxWidth()) { Text("Пазл") }
                    Button(onClick = { screen = "memory" }, modifier = Modifier.fillMaxWidth()) { Text("Найди пару") }
                }
                "next", "who" -> QuizPlay(
                    questions = story.quiz.filter { if (screen == "next") it.kind == "next" else it.kind != "next" }
                        .ifEmpty { story.quiz },
                    onMiss = { errors++ },
                    onHint = { hints++ },
                    onDone = { finish() },
                )
                "puzzle" -> PuzzlePlay(story.id, story.emoji, mode, onDone = { finish() })
                "memory" -> MemoryPlay(story.emoji, mode, onDone = { finish() }, onMiss = { errors++ })
                else -> {
                    val stars = kidsStarsForRun(errors, hints)
                    KidsRewardStars(stars)
                    Text("Готово", fontWeight = FontWeight.Black, fontSize = 32.sp)
                    Text("Ошибок: $errors. Ошибка не страшна — можно играть ещё.")
                    Button(onClick = onBack) { Text("К истории") }
                }
            }
        }
    }
}

@Composable
private fun QuizPlay(
    questions: List<KidsStoryQuiz>,
    onMiss: () -> Unit,
    onHint: () -> Unit,
    onDone: () -> Unit,
) {
    if (questions.isEmpty()) {
        Text("Для этой истории вопросы ещё готовятся.")
        Button(onClick = onDone) { Text("Дальше") }
        return
    }
    var index by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    val question = questions[index.coerceIn(0, questions.lastIndex)]
    val options = remember(question.question) { question.options.shuffled() }
    Text(question.question, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    options.forEach { option ->
        val correct = option == question.options[question.answer]
        Button(
            onClick = {
                if (locked) return@Button
                if (!correct) onMiss()
                locked = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(option) }
    }
    if (locked) {
        Button(onClick = {
            if (index + 1 >= questions.size) onDone() else {
                index++
                locked = false
            }
        }) { Text("Дальше") }
        Button(onClick = { onHint() }) { Text("Подсказка: ${question.options[question.answer]}") }
    }
}

@Composable
private fun PuzzlePlay(storyId: String, emoji: String, mode: KidsAgeMode, onDone: () -> Unit) {
    val grid = if (mode == KidsAgeMode.SMALL) 2 else 3
    val count = grid * grid
    var tiles by remember { mutableStateOf((0 until count).toList().shuffled()) }
    var selected by remember { mutableIntStateOf(-1) }
    val image = kidsStoryParagraphImage(storyId, 0)
    Text(if (image == 0) "Пазл-заглушка: собери цветные части по порядку. Картинки kids_story_${storyId}_0.webp ещё нет." else "Собери картинку.")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(grid) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(grid) { col ->
                    val pos = row * grid + col
                    val tile = tiles[pos]
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFDE68A))
                            .border(if (selected == pos) 3.dp else 0.dp, Color(0xFFEA580C), RoundedCornerShape(12.dp))
                            .clickable {
                                if (selected < 0) selected = pos
                                else if (selected != pos) {
                                    val next = tiles.toMutableList()
                                    val tmp = next[selected]
                                    next[selected] = next[pos]
                                    next[pos] = tmp
                                    tiles = next
                                    selected = -1
                                    if (next.indices.all { next[it] == it }) onDone()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (image != 0) {
                            PuzzleCrop(image, tile % grid, tile / grid, grid)
                        } else {
                            Text("$emoji\n${tile + 1}", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzleCrop(image: Int, col: Int, row: Int, grid: Int) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .requiredSize(140.dp * grid)
                .offset(x = (-140).dp * col, y = (-140).dp * row),
        )
    }
}

@Composable
private fun MemoryPlay(emoji: String, mode: KidsAgeMode, onDone: () -> Unit, onMiss: () -> Unit) {
    val pairs = remember(emoji, mode) {
        val base = listOf(emoji to "герой", "⭐" to "звезда", "❤️" to "любовь", "📖" to "книга")
        val use = if (mode == KidsAgeMode.SMALL) base.take(3) else base
        use.flatMap { (icon, name) -> listOf(icon to name, icon to name) }.shuffled()
    }
    var open by remember { mutableStateOf(setOf<Int>()) }
    var matched by remember { mutableStateOf(setOf<Int>()) }
    var first by remember { mutableIntStateOf(-1) }
    LazyVerticalGrid(columns = GridCells.Fixed(if (mode == KidsAgeMode.SMALL) 3 else 4), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(pairs.size) { index ->
            val shown = index in open || index in matched
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (shown) Color(0xFFFEF3C7) else Color(0xFF6366F1))
                    .clickable {
                        if (index in matched || index in open) return@clickable
                        if (first < 0) {
                            first = index
                            open = setOf(index)
                        } else {
                            val same = pairs[first].second == pairs[index].second && first != index
                            if (same) {
                                matched = matched + first + index
                                open = emptySet()
                                first = -1
                                if (matched.size >= pairs.size) onDone()
                            } else {
                                onMiss()
                                open = setOf(index)
                                first = index
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (shown) pairs[index].first else "?", color = if (shown) Color.Black else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
