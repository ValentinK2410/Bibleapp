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
    private val languageTag: String,
) {
    private val ready = AtomicBoolean(false)

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onStart(utteranceId: String?) {}
        })
    }

    fun onEngineInitialized(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ready.set(false)
            return
        }
        applyBestLocaleForTag(languageTag)
        if (languageTag == "arabic" || languageTag == "irit") {
            try {
                tts.setSpeechRate(0.92f)
            } catch (_: Exception) {}
        }
        ready.set(true)
    }

    private fun applyBestLocaleForTag(tag: String): Boolean {
        for (loc in candidateLocalesForStudy(tag)) {
            if (tts.setLanguage(loc) != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }
        val r = tts.setLanguage(Locale.US)
        return r != TextToSpeech.LANG_NOT_SUPPORTED
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

private fun candidateLocalesForStudy(languageTag: String): List<Locale> = when (languageTag) {
    "english" -> listOf(Locale.US, Locale.UK, Locale("en"))
    "arabic" -> listOf(
        Locale("ar", "SA"),
        Locale("ar", "EG"),
        Locale("ar", "MA"),
        Locale("ar", "AE"),
        Locale.forLanguageTag("ar-SA"),
        Locale.forLanguageTag("ar-EG"),
        Locale("ar"),
    )
    "irit" -> listOf(
        Locale.forLanguageTag("he-IL"),
        Locale("he", "IL"),
        Locale.forLanguageTag("iw-IL"),
        Locale("iw", "IL"),
        Locale.forLanguageTag("he"),
    )
    "greek" -> listOf(
        Locale.forLanguageTag("el-GR"),
        Locale("el", "GR"),
        Locale("el"),
    )
    else -> listOf(Locale.US)
}

@Composable
fun rememberLangStudyTts(languageTag: String): LangStudyTtsFacade {
    val context = LocalContext.current
    val facade = remember(languageTag) {
        val holder = arrayOfNulls<LangStudyTtsFacade>(1)
        val tts = TextToSpeech(context) { status ->
            holder[0]?.onEngineInitialized(status)
        }
        val f = LangStudyTtsFacade(tts = tts, languageTag = languageTag)
        holder[0] = f
        f
    }
    DisposableEffect(facade, languageTag) {
        onDispose { facade.shutdown() }
    }
    return facade
}
