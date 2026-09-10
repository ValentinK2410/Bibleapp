package com.example.bible.data

import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Движок озвучки ответов ИИ. */
enum class AiChatTtsEngine(val key: String) {
    /** Нейросеть SaluteSpeech (Сбер), 24 кГц — максимально естественный голос. */
    NEURAL("neural"),
    /** Системный TTS телефона (Google и др.). */
    SYSTEM("system"),
    ;

    companion object {
        fun fromKey(raw: String?): AiChatTtsEngine =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: NEURAL
    }
}

/** Интонация озвучки ответов GigaChat / DeepSeek (через pitch, скорость и громкость TTS). */
enum class AiChatTtsIntonation(val key: String) {
    CALM("calm"),
    NORMAL("normal"),
    JOYFUL("joyful"),
    BOLD("bold"),
    WHISPER("whisper"),
    ;

    data class Modifiers(
        val pitchMul: Float,
        val rateMul: Float,
        val volume: Float = 1f,
    )

    fun modifiers(): Modifiers = when (this) {
        WHISPER -> Modifiers(pitchMul = 0.74f, rateMul = 0.84f, volume = 0.42f)
        CALM -> Modifiers(pitchMul = 0.90f, rateMul = 0.86f, volume = 0.92f)
        NORMAL -> Modifiers(pitchMul = 1.0f, rateMul = 1.0f, volume = 1f)
        JOYFUL -> Modifiers(pitchMul = 1.20f, rateMul = 1.14f, volume = 1f)
        BOLD -> Modifiers(pitchMul = 0.96f, rateMul = 1.12f, volume = 1f)
    }

    companion object {
        fun fromKey(raw: String?): AiChatTtsIntonation =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: NORMAL
    }
}

data class AiChatTtsSettings(
    val engine: AiChatTtsEngine = AiChatTtsEngine.NEURAL,
    /** Для [AiChatTtsEngine.NEURAL] — id голоса SaluteSpeech; для SYSTEM — имя голоса TTS. */
    val voiceName: String = SaluteSpeechClient.DEFAULT_VOICE,
    val intonation: AiChatTtsIntonation = AiChatTtsIntonation.NORMAL,
) {
    companion object {
        val Default = AiChatTtsSettings()
    }
}

data class AiChatTtsVoiceOption(
    val name: String,
    val label: String,
    val network: Boolean,
)

object AiChatTtsController {
    private val _settings = MutableStateFlow(AiChatTtsSettings.Default)
    val settings: StateFlow<AiChatTtsSettings> = _settings.asStateFlow()

    fun setSettings(s: AiChatTtsSettings) {
        if (_settings.value != s) _settings.value = s
    }
}

fun applyAiChatVoice(
    tts: TextToSpeech,
    user: TtsUserSettings,
    ai: AiChatTtsSettings,
) {
    tts.setLanguage(Locale.forLanguageTag("ru"))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val requested = ai.voiceName.trim()
        if (requested.isNotEmpty()) {
            val voice = tts.voices?.firstOrNull { it.name == requested }
            if (voice != null) tts.setVoice(voice)
        } else {
            BibleTtsVoiceHelper.applyBestVoiceForCurrentLanguage(tts, preferHigh = true)
        }
    }
    val mods = ai.intonation.modifiers()
    val baseRate = user.speechRate.coerceIn(0.35f, 2.2f)
    val basePitch = user.pitch.coerceIn(0.5f, 1.4f)
    tts.setSpeechRate((baseRate * mods.rateMul).coerceIn(0.35f, 2.2f))
    tts.setPitch((basePitch * mods.pitchMul).coerceIn(0.2f, 2.0f))
}

fun listAiChatRussianVoices(tts: TextToSpeech): List<AiChatTtsVoiceOption> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
    return tts.voices
        ?.filter { it.locale?.language == "ru" }
        ?.sortedByDescending { BibleTtsVoiceHelper.scoreVoice(it) }
        ?.distinctBy { it.name }
        ?.map { voice ->
            AiChatTtsVoiceOption(
                name = voice.name,
                label = formatAiChatVoiceLabel(voice),
                network = voice.isNetworkConnectionRequired,
            )
        }
        .orEmpty()
}

fun formatAiChatVoiceLabel(voice: Voice): String {
    val raw = voice.name
    val tail = raw.substringAfterLast('-', raw).replace('-', ' ').trim()
    val pretty = tail.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
    }
    return pretty.ifBlank { raw }
}

fun speakAiChatChunk(
    engine: TextToSpeech,
    text: String,
    queueMode: Int,
    utteranceId: String,
    volume: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.05f, 1f))
        }
        engine.speak(text, queueMode, params, utteranceId)
    } else {
        @Suppress("DEPRECATION")
        engine.speak(text, queueMode, null)
    }
}
