package com.example.bible.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Текущие настройки TTS для читалки, путей и [TravelVoicePrompter].
 * Обновляется из [BibleViewModel] по DataStore.
 */
object BibleTtsController {
    private val _settings = MutableStateFlow(TtsUserSettings.Default)
    val settings: StateFlow<TtsUserSettings> = _settings.asStateFlow()

    fun setSettings(s: TtsUserSettings) {
        if (_settings.value != s) {
            _settings.value = s
        }
    }
}
