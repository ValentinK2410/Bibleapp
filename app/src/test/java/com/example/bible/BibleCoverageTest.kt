package com.example.bible

import com.example.bible.data.BibleCoverage
import com.example.bible.data.TranslationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleCoverageTest {
    @Test
    fun narratorMapsToTranslationTrack() {
        assertEquals(TranslationId.SYNODAL.code, BibleCoverage.trackForNarrator("bondarenko"))
        assertEquals(TranslationId.SYNODAL.code, BibleCoverage.trackForNarrator("kozlov"))
        assertEquals(TranslationId.NRT.code, BibleCoverage.trackForNarrator("new-russian"))
        assertEquals(BibleCoverage.HEBREW, BibleCoverage.trackForNarrator("hebrew-ot"))
        assertEquals(BibleCoverage.GREEK, BibleCoverage.trackForNarrator("greek-nt"))
    }

    @Test
    fun countsOnlyChaptersOfTheSelectedTrack() {
        val read = setOf(
            BibleCoverage.chapterKey(TranslationId.SYNODAL.code, "jonah", 1),
            BibleCoverage.chapterKey(TranslationId.SYNODAL.code, "jonah", 2),
            BibleCoverage.chapterKey(TranslationId.NRT.code, "jonah", 1),
            BibleCoverage.chapterKey(TranslationId.SYNODAL.code, "jonah", 99),
        )
        val progress = BibleCoverage.progressForTrack(TranslationId.SYNODAL.code, read, emptySet())
        val jonah = progress.first { it.book.id == "jonah" }
        assertEquals(2, jonah.read)
        assertEquals(0, jonah.listened)
        assertFalse(jonah.readComplete)
        val nrt = BibleCoverage.progressForTrack(TranslationId.NRT.code, read, emptySet())
            .first { it.book.id == "jonah" }
        assertEquals(1, nrt.read)
    }

    @Test
    fun hebrewTrackSkipsNewTestament() {
        val books = BibleCoverage.booksFor(BibleCoverage.HEBREW)
        assertTrue(books.all { it.id != "matthew" })
        assertTrue(books.any { it.id == "genesis" })
        val greek = BibleCoverage.booksFor(BibleCoverage.GREEK)
        assertTrue(greek.none { it.id == "genesis" })
        assertTrue(greek.any { it.id == "matthew" })
    }

    @Test
    fun firstMissingChapterSkipsMarkedOnes() {
        val book = BibleCoverage.progressForTrack(
            TranslationId.SYNODAL.code,
            setOf(
                BibleCoverage.chapterKey(TranslationId.SYNODAL.code, "obadiah", 1),
            ),
            emptySet(),
        ).first { it.book.id == "obadiah" }
        assertTrue(book.readComplete)
        val jonah = BibleCoverage.progressForTrack(
            TranslationId.SYNODAL.code,
            setOf(BibleCoverage.chapterKey(TranslationId.SYNODAL.code, "jonah", 1)),
            emptySet(),
        ).first { it.book.id == "jonah" }
        val marked = BibleCoverage.chaptersMarked(
            setOf(BibleCoverage.chapterKey(TranslationId.SYNODAL.code, "jonah", 1)),
            TranslationId.SYNODAL.code,
            "jonah",
        )
        assertEquals(2, jonah.firstMissing(marked, wantRead = true))
    }

    @Test
    fun verseSpansRoundTripAndMergeOnce() {
        assertEquals("1-3,5,8-9", BibleCoverage.encodeVerseSpans(listOf(9, 1, 2, 3, 5, 8)))
        assertEquals(setOf(1, 2, 3, 5, 8, 9), BibleCoverage.decodeVerseSpans("1-3,5,8-9"))
        val keys = mutableSetOf<String>()
        assertTrue(BibleCoverage.mergeVerseRecord(keys, "SYN", "john", 1, listOf(1, 2)))
        assertFalse(BibleCoverage.mergeVerseRecord(keys, "SYN", "john", 1, listOf(2)))
        assertEquals(1, keys.size)
        assertTrue(keys.single().endsWith("|1-2"))
    }

    @Test
    fun verseMarksKeepEachTranslationColorTrack() {
        val read = mutableSetOf<String>()
        BibleCoverage.mergeVerseRecord(read, TranslationId.SYNODAL.code, "john", 1, listOf(3))
        BibleCoverage.mergeVerseRecord(read, TranslationId.NRT.code, "john", 1, listOf(3, 4))
        val listen = mutableSetOf<String>()
        BibleCoverage.mergeVerseRecord(listen, TranslationId.SYNODAL.code, "john", 1, listOf(3))
        val marks = BibleCoverage.marksForChapter(read, listen, "john", 1)
        val syn = marks[3]!!.first { it.trackId == TranslationId.SYNODAL.code }
        assertTrue(syn.read && syn.listened)
        val nrt = marks[3]!!.first { it.trackId == TranslationId.NRT.code }
        assertTrue(nrt.read && !nrt.listened)
        assertEquals(TranslationId.NRT.code, marks[4]!!.single().trackId)
        assertTrue(BibleCoverage.markColorArgb(TranslationId.SYNODAL.code) != BibleCoverage.markColorArgb(TranslationId.NRT.code))
        assertEquals(0xFF00FF00.toInt(), BibleCoverage.markColorArgb(TranslationId.SYNODAL.code, mapOf(TranslationId.SYNODAL.code to 0xFF00FF00.toInt())))
    }

    @Test
    fun interlinearListenTrackFollowsTestament() {
        assertEquals(BibleCoverage.HEBREW, BibleCoverage.listenTrackFor(TranslationId.INTERLINEAR, "genesis"))
        assertEquals(BibleCoverage.GREEK, BibleCoverage.listenTrackFor(TranslationId.INTERLINEAR, "john"))
        assertEquals(100, BibleCoverage.percent(5, 5))
        assertEquals(0, BibleCoverage.percent(0, 5))
    }
}
