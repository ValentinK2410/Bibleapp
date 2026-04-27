package com.example.bible.data.travel

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.example.bible.data.BibleTtsController
import com.example.bible.data.TranslationId
import com.example.bible.data.applyTtsVoiceForTranslation

/**
 * Озвучивание коротких подсказок при входе в полигон (TextToSpeech).
 * Инициализация и [speak] выполняются на главном потоке.
 * Учитывает [BibleTtsController] (движок, скорость, тон, голос).
 */
object TravelVoicePrompter {

    private val lock = Any()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingUtterance: String? = null
    private var lastEnginePackage: String? = null

    fun speak(applicationContext: Context, text: String) {
        val utter = text.trim()
        if (utter.isEmpty()) return
        val app = applicationContext.applicationContext
        val run = Runnable { ensureAndSpeak(app, utter) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run.run()
        } else {
            Handler(Looper.getMainLooper()).post(run)
        }
    }

    private fun ensureAndSpeak(app: Context, utter: String) {
        synchronized(lock) {
            val user = BibleTtsController.settings.value
            val wantEngine = user.enginePackage.trim()
            if (tts != null && wantEngine != (lastEnginePackage ?: "")) {
                runCatching {
                    tts?.stop()
                    tts?.shutdown()
                }
                tts = null
                ready = false
                lastEnginePackage = null
            }
            if (tts == null) {
                pendingUtterance = utter
                if (wantEngine.isNotEmpty()) {
                    tts = TextToSpeech(
                        app,
                        { status ->
                            synchronized(lock) { onTtsInit(status, wantEngine) }
                        },
                        wantEngine,
                    )
                } else {
                    tts = TextToSpeech(
                        app,
                        { status ->
                            synchronized(lock) { onTtsInit(status, wantEngine) }
                        },
                    )
                }
                lastEnginePackage = wantEngine
            } else if (ready) {
                speakNow(utter)
            } else {
                pendingUtterance = utter
            }
        }
    }

    private fun onTtsInit(status: Int, wantEngine: String) {
        if (status != TextToSpeech.SUCCESS) {
            pendingUtterance = null
            return
        }
        ready = true
        lastEnginePackage = wantEngine
        tts?.let { engine ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
            val u = BibleTtsController.settings.value
            applyTtsVoiceForTranslation(engine, TranslationId.SYNODAL, u)
        }
        val toSpeak = pendingUtterance?.trim()
        pendingUtterance = null
        if (!toSpeak.isNullOrEmpty()) speakNow(toSpeak)
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        val u = BibleTtsController.settings.value
        applyTtsVoiceForTranslation(engine, TranslationId.SYNODAL, u)
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "travel_voice_${System.nanoTime()}")
    }
}
