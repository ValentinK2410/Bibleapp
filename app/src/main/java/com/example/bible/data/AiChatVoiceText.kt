package com.example.bible.data

/**
 * Текст ответа ИИ для озвучки: убираем разметку, чтобы TTS не читал звёздочки и блоки кода.
 */
object AiChatVoiceText {

    fun forSpeech(raw: String): String {
        var t = GigaChatImages.stripForSpeech(raw)
        if (t.isEmpty()) return ""
        t = t.replace(Regex("```[\\s\\S]*?```"), " ")
        t = t.replace(Regex("`([^`]+)`"), "$1")
        t = t.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        t = t.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        t = t.replace(Regex("(\\*\\*|__)(.*?)\\1"), "$2")
        t = t.replace(Regex("(\\*|_)(.*?)\\1"), "$2")
        t = t.replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
        t = t.replace(Regex("\\s+"), " ")
        return t.trim()
    }
}
