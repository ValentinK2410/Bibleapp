package com.example.bible

import com.example.bible.data.TimemarkCue
import com.example.bible.data.timeMsForTimemarkVerse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimemarkVerseSyncTest {

    private val cues = listOf(
        TimemarkCue(timeMs = 10_000, verseStart = 1),
        TimemarkCue(timeMs = 60_000, verseStart = 5, verseEnd = 6),
        TimemarkCue(timeMs = 120_000, verseStart = 10),
    )

    @Test
    fun exactVerseStart_returnsCueTime() {
        assertEquals(120_000L, timeMsForTimemarkVerse(10, cues))
    }

    @Test
    fun verseInsideRange_returnsRangeStartTime() {
        assertEquals(60_000L, timeMsForTimemarkVerse(6, cues))
    }

    @Test
    fun verseAfterLastMark_returnsNearestPrevious() {
        assertEquals(120_000L, timeMsForTimemarkVerse(12, cues))
    }

    @Test
    fun stopAfterVerse_usesNextVerseStart() {
        val project = com.example.bible.data.TimemarkProject(
            translationCode = "RBO",
            bookId = "romans",
            chapter = 6,
            title = "test",
            audioFilePath = "test.mp3",
            cues = cues,
        )
        assertEquals(120_000, com.example.bible.data.timemarkPlaybackStopAfterMs(project, 6))
    }
}
