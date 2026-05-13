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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.games.go.GoAi
import com.example.bible.games.go.GoGame
import com.example.bible.games.go.GoGameMode
import com.example.bible.games.go.GoIntersection
import com.example.bible.games.go.GoPlayer
import kotlinx.coroutines.delay
import kotlin.random.Random

private fun hoshiPoints(size: Int): Set<Pair<Int, Int>> = when (size) {
    9 -> setOf(2 to 2, 2 to 6, 6 to 2, 6 to 6, 4 to 4)
    13 -> setOf(3 to 3, 3 to 9, 9 to 3, 9 to 9, 6 to 6)
    19 -> setOf(
        3 to 3, 3 to 9, 3 to 15,
        9 to 3, 9 to 9, 9 to 15,
        15 to 3, 15 to 9, 15 to 15,
    )
    else -> emptySet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoScreen(onBack: () -> Unit) {
    var boardSize by remember { mutableIntStateOf(9) }
    var newGameKey by remember { mutableIntStateOf(0) }
    val game = remember(boardSize, newGameKey) { GoGame(boardSize) }
    var mode by remember { mutableStateOf(GoGameMode.HUMAN_VS_AI) }
    var stateRevision by remember(boardSize, newGameKey) { mutableIntStateOf(0) }
    val random = remember { Random(System.currentTimeMillis()) }

    // Не включаем полный срез доски в ключ LaunchedEffect: иначе корутина ИИ отменялась при пересборке.
    // отменяла корутину ИИ во время delay() — белые не ходили, на доске копились только чёрные.
    LaunchedEffect(mode, stateRevision, game.toPlay, game.gameOver) {
        if (game.gameOver) return@LaunchedEffect
        if (mode != GoGameMode.HUMAN_VS_AI) return@LaunchedEffect
        if (game.toPlay != GoPlayer.White) return@LaunchedEffect
        delay(420)
        if (game.gameOver) return@LaunchedEffect
        if (game.toPlay != GoPlayer.White) return@LaunchedEffect
        val move = GoAi.pickMove(game, random)
        if (move != null) {
            game.play(move)
        } else {
            game.pass()
        }
        stateRevision++
    }

    val humanIsBlack = mode == GoGameMode.HUMAN_VS_AI
    val humanTurn =
        !game.gameOver &&
            (mode == GoGameMode.TWO_HUMANS ||
                (humanIsBlack && game.toPlay == GoPlayer.Black) ||
                (!humanIsBlack && game.toPlay == GoPlayer.White))

    fun onTapCell(at: Int) {
        if (!humanTurn) return
        if (game.play(at)) stateRevision++
    }

    fun onPass() {
        if (!humanTurn) return
        game.pass()
        stateRevision++
    }

    val status =
        when {
            game.gameOver ->
                "Партия окончена (два паса подряд). Окружение и очки — по договорённости или отдельный подсчёт."
            mode == GoGameMode.HUMAN_VS_AI && game.toPlay == GoPlayer.Black ->
                "Ваш ход (чёрные)."
            mode == GoGameMode.HUMAN_VS_AI && game.toPlay == GoPlayer.White ->
                "Ход программы (белые)."
            mode == GoGameMode.TWO_HUMANS && game.toPlay == GoPlayer.Black ->
                "Ход чёрных."
            mode == GoGameMode.TWO_HUMANS && game.toPlay == GoPlayer.White ->
                "Ход белых."
            else -> ""
        }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Го", style = MaterialTheme.typography.titleLarge) },
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
                "Размер доски",
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
                listOf(9, 13, 19).forEach { sz ->
                    FilterChip(
                        selected = boardSize == sz,
                        onClick = { boardSize = sz },
                        label = { Text("${sz}×$sz") },
                    )
                }
            }
            Text(
                "Режим",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = mode == GoGameMode.HUMAN_VS_AI,
                    onClick = { mode = GoGameMode.HUMAN_VS_AI },
                    label = { Text("Вы и компьютер", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
                FilterChip(
                    selected = mode == GoGameMode.TWO_HUMANS,
                    onClick = { mode = GoGameMode.TWO_HUMANS },
                    label = { Text("Два игрока", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
            }
            Text(
                "Окружите камни соперника, чтобы снять группу. Камень нельзя поставить в поле без «дыханий», если он не снимает соперника.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                val side = minOf(maxWidth, maxHeight)
                val n = game.size
                val hoshi = hoshiPoints(n)
                key(stateRevision, n) {
                Column(
                    modifier = Modifier
                        .size(side)
                        .align(Alignment.Center)
                        .background(Color(0xFFDEB887))
                        .padding(2.dp),
                ) {
                    for (r in 0 until n) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            for (c in 0 until n) {
                                val idx = game.idx(r, c)
                                val stone = game.board[idx]
                                val playable = !game.gameOver && humanTurn
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(enabled = playable) { onTapCell(idx) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val line = Color(0xFF5D4037)
                                        val t = 1.5f
                                        drawLine(line, Offset(0f, h / 2), Offset(w, h / 2), t)
                                        drawLine(line, Offset(w / 2, 0f), Offset(w / 2, h), t)
                                    }
                                    if (r to c in hoshi) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize(0.2f)
                                                .clip(CircleShape)
                                                .background(Color(0xFF5D4037)),
                                        )
                                    }
                                    when (stone) {
                                        GoIntersection.Black ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize(0.62f)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF212121)),
                                            )
                                        GoIntersection.White ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize(0.62f)
                                                    .border(1.dp, Color(0xFF9E9E9E), CircleShape)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFF5F5F5)),
                                            )
                                        GoIntersection.Empty -> {}
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onPass() },
                    modifier = Modifier.weight(1f),
                    enabled = humanTurn && !game.gameOver,
                ) {
                    Text("Пас")
                }
                Button(
                    onClick = { newGameKey++ },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Новая партия")
                }
            }
        }
    }
}
