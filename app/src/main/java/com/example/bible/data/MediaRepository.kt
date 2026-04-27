package com.example.bible.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioPlaybackState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR,
}

class MediaRepository(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow(AudioPlaybackState.IDLE)
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    companion object {
        val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }

    private fun findAssetAudioPath(translation: TranslationId, bookId: String, chapter: Int): String? {
        val base = "audio/${translation.code}/$bookId"
        val candidates = listOf(
            "$base/${chapter.toString().padStart(2, '0')}.mp3",
            "$base/$chapter.mp3",
        )
        for (path in candidates) {
            try {
                context.assets.open(path).close()
                return path
            } catch (_: Exception) { }
        }
        return null
    }

    fun hasChapterAudio(translation: TranslationId, bookId: String, chapter: Int): Boolean =
        findAssetAudioPath(translation, bookId, chapter) != null

    fun hasChapterAudioDownloaded(narratorId: String, bookId: String, chapter: Int): Boolean =
        isChapterDownloaded(context, narratorId, bookId, chapter)

    fun hasBookAudio(translation: TranslationId, bookId: String): Boolean {
        val base = "audio/${translation.code}/$bookId"
        return try {
            val files = context.assets.list(base)
            files != null && files.any { it.endsWith(".mp3") }
        } catch (_: Exception) {
            false
        }
    }

    fun booksWithAudio(translation: TranslationId): Set<String> {
        val base = "audio/${translation.code}"
        val assetBooks = try {
            val dirs = context.assets.list(base) ?: emptyArray()
            dirs.filter { bookId ->
                try {
                    val files = context.assets.list("$base/$bookId")
                    files != null && files.any { it.endsWith(".mp3") }
                } catch (_: Exception) {
                    false
                }
            }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        return assetBooks
    }

    fun booksWithDownloaded(narratorId: String): Set<String> =
        booksWithDownloadedAudio(context, narratorId)

    fun downloadedChaptersFor(narratorId: String, bookId: String): Set<Int> =
        downloadedChapters(context, narratorId, bookId)

    fun playChapterAudio(
        translation: TranslationId,
        bookId: String,
        chapter: Int,
        onError: () -> Unit = {},
    ) {
        val localPath = findAssetAudioPath(translation, bookId, chapter)
        if (localPath != null) {
            playFromAssets(localPath, onError)
        } else {
            val url = ServerConfig.audioUrl(translation, bookId, chapter)
            playFromUrl(url, onError = onError)
        }
    }

    private fun playFromAssets(
        assetPath: String,
        onError: () -> Unit = {},
    ) {
        stop()
        _playbackState.value = AudioPlaybackState.LOADING
        _currentUrl.value = "asset://$assetPath"
        var afd: AssetFileDescriptor? = null
        try {
            afd = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setOnPreparedListener {
                    applySpeed(this)
                    _playbackState.value = AudioPlaybackState.PLAYING
                    start()
                }
                setOnCompletionListener {
                    _playbackState.value = AudioPlaybackState.IDLE
                    _currentUrl.value = null
                }
                setOnErrorListener { _, _, _ ->
                    _playbackState.value = AudioPlaybackState.ERROR
                    _currentUrl.value = null
                    onError()
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            _playbackState.value = AudioPlaybackState.ERROR
            _currentUrl.value = null
            onError()
        } finally {
            try { afd?.close() } catch (_: Exception) {}
        }
    }

    fun playFromUrl(
        url: String,
        onComplete: () -> Unit = {},
        onError: () -> Unit = {},
    ) {
        stop()
        _playbackState.value = AudioPlaybackState.LOADING
        _currentUrl.value = url
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    applySpeed(this)
                    _playbackState.value = AudioPlaybackState.PLAYING
                    start()
                }
                setOnCompletionListener {
                    _playbackState.value = AudioPlaybackState.IDLE
                    _currentUrl.value = null
                    onComplete()
                }
                setOnErrorListener { _, _, _ ->
                    _playbackState.value = AudioPlaybackState.ERROR
                    _currentUrl.value = null
                    onError()
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            _playbackState.value = AudioPlaybackState.ERROR
            _currentUrl.value = null
            onError()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _playbackState.value = AudioPlaybackState.PAUSED
                }
            }
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                if (_playbackState.value == AudioPlaybackState.PAUSED) {
                    it.start()
                    _playbackState.value = AudioPlaybackState.PLAYING
                }
            }
        } catch (_: Exception) {}
    }

    fun togglePauseResume() {
        when (_playbackState.value) {
            AudioPlaybackState.PLAYING -> pause()
            AudioPlaybackState.PAUSED -> resume()
            else -> {}
        }
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.let { applySpeed(it) }
    }

    fun cycleSpeed() {
        val current = _playbackSpeed.value
        val idx = SPEED_OPTIONS.indexOfFirst { it >= current }
        val next = if (idx < 0 || idx >= SPEED_OPTIONS.lastIndex) SPEED_OPTIONS[0] else SPEED_OPTIONS[idx + 1]
        setSpeed(next)
    }

    private fun applySpeed(player: MediaPlayer) {
        try {
            player.playbackParams = PlaybackParams().setSpeed(_playbackSpeed.value)
        } catch (_: Exception) {}
    }

    fun getProgress(): Pair<Int, Int> {
        return try {
            val mp = mediaPlayer ?: return 0 to 0
            mp.currentPosition to mp.duration
        } catch (_: Exception) {
            0 to 0
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _playbackState.value = AudioPlaybackState.IDLE
        _currentUrl.value = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
}
