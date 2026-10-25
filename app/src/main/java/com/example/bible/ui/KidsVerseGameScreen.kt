package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsMemoryVerse
import com.example.bible.data.KidsMemoryVerses
import com.example.bible.data.synodalVerseOrNull
import com.example.bible.data.verseChunks
import com.example.bible.data.verseContainsFragment
import com.example.bible.data.verseTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

private data class VerseChip(val id: Int, val text: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KidsVerseScreen(onBack: () -> Unit, onOpenBible: (String, Int, Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val mode = family.resolvedMode()
    val today = remember { LocalDate.now() }
    val verse = remember(mode, today) { KidsMemoryVerses.ofWeek(today, mode) }
    val day = KidsMemoryVerses.taskDay(today)
    val speak = rememberKidsSpeaker(if (mode == KidsAgeMode.SMALL) 0.82f else 0.95f)
    var full by remember(verse.id) { mutableStateOf(verse.fullText) }
    var verified by remember(verse.id) { mutableStateOf(false) }
    var gate by remember { mutableStateOf(false) }
    val done = verse.id in family.activeProgress().versesDone
    LaunchedEffect(verse.id) {
        val db = withContext(Dispatchers.IO) {
            synodalVerseOrNull(context, verse.bookId, verse.chapter, verse.verse)
        }
        if (!db.isNullOrBlank()) {
            full = db
            verified = verseContainsFragment(db, verse.gameText)
        }
    }
    KidsSectionScaffold("Стих недели", onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(verse.reference, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            if (done) KidsRewardStars(1)
            Text(verse.gameText, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Text(
                if (verified) "Сверено с Синодальным текстом в базе приложения."
                else "Пока показан текст из приложения. Когда база откроется, стих подставится из неё.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (full != verse.gameText) {
                Text("Полный стих: $full", fontSize = 16.sp)
            }
            Text("День $day. ${KidsMemoryVerses.taskTitle(day, mode)}", fontWeight = FontWeight.Bold)
            Button(onClick = { speak(verse.gameText) }) { Text("Послушать") }
            TextButton(onClick = { onOpenBible(verse.bookId, verse.chapter, verse.verse) }) {
                Text("В Библии")
            }
            if (day == 1) {
                Text("Сегодня достаточно послушать и прочитать вместе. Завтра стих можно будет собирать.")
            } else if (day >= 7) {
                Text(if (done) "Родитель подтвердил: стих рассказан наизусть." else "Расскажи стих родителю. Подтвердить может только взрослый.")
                Button(onClick = { gate = true }, enabled = !done) { Text("Родитель подтверждает") }
            } else {
                VerseAssembleGame(verse, mode, day, onSpeak = speak)
            }
        }
    }
    if (gate) {
        ParentGateDialog(
            onDismiss = { gate = false },
            onPassed = {
                gate = false
                scope.launch { KidsFamilyStore.recordVerse(context, verse.id) }
            },
            hard = mode == KidsAgeMode.BIG,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerseAssembleGame(
    verse: KidsMemoryVerse,
    mode: KidsAgeMode,
    day: Int,
    onSpeak: (String) -> Unit,
) {
    val tokens = remember(verse.id) { verseTokens(verse.gameText) }
    val pieces = remember(verse.id, mode, day) {
        when {
            mode == KidsAgeMode.SMALL -> verseChunks(tokens, 3.coerceAtMost(tokens.size).coerceAtLeast(1))
            day <= 3 -> verseChunks(tokens, 4.coerceAtMost(tokens.size))
            else -> tokens
        }
    }
    val prefilled = remember(pieces, day) {
        if (day == 6) pieces.indices.filter { it % 2 == 0 }.toSet() else emptySet()
    }
    var bank by remember(pieces, day) {
        mutableStateOf(
            (pieces.mapIndexed { index, text -> VerseChip(index, text) } +
                extraDecoys(mode, day).mapIndexed { index, text -> VerseChip(10_000 + index, text) })
                .filter { chip -> chip.id !in prefilled }
                .shuffled(),
        )
    }
    var placed by remember(pieces, day) {
        mutableStateOf(pieces.indices.associateWith { index -> if (index in prefilled) pieces[index] else "" })
    }
    var errors by remember(pieces) { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val slotBounds = remember { mutableMapOf<Int, Rect>() }
    val chipBounds = remember { mutableMapOf<Int, Rect>() }
    val solved = pieces.indices.all { placed[it] == pieces[it] }

    Text(if (mode == KidsAgeMode.SMALL) "Перетащи большие плашки. Короткое касание — услышишь слово." else "Перетащи слова на пустые места.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pieces.indices.forEach { index ->
            val value = placed[index].orEmpty()
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (value.isBlank()) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.primaryContainer,
                    )
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .onGloballyPositioned { slotBounds[index] = it.boundsInRoot() },
            ) {
                Text(if (value.isBlank()) "…" else value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
    if (!solved) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bank.forEach { chip ->
                val active = dragging == chip.id
                Text(
                    chip.text,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .zIndex(if (active) 2f else 0f)
                        .onGloballyPositioned { chipBounds[chip.id] = it.boundsInRoot() }
                        .offset { IntOffset(if (active) drag.x.roundToInt() else 0, if (active) drag.y.roundToInt() else 0) }
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .pointerInput(chip.id) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = chip.id
                                    drag = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag += amount
                                },
                                onDragEnd = {
                                    val origin = chipBounds[chip.id]?.center ?: Offset.Zero
                                    val point = origin + drag
                                    val dropped = slotBounds.entries
                                        .filter { placed[it.key].isNullOrBlank() }
                                        .minByOrNull { (_, rect) -> (rect.center - point).getDistance() }
                                        ?.takeIf { (_, rect) -> (rect.center - point).getDistance() < 180f }
                                        ?.key
                                    dragging = null
                                    drag = Offset.Zero
                                    if (dropped == null) {
                                        onSpeak(chip.text)
                                        return@detectDragGestures
                                    }
                                    if (pieces[dropped] == chip.text) {
                                        placed = placed + (dropped to chip.text)
                                        bank = bank.filterNot { it.id == chip.id }
                                    } else {
                                        errors++
                                    }
                                },
                                onDragCancel = {
                                    dragging = null
                                    drag = Offset.Zero
                                },
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Text("Ошибок: $errors. Неверная плашка возвращается на место.", fontSize = 13.sp)
    } else {
        KidsRewardStars()
        Text("Получилось. Стих собран.", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
    }
}

private fun extraDecoys(mode: KidsAgeMode, day: Int): List<String> =
    if (mode == KidsAgeMode.BIG && day in 4..5) listOf("вчера", "никогда") else emptyList()
