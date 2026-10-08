package com.example.bible

import com.example.bible.data.BibleCoverage
import com.example.bible.data.TranslationId
import com.example.bible.data.TimemarkCue
import com.example.bible.data.versesHeardByEstimate
import com.example.bible.data.versesHeardInRanges
import org.junit.Assert.assertEquals
import org.junit.Test

class VersesHeardTest {
    @Test
    fun tenSeconds_marksOnlyVersesInsideThePlayedRange() {
        val cues = listOf(
            TimemarkCue(timeMs = 0L, verseStart = 1),
            TimemarkCue(timeMs = 8_000L, verseStart = 2),
            TimemarkCue(timeMs = 20_000L, verseStart = 3),
        )
        assertEquals(listOf(1, 2), versesHeardInRanges(cues, listOf(0L to 10_000L)))
    }

    @Test
    fun interrupt_keepsTheHeardSpanAndIgnoresASeekJump() {
        val cues = listOf(
            TimemarkCue(timeMs = 0L, verseStart = 1),
            TimemarkCue(timeMs = 5_000L, verseStart = 2),
            TimemarkCue(timeMs = 40_000L, verseStart = 3),
        )
        val heard = versesHeardInRanges(
            cues,
            listOf(0L to 6_000L, 40_000L to 41_000L),
        )
        assertEquals(listOf(1, 2, 3), heard)
    }

    @Test
    fun withoutCues_evenSplitUsesPlaybackPosition() {
        val heard = versesHeardByEstimate(
            verseCount = 4,
            durationMs = 40_000,
            ranges = listOf(0L to 10_000L),
        )
        assertEquals(listOf(1), heard)
    }

    @Test
    fun partialChapter_countsVersesForTheBookGrid() {
        val keys = setOf("SYN|titus|1|3-5")
        assertEquals(mapOf(1 to 3), BibleCoverage.listenedVersesByChapter(keys, "SYN", "titus"))
    }

    @Test
    fun homeTile_showsPartialVersesBesideFinishedChapters() {
        val tiles = BibleCoverage.homeTiles(
            translation = TranslationId.SYNODAL,
            readKeys = setOf("SYN|titus|1"),
            listenChapterKeys = emptySet(),
            listenVerseKeys = setOf("SYN|titus|1|3-5"),
        )
        val titus = tiles.getValue("titus")
        assertEquals(1, titus.readChapters)
        assertEquals(0, titus.listenedChapters)
        assertEquals(3, titus.listenedVerses)
        assertEquals(1, titus.listenedOpenChapters)
    }
}
