package com.example.bible.ui

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
import com.example.bible.games.pipes.PipePuzzleSoundPlayer
import com.example.bible.games.pipes.PipePuzzleStatus
import com.example.bible.games.pipes.PipeRotateSound
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
    var soundEnabled by remember { mutableStateOf(true) }
    var rotateSound by remember { mutableStateOf(PipeRotateSound.PAGE_FLIP) }
    val context = LocalContext.current
    val soundPlayer = remember { PipePuzzleSoundPlayer(context) }
    val random = remember { Random(System.currentTimeMillis()) }
    val tiles: SnapshotStateList<PipeTile> = remember { mutableStateListOf() }
    var loading by remember { mutableStateOf(true) }

    var reloadNonce by remember { mutableIntStateOf(0) }
    var victoryPlayed by remember { mutableStateOf(false) }

    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer.stop() }
    }

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

    LaunchedEffect(solved, soundEnabled, victoryPlayed) {
        if (solved && soundEnabled && !victoryPlayed) {
            victoryPlayed = true
            soundPlayer.playWin()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Водопровод", style = MaterialTheme.typography.titleLarge) },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Поворачивай все плитки: вода должна пройти от крана через каждую трубу до стока. Ответвлений нет — одна длинная цепочка.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = gridSize == 4,
                    onClick = {
                        if (gridSize != 4) gridSize = 4
                    },
                    label = { Text("4×4") },
                )
                FilterChip(
                    selected = gridSize == 5,
                    onClick = {
                        if (gridSize != 5) gridSize = 5
                    },
                    label = { Text("5×5") },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = soundEnabled,
                    onClick = { soundEnabled = true },
                    label = { Text("Со звуком") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    },
                )
                FilterChip(
                    selected = !soundEnabled,
                    onClick = {
                        soundEnabled = false
                        soundPlayer.stop()
                    },
                    label = { Text("Без звука") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    },
                )
            }

            if (soundEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rotateSound == PipeRotateSound.PAGE_FLIP,
                        onClick = { rotateSound = PipeRotateSound.PAGE_FLIP },
                        label = { Text("Страница") },
                    )
                    FilterChip(
                        selected = rotateSound == PipeRotateSound.SPIDER_WEB,
                        onClick = { rotateSound = PipeRotateSound.SPIDER_WEB },
                        label = { Text("Паутина") },
                    )
                }
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
                    PipePuzzleBoard(
                        tiles = tiles,
                        gridSize = gridSize,
                        solved = solved,
                        connectedIndices = status.connectedIndices,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        onTileTap = { index ->
                            if (solved) return@PipePuzzleBoard
                            tiles[index] = tiles[index].rotated()
                            if (soundEnabled) soundPlayer.playRotate(rotateSound)
                        },
                    )
                }
            }

            if (solved) {
                Text(
                    "Отлично! Весь водопровод собран!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            } else if (!loading && tiles.isNotEmpty()) {
                Text(
                    pipePuzzleStatusText(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Нажми на плитку — она повернётся на 90°.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = { reloadNonce++ },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Text("Новая игра")
            }
        }
    }
}

private fun pipePuzzleStatusText(status: PipePuzzleStatus): String {
    val progress = "Подключено: ${status.connectedCount} из ${status.totalCount}"
    return when {
        status.connectedCount <= 1 -> "$progress. Начни с крана и поворачивай соседние плитки."
        status.sinkConnected && !status.isSolved ->
            "$progress. Вода дошла до стока, но часть труб ещё не в цепи — поверни оставшиеся плитки."
        else -> "$progress. Светлые плитки уже в сети, остальные нужно довернуть."
    }
}

@Composable
private fun PipePuzzleBoard(
    tiles: List<PipeTile>,
    gridSize: Int,
    solved: Boolean,
    connectedIndices: Set<Int>,
    modifier: Modifier = Modifier,
    onTileTap: (Int) -> Unit,
) {
    val solvedBg by animateColorAsState(
        if (solved) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        label = "pipeBoardBg",
    )
    val pipeColor = MaterialTheme.colorScheme.primary
    val pipeHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val tileBg = MaterialTheme.colorScheme.surface
    val tileBgConnected = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val tileBgDisconnected = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
    val tileBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val tileBorderConnected = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(solvedBg)
            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(6.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0 until gridSize) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    for (col in 0 until gridSize) {
                        val index = row * gridSize + col
                        val tile = tiles.getOrNull(index) ?: return@Column
                        val inNetwork = index in connectedIndices
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        solved -> tileBgConnected
                                        inNetwork -> tileBgConnected
                                        connectedIndices.isNotEmpty() -> tileBgDisconnected
                                        else -> tileBg
                                    },
                                )
                                .border(
                                    1.dp,
                                    if (inNetwork && !solved) tileBorderConnected else tileBorder,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onTileTap(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            PipeTileCanvas(
                                tile = tile,
                                pipeColor = if (inNetwork || solved) pipeColor else pipeColor.copy(alpha = 0.45f),
                                pipeHighlight = if (inNetwork || solved) pipeHighlight else pipeHighlight.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PipeTileCanvas(
    tile: PipeTile,
    pipeColor: Color,
    pipeHighlight: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.padding(4.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val stroke = min(w, h) * 0.18f
        val hubR = stroke * 0.55f
        val mask = tile.connectionMask()

        fun edgePoint(dir: PipeDir): Offset = when (dir) {
            PipeDir.N -> Offset(cx, 0f)
            PipeDir.E -> Offset(w, cy)
            PipeDir.S -> Offset(cx, h)
            PipeDir.W -> Offset(0f, cy)
        }

        val dirs = PipeDir.entries.filter { mask and it.bit != 0 }
        for (dir in dirs) {
            drawLine(
                color = pipeHighlight,
                start = Offset(cx, cy),
                end = edgePoint(dir),
                strokeWidth = stroke * 1.15f,
                cap = StrokeCap.Round,
            )
        }
        for (dir in dirs) {
            drawLine(
                color = pipeColor,
                start = Offset(cx, cy),
                end = edgePoint(dir),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        drawCircle(color = pipeHighlight, radius = hubR * 1.1f, center = Offset(cx, cy))
        drawCircle(color = pipeColor, radius = hubR, center = Offset(cx, cy))

        when (tile.kind) {
            PipeKind.SOURCE -> {
                drawCircle(
                    color = Color(0xFF4FC3F7),
                    radius = stroke * 0.75f,
                    center = Offset(cx, cy - stroke * 0.35f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = stroke * 0.22f,
                    center = Offset(cx - stroke * 0.18f, cy - stroke * 0.45f),
                )
            }
            PipeKind.SINK -> {
                drawCircle(
                    color = Color(0xFF1565C0),
                    radius = stroke * 0.85f,
                    center = Offset(cx, cy),
                    style = Stroke(width = stroke * 0.35f),
                )
                drawCircle(
                    color = Color(0xFF1565C0).copy(alpha = 0.35f),
                    radius = stroke * 0.45f,
                    center = Offset(cx, cy),
                )
            }
            else -> Unit
        }
    }
}
