package com.example.bible.data

/**
 * Встроенная расширяемая база слов для тематической подсветки (пресеты).
 * Пользователь может отключать целые оси ([LexiconTone]) или копировать правила в «Мои слова».
 */
object PresetSemanticLexicon {
    private val cached by lazy { buildPresetSemanticLexiconRules() }

    fun rules(): List<SemanticLexiconRule> = cached
}
