package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.games.tictactoe.TicGameMode
import com.example.bible.games.tictactoe.TicMark
import com.example.bible.games.tictactoe.TicTacToeAi
import com.example.bible.games.tictactoe.TicTacToeEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen(
    onBack: () -> Unit,
) {
    var mode by remember { mutableStateOf(TicGameMode.HUMAN_VS_AI) }
    var boardSize by remember { mutableIntStateOf(3) }
    val engine = remember(boardSize) { TicTacToeEngine(boardSize, winLength = 3) }
    val cells: SnapshotStateList<TicMark> = remember(boardSize) {
        mutableStateListOf<TicMark>().apply {
            repeat(boardSize * boardSize) {
                add(TicMark.Empty)
            }
        }
    }
    var current by remember(boardSize, mode) { mutableStateOf(TicMark.X) }
    val random = remember { Random(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    fun toArray(): Array<TicMark> = Array(cells.size) { cells[it] }

    fun gameOver(): Boolean {
        val w = engine.winner(toArray())
        return w != null || engine.isDraw(toArray())
    }

    fun opponent(m: TicMark): TicMark = when (m) {
        TicMark.X -> TicMark.O
        TicMark.O -> TicMark.X
        TicMark.Empty -> TicMark.X
    }

    fun restart() {
        for (i in cells.indices) cells[i] = TicMark.Empty
        current = TicMark.X
    }

    LaunchedEffect(mode) {
        restart()
    }

    val cellSig = cells.joinToString(separator = "") { "${it.ordinal}" }
    /** Ход ИИ для текущего знака [current]. */
    fun applyAiMove() {
        if (gameOver()) return
        val idx = TicTacToeAi.pickMove(engine, toArray(), current, random) ?: return
        if (cells[idx] != TicMark.Empty) return
        cells[idx] = current
        if (!gameOver()) current = opponent(current)
    }

    LaunchedEffect(mode, boardSize, cellSig) {
        if (mode != TicGameMode.AI_VS_AI) return@LaunchedEffect
        if (gameOver()) return@LaunchedEffect
        delay(380)
        if (mode != TicGameMode.AI_VS_AI) return@LaunchedEffect
        if (gameOver()) return@LaunchedEffect
        applyAiMove()
    }

    fun onHumanCell(index: Int) {
        if (gameOver()) return
        if (cells[index] != TicMark.Empty) return
        when (mode) {
            TicGameMode.AI_VS_AI -> return
            TicGameMode.HUMAN_VS_AI -> {
                if (current != TicMark.X) return
                cells[index] = TicMark.X
                if (gameOver()) return
                current = TicMark.O
                scope.launch {
                    delay(280)
                    if (mode != TicGameMode.HUMAN_VS_AI || gameOver()) return@launch
                    if (current != TicMark.O) return@launch
                    applyAiMove()
                }
            }
            TicGameMode.TWO_HUMANS -> {
                cells[index] = current
                if (!gameOver()) current = opponent(current)
            }
        }
    }

    val win = engine.winner(toArray())
    val draw = engine.isDraw(toArray())
    val over = win != null || draw

    val statusLine =
        when {
            win != null -> when (win) {
                TicMark.X -> "Победили крестики!"
                TicMark.O -> "Победили нолики!"
                TicMark.Empty -> ""
            }
            draw -> "Ничья"
            mode == TicGameMode.HUMAN_VS_AI && current == TicMark.X ->
                "Ваш ход (крестики)"
            mode == TicGameMode.HUMAN_VS_AI && current == TicMark.O ->
                "Ход компьютера (нолики)"
            mode == TicGameMode.TWO_HUMANS ->
                if (current == TicMark.X) "Ход крестиков" else "Ход ноликов"
            mode == TicGameMode.AI_VS_AI ->
                if (over) "Игра окончена" else (
                    if (current == TicMark.X) "Ход программы за крестики" else "Ход программы за нолики"
                    )
            else -> ""
        }

    val humanCanTap =
        !over &&
            when (mode) {
                TicGameMode.AI_VS_AI -> false
                TicGameMode.HUMAN_VS_AI -> current == TicMark.X
                TicGameMode.TWO_HUMANS -> true
            }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Крестики-нолики", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Режим",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = mode == TicGameMode.HUMAN_VS_AI,
                    onClick = { mode = TicGameMode.HUMAN_VS_AI },
                    label = { Text("Вы и компьютер", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
                FilterChip(
                    selected = mode == TicGameMode.TWO_HUMANS,
                    onClick = { mode = TicGameMode.TWO_HUMANS },
                    label = { Text("Два игрока", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
                FilterChip(
                    selected = mode == TicGameMode.AI_VS_AI,
                    onClick = { mode = TicGameMode.AI_VS_AI },
                    label = { Text("Два компьютера", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
            }
            Text(
                "Размер поля (больше ячеек — сложнее)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (n in 3..6) {
                    FilterChip(
                        selected = boardSize == n,
                        onClick = { boardSize = n },
                        label = { Text("${n}×$n") },
                    )
                }
            }
            Text(
                "Нужно собрать три в ряд — по строке, столбцу или диагонали.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                statusLine,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                key(boardSize, mode.name) {
                    TicBoardGrid(
                        size = boardSize,
                        marks = cells,
                        humanCanTap = humanCanTap,
                        onCell = { onHumanCell(it) },
                    )
                }
            }
            Button(
                onClick = {
                    restart()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text("Новая партия")
            }
        }
    }
}

@Composable
private fun TicBoardGrid(
    size: Int,
    marks: SnapshotStateList<TicMark>,
    humanCanTap: Boolean,
    onCell: (Int) -> Unit,
) {
    val count = size * size
    LazyVerticalGrid(
        columns = GridCells.Fixed(size),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        items(
            count = count,
            key = { it },
        ) { index ->
            val mark = marks[index]
            val clickable = humanCanTap && mark == TicMark.Empty
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(enabled = clickable) { onCell(index) },
                contentAlignment = Alignment.Center,
            ) {
                when (mark) {
                    TicMark.X -> Text(
                        "✕",
                        fontSize = when (size) {
                            3 -> 40.sp
                            4 -> 32.sp
                            5 -> 26.sp
                            else -> 22.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F),
                        textAlign = TextAlign.Center,
                    )
                    TicMark.O -> Text(
                        "○",
                        fontSize = when (size) {
                            3 -> 38.sp
                            4 -> 30.sp
                            5 -> 24.sp
                            else -> 20.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        textAlign = TextAlign.Center,
                    )
                    TicMark.Empty -> {}
                }
            }
        }
    }
}
