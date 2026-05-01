package com.example.bible

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Ленивая инициализация [TextToSpeech] для озвучки входящих SMS (главный поток).
 */
object IncomingSmsSpeak {
    internal const val MAX_UTTERANCE_CHARS = 1200

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var initializing = false
    private var pendingText: String? = null

    fun speak(appContext: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val app = appContext.applicationContext
        mainHandler.post {
            speakOnMain(app, trimmed)
        }
    }

    private fun speakOnMain(app: Context, text: String) {
        tts?.let { engine ->
            runCatching {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sms_${System.nanoTime()}")
            }
            return
        }
        if (initializing) {
            pendingText = text
            return
        }
        initializing = true
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(app) { status ->
            initializing = false
            if (status != TextToSpeech.SUCCESS) {
                engine.shutdown()
                pendingText = null
                return@TextToSpeech
            }
            runCatching {
                engine.language = Locale.getDefault()
                tts = engine
                val utterance = pendingText ?: text
                pendingText = null
                engine.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "sms_${System.nanoTime()}")
            }
        }
    }
}
