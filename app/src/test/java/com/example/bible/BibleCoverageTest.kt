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
}
