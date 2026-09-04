package com.example.bible.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.bible.data.BibleUserVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

private val VIDEO_SPEED_PRESETS =
    floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f)

/** Цвет просмотренной части шкалы: яркий на любом кадре, как в привычных плеерах. */
private val PLAYER_ACCENT = Color(0xFFFF4B3E)

/** Минимальная высота области управления: верхняя панель, кнопки и шкала друг на друга не налезают. */
private val CONTROLS_MIN_HEIGHT = 300.dp

@Suppress("DEPRECATION")
private fun MediaPlayer.applyPlaybackSpeedVideoLegacy(speed: Float) {
    val s = speed.coerceIn(0.5f, 2.5f)
    try {
        playbackParams = playbackParams.setSpeed(s)
    } catch (_: Exception) {
        try {
            playbackParams = PlaybackParams().setSpeed(s).setPitch(1f)
        } catch (_: Exception) {}
    }
}

private fun MediaPlayer.applyForwardSpeedVideo(speed: Float) {
    if (!applyPlaybackSpeedSigned(speed, false)) applyPlaybackSpeedVideoLegacy(speed)
}

private fun formatVideoTimelineMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val r = s % 60
    return "$m:${r.toString().padStart(2, '0')}"
}

/** На части кодеков seek до start() сбрасывается; SEEK_CLOSEST точнее ключевого кадра. */
private fun MediaPlayer.seekToNearestMs(ms: Int) {
    val t = ms.coerceAtLeast(0)
    try {
        if (Build.VERSION.SDK_INT >= 26) {
            seekTo(t.toLong(), MediaPlayer.SEEK_CLOSEST)
        } else {
            seekTo(t)
        }
    } catch (_: Exception) {
        try {
            seekTo(t)
        } catch (_: Exception) {
        }
    }
}

private fun trimSpeedLabel(v: Float): String {
    val s = String.format(Locale.US, "%.2f", v)
    return s.trimEnd('0').trimEnd('.')
}

/** Не показывать пользователю технические сообщения вроде «coroutine scope left composition». */
private fun toastPlaybackErrorRu(context: Context, cause: Throwable? = null) {
    if (cause is CancellationException) return
    val raw = cause?.message.orEmpty()
    val safe =
        when {
            raw.contains("coroutine scope", ignoreCase = true) ||
                raw.contains("left the composition", ignoreCase = true) ->
                "Действие прервано (окно уже закрыто). Откройте видео снова."
            raw.contains("surface", ignoreCase = true) ->
                "Не удалось подключить экран воспроизведения."
            raw.isBlank() -> "Не удалось воспроизвести видео."
            else -> "Не удалось воспроизвести видео."
        }
    Toast.makeText(context, safe, Toast.LENGTH_SHORT).show()
}

private suspend fun MediaPlayer.prepareAsyncSuspendVideo(): Boolean =
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryVideoPlayerDialog(
    tracks: List<Pair<BibleUserVideo, File>>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onOpenInOtherApp: (File) -> Unit,
    initialSeekByMediaId: Map<String, Long> = emptyMap(),
    onPlaybackProgress: (mediaId: String, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
    onMarkFullyWatched: (mediaId: String, durationMs: Long) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    /** Колбэки MediaPlayer живут дольше compose-scope — отдельная область, не rememberCoroutineScope. */
    val playbackScope = remember {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (tracks.isEmpty()) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Нет видеофайлов", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        return
    }

    val safeStart = startIndex.coerceIn(0, tracks.lastIndex)

    val player = remember { MediaPlayer() }

    var currentIx by remember { mutableIntStateOf(safeStart) }
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    /** Реверс: сначала нативный (−speed + звук), при отказе кодека — перемотка без звука. */
    var reversePlayback by remember { mutableStateOf(false) }
    var reverseSeekFallback by remember { mutableStateOf(false) }
    /** false — только текущее; true — переходить к следующему в очереди */
    var continueQueue by remember { mutableStateOf(true) }

    var durationMs by remember { mutableIntStateOf(1) }
    var positionMs by remember { mutableIntStateOf(0) }
    var sliderDragging by remember { mutableStateOf(false) }

    /** Размер кадра видео (пиксели), для сохранения пропорций без растягивания. */
    var videoWidthPx by remember { mutableIntStateOf(0) }
    var videoHeightPx by remember { mutableIntStateOf(0) }
    /** Панель управления: показывается по тапу и прячется сама во время воспроизведения. */
    var controlsVisible by remember { mutableStateOf(true) }
    /** Счётчик касаний — перезапускает таймер автоскрытия. */
    var controlsPoke by remember { mutableIntStateOf(0) }
    /** Панель настроек (скорость, реверс, очередь) поверх видео. */
    var settingsOpen by remember { mutableStateOf(false) }
    /** Полноэкранный режим с поворотом в альбомную ориентацию. */
    var fullscreen by remember { mutableStateOf(false) }
    /** Подсказка «−10 с» / «+10 с» после двойного тапа. */
    var seekFeedbackSec by remember { mutableIntStateOf(0) }
    /** Подсказка при свайпе: яркость или громкость. */
    var gestureHint by remember { mutableStateOf<GestureHint?>(null) }
    /** Яркость окна плеера (свайп по левой половине). */
    var brightness by remember { mutableFloatStateOf(0.6f) }

    /** Surface текущего TextureView для MediaPlayer.setSurface(...) */
    var surfaceHolder by remember { mutableStateOf<Surface?>(null) }
    var surfaceReady by remember { mutableStateOf(false) }

    val trackIdsKey = remember(tracks) { tracks.joinToString("|") { it.first.id } }

    val continueRef = rememberUpdatedState(continueQueue)
    val tracksRef = rememberUpdatedState(tracks)
    val speedRef = rememberUpdatedState(speed)
    val currentIxAtomic = remember { AtomicInteger(safeStart) }

    DisposableEffect(Unit) {
        onDispose {
            playbackScope.cancel()
            try {
                player.release()
            } catch (_: Exception) {}
        }
    }

    val initialSeekRef = rememberUpdatedState(initialSeekByMediaId)
    val onProgressRef = rememberUpdatedState(onPlaybackProgress)
    val onMarkWatchedRef = rememberUpdatedState(onMarkFullyWatched)
    val sessionSeekById = remember { initialSeekByMediaId.toMutableMap() }
    var playbackSessionStarted by remember { mutableStateOf(false) }
    var boundPlaybackKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialSeekByMediaId) {
        initialSeekByMediaId.forEach { (id, pos) ->
            if (pos > (sessionSeekById[id] ?: 0L)) {
                sessionSeekById[id] = pos
            }
        }
    }

    fun flushProgress() {
        val id = tracksRef.value.getOrNull(currentIxAtomic.get())?.first?.id ?: return
        val pos = try {
            player.currentPosition.toLong().coerceAtLeast(positionMs.toLong())
        } catch (_: Exception) {
            positionMs.toLong()
        }
        val dur = try {
            player.duration.toLong()
        } catch (_: Exception) {
            durationMs.toLong()
        }
        if (dur < 1_500L && pos < 2_000L) return
        if (pos >= 400L) {
            sessionSeekById[id] = pos
        }
        onProgressRef.value(id, pos, dur.coerceAtLeast(1L))
    }

    DisposableEffect(Unit) {
        onDispose { flushProgress() }
    }

    suspend fun playIndex(index: Int, surfaceNonNull: Surface): Boolean {
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
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (currentIxAtomic.get() != index) {
                    flushProgress()
                }
                reversePlayback = false
                reverseSeekFallback = false
                player.reset()
                player.setSurface(surfaceNonNull)
                player.setDataSource(file.absolutePath)
            }
            val prepared = withContext(kotlinx.coroutines.Dispatchers.Main) {
                player.prepareAsyncSuspendVideo()
            }
            if (!prepared) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    durationMs = 1
                    positionMs = 0
                    videoWidthPx = 0
                    videoHeightPx = 0
                    isPlaying = false
                }
                return false
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                player.applyForwardSpeedVideo(speedRef.value)
                durationMs = player.duration.coerceAtLeast(1)
                val seek = maxOf(
                    sessionSeekById[pair.first.id] ?: 0L,
                    initialSeekRef.value[pair.first.id] ?: 0L,
                ).toInt().coerceAtLeast(0)
                val clamped = if (seek >= 400) seek.coerceIn(0, durationMs) else 0
                if (clamped > 0) {
                    player.seekToNearestMs(clamped)
                    positionMs = clamped
                } else {
                    positionMs = 0
                }
                videoWidthPx = player.videoWidth
                videoHeightPx = player.videoHeight
                player.setOnVideoSizeChangedListener { _, w, h ->
                    mainHandler.post {
                        if (w > 0 && h > 0) {
                            videoWidthPx = w
                            videoHeightPx = h
                        }
                    }
                }
                currentIxAtomic.set(index)
                currentIx = index
                player.setOnCompletionListener {
                    val goNext = continueRef.value
                    val lst = tracksRef.value
                    val ci = currentIxAtomic.get()
                    val mediaId = lst.getOrNull(ci)?.first?.id
                    mainHandler.post {
                        if (mediaId != null) {
                            onProgressRef.value(mediaId, durationMs.toLong(), durationMs.toLong())
                        }
                        if (goNext && ci + 1 < lst.size) {
                            playbackScope.launch { playIndex(ci + 1, surfaceNonNull) }
                        } else {
                            isPlaying = false
                        }
                    }
                }
                player.start()
                try {
                    player.setVolume(1f, 1f)
                } catch (_: Exception) {}
                if (clamped > 0) {
                    delay(90)
                    val now = try {
                        player.currentPosition
                    } catch (_: Exception) {
                        0
                    }
                    if (abs(now - clamped) > 1_200) {
                        player.seekToNearestMs(clamped)
                        positionMs = clamped
                    }
                }
                isPlaying = true
                playbackSessionStarted = true
                mainHandler.post {
                    try {
                        player.applyForwardSpeedVideo(speedRef.value)
                    } catch (_: Exception) {}
                }
            }
            true
        } catch (e: Exception) {
            mainHandler.post {
                toastPlaybackErrorRu(context, e)
                isPlaying = false
                durationMs = 1
                positionMs = 0
            }
            false
        }
    }
    LaunchedEffect(surfaceHolder, safeStart, trackIdsKey) {
        val surface = surfaceHolder ?: return@LaunchedEffect
        surfaceReady = true
        val sessionKey = "$trackIdsKey#$safeStart"
        if (boundPlaybackKey == sessionKey) {
            try {
                player.setSurface(surface)
            } catch (_: Exception) {
                val ix = currentIxAtomic.get().coerceIn(0, tracksRef.value.lastIndex)
                playIndex(ix, surface)
            }
            return@LaunchedEffect
        }
        val ok = playIndex(safeStart, surface)
        if (ok) boundPlaybackKey = sessionKey
        if (!ok) {
            durationMs = 1
            positionMs = 0
            Toast.makeText(context, "Не удалось открыть это видео", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isPlaying, sliderDragging, reversePlayback, reverseSeekFallback, currentIx) {
        while (isActive && isPlaying && !sliderDragging) {
            if (reversePlayback && reverseSeekFallback) break
            delay(350)
            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (player.isPlaying) {
                        positionMs = player.currentPosition
                        val id = tracksRef.value.getOrNull(currentIxAtomic.get())?.first?.id
                        if (id != null && positionMs >= 400) {
                            sessionSeekById[id] = positionMs.toLong()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying, currentIx, positionMs, durationMs) {
        if (!isPlaying) return@LaunchedEffect
        delay(5_000)
        flushProgress()
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            playbackSessionStarted = true
        } else if (playbackSessionStarted) {
            flushProgress()
        }
    }

    LaunchedEffect(reversePlayback, reverseSeekFallback) {
        try {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val silence = reversePlayback && reverseSeekFallback
                player.setVolume(if (silence) 0f else 1f, if (silence) 0f else 1f)
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(isPlaying, reversePlayback, reverseSeekFallback, sliderDragging, speed, currentIx) {
        if (!reversePlayback || !isPlaying || sliderDragging || !reverseSeekFallback) return@LaunchedEffect
        try {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (player.isPlaying) player.pause()
            }
        } catch (_: Exception) {}
        while (isActive && reversePlayback && isPlaying && !sliderDragging && reverseSeekFallback) {
            val intervalMs =
                (42f / speed.coerceIn(0.5f, 2.5f)).toLong().coerceIn(24L, 100L)
            delay(intervalMs)
            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val stepMs = (intervalMs * speed.coerceIn(0.5f, 2.5f)).toInt().coerceAtLeast(4)
                    val cur = player.currentPosition.coerceAtLeast(0)
                    val newPos = (cur - stepMs).coerceAtLeast(0)
                    player.seekTo(newPos)
                    positionMs = newPos
                    if (newPos <= 0) {
                        isPlaying = false
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /** Если −speed принят, но кадр/время не идут назад — переключаемся на пошаговый seek. */
    LaunchedEffect(isPlaying, reversePlayback, reverseSeekFallback, currentIx, speed, durationMs) {
        if (!reversePlayback || reverseSeekFallback || !isPlaying) return@LaunchedEffect
        delay(520)
        if (!reversePlayback || reverseSeekFallback || !isPlaying) return@LaunchedEffect
        val p0 =
            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) { player.currentPosition }
            } catch (_: Exception) {
                return@LaunchedEffect
            }
        delay(320)
        if (!reversePlayback || reverseSeekFallback || !isPlaying) return@LaunchedEffect
        val p1 =
            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) { player.currentPosition }
            } catch (_: Exception) {
                return@LaunchedEffect
            }
        val moved = kotlin.math.abs(p1 - p0)
        val bad = (p1 > p0 + 20) || (moved < 4 && durationMs > 800)
        if (bad) {
            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    player.pause()
                    player.applyForwardSpeedVideo(speed)
                }
            } catch (_: Exception) {}
            reverseSeekFallback = true
        }
    }

    LaunchedEffect(speed, reversePlayback, reverseSeekFallback) {
        try {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                when {
                    !reversePlayback -> player.applyForwardSpeedVideo(speed)
                    reverseSeekFallback -> { /* пауза + seek, параметры не трогаем */ }
                    else -> {
                        if (!player.applyPlaybackSpeedSigned(speed, true)) {
                            reverseSeekFallback = true
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun finishPlayerUi() {
        flushProgress()
        try {
            player.pause()
            player.stop()
            player.setSurface(null)
        } catch (_: Exception) {}
        try {
            surfaceHolder?.release()
        } catch (_: Exception) {}
        surfaceHolder = null
        onDismiss()
    }

    fun applySeekTo(targetMs: Int) {
        val target = targetMs.coerceIn(0, durationMs)
        reversePlayback = false
        reverseSeekFallback = false
        try {
            player.setVolume(1f, 1f)
            player.seekToNearestMs(target)
            player.applyForwardSpeedVideo(speed)
            if (isPlaying && !player.isPlaying) player.start()
        } catch (_: Exception) {}
        positionMs = target
    }

    fun togglePlayPause() {
        try {
            if (reversePlayback) {
                when {
                    reverseSeekFallback -> {
                        if (isPlaying) {
                            isPlaying = false
                        } else {
                            val sVal = surfaceHolder ?: return
                            if (player.duration <= 0) {
                                playbackScope.launch { playIndex(currentIx, sVal) }
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
                        val sVal = surfaceHolder ?: return
                        if (player.duration <= 0) {
                            playbackScope.launch { playIndex(currentIx, sVal) }
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
                val sVal = surfaceHolder ?: return
                if (player.duration <= 0) {
                    playbackScope.launch { playIndex(currentIx, sVal) }
                } else {
                    player.start()
                    player.applyForwardSpeedVideo(speed)
                    isPlaying = true
                }
            }
        } catch (e: Exception) {
            toastPlaybackErrorRu(context, e)
        }
    }

    Dialog(
        onDismissRequest = { finishPlayerUi() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        val activity = remember(context) { context.findActivityOrNull() }
        val audioManager = remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
        val maxVolume = remember(audioManager) {
            (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        }

        DisposableEffect(dialogWindow, view) {
            val window = dialogWindow
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, view).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (window != null) {
                    WindowInsetsControllerCompat(window, view)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        LaunchedEffect(fullscreen) {
            activity?.requestedOrientation = if (fullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        // Панель прячется сама, как в YouTube: во время игры — через несколько секунд бездействия.
        LaunchedEffect(controlsVisible, isPlaying, controlsPoke, settingsOpen) {
            if (controlsVisible && isPlaying && !settingsOpen) {
                delay(3_500)
                controlsVisible = false
            }
        }

        LaunchedEffect(seekFeedbackSec) {
            if (seekFeedbackSec != 0) {
                delay(700)
                seekFeedbackSec = 0
            }
        }

        LaunchedEffect(gestureHint) {
            if (gestureHint != null) {
                delay(900)
                gestureHint = null
            }
        }

        fun pokeControls() {
            controlsVisible = true
            controlsPoke++
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Размер кадра на экране: по нему выравниваем управление, чтобы оно
                // лежало на видео, а не улетало к краям телефона.
                val videoAsp =
                    if (videoWidthPx > 0 && videoHeightPx > 0) {
                        videoWidthPx.toFloat() / videoHeightPx.toFloat()
                    } else {
                        null
                    }
                val containerAsp = if (maxHeight > 0.dp) maxWidth / maxHeight else 1f
                val videoWidth: Dp
                val videoHeight: Dp
                if (videoAsp == null) {
                    videoWidth = maxWidth
                    videoHeight = maxHeight
                } else if (videoAsp > containerAsp) {
                    videoWidth = maxWidth
                    videoHeight = maxWidth / videoAsp
                } else {
                    videoWidth = maxHeight * videoAsp
                    videoHeight = maxHeight
                }
                // Трём рядам кнопок нужна своя высота: у низкого кадра панели
                // выходят чуть за его границы, но остаются рядом с картинкой.
                val controlsHeight = maxOf(videoHeight, minOf(maxHeight, CONTROLS_MIN_HEIGHT))

                Box(
                    modifier = Modifier
                        .width(maxWidth)
                        .height(controlsHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        modifier = Modifier
                            .width(videoWidth)
                            .height(videoHeight),
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                surfaceTextureListener =
                                    object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(
                                            surfTex: SurfaceTexture,
                                            width: Int,
                                            height: Int,
                                        ) {
                                            try {
                                                surfaceHolder?.release()
                                            } catch (_: Exception) {}
                                            surfaceHolder = Surface(surfTex)
                                        }

                                        override fun onSurfaceTextureSizeChanged(
                                            surfTex: SurfaceTexture,
                                            width: Int,
                                            height: Int,
                                        ) {}

                                        override fun onSurfaceTextureDestroyed(surfTex: SurfaceTexture): Boolean {
                                            surfaceReady = false
                                            try {
                                                player.setSurface(null)
                                            } catch (_: Exception) {}
                                            surfaceHolder?.release()
                                            surfaceHolder = null
                                            return true
                                        }

                                        override fun onSurfaceTextureUpdated(surfTex: SurfaceTexture) {}
                                    }
                            }
                        },
                    )

                // Жесты поверх всего кадра: тап — панель, двойной тап — ±10 с,
                // вертикальный свайп — яркость (слева) и громкость (справа), горизонтальный — перемотка.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(durationMs) {
                            detectTapGestures(
                                onTap = {
                                    if (controlsVisible) controlsVisible = false else pokeControls()
                                },
                                onDoubleTap = { offset ->
                                    val back = offset.x < size.width / 2f
                                    applySeekTo(positionMs + if (back) -10_000 else 10_000)
                                    seekFeedbackSec = if (back) -10 else 10
                                    pokeControls()
                                },
                            )
                        }
                        .pointerInput(durationMs, maxVolume) {
                            var mode = 0 // 1 — перемотка, 2 — яркость, 3 — громкость
                            var startX = 0f
                            var accum = 0f
                            var target = 0
                            detectDragGestures(
                                onDragStart = { offset ->
                                    mode = 0
                                    startX = offset.x
                                    accum = 0f
                                    target = positionMs
                                },
                                onDragCancel = {
                                    if (mode == 1) sliderDragging = false
                                    mode = 0
                                    gestureHint = null
                                },
                                onDragEnd = {
                                    if (mode == 1) {
                                        sliderDragging = false
                                        applySeekTo(target)
                                    }
                                    mode = 0
                                    gestureHint = null
                                },
                            ) { change, drag ->
                                change.consume()
                                if (mode == 0) {
                                    mode = when {
                                        abs(drag.x) > abs(drag.y) -> 1
                                        startX < size.width / 2f -> 2
                                        else -> 3
                                    }
                                    if (mode == 1) {
                                        sliderDragging = true
                                        pokeControls()
                                    }
                                }
                                when (mode) {
                                    1 -> {
                                        val span = minOf(durationMs, 120_000).coerceAtLeast(10_000)
                                        val msPerPx = span.toFloat() / size.width.toFloat()
                                        target = (target + drag.x * msPerPx).toInt()
                                            .coerceIn(0, durationMs)
                                        positionMs = target
                                        pokeControls()
                                    }
                                    2 -> {
                                        accum -= drag.y
                                        val step = size.height / 220f
                                        if (abs(accum) >= step) {
                                            val delta = (accum / step) * 0.01f
                                            accum = 0f
                                            val next = (brightness + delta).coerceIn(0.02f, 1f)
                                            brightness = next
                                            dialogWindow?.let { w ->
                                                w.attributes = w.attributes.apply {
                                                    screenBrightness = next
                                                }
                                            }
                                            gestureHint = GestureHint(
                                                brightnessHint = true,
                                                value = next,
                                                label = "${(next * 100).roundToInt()}%",
                                            )
                                        }
                                    }
                                    else -> {
                                        accum -= drag.y
                                        val step = size.height / (maxVolume * 1.6f)
                                        if (abs(accum) >= step) {
                                            val steps = (accum / step).toInt()
                                            accum = 0f
                                            val cur = audioManager
                                                ?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                            val next = (cur + steps).coerceIn(0, maxVolume)
                                            audioManager?.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                next,
                                                0,
                                            )
                                            gestureHint = GestureHint(
                                                brightnessHint = false,
                                                value = next.toFloat() / maxVolume,
                                                label = "$next / $maxVolume",
                                            )
                                        }
                                    }
                                }
                            }
                        },
                )

                // Крупная подсказка при двойном тапе
                androidx.compose.animation.AnimatedVisibility(
                    visible = seekFeedbackSec != 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(if (seekFeedbackSec < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 48.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (seekFeedbackSec < 0) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (seekFeedbackSec < 0) "−" else "+"}10 с",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                gestureHint?.let { hint ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            if (hint.brightnessHint) {
                                Icons.Filled.BrightnessHigh
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(hint.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { hint.value.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(120.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // Верхняя панель
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.72f),
                                        1f to Color.Transparent,
                                    ),
                                )
                                .then(if (fullscreen) Modifier.statusBarsPadding() else Modifier)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { finishPlayerUi() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Закрыть",
                                    tint = Color.White,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tracks.getOrNull(currentIx)?.first?.title.orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (tracks.size > 1) {
                                    Text(
                                        "${currentIx + 1} из ${tracks.size}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val f = tracksRef.value
                                        .getOrNull(currentIxAtomic.get())?.second ?: return@IconButton
                                    onOpenInOtherApp(f)
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Открыть в другом приложении",
                                    tint = Color.White,
                                )
                            }
                            IconButton(
                                onClick = {
                                    settingsOpen = true
                                    pokeControls()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Настройки воспроизведения",
                                    tint = Color.White,
                                )
                            }
                        }

                        // Центральный транспорт
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            IconButton(
                                enabled = surfaceReady && currentIx > 0,
                                onClick = {
                                    val sVal = surfaceHolder ?: return@IconButton
                                    playbackScope.launch { playIndex(currentIx - 1, sVal) }
                                    pokeControls()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.SkipPrevious,
                                    contentDescription = "Предыдущее",
                                    tint = if (currentIx > 0) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    applySeekTo(positionMs - 10_000)
                                    seekFeedbackSec = -10
                                    pokeControls()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Replay10,
                                    contentDescription = "Назад на 10 секунд",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(
                                    onClick = {
                                        togglePlayPause()
                                        pokeControls()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    applySeekTo(positionMs + 10_000)
                                    seekFeedbackSec = 10
                                    pokeControls()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Forward10,
                                    contentDescription = "Вперёд на 10 секунд",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            IconButton(
                                enabled = surfaceReady && currentIx < tracks.lastIndex,
                                onClick = {
                                    val sVal = surfaceHolder ?: return@IconButton
                                    playbackScope.launch { playIndex(currentIx + 1, sVal) }
                                    pokeControls()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    contentDescription = "Следующее",
                                    tint = if (currentIx < tracks.lastIndex) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.35f)
                                    },
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }

                        // Нижняя панель: время, шкала, скорость и полноэкранный режим
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.82f),
                                    ),
                                )
                                .then(if (fullscreen) Modifier.navigationBarsPadding() else Modifier)
                                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                        ) {
                            val playedFraction =
                                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            Slider(
                                value = playedFraction,
                                onValueChange = { f ->
                                    sliderDragging = true
                                    positionMs = (f * durationMs).toInt()
                                        .coerceIn(0, durationMs)
                                    pokeControls()
                                },
                                onValueChangeFinished = {
                                    sliderDragging = false
                                    applySeekTo(positionMs)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp),
                                thumb = {
                                    Box(
                                        Modifier
                                            .size(if (sliderDragging) 16.dp else 11.dp)
                                            .clip(CircleShape)
                                            .background(PLAYER_ACCENT),
                                    )
                                },
                                track = {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White.copy(alpha = 0.28f)),
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(playedFraction)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(PLAYER_ACCENT),
                                        )
                                    }
                                },
                            )

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${formatVideoTimelineMs(positionMs)} / ${formatVideoTimelineMs(durationMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        settingsOpen = true
                                        pokeControls()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                ) {
                                    Text(
                                        "${trimSpeedLabel(speed)}×",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IconButton(
                                        onClick = {
                                            fullscreen = !fullscreen
                                            pokeControls()
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Icon(
                                            if (fullscreen) {
                                                Icons.Filled.FullscreenExit
                                            } else {
                                                Icons.Filled.Fullscreen
                                            },
                                            contentDescription = if (fullscreen) {
                                                "Выйти из полноэкранного режима"
                                            } else {
                                                "Развернуть на весь экран"
                                            },
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }


            if (settingsOpen) {
                VideoPlayerSettingsPanel(
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
                                player.applyForwardSpeedVideo(speed)
                                if (isPlaying) player.start()
                            }
                        } catch (_: Exception) {}
                    },
                    continueQueue = continueQueue,
                    onContinueQueue = { continueQueue = it },
                    showQueueOption = tracks.size > 1,
                    onMarkWatched = {
                        tracks.getOrNull(currentIx)?.first?.id?.let { id ->
                            onMarkWatchedRef.value(id, durationMs.toLong())
                            Toast.makeText(
                                context,
                                "Отмечено как просмотренное",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onClose = {
                        settingsOpen = false
                        pokeControls()
                    },
                )
            }
        }
    }
}

/** Подсказка при свайпе: яркость или громкость. */
private data class GestureHint(
    val brightnessHint: Boolean,
    val value: Float,
    val label: String,
)

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}

/** Настройки поверх видео: скорость, реверс, очередь — как «шестерёнка» в YouTube. */
@Composable
private fun VideoPlayerSettingsPanel(
    speed: Float,
    onSpeed: (Float) -> Unit,
    reversePlayback: Boolean,
    reverseSeekFallback: Boolean,
    onToggleReverse: () -> Unit,
    continueQueue: Boolean,
    onContinueQueue: (Boolean) -> Unit,
    showQueueOption: Boolean,
    onMarkWatched: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1B1B1B))
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Скорость ${trimSpeedLabel(speed)}×",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VIDEO_SPEED_PRESETS.forEach { s ->
                    FilterChip(
                        selected = abs(speed - s) < 0.04f,
                        onClick = { onSpeed(s) },
                        label = { Text("${trimSpeedLabel(s)}×") },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    onClick = onMarkWatched,
                    label = { Text("Просмотрено") },
                    leadingIcon = {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.height(18.dp))
                    },
                )
            }

            if (reversePlayback && reverseSeekFallback) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Реверс без звука: устройство не поддерживает обратное воспроизведение этого файла.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            if (showQueueOption) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "После окончания",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !continueQueue,
                        onClick = { onContinueQueue(false) },
                        label = { Text("Остановиться") },
                    )
                    FilterChip(
                        selected = continueQueue,
                        onClick = { onContinueQueue(true) },
                        label = { Text("Далее по списку") },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                null,
                                Modifier.height(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
