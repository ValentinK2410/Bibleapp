package com.example.bible.ui.languagestudy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bible.data.db.LangVocabWordEntity
import com.example.bible.data.languagestudy.LanguageStudyRepository
import com.example.bible.data.languagestudy.LanguageStudySm2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LangStudyOverviewState(
    val totalWords: Int = 0,
    val dueCount: Int = 0,
    val loaded: Boolean = false,
)

data class LangStudySessionUi(
    val queue: List<LangVocabWordEntity> = emptyList(),
    val index: Int = 0,
    val revealAnswer: Boolean = false,
)

class LanguageStudyViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LanguageStudyRepository(application)
    private var activeLangCode: String = ""

    private val _overview = MutableStateFlow(LangStudyOverviewState())
    val overview: StateFlow<LangStudyOverviewState> = _overview.asStateFlow()

    private val _session = MutableStateFlow(LangStudySessionUi())
    val session: StateFlow<LangStudySessionUi> = _session.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    private val _dictQuery = MutableStateFlow("")
    val dictQuery: StateFlow<String> = _dictQuery.asStateFlow()

    private val _dictHits = MutableStateFlow<List<LangVocabWordEntity>>(emptyList())
    val dictHits: StateFlow<List<LangVocabWordEntity>> = _dictHits.asStateFlow()

    fun clearImportMessage() {
        _importMessage.value = null
    }

    fun prepareLanguage(langCode: String) {
        activeLangCode = langCode
        viewModelScope.launch(Dispatchers.IO) {
            repo.ensureDemoIfEmpty(langCode)
            refreshOverview(langCode)
        }
    }

    private fun refreshActiveIfPossible() {
        if (activeLangCode.isNotBlank()) refreshOverview(activeLangCode)
    }
    fun refreshOverview(langCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val total = repo.countWords(langCode)
            val due = repo.dueCount(langCode)
            _overview.value = LangStudyOverviewState(totalWords = total, dueCount = due, loaded = true)
        }
    }

    fun searchDictionary(langCode: String, needle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _dictQuery.value = needle
            _dictHits.value = repo.searchWords(langCode, needle)
        }
    }

    fun startSession(langCode: String, limit: Int = 24) {
        viewModelScope.launch(Dispatchers.IO) {
            val q = repo.listDueWords(langCode, limit)
            _session.value = LangStudySessionUi(queue = q, index = 0, revealAnswer = false)
        }
    }

    fun toggleReveal() {
        val s = _session.value
        _session.value = s.copy(revealAnswer = true)
    }

    fun gradeCurrent(langCode: String, quality: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _session.value
            val idx = s.index
            val q = s.queue
            if (idx !in q.indices) return@launch
            val w = q[idx]
            val prev = repo.getSrs(w.wordKey)
            val next = LanguageStudySm2.schedule(prev = prev, wordKey = w.wordKey, quality = quality)
            repo.upsertSrs(next)
            if (idx + 1 < q.size) {
                _session.value = LangStudySessionUi(queue = q, index = idx + 1, revealAnswer = false)
            } else {
                _session.value = LangStudySessionUi()
                refreshOverview(langCode)
            }
        }
    }

    fun saveNoteForWord(wordKey: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateUserNote(wordKey, note)
        }
    }

    fun loadUserNote(wordKey: String): String =
        repo.getSrs(wordKey)?.userNote.orEmpty()

    fun importFromUri(androidUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repo.importPackFromUri(androidUri)
            refreshActiveIfPossible()
            _importMessage.value = res.fold(
                onSuccess = { "Импортировано слов: $it" },
                onFailure = { it.message ?: it.toString() },
            )
        }
    }

    fun importBundled(assetName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = repo.importBundledFullPack(assetName)
            refreshActiveIfPossible()
            _importMessage.value = res.fold(
                onSuccess = { "Готово: $it записей из пакета" },
                onFailure = { it.message ?: it.toString() },
            )
        }
    }

    fun downloadPackFromUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val res = repo.downloadAndImportPack(url.trim())
            refreshActiveIfPossible()
            _importMessage.value = res.fold(
                onSuccess = { "Загрузка и импорт: $it слов" },
                onFailure = { it.message ?: it.toString() },
            )
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(LanguageStudyViewModel::class.java))
            return LanguageStudyViewModel(application) as T
        }
    }
}
