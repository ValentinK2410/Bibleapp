package com.example.bible.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BibleAudioDownloadState(
    val running: Boolean = false,
    val narratorIds: List<String> = emptyList(),
    val doneChapters: Int = 0,
    val totalChapters: Int = 0,
    val currentLabel: String = "",
    val errors: Int = 0,
    /** true после успешного завершения всех выбранных дикторов */
    val finished: Boolean = false,
    val cancelled: Boolean = false,
)

object BibleAudioDownloadQueue {
    private val _state = MutableStateFlow(BibleAudioDownloadState())
    val state: StateFlow<BibleAudioDownloadState> = _state.asStateFlow()

    @Volatile
    var cancelRequested: Boolean = false
        private set

    fun begin(narratorIds: List<String>, totalChapters: Int) {
        cancelRequested = false
        _state.value = BibleAudioDownloadState(
            running = true,
            narratorIds = narratorIds,
            totalChapters = totalChapters,
        )
    }

    fun updateProgress(done: Int, label: String, errors: Int = _state.value.errors) {
        val cur = _state.value
        if (!cur.running) return
        _state.value = cur.copy(
            doneChapters = done,
            currentLabel = label,
            errors = errors,
        )
    }

    fun incrementError() {
        val cur = _state.value
        _state.value = cur.copy(errors = cur.errors + 1)
    }

    fun finish(cancelled: Boolean = false) {
        val cur = _state.value
        _state.value = cur.copy(
            running = false,
            finished = !cancelled && cur.errors == 0,
            cancelled = cancelled,
        )
        cancelRequested = false
    }

    fun requestCancel() {
        cancelRequested = true
    }

    fun dismissFinished() {
        _state.value = BibleAudioDownloadState()
    }
}
