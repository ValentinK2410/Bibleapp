package com.example.bible.ui

import android.content.Context
import com.example.bible.data.AudioNarrator
import com.example.bible.data.BibleAudioPlayer
import com.example.bible.data.ScriptureAudioSegment
import com.example.bible.data.TimemarkStore
import com.example.bible.data.TranslationId
import com.example.bible.data.timemarkPlaybackStartMs
import com.example.bible.data.timemarkPlaybackStopAfterMs
import com.example.bible.data.estimatedPlaybackStartMs

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

fun clearReaderAudioSegmentQueue() {
    readerAudioSegmentQueue = null
    BibleAudioPlayer.setSegmentStopListener(null)
}

fun stopReaderChapterAudio() {
    clearReaderAudioSegmentQueue()
    BibleAudioPlayer.stopForNavigation()
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
) {
    val start = startIndex.coerceAtLeast(0)
    if (segments.isEmpty() || start >= segments.size) {
        clearReaderAudioSegmentQueue()
        return
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
    BibleAudioPlayer.setSegmentStopListener { onQueuedAudioSegmentFinished() }
    playQueuedAudioSegment()
}

private fun onQueuedAudioSegmentFinished() {
    val q = readerAudioSegmentQueue ?: return
    q.index++
    if (q.index >= q.segments.size) {
        clearReaderAudioSegmentQueue()
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
    if (!fromQueue) {
        clearReaderAudioSegmentQueue()
    }
    val timemark = TimemarkStore.findProjectMatchingNarration(
        context,
        translation.code,
        bookId,
        chapter,
        narrator.id,
    )
    val knownDurationMs = BibleAudioPlayer.state.value.durationMs
    val startMs = timemarkPlaybackStartMs(timemark, visibleVerse)
        ?: estimatedPlaybackStartMs(visibleVerse, chapterVerseCount, knownDurationMs)
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
            preserveSegmentStop = preserveSegmentStop,
        )
        return
    }
    when {
        isThisChapter && st.isPlaying -> BibleAudioPlayer.togglePlay()
        isThisChapter -> {
            if (startMs != null) {
                BibleAudioPlayer.seekTo(startMs)
            }
            BibleAudioPlayer.togglePlay()
        }
        else -> BibleAudioPlayer.playChapter(
            context,
            narrator,
            bookId,
            chapter,
            startPositionMs = startMs,
            preserveSegmentStop = preserveSegmentStop,
        )
    }
}
