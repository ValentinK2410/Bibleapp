package com.example.bible.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.bible.data.AudioNarrator
import com.example.bible.data.BibleAudioNarrators
import com.example.bible.data.BibleAudioPlayer
import com.example.bible.data.BibleAudioRepeatMode
import com.example.bible.data.BibleTtsController
import com.example.bible.data.BibleVerse
import com.example.bible.data.BibleVerseRepeatConfig
import com.example.bible.data.NoteScriptureLinks
import com.example.bible.data.ScriptureAudioSegment
import com.example.bible.data.TimemarkStore
import com.example.bible.data.TranslationId
import com.example.bible.data.applyTtsVoiceForTranslation
import com.example.bible.data.estimatedPlaybackStartMs
import com.example.bible.data.timemarkPlaybackStartMs
import com.example.bible.data.timemarkPlaybackStopAfterMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Актуальные данные открытой в читалке главы, чтобы панель [BibleAudioBar] знала
 * количество стихов и их тексты для выбора диапазона и повтора.
 */
data class ReaderActiveChapterSnapshot(
    val bookId: String = "",
    val chapter: Int = 0,
    val translation: TranslationId = TranslationId.SYNODAL,
    val narratorId: String = "bondarenko",
    val verses: List<BibleVerse> = emptyList(),
)

object ReaderChapterAudioContext {
    private val _snapshot = MutableStateFlow(ReaderActiveChapterSnapshot())
    val snapshot: StateFlow<ReaderActiveChapterSnapshot> = _snapshot.asStateFlow()

    fun update(
        bookId: String,
        chapter: Int,
        translation: TranslationId,
        narratorId: String,
        verses: List<BibleVerse>,
    ) {
        _snapshot.value = ReaderActiveChapterSnapshot(
            bookId = bookId,
            chapter = chapter,
            translation = translation,
            narratorId = narratorId,
            verses = verses,
        )
    }
}

/** Состояние чтения и повтора выбранных стихов синтезатором речи (TTS). */
data class ReaderVerseTtsLoopState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val bookId: String = "",
    val chapter: Int = 0,
    val translation: TranslationId = TranslationId.SYNODAL,
    val verses: List<BibleVerse> = emptyList(),
    val currentVerseIndex: Int = 0,
    val repeatEnabled: Boolean = true,
    /** 0 = бесконечно (∞), >0 = число повторов. */
    val targetLoops: Int = 0,
    val completedLoops: Int = 0,
    val rangeLabelRu: String = "",
) {
    val currentVerseNumber: Int
        get() = verses.getOrNull(currentVerseIndex)?.number ?: verses.firstOrNull()?.number ?: 1

    val currentVerseText: String
        get() = verses.getOrNull(currentVerseIndex)?.text.orEmpty()
}

/**
 * Контроллер озвучки и зацикленного повтора выбранных стихов (или всей главы) через системный TTS.
 * Работает полностью офлайн и точно соблюдает границы каждого стиха.
 */
object ReaderVerseTtsLooper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var currentEnginePkg: String = ""

    private val _state = MutableStateFlow(ReaderVerseTtsLoopState())
    val state: StateFlow<ReaderVerseTtsLoopState> = _state.asStateFlow()

    fun start(
        context: Context,
        bookId: String,
        chapter: Int,
        translation: TranslationId,
        verses: List<BibleVerse>,
        repeatEnabled: Boolean,
        targetLoops: Int = 0,
        rangeLabelRu: String = "",
    ) {
        if (verses.isEmpty()) return
        // Останавливаем MP3-плеер, чтобы две озвучки не накладывались друг на друга
        clearReaderAudioSegmentQueue()
        BibleAudioPlayer.stopForNavigation()

        val label = rangeLabelRu.ifBlank {
            val first = verses.first().number
            val last = verses.last().number
            if (first == last) "ст. $first" else "ст. $first–$last"
        }

        _state.value = ReaderVerseTtsLoopState(
            isActive = true,
            isPlaying = true,
            bookId = bookId,
            chapter = chapter,
            translation = translation,
            verses = verses,
            currentVerseIndex = 0,
            repeatEnabled = repeatEnabled,
            targetLoops = targetLoops.coerceAtLeast(0),
            completedLoops = 0,
            rangeLabelRu = label,
        )
        ensureTtsAndSpeak(context.applicationContext)
    }

    fun togglePlay(context: Context) {
        val st = _state.value
        if (!st.isActive) return
        if (st.isPlaying) {
            mainHandler.removeCallbacksAndMessages(null)
            runCatching { tts?.stop() }
            _state.value = st.copy(isPlaying = false)
        } else {
            _state.value = st.copy(isPlaying = true)
            ensureTtsAndSpeak(context.applicationContext)
        }
    }

    fun setRepeatSettings(repeatEnabled: Boolean, targetLoops: Int) {
        val st = _state.value
        if (!st.isActive) return
        _state.value = st.copy(
            repeatEnabled = repeatEnabled,
            targetLoops = targetLoops.coerceAtLeast(0),
            completedLoops = 0,
        )
    }

    fun skipVerse(context: Context, delta: Int) {
        val st = _state.value
        if (!st.isActive || st.verses.isEmpty()) return
        val nextIdx = (st.currentVerseIndex + delta).coerceIn(0, st.verses.lastIndex)
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        _state.value = st.copy(currentVerseIndex = nextIdx, isPlaying = true)
        ensureTtsAndSpeak(context.applicationContext)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        _state.value = ReaderVerseTtsLoopState()
    }

    private fun ensureTtsAndSpeak(appContext: Context) {
        val wantedPkg = BibleTtsController.settings.value.enginePackage.trim()
        if (tts != null && ttsReady && wantedPkg == currentEnginePkg) {
            speakCurrentVerse()
            return
        }
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
        currentEnginePkg = wantedPkg

        var engineRef: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS && engineRef != null) {
                    tts = engineRef
                    ttsReady = true
                    attachProgressListener(engineRef!!)
                    speakCurrentVerse()
                } else {
                    _state.value = _state.value.copy(isPlaying = false)
                }
            }
        }
        engineRef = if (wantedPkg.isNotEmpty()) {
            TextToSpeech(appContext, listener, wantedPkg)
        } else {
            TextToSpeech(appContext, listener)
        }
    }

    private fun attachProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {
                mainHandler.post { onVerseUtteranceCompleted(utteranceId) }
            }
            override fun onDone(utteranceId: String?) {
                mainHandler.post { onVerseUtteranceCompleted(utteranceId) }
            }
        })
    }

    private fun speakCurrentVerse() {
        val st = _state.value
        if (!st.isActive || !st.isPlaying || st.verses.isEmpty()) return
        val engine = tts ?: return
        applyTtsVoiceForTranslation(engine, st.translation, BibleTtsController.settings.value)
        val idx = st.currentVerseIndex.coerceIn(0, st.verses.lastIndex)
        val verse = st.verses[idx]
        val textToSpeak = verse.text.trim().ifBlank { "Стих ${verse.number}" }
        val utteranceId = "tts_loop_${st.bookId}_${st.chapter}_${verse.number}_${System.nanoTime()}"
        engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun onVerseUtteranceCompleted(utteranceId: String?) {
        if (utteranceId == null || !utteranceId.startsWith("tts_loop_")) return
        val st = _state.value
        if (!st.isActive || !st.isPlaying || st.verses.isEmpty()) return
        val nextIdx = st.currentVerseIndex + 1
        if (nextIdx < st.verses.size) {
            _state.value = st.copy(currentVerseIndex = nextIdx)
            mainHandler.postDelayed({ speakCurrentVerse() }, 350L)
        } else {
            // Завершён проход всех выбранных стихов
            if (st.repeatEnabled) {
                val done = st.completedLoops + 1
                if (st.targetLoops <= 0 || done < st.targetLoops) {
                    _state.value = st.copy(
                        currentVerseIndex = 0,
                        completedLoops = done,
                    )
                    mainHandler.postDelayed({ speakCurrentVerse() }, 750L)
                    return
                }
            }
            _state.value = st.copy(
                isPlaying = false,
                currentVerseIndex = 0,
                completedLoops = 0,
            )
        }
    }
}

private data class ReaderAudioSegmentQueue(
    val appContext: Context,
    val narrator: AudioNarrator,
    val bookId: String,
    val chapter: Int,
    val translation: TranslationId,
    val segments: List<ScriptureAudioSegment>,
    val chapterVerseCount: Int,
    var index: Int,
)

private var readerAudioSegmentQueue: ReaderAudioSegmentQueue? = null
private var onNoteAudioAdvance: (() -> Unit)? = null
private var noteAdvanceArmed = false

fun setNoteAudioAdvanceListener(listener: (() -> Unit)?) {
    onNoteAudioAdvance = listener
    if (listener == null) noteAdvanceArmed = false
}

fun clearReaderAudioSegmentQueue() {
    readerAudioSegmentQueue = null
    BibleAudioPlayer.setSegmentStopListener(null)
}

fun stopReaderChapterAudio() {
    onNoteAudioAdvance = null
    noteAdvanceArmed = false
    clearReaderAudioSegmentQueue()
    ReaderVerseTtsLooper.stop()
    BibleAudioPlayer.stopForNavigation()
}

private fun armNoteAudioAdvance() {
    noteAdvanceArmed = onNoteAudioAdvance != null
}

private fun notifyNoteAudioAdvance() {
    if (!noteAdvanceArmed) return
    noteAdvanceArmed = false
    onNoteAudioAdvance?.invoke()
}

/**
 * Запуск прослушивания или циклического повтора выбранных стихов (диапазона [startVerse]..[endVerse]
 * или набора сегментов [segmentSpec], например "1-3,7,10-12") через аудиодорожку диктора.
 */
fun startReaderVerseRangeAudio(
    context: Context,
    narrator: AudioNarrator,
    bookId: String,
    chapter: Int,
    translation: TranslationId,
    startVerse: Int,
    endVerse: Int,
    chapterVerseCount: Int,
    repeat: Boolean,
    targetLoops: Int = 0,
    segmentSpec: String? = null,
) {
    ReaderVerseTtsLooper.stop()
    val safeCount = chapterVerseCount.coerceAtLeast(maxOf(startVerse, endVerse, 1))
    val from = minOf(startVerse, endVerse).coerceIn(1, safeCount)
    val to = maxOf(startVerse, endVerse).coerceIn(from, safeCount)

    val segments = if (!segmentSpec.isNullOrBlank()) {
        val parsed = NoteScriptureLinks.parseSegmentSpec(segmentSpec, safeCount)
        if (parsed.isNotEmpty()) {
            NoteScriptureLinks.coalesceAdjacentAudioSegments(parsed)
        } else {
            listOf(ScriptureAudioSegment(from, to))
        }
    } else {
        listOf(ScriptureAudioSegment(from, to))
    }

    val effectiveFirst = segments.firstOrNull()?.startVerse ?: from
    val effectiveLast = segments.lastOrNull()?.endVerseInclusive ?: safeCount

    BibleAudioPlayer.resetSegmentOffsets()
    BibleAudioPlayer.setVerseRepeatConfig(
        BibleVerseRepeatConfig(
            startVerse = effectiveFirst,
            endVerse = effectiveLast,
            segmentSpec = segmentSpec?.takeIf { it.isNotBlank() },
            chapterVerseCount = safeCount,
            translationCode = translation.code,
        ),
    )
    if (repeat) {
        BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.VERSES, targetCount = targetLoops)
    } else {
        BibleAudioPlayer.resetRepeatCompletedCount()
        if (BibleAudioPlayer.repeatMode.value == BibleAudioRepeatMode.VERSES) {
            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF, targetCount = 0)
            // Сохраняем плашку выбранного отрезка стихов даже при однократном проигрывании
            BibleAudioPlayer.setVerseRepeatConfig(
                BibleVerseRepeatConfig(
                    startVerse = effectiveFirst,
                    endVerse = effectiveLast,
                    segmentSpec = segmentSpec?.takeIf { it.isNotBlank() },
                    chapterVerseCount = safeCount,
                    translationCode = translation.code,
                ),
            )
        }
    }

    playReaderChapterAudioSegments(
        context = context,
        narrator = narrator,
        bookId = bookId,
        chapter = chapter,
        translation = translation,
        segments = segments,
        chapterVerseCount = safeCount,
        startIndex = 0,
        preserveRepeatConfig = true,
    )
}

/**
 * Сдвиг начала зацикленного отрезка стихов на [deltaMs] мс (например -2000 или +2000)
 * с мгновенным перезапуском текущего сегмента с новой позиции.
 */
fun nudgeReaderVerseLoopStart(deltaMs: Int) {
    BibleAudioPlayer.adjustSegmentLoopStartOffsetMs(deltaMs)
    if (readerAudioSegmentQueue != null) {
        playQueuedAudioSegment()
    }
}

/**
 * Сбросить ограничение по стихам и продолжить слушать всю главу целиком.
 */
fun resetReaderVerseRangeToFullChapter() {
    clearReaderAudioSegmentQueue()
    BibleAudioPlayer.setVerseRepeatConfig(null)
    BibleAudioPlayer.resetSegmentOffsets()
    BibleAudioPlayer.clearSegmentStop()
}

/** Озвучка всех сегментов ссылки подряд (диапазон или 7,9,10) без повторного нажатия. */
fun playReaderChapterAudioSegments(
    context: Context,
    narrator: AudioNarrator,
    bookId: String,
    chapter: Int,
    translation: TranslationId,
    segments: List<ScriptureAudioSegment>,
    chapterVerseCount: Int,
    startIndex: Int = 0,
    preserveRepeatConfig: Boolean = false,
) {
    ReaderVerseTtsLooper.stop()
    val start = startIndex.coerceAtLeast(0)
    if (segments.isEmpty() || start >= segments.size) {
        clearReaderAudioSegmentQueue()
        return
    }
    if (!preserveRepeatConfig) {
        val firstSeg = segments.first()
        val lastSeg = segments.last()
        val spec = segments.joinToString(",") { seg ->
            val end = seg.endVerseInclusive
            when {
                end == null -> "${seg.startVerse}-*"
                end == seg.startVerse -> "${seg.startVerse}"
                else -> "${seg.startVerse}-$end"
            }
        }
        BibleAudioPlayer.setVerseRepeatConfig(
            BibleVerseRepeatConfig(
                startVerse = firstSeg.startVerse,
                endVerse = lastSeg.endVerseInclusive ?: chapterVerseCount.coerceAtLeast(firstSeg.startVerse),
                segmentSpec = spec.takeIf { segments.size > 1 },
                chapterVerseCount = chapterVerseCount,
                translationCode = translation.code,
            ),
        )
    }
    readerAudioSegmentQueue = ReaderAudioSegmentQueue(
        appContext = context.applicationContext,
        narrator = narrator,
        bookId = bookId,
        chapter = chapter,
        translation = translation,
        segments = segments,
        chapterVerseCount = chapterVerseCount,
        index = start,
    )
    playQueuedAudioSegment()
}

private fun onQueuedAudioSegmentFinished() {
    val q = readerAudioSegmentQueue ?: return
    q.index++
    if (q.index >= q.segments.size) {
        if (BibleAudioPlayer.repeatMode.value == BibleAudioRepeatMode.VERSES) {
            val target = BibleAudioPlayer.repeatTargetCount.value
            val done = BibleAudioPlayer.incrementRepeatCompletedCount()
            if (target <= 0 || done < target) {
                q.index = 0
                playQueuedAudioSegment()
                return
            } else {
                BibleAudioPlayer.resetRepeatCompletedCount()
            }
        }
        clearReaderAudioSegmentQueue()
        notifyNoteAudioAdvance()
        return
    }
    playQueuedAudioSegment()
}

private fun playQueuedAudioSegment() {
    val q = readerAudioSegmentQueue ?: return
    val seg = q.segments[q.index]
    playReaderChapterAudio(
        context = q.appContext,
        narrator = q.narrator,
        bookId = q.bookId,
        chapter = q.chapter,
        visibleVerse = seg.startVerse,
        translation = q.translation,
        forceVerseStart = true,
        stopAfterVerse = seg.endVerseInclusive,
        playToChapterEnd = seg.endVerseInclusive == null,
        chapterVerseCount = q.chapterVerseCount,
        fromQueue = true,
    )
}

/**
 * Перезапускает текущий выбранный диапазон стихов из [BibleAudioPlayer.verseRepeatConfig],
 * если пользователь включил режим [BibleAudioRepeatMode.VERSES] прямо на панели плеера.
 */
fun restartActiveVerseRepeatIfConfigured(context: Context) {
    val cfg = BibleAudioPlayer.verseRepeatConfig.value ?: return
    val st = BibleAudioPlayer.state.value
    val snap = ReaderChapterAudioContext.snapshot.value
    val bookId = st.bookId.ifBlank { snap.bookId }
    val chapter = if (st.chapter > 0) st.chapter else snap.chapter
    if (bookId.isBlank() || chapter <= 0) return
    val narrator = BibleAudioNarrators.byId(st.narratorId.ifBlank { snap.narratorId })
    val translation = TranslationId.entries.find { it.code == cfg.translationCode } ?: snap.translation
    val verseCount = cfg.chapterVerseCount.takeIf { it > 0 } ?: snap.verses.size.coerceAtLeast(cfg.endVerse)
    startReaderVerseRangeAudio(
        context = context,
        narrator = narrator,
        bookId = bookId,
        chapter = chapter,
        translation = translation,
        startVerse = cfg.startVerse,
        endVerse = cfg.endVerse,
        chapterVerseCount = verseCount,
        repeat = BibleAudioPlayer.repeatMode.value == BibleAudioRepeatMode.VERSES,
        targetLoops = BibleAudioPlayer.repeatTargetCount.value,
        segmentSpec = cfg.segmentSpec,
    )
}

/** Старт/продолжение озвучки главы с таймкода стиха (если есть проект). */
fun playReaderChapterAudio(
    context: Context,
    narrator: AudioNarrator,
    bookId: String,
    chapter: Int,
    visibleVerse: Int,
    translation: TranslationId,
    forceVerseStart: Boolean = false,
    stopAfterVerse: Int? = null,
    playToChapterEnd: Boolean = false,
    chapterVerseCount: Int = 0,
    fromQueue: Boolean = false,
) {
    ReaderVerseTtsLooper.stop()
    if (onNoteAudioAdvance != null) armNoteAudioAdvance()
    if (!fromQueue) {
        clearReaderAudioSegmentQueue()
        if (stopAfterVerse != null && !playToChapterEnd) {
            BibleAudioPlayer.setVerseRepeatConfig(
                BibleVerseRepeatConfig(
                    startVerse = visibleVerse.coerceAtLeast(1),
                    endVerse = stopAfterVerse.coerceAtLeast(visibleVerse),
                    segmentSpec = null,
                    chapterVerseCount = chapterVerseCount,
                    translationCode = translation.code,
                ),
            )
        } else if (BibleAudioPlayer.repeatMode.value != BibleAudioRepeatMode.VERSES) {
            BibleAudioPlayer.setVerseRepeatConfig(null)
        }
    }
    val timemark = TimemarkStore.findProjectMatchingNarration(
        context,
        translation.code,
        bookId,
        chapter,
        narrator.id,
    )
    val knownDurationMs = BibleAudioPlayer.state.value.durationMs
    val rawStartMs = timemarkPlaybackStartMs(timemark, visibleVerse)
        ?: estimatedPlaybackStartMs(visibleVerse, chapterVerseCount, knownDurationMs)
        ?: if (forceVerseStart && visibleVerse <= 1) 0 else null
    val startOffset = if (fromQueue || stopAfterVerse != null) {
        BibleAudioPlayer.segmentLoopStartOffsetMs.value
    } else {
        0
    }
    val startMs = rawStartMs?.let { raw ->
        val withOffset = raw + startOffset
        if (knownDurationMs > 0) withOffset.coerceIn(0, knownDurationMs) else withOffset.coerceAtLeast(0)
    }

    val st = BibleAudioPlayer.state.value
    val isThisChapter = st.bookId == bookId &&
        st.chapter == chapter &&
        st.narratorId == narrator.id

    if (!playToChapterEnd && stopAfterVerse != null) {
        val timemarkStop = timemarkPlaybackStopAfterMs(
            timemark,
            stopAfterVerse,
            chapterDurationMs = st.durationMs.takeIf { it > 0 },
        )
        BibleAudioPlayer.applySegmentStop(stopAfterVerse, chapterVerseCount, timemarkStop)
    } else {
        BibleAudioPlayer.clearSegmentStop()
    }

    val preserveSegmentStop = !playToChapterEnd && stopAfterVerse != null
    if (forceVerseStart) {
        BibleAudioPlayer.playChapter(
            context,
            narrator,
            bookId,
            chapter,
            startPositionMs = startMs,
            startVerse = visibleVerse,
            chapterVerseCount = chapterVerseCount,
            preserveSegmentStop = preserveSegmentStop,
        )
        bindPlaybackEnd(fromQueue, preserveSegmentStop)
        return
    }
    when {
        isThisChapter && st.isPlaying -> BibleAudioPlayer.togglePlay()
        isThisChapter -> {
            val seek = startMs ?: estimatedPlaybackStartMs(visibleVerse, chapterVerseCount, st.durationMs)
            if (seek != null && visibleVerse > 1) {
                BibleAudioPlayer.seekTo(seek)
            }
            BibleAudioPlayer.togglePlay()
        }
        else -> BibleAudioPlayer.playChapter(
            context,
            narrator,
            bookId,
            chapter,
            startPositionMs = startMs,
            startVerse = visibleVerse,
            chapterVerseCount = chapterVerseCount,
            preserveSegmentStop = preserveSegmentStop,
        )
    }
    bindPlaybackEnd(fromQueue, preserveSegmentStop)
}

private fun bindPlaybackEnd(fromQueue: Boolean, hasSingleSegmentStop: Boolean = false) {
    when {
        fromQueue -> BibleAudioPlayer.setSegmentStopListener { onQueuedAudioSegmentFinished() }
        hasSingleSegmentStop && BibleAudioPlayer.repeatMode.value == BibleAudioRepeatMode.VERSES -> {
            BibleAudioPlayer.setSegmentStopListener {
                val target = BibleAudioPlayer.repeatTargetCount.value
                val done = BibleAudioPlayer.incrementRepeatCompletedCount()
                if (target <= 0 || done < target) {
                    appContextForSingleRepeat()?.let { ctx ->
                        restartActiveVerseRepeatIfConfigured(ctx)
                    }
                } else {
                    BibleAudioPlayer.resetRepeatCompletedCount()
                }
            }
        }
        onNoteAudioAdvance != null -> BibleAudioPlayer.setSegmentStopListener { notifyNoteAudioAdvance() }
    }
}

private fun appContextForSingleRepeat(): Context? =
    readerAudioSegmentQueue?.appContext
