package com.example.bible.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

private val Context.bibleDataStore: DataStore<Preferences> by preferencesDataStore(name = "bible_user_prefs")

private const val READING_TRACE_MAX_ENTRIES = 800

private object Keys {
    val TRANSLATION = stringPreferencesKey("translation")
    val BOOKMARKS = stringSetPreferencesKey("bookmarks")
    val HIGHLIGHTS_JSON = stringPreferencesKey("text_highlights_json")
    val READER_FONT_SCALE = floatPreferencesKey("reader_font_scale")
    /** Абсолютный размер шрифта текста песен (sp). */
    val SONG_FONT_SIZE = floatPreferencesKey("song_font_size")
    /** Подсветка текущей строки текста при воспроизведении аудио (если есть таймкоды). */
    val SONG_HIGHLIGHT_LINE_WHILE_PLAYING = booleanPreferencesKey("song_highlight_line_while_playing")
    /** Масштаб текста названий в списке видео (Медиа → Видео). */
    val VIDEO_LIBRARY_TITLE_SCALE = floatPreferencesKey("video_library_title_scale")
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val HISTORY_JSON = stringPreferencesKey("reading_history_json")
    /** Хронология: какие стихи открывались и сколько на них задерживались (порядок по времени). */
    val READING_TRACE_JSON = stringPreferencesKey("reading_trace_json")
    val NOTES_JSON = stringPreferencesKey("user_notes_json")
    /** JSON-массив строк — пользовательские названия типов заметок (чипы в редакторе). */
    val NOTES_CUSTOM_KINDS_JSON = stringPreferencesKey("note_custom_kinds_json")
    val SONGS_JSON = stringPreferencesKey("user_songs_json")
    val SONG_TAGS = stringSetPreferencesKey("user_song_tags")
    val AUDIO_NARRATOR = stringPreferencesKey("audio_narrator_id")
    val READING_PLAN_COMPLETED = stringSetPreferencesKey("reading_plan_completed_dates")
    val READING_PLAN_REMINDER_HOUR = intPreferencesKey("reading_plan_reminder_h")
    val READING_PLAN_REMINDER_MIN = intPreferencesKey("reading_plan_reminder_m")
    val BOOKMARK_TAGS_JSON = stringPreferencesKey("bookmark_tags_json")
    val USER_BIBLE_IMAGES_JSON = stringPreferencesKey("user_bible_images_json")
    val USER_BIBLE_VIDEOS_JSON = stringPreferencesKey("user_bible_videos_json")
    val USER_BIBLE_AUDIOS_JSON = stringPreferencesKey("user_bible_audios_json")
    /** JSON-массив пользовательских плейлистов видео/аудио (см. [UserMediaPlaylist]). */
    val USER_MEDIA_PLAYLISTS_JSON = stringPreferencesKey("user_media_playlists_json")
    /** JSON-массив прогресса просмотра/прослушивания медиафайлов. */
    val USER_MEDIA_PLAYBACK_PROGRESS_JSON = stringPreferencesKey("user_media_playback_progress_json")
    val USER_SEMANTIC_LEXICON_JSON = stringPreferencesKey("user_semantic_lexicon_json")
    val LEXICON_PRESET_ENABLED = booleanPreferencesKey("lexicon_preset_enabled")
    /** Пустой набор = все оси пресета включены. */
    val LEXICON_PRESET_TONES = stringSetPreferencesKey("lexicon_preset_tones")
    val LEXICON_USER_ENABLED = booleanPreferencesKey("lexicon_user_enabled")
    /** Пустой набор = все оси «Мои слова» включены. */
    val LEXICON_USER_TONES = stringSetPreferencesKey("lexicon_user_tones")
    /** Медиа к выделенным фрагментам в стихах (не лексикон). */
    val WORD_SPAN_MEDIA_JSON = stringPreferencesKey("word_span_media_json")
    /** Отпечатки файлов из папки «Загрузки/Bible», уже импортированных в библиотеку. */
    val DOWNLOAD_IMPORT_FINGERPRINTS = stringSetPreferencesKey("download_import_fingerprints")
    /** JSON-массив id книг — порядок офлайн-предзагрузки «Изучение». */
    val OFFLINE_DOWNLOAD_BOOK_ORDER_JSON = stringPreferencesKey("offline_download_book_order_json")
    /** Список id разделов каталога медиа через запятую (см. [MediaHomeSectionOrder]). */
    val MEDIA_HOME_SECTION_ORDER = stringPreferencesKey("media_home_section_order")
    /** Пользовательское название микроблога. */
    val MICROBLOG_TITLE = stringPreferencesKey("microblog_title")
    /** Порядок пунктов главного меню экрана книг (см. [BooksMainMenuOrder]), без «Настройки» и переводов. */
    val BOOKS_MAIN_MENU_ORDER = stringPreferencesKey("books_main_menu_order")
    /** Озвучка полного названия книги при долгом нажатии на экране выбора книг. */
    val BOOK_PICKER_LONG_PRESS_TTS = booleanPreferencesKey("book_picker_long_press_tts")
    /** Ключ пресета темы приложения (см. [com.example.bible.ui.theme.BibleAppThemePreset]). */
    val APP_THEME_PRESET = stringPreferencesKey("bible_app_theme_preset")
    /** Управление мимикой: камера в фоне, курсор по носу, прокрутка читалки. */
    val MIMIC_CONTROL_ENABLED = booleanPreferencesKey("mimic_control_enabled")
    /** Режим «мимика 2»: направление по дельте носа в экранных координатах, разгон; открытый рот = удержание без двойного открытия. */
    val MIMIC_CONTROL_V2_ENABLED = booleanPreferencesKey("mimic_control_v2_enabled")
    /** Полупрозрачное видео фронтальной камеры при мимике. */
    val MIMIC_CAMERA_PREVIEW_ENABLED = booleanPreferencesKey("mimic_camera_preview_enabled")
    /** Наложение контуров лица (рот, глаза, нос) поверх кадра. */
    val MIMIC_FACE_OVERLAY_ENABLED = booleanPreferencesKey("mimic_face_overlay_enabled")
    /** Показывать на экране вектор направления движения курсора мимики (отладка/наглядность). */
    val MIMIC_VELOCITY_VECTOR_VISIBLE = booleanPreferencesKey("mimic_velocity_vector_visible")
    /** Старый единый флаг превью; читается для миграции, если новые ключи ещё не заданы. */
    val MIMIC_FACE_PREVIEW_ENABLED = booleanPreferencesKey("mimic_face_preview_enabled")
    /**
     * Геометрия лица (контуры) из MediaPipe Face Landmarker + классификация/углы из ML Kit.
     * По умолчанию выкл. — только ML Kit.
     */
    val MIMIC_MEDIAPIPE_FACE_GEOMETRY = booleanPreferencesKey("mimic_mediapipe_face_geometry")
    /** JSON-массив: история поиска по переводу Корана (запрос + время). */
    val QURAN_SEARCH_HISTORY_JSON = stringPreferencesKey("quran_search_history_json")
    /** Посещения аятов Корана (сура + аят). */
    val QURAN_READING_HISTORY_JSON = stringPreferencesKey("quran_reading_history_json")
    /** Хронология чтения Корана (стих за стихом). */
    val QURAN_READING_TRACE_JSON = stringPreferencesKey("quran_reading_trace_json")
    /** JSON-массив: история поиска по текстам Библии. */
    val BIBLE_SEARCH_HISTORY_JSON = stringPreferencesKey("bible_search_history_json")
    /** Множитель размера шрифта текста аятов Корана (арабский, транслит., перевод, тафсир в карточке). */
    val QURAN_READER_TEXT_SCALE = floatPreferencesKey("quran_reader_text_scale")
    /** Множитель размера арабского текста на экране «Песочница: арабское слово». */
    val QURAN_SANDBOX_ARABIC_TEXT_SCALE = floatPreferencesKey("quran_sandbox_arabic_text_scale")
    /** Множитель размера ивритского текста в песочнице подстрочника (ВЗ). */
    val INTERLINEAR_HEBREW_SANDBOX_TEXT_SCALE = floatPreferencesKey("interlinear_hebrew_sandbox_text_scale")
    /** Озвучка арабского аята по словам (TTS), а не целой строкой. */
    val QURAN_ARABIC_WORD_BY_WORD_TTS = booleanPreferencesKey("quran_arabic_word_by_word_tts")
    /** Пользовательский API-ключ DeepSeek (chat completions). */
    val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
    /** Пауза между циклами повтора воспроизведения на карточке аята (мс). */
    val QURAN_AYAH_REPEAT_PAUSE_MS = longPreferencesKey("quran_ayah_repeat_pause_ms")
    /** Пауза между повторами TTS на карточках интервального повторения языков (мс). */
    val LANG_STUDY_SRS_REPEAT_PAUSE_MS = longPreferencesKey("lang_study_srs_repeat_pause_ms")
    /** JSON: пользовательские разделы «Детям», порядок хаба, свои картинки и звуки. */
    val KIDS_USER_SECTIONS_JSON = stringPreferencesKey("kids_user_sections_json")
    /** Скорость TTS, обычно 0.5…1.5 (1 = норма). */
    val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
    /** Множитель тона 0.6…1.4. */
    val TTS_PITCH = floatPreferencesKey("tts_pitch")
    /** Пакет движка TTS, пусто = системный по умолчанию. */
    val TTS_ENGINE_PACKAGE = stringPreferencesKey("tts_engine_package")
    /** Подбирать максимальное качество голоса для языка, если движок отдаёт несколько. */
    val TTS_PREFER_HQ = booleanPreferencesKey("tts_prefer_hq_voice")
    /** Последнее место чтения Библии или редактора заметки для возобновления после выхода из приложения. */
    val LAST_SESSION_RESUME_JSON = stringPreferencesKey("last_session_resume_json")
}

/** Запись истории поиска по русскому переводу Корана (локально в DataStore). */
data class QuranSearchHistoryEntry(
    val query: String,
    val timestamp: Long,
    /** Ключ для объединения повторов (нормализованный запрос). */
    val dedupKey: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("q", query)
        put("ts", timestamp)
        put("k", dedupKey)
    }

    companion object {
        private const val MAX_ENTRIES = 80

        fun fromJson(j: JSONObject): QuranSearchHistoryEntry {
            val q = j.getString("q")
            val k = j.optString("k").ifBlank {
                q.lowercase()
                    .replace('ё', 'е')
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            return QuranSearchHistoryEntry(
                query = q,
                timestamp = j.getLong("ts"),
                dedupKey = k,
            )
        }

        fun parseList(json: String): List<QuranSearchHistoryEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<QuranSearchHistoryEntry>): String {
            val arr = JSONArray()
            list.takeLast(MAX_ENTRIES).forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/** Запись истории поиска по Библии (локально в DataStore). */
data class BibleSearchHistoryEntry(
    val query: String,
    val timestamp: Long,
    val dedupKey: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("q", query)
        put("ts", timestamp)
        put("k", dedupKey)
    }

    companion object {
        private const val MAX_ENTRIES = 80

        fun fromJson(j: JSONObject): BibleSearchHistoryEntry {
            val q = j.getString("q")
            val k = j.optString("k").ifBlank {
                q.lowercase()
                    .replace('ё', 'е')
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            return BibleSearchHistoryEntry(
                query = q,
                timestamp = j.getLong("ts"),
                dedupKey = k,
            )
        }

        fun parseList(json: String): List<BibleSearchHistoryEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<BibleSearchHistoryEntry>): String {
            val arr = JSONArray()
            list.takeLast(MAX_ENTRIES).forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

data class HistoryEntry(
    val translation: String,
    val bookId: String,
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val timestamp: Long,
    /** Накопленное время «на этом стихе» (сек), по данным читалки. */
    val dwellSeconds: Int = 0,
    /** Метки активности: словари, изучение и т.д., разделитель | */
    val toolsUsed: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", translation)
        put("b", bookId)
        put("n", bookName)
        put("c", chapter)
        put("v", verse)
        put("ts", timestamp)
        if (dwellSeconds > 0) put("ds", dwellSeconds)
        if (toolsUsed.isNotBlank()) put("tu", toolsUsed)
    }

    companion object {
        /** Всё хранится локально в DataStore на устройстве; лимит защищает от неограниченного роста JSON. */
        private const val MAX_HISTORY = 50_000

        fun fromJson(j: JSONObject): HistoryEntry = HistoryEntry(
            translation = j.getString("t"),
            bookId = j.getString("b"),
            bookName = j.optString("n", ""),
            chapter = j.getInt("c"),
            verse = j.getInt("v"),
            timestamp = j.getLong("ts"),
            dwellSeconds = j.optInt("ds", 0),
            toolsUsed = j.optString("tu", ""),
        )

        fun parseList(json: String): List<HistoryEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<HistoryEntry>): String {
            val arr = JSONArray()
            list.takeLast(MAX_HISTORY).forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/** Одна строка журнала «стих за стихом» (для экрана истории). */
data class ReadingTraceEntry(
    val timestamp: Long,
    val translation: String,
    val bookId: String,
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val dwellSeconds: Int,
    val tools: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestamp)
        put("t", translation)
        put("b", bookId)
        put("n", bookName)
        put("c", chapter)
        put("v", verse)
        put("d", dwellSeconds)
        if (tools.isNotBlank()) put("tu", tools)
    }

    companion object {
        /** Локальный журнал посещений стихов; при переполнении отбрасываются самые старые записи. */
        private const val MAX_TRACE = 100_000

        fun fromJson(j: JSONObject): ReadingTraceEntry = ReadingTraceEntry(
            timestamp = j.getLong("ts"),
            translation = j.getString("t"),
            bookId = j.getString("b"),
            bookName = j.optString("n", ""),
            chapter = j.getInt("c"),
            verse = j.getInt("v"),
            dwellSeconds = j.optInt("d", 0),
            tools = j.optString("tu", ""),
        )

        fun parseList(json: String): List<ReadingTraceEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<ReadingTraceEntry>): String {
            val arr = JSONArray()
            list.takeLast(MAX_TRACE).forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

private fun mergeHistoryToolLabels(existing: String, newLabel: String): String {
    val set = LinkedHashSet<String>()
    if (existing.isNotBlank()) {
        existing.split('|').map { it.trim() }.filter { it.isNotEmpty() }.forEach { set.add(it) }
    }
    set.add(newLabel.trim())
    return set.joinToString("|")
}

class BiblePreferences(
    context: Context,
) {
    private val appContext = context.applicationContext

    val selectedTranslation: Flow<TranslationId> = appContext.bibleDataStore.data.map { prefs ->
        TranslationId.fromCode(prefs[Keys.TRANSLATION] ?: TranslationId.SYNODAL.code)
    }

    val bookmarkKeys: Flow<Set<String>> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.BOOKMARKS] ?: emptySet()
    }

    /** Порядок книг для массовой офлайн-загрузки (66 id после нормализации). */
    val offlineDownloadBookOrder: Flow<List<String>> = appContext.bibleDataStore.data.map { prefs ->
        val parsed = OfflineDownloadBookOrder.parseJson(prefs[Keys.OFFLINE_DOWNLOAD_BOOK_ORDER_JSON])
        OfflineDownloadBookOrder.normalize(parsed)
    }

    suspend fun setOfflineDownloadBookOrder(ids: List<String>) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.OFFLINE_DOWNLOAD_BOOK_ORDER_JSON] = JSONArray(ids).toString()
        }
    }

    /** Порядок карточек на экране «Каталог медиа» (сверху вниз). */
    val mediaHomeSectionOrder: Flow<List<String>> = appContext.bibleDataStore.data.map { prefs ->
        MediaHomeSectionOrder.parseStored(prefs[Keys.MEDIA_HOME_SECTION_ORDER])
    }

    suspend fun setMediaHomeSectionOrder(ids: List<String>) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.MEDIA_HOME_SECTION_ORDER] = MediaHomeSectionOrder.toStored(ids)
        }
    }

    val microblogTitle: Flow<String> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.MICROBLOG_TITLE]?.trim().orEmpty().ifEmpty { "Микроблог" }
    }

    suspend fun setMicroblogTitle(title: String) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.MICROBLOG_TITLE] = title.trim()
        }
    }

    /** Порядок пунктов меню на экране выбора книги (кроме фиксированных «Настройки» и списка переводов). */
    val booksMainMenuOrder: Flow<List<String>> = appContext.bibleDataStore.data.map { prefs ->
        BooksMainMenuOrder.parseStored(prefs[Keys.BOOKS_MAIN_MENU_ORDER])
    }

    suspend fun setBooksMainMenuOrder(ids: List<String>) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.BOOKS_MAIN_MENU_ORDER] = BooksMainMenuOrder.toStored(ids)
        }
    }

    val bookPickerLongPressTts: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.BOOK_PICKER_LONG_PRESS_TTS] ?: true
    }

    suspend fun setBookPickerLongPressTts(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.BOOK_PICKER_LONG_PRESS_TTS] = enabled }
    }

    /** Пользовательские разделы и правки экрана «Детям». */
    val kidsUserSections: Flow<KidsUserSectionsState> = appContext.bibleDataStore.data
        .map { prefs -> KidsUserSectionsState.fromJson(prefs[Keys.KIDS_USER_SECTIONS_JSON]) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)

    suspend fun setKidsUserSections(state: KidsUserSectionsState) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.KIDS_USER_SECTIONS_JSON] = state.toJsonString()
        }
    }

    /** Значение [com.example.bible.ui.theme.BibleAppThemePreset.storageKey]; по умолчанию standard. */
    val appThemePresetKey: Flow<String> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.APP_THEME_PRESET] ?: "standard"
    }

    suspend fun setAppThemePresetKey(storageKey: String) {
        appContext.bibleDataStore.edit { it[Keys.APP_THEME_PRESET] = storageKey }
    }

    val mimicControlEnabled: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.MIMIC_CONTROL_ENABLED] ?: false
    }

    suspend fun setMimicControlEnabled(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.MIMIC_CONTROL_ENABLED] = enabled }
    }

    val mimicControlV2Enabled: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.MIMIC_CONTROL_V2_ENABLED] ?: false
    }

    suspend fun setMimicControlV2Enabled(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.MIMIC_CONTROL_V2_ENABLED] = enabled }
    }

    val mimicCameraPreviewEnabled: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.MIMIC_CAMERA_PREVIEW_ENABLED]
            ?: (prefs[Keys.MIMIC_FACE_PREVIEW_ENABLED] ?: false)
    }

    val mimicFaceOverlayEnabled: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.MIMIC_FACE_OVERLAY_ENABLED]
            ?: (prefs[Keys.MIMIC_FACE_PREVIEW_ENABLED] ?: false)
    }

    suspend fun setMimicCameraPreviewEnabled(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.MIMIC_CAMERA_PREVIEW_ENABLED] = enabled }
    }

    suspend fun setMimicFaceOverlayEnabled(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.MIMIC_FACE_OVERLAY_ENABLED] = enabled }
    }

    val mimicVelocityVectorVisible: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.MIMIC_VELOCITY_VECTOR_VISIBLE] ?: false
    }

    suspend fun setMimicVelocityVectorVisible(visible: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.MIMIC_VELOCITY_VECTOR_VISIBLE] = visible }
    }

    val mimicMediaPipeFaceGeometryEnabled: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.MIMIC_MEDIAPIPE_FACE_GEOMETRY] ?: false
    }

    suspend fun setMimicMediaPipeFaceGeometryEnabled(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.MIMIC_MEDIAPIPE_FACE_GEOMETRY] = enabled }
    }

    suspend fun clearMimicVisualSettings() {
        appContext.bibleDataStore.edit { prefs ->
            prefs.remove(Keys.MIMIC_CAMERA_PREVIEW_ENABLED)
            prefs.remove(Keys.MIMIC_FACE_OVERLAY_ENABLED)
            prefs.remove(Keys.MIMIC_VELOCITY_VECTOR_VISIBLE)
            prefs.remove(Keys.MIMIC_FACE_PREVIEW_ENABLED)
        }
    }

    val ttsSpeechRate: Flow<Float> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f
    }

    val ttsPitch: Flow<Float> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.TTS_PITCH] ?: 1.0f
    }

    val ttsEnginePackage: Flow<String> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.TTS_ENGINE_PACKAGE] ?: ""
    }

    val ttsPreferHighQuality: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.TTS_PREFER_HQ] ?: true
    }

    suspend fun setTtsSpeechRate(v: Float) {
        val x = v.coerceIn(0.35f, 2.2f)
        appContext.bibleDataStore.edit { it[Keys.TTS_SPEECH_RATE] = x }
    }

    suspend fun setTtsPitch(v: Float) {
        val x = v.coerceIn(0.5f, 1.4f)
        appContext.bibleDataStore.edit { it[Keys.TTS_PITCH] = x }
    }

    suspend fun setTtsEnginePackage(packageName: String) {
        val p = packageName.trim()
        appContext.bibleDataStore.edit { it[Keys.TTS_ENGINE_PACKAGE] = p }
    }

    suspend fun setTtsPreferHighQuality(prefer: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.TTS_PREFER_HQ] = prefer }
    }

    val textHighlights: Flow<List<TextHighlight>> = appContext.bibleDataStore.data.map { prefs ->
        TextHighlight.parseList(prefs[Keys.HIGHLIGHTS_JSON].orEmpty())
    }

    val userSemanticLexiconRules: Flow<List<SemanticLexiconRule>> = appContext.bibleDataStore.data.map { prefs ->
        SemanticLexiconRule.parseList(prefs[Keys.USER_SEMANTIC_LEXICON_JSON].orEmpty())
    }

    /** Встроенная пресет-база слов в читалке. */
    val lexiconPresetEnabled: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.LEXICON_PRESET_ENABLED] ?: true
    }

    /** Пустой набор — все [LexiconTone] из пресета. */
    val lexiconPresetToneIds: Flow<Set<String>> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.LEXICON_PRESET_TONES] ?: emptySet()
    }

    suspend fun setUserSemanticLexiconRules(list: List<SemanticLexiconRule>) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.USER_SEMANTIC_LEXICON_JSON] = SemanticLexiconRule.toJsonArray(list)
        }
    }

    suspend fun saveUserSemanticLexiconRule(rule: SemanticLexiconRule) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = SemanticLexiconRule.parseList(prefs[Keys.USER_SEMANTIC_LEXICON_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.id == rule.id }
            cur.add(rule)
            prefs[Keys.USER_SEMANTIC_LEXICON_JSON] = SemanticLexiconRule.toJsonArray(cur)
        }
    }

    suspend fun deleteUserSemanticLexiconRule(id: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = SemanticLexiconRule.parseList(prefs[Keys.USER_SEMANTIC_LEXICON_JSON].orEmpty())
                .filterNot { it.id == id }
            prefs[Keys.USER_SEMANTIC_LEXICON_JSON] = SemanticLexiconRule.toJsonArray(cur)
        }
    }

    suspend fun setLexiconPresetEnabled(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.LEXICON_PRESET_ENABLED] = enabled }
    }

    suspend fun setLexiconPresetToneIds(ids: Set<String>) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.LEXICON_PRESET_TONES] = ids
        }
    }

    val lexiconUserEnabled: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.LEXICON_USER_ENABLED] ?: true
    }

    val lexiconUserToneIds: Flow<Set<String>> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.LEXICON_USER_TONES] ?: emptySet()
    }

    suspend fun setLexiconUserEnabled(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.LEXICON_USER_ENABLED] = enabled }
    }

    suspend fun setLexiconUserToneIds(ids: Set<String>) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.LEXICON_USER_TONES] = ids
        }
    }

    val wordSpanMediaAttachments: Flow<List<WordSpanMediaAttachment>> = appContext.bibleDataStore.data.map { prefs ->
        WordSpanMediaAttachment.parseList(prefs[Keys.WORD_SPAN_MEDIA_JSON].orEmpty())
    }

    suspend fun upsertWordSpanMediaAttachment(a: WordSpanMediaAttachment) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = WordSpanMediaAttachment.parseList(prefs[Keys.WORD_SPAN_MEDIA_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.id == a.id }
            cur.removeAll { existing ->
                existing.matchesVerse(
                    VerseRef(a.translation, a.bookId, a.chapter, a.verse),
                ) && existing.overlapsOffsets(a.startOffset, a.endOffset)
            }
            cur.add(a)
            prefs[Keys.WORD_SPAN_MEDIA_JSON] = WordSpanMediaAttachment.toJsonArray(cur)
        }
    }

    suspend fun deleteWordSpanMediaAttachment(id: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = WordSpanMediaAttachment.parseList(prefs[Keys.WORD_SPAN_MEDIA_JSON].orEmpty())
                .filterNot { it.id == id }
            prefs[Keys.WORD_SPAN_MEDIA_JSON] = WordSpanMediaAttachment.toJsonArray(cur)
        }
    }

    suspend fun removeWordSpanMediaIntersecting(ref: VerseRef, selStart: Int, selEnd: Int) {
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)
        if (start >= end) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = WordSpanMediaAttachment.parseList(prefs[Keys.WORD_SPAN_MEDIA_JSON].orEmpty())
                .filterNot { att ->
                    att.matchesVerse(ref) && att.overlapsOffsets(start, end)
                }
            prefs[Keys.WORD_SPAN_MEDIA_JSON] = WordSpanMediaAttachment.toJsonArray(cur)
        }
    }

    /** Множитель к системному масштабу шрифта при чтении (1.0 = по умолчанию). */
    val readerFontScale: Flow<Float> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.READER_FONT_SCALE] ?: 1f
    }

    /** null = следовать системной теме, true = тёмная, false = светлая. */
    val darkMode: Flow<Boolean?> = appContext.bibleDataStore.data.map { prefs ->
        if (prefs.contains(Keys.DARK_MODE)) prefs[Keys.DARK_MODE] else null
    }

    suspend fun setDarkMode(dark: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.DARK_MODE] = dark }
    }

    val deepSeekApiKey: Flow<String> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.DEEPSEEK_API_KEY]?.trim().orEmpty()
    }

    suspend fun setDeepSeekApiKey(key: String) {
        val trimmed = key.trim()
        appContext.bibleDataStore.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(Keys.DEEPSEEK_API_KEY)
            else prefs[Keys.DEEPSEEK_API_KEY] = trimmed
        }
    }

    suspend fun setTranslation(id: TranslationId) {
        appContext.bibleDataStore.edit { it[Keys.TRANSLATION] = id.code }
    }

    suspend fun setReaderFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.16f, 8.4f)
        appContext.bibleDataStore.edit { it[Keys.READER_FONT_SCALE] = clamped }
    }

    val songFontSize: Flow<Float> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.SONG_FONT_SIZE] ?: 18f
    }

    suspend fun setSongFontSize(size: Float) {
        val clamped = size.coerceIn(3f, 150f)
        appContext.bibleDataStore.edit { it[Keys.SONG_FONT_SIZE] = clamped }
    }

    val songHighlightLineWhilePlaying: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.SONG_HIGHLIGHT_LINE_WHILE_PLAYING] ?: true
    }

    suspend fun setSongHighlightLineWhilePlaying(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.SONG_HIGHLIGHT_LINE_WHILE_PLAYING] = enabled }
    }

    val videoLibraryTitleScale: Flow<Float> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.VIDEO_LIBRARY_TITLE_SCALE] ?: 1f
    }

    suspend fun setVideoLibraryTitleScale(scale: Float) {
        val clamped = scale.coerceIn(0.75f, 1.4f)
        appContext.bibleDataStore.edit { it[Keys.VIDEO_LIBRARY_TITLE_SCALE] = clamped }
    }

    suspend fun toggleBookmark(key: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = prefs[Keys.BOOKMARKS] ?: emptySet()
            prefs[Keys.BOOKMARKS] = if (key in cur) cur - key else cur + key
        }
    }

    suspend fun setTextHighlights(list: List<TextHighlight>) {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.HIGHLIGHTS_JSON] = TextHighlight.toJsonArray(list)
        }
    }

    suspend fun addTextHighlight(h: TextHighlight) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = TextHighlight.parseList(prefs[Keys.HIGHLIGHTS_JSON].orEmpty()).toMutableList()
            cur.removeAll { existing ->
                existing.matchesVerse(
                    VerseRef(h.translation, h.bookId, h.chapter, h.verse),
                ) && existing.overlapsOffsets(h.startOffset, h.endOffset)
            }
            cur.add(h)
            prefs[Keys.HIGHLIGHTS_JSON] = TextHighlight.toJsonArray(cur)
        }
    }

    /** Пакетное добавление (например тематическая подсветка главы/книги). */
    suspend fun addTextHighlightsBatch(add: List<TextHighlight>) {
        if (add.isEmpty()) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = TextHighlight.parseList(prefs[Keys.HIGHLIGHTS_JSON].orEmpty()).toMutableList()
            for (h in add) {
                cur.removeAll { existing ->
                    existing.matchesVerse(
                        VerseRef(h.translation, h.bookId, h.chapter, h.verse),
                    ) && existing.overlapsOffsets(h.startOffset, h.endOffset)
                }
                cur.add(h)
            }
            prefs[Keys.HIGHLIGHTS_JSON] = TextHighlight.toJsonArray(cur)
        }
    }

    suspend fun removeTextHighlightsIntersecting(ref: VerseRef, selStart: Int, selEnd: Int) {
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)
        if (start >= end) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = TextHighlight.parseList(prefs[Keys.HIGHLIGHTS_JSON].orEmpty())
            val filtered = cur.filterNot { hl ->
                hl.matchesVerse(ref) && hl.overlapsOffsets(start, end)
            }
            prefs[Keys.HIGHLIGHTS_JSON] = TextHighlight.toJsonArray(filtered)
        }
    }

    val readingHistory: Flow<List<HistoryEntry>> = appContext.bibleDataStore.data.map { prefs ->
        HistoryEntry.parseList(prefs[Keys.HISTORY_JSON].orEmpty())
    }

    val readingTrace: Flow<List<ReadingTraceEntry>> = appContext.bibleDataStore.data.map { prefs ->
        ReadingTraceEntry.parseList(prefs[Keys.READING_TRACE_JSON].orEmpty())
    }

    /** История поиска по переводу Корана; порядок — по времени добавления (старые первыми). */
    val quranSearchHistory: Flow<List<QuranSearchHistoryEntry>> = appContext.bibleDataStore.data.map { prefs ->
        QuranSearchHistoryEntry.parseList(prefs[Keys.QURAN_SEARCH_HISTORY_JSON].orEmpty())
    }

    /** История поиска по Библии; порядок — по времени добавления (старые первыми). */
    val bibleSearchHistory: Flow<List<BibleSearchHistoryEntry>> = appContext.bibleDataStore.data.map { prefs ->
        BibleSearchHistoryEntry.parseList(prefs[Keys.BIBLE_SEARCH_HISTORY_JSON].orEmpty())
    }

    suspend fun appendQuranSearchHistory(displayQuery: String, dedupKey: String) {
        val q = displayQuery.trim()
        if (dedupKey.isBlank() || q.isEmpty()) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = QuranSearchHistoryEntry.parseList(prefs[Keys.QURAN_SEARCH_HISTORY_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.dedupKey == dedupKey }
            cur.add(
                QuranSearchHistoryEntry(
                    query = q,
                    timestamp = System.currentTimeMillis(),
                    dedupKey = dedupKey,
                ),
            )
            prefs[Keys.QURAN_SEARCH_HISTORY_JSON] = QuranSearchHistoryEntry.toJsonArray(cur)
        }
    }

    suspend fun removeQuranSearchHistoryEntry(entry: QuranSearchHistoryEntry) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = QuranSearchHistoryEntry.parseList(prefs[Keys.QURAN_SEARCH_HISTORY_JSON].orEmpty())
                .filterNot { it.timestamp == entry.timestamp && it.dedupKey == entry.dedupKey }
            prefs[Keys.QURAN_SEARCH_HISTORY_JSON] = QuranSearchHistoryEntry.toJsonArray(cur)
        }
    }

    suspend fun clearQuranSearchHistory() {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.QURAN_SEARCH_HISTORY_JSON] = "[]"
        }
    }

    val quranReadingHistory: Flow<List<QuranReadingHistoryEntry>> = appContext.bibleDataStore.data.map { prefs ->
        QuranReadingHistoryEntry.parseList(prefs[Keys.QURAN_READING_HISTORY_JSON].orEmpty())
    }

    val quranReadingTrace: Flow<List<QuranReadingTraceEntry>> = appContext.bibleDataStore.data.map { prefs ->
        QuranReadingTraceEntry.parseList(prefs[Keys.QURAN_READING_TRACE_JSON].orEmpty())
    }

    suspend fun addQuranHistoryEntry(entry: QuranReadingHistoryEntry) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = QuranReadingHistoryEntry.parseList(prefs[Keys.QURAN_READING_HISTORY_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.surahNumber == entry.surahNumber && it.ayahNumber == entry.ayahNumber }
            cur.add(0, entry.copy(timestamp = System.currentTimeMillis()))
            prefs[Keys.QURAN_READING_HISTORY_JSON] = QuranReadingHistoryEntry.toJsonArray(cur)
        }
    }

    suspend fun mergeQuranReadingAnalytics(
        surahNumber: Int,
        ayahNumber: Int,
        dwellDeltaSeconds: Int,
    ) {
        if (dwellDeltaSeconds <= 0) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = QuranReadingHistoryEntry.parseList(prefs[Keys.QURAN_READING_HISTORY_JSON].orEmpty()).toMutableList()
            val idx = cur.indexOfFirst { it.surahNumber == surahNumber && it.ayahNumber == ayahNumber }
            if (idx < 0) return@edit
            val e = cur[idx]
            cur[idx] = e.copy(dwellSeconds = e.dwellSeconds + dwellDeltaSeconds.coerceAtLeast(0))
            prefs[Keys.QURAN_READING_HISTORY_JSON] = QuranReadingHistoryEntry.toJsonArray(cur)
        }
    }

    suspend fun appendQuranReadingTrace(entry: QuranReadingTraceEntry) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = QuranReadingTraceEntry.parseList(prefs[Keys.QURAN_READING_TRACE_JSON].orEmpty()).toMutableList()
            cur.add(entry)
            prefs[Keys.QURAN_READING_TRACE_JSON] = QuranReadingTraceEntry.toJsonArray(cur)
        }
    }

    suspend fun clearQuranReadingHistory() {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.QURAN_READING_HISTORY_JSON] = "[]"
            prefs[Keys.QURAN_READING_TRACE_JSON] = "[]"
        }
    }

    suspend fun appendBibleSearchHistory(displayQuery: String, dedupKey: String) {
        val q = displayQuery.trim()
        if (dedupKey.isBlank() || q.isEmpty()) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = BibleSearchHistoryEntry.parseList(prefs[Keys.BIBLE_SEARCH_HISTORY_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.dedupKey == dedupKey }
            cur.add(
                BibleSearchHistoryEntry(
                    query = q,
                    timestamp = System.currentTimeMillis(),
                    dedupKey = dedupKey,
                ),
            )
            prefs[Keys.BIBLE_SEARCH_HISTORY_JSON] = BibleSearchHistoryEntry.toJsonArray(cur)
        }
    }

    suspend fun removeBibleSearchHistoryEntry(entry: BibleSearchHistoryEntry) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = BibleSearchHistoryEntry.parseList(prefs[Keys.BIBLE_SEARCH_HISTORY_JSON].orEmpty())
                .filterNot { it.timestamp == entry.timestamp && it.dedupKey == entry.dedupKey }
            prefs[Keys.BIBLE_SEARCH_HISTORY_JSON] = BibleSearchHistoryEntry.toJsonArray(cur)
        }
    }

    suspend fun clearBibleSearchHistory() {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.BIBLE_SEARCH_HISTORY_JSON] = "[]"
        }
    }

    /** Масштаб шрифта при чтении сур Корана. */
    val quranReaderTextScale: Flow<Float> = appContext.bibleDataStore.data.map { prefs ->
        (prefs[Keys.QURAN_READER_TEXT_SCALE] ?: QURAN_READER_TEXT_SCALE_DEFAULT).coerceIn(
            QURAN_READER_TEXT_SCALE_MIN,
            QURAN_READER_TEXT_SCALE_MAX,
        )
    }

    suspend fun setQuranReaderTextScale(scale: Float) {
        val v = scale.coerceIn(QURAN_READER_TEXT_SCALE_MIN, QURAN_READER_TEXT_SCALE_MAX)
        appContext.bibleDataStore.edit { it[Keys.QURAN_READER_TEXT_SCALE] = v }
    }

    /** Масштаб арабского текста в песочнице слова (отдельно от читалки суры). */
    val quranSandboxArabicTextScale: Flow<Float> = appContext.bibleDataStore.data.map { prefs ->
        (prefs[Keys.QURAN_SANDBOX_ARABIC_TEXT_SCALE] ?: QURAN_READER_TEXT_SCALE_DEFAULT).coerceIn(
            QURAN_READER_TEXT_SCALE_MIN,
            QURAN_READER_TEXT_SCALE_MAX,
        )
    }

    suspend fun setQuranSandboxArabicTextScale(scale: Float) {
        val v = scale.coerceIn(QURAN_READER_TEXT_SCALE_MIN, QURAN_READER_TEXT_SCALE_MAX)
        appContext.bibleDataStore.edit { it[Keys.QURAN_SANDBOX_ARABIC_TEXT_SCALE] = v }
    }

    val interlinearHebrewSandboxTextScale: Flow<Float> = appContext.bibleDataStore.data.map { prefs ->
        (prefs[Keys.INTERLINEAR_HEBREW_SANDBOX_TEXT_SCALE] ?: QURAN_READER_TEXT_SCALE_DEFAULT).coerceIn(
            QURAN_READER_TEXT_SCALE_MIN,
            QURAN_READER_TEXT_SCALE_MAX,
        )
    }

    suspend fun setInterlinearHebrewSandboxTextScale(scale: Float) {
        val v = scale.coerceIn(QURAN_READER_TEXT_SCALE_MIN, QURAN_READER_TEXT_SCALE_MAX)
        appContext.bibleDataStore.edit { it[Keys.INTERLINEAR_HEBREW_SANDBOX_TEXT_SCALE] = v }
    }

    val quranArabicWordByWordTts: Flow<Boolean> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.QURAN_ARABIC_WORD_BY_WORD_TTS] ?: false
    }

    suspend fun setQuranArabicWordByWordTts(enabled: Boolean) {
        appContext.bibleDataStore.edit { it[Keys.QURAN_ARABIC_WORD_BY_WORD_TTS] = enabled }
    }

    /** Пауза между повторами при включённом режиме «Повтор» на карточке аята. */
    val quranAyahRepeatPauseMs: Flow<Long> = appContext.bibleDataStore.data.map { prefs ->
        (prefs[Keys.QURAN_AYAH_REPEAT_PAUSE_MS] ?: QURAN_AYAH_REPEAT_PAUSE_MS_DEFAULT).coerceIn(
            QURAN_AYAH_REPEAT_PAUSE_MS_MIN,
            QURAN_AYAH_REPEAT_PAUSE_MS_MAX,
        )
    }

    suspend fun setQuranAyahRepeatPauseMs(ms: Long) {
        val v = ms.coerceIn(QURAN_AYAH_REPEAT_PAUSE_MS_MIN, QURAN_AYAH_REPEAT_PAUSE_MS_MAX)
        appContext.bibleDataStore.edit { it[Keys.QURAN_AYAH_REPEAT_PAUSE_MS] = v }
    }

    /** Пауза между повторами озвучки в сессии интервальных карточек языка. */
    val langStudySrsRepeatPauseMs: Flow<Long> = appContext.bibleDataStore.data.map { prefs ->
        (prefs[Keys.LANG_STUDY_SRS_REPEAT_PAUSE_MS] ?: LANG_STUDY_SRS_REPEAT_PAUSE_MS_DEFAULT).coerceIn(
            LANG_STUDY_SRS_REPEAT_PAUSE_MS_MIN,
            LANG_STUDY_SRS_REPEAT_PAUSE_MS_MAX,
        )
    }

    suspend fun setLangStudySrsRepeatPauseMs(ms: Long) {
        val v = ms.coerceIn(LANG_STUDY_SRS_REPEAT_PAUSE_MS_MIN, LANG_STUDY_SRS_REPEAT_PAUSE_MS_MAX)
        appContext.bibleDataStore.edit { it[Keys.LANG_STUDY_SRS_REPEAT_PAUSE_MS] = v }
    }

    suspend fun appendReadingTrace(entry: ReadingTraceEntry) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = ReadingTraceEntry.parseList(prefs[Keys.READING_TRACE_JSON].orEmpty()).toMutableList()
            cur.add(entry)
            while (cur.size > READING_TRACE_MAX_ENTRIES) {
                cur.removeAt(0)
            }
            prefs[Keys.READING_TRACE_JSON] = ReadingTraceEntry.toJsonArray(cur)
        }
    }

    suspend fun addHistoryEntry(entry: HistoryEntry) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = HistoryEntry.parseList(prefs[Keys.HISTORY_JSON].orEmpty()).toMutableList()
            val existing = cur.firstOrNull {
                it.translation == entry.translation && it.bookId == entry.bookId &&
                    it.chapter == entry.chapter && it.verse == entry.verse
            }
            cur.removeAll {
                it.translation == entry.translation && it.bookId == entry.bookId &&
                    it.chapter == entry.chapter && it.verse == entry.verse
            }
            cur.add(
                entry.copy(
                    dwellSeconds = existing?.dwellSeconds ?: entry.dwellSeconds,
                    toolsUsed = existing?.toolsUsed ?: entry.toolsUsed,
                ),
            )
            prefs[Keys.HISTORY_JSON] = HistoryEntry.toJsonArray(cur)
        }
    }

    /** Добавить время и метки к уже существующей записи истории (тот же стих и перевод). */
    suspend fun mergeReadingAnalytics(
        translation: String,
        bookId: String,
        chapter: Int,
        verse: Int,
        dwellDeltaSeconds: Int,
        tools: List<String>,
    ) {
        if (dwellDeltaSeconds <= 0 && tools.isEmpty()) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = HistoryEntry.parseList(prefs[Keys.HISTORY_JSON].orEmpty()).toMutableList()
            val idx = cur.indexOfFirst {
                it.translation == translation && it.bookId == bookId &&
                    it.chapter == chapter && it.verse == verse
            }
            if (idx < 0) return@edit
            val e = cur[idx]
            var tu = e.toolsUsed
            for (t in tools) {
                val x = t.trim()
                if (x.isNotEmpty()) tu = mergeHistoryToolLabels(tu, x)
            }
            cur[idx] = e.copy(
                dwellSeconds = e.dwellSeconds + dwellDeltaSeconds.coerceAtLeast(0),
                toolsUsed = tu,
            )
            prefs[Keys.HISTORY_JSON] = HistoryEntry.toJsonArray(cur)
        }
    }

    suspend fun clearHistory() {
        appContext.bibleDataStore.edit { prefs ->
            prefs[Keys.HISTORY_JSON] = "[]"
            prefs[Keys.READING_TRACE_JSON] = "[]"
        }
    }

    val userNotes: Flow<List<UserNote>> = appContext.bibleDataStore.data.map { prefs ->
        UserNote.parseList(prefs[Keys.NOTES_JSON].orEmpty())
    }

    /** Пользовательские подписи типов записей для чипов в редакторе заметок. */
    val noteCustomKinds: Flow<List<String>> = appContext.bibleDataStore.data.map { prefs ->
        parseJsonStringArray(prefs[Keys.NOTES_CUSTOM_KINDS_JSON]).distinct()
    }

    suspend fun addNoteCustomKind(label: String) {
        val trimmed = label.trim().take(48)
        if (trimmed.isBlank()) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = parseJsonStringArray(prefs[Keys.NOTES_CUSTOM_KINDS_JSON]).toMutableList()
            if (!cur.any { it.equals(trimmed, ignoreCase = true) }) {
                cur.add(trimmed)
                prefs[Keys.NOTES_CUSTOM_KINDS_JSON] = JSONArray(cur).toString()
            }
        }
    }

    private fun parseJsonStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveNote(note: UserNote) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = UserNote.parseList(prefs[Keys.NOTES_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.id == note.id }
            cur.add(note.copy(updatedAt = System.currentTimeMillis()))
            prefs[Keys.NOTES_JSON] = UserNote.toJsonArray(cur)
        }
    }

    suspend fun deleteNote(noteId: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = UserNote.parseList(prefs[Keys.NOTES_JSON].orEmpty())
            prefs[Keys.NOTES_JSON] = UserNote.toJsonArray(cur.filter { it.id != noteId })
        }
    }

    val userSongs: Flow<List<SongItem>> = appContext.bibleDataStore.data.map { prefs ->
        SongItem.parseList(prefs[Keys.SONGS_JSON].orEmpty())
    }

    suspend fun saveSong(song: SongItem) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = SongItem.parseList(prefs[Keys.SONGS_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.id == song.id }
            cur.add(0, song)
            prefs[Keys.SONGS_JSON] = SongItem.toJsonArray(cur)
        }
    }

    suspend fun deleteSong(songId: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = SongItem.parseList(prefs[Keys.SONGS_JSON].orEmpty())
            prefs[Keys.SONGS_JSON] = SongItem.toJsonArray(cur.filter { it.id != songId })
        }
    }

    val songTags: Flow<Set<String>> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.SONG_TAGS] ?: emptySet()
    }

    suspend fun addSongTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = prefs[Keys.SONG_TAGS] ?: emptySet()
            prefs[Keys.SONG_TAGS] = cur + trimmed
        }
    }

    suspend fun removeSongTag(tag: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = prefs[Keys.SONG_TAGS] ?: emptySet()
            prefs[Keys.SONG_TAGS] = cur - tag
        }
    }

    val userBibleImages: Flow<List<BibleUserImage>> = appContext.bibleDataStore.data.map { prefs ->
        BibleUserImage.parseList(prefs[Keys.USER_BIBLE_IMAGES_JSON].orEmpty())
    }

    suspend fun saveBibleImage(image: BibleUserImage) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = BibleUserImage.parseList(prefs[Keys.USER_BIBLE_IMAGES_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.id == image.id }
            cur.add(0, image)
            prefs[Keys.USER_BIBLE_IMAGES_JSON] = BibleUserImage.toJsonArray(cur)
        }
    }

    suspend fun deleteBibleImage(id: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = BibleUserImage.parseList(prefs[Keys.USER_BIBLE_IMAGES_JSON].orEmpty())
            prefs[Keys.USER_BIBLE_IMAGES_JSON] = BibleUserImage.toJsonArray(cur.filter { it.id != id })
        }
    }

    val userBibleVideos: Flow<List<BibleUserVideo>> = appContext.bibleDataStore.data.map { prefs ->
        BibleUserVideo.parseList(prefs[Keys.USER_BIBLE_VIDEOS_JSON].orEmpty())
    }

    suspend fun saveBibleVideo(video: BibleUserVideo) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = BibleUserVideo.parseList(prefs[Keys.USER_BIBLE_VIDEOS_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.id == video.id }
            cur.add(0, video)
            prefs[Keys.USER_BIBLE_VIDEOS_JSON] = BibleUserVideo.toJsonArray(cur)
        }
    }

    suspend fun deleteBibleVideo(id: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = BibleUserVideo.parseList(prefs[Keys.USER_BIBLE_VIDEOS_JSON].orEmpty())
            prefs[Keys.USER_BIBLE_VIDEOS_JSON] = BibleUserVideo.toJsonArray(cur.filter { it.id != id })
            stripMediaFromUserPlaylists(
                prefs,
                mediaId = id,
                kind = UserMediaPlaylistKind.VIDEO,
            )
            stripMediaPlaybackProgress(prefs, id)
        }
    }

    private fun stripMediaPlaybackProgress(prefs: MutablePreferences, mediaId: String) {
        val map = UserMediaPlaybackProgress.parseMap(
            prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON].orEmpty(),
        ).toMutableMap()
        if (map.remove(mediaId) != null) {
            prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON] = UserMediaPlaybackProgress.toJsonArray(map)
        }
    }

    private fun stripMediaFromUserPlaylists(
        prefs: MutablePreferences,
        mediaId: String,
        kind: UserMediaPlaylistKind,
    ) {
        val playlists = UserMediaPlaylist.parseList(prefs[Keys.USER_MEDIA_PLAYLISTS_JSON].orEmpty())
        val now = System.currentTimeMillis()
        val updated = playlists.map { pl ->
            if (pl.kind != kind || mediaId !in pl.itemIds) {
                pl
            } else {
                pl.copy(
                    itemIds = pl.itemIds.filter { it != mediaId },
                    updatedAt = now,
                )
            }
        }
        prefs[Keys.USER_MEDIA_PLAYLISTS_JSON] = UserMediaPlaylist.toJsonArray(updated)
    }

    val userMediaPlaylists: Flow<List<UserMediaPlaylist>> = appContext.bibleDataStore.data.map { prefs ->
        UserMediaPlaylist.parseList(prefs[Keys.USER_MEDIA_PLAYLISTS_JSON].orEmpty())
    }

    suspend fun saveUserMediaPlaylist(playlist: UserMediaPlaylist) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = UserMediaPlaylist.parseList(prefs[Keys.USER_MEDIA_PLAYLISTS_JSON].orEmpty()).toMutableList()
            val ix = cur.indexOfFirst { it.id == playlist.id }
            val stamped = playlist.copy(updatedAt = System.currentTimeMillis())
            if (ix >= 0) cur[ix] = stamped else cur.add(0, stamped)
            prefs[Keys.USER_MEDIA_PLAYLISTS_JSON] = UserMediaPlaylist.toJsonArray(cur)
        }
    }

    suspend fun deleteUserMediaPlaylist(id: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = UserMediaPlaylist.parseList(prefs[Keys.USER_MEDIA_PLAYLISTS_JSON].orEmpty())
            prefs[Keys.USER_MEDIA_PLAYLISTS_JSON] = UserMediaPlaylist.toJsonArray(cur.filter { it.id != id })
        }
    }

    suspend fun addMediaItemToUserPlaylist(playlistId: String, mediaItemId: String) {
        addMediaItemsToUserPlaylist(playlistId, listOf(mediaItemId))
    }

    suspend fun addMediaItemsToUserPlaylist(playlistId: String, mediaItemIds: List<String>) {
        if (mediaItemIds.isEmpty()) return
        appContext.bibleDataStore.edit { prefs ->
            val cur = UserMediaPlaylist.parseList(prefs[Keys.USER_MEDIA_PLAYLISTS_JSON].orEmpty()).toMutableList()
            val ix = cur.indexOfFirst { it.id == playlistId }
            if (ix < 0) return@edit
            val pl = cur[ix]
            val toAdd = mediaItemIds.filter { it !in pl.itemIds }
            if (toAdd.isEmpty()) return@edit
            cur[ix] = pl.copy(
                itemIds = pl.itemIds + toAdd,
                updatedAt = System.currentTimeMillis(),
            )
            prefs[Keys.USER_MEDIA_PLAYLISTS_JSON] = UserMediaPlaylist.toJsonArray(cur)
        }
    }

    suspend fun removeMediaItemFromUserPlaylist(playlistId: String, mediaItemId: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = UserMediaPlaylist.parseList(prefs[Keys.USER_MEDIA_PLAYLISTS_JSON].orEmpty()).toMutableList()
            val ix = cur.indexOfFirst { it.id == playlistId }
            if (ix < 0) return@edit
            val pl = cur[ix]
            if (mediaItemId !in pl.itemIds) return@edit
            cur[ix] = pl.copy(
                itemIds = pl.itemIds.filter { it != mediaItemId },
                updatedAt = System.currentTimeMillis(),
            )
            prefs[Keys.USER_MEDIA_PLAYLISTS_JSON] = UserMediaPlaylist.toJsonArray(cur)
        }
    }

    /**
     * Установить новый порядок элементов плейлиста (та же мультимножина id, что была).
     */
    suspend fun setUserMediaPlaylistItemOrder(playlistId: String, orderedItemIds: List<String>) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = UserMediaPlaylist.parseList(prefs[Keys.USER_MEDIA_PLAYLISTS_JSON].orEmpty()).toMutableList()
            val ix = cur.indexOfFirst { it.id == playlistId }
            if (ix < 0) return@edit
            val pl = cur[ix]
            if (pl.itemIds.size != orderedItemIds.size) return@edit
            val prevCounts = pl.itemIds.groupingBy { it }.eachCount()
            val newCounts = orderedItemIds.groupingBy { it }.eachCount()
            if (prevCounts != newCounts) return@edit
            cur[ix] = pl.copy(
                itemIds = orderedItemIds,
                updatedAt = System.currentTimeMillis(),
            )
            prefs[Keys.USER_MEDIA_PLAYLISTS_JSON] = UserMediaPlaylist.toJsonArray(cur)
        }
    }

    val userBibleAudios: Flow<List<BibleUserAudio>> = appContext.bibleDataStore.data.map { prefs ->
        BibleUserAudio.parseList(prefs[Keys.USER_BIBLE_AUDIOS_JSON].orEmpty())
    }

    suspend fun saveBibleAudio(audio: BibleUserAudio) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = BibleUserAudio.parseList(prefs[Keys.USER_BIBLE_AUDIOS_JSON].orEmpty()).toMutableList()
            cur.removeAll { it.id == audio.id }
            cur.add(0, audio)
            prefs[Keys.USER_BIBLE_AUDIOS_JSON] = BibleUserAudio.toJsonArray(cur)
        }
    }

    suspend fun deleteBibleAudio(id: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = BibleUserAudio.parseList(prefs[Keys.USER_BIBLE_AUDIOS_JSON].orEmpty())
            prefs[Keys.USER_BIBLE_AUDIOS_JSON] = BibleUserAudio.toJsonArray(cur.filter { it.id != id })
            stripMediaFromUserPlaylists(
                prefs,
                mediaId = id,
                kind = UserMediaPlaylistKind.AUDIO,
            )
            stripMediaPlaybackProgress(prefs, id)
        }
    }

    val userMediaPlaybackProgress: Flow<Map<String, UserMediaPlaybackProgress>> =
        appContext.bibleDataStore.data.map { prefs ->
            UserMediaPlaybackProgress.parseMap(
                prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON].orEmpty(),
            )
        }

    suspend fun upsertMediaPlaybackProgress(progress: UserMediaPlaybackProgress) {
        appContext.bibleDataStore.edit { prefs ->
            val map = UserMediaPlaybackProgress.parseMap(
                prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON].orEmpty(),
            ).toMutableMap()
            map[progress.mediaId] = progress.copy(updatedAt = System.currentTimeMillis())
            prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON] = UserMediaPlaybackProgress.toJsonArray(map)
        }
    }

    suspend fun markMediaFullyWatched(mediaId: String, kind: UserMediaKind, durationMs: Long = 0L) {
        appContext.bibleDataStore.edit { prefs ->
            val map = UserMediaPlaybackProgress.parseMap(
                prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON].orEmpty(),
            ).toMutableMap()
            val prev = map[mediaId]
            val dur = durationMs.takeIf { it > 0 } ?: prev?.durationMs ?: 0L
            map[mediaId] = UserMediaPlaybackProgress(
                mediaId = mediaId,
                kind = kind,
                positionMs = dur,
                durationMs = dur,
                completed = true,
                updatedAt = System.currentTimeMillis(),
            )
            prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON] = UserMediaPlaybackProgress.toJsonArray(map)
        }
    }

    suspend fun unmarkMediaFullyWatched(mediaId: String) {
        appContext.bibleDataStore.edit { prefs ->
            val map = UserMediaPlaybackProgress.parseMap(
                prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON].orEmpty(),
            ).toMutableMap()
            val prev = map[mediaId] ?: return@edit
            map[mediaId] = prev.copy(
                completed = false,
                updatedAt = System.currentTimeMillis(),
            )
            prefs[Keys.USER_MEDIA_PLAYBACK_PROGRESS_JSON] = UserMediaPlaybackProgress.toJsonArray(map)
        }
    }

    suspend fun clearMediaPlaybackProgress(mediaId: String) {
        appContext.bibleDataStore.edit { prefs ->
            stripMediaPlaybackProgress(prefs, mediaId)
        }
    }

    suspend fun hasDownloadImportFingerprint(fp: String): Boolean {
        val prefs = appContext.bibleDataStore.data.first()
        return fp in (prefs[Keys.DOWNLOAD_IMPORT_FINGERPRINTS] ?: emptySet())
    }

    suspend fun addDownloadImportFingerprint(fp: String) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = prefs[Keys.DOWNLOAD_IMPORT_FINGERPRINTS] ?: emptySet()
            prefs[Keys.DOWNLOAD_IMPORT_FINGERPRINTS] = cur + fp
        }
    }

    /** По умолчанию озвучка Бондаренко (Синод.) — см. [BibleAudioNarrators]. */
    val audioNarratorId: Flow<String> = appContext.bibleDataStore.data.map { prefs ->
        val raw = prefs[Keys.AUDIO_NARRATOR]?.trim().orEmpty()
        if (raw.isEmpty()) "bondarenko" else raw
    }

    suspend fun setAudioNarrator(id: String) {
        appContext.bibleDataStore.edit { it[Keys.AUDIO_NARRATOR] = id }
    }

    val readingPlanCompletedDates: Flow<Set<String>> = appContext.bibleDataStore.data.map { prefs ->
        prefs[Keys.READING_PLAN_COMPLETED] ?: emptySet()
    }

    suspend fun setReadingPlanDayCompleted(date: LocalDate, completed: Boolean) {
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        appContext.bibleDataStore.edit { prefs ->
            val cur = prefs[Keys.READING_PLAN_COMPLETED] ?: emptySet()
            prefs[Keys.READING_PLAN_COMPLETED] = if (completed) cur + key else cur - key
        }
    }

    /** Час напоминания; null — выключено. */
    val readingPlanReminderTime: Flow<Pair<Int, Int>?> = appContext.bibleDataStore.data.map { prefs ->
        val h = prefs[Keys.READING_PLAN_REMINDER_HOUR]
        if (h == null || h < 0) null else (h to (prefs[Keys.READING_PLAN_REMINDER_MIN] ?: 0))
    }

    suspend fun setReadingPlanReminder(hour: Int, minute: Int) {
        appContext.bibleDataStore.edit { prefs ->
            if (hour < 0) {
                prefs.remove(Keys.READING_PLAN_REMINDER_HOUR)
                prefs.remove(Keys.READING_PLAN_REMINDER_MIN)
            } else {
                prefs[Keys.READING_PLAN_REMINDER_HOUR] = hour
                prefs[Keys.READING_PLAN_REMINDER_MIN] = minute
            }
        }
    }

    val bookmarkTagsMap: Flow<Map<String, Set<String>>> = appContext.bibleDataStore.data.map { prefs ->
        parseBookmarkTagsJson(prefs[Keys.BOOKMARK_TAGS_JSON].orEmpty())
    }

    suspend fun setBookmarkTags(verseKey: String, tags: Set<String>) {
        appContext.bibleDataStore.edit { prefs ->
            val cur = parseBookmarkTagsJson(prefs[Keys.BOOKMARK_TAGS_JSON].orEmpty()).toMutableMap()
            if (tags.isEmpty()) cur.remove(verseKey) else cur[verseKey] = tags
            prefs[Keys.BOOKMARK_TAGS_JSON] = bookmarkTagsToJson(cur)
        }
    }

    suspend fun preferencesSnapshot(): Preferences =
        appContext.bibleDataStore.data.first()

    suspend fun snapshotToJson(): JSONObject {
        val snapshot = appContext.bibleDataStore.data.first()
        val jo = JSONObject()
        snapshot.asMap().forEach { (key, value) ->
            val name = key.name
            when (value) {
                is String -> jo.put(name, value)
                is Boolean -> jo.put(name, value)
                is Int -> jo.put(name, value)
                is Long -> jo.put(name, value)
                is Float -> jo.put(name, value.toDouble())
                is Double -> jo.put(name, value)
                is Set<*> -> {
                    val arr = JSONArray()
                    @Suppress("UNCHECKED_CAST")
                    (value as Set<String>).forEach { arr.put(it) }
                    jo.put(name, arr)
                }
                else -> jo.put(name, value.toString())
            }
        }
        return jo
    }

    suspend fun replaceAllFromJson(root: JSONObject) {
        appContext.bibleDataStore.edit { prefs ->
            prefs.clear()
            applyJsonToMutablePreferences(prefs, root)
        }
    }

    suspend fun mergePreferencesFromJson(root: JSONObject) {
        appContext.bibleDataStore.edit { prefs ->
            applyJsonToMutablePreferences(prefs, root)
        }
    }

    suspend fun loadLastSessionResume(): LastSessionResume? {
        val raw = appContext.bibleDataStore.data.first()[Keys.LAST_SESSION_RESUME_JSON].orEmpty()
        return LastSessionResume.fromJson(raw)
    }

    suspend fun applyResumePersistAction(action: ResumePersistAction) {
        appContext.bibleDataStore.edit { prefs ->
            when (action) {
                ResumePersistAction.KeepExisting -> Unit
                ResumePersistAction.ClearStored -> prefs.remove(Keys.LAST_SESSION_RESUME_JSON)
                is ResumePersistAction.Store -> {
                    prefs[Keys.LAST_SESSION_RESUME_JSON] = action.resume.toJson().toString()
                }
            }
        }
    }

    companion object {
        /** Диапазон масштаба текста читалки Корана (Другие книги → настройки). */
        const val QURAN_READER_TEXT_SCALE_DEFAULT = 1f
        const val QURAN_READER_TEXT_SCALE_MIN = 0.75f
        const val QURAN_READER_TEXT_SCALE_MAX = 1.75f

        const val QURAN_AYAH_REPEAT_PAUSE_MS_DEFAULT = 2000L
        const val QURAN_AYAH_REPEAT_PAUSE_MS_MIN = 500L
        const val QURAN_AYAH_REPEAT_PAUSE_MS_MAX = 12_000L

        const val LANG_STUDY_SRS_REPEAT_PAUSE_MS_DEFAULT = 2000L
        const val LANG_STUDY_SRS_REPEAT_PAUSE_MS_MIN = 500L
        const val LANG_STUDY_SRS_REPEAT_PAUSE_MS_MAX = 12_000L
    }
}

private fun applyJsonToMutablePreferences(prefs: MutablePreferences, root: JSONObject) {
    val keys = root.keys()
    while (keys.hasNext()) {
        val name = keys.next()
        val v = root.get(name)
        when (v) {
            is Boolean -> prefs[booleanPreferencesKey(name)] = v
            is Int -> prefs[intPreferencesKey(name)] = v
            is Long -> prefs[longPreferencesKey(name)] = v
            is Double -> {
                val d = v
                if (d == d.toInt().toDouble()) prefs[intPreferencesKey(name)] = d.toInt()
                else prefs[floatPreferencesKey(name)] = d.toFloat()
            }
            is String -> prefs[stringPreferencesKey(name)] = v
            is JSONArray -> {
                val set = (0 until v.length()).map { i -> v.getString(i) }.toSet()
                prefs[stringSetPreferencesKey(name)] = set
            }
        }
    }
}

private fun parseBookmarkTagsJson(json: String): Map<String, Set<String>> {
    if (json.isBlank()) return emptyMap()
    return try {
        val o = JSONObject(json)
        val out = mutableMapOf<String, Set<String>>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = o.getJSONArray(k)
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            out[k] = set
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun bookmarkTagsToJson(map: Map<String, Set<String>>): String {
    val o = JSONObject()
    map.forEach { (k, tags) ->
        val arr = JSONArray()
        tags.forEach { arr.put(it) }
        o.put(k, arr)
    }
    return o.toString()
}
