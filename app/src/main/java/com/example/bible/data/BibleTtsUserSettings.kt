package com.example.bible.data

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Пользовательские настройки системного TTS: скорость, тон, движок, приоритет «красивого» голоса.
 */
data class TtsUserSettings(
    val speechRate: Float,
    val pitch: Float,
    /** Пусто — движок по умолчанию (часто Google TTS). */
    val enginePackage: String,
    val preferHighQuality: Boolean,
) {
    companion object {
        val Default = TtsUserSettings(
            speechRate = 1.0f,
            pitch = 1.0f,
            enginePackage = "",
            preferHighQuality = true,
        )
    }
}

object BibleTtsVoiceHelper {

    /**
     * Подобрать наиболее «качественный» голос под текущий [TextToSpeech.getLanguage] / локаль.
     * При [preferHigh] отдаётся приоритет [Voice.QUALITY_VERY_HIGH] и [Voice.LATENCY_NORMAL].
     */
    fun applyBestVoiceForCurrentLanguage(tts: TextToSpeech, preferHigh: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val lang = tts.voice?.locale
            ?: tts.language
            ?: Locale("ru", "RU")
        val all = tts.voices?.filter { v ->
            v != null && v.locale != null && (
                v.locale == lang ||
                v.locale.language == lang.language
            )
        }?.ifEmpty { null } ?: tts.voices?.toList() ?: return
        if (all.isEmpty()) return
        val chosen = if (preferHigh) {
            all.maxByOrNull { v ->
                v.quality * 1_000 + (if (v.isNetworkConnectionRequired) 0 else 1)
            } ?: return
        } else {
            all.minByOrNull { it.name } ?: return
        }
        tts.setVoice(chosen)
    }
}

/**
 * Сначала настраивает язык и слоты голоса под перевод, затем применяет скорость/тон из [user].
 * Для [TranslationId.RBO] / [TranslationId.BTI] — разные русские голоса (см. слот), без «лучшего» глобального ru-голоса.
 */
fun applyTtsVoiceForTranslation(tts: TextToSpeech, translation: TranslationId, user: TtsUserSettings) {
    val userPitch = user.pitch.coerceIn(0.5f, 1.4f)
    val userRate = user.speechRate.coerceIn(0.35f, 2.2f)
    var basePitch = 1.0f
    when (translation) {
        TranslationId.WEB -> {
            tts.setLanguage(Locale.US)
            if (user.preferHighQuality) {
                BibleTtsVoiceHelper.applyBestVoiceForCurrentLanguage(tts, true)
            }
            basePitch = 1.0f
        }
        TranslationId.RBO -> {
            basePitch = applyRussianVoiceSlot(tts, slotIndex = 0)
        }
        TranslationId.BTI -> {
            basePitch = applyRussianVoiceSlot(tts, slotIndex = 1)
        }
        else -> {
            tts.setLanguage(Locale.forLanguageTag("ru"))
            BibleTtsVoiceHelper.applyBestVoiceForCurrentLanguage(
                tts,
                user.preferHighQuality,
            )
            basePitch = 1.0f
        }
    }
    tts.setSpeechRate(userRate)
    tts.setPitch((basePitch * userPitch).coerceIn(0.2f, 2.0f))
}

/**
 * [slotIndex] 0 — РБО, 1 — Кулаковы: разные голоса из списка ru или разный базовый тон.
 * @return Базовая высота тона (до пользовательского множителя).
 */
private fun applyRussianVoiceSlot(tts: TextToSpeech, slotIndex: Int): Float {
    val ru = Locale.forLanguageTag("ru")
    tts.setLanguage(ru)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val list = tts.voices
            ?.filter { it.locale != null && it.locale.language == "ru" }
            ?.sortedBy { it.name }
            ?.distinctBy { it.name }
            .orEmpty()
        if (list.isNotEmpty()) {
            val voice = list[slotIndex % list.size]
            if (tts.setVoice(voice) == TextToSpeech.SUCCESS) {
                return if (list.size == 1) {
                    if (slotIndex == 0) 1.0f else 0.88f
                } else {
                    1.0f
                }
            }
        }
    }
    return if (slotIndex == 0) 1.0f else 0.88f
}

/** Краткий тест TTS c текущими [BibleTtsController] и движком. */
object BibleTtsSampleSpeak {
    private var last: TextToSpeech? = null
    private val lock = Any()

    fun play(appContext: Context) {
        val app = appContext.applicationContext
        val run = Runnable { playOnMain(app) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run.run()
        } else {
            Handler(Looper.getMainLooper()).post(run)
        }
    }

    private fun playOnMain(app: Context) {
        synchronized(lock) {
            runCatching {
                last?.stop()
                last?.shutdown()
            }
            last = null
            val user = BibleTtsController.settings.value
            val pkg = user.enginePackage.trim()
            var engine: TextToSpeech? = null
            val init = TextToSpeech.OnInitListener { status ->
                if (status != TextToSpeech.SUCCESS) return@OnInitListener
                val e = engine ?: return@OnInitListener
                applyTtsVoiceForTranslation(e, TranslationId.SYNODAL, user)
                e.speak(
                    "В начале сотворил Бог небо и землю",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "bible_tts_preview",
                )
                last = e
                val ref = e
                Handler(Looper.getMainLooper()).postDelayed({
                    if (last === ref) {
                        runCatching {
                            ref.stop()
                            ref.shutdown()
                        }
                        if (last === ref) last = null
                    }
                }, 4_000L)
            }
            engine = if (pkg.isNotEmpty()) {
                TextToSpeech(app, init, pkg)
            } else {
                TextToSpeech(app, init)
            }
        }
    }
}
