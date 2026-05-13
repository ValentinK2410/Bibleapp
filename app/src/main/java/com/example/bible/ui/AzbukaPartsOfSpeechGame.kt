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
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.nextPartsOfSpeechRound
import com.example.bible.data.PartsOfSpeechKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private sealed class PosTapFeedback {
    data object Idle : PosTapFeedback()
    data class Correct(val word: String) : PosTapFeedback()
    data class Wrong(val word: String) : PosTapFeedback()
}

private val POS_WRONG_PHRASES: List<String> = listOf(
    "Неправильно. Попробуй ещё.",
    "Неверно, попробуй ещё.",
    "Не то слово, попробуй снова.",
    "Здесь ошибка. Выбери другое.",
    "Пока не угадано. Подумай ещё раз.",
    "Мимо! Попробуй другое слово.",
    "Нет, смотри внимательнее.",
    "Неправильный выбор, попытайся снова.",
    "Не подходит. Ищи нужное слово.",
    "Увы, неверно. Ещё разок.",
    "Неверно. Попробуй ещё раз.",
)

private val POS_CORRECT_PHRASES: List<String> = listOf(
    "Отлично! Верно!",
    "Супер! Угадано!",
    "Здорово! Так держать!",
    "Правильно! Очень хорошо!",
    "Да! Всё получилось!",
    "Браво! Верный выбор!",
    "Класс! Получилось!",
    "Ура! Верно!",
    "Идеально! Можно идти дальше!",
)

@Composable
internal fun PartsOfSpeechGamePane(
    speak: (String) -> Unit,
    speakWhenDone: (String, () -> Unit) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    val random = remember { Random(System.currentTimeMillis()) }
    var roundKey by remember { mutableIntStateOf(0) }
    val round = remember(roundKey) { nextPartsOfSpeechRound(random) }
    var tapFeedback by remember { mutableStateOf<PosTapFeedback>(PosTapFeedback.Idle) }

    LaunchedEffect(roundKey) {
        tapFeedback = PosTapFeedback.Idle
        speak(round.targetKind.taskSpeakRu)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Найди часть речи",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Послушай, какую часть речи нужно найти, и нажми на слово.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SurfaceHint(kind = round.targetKind)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { speak(round.targetKind.taskSpeakRu) }) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 6.dp),
                )
                Text("Повторить задание")
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
            items(round.choices, key = { "${it.word}_${roundKey}" }) { item ->
                val correctShown =
                    (tapFeedback as? PosTapFeedback.Correct)?.word == item.word
                val wrongShown =
                    (tapFeedback as? PosTapFeedback.Wrong)?.word == item.word
                val idle = tapFeedback is PosTapFeedback.Idle
                PartsOfSpeechWordCard(
                    word = item.word,
                    showCorrectCheck = correctShown,
                    showWrongX = wrongShown,
                    enabled = idle,
                    onClick = {
                        if (idle) {
                            if (item.word == round.correct.word) {
                                tapFeedback = PosTapFeedback.Correct(item.word)
                                val praise = POS_CORRECT_PHRASES.random(random)
                                scope.launch { progressRepo.addPoints(8) }
                                speakWhenDone(praise) {
                                    roundKey++
                                }
                            } else {
                                tapFeedback = PosTapFeedback.Wrong(item.word)
                                speak(POS_WRONG_PHRASES.random(random))
                                scope.launch {
                                    delay(1200L)
                                    tapFeedback = PosTapFeedback.Idle
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
private fun SurfaceHint(kind: PartsOfSpeechKind) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = "Нужно: ${kind.labelRu.lowercase()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PartsOfSpeechWordCard(
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
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
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
