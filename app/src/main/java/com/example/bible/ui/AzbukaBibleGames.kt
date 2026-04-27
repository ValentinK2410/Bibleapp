package com.example.bible.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.BibleLibrary
import com.example.bible.data.BibleWordGamePool
import com.example.bible.data.BibleWordItem
import com.example.bible.data.TranslationId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class BibleGameId {
    SCRAMBLE,
    MISSING_WORD,
    CHUNKS,
    FIRST_LETTER,
    SYLLABLE_IN_WORD,
    LONGEST_WORD,
    SYLLABLE_COUNT,
}

private data class GameCardInfo(
    val id: BibleGameId,
    val title: String,
    val subtitle: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleGamesSection(
    library: BibleLibrary,
    translation: TranslationId,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var pool by remember { mutableStateOf<List<BibleWordItem>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val random = remember { Random.Default }

    LaunchedEffect(library, translation) {
        loadError = null
        pool = try {
            BibleWordGamePool.collectWords(library, translation)
        } catch (e: Exception) {
            loadError = e.message ?: "Ошибка загрузки"
            emptyList()
        }
    }

    var activeGame by remember { mutableStateOf<BibleGameId?>(null) }

    val words = pool
    when {
        words == null -> {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Загружаем слова из Библии…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        loadError != null -> {
            Text(
                "Не удалось загрузить тексты: $loadError",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
        words.isEmpty() -> {
            Text(
                "Нет русского текста Библии для игр. Добавьте перевод (например, синодальный) в приложение.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        activeGame == null -> {
            BibleGamePicker(
                onPick = { activeGame = it },
            )
        }
        else -> {
            val game = activeGame!!
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { activeGame = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К играм")
                    }
                    Text(
                        when (game) {
                            BibleGameId.SCRAMBLE -> "Перемешанные буквы"
                            BibleGameId.MISSING_WORD -> "Слово в стихе"
                            BibleGameId.CHUNKS -> "Собери из частей"
                            BibleGameId.FIRST_LETTER -> "Первая буква"
                            BibleGameId.SYLLABLE_IN_WORD -> "Где слог?"
                            BibleGameId.LONGEST_WORD -> "Самое длинное слово"
                            BibleGameId.SYLLABLE_COUNT -> "Число слогов"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
                when (game) {
                    BibleGameId.SCRAMBLE -> BibleGameScramble(
                        pool = words,
                        random = random,
                        speak = speak,
                        progressRepo = progressRepo,
                        scope = scope,
                    )
                    BibleGameId.MISSING_WORD -> BibleGameMissingWord(
                        pool = words,
                        random = random,
                        speak = speak,
                        progressRepo = progressRepo,
                        scope = scope,
                    )
                    BibleGameId.CHUNKS -> BibleGameChunks(
                        pool = words,
                        random = random,
                        speak = speak,
                        progressRepo = progressRepo,
                        scope = scope,
                    )
                    BibleGameId.FIRST_LETTER -> BibleGameFirstLetter(
                        pool = words,
                        random = random,
                        speak = speak,
                        progressRepo = progressRepo,
                        scope = scope,
                    )
                    BibleGameId.SYLLABLE_IN_WORD -> BibleGameSyllableInWord(
                        pool = words,
                        random = random,
                        speak = speak,
                        progressRepo = progressRepo,
                        scope = scope,
                    )
                    BibleGameId.LONGEST_WORD -> BibleGameLongestWord(
                        pool = words,
                        random = random,
                        speak = speak,
                        progressRepo = progressRepo,
                        scope = scope,
                    )
                    BibleGameId.SYLLABLE_COUNT -> BibleGameSyllableCount(
                        pool = words,
                        random = random,
                        speak = speak,
                        progressRepo = progressRepo,
                        scope = scope,
                    )
                }
            }
        }
    }
}

@Composable
private fun BibleGamePicker(onPick: (BibleGameId) -> Unit) {
    val items = listOf(
        GameCardInfo(
            BibleGameId.SCRAMBLE,
            "Перемешанные буквы",
            "Собери слово из библейского текста по перемешанным буквам.",
        ),
        GameCardInfo(
            BibleGameId.MISSING_WORD,
            "Пропущенное слово",
            "Восстанови слово, которое выпало из стиха.",
        ),
        GameCardInfo(
            BibleGameId.CHUNKS,
            "Части слова",
            "Соедини части в правильном порядке (слоги/куски из слова).",
        ),
        GameCardInfo(
            BibleGameId.FIRST_LETTER,
            "Первая буква",
            "Угадай, с какой буквы начинается слово из Писания.",
        ),
        GameCardInfo(
            BibleGameId.SYLLABLE_IN_WORD,
            "Где слог?",
            "Выбери слово из Библии, в котором есть этот слог.",
        ),
        GameCardInfo(
            BibleGameId.LONGEST_WORD,
            "Самое длинное",
            "Какое из четырёх библейских слов самое длинное?",
        ),
        GameCardInfo(
            BibleGameId.SYLLABLE_COUNT,
            "Сколько слогов?",
            "Угадай число слогов в слове из стиха (по гласным).",
        ),
    )
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Игры по словам и слогам из русского текста Библии",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(items) { info ->
            val icon = when (info.id) {
                BibleGameId.SCRAMBLE -> Icons.Default.TextFields
                BibleGameId.MISSING_WORD -> Icons.Default.QuestionMark
                BibleGameId.CHUNKS -> Icons.Default.Extension
                BibleGameId.FIRST_LETTER -> Icons.Default.Spellcheck
                BibleGameId.SYLLABLE_IN_WORD -> Icons.Default.AutoAwesome
                BibleGameId.LONGEST_WORD -> Icons.Default.Star
                BibleGameId.SYLLABLE_COUNT -> Icons.Default.Lightbulb
            }
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(info.id) },
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            info.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun award(scope: CoroutineScope, progressRepo: AzbukaProgressRepository, correct: Boolean) {
    scope.launch {
        progressRepo.addPoints(if (correct) 12 else 4)
    }
}

@Composable
private fun BibleGameScramble(
    pool: List<BibleWordItem>,
    random: Random,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var round by remember { mutableIntStateOf(0) }
    val item = remember(round, pool) { pool.random(random) }
    val scrambled = remember(item.word) { BibleWordGamePool.shuffledLetters(item.word) }
    val options = remember(item, pool) {
        val wrong = BibleWordGamePool.pickDistractorWords(pool, item.word, 3, random)
        (wrong + item.word).shuffled(random)
    }
    var picked by remember(round) { mutableStateOf<String?>(null) }
    val done = picked != null
    val correct = picked?.equals(item.word, ignoreCase = true) == true

    Column(Modifier.padding(16.dp)) {
        Text("Подсказка: ${item.reference}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            scrambled,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        options.forEach { opt ->
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !done) {
                        picked = opt
                        speak(opt)
                        award(scope, progressRepo, opt.equals(item.word, ignoreCase = true))
                    },
                border = BorderStroke(
                    2.dp,
                    when {
                        !done -> MaterialTheme.colorScheme.outline
                        opt.equals(item.word, ignoreCase = true) -> Color(0xFF4CAF50)
                        picked == opt -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                ),
            ) {
                Text(opt, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        AnimatedVisibility(done) {
            Text(
                if (correct) "Верно! Слово из: «${item.verseText.take(80)}…»" else "Правильно: ${item.word}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { round++ }) { Text("Другой вопрос") }
    }
}

@Composable
private fun BibleGameMissingWord(
    pool: List<BibleWordItem>,
    random: Random,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var round by remember { mutableIntStateOf(0) }
    val item = remember(round, pool) { pool.random(random) }
    val masked = remember(item) {
        val w = item.word
        item.verseText.replaceFirst(
            Regex(Regex.escape(w), RegexOption.IGNORE_CASE),
            "______",
        )
    }
    val options = remember(item, pool) {
        (BibleWordGamePool.pickDistractorWords(pool, item.word, 3, random) + item.word).shuffled(random)
    }
    var picked by remember(round) { mutableStateOf<String?>(null) }
    val done = picked != null
    val correct = picked?.equals(item.word, ignoreCase = true) == true

    Column(Modifier.padding(16.dp)) {
        Text(item.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(masked, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        options.forEach { opt ->
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !done) {
                        picked = opt
                        speak(opt)
                        award(scope, progressRepo, opt.equals(item.word, ignoreCase = true))
                    },
            ) {
                Text(opt, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        AnimatedVisibility(done) {
            Text(
                if (correct) "Так и есть!" else "Было: ${item.word}",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = { round++ }) { Text("Дальше") }
    }
}

@Composable
private fun BibleGameChunks(
    pool: List<BibleWordItem>,
    random: Random,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var round by remember { mutableIntStateOf(0) }
    val item = remember(round, pool) {
        pool.filter { it.word.length >= 5 }.randomOrNull() ?: pool.random(random)
    }
    val parts = remember(item.word) { BibleWordGamePool.splitIntoChunks(item.word, 3) }
    val correctOrder = parts.joinToString("")
    val wrongOrders = remember(parts, correctOrder) {
        val out = linkedSetOf<String>()
        repeat(60) {
            val sh = parts.shuffled()
            val j = sh.joinToString("")
            if (j != correctOrder) out.add(j)
            if (out.size >= 3) return@remember out.toList()
        }
        out.toList()
    }
    val options = remember(correctOrder, wrongOrders) {
        (wrongOrders + correctOrder).shuffled(random)
    }
    var picked by remember(round) { mutableStateOf<String?>(null) }
    val done = picked != null
    val correct = picked == correctOrder

    Column(Modifier.padding(16.dp)) {
        Text("Части: ${parts.joinToString(" · ")}", style = MaterialTheme.typography.titleSmall)
        Text(item.reference, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        options.forEach { opt ->
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !done) {
                        picked = opt
                        speak(opt)
                        award(scope, progressRepo, opt == correctOrder)
                    },
            ) {
                Text(opt, Modifier.padding(14.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        AnimatedVisibility(done) {
            Text(
                if (correct) "Верно: ${item.word}" else "Слово: ${item.word}",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = { round++ }) { Text("Дальше") }
    }
}

@Composable
private fun BibleGameFirstLetter(
    pool: List<BibleWordItem>,
    random: Random,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var round by remember { mutableIntStateOf(0) }
    val item = remember(round, pool) {
        pool.filter { it.word.length >= 5 }.randomOrNull() ?: pool.random(random)
    }
    val w = item.word
    val first = w.first().uppercaseChar()
    val mask = "·" + w.drop(1)
    val letters = remember(first, random) {
        val alphabet = ('А'..'Я').toList() + listOf('Ё')
        val wrong = alphabet.filter { it != first }.shuffled(random).take(3)
        (wrong + first).shuffled(random)
    }
    var picked by remember(round) { mutableStateOf<Char?>(null) }
    val done = picked != null
    val correct = picked == first

    Column(Modifier.padding(16.dp)) {
        Text(mask, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(item.reference, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            letters.forEach { ch ->
                FilterChip(
                    selected = picked == ch,
                    onClick = {
                        if (done) return@FilterChip
                        picked = ch
                        speak(ch.toString())
                        award(scope, progressRepo, ch == first)
                    },
                    label = { Text(ch.toString(), fontWeight = FontWeight.Bold) },
                )
            }
        }
        AnimatedVisibility(done) {
            Text(if (correct) "Верно: $w" else "Это слово: $w", modifier = Modifier.padding(top = 12.dp))
        }
        TextButton(onClick = { round++ }) { Text("Дальше") }
    }
}

@Composable
private fun BibleGameSyllableInWord(
    pool: List<BibleWordItem>,
    random: Random,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var round by remember { mutableIntStateOf(0) }
    val item = remember(round, pool) {
        pool.filter { it.word.length >= 5 }.randomOrNull() ?: pool.random(random)
    }
    val syllable = remember(item.word) {
        BibleWordGamePool.randomSyllableFromWord(item.word, 2)?.lowercase() ?: item.word.take(2).lowercase()
    }
    val options = remember(item, syllable, pool, random) {
        val correct = item.word
        val wrong = pool
            .map { it.word }
            .filter {
                it.length >= 4 &&
                    !it.equals(correct, ignoreCase = true) &&
                    !it.lowercase().contains(syllable)
            }
            .distinct()
            .shuffled(random)
            .take(3)
        (wrong + correct).shuffled(random)
    }
    var picked by remember(round) { mutableStateOf<String?>(null) }
    val done = picked != null
    val correct = picked?.equals(item.word, ignoreCase = true) == true

    Column(Modifier.padding(16.dp)) {
        Text("Найди слово, где есть слог «$syllable»", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        options.forEach { opt ->
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !done) {
                        picked = opt
                        speak(opt)
                        award(scope, progressRepo, opt.equals(item.word, ignoreCase = true))
                    },
            ) {
                Text(opt, Modifier.padding(14.dp))
            }
        }
        AnimatedVisibility(done) {
            Text(
                if (correct) "Верно! (${item.reference})" else "Это: ${item.word}",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = { round++ }) { Text("Дальше") }
    }
}

@Composable
private fun BibleGameLongestWord(
    pool: List<BibleWordItem>,
    random: Random,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var round by remember { mutableIntStateOf(0) }
    val four = remember(round, pool) {
        pool.shuffled(random).distinctBy { it.word.lowercase() }.take(4)
    }
    val longest = remember(four) {
        four.maxByOrNull { it.word.length } ?: four.first()
    }
    var picked by remember(round) { mutableStateOf<String?>(null) }
    val done = picked != null
    val correct = picked?.equals(longest.word, ignoreCase = true) == true

    Column(Modifier.padding(16.dp)) {
        Text("Где самое длинное слово?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Все слова взяты из русского текста Библии.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        four.forEach { item ->
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !done) {
                        picked = item.word
                        speak(item.word)
                        award(scope, progressRepo, item.word.equals(longest.word, ignoreCase = true))
                    },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(item.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.reference, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AnimatedVisibility(done) {
            Text(
                if (correct) "Верно!" else "Самое длинное: ${longest.word}",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        TextButton(onClick = { round++ }) { Text("Дальше") }
    }
}

@Composable
private fun BibleGameSyllableCount(
    pool: List<BibleWordItem>,
    random: Random,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var round by remember { mutableIntStateOf(0) }
    val item = remember(round, pool) {
        pool.filter { it.word.length >= 4 }.randomOrNull() ?: pool.random(random)
    }
    val syllables = remember(item.word) { BibleWordGamePool.approximateSyllableCount(item.word) }
    val options = remember(syllables, random) {
        val wrong = buildSet {
            add((syllables + 1).coerceAtMost(8))
            add((syllables - 1).coerceAtLeast(1))
            add((syllables + 2).coerceAtMost(8))
            add((syllables - 2).coerceAtLeast(1))
        }.filter { it != syllables && it in 1..8 }.take(3)
        (wrong + syllables).shuffled(random)
    }
    var picked by remember(round) { mutableStateOf<Int?>(null) }
    val done = picked != null
    val correct = picked == syllables

    Column(Modifier.padding(16.dp)) {
        Text("Слово: ${item.word}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(item.reference, style = MaterialTheme.typography.labelSmall)
        Text(
            "Сколько в нём слогов? (считаем по гласным буквам)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            options.forEach { n ->
                FilterChip(
                    selected = picked == n,
                    onClick = {
                        if (done) return@FilterChip
                        picked = n
                        speak(n.toString())
                        award(scope, progressRepo, n == syllables)
                    },
                    label = { Text(n.toString()) },
                )
            }
        }
        AnimatedVisibility(done) {
            Text(
                if (correct) "Верно: $syllables слог(ов)" else "Было: $syllables",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        TextButton(onClick = { round++ }) { Text("Дальше") }
    }
}
