package com.example.bible.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.nextReversedWordRound
import com.example.bible.data.prettifyReversedDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private sealed class RevTapFeedback {
    data object Idle : RevTapFeedback()
    data class Correct(val word: String) : RevTapFeedback()
    data class Wrong(val word: String) : RevTapFeedback()
}

private val REV_WRONG_PHRASES: List<String> = listOf(
    "Неправильно. Попробуй ещё.",
    "Неверно, попробуй ещё.",
    "Не то слово, попробуй снова.",
    "Пока не угадано. Подумай ещё раз.",
    "Мимо! Попробуй другое слово.",
    "Нет, смотри внимательнее.",
    "Неправильный выбор, попытайся снова.",
    "Увы, неверно. Ещё разок.",
)

private val REV_CORRECT_PHRASES: List<String> = listOf(
    "Отлично! Верно!",
    "Супер! Угадано!",
    "Здорово! Так держать!",
    "Правильно! Очень хорошо!",
    "Да! Всё получилось!",
    "Браво! Верный выбор!",
    "Класс! Получилось!",
    "Ура! Верно!",
)

private const val REV_TASK_TTS =
    "Какое это слово, если прочитать написанное наоборот, с конца к началу?"

/** Буквы в порядке «с конца» — TTS читает понятнее, чем слитный «абурт». */
private fun reversedLettersSpacedForTts(forwardWord: String): String =
    forwardWord.lowercase().reversed().toList().joinToString(" ") { it.toString() }

@Composable
internal fun ReversedWordGamePane(
    speak: (String) -> Unit,
    speakWhenDone: (String, () -> Unit) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    val random = remember { Random(System.currentTimeMillis()) }
    var roundKey by remember { mutableIntStateOf(0) }
    val round = remember(roundKey) { nextReversedWordRound(random) }
    var tapFeedback by remember { mutableStateOf<RevTapFeedback>(RevTapFeedback.Idle) }
    val reversedShown = remember(round.answerForward) { prettifyReversedDisplay(round.answerForward) }

    LaunchedEffect(roundKey) {
        tapFeedback = RevTapFeedback.Idle
        speakWhenDone(REV_TASK_TTS) {
            speak(reversedLettersSpacedForTts(round.answerForward))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Слово наоборот",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Вверху — то же слово, записанное с конца. Сначала звучит подсказка, потом буквы по порядку от конца слова. Ещё можно нажать «Прочитать наоборот».",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                reversedShown,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { speak(REV_TASK_TTS) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 6.dp),
                )
                Text("Задание", maxLines = 1)
            }
            TextButton(
                onClick = { speak(reversedLettersSpacedForTts(round.answerForward)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 6.dp),
                )
                Text("Прочитать наоборот", maxLines = 1)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(round.choices, key = { "${it}_$roundKey" }) { word ->
                val correctShown =
                    (tapFeedback as? RevTapFeedback.Correct)?.word == word
                val wrongShown =
                    (tapFeedback as? RevTapFeedback.Wrong)?.word == word
                val idle = tapFeedback is RevTapFeedback.Idle
                ReversedWordChoiceCard(
                    word = word,
                    showCorrectCheck = correctShown,
                    showWrongX = wrongShown,
                    enabled = idle,
                    onClick = {
                        if (idle) {
                            if (word == round.answerForward) {
                                tapFeedback = RevTapFeedback.Correct(word)
                                val praise = REV_CORRECT_PHRASES.random(random)
                                scope.launch { progressRepo.addPoints(8) }
                                speakWhenDone(praise) {
                                    roundKey++
                                }
                            } else {
                                tapFeedback = RevTapFeedback.Wrong(word)
                                speak(REV_WRONG_PHRASES.random(random))
                                scope.launch {
                                    delay(1200L)
                                    tapFeedback = RevTapFeedback.Idle
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReversedWordChoiceCard(
    word: String,
    showCorrectCheck: Boolean,
    showWrongX: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val baseBorder = MaterialTheme.colorScheme.outline
    val resultBorder = when {
        showCorrectCheck -> Color(0xFF4CAF50)
        showWrongX -> Color(0xFFF44336)
        else -> baseBorder
    }
    val resultBg = when {
        showCorrectCheck -> Color(0x224CAF50)
        showWrongX -> Color(0x22F44336)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val label = word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = resultBg),
        border = BorderStroke(2.dp, resultBorder),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp),
            )
            if (showCorrectCheck) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (showWrongX) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF44336)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
