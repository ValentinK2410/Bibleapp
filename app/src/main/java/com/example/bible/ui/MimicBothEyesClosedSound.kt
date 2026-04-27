package com.example.bible.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Короткий тон при жесте «оба глаза закрыты» (системный сигнал уведомления).
 */
internal fun playBothEyesClosedTone() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 88)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { tone.release() }
        }, 260)
    } catch (_: Exception) {
        // Нет аудио / режим «без звука» — тихо пропускаем.
    }
}

/**
 * Один короткий звук на **фронте** «оба глаза закрыты по геометрии века» ([leftEyeOpenFromGeometry]/[rightEyeOpenFromGeometry] == false).
 * Не используем [ExperimentMimicSignals.leftEyeOpen]: при отсутствии пятна зрачка глаз принудительно «закрывается»,
 * что давало ложный сигнал, когда зрачок просто не виден в кадре.
 */
internal class BothEyesClosedSoundOnEdge {
    private var wasBothClosed = false

    fun consume(m: ExperimentMimicSignals?) {
        val present = m?.facePresent == true
        val bothClosed = present &&
            m.leftEyeOpenFromGeometry == false &&
            m.rightEyeOpenFromGeometry == false
        if (bothClosed && !wasBothClosed) {
            playBothEyesClosedTone()
        }
        wasBothClosed = bothClosed
        if (!present) wasBothClosed = false
    }
}
