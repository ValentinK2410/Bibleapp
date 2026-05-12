package com.example.bible.ui.languagestudy

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCompleteActions = mutableMapOf<String, () -> Unit>()

    init {
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    fireUtteranceCompletion(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    fireUtteranceCompletion(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    fireUtteranceCompletion(utteranceId)
                }
            },
        )
    }

    private fun fireUtteranceCompletion(utteranceId: String?) {
        if (utteranceId == null) return
        utteranceCompleteActions.remove(utteranceId)?.let { mainHandler.post(it) }
    }

    fun onEngineInitialized(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ready.set(false)
            return
        }
        restoreStudyVoiceAndTune()
        ready.set(true)
    }

    private fun restoreStudyVoiceAndTune() {
        if (!pickAndSetNativeVoice()) {
            applyBestLocaleForTag(languageTag)
        }
        applySpeechTuningForStudy(languageTag)
    }

    private fun pickAndSetNativeVoice(): Boolean {
        val voice = pickNativeStudyVoice(tts, languageTag) ?: return false
        return try {
            tts.setVoice(voice) == TextToSpeech.SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    private fun applyBestLocaleForTag(tag: String): Boolean {
        for (loc in candidateLocalesForStudy(tag)) {
            if (tts.setLanguage(loc) != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }
        val r = tts.setLanguage(Locale.UK)
        return r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun applySpeechTuningForStudy(tag: String) {
        try {
            when (tag) {
                "arabic", "irit" -> {
                    tts.setSpeechRate(0.92f)
                    tts.setPitch(1.0f)
                }
                "greek" -> {
                    tts.setSpeechRate(0.94f)
                    tts.setPitch(1.0f)
                }
                "english" -> {
                    tts.setSpeechRate(0.98f)
                    tts.setPitch(1.0f)
                }
                else -> {
                    tts.setSpeechRate(1.0f)
                    tts.setPitch(1.0f)
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Короткая форма без колбэка (язык пакета / изучения). */
    fun speak(text: String): Boolean = speakStudy(text, null)

    /**
     * Озвучка на языке изучения (голос родной акцент по [languageTag]).
     * @param onFullySpoken вызывается после окончания фразы (или ошибке движка для этого utterance).
     */
    fun speakStudy(text: String, onFullySpoken: (() -> Unit)? = null): Boolean {
        if (text.isBlank()) {
            onFullySpoken?.let { mainHandler.post(it) }
            return false
        }
        if (!ready.get()) {
            onFullySpoken?.let { mainHandler.post(it) }
            return false
        }
        utteranceCallbacksReset()
        try {
            tts.stop()
        } catch (_: Exception) {
        }
        restoreStudyVoiceAndTune()
        val uid = "ls_${System.nanoTime()}"
        onFullySpoken?.let { utteranceCompleteActions[uid] = it }
        return try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)
            true
        } catch (_: Exception) {
            utteranceCompleteActions.remove(uid)
            onFullySpoken?.let { mainHandler.post(it) }
            false
        }
    }

    /** Русский перевод gloss / пример; после фразы возвращаются голос и тюнинг языка изучения. */
    fun speakRussian(text: String, onFullySpoken: (() -> Unit)? = null): Boolean {
        if (text.isBlank()) {
            onFullySpoken?.let { mainHandler.post(it) }
            return false
        }
        if (!ready.get()) {
            onFullySpoken?.let { mainHandler.post(it) }
            return false
        }
        utteranceCallbacksReset()
        try {
            tts.stop()
        } catch (_: Exception) {
        }
        val uid = "ls_ru_${System.nanoTime()}"
        utteranceCompleteActions[uid] = {
            restoreStudyVoiceAndTune()
            onFullySpoken?.invoke()
        }
        val ru = Locale.forLanguageTag("ru-RU")
        var r = tts.setLanguage(ru)
        if (
            r == TextToSpeech.LANG_MISSING_DATA ||
            r == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            r = tts.setLanguage(Locale("ru"))
        }
        if (
            r == TextToSpeech.LANG_MISSING_DATA ||
            r == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.setLanguage(Locale.US)
        }
        return try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)
            true
        } catch (_: Exception) {
            utteranceCompleteActions.remove(uid)
            restoreStudyVoiceAndTune()
            onFullySpoken?.let { mainHandler.post(it) }
            false
        }
    }

    private fun utteranceCallbacksReset() {
        utteranceCompleteActions.clear()
    }

    fun shutdown() {
        utteranceCompleteActions.clear()
        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }
    }
}

/**
 * Локали в порядке приоритета: сначала родной регион (акцент страны), затем общий язык.
 * Для english — сначала British English (en-GB), затем американский и нейтральный en.
 */
@Suppress("DEPRECATION")
private fun candidateLocalesForStudy(languageTag: String): List<Locale> =
    when (languageTag) {
        "english" -> listOf(
            Locale.UK,
            Locale.forLanguageTag("en-GB"),
            Locale("en", "GB"),
            Locale.US,
            Locale.forLanguageTag("en-US"),
            Locale("en", "US"),
            Locale("en"),
        )
        "arabic" -> listOf(
            Locale("ar", "SA"),
            Locale.forLanguageTag("ar-SA"),
            Locale("ar", "EG"),
            Locale.forLanguageTag("ar-EG"),
            Locale("ar", "AE"),
            Locale.forLanguageTag("ar-AE"),
            Locale("ar", "JO"),
            Locale.forLanguageTag("ar-JO"),
            Locale("ar", "MA"),
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
            Locale.forLanguageTag("el-CY"),
            Locale("el"),
        )
        else -> listOf(Locale.UK, Locale.US)
    }

private fun canonicalTtsTag(languageTag: String, raw: String): String {
    var t = raw.lowercase(Locale.ROOT).replace('_', '-')
    if (languageTag == "irit" && (t == "iw-il" || t.startsWith("iw-"))) {
        t = t.replaceFirst("iw", "he")
    }
    return t
}

/**
 * Выбирает установленный голос с максимальным соответствием целевым локалям (не гостевой «ломаный» акцент другого языка).
 * Сначала точное совпадение тега локали, затем язык+страна, затем любой голос того же языка.
 * Предпочтение: локальные (без сети) и более высокое [Voice.quality].
 */
private fun pickNativeStudyVoice(tts: TextToSpeech, studyTag: String): Voice? {
    val all = try {
        tts.voices
    } catch (_: Exception) {
        null
    }.orEmpty().ifEmpty { return null }

    val locales = candidateLocalesForStudy(studyTag)

    fun rank(list: List<Voice>): Voice? =
        list.sortedWith(
            compareBy<Voice> { if (it.isNetworkConnectionRequired) 1 else 0 }
                .thenByDescending { it.quality },
        ).firstOrNull()

    for (loc in locales) {
        val want = canonicalTtsTag(studyTag, loc.toLanguageTag())
        val exact = all.filter { v ->
            canonicalTtsTag(studyTag, v.locale.toLanguageTag()) == want
        }
        rank(exact)?.let { return it }
    }
    for (loc in locales) {
        if (loc.country.isNullOrEmpty()) continue
        val match = all.filter { v ->
            v.locale.language.equals(loc.language, ignoreCase = true) &&
                v.locale.country.equals(loc.country, ignoreCase = true)
        }
        rank(match)?.let { return it }
    }
    for (loc in locales) {
        val lang = loc.language
        if (lang.isNullOrEmpty()) continue
        val anyLang = all.filter { v -> v.locale.language.equals(lang, ignoreCase = true) }
        rank(anyLang)?.let { return it }
    }
    return null
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
