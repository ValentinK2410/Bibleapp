package com.example.bible.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bible.games.pipes.PipeDir
import com.example.bible.games.pipes.PipeKind
import com.example.bible.games.pipes.PipePuzzleEngine
import com.example.bible.games.pipes.PipePuzzleStatus
import com.example.bible.games.pipes.PipeTile
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipePuzzleScreen(
    onBack: () -> Unit,
) {
    var gridSize by remember { mutableIntStateOf(4) }
    val random = remember { Random(System.currentTimeMillis()) }
    val tiles: SnapshotStateList<PipeTile> = remember { mutableStateListOf() }
    var loading by remember { mutableStateOf(true) }

    var reloadNonce by remember { mutableIntStateOf(0) }
    var victoryPlayed by remember { mutableStateOf(false) }


    suspend fun loadGame(size: Int) {
        loading = true
        val game = withContext(Dispatchers.Default) {
            PipePuzzleEngine.newGame(size, random)
        }
        tiles.clear()
        tiles.addAll(game)
        loading = false
    }

    LaunchedEffect(gridSize, reloadNonce) {
        loadGame(gridSize)
        victoryPlayed = false
    }

    val status by remember(gridSize) {
        derivedStateOf {
            if (tiles.isEmpty()) {
                PipePuzzleEngine.evaluate(emptyList(), gridSize)
            } else {
                PipePuzzleEngine.evaluate(tiles.toList(), gridSize)
            }
        }
    }
    val solved = status.isSolved
    KidsGameWinReward(game = com.example.bible.data.KidsGames.PIPES, won = solved)

    LaunchedEffect(solved, victoryPlayed) {
        if (solved && !victoryPlayed) {
            victoryPlayed = true
            com.example.bible.games.KidsGameAudio.play(com.example.bible.games.KidsSfx.WIN)
            kotlinx.coroutines.delay(650)
            com.example.bible.games.KidsGameAudio.play(com.example.bible.games.KidsSfx.WATER)
        }
    }
    var lastConnected by remember(gridSize, reloadNonce) { mutableIntStateOf(0) }
    LaunchedEffect(status.connectedCount) {
        if (!loading && status.connectedCount > lastConnected && lastConnected > 0 && !status.isSolved) {
            com.example.bible.games.KidsGameAudio.play(com.example.bible.games.KidsSfx.PIPE_CONNECT)
        }
        lastConnected = status.connectedCount
    }

    KidsGameMusic(com.example.bible.games.KidsMusicTrack.PIPES)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Водопровод", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { KidsAudioToggles() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Поворачивай трубы, чтобы вода от красного крана дошла до стока через все плитки 💧",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TmGradientPill(
                    text = "4×4",
                    selected = gridSize == 4,
                    gradient = kidsGradient(0),
                    onClick = {
                        if (gridSize != 4) gridSize = 4
                    },
                )
                TmGradientPill(
                    text = "5×5",
                    selected = gridSize == 5,
                    gradient = kidsGradient(1),
                    onClick = {
                        if (gridSize != 5) gridSize = 5
                    },
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (loading || tiles.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    PipePuzzleArtBoard(
                        tiles = tiles,
                        gridSize = gridSize,
                        solved = solved,
                        gameKey = gridSize * 1000 + reloadNonce,
                        modifier = Modifier.size(minOf(maxWidth, maxHeight)),
                        onTileTap = { index ->
                            if (solved) return@PipePuzzleArtBoard
                            tiles[index] = tiles[index].rotated()
                            com.example.bible.games.KidsGameAudio.play(com.example.bible.games.KidsSfx.PIPE_ROTATE)
                        },
                    )
                }
            }

            if (solved) {
                KidsBanner(
                    "🎉 Отлично! Весь водопровод собран!",
                    gradient = listOf(Color(0xFF22C55E), Color(0xFF06B6D4)),
                )
            } else if (!loading && tiles.isNotEmpty()) {
                KidsBanner(pipePuzzleStatusText(status) + "\n👆 Нажми на плитку — она повернётся.", fontSize = 15)
            }

            KidsBigButton(
                text = "Новая игра",
                onClick = { reloadNonce++ },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun pipePuzzleStatusText(status: PipePuzzleStatus): String {
    val progress = "Подключено: ${status.connectedCount} из ${status.totalCount}"
    return when {
        status.connectedCount <= 1 -> "$progress. Начни с крана и поворачивай соседние плитки."
        status.sinkConnected && !status.isSolved ->
            "$progress. Вода дошла до стока, но часть труб ещё не в цепи — поверни оставшиеся плитки."
        else -> "$progress. Синие плитки с водой уже подключены, остальные доверни."
    }
}
