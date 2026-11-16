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
import com.example.bible.data.VerseRef
import com.example.bible.data.applyTtsVoiceForTranslation
import com.example.bible.data.estimatedPlaybackStartMs
import com.example.bible.data.remapTtsPlaylist
import com.example.bible.data.timemarkPlaybackStartMs
import com.example.bible.data.timemarkPlaybackStopAfterMs
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Пауза после остановки свайпа, чтобы серия быстрых свайпов озвучила только последний перевод. */
internal const val TTS_TRANSLATION_SWIPE_SETTLE_MS = 350L

/**
 * Снимок озвучки на момент свайпа: с какого стиха продолжить и какой был режим повтора.
 */
internal data class TtsTranslationHandoff(
    val token: Int,
    val bookId: String,
    val chapter: Int,
    val anchorVerse: Int,
    val selectedVerseNumbers: List<Int>,
    val repeatEnabled: Boolean,
    val targetLoops: Int,
    val completedLoops: Int,
    /** Если ни один номер отрывка не нашёлся — читать от ближайшего стиха до конца главы. */
    val continueToChapterEndIfUnmapped: Boolean,
    val rangeLabelRu: String,
)

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
internal object ReaderVerseTtsLooper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var currentEnginePkg: String = ""
    /** Какая реплика сейчас в движке. Чужие onDone (после stop) очередь не двигают. */
    private var activeUtteranceId: String? = null
    private var ttsEngineEpoch = 0
    private var handoffGeneration = 0
    private var pendingHandoffToken: Int? = null

    private val _state = MutableStateFlow(ReaderVerseTtsLoopState())
    val state: StateFlow<ReaderVerseTtsLoopState> = _state.asStateFlow()

    /**
     * Свайп на другой перевод, пока стихи читаются вслух.
     * Сразу глушит текущую очередь и плеер главы. null — озвучка и так молчит
     * (пауза и «не запущено» не трогаем).
     */
    fun beginTranslationSwipe(bookId: String, chapter: Int): TtsTranslationHandoff? {
        val st = _state.value
        if (!st.isActive || !st.isPlaying) return null
        if (st.bookId != bookId || st.chapter != chapter) return null
        return armHandoff(
            TtsTranslationHandoff(
                token = 0,
                bookId = st.bookId,
                chapter = st.chapter,
                anchorVerse = st.currentVerseNumber.coerceAtLeast(1),
                selectedVerseNumbers = st.verses.map { it.number },
                repeatEnabled = st.repeatEnabled,
                targetLoops = st.targetLoops,
                completedLoops = st.completedLoops,
                continueToChapterEndIfUnmapped = st.verses.size > 1,
                rangeLabelRu = st.rangeLabelRu,
            ),
            pauseLooper = true,
        )
    }

    /** То же для разового стиха или цепочки подстрочника, которую вёл не этот лупер. */
    fun beginInlineTranslationSwipe(speech: ReaderInlineSpeech): TtsTranslationHandoff {
        return armHandoff(
            TtsTranslationHandoff(
                token = 0,
                bookId = speech.bookId,
                chapter = speech.chapter,
                anchorVerse = speech.anchorVerse.coerceAtLeast(1),
                selectedVerseNumbers = speech.verseNumbers.ifEmpty { listOf(speech.anchorVerse.coerceAtLeast(1)) },
                repeatEnabled = false,
                targetLoops = 0,
                completedLoops = 0,
                continueToChapterEndIfUnmapped = speech.continueToChapterEndIfUnmapped,
                rangeLabelRu = "",
            ),
            pauseLooper = false,
        )
    }

    /**
     * Запускает чтение [translation] с якорного стиха. false — жест уже устарел
     * (новый свайп, стоп) или в главе нечего читать.
     */
    fun resumeAfterTranslationSwipe(
        context: Context,
        handoff: TtsTranslationHandoff,
        translation: TranslationId,
        targetVerses: List<BibleVerse>,
    ): Boolean {
        if (handoff.token != pendingHandoffToken) return false
        pendingHandoffToken = null
        ReaderInlineVerseSpeech.suppress(false)
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = handoff.selectedVerseNumbers,
            anchorVerse = handoff.anchorVerse,
            targetVerses = targetVerses,
            continueToChapterEndIfUnmapped = handoff.continueToChapterEndIfUnmapped,
        )
        if (mapped == null) {
            if (_state.value.isActive && _state.value.bookId == handoff.bookId && _state.value.chapter == handoff.chapter) {
                _state.value = _state.value.copy(isPlaying = false)
            }
            return false
        }
        val mappedNumbers = mapped.verses.map { it.number }
        val label = if (
            handoff.rangeLabelRu.isNotBlank() &&
            mappedNumbers == handoff.selectedVerseNumbers
        ) {
            handoff.rangeLabelRu
        } else {
            val first = mapped.verses.first().number
            val last = mapped.verses.last().number
            if (first == last) "ст. $first" else "ст. $first–$last"
        }
        _state.value = ReaderVerseTtsLoopState(
            isActive = true,
            isPlaying = true,
            bookId = handoff.bookId,
            chapter = handoff.chapter,
            translation = translation,
            verses = mapped.verses,
            currentVerseIndex = mapped.startIndex.coerceIn(0, mapped.verses.lastIndex),
            repeatEnabled = handoff.repeatEnabled,
            targetLoops = handoff.targetLoops,
            completedLoops = handoff.completedLoops,
            rangeLabelRu = label,
        )
        ensureTtsAndSpeak(context.applicationContext)
        return true
    }

    /** Уход с экрана, пока новый перевод ещё не начал говорить: автозапуск отменяем. */
    fun abandonTranslationSwipe() {
        if (pendingHandoffToken == null) return
        pendingHandoffToken = null
        activeUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        ReaderInlineVerseSpeech.suppress(false)
    }

    private fun armHandoff(draft: TtsTranslationHandoff, pauseLooper: Boolean): TtsTranslationHandoff {
        activeUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        clearReaderAudioSegmentQueue()
        BibleAudioPlayer.stopForNavigation()
        ReaderInlineVerseSpeech.stopEngines()
        ReaderInlineVerseSpeech.suppress(true)
        val token = ++handoffGeneration
        pendingHandoffToken = token
        if (pauseLooper) {
            _state.value = _state.value.copy(isPlaying = false)
        }
        return draft.copy(token = token)
    }

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
        pendingHandoffToken = null
        activeUtteranceId = null
        ReaderInlineVerseSpeech.suppress(false)
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
        if (pendingHandoffToken != null) {
            abandonTranslationSwipe()
            _state.value = st.copy(isPlaying = false)
            return
        }
        if (st.isPlaying) {
            activeUtteranceId = null
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
        if (pendingHandoffToken != null) return
        val nextIdx = (st.currentVerseIndex + delta).coerceIn(0, st.verses.lastIndex)
        activeUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        _state.value = st.copy(currentVerseIndex = nextIdx, isPlaying = true)
        ensureTtsAndSpeak(context.applicationContext)
    }

    fun stop() {
        pendingHandoffToken = null
        activeUtteranceId = null
        ReaderInlineVerseSpeech.suppress(false)
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
        val epoch = ++ttsEngineEpoch
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
                val engine = engineRef
                if (epoch != ttsEngineEpoch || !_state.value.isPlaying) {
                    if (tts !== engine) {
                        runCatching {
                            engine?.stop()
                            engine?.shutdown()
                        }
                    }
                    return@post
                }
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    tts = engine
                    ttsReady = true
                    attachProgressListener(engine)
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
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (!interrupted) return
                mainHandler.post {
                    if (utteranceId != null && utteranceId == activeUtteranceId) {
                        activeUtteranceId = null
                    }
                }
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
        activeUtteranceId = utteranceId
        engine.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun onVerseUtteranceCompleted(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        activeUtteranceId = null
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

internal enum class ReaderInlineSpeechKind {
    ONCE,
    SEQUENCE,
}

/** Разовая озвучка стиха или цепочка подстрочника — чтобы свайп знал, с какого стиха продолжить. */
internal data class ReaderInlineSpeech(
    val bookId: String,
    val chapter: Int,
    val anchorVerse: Int,
    val translation: TranslationId,
    val verseNumbers: List<Int>,
    val continueToChapterEndIfUnmapped: Boolean,
    val kind: ReaderInlineSpeechKind,
    val generation: Int,
)

/**
 * Движки TTS страниц читалки (стих из меню и подстрочник). Лупер их не держит,
 * поэтому при смене перевода их нужно остановить отдельно, иначе очереди наложатся.
 */
internal object ReaderInlineVerseSpeech {
    private val stops = CopyOnWriteArrayList<() -> Unit>()
    private var snapshot: ReaderInlineSpeech? = null
    private var generation = 0
    private var suppressed = false

    fun generationNow(): Int = generation

    fun noteOnce(ref: VerseRef) {
        if (suppressed) return
        val verse = ref.verse.coerceAtLeast(1)
        generation++
        snapshot = ReaderInlineSpeech(
            bookId = ref.bookId,
            chapter = ref.chapter,
            anchorVerse = verse,
            translation = ref.translation,
            verseNumbers = listOf(verse),
            continueToChapterEndIfUnmapped = false,
            kind = ReaderInlineSpeechKind.ONCE,
            generation = generation,
        )
    }

    fun noteSequence(
        bookId: String,
        chapter: Int,
        anchorVerse: Int,
        translation: TranslationId,
        verseNumbers: List<Int>,
    ) {
        if (suppressed) return
        val verse = anchorVerse.coerceAtLeast(1)
        val numbers = verseNumbers.ifEmpty { listOf(verse) }
        generation++
        snapshot = ReaderInlineSpeech(
            bookId = bookId,
            chapter = chapter,
            anchorVerse = verse,
            translation = translation,
            verseNumbers = numbers,
            continueToChapterEndIfUnmapped = true,
            kind = ReaderInlineSpeechKind.SEQUENCE,
            generation = generation,
        )
    }

    fun onUtteranceFinished(finishedGeneration: Int) {
        val current = snapshot ?: return
        if (current.kind == ReaderInlineSpeechKind.ONCE && current.generation == finishedGeneration) {
            snapshot = null
        }
    }

    fun clearSequence(translation: TranslationId) {
        val current = snapshot ?: return
        if (current.kind == ReaderInlineSpeechKind.SEQUENCE && current.translation == translation) {
            snapshot = null
        }
    }

    fun consume(bookId: String, chapter: Int): ReaderInlineSpeech? {
        val current = snapshot ?: return null
        if (current.bookId != bookId || current.chapter != chapter) return null
        snapshot = null
        return current
    }

    fun suppress(on: Boolean) {
        suppressed = on
        if (on) snapshot = null
    }

    fun registerStop(block: () -> Unit): () -> Unit {
        stops.add(block)
        return { stops.remove(block) }
    }

    fun stopEngines() {
        for (stop in stops) {
            runCatching { stop() }
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
