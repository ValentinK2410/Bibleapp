package com.example.bible.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.PlaybackParams
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "BibleAudio"

/**
 * Короткий «Mozilla/5.0» даёт HTTP 406 на wordpocket.org (WEB); MediaPlayer не шлёт такой UA — стрим работает.
 */
private const val AUDIO_DOWNLOAD_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

data class AudioNarrator(
    val id: String,
    val name: String,
    val urlFolder: String,
)

object BibleAudioNarrators {
    val all = listOf(
        AudioNarrator("bondarenko", "Бондаренко (Синод.)", "syn-bondarenko"),
        AudioNarrator("kozlov", "Козлов (Синод.)", "syn-kozlov"),
        AudioNarrator("efimov", "Ефимов (Синод.)", "syn-efimov"),
        AudioNarrator("jbl", "Свет на Востоке (Синод.)", "syn-jbl"),
        AudioNarrator("new-russian", "Новый русский перевод", "new-russian"),
        AudioNarrator("rbo", "РБО", "rbo-orgin"),
        AudioNarrator("bti", "Кулаковых", "bti-prozorovsky"),
        /** Англ. WEB: публичные MP3 (WordProject → wordpocket.org), кэш в bible_audio/web/ */
        AudioNarrator("web", "WEB (англ.)", ""),
    )

    /** Иврит ВЗ: Talking Bibles / Mechon Mamre (порядок книг как на сайте, не как в сетке 1…39). */
    val hebrewOt = AudioNarrator("hebrew-ot", "На иврите (ВЗ)", "")

    /** Koine НЗ: WordProAudio (нумерация книг 40…66 как у KJV audio). */
    val greekNt = AudioNarrator("greek-nt", "На греческом (НЗ)", "")

    /** Все дикторы + языки оригинала для диалога выбора озвучки. */
    val forPicker: List<AudioNarrator> by lazy {
        buildList {
            addAll(all)
            add(hebrewOt)
            add(greekNt)
        }
    }

    fun byId(id: String): AudioNarrator = when (id) {
        hebrewOt.id -> hebrewOt
        greekNt.id -> greekNt
        else -> all.find { it.id == id } ?: all.first()
    }
}

/**
 * Привязка озвучки к переводу:
 * - **WEB** — англ. дорожка `web`;
 * - **РБО** — `rbo`; **Кулаковых** — `bti`; **Новый русский** — `new-russian`;
 * - **Синодальный**, **Подстрочный** — диктор из настроек ([preferredNarratorId]),
 *   по умолчанию **Бондаренко** (см. [BiblePreferences.audioNarratorId]).
 * Идентификатор `web` в настройках для русских переводов подменяется на `bondarenko`.
 * Выбор **На иврите (ВЗ)** / **На греческом (НЗ)** в настройках имеет приоритет над привязкой к переводу.
 */
fun narratorForTranslation(
    translation: TranslationId,
    preferredNarratorId: String,
): AudioNarrator {
    when (preferredNarratorId) {
        BibleAudioNarrators.hebrewOt.id -> return BibleAudioNarrators.hebrewOt
        BibleAudioNarrators.greekNt.id -> return BibleAudioNarrators.greekNt
    }
    return when (translation) {
        TranslationId.WEB -> BibleAudioNarrators.byId("web")
        TranslationId.RBO -> BibleAudioNarrators.byId("rbo")
        TranslationId.BTI -> BibleAudioNarrators.byId("bti")
        TranslationId.NRT -> BibleAudioNarrators.byId("new-russian")
        TranslationId.SYNODAL,
        TranslationId.INTERLINEAR,
        -> {
            val id = if (preferredNarratorId == "web") "bondarenko" else preferredNarratorId
            BibleAudioNarrators.byId(id)
        }
    }
}

/**
 * Озвучка главы WEB (англ.): публичные MP3 WordProject (KJV), нумерация книг **1…66 в классическом протестантском порядке**
 * (НЗ: Евангелия → Деяния → послания Павла → Евреям → соборные → Откр.), **не** как порядок в сетке [BibleCanon].
 */
fun webChapterAudioUrl(bookId: String, chapter: Int): String? {
    val bookNum = WORDPROJECT_BOOK_NUMBER[bookId] ?: return null
    return "https://www.wordpocket.org/bibles/app/audio/1/$bookNum/$chapter.mp3"
}

/** Нумерация книг 1…66 для русских дорожек (4bbl.ru) — совпадает с порядком [BibleCanon.allBooks]. */
private val CANON_GRID_BOOK_NUMBER: Map<String, Int> by lazy {
    BibleCanon.allBooks.mapIndexed { index, entry ->
        entry.id to (index + 1)
    }.toMap()
}

/**
 * Стандартный протестантский порядок 1…66 (как у WordProject / KJV audio): ВЗ как в каноне приложения,
 * НЗ — Мф…Деян → Рим…Флм → Евр → Иак…Иуд → Откр.
 */
private val WORDPROJECT_BOOK_NUMBER: Map<String, Int> by lazy {
    val ot = BibleCanon.allBooks.take(39).map { it.id }
    val nt = listOf(
        "matthew", "mark", "luke", "john", "acts",
        "romans", "1_corinthians", "2_corinthians", "galatians", "ephesians", "philippians", "colossians",
        "1_thessalonians", "2_thessalonians", "1_timothy", "2_timothy", "titus", "philemon", "hebrews",
        "james", "1_peter", "2_peter", "1_john", "2_john", "3_john", "jude", "revelation",
    )
    check(ot.size == 39 && nt.size == 27)
    (ot + nt).mapIndexed { i, id -> id to (i + 1) }.toMap()
}

/**
 * Префикс имени файла после `t` на mechon-mamre.org (Tanach / Talking Bibles), см. ptmp3prq.htm.
 * Отличается от протестантского порядка книг (напр. Псалтирь — 26, а не 19).
 */
private val MECHON_BOOK_PREFIX: Map<String, String> = mapOf(
    "genesis" to "01",
    "exodus" to "02",
    "leviticus" to "03",
    "numbers" to "04",
    "deuteronomy" to "05",
    "joshua" to "06",
    "judges" to "07",
    "1_samuel" to "08a",
    "2_samuel" to "08b",
    "1_kings" to "09a",
    "2_kings" to "09b",
    "isaiah" to "10",
    "jeremiah" to "11",
    "ezekiel" to "12",
    "hosea" to "13",
    "joel" to "14",
    "amos" to "15",
    "obadiah" to "16",
    "jonah" to "17",
    "micah" to "18",
    "nahum" to "19",
    "habakkuk" to "20",
    "zephaniah" to "21",
    "haggai" to "22",
    "zechariah" to "23",
    "malachi" to "24",
    "1_chronicles" to "25a",
    "2_chronicles" to "25b",
    "psalms" to "26",
    "job" to "27",
    "proverbs" to "28",
    "ruth" to "29",
    "song_of_solomon" to "30",
    "ecclesiastes" to "31",
    "lamentations" to "32",
    "esther" to "33",
    "daniel" to "34",
    "ezra" to "35a",
    "nehemiah" to "35b",
)

/** Главы Псалмов 100–150: на Mechon не `t26100`, а `t26a0` … `t26f0`. */
private fun mechonPsalmsChapterSuffix(chapter: Int): String {
    require(chapter in 1..150) { "psalms chapter $chapter" }
    return when {
        chapter <= 99 -> "%02d".format(chapter)
        chapter == 150 -> "f0"
        else -> {
            val idx = chapter - 100
            val letter = 'a' + idx / 10
            val digit = idx % 10
            "$letter$digit"
        }
    }
}

/** Озвучка ивритом (ВЗ): прямые MP3 Mechon Mamre. */
fun hebrewOtChapterAudioUrl(bookId: String, chapter: Int): String? {
    if (!BibleCanon.isOldTestament(bookId)) return null
    val prefix = MECHON_BOOK_PREFIX[bookId] ?: return null
    val body = if (bookId == "psalms") mechonPsalmsChapterSuffix(chapter) else "%02d".format(chapter)
    return "https://www.mechon-mamre.org/mp3/t$prefix$body.mp3"
}

/** Озвучка греческим текстом НЗ (Koine): WordProAudio, та же нумерация 40…66, что у [webChapterAudioUrl]. */
fun greekNtChapterAudioUrl(bookId: String, chapter: Int): String? {
    if (!BibleCanon.isNewTestament(bookId)) return null
    val n = WORDPROJECT_BOOK_NUMBER[bookId] ?: return null
    if (n < 40) return null
    return "https://www.wordproaudio.net/bibles/app/audio/58/$n/$chapter.mp3"
}

/** Книги для [BibleAudioPlayer.downloadEntireBible]: весь канон, только ВЗ или только НЗ. */
fun booksForDownloadEntireBible(narrator: AudioNarrator): List<CanonBookEntry> = when (narrator.id) {
    "hebrew-ot" -> BibleCanon.allBooks.filter { BibleCanon.isOldTestament(it.id) }
    "greek-nt" -> BibleCanon.allBooks.filter { BibleCanon.isNewTestament(it.id) }
    else -> BibleCanon.allBooks
}

fun chapterCountForDownloadEntireBible(narrator: AudioNarrator): Int =
    booksForDownloadEntireBible(narrator).sumOf { it.chapters }

private fun bookNumberForNarrator(narratorId: String, bookId: String): Int? =
    when (narratorId) {
        "web" -> WORDPROJECT_BOOK_NUMBER[bookId]
        "greek-nt" -> WORDPROJECT_BOOK_NUMBER[bookId]?.takeIf { BibleCanon.isNewTestament(bookId) }
        "hebrew-ot" -> CANON_GRID_BOOK_NUMBER[bookId]?.takeIf { BibleCanon.isOldTestament(bookId) }
        else -> CANON_GRID_BOOK_NUMBER[bookId]
    }

fun bookAudioNumber(narratorId: String, bookId: String): String? {
    val num = bookNumberForNarrator(narratorId, bookId) ?: return null
    return "%02d".format(num)
}

fun chapterAudioUrl(narrator: AudioNarrator, bookId: String, chapter: Int): String? {
    when (narrator.id) {
        "hebrew-ot" -> return hebrewOtChapterAudioUrl(bookId, chapter)
        "greek-nt" -> return greekNtChapterAudioUrl(bookId, chapter)
    }
    val bookNum = bookAudioNumber(narrator.id, bookId) ?: return null
    val chapNum = "%02d".format(chapter)
    return "https://4bbl.ru/data/${narrator.urlFolder}/$bookNum/$chapNum.mp3"
}

fun localAudioFile(context: Context, narratorId: String, bookId: String, chapter: Int): File {
    val dir = File(context.filesDir, "bible_audio/$narratorId")
    dir.mkdirs()
    val bookNum = bookAudioNumber(narratorId, bookId) ?: "00"
    val chapNum = "%02d".format(chapter)
    return File(dir, "${bookNum}_${chapNum}.mp3")
}

/**
 * Путь к MP3 в assets: `bible_audio/{narratorId}/{bookNum}_{chapter}.mp3` — как у кэша в [localAudioFile].
 * Положите файлы в `app/src/main/assets/…` перед сборкой релиза, чтобы озвучка шла без скачивания.
 */
fun bibleChapterAudioAssetPath(narratorId: String, bookId: String, chapter: Int): String? {
    val bookNum = bookAudioNumber(narratorId, bookId) ?: return null
    val chapNum = "%02d".format(chapter)
    return "bible_audio/$narratorId/${bookNum}_$chapNum.mp3"
}

fun openBundledBibleChapterAudio(
    context: Context,
    narratorId: String,
    bookId: String,
    chapter: Int,
): AssetFileDescriptor? {
    val path = bibleChapterAudioAssetPath(narratorId, bookId, chapter) ?: return null
    return try {
        context.assets.openFd(path)
    } catch (_: Exception) {
        null
    }
}

fun isChapterDownloaded(context: Context, narratorId: String, bookId: String, chapter: Int): Boolean {
    val f = localAudioFile(context, narratorId, bookId, chapter)
    if (f.exists() && f.length() > 1024) return true
    return openBundledBibleChapterAudio(context, narratorId, bookId, chapter)?.use { true } ?: false
}

fun downloadedChapters(context: Context, narratorId: String, bookId: String): Set<Int> {
    val bookNum = bookAudioNumber(narratorId, bookId) ?: return emptySet()
    val dir = File(context.filesDir, "bible_audio/$narratorId")
    if (!dir.exists()) return emptySet()
    val prefix = "${bookNum}_"
    return dir.listFiles()
        ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".mp3") && it.length() > 1024 }
        ?.mapNotNull {
            it.name.removePrefix(prefix).removeSuffix(".mp3").toIntOrNull()
        }
        ?.toSet()
        ?: emptySet()
}

fun isBookFullyDownloaded(context: Context, narratorId: String, bookId: String, totalChapters: Int): Boolean {
    if (totalChapters <= 0) return false
    val downloaded = downloadedChapters(context, narratorId, bookId)
    return downloaded.size >= totalChapters
}

fun booksWithDownloadedAudio(context: Context, narratorId: String): Set<String> {
    val dir = File(context.filesDir, "bible_audio/$narratorId")
    if (!dir.exists()) return emptySet()
    val files = dir.listFiles() ?: return emptySet()
    val bookNums = files
        .filter { it.name.endsWith(".mp3") && it.length() > 1024 }
        .mapNotNull { it.name.split("_").firstOrNull()?.toIntOrNull() }
        .toSet()
    return when (narratorId) {
        "web",
        "greek-nt",
        -> {
            val numToId = WORDPROJECT_BOOK_NUMBER.entries.associate { (id, n) -> n to id }
            bookNums.mapNotNull { numToId[it] }.toSet()
        }
        else -> BibleCanon.allBooks.mapIndexedNotNull { index, entry ->
            if ((index + 1) in bookNums) entry.id else null
        }.toSet()
    }
}

data class BiblePlayerState(
    val isPlaying: Boolean = false,
    val bookId: String = "",
    val chapter: Int = 0,
    val narratorId: String = "",
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

object BibleAudioPlayer {

    private var player: MediaPlayer? = null
    private var currentKey: String = ""
    private var appContext: Context? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sleepTickRunnable = Runnable { tickSleepTimer() }
    private var sleepEndAtMs: Long = 0L
    private var pendingSleepStopAfterChapter = false

    private val _state = MutableStateFlow(BiblePlayerState())
    val state: StateFlow<BiblePlayerState> = _state.asStateFlow()

    private val _downloadTick = MutableStateFlow(0)
    val downloadTick: StateFlow<Int> = _downloadTick.asStateFlow()

    /** Автоматически переходить к следующей главе той же книги после окончания текущей. */
    private val _continueChapters = MutableStateFlow(false)
    val continueChapters: StateFlow<Boolean> = _continueChapters.asStateFlow()

    /**
     * Событие при переходе озвучки на следующую главу (той же книги): номер новой главы.
     * Экран чтения подписывается и открывает тот же текст, если пользователь смотрел предыдущую главу.
     */
    private val _chapterContinueNavigation = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
    val chapterContinueNavigation: SharedFlow<Pair<String, Int>> = _chapterContinueNavigation.asSharedFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    /** Оставшееся время таймера сна, мс; null — таймер выключен. */
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    /** Если true, после срабатывания таймера воспроизведение останавливается в конце текущей главы. */
    private val _sleepStopAtChapterEnd = MutableStateFlow(false)
    val sleepStopAtChapterEnd: StateFlow<Boolean> = _sleepStopAtChapterEnd.asStateFlow()

    /** Таймер сработал, ждём конца главы перед остановкой. */
    private val _sleepAwaitingChapterEnd = MutableStateFlow(false)
    val sleepAwaitingChapterEnd: StateFlow<Boolean> = _sleepAwaitingChapterEnd.asStateFlow()

    fun setContinueChapters(enabled: Boolean) {
        _continueChapters.value = enabled
    }

    fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2f)
        _playbackSpeed.value = s
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                player?.let { p ->
                    p.playbackParams = p.playbackParams.setSpeed(s)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * @param minutes 0 — выключить таймер
     * @param stopAtChapterEnd остановить в конце главы вместо немедленной остановки
     */
    fun setSleepTimer(minutes: Int, stopAtChapterEnd: Boolean) {
        cancelSleepTimerInternal()
        if (minutes <= 0) return
        _sleepStopAtChapterEnd.value = stopAtChapterEnd
        sleepEndAtMs = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimerRemainingMs.value = minutes * 60_000L
        mainHandler.postDelayed(sleepTickRunnable, 1000)
    }

    fun cancelSleepTimer() {
        cancelSleepTimerInternal()
    }

    private fun cancelSleepTimerInternal() {
        mainHandler.removeCallbacks(sleepTickRunnable)
        sleepEndAtMs = 0L
        pendingSleepStopAfterChapter = false
        _sleepTimerRemainingMs.value = null
        _sleepAwaitingChapterEnd.value = false
    }

    private fun tickSleepTimer() {
        val rem = sleepEndAtMs - System.currentTimeMillis()
        if (rem <= 0) {
            _sleepTimerRemainingMs.value = null
            onSleepTimerFired()
            return
        }
        _sleepTimerRemainingMs.value = rem
        mainHandler.postDelayed(sleepTickRunnable, 1000)
    }

    private fun onSleepTimerFired() {
        mainHandler.removeCallbacks(sleepTickRunnable)
        sleepEndAtMs = 0L
        _sleepTimerRemainingMs.value = null
        val stopAtEnd = _sleepStopAtChapterEnd.value
        if (stopAtEnd && player != null) {
            pendingSleepStopAfterChapter = true
            _sleepAwaitingChapterEnd.value = true
        } else {
            applySleepStop()
        }
    }

    private fun applySleepStop() {
        pendingSleepStopAfterChapter = false
        _sleepAwaitingChapterEnd.value = false
        try {
            player?.pause()
            _state.value = _state.value.copy(isPlaying = false)
        } catch (_: Exception) {}
        release()
    }

    private fun applyPlaybackSpeed(mp: MediaPlayer) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            mp.playbackParams = mp.playbackParams.setSpeed(_playbackSpeed.value)
        } catch (_: Exception) {
            try {
                mp.playbackParams = PlaybackParams().setSpeed(_playbackSpeed.value)
            } catch (_: Exception) {}
        }
    }

    fun playChapter(
        context: Context,
        narrator: AudioNarrator,
        bookId: String,
        chapter: Int,
    ) {
        val key = "${narrator.id}/$bookId/$chapter"
        if (key == currentKey && player != null) {
            appContext = context.applicationContext
            try {
                applyPlaybackSpeed(player!!)
                player!!.start()
                _state.value = _state.value.copy(isPlaying = true, error = null)
            } catch (e: Exception) {
                Log.e(TAG, "resume failed", e)
            }
            return
        }

        release()
        appContext = context.applicationContext
        currentKey = key
        _state.value = BiblePlayerState(
            bookId = bookId,
            chapter = chapter,
            narratorId = narrator.id,
            isLoading = true,
        )

        val local = localAudioFile(context, narrator.id, bookId, chapter)
        val bundledAfd = if (local.exists() && local.length() > 1024) {
            null
        } else {
            openBundledBibleChapterAudio(context, narrator.id, bookId, chapter)
        }
        val remoteUrl: String? = if ((local.exists() && local.length() > 1024) || bundledAfd != null) {
            null
        } else {
            when {
                narrator.id == "web" -> webChapterAudioUrl(bookId, chapter)
                narrator.id == "hebrew-ot" -> hebrewOtChapterAudioUrl(bookId, chapter)
                narrator.id == "greek-nt" -> greekNtChapterAudioUrl(bookId, chapter)
                else -> chapterAudioUrl(narrator, bookId, chapter)
            }
        }

        if (!local.exists() || local.length() <= 1024) {
            if (bundledAfd == null && remoteUrl == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Аудио недоступно")
                return
            }
        }

        try {
            val mp = MediaPlayer()
            when {
                local.exists() && local.length() > 1024 -> mp.setDataSource(local.absolutePath)
                bundledAfd != null -> bundledAfd.use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                else -> mp.setDataSource(remoteUrl!!)
            }
            mp.setOnPreparedListener { prepared ->
                applyPlaybackSpeed(prepared)
                prepared.start()
                _state.value = _state.value.copy(
                    isPlaying = true,
                    isLoading = false,
                    durationMs = prepared.duration,
                )
            }
            mp.setOnCompletionListener {
                if (pendingSleepStopAfterChapter) {
                    applySleepStop()
                    return@setOnCompletionListener
                }
                val ctx = appContext
                val st = _state.value
                if (
                    _continueChapters.value &&
                    ctx != null &&
                    st.bookId.isNotBlank() &&
                    st.narratorId.isNotBlank()
                ) {
                    val canon = BibleCanon.byId(st.bookId)
                    val narrator = BibleAudioNarrators.byId(st.narratorId)
                    if (canon != null && st.chapter < canon.chapters) {
                        val nextCh = st.chapter + 1
                        _chapterContinueNavigation.tryEmit(st.bookId to nextCh)
                        playChapter(ctx, narrator, st.bookId, nextCh)
                        return@setOnCompletionListener
                    }
                }
                _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isLoading = false,
                    error = "Ошибка воспроизведения",
                )
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            Log.e(TAG, "playChapter failed", e)
            _state.value = _state.value.copy(isLoading = false, error = e.message)
        }
    }

    fun togglePlay() {
        val mp = player ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                applyPlaybackSpeed(mp)
                mp.start()
                _state.value = _state.value.copy(isPlaying = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "togglePlay failed", e)
        }
    }

    /** Пауза озвучки главы (например перед воспроизведением пользовательского трека таймкодов). */
    fun pauseIfPlaying() {
        val mp = player ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                _state.value = _state.value.copy(isPlaying = false)
            }
        } catch (_: Exception) {}
    }

    fun seekTo(ms: Int) {
        try {
            player?.seekTo(ms)
            _state.value = _state.value.copy(positionMs = ms)
        } catch (_: Exception) {}
    }

    fun updatePosition() {
        if (_state.value.isPlaying) {
            try {
                player?.let {
                    _state.value = _state.value.copy(positionMs = it.currentPosition)
                }
            } catch (_: Exception) {}
        }
    }

    fun release() {
        cancelSleepTimerInternal()
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
        currentKey = ""
        appContext = null
        _state.value = BiblePlayerState()
    }

    suspend fun downloadChapter(
        context: Context,
        narrator: AudioNarrator,
        bookId: String,
        chapter: Int,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dest = localAudioFile(context, narrator.id, bookId, chapter)
        if (dest.exists() && dest.length() > 1024) {
            onProgress(100)
            return@withContext dest
        }

        val assetPath = bibleChapterAudioAssetPath(narrator.id, bookId, chapter)
        if (assetPath != null) {
            try {
                dest.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                if (dest.length() > 1024) {
                    onProgress(100)
                    _downloadTick.value++
                    return@withContext dest
                }
            } catch (_: Exception) {
                runCatching { dest.delete() }
            }
        }

        val url = when {
            narrator.id == "web" -> webChapterAudioUrl(bookId, chapter)
            narrator.id == "hebrew-ot" -> hebrewOtChapterAudioUrl(bookId, chapter)
            narrator.id == "greek-nt" -> greekNtChapterAudioUrl(bookId, chapter)
            else -> chapterAudioUrl(narrator, bookId, chapter)
        } ?: throw IllegalArgumentException("Unknown book: $bookId")

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", AUDIO_DOWNLOAD_USER_AGENT)
        conn.setRequestProperty("Accept", "*/*")
        when {
            "wordpocket.org" in url -> conn.setRequestProperty("Referer", "https://www.wordpocket.org/")
            "wordproaudio.net" in url -> conn.setRequestProperty("Referer", "https://www.wordproaudio.net/")
            "mechon-mamre.org" in url -> conn.setRequestProperty("Referer", "https://www.mechon-mamre.org/")
        }
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()

        if (conn.responseCode != 200) {
            throw Exception("HTTP ${conn.responseCode}")
        }

        val total = conn.contentLength
        var downloaded = 0
        val tmp = File(dest.parentFile, "${dest.name}.tmp")

        conn.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) onProgress((downloaded * 100L / total).toInt())
                }
            }
        }
        tmp.renameTo(dest)
        _downloadTick.value++
        dest
    }

    /**
     * Скачивает все главы Библии для выбранного диктора (РБО, Кулаковых и т.д.).
     * Уже скачанные файлы пропускаются ([downloadChapter]).
     */
    suspend fun downloadEntireBible(
        context: Context,
        narrator: AudioNarrator,
        onProgress: (done: Int, total: Int, label: String) -> Unit,
    ) {
        val books = booksForDownloadEntireBible(narrator)
        val total = books.sumOf { it.chapters }
        var done = 0
        for (book in books) {
            for (ch in 1..book.chapters) {
                val label = "${book.abbrRu} $ch"
                withContext(Dispatchers.IO) {
                    try {
                        downloadChapter(context, narrator, book.id, ch)
                    } catch (_: Exception) {
                        // пропускаем недоступные главы
                    }
                }
                done++
                withContext(Dispatchers.Main) {
                    onProgress(done, total, label)
                }
            }
        }
    }
}
