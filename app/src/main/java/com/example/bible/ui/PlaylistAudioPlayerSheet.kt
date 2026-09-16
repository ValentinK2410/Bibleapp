package com.example.bible.ui

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.data.BibleUserAudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private val SPEED_PRESETS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

class PlaylistAudioPlayerHandle {
    var currentIndex by mutableIntStateOf(0)
        internal set
    var isPlaying by mutableStateOf(false)
        internal set
    var currentTrackId by mutableStateOf<String?>(null)
        internal set

    internal var playAtImpl: (Int) -> Unit = {}
    internal var toggleImpl: () -> Unit = {}

    fun playAt(index: Int) = playAtImpl(index)
    fun togglePlayPause() = toggleImpl()
}

@Suppress("DEPRECATION")
private fun MediaPlayer.applyForwardSpeedAudio(speed: Float) {
    val s = speed.coerceIn(0.5f, 2.5f)
    if (!applyPlaybackSpeedSigned(s, false)) {
        try {
            playbackParams = PlaybackParams().setSpeed(s)
        } catch (_: Exception) {}
    }
}

private suspend fun MediaPlayer.prepareAsyncSuspend(): Boolean =
    suspendCancellableCoroutine { cont ->
        setOnPreparedListener { mp ->
            mp.setOnPreparedListener(null)
            if (cont.isActive) cont.resume(true)
        }
        setOnErrorListener { _, _, _ ->
            setOnErrorListener(null)
            if (cont.isActive) cont.resume(false)
            true
        }
        try {
            prepareAsync()
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(false)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistAudioPlayerSheet(
    tracks: List<Pair<BibleUserAudio, File>>,
    startIndex: Int,
    onDismiss: () -> Unit,
    initialSeekByMediaId: Map<String, Long> = emptyMap(),
    onPlaybackProgress: (mediaId: String, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
    onMarkFullyWatched: (mediaId: String, durationMs: Long) -> Unit = { _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(sheetState) {
        try {
            sheetState.expand()
        } catch (_: Exception) {}
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PlaylistAudioPlayer(
            tracks = tracks,
            startIndex = startIndex,
            embedded = false,
            autoPlayOnStart = true,
            restartOnTrackListChange = true,
            onClose = onDismiss,
            initialSeekByMediaId = initialSeekByMediaId,
            onPlaybackProgress = onPlaybackProgress,
            onMarkFullyWatched = onMarkFullyWatched,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
fun PlaylistAudioPlayer(
    tracks: List<Pair<BibleUserAudio, File>>,
    startIndex: Int,
    modifier: Modifier = Modifier,
    embedded: Boolean = true,
    autoPlayOnStart: Boolean = false,
    restartOnTrackListChange: Boolean = false,
    handle: PlaylistAudioPlayerHandle? = null,
    onClose: (() -> Unit)? = null,
    initialSeekByMediaId: Map<String, Long> = emptyMap(),
    onPlaybackProgress: (mediaId: String, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
    onMarkFullyWatched: (mediaId: String, durationMs: Long) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (tracks.isEmpty()) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Нет файлов для воспроизведения", Toast.LENGTH_SHORT).show()
            onClose?.invoke()
        }
        return
    }

    val safeStart = startIndex.coerceIn(0, tracks.lastIndex)
    val player = remember { MediaPlayer() }

    var currentIx by remember { mutableIntStateOf(safeStart) }
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var reversePlayback by remember { mutableStateOf(false) }
    var reverseSeekFallback by remember { mutableStateOf(false) }
    var continuePlaylist by remember { mutableStateOf(true) }

    var durationMs by remember { mutableIntStateOf(1) }
    var positionMs by remember { mutableIntStateOf(0) }
    var sliderDragging by remember { mutableStateOf(false) }

    val continuePlRef = rememberUpdatedState(continuePlaylist)
    val tracksRef = rememberUpdatedState(tracks)
    val speedRef = rememberUpdatedState(speed)
    val currentIxAtomic = remember { AtomicInteger(safeStart) }

    val initialSeekRef = rememberUpdatedState(initialSeekByMediaId)
    val onProgressRef = rememberUpdatedState(onPlaybackProgress)
    val onMarkWatchedRef = rememberUpdatedState(onMarkFullyWatched)
    val sessionSeekById = remember { initialSeekByMediaId.toMutableMap() }

    LaunchedEffect(initialSeekByMediaId) {
        initialSeekByMediaId.forEach { (id, pos) ->
            if (pos > (sessionSeekById[id] ?: 0L)) {
                sessionSeekById[id] = pos
            }
        }
    }

    fun publishHandle() {
        val h = handle ?: return
        h.currentIndex = currentIx
        h.isPlaying = isPlaying
        h.currentTrackId = tracksRef.value.getOrNull(currentIx)?.first?.id
    }

    fun flushProgress() {
        val id = tracksRef.value.getOrNull(currentIxAtomic.get())?.first?.id ?: return
        val pos = try {
            player.currentPosition.toLong().coerceAtLeast(positionMs.toLong())
        } catch (_: Exception) {
            positionMs.toLong()
        }
        val dur = try {
            player.duration.toLong().coerceAtLeast(1L)
        } catch (_: Exception) {
            durationMs.toLong().coerceAtLeast(1L)
        }
        if (dur < 1_500L && pos < 2_000L) return
        if (pos >= 400L) sessionSeekById[id] = pos
        onProgressRef.value(id, pos, dur)
    }

    fun dismissAndSave() {
        flushProgress()
        try {
            player.stop()
        } catch (_: Exception) {}
        onClose?.invoke()
    }

    DisposableEffect(Unit) {
        onDispose {
            flushProgress()
            try {
                player.release()
            } catch (_: Exception) {}
        }
    }

    suspend fun playIndex(index: Int, start: Boolean = true): Boolean {
        val list = tracksRef.value
        val pair = list.getOrNull(index) ?: return false
        val file = pair.second
        if (!file.exists()) {
            mainHandler.post {
                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (currentIxAtomic.get() != index) {
                    flushProgress()
                }
                reversePlayback = false
                reverseSeekFallback = false
                player.reset()
                player.setDataSource(file.absolutePath)
            }
            val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                player.prepareAsyncSuspend()
            }
            if (!prepared) return false
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                player.applyForwardSpeedAudio(speedRef.value)
                durationMs = player.duration.coerceAtLeast(1)
                val seek = maxOf(
                    sessionSeekById[pair.first.id] ?: 0L,
                    initialSeekRef.value[pair.first.id] ?: 0L,
                ).toInt().coerceAtLeast(0)
                val clamped = if (seek >= 400) seek.coerceIn(0, durationMs) else 0
                if (clamped > 0) {
                    player.seekTo(clamped)
                    positionMs = clamped
                } else {
                    positionMs = 0
                }
                currentIxAtomic.set(index)
                currentIx = index
                player.setOnCompletionListener {
                    val goNext = continuePlRef.value
                    val lst = tracksRef.value
                    val ci = currentIxAtomic.get()
                    val mediaId = lst.getOrNull(ci)?.first?.id
                    mainHandler.post {
                        if (mediaId != null) {
                            onMarkWatchedRef.value(mediaId, durationMs.toLong())
                            onProgressRef.value(mediaId, durationMs.toLong(), durationMs.toLong())
                        }
                        if (goNext && ci + 1 < lst.size) {
                            scope.launch { playIndex(ci + 1) }
                        } else {
                            isPlaying = false
                            publishHandle()
                        }
                    }
                }
                if (start) {
                    player.start()
                    if (clamped > 0) {
                        kotlinx.coroutines.delay(90)
                        val now = try {
                            player.currentPosition
                        } catch (_: Exception) {
                            0
                        }
                        if (kotlin.math.abs(now - clamped) > 1_200) {
                            player.seekTo(clamped)
                            positionMs = clamped
                        }
                    }
                    isPlaying = true
                } else {
                    isPlaying = false
                }
                publishHandle()
            }
            true
        } catch (e: Exception) {
            mainHandler.post {
                Toast.makeText(context, e.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                isPlaying = false
                publishHandle()
            }
            false
        }
    }

    fun togglePlayPause() {
        try {
            if (reversePlayback) {
                when {
                    reverseSeekFallback -> {
                        if (isPlaying) {
                            isPlaying = false
                        } else {
                            if (player.duration <= 0) {
                                scope.launch { playIndex(currentIx) }
                            } else {
                                isPlaying = true
                            }
                        }
                    }
                    player.isPlaying -> {
                        player.pause()
                        isPlaying = false
                    }
                    else -> {
                        if (player.duration <= 0) {
                            scope.launch { playIndex(currentIx) }
                        } else {
                            player.applyPlaybackSpeedSigned(speed, true)
                            player.start()
                            isPlaying = true
                        }
                    }
                }
            } else if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                if (player.duration <= 0) {
                    scope.launch { playIndex(currentIx) }
                } else {
                    player.start()
                    player.applyForwardSpeedAudio(speed)
                    isPlaying = true
                }
            }
            publishHandle()
            com.example.bible.data.AppMediaButtonSession.refreshPlaybackState()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Пауза", Toast.LENGTH_SHORT).show()
        }
    }

    handle?.playAtImpl = { index ->
        scope.launch { playIndex(index, start = true) }
    }
    handle?.toggleImpl = { togglePlayPause() }
    SideEffect { publishHandle() }

    val isPlayingRef = rememberUpdatedState(isPlaying)
    val titleForMedia = rememberUpdatedState(tracks.getOrNull(currentIx)?.first?.title.orEmpty())
    DisposableEffect(player) {
        val unregisterMedia = com.example.bible.data.AppMediaButtonSession.register(
            id = "media_playlist_audio",
            controls = com.example.bible.data.AppMediaButtonSession.Controls(
                title = { titleForMedia.value },
                isPlaying = {
                    try {
                        player.isPlaying
                    } catch (_: Exception) {
                        false
                    }
                },
                playPause = { togglePlayPause() },
                pause = {
                    try {
                        if (player.isPlaying) player.pause()
                        isPlaying = false
                        publishHandle()
                    } catch (_: Exception) {
                    }
                    com.example.bible.data.AppMediaButtonSession.refreshPlaybackState()
                },
                resume = {
                    try {
                        togglePlayPause()
                    } catch (_: Exception) {
                    }
                },
                skipToNext = {
                    mainHandler.post {
                        val next = currentIxAtomic.get() + 1
                        if (next < tracksRef.value.size) {
                            scope.launch { playIndex(next) }
                        }
                    }
                },
            ),
        )
        onDispose { unregisterMedia() }
    }

    val trackKey = remember(tracks) { tracks.joinToString { it.first.id } }
    LaunchedEffect(trackKey, safeStart, restartOnTrackListChange, autoPlayOnStart) {
        if (restartOnTrackListChange) {
            val ok = playIndex(safeStart, start = true)
            if (!ok) {
                Toast.makeText(
                    context,
                    "Не удалось воспроизвести файл. Попробуйте «в другом приложении».",
                    Toast.LENGTH_LONG,
                ).show()
            }
        } else {
            val id = tracksRef.value.getOrNull(currentIxAtomic.get())?.first?.id
            val newIx = tracks.indexOfFirst { it.first.id == id }
            if (newIx >= 0) {
                currentIxAtomic.set(newIx)
                currentIx = newIx
                publishHandle()
            }
        }
    }

    var preparedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(restartOnTrackListChange, autoPlayOnStart) {
        if (!restartOnTrackListChange && !preparedOnce) {
            preparedOnce = true
            playIndex(safeStart, start = autoPlayOnStart)
        }
    }

    LaunchedEffect(isPlaying, sliderDragging, reversePlayback, reverseSeekFallback, currentIx) {
        while (isActive && isPlaying && !sliderDragging) {
            if (reversePlayback && reverseSeekFallback) break
            delay(350)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (player.isPlaying) positionMs = player.currentPosition
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying, currentIx) {
        if (!isPlaying) {
            flushProgress()
            return@LaunchedEffect
        }
        while (isActive) {
            delay(4_000)
            flushProgress()
        }
    }

    LaunchedEffect(reversePlayback, reverseSeekFallback) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val silence = reversePlayback && reverseSeekFallback
                player.setVolume(if (silence) 0f else 1f, if (silence) 0f else 1f)
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(isPlaying, reversePlayback, reverseSeekFallback, sliderDragging, speed, currentIx) {
        if (!reversePlayback || !isPlaying || sliderDragging || !reverseSeekFallback) return@LaunchedEffect
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (player.isPlaying) player.pause()
            }
        } catch (_: Exception) {}
        while (isActive && reversePlayback && isPlaying && !sliderDragging && reverseSeekFallback) {
            val intervalMs =
                (42f / speed.coerceIn(0.5f, 2.5f)).toLong().coerceIn(24L, 100L)
            delay(intervalMs)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val stepMs = (intervalMs * speed.coerceIn(0.5f, 2.5f)).toInt().coerceAtLeast(4)
                    val cur = player.currentPosition.coerceAtLeast(0)
                    val newPos = (cur - stepMs).coerceAtLeast(0)
                    player.seekTo(newPos)
                    positionMs = newPos
                    if (newPos <= 0) {
                        isPlaying = false
                        publishHandle()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(speed, reversePlayback, reverseSeekFallback) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                when {
                    !reversePlayback -> player.applyForwardSpeedAudio(speed)
                    reverseSeekFallback -> { }
                    else -> {
                        if (!player.applyPlaybackSpeedSigned(speed, true)) {
                            reverseSeekFallback = true
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    PlaylistAudioPlayerChrome(
        modifier = modifier,
        embedded = embedded,
        title = tracks.getOrNull(currentIx)?.first?.title.orEmpty(),
        indexLabel = "${currentIx + 1} / ${tracks.size}",
        continuePlaylist = continuePlaylist,
        onToggleQueueMode = { continuePlaylist = !continuePlaylist },
        isPlaying = isPlaying,
        speed = speed,
        onSpeed = { speed = it },
        reversePlayback = reversePlayback,
        reverseSeekFallback = reverseSeekFallback,
        onToggleReverse = {
            try {
                if (!reversePlayback) {
                    val wasPlaying = player.isPlaying || isPlaying
                    val ok = player.applyPlaybackSpeedSigned(speed, true)
                    reverseSeekFallback = !ok
                    reversePlayback = true
                    if (reverseSeekFallback) {
                        player.pause()
                        isPlaying = wasPlaying
                    } else {
                        player.setVolume(1f, 1f)
                        if (wasPlaying) {
                            player.start()
                            isPlaying = true
                        }
                    }
                } else {
                    reversePlayback = false
                    reverseSeekFallback = false
                    player.setVolume(1f, 1f)
                    player.pause()
                    player.seekTo(positionMs)
                    player.applyForwardSpeedAudio(speed)
                    if (isPlaying) {
                        player.start()
                    }
                }
                publishHandle()
            } catch (_: Exception) {}
        },
        onMarkListened = {
            tracks.getOrNull(currentIx)?.first?.id?.let { id ->
                onMarkWatchedRef.value(id, durationMs.toLong())
                Toast.makeText(context, "Отмечено как прослушанное", Toast.LENGTH_SHORT).show()
            }
        },
        positionMs = positionMs,
        durationMs = durationMs,
        onSliderChange = { f ->
            sliderDragging = true
            positionMs = (f * durationMs).toInt().coerceIn(0, durationMs)
        },
        onSliderFinished = {
            sliderDragging = false
            reversePlayback = false
            reverseSeekFallback = false
            try {
                player.setVolume(1f, 1f)
                player.seekTo(positionMs)
                player.applyForwardSpeedAudio(speed)
                if (isPlaying && !player.isPlaying) {
                    player.start()
                }
            } catch (_: Exception) {}
        },
        canGoPrevious = currentIx > 0,
        canGoNext = currentIx < tracks.lastIndex,
        onPrevious = { scope.launch { playIndex(currentIx - 1) } },
        onNext = { scope.launch { playIndex(currentIx + 1) } },
        onTogglePlay = { togglePlayPause() },
        onClose = onClose?.let { { dismissAndSave() } },
    )
}

@Composable
private fun PlaylistAudioPlayerChrome(
    modifier: Modifier,
    embedded: Boolean,
    title: String,
    indexLabel: String,
    continuePlaylist: Boolean,
    onToggleQueueMode: () -> Unit,
    isPlaying: Boolean,
    speed: Float,
    onSpeed: (Float) -> Unit,
    reversePlayback: Boolean,
    reverseSeekFallback: Boolean,
    onToggleReverse: () -> Unit,
    onMarkListened: () -> Unit,
    positionMs: Int,
    durationMs: Int,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (embedded) 28.dp else 20.dp),
        color = scheme.surfaceContainerHigh,
        tonalElevation = if (embedded) 3.dp else 0.dp,
        shadowElevation = if (embedded) 6.dp else 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.primary.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (embedded) 72.dp else 56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(scheme.primary, scheme.tertiary),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (embedded) 36.dp else 28.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        if (embedded) "Сейчас играет" else "Плейлист",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            indexLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                        Text("  ·  ", color = scheme.onSurfaceVariant)
                        Text(
                            if (continuePlaylist) "Далее по списку" else "Только этот файл",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(onClick = onToggleQueueMode),
                        )
                    }
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Slider(
                value = (positionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderFinished,
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.primary.copy(alpha = 0.22f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(enabled = canGoPrevious, onClick = onPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Предыдущий",
                        modifier = Modifier.size(32.dp),
                    )
                }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .size(64.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(enabled = canGoNext, onClick = onNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Следующий",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Text(
                when {
                    !reversePlayback -> "Скорость"
                    reverseSeekFallback -> "Реверс: запасной режим без звука"
                    else -> "Реверс: со звуком (если поддерживает файл)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SPEED_PRESETS.forEach { s ->
                    FilterChip(
                        selected = speed == s && !reversePlayback,
                        onClick = { onSpeed(s) },
                        label = { Text("${s}×") },
                    )
                }
                FilterChip(
                    selected = reversePlayback,
                    onClick = onToggleReverse,
                    label = { Text("Реверс") },
                    leadingIcon = {
                        Icon(Icons.Filled.FastRewind, null, Modifier.height(18.dp))
                    },
                )
                FilterChip(
                    selected = false,
                    onClick = onMarkListened,
                    label = { Text("Прослушано") },
                    leadingIcon = {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.height(18.dp))
                    },
                )
                FilterChip(
                    selected = continuePlaylist,
                    onClick = onToggleQueueMode,
                    label = { Text(if (continuePlaylist) "Очередь" else "Один файл") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.height(18.dp))
                    },
                )
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val r = s % 60
    return "$m:${r.toString().padStart(2, '0')}"
}
