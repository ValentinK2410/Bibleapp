package com.example.bible.data

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AudioPlayerHolder"

data class PlayerState(
    val audioPath: String = "",
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val speed: Float = 1.0f,
    val pitch: Float = 0f,
)

object AudioPlayerHolder {

    private var player: MediaPlayer? = null
    private var currentPath: String = ""

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun play(audioPath: String, title: String) {
        if (currentPath == audioPath && player != null) {
            try {
                player!!.start()
                applyParams()
                _state.value = _state.value.copy(isPlaying = true)
            } catch (e: Exception) {
                Log.e(TAG, "resume failed", e)
            }
            return
        }
        release()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(audioPath)
            mp.prepare()
            mp.setOnCompletionListener {
                _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
            }
            mp.start()
            player = mp
            currentPath = audioPath
            _state.value = PlayerState(
                audioPath = audioPath,
                title = title,
                isPlaying = true,
                positionMs = 0,
                durationMs = mp.duration,
                speed = 1.0f,
                pitch = 0f,
            )
        } catch (e: Exception) {
            Log.e(TAG, "play failed: $audioPath", e)
        }
    }

    fun togglePlay() {
        val mp = player ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                mp.start()
                applyParams()
                _state.value = _state.value.copy(isPlaying = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "togglePlay failed", e)
        }
    }

    fun seekTo(ms: Int) {
        try {
            player?.seekTo(ms)
            _state.value = _state.value.copy(positionMs = ms)
        } catch (_: Exception) {}
    }

    fun setSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
        applyParams()
    }

    fun setPitch(pitch: Float) {
        _state.value = _state.value.copy(pitch = pitch)
        applyParams()
    }

    fun stop() {
        try {
            player?.pause()
            player?.seekTo(0)
            _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
        currentPath = ""
        _state.value = PlayerState()
    }

    fun updatePosition() {
        if (_state.value.isPlaying) {
            try {
                player?.let {
                    _state.value = _state.value.copy(positionMs = it.currentPosition)
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyParams() {
        try {
            val s = _state.value
            val pitchFactor = Math.pow(2.0, s.pitch.toDouble() / 12.0).toFloat()
            player?.playbackParams = PlaybackParams()
                .setSpeed(s.speed)
                .setPitch(pitchFactor)
        } catch (e: Exception) {
            Log.w(TAG, "PlaybackParams failed", e)
        }
    }
}
