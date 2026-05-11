package com.example.bible.ui.languagestudy

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class LangStudyTtsFacade internal constructor(
    private val tts: TextToSpeech,
    locale: Locale,
) {
    private val ready = AtomicBoolean(false)

    init {
        tts.language = locale
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onStart(utteranceId: String?) {}
        })
    }

    fun markInitialized(success: Boolean) {
        ready.set(success)
    }

    fun speak(text: String): Boolean {
        if (!ready.get() || text.isBlank()) return false
        return try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ls_${System.nanoTime()}")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }
    }
}

fun localeForStudyLang(languageTag: String): Locale = when (languageTag) {
    "english" -> Locale.US
    "arabic" -> Locale("ar")
    "greek" -> Locale.forLanguageTag("el-GR")
    "irit" -> Locale.forLanguageTag("he-IL")
    else -> Locale.US
}

@Composable
fun rememberLangStudyTts(languageTag: String): LangStudyTtsFacade {
    val context = LocalContext.current
    val locale = localeForStudyLang(languageTag)
    val facade = remember(locale, languageTag) {
        val holder = arrayOfNulls<LangStudyTtsFacade>(1)
        val tts = TextToSpeech(context) { status ->
            holder[0]?.markInitialized(status == TextToSpeech.SUCCESS)
        }
        val f = LangStudyTtsFacade(tts = tts, locale = locale)
        holder[0] = f
        f
    }
    DisposableEffect(facade, languageTag) {
        onDispose { facade.shutdown() }
    }
    return facade
}
