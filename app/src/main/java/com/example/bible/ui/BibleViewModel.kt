@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.bible.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bible.data.AppDataExport
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleLibrary
import com.example.bible.data.BiblePreferences
import com.example.bible.data.BibleSearchHistoryEntry
import com.example.bible.data.bibleSearchHistoryDedupKey
import com.example.bible.data.BibleRepository
import com.example.bible.data.BibleTtsController
import com.example.bible.data.TtsUserSettings
import com.example.bible.data.ExportBundleOptions
import com.example.bible.data.ExportShareProgressEvent
import com.example.bible.data.ShareExportOptions
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import com.example.bible.data.AudioPlaybackState
import com.example.bible.data.CommentaryRepository
import com.example.bible.data.HistoryEntry
import com.example.bible.data.QuranReadingHistoryEntry
import com.example.bible.data.QuranReadingTraceEntry
import com.example.bible.data.ReadingTraceEntry
import com.example.bible.data.BibleImageLibrary
import com.example.bible.data.BibleUserImage
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.BibleAudioLibrary
import com.example.bible.data.BibleVideoLibrary
import com.example.bible.data.WebImageSearch
import com.example.bible.data.CommonsVideoSearch
import com.example.bible.data.CommonsSearchResult
import com.example.bible.data.SafeImagePolicy
import com.example.bible.data.MediaRepository
import com.example.bible.data.SongItem
import com.example.bible.data.UserNote
import com.example.bible.data.UserNoteKind
import com.example.bible.data.SearchHit
import com.example.bible.data.TranslationId
import com.example.bible.data.SemanticDisplayStyle
import com.example.bible.data.SemanticHighlightSession
import com.example.bible.data.SemanticScope
import com.example.bible.data.TextHighlight
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.UserMediaPlaylist
import com.example.bible.data.UserMediaPlaylistKind
import com.example.bible.data.UserMediaKind
import com.example.bible.data.UserMediaPlaybackProgress
import com.example.bible.data.UserMediaPlaylistShareError
import com.example.bible.data.UserMediaPlaylistShareOutcome
import com.example.bible.data.UserMediaPlaylistSharePackage
import com.example.bible.data.WordSpanMediaAttachment
import com.example.bible.data.LexiconTone
import com.example.bible.data.PresetSemanticLexicon
import com.example.bible.data.SemanticLexiconRule
import com.example.bible.data.findUnifiedSemanticSpans
import com.example.bible.data.BooksMainMenuOrder
import com.example.bible.data.MediaHomeSectionOrder
import com.example.bible.data.OfflineDownloadBookOrder
import com.example.bible.data.KidsUserSectionsState
import com.example.bible.data.KidsUserMediaStorage
import com.example.bible.ui.theme.BibleAppThemePreset
import com.example.bible.data.StudyBulkDownloader
import com.example.bible.data.StudyContentCache
import com.example.bible.data.VerseCommentary
import com.example.bible.data.VerseRef
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SearchScope {
    ALL,
    OLD_TESTAMENT,
    NEW_TESTAMENT,
    SINGLE_BOOK,
}

/** Где искать по переводам: только в текущем, в одном выбранном или во всех доступных. */
enum class BibleSearchTranslationMode {
    /** Перевод, выбранный сейчас в читалке. */
    FOLLOW_READER,
    /** Один конкретный перевод ([SearchSettings.singleTranslationId]). */
    SINGLE,
    /** Все локально доступные переводы. */
    ALL,
}

data class SearchSettings(
    val wholeWords: Boolean = false,
    val orderedWords: Boolean = false,
    /** Учитывать только буквы и цифры: кавычки, пунктуация и прочие символы не мешают совпадению. */
    val ignoreSeparators: Boolean = true,
    val accentSensitive: Boolean = false,
    val caseSensitive: Boolean = false,
    val punctuationSensitive: Boolean = false,
    val highlightMatches: Boolean = true,
    val scope: SearchScope = SearchScope.ALL,
    val singleBookId: String? = null,
    val translationMode: BibleSearchTranslationMode = BibleSearchTranslationMode.FOLLOW_READER,
    val singleTranslationId: TranslationId = TranslationId.SYNODAL,
)

sealed interface BibleLoadState {
    data object Loading : BibleLoadState
    data class Ready(val library: BibleLibrary) : BibleLoadState
    data class Error(val message: String) : BibleLoadState
}

private data class BibleSearchRunContext(
    val loadState: BibleLoadState,
    val readerTranslation: TranslationId,
    val query: String,
    val settings: SearchSettings,
)

/** Состояние списка результатов поиска по Библии (индикатор загрузки + hits). */
data class BibleSearchListState(
    val inProgress: Boolean = false,
    val results: List<SearchHit> = emptyList(),
)

sealed interface CommentaryLoadState {
    data object Idle : CommentaryLoadState
    data object Loading : CommentaryLoadState
    data class Ready(val commentary: VerseCommentary) : CommentaryLoadState
    data object NotFound : CommentaryLoadState
    data class Error(val message: String) : CommentaryLoadState
}

data class OfflineDownloadUiState(
    val running: Boolean,
    val phase: String,
    val current: Int,
    val total: Int,
    val detail: String,
)

class BibleViewModel(
    private val repository: BibleRepository,
    private val preferences: BiblePreferences,
    private val appContext: Context,
    private val commentaryRepository: CommentaryRepository = CommentaryRepository(),
    val mediaRepository: MediaRepository,
    private val studyContentCache: StudyContentCache,
) : ViewModel() {

    private val _state = MutableStateFlow<BibleLoadState>(BibleLoadState.Loading)
    val state: StateFlow<BibleLoadState> = _state.asStateFlow()

    val selectedTranslation: StateFlow<TranslationId> = preferences.selectedTranslation.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TranslationId.SYNODAL,
    )

    val bookmarkKeys: StateFlow<Set<String>> = preferences.bookmarkKeys.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    val bookmarkTagsMap: StateFlow<Map<String, Set<String>>> = preferences.bookmarkTagsMap.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap(),
    )

    val readingPlanCompletedDates: StateFlow<Set<String>> = preferences.readingPlanCompletedDates.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    val readingPlanReminderTime: StateFlow<Pair<Int, Int>?> = preferences.readingPlanReminderTime.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )

    val textHighlights: StateFlow<List<TextHighlight>> = preferences.textHighlights.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    /** Медиа к выделенным фрагментам в стихах (не лексикон). */
    val wordSpanMediaAttachments: StateFlow<List<WordSpanMediaAttachment>> =
        preferences.wordSpanMediaAttachments.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    private val _semanticHighlightSession = MutableStateFlow<SemanticHighlightSession?>(null)
    val semanticHighlightSession: StateFlow<SemanticHighlightSession?> = _semanticHighlightSession.asStateFlow()

    val userSemanticLexiconRules: StateFlow<List<SemanticLexiconRule>> = preferences.userSemanticLexiconRules.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val lexiconPresetEnabled: StateFlow<Boolean> = preferences.lexiconPresetEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true,
    )

    val lexiconPresetToneIds: StateFlow<Set<String>> = preferences.lexiconPresetToneIds.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    val lexiconUserEnabled: StateFlow<Boolean> = preferences.lexiconUserEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true,
    )

    val lexiconUserToneIds: StateFlow<Set<String>> = preferences.lexiconUserToneIds.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    /** «Мои слова» в читалке: учёт глобального выключателя и фильтра по осям. */
    val readerUserLexiconRules: StateFlow<List<SemanticLexiconRule>> = combine(
        userSemanticLexiconRules,
        lexiconUserEnabled,
        lexiconUserToneIds,
    ) { rules, enabled, toneIds ->
        if (!enabled) return@combine emptyList()
        val allToneIds = LexiconTone.entries.map { it.id }.toSet()
        val eff = if (toneIds.isEmpty()) allToneIds else toneIds
        rules.filter { it.enabled && it.tone.id in eff }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    /** Пресет-правила для читалки с учётом переключателей. */
    val readerPresetLexiconRules: StateFlow<List<SemanticLexiconRule>> = combine(
        lexiconPresetEnabled,
        lexiconPresetToneIds,
    ) { enabled, toneIds ->
        if (!enabled) return@combine emptyList()
        val all = PresetSemanticLexicon.rules()
        if (toneIds.isEmpty()) all else all.filter { it.tone.id in toneIds }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PresetSemanticLexicon.rules(),
    )

    fun setSemanticHighlightSession(session: SemanticHighlightSession?) {
        _semanticHighlightSession.value = session
    }

    fun saveUserSemanticLexiconRule(rule: SemanticLexiconRule) {
        viewModelScope.launch { preferences.saveUserSemanticLexiconRule(rule) }
    }

    fun deleteUserSemanticLexiconRule(id: String) {
        viewModelScope.launch { preferences.deleteUserSemanticLexiconRule(id) }
    }

    fun setLexiconPresetEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setLexiconPresetEnabled(enabled) }
    }

    fun setLexiconPresetToneIds(ids: Set<String>) {
        viewModelScope.launch { preferences.setLexiconPresetToneIds(ids) }
    }

    fun setLexiconUserEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setLexiconUserEnabled(enabled) }
    }

    fun setLexiconUserToneIds(ids: Set<String>) {
        viewModelScope.launch { preferences.setLexiconUserToneIds(ids) }
    }

    /**
     * Сохраняет тематические подсветки как обычные [TextHighlight] (текущий перевод).
     * Режим «подчёркивание» сохраняется как цвет текста.
     */
    fun persistSemanticHighlightSession(
        session: SemanticHighlightSession,
        library: BibleLibrary,
        translation: TranslationId,
        onProgress: (String) -> Unit = {},
        /** Количество сохранённых фрагментов; второй аргумент — обрезка по лимиту. */
        onDone: (Int, Boolean) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val styleForPersist = when (session.displayStyle) {
                    SemanticDisplayStyle.UNDERLINE -> SemanticDisplayStyle.FOREGROUND
                    else -> session.displayStyle
                }
                val highlights = mutableListOf<TextHighlight>()
                val canon = BibleCanon.byId(session.bookId)
                val totalChapters = canon?.chapters
                    ?: library.loadBookShell(translation, session.bookId)?.chapters?.size ?: 1
                val chapterRange = when (session.scope) {
                    SemanticScope.VERSE, SemanticScope.CHAPTER -> session.chapter..session.chapter
                    SemanticScope.BOOK -> 1..totalChapters
                }
                for (ch in chapterRange) {
                    onProgress("$ch / ${chapterRange.last}")
                    val verses = fetchVersesForChapterPersist(library, translation, session.bookId, ch)
                    if (verses.isEmpty()) continue
                    val toScan = when (session.scope) {
                        SemanticScope.VERSE -> verses.filter { it.number == session.verseNumber }
                        else -> verses
                    }
                    for (verse in toScan) {
                        val spans = findUnifiedSemanticSpans(
                            verse.text,
                            session.categories,
                            session.biblicalThematicCategories,
                            translation,
                            styleForPersist,
                        )
                        for (span in spans) {
                            highlights.add(
                                TextHighlight(
                                    translation = translation,
                                    bookId = session.bookId,
                                    chapter = ch,
                                    verse = verse.number,
                                    startOffset = span.start,
                                    endOffset = span.end,
                                    isBackground = span.isBackground,
                                    colorArgb = span.colorArgb,
                                ),
                            )
                        }
                    }
                }
                if (highlights.isEmpty()) {
                    withContext(Dispatchers.Main) { onError("Нет совпадений по выбранным темам") }
                    return@launch
                }
                val capped = highlights.take(12_000)
                preferences.addTextHighlightsBatch(capped)
                withContext(Dispatchers.Main) {
                    onDone(capped.size, highlights.size > capped.size)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: e.toString()) }
            }
        }
    }

    private suspend fun fetchVersesForChapterPersist(
        library: BibleLibrary,
        translation: TranslationId,
        bookId: String,
        chapter: Int,
    ): List<com.example.bible.data.BibleVerse> {
        val chapterObj = library.loadChapter(translation, bookId, chapter)
        val local = chapterObj?.verses
        if (local != null && local.isNotEmpty()) return local
        return fetchOnlineVerses(translation, bookId, chapter)
    }

    val readerFontScale: StateFlow<Float> = preferences.readerFontScale.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ReaderFontScaleDefaults.DEFAULT,
    )

    val songFontSize: StateFlow<Float> = preferences.songFontSize.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        18f,
    )

    fun setSongFontSize(size: Float) {
        viewModelScope.launch { preferences.setSongFontSize(size) }
    }

    val songHighlightLineWhilePlaying: StateFlow<Boolean> = preferences.songHighlightLineWhilePlaying.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true,
    )

    fun setSongHighlightLineWhilePlaying(enabled: Boolean) {
        viewModelScope.launch { preferences.setSongHighlightLineWhilePlaying(enabled) }
    }

    val bookPickerLongPressTts: StateFlow<Boolean> = preferences.bookPickerLongPressTts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true,
    )

    fun setBookPickerLongPressTts(enabled: Boolean) {
        viewModelScope.launch { preferences.setBookPickerLongPressTts(enabled) }
    }

    val videoLibraryTitleScale: StateFlow<Float> = preferences.videoLibraryTitleScale.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        VideoLibraryFontDefaults.DEFAULT,
    )

    /** null = системная тема, true = тёмная, false = светлая. */
    val darkMode: StateFlow<Boolean?> = preferences.darkMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )

    val mimicControlEnabled: StateFlow<Boolean> = preferences.mimicControlEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    val mimicControlV2Enabled: StateFlow<Boolean> = preferences.mimicControlV2Enabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    val mimicCameraPreviewEnabled: StateFlow<Boolean> = preferences.mimicCameraPreviewEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    val mimicFaceOverlayEnabled: StateFlow<Boolean> = preferences.mimicFaceOverlayEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    val mimicVelocityVectorVisible: StateFlow<Boolean> = preferences.mimicVelocityVectorVisible.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    val mimicMediaPipeFaceGeometryEnabled: StateFlow<Boolean> = preferences.mimicMediaPipeFaceGeometryEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    private val _mimicScrollDy = MutableSharedFlow<Float>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val mimicScrollDy: SharedFlow<Float> = _mimicScrollDy.asSharedFlow()

    fun postMimicScrollDy(dy: Float) {
        if (dy != 0f) _mimicScrollDy.tryEmit(dy)
    }

    fun toggleMimicControl() {
        viewModelScope.launch {
            val cur = preferences.mimicControlEnabled.first()
            val next = !cur
            preferences.setMimicControlEnabled(next)
            if (!next) {
                preferences.setMimicControlV2Enabled(false)
                preferences.clearMimicVisualSettings()
            }
        }
    }

    fun toggleMimicControlV2() {
        viewModelScope.launch {
            if (!preferences.mimicControlEnabled.first()) return@launch
            val cur = preferences.mimicControlV2Enabled.first()
            preferences.setMimicControlV2Enabled(!cur)
        }
    }

    fun toggleMimicCameraPreview() {
        viewModelScope.launch {
            if (!preferences.mimicControlEnabled.first()) return@launch
            val cur = preferences.mimicCameraPreviewEnabled.first()
            preferences.setMimicCameraPreviewEnabled(!cur)
        }
    }

    fun toggleMimicFaceOverlay() {
        viewModelScope.launch {
            if (!preferences.mimicControlEnabled.first()) return@launch
            val cur = preferences.mimicFaceOverlayEnabled.first()
            preferences.setMimicFaceOverlayEnabled(!cur)
        }
    }

    fun toggleMimicVelocityVectorVisible() {
        viewModelScope.launch {
            if (!preferences.mimicControlEnabled.first()) return@launch
            val cur = preferences.mimicVelocityVectorVisible.first()
            preferences.setMimicVelocityVectorVisible(!cur)
        }
    }

    fun toggleMimicMediaPipeFaceGeometry() {
        viewModelScope.launch {
            if (!preferences.mimicControlEnabled.first()) return@launch
            val cur = preferences.mimicMediaPipeFaceGeometryEnabled.first()
            preferences.setMimicMediaPipeFaceGeometryEnabled(!cur)
        }
    }

    val appThemePreset: StateFlow<BibleAppThemePreset> = preferences.appThemePresetKey
        .map(BibleAppThemePreset::fromStorageKey)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BibleAppThemePreset.STANDARD,
        )

    fun setAppThemePreset(preset: BibleAppThemePreset) {
        viewModelScope.launch {
            preferences.setAppThemePresetKey(preset.storageKey)
        }
    }

    val ttsUserSettings: StateFlow<TtsUserSettings> = combine(
        preferences.ttsSpeechRate,
        preferences.ttsPitch,
        preferences.ttsEnginePackage,
        preferences.ttsPreferHighQuality,
    ) { r, p, e, h ->
        TtsUserSettings(r, p, e, h)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TtsUserSettings.Default,
    )

    fun setTtsSpeechRate(v: Float) {
        viewModelScope.launch { preferences.setTtsSpeechRate(v) }
    }

    fun setTtsPitch(v: Float) {
        viewModelScope.launch { preferences.setTtsPitch(v) }
    }

    fun setTtsEnginePackage(packageName: String) {
        viewModelScope.launch { preferences.setTtsEnginePackage(packageName) }
    }

    fun setTtsPreferHighQuality(prefer: Boolean) {
        viewModelScope.launch { preferences.setTtsPreferHighQuality(prefer) }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchSettings = MutableStateFlow(SearchSettings())
    val searchSettings: StateFlow<SearchSettings> = _searchSettings.asStateFlow()

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    /** Сохранить запрос в историю поиска по Библии (после стабилизации строки и завершения поиска). */
    fun recordBibleSearchHistory(trimmedDisplayQuery: String) {
        val q = trimmedDisplayQuery.trim()
        if (q.isEmpty()) return
        val dk = bibleSearchHistoryDedupKey(q)
        if (dk.isEmpty()) return
        viewModelScope.launch {
            preferences.appendBibleSearchHistory(q, dk)
        }
    }

    fun removeBibleSearchHistoryEntry(entry: BibleSearchHistoryEntry) {
        viewModelScope.launch {
            preferences.removeBibleSearchHistoryEntry(entry)
        }
    }

    fun clearBibleSearchHistory() {
        viewModelScope.launch {
            preferences.clearBibleSearchHistory()
        }
    }

    fun updateSearchSettings(s: SearchSettings) {
        _searchSettings.value = s
    }

    val bibleSearchHistory: StateFlow<List<BibleSearchHistoryEntry>> = preferences.bibleSearchHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val searchListState: StateFlow<BibleSearchListState> = combine(
        state,
        selectedTranslation,
        _searchQuery.flatMapLatest { q ->
            flow {
                if (q.isBlank()) {
                    emit(q)
                } else {
                    delay(220)
                    emit(q)
                }
            }
        },
        _searchSettings,
    ) { st, trans, q, settings ->
        BibleSearchRunContext(st, trans, q, settings)
    }.flatMapLatest { ctx ->
        flow {
            try {
                if (ctx.query.isBlank()) {
                    emit(BibleSearchListState(inProgress = false, results = emptyList()))
                    return@flow
                }
                if (ctx.loadState !is BibleLoadState.Ready) {
                    emit(BibleSearchListState(inProgress = true, results = emptyList()))
                    return@flow
                }
                emit(BibleSearchListState(inProgress = true, results = emptyList()))
                val lib = ctx.loadState.library
                val trimmed = ctx.query.trim()
                val translations = runCatching {
                    when (ctx.settings.translationMode) {
                        BibleSearchTranslationMode.FOLLOW_READER -> listOf(ctx.readerTranslation)
                        BibleSearchTranslationMode.SINGLE -> listOf(ctx.settings.singleTranslationId)
                        BibleSearchTranslationMode.ALL -> lib.translationIdsWithLocalText()
                    }
                }.getOrElse { emptyList() }
                if (translations.isEmpty()) {
                    emit(BibleSearchListState(inProgress = false, results = emptyList()))
                } else {
                    val hits = runCatching {
                        lib.searchMultiple(translations, trimmed, settings = ctx.settings)
                    }.getOrElse { emptyList() }
                    emit(BibleSearchListState(inProgress = false, results = hits))
                }
            } catch (e: Exception) {
                emit(BibleSearchListState(inProgress = false, results = emptyList()))
            }
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BibleSearchListState(),
        )

    init {
        viewModelScope.launch {
            ttsUserSettings.collect { BibleTtsController.setSettings(it) }
        }
        loadInternal(clear = false, forceLoading = false)
    }

    fun reload() {
        loadInternal(clear = true, forceLoading = true)
    }

    private fun loadInternal(clear: Boolean = false, forceLoading: Boolean = true) {
        viewModelScope.launch {
            if (clear) repository.clearCache()
            if (forceLoading) _state.value = BibleLoadState.Loading
            runCatching { withContext(Dispatchers.IO) { repository.loadLibrary() } }
                .onSuccess { _state.value = BibleLoadState.Ready(it) }
                .onFailure { e ->
                    _state.value = BibleLoadState.Error(e.message ?: e.toString())
                }
        }
    }

    suspend fun setTranslation(id: TranslationId) {
        preferences.setTranslation(id)
    }

    fun toggleBookmark(ref: VerseRef) {
        viewModelScope.launch {
            preferences.toggleBookmark(ref.toKey())
        }
    }

    fun setBookmarkTags(ref: VerseRef, tags: Set<String>) {
        viewModelScope.launch {
            preferences.setBookmarkTags(ref.toKey(), tags)
        }
    }

    fun setReadingPlanDayCompleted(date: LocalDate, completed: Boolean) {
        viewModelScope.launch {
            preferences.setReadingPlanDayCompleted(date, completed)
        }
    }

    fun setReadingPlanReminder(hour: Int, minute: Int) {
        viewModelScope.launch {
            preferences.setReadingPlanReminder(hour, minute)
        }
    }

    /** Полный архив в старом формате: весь DataStore + вложения к стихам. */
    suspend fun exportBackupZip(): File =
        AppDataExport.exportFullLegacyStyleZip(appContext, preferences)

    /** Выборочный экспорт (настройки, данные, кэш изучения, таймкоды, медиа песен и т. д.). */
    suspend fun exportDataBundle(options: ExportBundleOptions): File =
        AppDataExport.exportZip(appContext, preferences, options)

    /** Детальный пакет для «Поделиться приложением». */
    suspend fun exportShareBundle(
        options: ShareExportOptions,
        onProgress: ((ExportShareProgressEvent) -> Unit)? = null,
    ): File = AppDataExport.exportShareZip(appContext, preferences, options, onProgress)

    suspend fun importBackupZip(file: File): Boolean {
        val ok = withContext(Dispatchers.IO) {
            AppDataExport.importZip(appContext, preferences, file)
        }
        if (ok) reload()
        return ok
    }

    fun addTextHighlight(h: TextHighlight) {
        viewModelScope.launch {
            preferences.addTextHighlight(h)
        }
    }

    fun upsertWordSpanMediaAttachment(a: WordSpanMediaAttachment) {
        viewModelScope.launch { preferences.upsertWordSpanMediaAttachment(a) }
    }

    fun deleteWordSpanMediaAttachment(id: String) {
        viewModelScope.launch { preferences.deleteWordSpanMediaAttachment(id) }
    }

    fun removeWordSpanMediaIntersecting(ref: VerseRef, selStart: Int, selEnd: Int) {
        viewModelScope.launch { preferences.removeWordSpanMediaIntersecting(ref, selStart, selEnd) }
    }

    fun removeTextHighlightsIntersecting(ref: VerseRef, selStart: Int, selEnd: Int) {
        viewModelScope.launch {
            preferences.removeTextHighlightsIntersecting(ref, selStart, selEnd)
        }
    }

    fun adjustReaderFontScale(delta: Float) {
        viewModelScope.launch {
            val cur = readerFontScale.value
            preferences.setReaderFontScale(cur + delta)
        }
    }

    fun setReaderFontScale(scale: Float) {
        viewModelScope.launch { preferences.setReaderFontScale(scale) }
    }

    fun adjustVideoLibraryTitleScale(delta: Float) {
        viewModelScope.launch {
            val cur = videoLibraryTitleScale.value
            preferences.setVideoLibraryTitleScale(cur + delta)
        }
    }

    private val _commentaryState = MutableStateFlow<CommentaryLoadState>(CommentaryLoadState.Idle)
    val commentaryState: StateFlow<CommentaryLoadState> = _commentaryState.asStateFlow()

    fun loadCommentary(translation: TranslationId, bookId: String, chapter: Int, verse: Int) {
        _commentaryState.value = CommentaryLoadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                studyContentCache.getVerseCommentaryApi(translation.code, bookId, chapter, verse)?.let { cached ->
                    _commentaryState.value = CommentaryLoadState.Ready(cached)
                    return@launch
                }
                val result = commentaryRepository.loadCommentary(translation, bookId, chapter, verse)
                if (result != null) {
                    studyContentCache.putVerseCommentaryApi(translation.code, bookId, chapter, verse, result)
                    _commentaryState.value = CommentaryLoadState.Ready(result)
                } else {
                    _commentaryState.value = CommentaryLoadState.NotFound
                }
            } catch (e: Exception) {
                _commentaryState.value = CommentaryLoadState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetCommentaryState() {
        _commentaryState.value = CommentaryLoadState.Idle
    }

    val audioPlaybackState: StateFlow<AudioPlaybackState> = mediaRepository.playbackState
    val audioPlaybackSpeed: StateFlow<Float> = mediaRepository.playbackSpeed

    fun booksWithAudio(translation: TranslationId): Set<String> =
        mediaRepository.booksWithAudio(translation)

    fun booksWithDownloadedAudio(narratorId: String): Set<String> =
        mediaRepository.booksWithDownloaded(narratorId)

    fun hasChapterAudio(translation: TranslationId, bookId: String, chapter: Int): Boolean =
        mediaRepository.hasChapterAudio(translation, bookId, chapter)

    fun downloadedChaptersFor(narratorId: String, bookId: String): Set<Int> =
        mediaRepository.downloadedChaptersFor(narratorId, bookId)

    fun playAudio(url: String) {
        mediaRepository.playFromUrl(url)
    }

    fun playVerseAudio(translation: TranslationId, bookId: String, chapter: Int, ttsFallback: () -> Unit) {
        if (translation == TranslationId.INTERLINEAR) {
            com.example.bible.data.originalLanguageNarratorForBook(bookId)?.let { narrator ->
                com.example.bible.data.BibleAudioPlayer.playChapter(appContext, narrator, bookId, chapter)
                return
            }
        }
        mediaRepository.playChapterAudio(
            translation = translation,
            bookId = bookId,
            chapter = chapter,
            onError = { ttsFallback() },
        )
    }

    fun togglePauseResume() {
        mediaRepository.togglePauseResume()
    }

    fun cycleAudioSpeed() {
        mediaRepository.cycleSpeed()
    }

    fun getAudioProgress(): Pair<Int, Int> = mediaRepository.getProgress()

    fun seekAudio(positionMs: Int) {
        mediaRepository.seekTo(positionMs)
    }

    fun stopAudio() {
        mediaRepository.stop()
    }

    /** Пауза озвучки главы, чтобы не смешивать её с локальным вложением (аудио/видео). */
    fun pauseAudioIfPlaying() {
        if (mediaRepository.playbackState.value == AudioPlaybackState.PLAYING) {
            mediaRepository.pause()
        }
    }

    override fun onCleared() {
        val flushThread = Thread {
            kotlinx.coroutines.runBlocking {
                readingDwellMutex.withLock { commitReadingDwellSegmentLocked() }
            }
        }
        flushThread.start()
        flushThread.join(400)
        mediaRepository.stop()
        super.onCleared()
    }

    fun toggleDarkMode(isCurrentlyDark: Boolean) {
        viewModelScope.launch {
            preferences.setDarkMode(!isCurrentlyDark)
        }
    }

    val readingHistory: StateFlow<List<HistoryEntry>> = preferences.readingHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val readingTrace: StateFlow<List<ReadingTraceEntry>> = preferences.readingTrace.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val quranReadingHistory: StateFlow<List<QuranReadingHistoryEntry>> = preferences.quranReadingHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val quranReadingTrace: StateFlow<List<QuranReadingTraceEntry>> = preferences.quranReadingTrace.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    private val quranDwellMutex = Mutex()
    private var quranDwellSurah: Int = 0
    private var quranDwellSurahName: String = ""
    private var quranDwellAyah: Int = 0
    private var quranDwellStartMs: Long = 0L

    fun beginQuranReadingSession(surahNumber: Int, surahNameRu: String, initialAyah: Int) {
        viewModelScope.launch {
            quranDwellMutex.withLock {
                commitQuranDwellSegmentLocked()
                val ayah = initialAyah.coerceAtLeast(1)
                quranDwellSurah = surahNumber
                quranDwellSurahName = surahNameRu
                quranDwellAyah = ayah
                quranDwellStartMs = System.currentTimeMillis()
                val now = System.currentTimeMillis()
                preferences.addQuranHistoryEntry(
                    QuranReadingHistoryEntry(
                        surahNumber = surahNumber,
                        surahNameRu = surahNameRu,
                        ayahNumber = ayah,
                        timestamp = now,
                    ),
                )
                preferences.appendQuranReadingTrace(
                    QuranReadingTraceEntry(
                        timestamp = now,
                        surahNumber = surahNumber,
                        surahNameRu = surahNameRu,
                        ayahNumber = ayah,
                    ),
                )
            }
        }
    }

    fun onQuranVisibleAyah(surahNumber: Int, surahNameRu: String, ayah: Int) {
        viewModelScope.launch {
            quranDwellMutex.withLock {
                val a = ayah.coerceAtLeast(1)
                if (surahNumber != quranDwellSurah) {
                    commitQuranDwellSegmentLocked()
                    quranDwellSurah = surahNumber
                    quranDwellSurahName = surahNameRu
                    quranDwellAyah = a
                    quranDwellStartMs = System.currentTimeMillis()
                    val now = System.currentTimeMillis()
                    preferences.addQuranHistoryEntry(
                        QuranReadingHistoryEntry(
                            surahNumber = surahNumber,
                            surahNameRu = surahNameRu,
                            ayahNumber = a,
                            timestamp = now,
                        ),
                    )
                    preferences.appendQuranReadingTrace(
                        QuranReadingTraceEntry(
                            timestamp = now,
                            surahNumber = surahNumber,
                            surahNameRu = surahNameRu,
                            ayahNumber = a,
                        ),
                    )
                    return@withLock
                }
                if (a != quranDwellAyah) {
                    commitQuranDwellSegmentLocked()
                    quranDwellAyah = a
                    quranDwellStartMs = System.currentTimeMillis()
                    val now = System.currentTimeMillis()
                    preferences.addQuranHistoryEntry(
                        QuranReadingHistoryEntry(
                            surahNumber = surahNumber,
                            surahNameRu = surahNameRu,
                            ayahNumber = a,
                            timestamp = now,
                        ),
                    )
                    preferences.appendQuranReadingTrace(
                        QuranReadingTraceEntry(
                            timestamp = now,
                            surahNumber = surahNumber,
                            surahNameRu = surahNameRu,
                            ayahNumber = a,
                        ),
                    )
                }
            }
        }
    }

    fun flushQuranReadingDwell() {
        viewModelScope.launch {
            quranDwellMutex.withLock {
                commitQuranDwellSegmentLocked()
                quranDwellSurah = 0
                quranDwellSurahName = ""
                quranDwellAyah = 0
            }
        }
    }

    fun clearQuranReadingHistory() {
        viewModelScope.launch {
            preferences.clearQuranReadingHistory()
        }
    }

    private suspend fun commitQuranDwellSegmentLocked() {
        if (quranDwellSurah <= 0 || quranDwellAyah <= 0) return
        val elapsed = System.currentTimeMillis() - quranDwellStartMs
        val sec = if (elapsed >= 3_000L) {
            (elapsed / 1000L).toInt().coerceIn(1, 900)
        } else {
            0
        }
        if (sec > 0) {
            preferences.mergeQuranReadingAnalytics(
                surahNumber = quranDwellSurah,
                ayahNumber = quranDwellAyah,
                dwellDeltaSeconds = sec,
            )
            preferences.appendQuranReadingTrace(
                QuranReadingTraceEntry(
                    timestamp = System.currentTimeMillis(),
                    surahNumber = quranDwellSurah,
                    surahNameRu = quranDwellSurahName,
                    ayahNumber = quranDwellAyah,
                    dwellSeconds = sec,
                ),
            )
        }
        quranDwellStartMs = System.currentTimeMillis()
    }

    fun addHistoryEntry(
        translation: TranslationId,
        bookId: String,
        bookName: String,
        chapter: Int,
        verse: Int = 0,
    ) {
        viewModelScope.launch {
            preferences.addHistoryEntry(
                HistoryEntry(
                    translation = translation.code,
                    bookId = bookId,
                    bookName = bookName,
                    chapter = chapter,
                    verse = verse,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            preferences.clearHistory()
        }
    }

    private val readingDwellMutex = Mutex()
    private var dwellTransCode: String = ""
    private var dwellBookId: String = ""
    private var dwellBookName: String = ""
    private var dwellChapter: Int = 0
    private var dwellVerse: Int = 0
    private var dwellSegmentStartMs: Long = 0L
    private val dwellPendingTools = mutableSetOf<String>()

    fun beginReadingDwellSession(
        translation: TranslationId,
        bookId: String,
        bookName: String,
        chapter: Int,
        initialVerse: Int,
    ) {
        viewModelScope.launch {
            readingDwellMutex.withLock {
                commitReadingDwellSegmentLocked()
                dwellTransCode = translation.code
                dwellBookId = bookId
                dwellBookName = bookName
                dwellChapter = chapter
                dwellVerse = initialVerse.coerceAtLeast(1)
                dwellSegmentStartMs = System.currentTimeMillis()
                val iv = initialVerse.coerceAtLeast(1)
                preferences.appendReadingTrace(
                    ReadingTraceEntry(
                        timestamp = System.currentTimeMillis(),
                        translation = translation.code,
                        bookId = bookId,
                        bookName = bookName,
                        chapter = chapter,
                        verse = iv,
                        dwellSeconds = 0,
                        tools = "",
                    ),
                )
            }
        }
    }

    fun onReadingVisibleVerse(
        translation: TranslationId,
        bookId: String,
        bookName: String,
        chapter: Int,
        verse: Int,
    ) {
        viewModelScope.launch {
            readingDwellMutex.withLock {
                val v = verse.coerceAtLeast(1)
                if (bookId != dwellBookId || chapter != dwellChapter || translation.code != dwellTransCode) {
                    commitReadingDwellSegmentLocked()
                    dwellTransCode = translation.code
                    dwellBookId = bookId
                    dwellBookName = bookName
                    dwellChapter = chapter
                    dwellVerse = v
                    dwellSegmentStartMs = System.currentTimeMillis()
                    preferences.appendReadingTrace(
                        ReadingTraceEntry(
                            timestamp = System.currentTimeMillis(),
                            translation = translation.code,
                            bookId = bookId,
                            bookName = bookName,
                            chapter = chapter,
                            verse = v,
                            dwellSeconds = 0,
                            tools = "",
                        ),
                    )
                    return@withLock
                }
                if (v != dwellVerse) {
                    commitReadingDwellSegmentLocked()
                    dwellVerse = v
                    dwellSegmentStartMs = System.currentTimeMillis()
                    preferences.appendReadingTrace(
                        ReadingTraceEntry(
                            timestamp = System.currentTimeMillis(),
                            translation = dwellTransCode,
                            bookId = dwellBookId,
                            bookName = dwellBookName,
                            chapter = dwellChapter,
                            verse = v,
                            dwellSeconds = 0,
                            tools = "",
                        ),
                    )
                }
            }
        }
    }

    fun recordReadingToolUse(label: String) {
        val x = label.trim()
        if (x.isEmpty()) return
        viewModelScope.launch {
            readingDwellMutex.withLock {
                dwellPendingTools.add(x)
            }
        }
    }

    fun flushReadingDwell() {
        viewModelScope.launch {
            readingDwellMutex.withLock {
                commitReadingDwellSegmentLocked()
            }
        }
    }

    private suspend fun commitReadingDwellSegmentLocked() {
        if (dwellBookId.isEmpty() || dwellVerse <= 0 || dwellTransCode.isEmpty()) return
        val elapsed = System.currentTimeMillis() - dwellSegmentStartMs
        val sec = if (elapsed >= 3_000L) {
            (elapsed / 1000L).toInt().coerceIn(1, 900)
        } else {
            0
        }
        val tools = dwellPendingTools.toList()
        dwellPendingTools.clear()
        if (sec > 0 || tools.isNotEmpty()) {
            preferences.mergeReadingAnalytics(
                translation = dwellTransCode,
                bookId = dwellBookId,
                chapter = dwellChapter,
                verse = dwellVerse,
                dwellDeltaSeconds = sec,
                tools = tools,
            )
            preferences.appendReadingTrace(
                ReadingTraceEntry(
                    timestamp = System.currentTimeMillis(),
                    translation = dwellTransCode,
                    bookId = dwellBookId,
                    bookName = dwellBookName,
                    chapter = dwellChapter,
                    verse = dwellVerse,
                    dwellSeconds = sec,
                    tools = tools.joinToString("|"),
                ),
            )
        }
        dwellSegmentStartMs = System.currentTimeMillis()
    }

    val userNotes: StateFlow<List<UserNote>> = preferences.userNotes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val noteCustomKinds: StateFlow<List<String>> = preferences.noteCustomKinds.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    fun addNoteCustomKind(label: String) {
        viewModelScope.launch {
            preferences.addNoteCustomKind(label)
        }
    }

    fun saveNote(note: UserNote) {
        viewModelScope.launch {
            preferences.saveNote(note)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            preferences.deleteNote(noteId)
        }
    }

    /**
     * Создаёт заметку, привязанную к стиху, сохраняет и вызывает [onCreated] с id (для навигации на экран редактора).
     */
    fun createNoteForVerse(
        ref: VerseRef,
        bookName: String,
        verseText: String,
        kind: UserNoteKind = UserNoteKind.NOTE,
        onCreated: (String) -> Unit,
    ) {
        val placeTitle = BibleCanon.byId(ref.bookId)?.abbrRu?.trim()?.takeIf { it.isNotEmpty() }
            ?: bookName.trim()
        val note = UserNote(
            title = "$placeTitle ${ref.chapter}:${ref.verse}",
            verseTranslationCode = ref.translation.code,
            verseBookId = ref.bookId,
            verseChapter = ref.chapter,
            verseVerse = ref.verse,
            verseBookName = bookName,
            verseTextSnapshot = verseText.trim().ifEmpty { null },
            kind = kind,
        )
        viewModelScope.launch {
            preferences.saveNote(note)
            onCreated(note.id)
        }
    }

    val userSongs: StateFlow<List<SongItem>> = preferences.userSongs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    fun saveSong(song: SongItem) {
        viewModelScope.launch {
            preferences.saveSong(song)
        }
    }

    fun deleteSong(songId: String) {
        viewModelScope.launch {
            preferences.deleteSong(songId)
        }
    }

    val songTags: StateFlow<Set<String>> = preferences.songTags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet(),
    )

    fun addSongTag(tag: String) {
        viewModelScope.launch { preferences.addSongTag(tag) }
    }

    fun removeSongTag(tag: String) {
        viewModelScope.launch { preferences.removeSongTag(tag) }
    }

    private val bibleImageLibrary = BibleImageLibrary(appContext)

    val bibleUserImages: StateFlow<List<BibleUserImage>> = preferences.userBibleImages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    private val _commonsSearchLoading = MutableStateFlow(false)
    val commonsSearchLoading: StateFlow<Boolean> = _commonsSearchLoading.asStateFlow()

    private val _commonsSearchResults = MutableStateFlow<List<CommonsSearchResult>>(emptyList())
    val commonsSearchResults: StateFlow<List<CommonsSearchResult>> = _commonsSearchResults.asStateFlow()

    /** Последний завершённый запрос поиска картинок (для пустого стейта, не до нажатия «Искать»). */
    private val _commonsSearchLastQuery = MutableStateFlow("")
    val commonsSearchLastQuery: StateFlow<String> = _commonsSearchLastQuery.asStateFlow()

    fun clearCommonsSearch() {
        _commonsSearchResults.value = emptyList()
        _commonsSearchLastQuery.value = ""
    }

    fun searchCommonsImages(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _commonsSearchResults.value = emptyList()
            _commonsSearchLastQuery.value = ""
            return
        }
        viewModelScope.launch {
            _commonsSearchLoading.value = true
            try {
                _commonsSearchResults.value = WebImageSearch.searchCombined(q, limit = 72)
            } catch (_: Exception) {
                _commonsSearchResults.value = emptyList()
            } finally {
                _commonsSearchLoading.value = false
                _commonsSearchLastQuery.value = q
            }
        }
    }

    fun importBibleImageFromUri(
        uri: Uri,
        title: String,
        tags: List<String>,
        source: String,
        sourceUrl: String? = null,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bibleImageLibrary.importFromUri(uri)
            }
            result.fold(
                onSuccess = { fileName ->
                    val normTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
                    preferences.saveBibleImage(
                        BibleUserImage(
                            title = title.trim().ifBlank { "Без названия" },
                            tags = normTags,
                            fileName = fileName,
                            source = source,
                            sourceUrl = sourceUrl,
                        ),
                    )
                    onDone(null)
                },
                onFailure = { e -> onDone(e.message ?: "Ошибка импорта") },
            )
        }
    }

    fun importBibleImageFromRemoteUrl(
        fullUrl: String,
        title: String,
        tags: List<String>,
        sourceUrl: String,
        /** commons, web_google, web_yandex и т.п. */
        imageSource: String = "commons",
        onDone: (String?) -> Unit,
    ) {
        if (SafeImagePolicy.isBlockedRemoteImport(fullUrl, title, sourceUrl)) {
            onDone("Такой контент не сохраняется по правилам безопасности")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bibleImageLibrary.downloadFromUrl(fullUrl)
            }
            result.fold(
                onSuccess = { fileName ->
                    val normTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
                    preferences.saveBibleImage(
                        BibleUserImage(
                            title = title.trim().ifBlank { "Без названия" },
                            tags = normTags,
                            fileName = fileName,
                            source = imageSource,
                            sourceUrl = sourceUrl,
                        ),
                    )
                    onDone(null)
                },
                onFailure = { e -> onDone(e.message ?: "Ошибка загрузки") },
            )
        }
    }

    fun updateBibleImage(image: BibleUserImage) {
        viewModelScope.launch {
            preferences.saveBibleImage(image)
        }
    }

    fun deleteBibleImage(image: BibleUserImage) {
        viewModelScope.launch {
            bibleImageLibrary.deleteStoredFile(image.fileName)
            preferences.deleteBibleImage(image.id)
        }
    }

    private val bibleVideoLibrary = BibleVideoLibrary(appContext)
    private val bibleAudioLibrary = BibleAudioLibrary(appContext)

    private val publicDownloadImportMutex = Mutex()

    val bibleUserVideos: StateFlow<List<BibleUserVideo>> = preferences.userBibleVideos.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val bibleUserAudios: StateFlow<List<BibleUserAudio>> = preferences.userBibleAudios.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val userMediaPlaylists: StateFlow<List<UserMediaPlaylist>> = preferences.userMediaPlaylists.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    fun createUserMediaPlaylist(name: String, kind: UserMediaPlaylistKind, initialItemIds: List<String> = emptyList()) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            preferences.saveUserMediaPlaylist(
                UserMediaPlaylist(
                    name = n,
                    kind = kind,
                    itemIds = initialItemIds,
                ),
            )
        }
    }

    fun renameUserMediaPlaylist(playlistId: String, newName: String) {
        val n = newName.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            val cur = userMediaPlaylists.value
            val pl = cur.firstOrNull { it.id == playlistId } ?: return@launch
            preferences.saveUserMediaPlaylist(pl.copy(name = n))
        }
    }

    fun deleteUserMediaPlaylist(playlistId: String) {
        viewModelScope.launch {
            preferences.deleteUserMediaPlaylist(playlistId)
        }
    }

    fun addItemToUserMediaPlaylist(playlistId: String, mediaItemId: String) {
        addItemsToUserMediaPlaylist(playlistId, listOf(mediaItemId))
    }

    fun addItemsToUserMediaPlaylist(playlistId: String, mediaItemIds: List<String>) {
        if (mediaItemIds.isEmpty()) return
        viewModelScope.launch {
            preferences.addMediaItemsToUserPlaylist(playlistId, mediaItemIds)
        }
    }

    fun removeItemFromUserMediaPlaylist(playlistId: String, mediaItemId: String) {
        viewModelScope.launch {
            preferences.removeMediaItemFromUserPlaylist(playlistId, mediaItemId)
        }
    }

    fun setUserMediaPlaylistItemOrder(playlistId: String, orderedItemIds: List<String>) {
        viewModelScope.launch {
            preferences.setUserMediaPlaylistItemOrder(playlistId, orderedItemIds)
        }
    }

    fun importUserMediaPlaylistFromFile(file: File, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val outcome = UserMediaPlaylistSharePackage.importFromFile(
                appContext,
                file,
                bibleUserVideos.value,
                bibleUserAudios.value,
                bibleVideoLibrary,
                bibleAudioLibrary,
            )
            when (outcome) {
                is UserMediaPlaylistShareOutcome.Ok -> {
                    outcome.videos.forEach { preferences.saveBibleVideo(it) }
                    outcome.audios.forEach { preferences.saveBibleAudio(it) }
                    val existing = userMediaPlaylists.value
                    val name = uniqueUserMediaPlaylistName(outcome.playlist.name, existing)
                    preferences.saveUserMediaPlaylist(outcome.playlist.copy(name = name))
                    val filesBit = when {
                        outcome.filesCopied > 0 ->
                            " Файлов скопировано: ${outcome.filesCopied}."
                        outcome.linksOnly ->
                            " Файлы не вложены — их можно скачать по ссылкам в плейлисте."
                        else -> ""
                    }
                    onDone("Плейлист «$name» импортирован.$filesBit")
                }
                is UserMediaPlaylistShareOutcome.Err -> onDone(
                    when (outcome.error) {
                        UserMediaPlaylistShareError.MISSING_MANIFEST ->
                            "В файле нет описания плейлиста"
                        UserMediaPlaylistShareError.FULL_APP_BACKUP ->
                            "Это полный архив приложения, а не плейлист"
                        UserMediaPlaylistShareError.WRONG_FORMAT ->
                            "Неизвестный формат файла"
                        UserMediaPlaylistShareError.EMPTY ->
                            "Плейлист пустой"
                        UserMediaPlaylistShareError.IO_OR_PARSE ->
                            "Не удалось прочитать файл"
                    },
                )
            }
        }
    }

    fun downloadUserMediaItemFromUrl(
        mediaId: String,
        kind: UserMediaKind,
        onDone: (ok: Boolean, message: String) -> Unit,
    ) {
        viewModelScope.launch {
            when (kind) {
                UserMediaKind.VIDEO -> {
                    val v = bibleUserVideos.value.firstOrNull { it.id == mediaId } ?: run {
                        onDone(false, "Запись не найдена")
                        return@launch
                    }
                    val url = v.sourceUrl?.takeIf { it.isNotBlank() } ?: run {
                        onDone(false, "Нет ссылки для скачивания")
                        return@launch
                    }
                    val result = withContext(Dispatchers.IO) {
                        bibleVideoLibrary.downloadFromUrl(url)
                    }
                    result.fold(
                        onSuccess = { name ->
                            preferences.saveBibleVideo(v.copy(fileName = name))
                            onDone(true, "Скачано: ${v.title}")
                        },
                        onFailure = { e -> onDone(false, e.message ?: "Ошибка загрузки") },
                    )
                }
                UserMediaKind.AUDIO -> {
                    val a = bibleUserAudios.value.firstOrNull { it.id == mediaId } ?: run {
                        onDone(false, "Запись не найдена")
                        return@launch
                    }
                    val url = a.sourceUrl?.takeIf { it.isNotBlank() } ?: run {
                        onDone(false, "Нет ссылки для скачивания")
                        return@launch
                    }
                    val result = withContext(Dispatchers.IO) {
                        bibleAudioLibrary.downloadFromUrl(url)
                    }
                    result.fold(
                        onSuccess = { name ->
                            preferences.saveBibleAudio(a.copy(fileName = name))
                            onDone(true, "Скачано: ${a.title}")
                        },
                        onFailure = { e -> onDone(false, e.message ?: "Ошибка загрузки") },
                    )
                }
            }
        }
    }

    fun downloadMissingUserMediaPlaylistFiles(
        playlistId: String,
        onProgress: (done: Int, total: Int) -> Unit,
        onDone: (ok: Int, fail: Int) -> Unit,
    ) {
        viewModelScope.launch {
            val pl = userMediaPlaylists.value.firstOrNull { it.id == playlistId } ?: run {
                onDone(0, 0)
                return@launch
            }
            val videos = bibleUserVideos.value.associateBy { it.id }
            val audios = bibleUserAudios.value.associateBy { it.id }
            val videoTasks = if (pl.kind == UserMediaPlaylistKind.VIDEO) {
                pl.itemIds.mapNotNull { id ->
                    val v = videos[id] ?: return@mapNotNull null
                    val f = MediaCatalogPaths.videoFile(appContext, v.fileName)
                    if (f.isFile && f.length() > 64) return@mapNotNull null
                    val url = v.sourceUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    v to url
                }
            } else {
                emptyList()
            }
            val audioTasks = if (pl.kind == UserMediaPlaylistKind.AUDIO) {
                pl.itemIds.mapNotNull { id ->
                    val a = audios[id] ?: return@mapNotNull null
                    val f = MediaCatalogPaths.audioFile(appContext, a.fileName)
                    if (f.isFile && f.length() > 64) return@mapNotNull null
                    val url = a.sourceUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    a to url
                }
            } else {
                emptyList()
            }
            val total = videoTasks.size + audioTasks.size
            if (total == 0) {
                onDone(0, 0)
                return@launch
            }
            var ok = 0
            var fail = 0
            var index = 0
            for ((item, url) in videoTasks) {
                onProgress(index, total)
                val result = withContext(Dispatchers.IO) {
                    bibleVideoLibrary.downloadFromUrl(url)
                }
                result.fold(
                    onSuccess = { name ->
                        preferences.saveBibleVideo(item.copy(fileName = name))
                        ok++
                    },
                    onFailure = { fail++ },
                )
                index++
            }
            for ((item, url) in audioTasks) {
                onProgress(index, total)
                val result = withContext(Dispatchers.IO) {
                    bibleAudioLibrary.downloadFromUrl(url)
                }
                result.fold(
                    onSuccess = { name ->
                        preferences.saveBibleAudio(item.copy(fileName = name))
                        ok++
                    },
                    onFailure = { fail++ },
                )
                index++
            }
            onProgress(total, total)
            onDone(ok, fail)
        }
    }

    private fun uniqueUserMediaPlaylistName(
        base: String,
        existing: List<UserMediaPlaylist>,
    ): String {
        if (existing.none { it.name.equals(base, ignoreCase = true) }) return base
        var n = 2
        while (existing.any { it.name.equals("$base ($n)", ignoreCase = true) }) n++
        return "$base ($n)"
    }

    val userMediaPlaybackProgress: StateFlow<Map<String, UserMediaPlaybackProgress>> =
        preferences.userMediaPlaybackProgress.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyMap(),
        )

    fun updateMediaPlaybackProgress(
        mediaId: String,
        kind: UserMediaKind,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (mediaId.isBlank()) return
        // Плеер до prepare пишет duration=1 и position=0 — это затирало «продолжить с …».
        if (durationMs < 1_500L && positionMs < 2_000L) return
        val completed = durationMs > 0 && positionMs >= durationMs * 0.92
        viewModelScope.launch {
            preferences.upsertMediaPlaybackProgress(
                UserMediaPlaybackProgress(
                    mediaId = mediaId,
                    kind = kind,
                    positionMs = if (completed) durationMs else positionMs.coerceAtLeast(0),
                    durationMs = durationMs.coerceAtLeast(0),
                    completed = completed,
                ),
            )
        }
    }

    fun markMediaFullyWatched(mediaId: String, kind: UserMediaKind, durationMs: Long = 0L) {
        if (mediaId.isBlank()) return
        viewModelScope.launch {
            preferences.markMediaFullyWatched(mediaId, kind, durationMs)
        }
    }

    fun unmarkMediaFullyWatched(mediaId: String) {
        if (mediaId.isBlank()) return
        viewModelScope.launch {
            preferences.unmarkMediaFullyWatched(mediaId)
        }
    }

    fun clearMediaPlaybackProgress(mediaId: String) {
        if (mediaId.isBlank()) return
        viewModelScope.launch {
            preferences.clearMediaPlaybackProgress(mediaId)
        }
    }

    private val _commonsVideoSearchLoading = MutableStateFlow(false)
    val commonsVideoSearchLoading: StateFlow<Boolean> = _commonsVideoSearchLoading.asStateFlow()

    private val _commonsVideoSearchResults = MutableStateFlow<List<CommonsSearchResult>>(emptyList())
    val commonsVideoSearchResults: StateFlow<List<CommonsSearchResult>> = _commonsVideoSearchResults.asStateFlow()

    fun clearCommonsVideoSearch() {
        _commonsVideoSearchResults.value = emptyList()
    }

    fun searchCommonsVideos(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _commonsVideoSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _commonsVideoSearchLoading.value = true
            try {
                _commonsVideoSearchResults.value = CommonsVideoSearch.search(q)
            } finally {
                _commonsVideoSearchLoading.value = false
            }
        }
    }

    fun importBibleVideoFromUri(
        uri: Uri,
        title: String,
        tags: List<String>,
        source: String,
        sourceUrl: String? = null,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bibleVideoLibrary.importFromUri(uri)
            }
            result.fold(
                onSuccess = { fileName ->
                    val normTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
                    preferences.saveBibleVideo(
                        BibleUserVideo(
                            title = title.trim().ifBlank { "Без названия" },
                            tags = normTags,
                            fileName = fileName,
                            source = source,
                            sourceUrl = sourceUrl,
                        ),
                    )
                    onDone(null)
                },
                onFailure = { e -> onDone(e.message ?: "Ошибка импорта") },
            )
        }
    }

    fun importBibleVideoFromRemoteUrl(
        fullUrl: String,
        title: String,
        tags: List<String>,
        sourceUrl: String,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bibleVideoLibrary.downloadFromUrl(fullUrl)
            }
            result.fold(
                onSuccess = { fileName ->
                    val normTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
                    preferences.saveBibleVideo(
                        BibleUserVideo(
                            title = title.trim().ifBlank { "Без названия" },
                            tags = normTags,
                            fileName = fileName,
                            source = "commons",
                            sourceUrl = sourceUrl,
                        ),
                    )
                    onDone(null)
                },
                onFailure = { e -> onDone(e.message ?: "Ошибка загрузки") },
            )
        }
    }

    fun updateBibleVideo(video: BibleUserVideo) {
        viewModelScope.launch {
            preferences.saveBibleVideo(video)
        }
    }

    fun deleteBibleVideo(video: BibleUserVideo) {
        viewModelScope.launch {
            bibleVideoLibrary.deleteStoredFile(video.fileName)
            preferences.deleteBibleVideo(video.id)
        }
    }

    fun importBibleAudioFromUri(
        uri: Uri,
        title: String,
        tags: List<String>,
        source: String,
        sourceUrl: String? = null,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bibleAudioLibrary.importFromUri(uri)
            }
            result.fold(
                onSuccess = { fileName ->
                    val normTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
                    preferences.saveBibleAudio(
                        BibleUserAudio(
                            title = title.trim().ifBlank { "Без названия" },
                            tags = normTags,
                            fileName = fileName,
                            source = source,
                            sourceUrl = sourceUrl,
                        ),
                    )
                    onDone(null)
                },
                onFailure = { e -> onDone(e.message ?: "Ошибка импорта") },
            )
        }
    }

    fun importBibleAudiosFromUris(
        uris: List<Uri>,
        source: String,
        tags: List<String> = emptyList(),
        onDone: (ok: Int, fail: Int) -> Unit,
    ) {
        if (uris.isEmpty()) {
            onDone(0, 0)
            return
        }
        viewModelScope.launch {
            val normTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
            var ok = 0
            var fail = 0
            for (uri in uris) {
                val title = withContext(Dispatchers.IO) {
                    KidsUserMediaStorage.displayName(appContext, uri)
                        ?.substringBeforeLast('.')
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "Без названия" }
                }
                val result = withContext(Dispatchers.IO) {
                    bibleAudioLibrary.importFromUri(uri)
                }
                result.fold(
                    onSuccess = { fileName ->
                        preferences.saveBibleAudio(
                            BibleUserAudio(
                                title = title,
                                tags = normTags,
                                fileName = fileName,
                                source = source,
                            ),
                        )
                        ok++
                    },
                    onFailure = { fail++ },
                )
            }
            onDone(ok, fail)
        }
    }

    fun importBibleAudioFromRemoteUrl(
        fullUrl: String,
        title: String,
        tags: List<String>,
        sourceUrl: String,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bibleAudioLibrary.downloadFromUrl(fullUrl)
            }
            result.fold(
                onSuccess = { fileName ->
                    val normTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
                    preferences.saveBibleAudio(
                        BibleUserAudio(
                            title = title.trim().ifBlank { "Без названия" },
                            tags = normTags,
                            fileName = fileName,
                            source = "commons",
                            sourceUrl = sourceUrl,
                        ),
                    )
                    onDone(null)
                },
                onFailure = { e -> onDone(e.message ?: "Ошибка загрузки") },
            )
        }
    }

    fun updateBibleAudio(audio: BibleUserAudio) {
        viewModelScope.launch {
            preferences.saveBibleAudio(audio)
        }
    }

    fun deleteBibleAudio(audio: BibleUserAudio) {
        viewModelScope.launch {
            bibleAudioLibrary.deleteStoredFile(audio.fileName)
            preferences.deleteBibleAudio(audio.id)
        }
    }

    /**
     * Импорт в «Медиа → Аудио» после скачивания в папку загрузок или через yt-dlp.
     */
    fun importBibleAudioFromPublicDownloadFile(
        sourceFile: File,
        title: String,
        sourceUrl: String?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            publicDownloadImportMutex.withLock {
                if (!sourceFile.exists()) {
                    onDone("Файл не найден")
                    return@withLock
                }
                val fp = fingerprintPublicDownloadFile(sourceFile)
                if (preferences.hasDownloadImportFingerprint(fp)) {
                    onDone(null)
                    return@withLock
                }
                val result = withContext(Dispatchers.IO) {
                    bibleAudioLibrary.importFromExternalFile(sourceFile)
                }
                result.fold(
                    onSuccess = { fileName ->
                        preferences.saveBibleAudio(
                            BibleUserAudio(
                                title = title.trim().ifBlank { sourceFile.nameWithoutExtension.ifBlank { "Без названия" } },
                                tags = emptyList(),
                                fileName = fileName,
                                source = "download",
                                sourceUrl = sourceUrl,
                            ),
                        )
                        preferences.addDownloadImportFingerprint(fp)
                        onDone(null)
                    },
                    onFailure = { e -> onDone(e.message ?: "Ошибка импорта") },
                )
            }
        }
    }

    private fun fingerprintPublicDownloadFile(f: File) =
        "${f.absolutePath}|${f.length()}|${f.lastModified()}"

    /**
     * Переносит файл из «Загрузки/Bible» в базу «Медиа → Видео» (после скачивания по ссылке).
     */
    fun importBibleVideoFromPublicDownloadFile(
        sourceFile: File,
        title: String,
        sourceUrl: String?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            publicDownloadImportMutex.withLock {
                if (!sourceFile.exists()) {
                    onDone("Файл не найден")
                    return@withLock
                }
                val fp = fingerprintPublicDownloadFile(sourceFile)
                if (preferences.hasDownloadImportFingerprint(fp)) {
                    onDone(null)
                    return@withLock
                }
                val result = withContext(Dispatchers.IO) {
                    bibleVideoLibrary.importFromExternalFile(sourceFile)
                }
                result.fold(
                    onSuccess = { fileName ->
                        preferences.saveBibleVideo(
                            BibleUserVideo(
                                title = title.trim().ifBlank { sourceFile.nameWithoutExtension.ifBlank { "Без названия" } },
                                tags = emptyList(),
                                fileName = fileName,
                                source = "download",
                                sourceUrl = sourceUrl,
                            ),
                        )
                        preferences.addDownloadImportFingerprint(fp)
                        onDone(null)
                    },
                    onFailure = { e -> onDone(e.message ?: "Ошибка импорта") },
                )
            }
        }
    }

    /** Импортирует оставшиеся файлы из публичной папки загрузок (миграция и догонка). */
    fun syncLegacyDownloadsFromPublicFolder() {
        viewModelScope.launch {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Bible",
            )
            if (!dir.isDirectory) return@launch
            val exts = setOf(
                "mp4", "webm", "mkv", "mov", "3gp", "ogv",
            )
            val files = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in exts }
                ?.sortedByDescending { it.lastModified() }
                ?: return@launch
            for (f in files) {
                publicDownloadImportMutex.withLock {
                    if (!f.exists()) return@withLock
                    val fp = fingerprintPublicDownloadFile(f)
                    if (preferences.hasDownloadImportFingerprint(fp)) return@withLock
                    val result = withContext(Dispatchers.IO) {
                        bibleVideoLibrary.importFromExternalFile(f)
                    }
                    result.fold(
                        onSuccess = { fileName ->
                            preferences.saveBibleVideo(
                                BibleUserVideo(
                                    title = f.nameWithoutExtension.ifBlank { "Без названия" },
                                    tags = emptyList(),
                                    fileName = fileName,
                                    source = "download",
                                    sourceUrl = null,
                                ),
                            )
                            preferences.addDownloadImportFingerprint(fp)
                        },
                        onFailure = { },
                    )
                }
            }
        }
    }

    /** Импорт аудио из «Загрузки/Bible» в базу «Медиа → Аудио». */
    fun syncLegacyAudioDownloadsFromPublicFolder() {
        viewModelScope.launch {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Bible",
            )
            if (!dir.isDirectory) return@launch
            val exts = setOf(
                "mp3", "ogg", "m4a", "opus", "aac", "wav", "flac",
            )
            val files = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in exts }
                ?.sortedByDescending { it.lastModified() }
                ?: return@launch
            for (f in files) {
                publicDownloadImportMutex.withLock {
                    if (!f.exists()) return@withLock
                    val fp = fingerprintPublicDownloadFile(f)
                    if (preferences.hasDownloadImportFingerprint(fp)) return@withLock
                    val result = withContext(Dispatchers.IO) {
                        bibleAudioLibrary.importFromExternalFile(f)
                    }
                    result.fold(
                        onSuccess = { fileName ->
                            preferences.saveBibleAudio(
                                BibleUserAudio(
                                    title = f.nameWithoutExtension.ifBlank { "Без названия" },
                                    tags = emptyList(),
                                    fileName = fileName,
                                    source = "download",
                                    sourceUrl = null,
                                ),
                            )
                            preferences.addDownloadImportFingerprint(fp)
                        },
                        onFailure = { },
                    )
                }
            }
        }
    }

    val audioNarratorId: StateFlow<String> = preferences.audioNarratorId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "bondarenko",
    )

    fun setAudioNarrator(id: String) {
        viewModelScope.launch { preferences.setAudioNarrator(id) }
    }

    // --- Online translation loading (per chapter) ---

    private val _onlineChapterLoading = MutableStateFlow(false)
    val onlineChapterLoading: StateFlow<Boolean> = _onlineChapterLoading.asStateFlow()

    private val _onlineChapterVerses = MutableStateFlow<List<com.example.bible.data.BibleVerse>>(emptyList())
    val onlineChapterVerses: StateFlow<List<com.example.bible.data.BibleVerse>> = _onlineChapterVerses.asStateFlow()

    private val _onlineChapterError = MutableStateFlow<String?>(null)
    val onlineChapterError: StateFlow<String?> = _onlineChapterError.asStateFlow()

    fun loadOnlineChapter(
        translation: TranslationId,
        bookId: String,
        chapter: Int,
    ) {
        val code = translation.onlineCode ?: return
        _onlineChapterLoading.value = true
        _onlineChapterError.value = null
        _onlineChapterVerses.value = emptyList()
        viewModelScope.launch {
            try {
                val verses = com.example.bible.data.StudyBibleRepository.fetchChapterText(code, bookId, chapter)
                if (verses.isEmpty()) {
                    _onlineChapterError.value = "Не удалось загрузить текст"
                } else {
                    _onlineChapterVerses.value = verses.map { (n, t) ->
                        com.example.bible.data.BibleVerse(n, t)
                    }
                }
            } catch (e: Exception) {
                _onlineChapterError.value = e.message ?: "Ошибка загрузки"
            } finally {
                _onlineChapterLoading.value = false
            }
        }
    }

    fun clearOnlineChapter() {
        _onlineChapterVerses.value = emptyList()
        _onlineChapterError.value = null
    }

    suspend fun fetchOnlineVerses(
        translation: TranslationId,
        bookId: String,
        chapter: Int,
    ): List<com.example.bible.data.BibleVerse> {
        val code = translation.onlineCode ?: return emptyList()
        val raw = com.example.bible.data.StudyBibleRepository.fetchChapterText(code, bookId, chapter)
        return raw.map { (n, t) -> com.example.bible.data.BibleVerse(n, t) }
    }

    // --- StudyBible online features ---

    private val _onlineCommentary = MutableStateFlow("")
    val onlineCommentary: StateFlow<String> = _onlineCommentary.asStateFlow()

    private val _verseComparisons = MutableStateFlow<List<com.example.bible.data.VerseComparison>>(emptyList())
    val verseComparisons: StateFlow<List<com.example.bible.data.VerseComparison>> = _verseComparisons.asStateFlow()

    private val _crossReferences = MutableStateFlow<List<com.example.bible.data.CrossReference>>(emptyList())
    val crossReferences: StateFlow<List<com.example.bible.data.CrossReference>> = _crossReferences.asStateFlow()

    private val _strongWords = MutableStateFlow<List<com.example.bible.data.StrongWord>>(emptyList())
    val strongWords: StateFlow<List<com.example.bible.data.StrongWord>> = _strongWords.asStateFlow()

    private val _studyLoading = MutableStateFlow(false)
    val studyLoading: StateFlow<Boolean> = _studyLoading.asStateFlow()

    fun loadOnlineCommentary(slug: String, bookId: String, chapter: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            studyContentCache.getCommentary(slug, bookId, chapter)?.let { cached ->
                _onlineCommentary.value = cached
                _studyLoading.value = false
                return@launch
            }
            _studyLoading.value = true
            val text = com.example.bible.data.StudyBibleRepository.fetchCommentary(slug, bookId, chapter)
            if (text.isNotBlank()) {
                studyContentCache.putCommentary(slug, bookId, chapter, text)
            }
            _onlineCommentary.value = text
            _studyLoading.value = false
        }
    }

    fun loadVerseComparison(bookId: String, chapter: Int, verse: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            studyContentCache.getVerseComparisons(bookId, chapter, verse)?.let { cached ->
                _verseComparisons.value = cached
                _studyLoading.value = false
                return@launch
            }
            _studyLoading.value = true
            val list = com.example.bible.data.StudyBibleRepository.fetchVerseComparison(bookId, chapter, verse)
            if (list.isNotEmpty()) {
                studyContentCache.putVerseComparisons(bookId, chapter, verse, list)
            }
            _verseComparisons.value = list
            _studyLoading.value = false
        }
    }

    fun loadCrossReferences(bookId: String, chapter: Int, verse: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            studyContentCache.getCrossReferences(bookId, chapter, verse)?.let { cached ->
                _crossReferences.value = cached
                _studyLoading.value = false
                return@launch
            }
            _studyLoading.value = true
            val list = com.example.bible.data.StudyBibleRepository.fetchCrossReferences(bookId, chapter, verse)
            if (list.isNotEmpty()) {
                studyContentCache.putCrossReferences(bookId, chapter, verse, list)
            }
            _crossReferences.value = list
            _studyLoading.value = false
        }
    }

    fun loadStrongNumbers(bookId: String, chapter: Int, verse: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            studyContentCache.getStrongWords(bookId, chapter, verse)?.let { cached ->
                _strongWords.value = cached
                _studyLoading.value = false
                return@launch
            }
            _studyLoading.value = true
            val list = com.example.bible.data.StudyBibleRepository.fetchStrongNumbers(bookId, chapter, verse)
            if (list.isNotEmpty()) {
                studyContentCache.putStrongWords(bookId, chapter, verse, list)
            }
            _strongWords.value = list
            _studyLoading.value = false
        }
    }

    fun clearStudyData() {
        _onlineCommentary.value = ""
        _verseComparisons.value = emptyList()
        _crossReferences.value = emptyList()
        _strongWords.value = emptyList()
    }

    /** Для подписи «В кэше» в инструментах изучения. */
    fun hasCachedChapterCommentary(slug: String, bookId: String, chapter: Int): Boolean =
        studyContentCache.getCommentary(slug, bookId, chapter)?.isNotBlank() == true

    fun hasCachedVerseComparisons(bookId: String, chapter: Int, verse: Int): Boolean =
        studyContentCache.getVerseComparisons(bookId, chapter, verse) != null

    fun hasCachedCrossReferences(bookId: String, chapter: Int, verse: Int): Boolean =
        studyContentCache.getCrossReferences(bookId, chapter, verse) != null

    fun hasCachedStrongWords(bookId: String, chapter: Int, verse: Int): Boolean =
        studyContentCache.getStrongWords(bookId, chapter, verse) != null

    private val _offlineDownload = MutableStateFlow<OfflineDownloadUiState?>(null)
    val offlineDownload: StateFlow<OfflineDownloadUiState?> = _offlineDownload.asStateFlow()

    /** Порядок книг для офлайн-предзагрузки (сверху — в первую очередь). */
    val offlineDownloadBookOrder: StateFlow<List<String>> = preferences.offlineDownloadBookOrder.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        OfflineDownloadBookOrder.defaultOrder(),
    )

    private var offlineDownloadJob: Job? = null

    fun setOfflineDownloadBookOrder(ids: List<String>) {
        viewModelScope.launch {
            preferences.setOfflineDownloadBookOrder(OfflineDownloadBookOrder.normalize(ids))
        }
    }

    /** Порядок разделов на экране «Каталог медиа». */
    val mediaHomeSectionOrder: StateFlow<List<String>> = preferences.mediaHomeSectionOrder.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MediaHomeSectionOrder.defaultOrder(),
    )

    fun setMediaHomeSectionOrder(ids: List<String>) {
        viewModelScope.launch {
            preferences.setMediaHomeSectionOrder(MediaHomeSectionOrder.normalize(ids))
        }
    }

    /** Порядок пунктов главного меню (экран книг), без «Настройки» и переводов. */
    val booksMainMenuOrder: StateFlow<List<String>> = preferences.booksMainMenuOrder.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BooksMainMenuOrder.defaultOrder(),
    )

    fun setBooksMainMenuOrder(ids: List<String>) {
        viewModelScope.launch {
            preferences.setBooksMainMenuOrder(BooksMainMenuOrder.normalize(ids))
        }
    }

    /** Пользовательские разделы «Детям» (хаб, альбомы, свои медиа). */
    val kidsUserSections: StateFlow<KidsUserSectionsState> = preferences.kidsUserSections.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        KidsUserSectionsState.empty(),
    )

    fun setKidsUserSections(state: KidsUserSectionsState) {
        viewModelScope.launch {
            preferences.setKidsUserSections(state)
        }
    }

    fun cancelOfflineDownload() {
        offlineDownloadJob?.cancel()
        offlineDownloadJob = null
    }

    fun dismissOfflineDownloadUi() {
        _offlineDownload.value = null
    }

    /**
     * Заранее скачивает в локальный кэш комментарии и материалы «Изучение».
     * Тексты переводов уже в приложении; здесь не нужны.
     */
    fun startOfflineDownload(
        chapterCommentaries: Boolean,
        verseStudyTools: Boolean,
        apiCommentary: Boolean,
        apiTranslation: TranslationId,
        bookOrder: List<String>,
    ) {
        if (offlineDownloadJob?.isActive == true) return
        if (!chapterCommentaries && !verseStudyTools && !apiCommentary) return
        val bookIds = OfflineDownloadBookOrder.normalize(bookOrder)
        offlineDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (chapterCommentaries) {
                    StudyBulkDownloader.downloadChapterCommentaries(
                        studyContentCache,
                        delayMs = 300L,
                        bookIdsInOrder = bookIds,
                        onProgress = { cur, tot, label ->
                            _offlineDownload.value = OfflineDownloadUiState(
                                running = true,
                                phase = "Комментарии к главам",
                                current = cur,
                                total = tot.coerceAtLeast(1),
                                detail = label,
                            )
                        },
                    )
                }
                if (verseStudyTools) {
                    StudyBulkDownloader.downloadVerseStudyTools(
                        repository,
                        studyContentCache,
                        delayMs = 280L,
                        bookIdsInOrder = bookIds,
                        onProgress = { cur, tot, label ->
                            _offlineDownload.value = OfflineDownloadUiState(
                                running = true,
                                phase = "Переводы, ссылки, Стронг",
                                current = cur,
                                total = tot.coerceAtLeast(1),
                                detail = label,
                            )
                        },
                    )
                }
                if (apiCommentary) {
                    StudyBulkDownloader.downloadApiVerseCommentaries(
                        repository,
                        studyContentCache,
                        commentaryRepository,
                        apiTranslation,
                        delayMs = 220L,
                        bookIdsInOrder = bookIds,
                        onProgress = { cur, tot, label ->
                            _offlineDownload.value = OfflineDownloadUiState(
                                running = true,
                                phase = "Комментарии к стихам (API)",
                                current = cur,
                                total = tot.coerceAtLeast(1),
                                detail = label,
                            )
                        },
                    )
                }
                _offlineDownload.value = OfflineDownloadUiState(
                    running = false,
                    phase = "Готово",
                    current = 1,
                    total = 1,
                    detail = "Данные сохранены в память устройства",
                )
            } catch (e: CancellationException) {
                _offlineDownload.value = OfflineDownloadUiState(
                    running = false,
                    phase = "Отменено",
                    current = 0,
                    total = 0,
                    detail = "",
                )
                throw e
            } catch (e: Exception) {
                _offlineDownload.value = OfflineDownloadUiState(
                    running = false,
                    phase = "Ошибка",
                    current = 0,
                    total = 0,
                    detail = e.message ?: e.toString(),
                )
            }
        }
    }
}

class BibleViewModelFactory(
    private val context: Context,
    private val repository: BibleRepository,
    private val preferences: BiblePreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BibleViewModel::class.java)) {
            return BibleViewModel(
                repository = repository,
                preferences = preferences,
                appContext = context.applicationContext,
                mediaRepository = MediaRepository(context.applicationContext),
                studyContentCache = StudyContentCache(context.applicationContext),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
