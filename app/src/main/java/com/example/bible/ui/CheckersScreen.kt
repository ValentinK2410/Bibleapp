package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.games.checkers.CheckersAi
import com.example.bible.games.checkers.CheckersCell
import com.example.bible.games.checkers.CheckersEngine
import com.example.bible.games.checkers.CheckersGameMode
import com.example.bible.games.checkers.CheckersPath
import com.example.bible.games.checkers.CheckersSide
import kotlinx.coroutines.delay
import kotlin.random.Random

private fun opponent(side: CheckersSide): CheckersSide = when (side) {
    CheckersSide.Light -> CheckersSide.Dark
    CheckersSide.Dark -> CheckersSide.Light
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckersScreen(onBack: () -> Unit) {
    var mode by remember { mutableStateOf(CheckersGameMode.HUMAN_VS_AI) }
    var board by remember { mutableStateOf(CheckersEngine.initialBoard()) }
    var current by remember { mutableStateOf(CheckersSide.Light) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val random = remember { Random(System.currentTimeMillis()) }

    fun restart() {
        board = CheckersEngine.initialBoard()
        current = CheckersSide.Light
        selected = null
    }

    LaunchedEffect(mode) {
        restart()
    }

    val boardSig = board.joinToString("") { "${it.ordinal}" }

    fun legalForSide(side: CheckersSide) = CheckersEngine.legalPaths(board, side)

    fun resolvePath(from: Int, to: Int, side: CheckersSide): CheckersPath? {
        val paths = legalForSide(side)
        return paths
            .filter { it.cells.first() == from && it.cells.last() == to }
            .maxByOrNull { it.cells.size }
    }

    val finished: CheckersSide? = remember(boardSig, current) {
        when {
            !CheckersEngine.hasPieces(board, CheckersSide.Light) -> CheckersSide.Dark
            !CheckersEngine.hasPieces(board, CheckersSide.Dark) -> CheckersSide.Light
            CheckersEngine.legalPaths(board, current).isEmpty() -> opponent(current)
            else -> null
        }
    }

    val gameOver = finished != null

    fun applyHumanPath(path: CheckersPath) {
        board = CheckersEngine.applyPath(board, path)
        selected = null
        current = opponent(current)
    }

    LaunchedEffect(mode, boardSig, current) {
        if (mode != CheckersGameMode.HUMAN_VS_AI) return@LaunchedEffect
        if (gameOver) return@LaunchedEffect
        if (current != CheckersSide.Dark) return@LaunchedEffect
        delay(320)
        if (mode != CheckersGameMode.HUMAN_VS_AI) return@LaunchedEffect
        if (gameOver) return@LaunchedEffect
        if (current != CheckersSide.Dark) return@LaunchedEffect
        val path = CheckersAi.pickPath(board, CheckersSide.Dark, random) ?: return@LaunchedEffect
        board = CheckersEngine.applyPath(board, path)
        current = opponent(current)
    }

    val humanTurn = mode == CheckersGameMode.HUMAN_VS_AI && current == CheckersSide.Light ||
        mode == CheckersGameMode.TWO_HUMANS
    val humanCanTap = humanTurn && !gameOver

    val sidePaths = if (gameOver) emptyList() else legalForSide(current)
    val targetCells: Set<Int> = selected?.let { sel ->
        sidePaths.filter { it.cells.first() == sel }.map { it.cells.last() }.toSet()
    } ?: emptySet()

    fun onCellClick(index: Int) {
        if (!humanCanTap) return
        val cell = board[index]
        val mine = CheckersEngine.sideOf(cell) == current

        if (mine) {
            selected = if (selected == index) null else index
            return
        }

        val from = selected ?: return
        val path = resolvePath(from, index, current) ?: return
        applyHumanPath(path)
    }

    val statusLine = when {
        finished != null -> when (finished) {
            CheckersSide.Light -> "Победили белые!"
            CheckersSide.Dark -> "Победили чёрные!"
        }
        mode == CheckersGameMode.HUMAN_VS_AI && current == CheckersSide.Light -> "Ваш ход (белые)"
        mode == CheckersGameMode.HUMAN_VS_AI && current == CheckersSide.Dark -> "Ход компьютера (чёрные)"
        mode == CheckersGameMode.TWO_HUMANS ->
            if (current == CheckersSide.Light) "Ход белых" else "Ход чёрных"
        else -> ""
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Шашки", style = MaterialTheme.typography.titleLarge) },
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
                    selected = mode == CheckersGameMode.HUMAN_VS_AI,
                    onClick = { mode = CheckersGameMode.HUMAN_VS_AI },
                    label = { Text("Вы и компьютер", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
                FilterChip(
                    selected = mode == CheckersGameMode.TWO_HUMANS,
                    onClick = { mode = CheckersGameMode.TWO_HUMANS },
                    label = { Text("Два игрока", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
            }
            Text(
                "Вы играете белыми снизу. Есть обязательное взятие; дамка ходит по диагонали на любое число полей.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                statusLine,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                val side = minOf(maxWidth, maxHeight)
                Column(
                    modifier = Modifier
                        .size(side)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                ) {
                    for (r in 0 until CheckersEngine.SIZE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            for (c in 0 until CheckersEngine.SIZE) {
                                val idx = CheckersEngine.idx(r, c)
                                val playable = CheckersEngine.isPlayable(r, c)
                                val piece = board[idx]
                                val isSel = selected == idx
                                val isTarget = idx in targetCells
                                val bg = if (!playable) {
                                    Color(0xFFE0D6CF)
                                } else {
                                    Color(0xFF4E342E)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(bg)
                                        .then(
                                            if (playable && humanCanTap) {
                                                Modifier.clickable { onCellClick(idx) }
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .then(
                                            if (isSel) {
                                                Modifier.border(2.dp, Color(0xFF81D4FA))
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isTarget && playable) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize(0.28f)
                                                .background(Color(0x66FFEB3B), CircleShape),
                                        )
                                    }
                                    when (piece) {
                                        CheckersCell.LightMan, CheckersCell.LightKing ->
                                            CheckersPieceCanvas(
                                                isLight = true,
                                                isKing = piece == CheckersCell.LightKing,
                                            )
                                        CheckersCell.DarkMan, CheckersCell.DarkKing ->
                                            CheckersPieceCanvas(
                                                isLight = false,
                                                isKing = piece == CheckersCell.DarkKing,
                                            )
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { restart() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Новая партия")
            }
        }
    }
}

@Composable
private fun CheckersPieceCanvas(isLight: Boolean, isKing: Boolean) {
    val main = if (isLight) Color(0xFFFFCC80) else Color(0xFF1E3A5F)
    val strokeC = if (isLight) Color(0xFFE65100) else Color(0xFF90CAF9)
    Box(
        modifier = Modifier.fillMaxSize(0.72f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val m = size.minDimension
            drawCircle(color = main, radius = m * 0.48f)
            drawCircle(color = strokeC, radius = m * 0.48f, style = Stroke(width = m * 0.06f))
            if (isKing) {
                drawCircle(
                    color = strokeC.copy(alpha = 0.75f),
                    radius = m * 0.22f,
                    style = Stroke(width = m * 0.05f),
                )
            }
        }
    }
}
