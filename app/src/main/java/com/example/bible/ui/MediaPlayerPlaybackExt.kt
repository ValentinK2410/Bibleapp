package com.example.bible.ui

import android.media.MediaPlayer
import android.media.PlaybackParams

/**
 * Скорость воспроизведения, в том числе обратная (отрицательная), через [PlaybackParams] (API 23+).
 * Для многих **аудио** и части **видео** даёт нормальный звук в реверсе; при отказе кодека возвращает false.
 */
@Suppress("DEPRECATION")
fun MediaPlayer.applyPlaybackSpeedSigned(speedAbs: Float, reverse: Boolean): Boolean {
    val s = speedAbs.coerceIn(0.5f, 2.5f)
    val signed = if (reverse) -s else s
    return try {
        playbackParams = PlaybackParams().setSpeed(signed).setPitch(1f)
        true
    } catch (_: Exception) {
        try {
            playbackParams = playbackParams.setSpeed(signed).setPitch(1f)
            true
        } catch (_: Exception) {
            false
        }
    }
}
