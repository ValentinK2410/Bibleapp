package com.example.bible

import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleCoverage
import com.example.bible.data.PassageActivity
import com.example.bible.data.TranslationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenRepeatCountsTest {

    @Test
    fun listenRepeatCounts_matchesPerBookMaxCount() {
        var json = ""
        json = PassageActivity.bump(json, PassageActivity.LISTEN, "HEB", "genesis", 1, listOf(1), 1L)
        json = PassageActivity.bump(json, PassageActivity.LISTEN, "HEB", "genesis", 1, listOf(1), 2L)
        json = PassageActivity.bump(json, PassageActivity.LISTEN, "GRK", "john", 2, listOf(3), 3L)
        json = PassageActivity.bump(json, PassageActivity.LISTEN, "SYN", "genesis", 1, listOf(1), 4L)
        json = PassageActivity.bump(json, PassageActivity.LISTEN, "SYN", "exodus", 4, listOf(1, 2, 3), 5L)
        json = PassageActivity.bump(json, PassageActivity.READ, "SYN", "exodus", 4, listOf(1), 6L)
        json = "$json;nope;L|SYN|genesis|x|1=9|1;L|SYN|psalms|1|2=4|9;L|SYN|psalms|1|3=0|1"

        assertTrue(BibleCoverage.listenRepeatCounts("", TranslationId.SYNODAL).isEmpty())
        assertTrue(BibleCoverage.listenRepeatCounts(json, null).isEmpty())

        for (translation in listOf(TranslationId.SYNODAL, TranslationId.INTERLINEAR, TranslationId.NRT)) {
            val counts = BibleCoverage.listenRepeatCounts(json, translation)
            for (book in BibleCanon.allBooks) {
                val expected = PassageActivity.maxCount(
                    json,
                    PassageActivity.LISTEN,
                    BibleCoverage.listenTrackFor(translation, book.id),
                    book.id,
                )
                assertEquals(book.id + " " + translation.code, expected, counts[book.id] ?: 0)
            }
        }
    }

    @Test
    fun listenRepeatCounts_interlinearUsesOriginalLanguageTrack() {
        val json = PassageActivity.bump("", PassageActivity.LISTEN, "HEB", "genesis", 1, listOf(1), 1L) +
            ";" + PassageActivity.bump("", PassageActivity.LISTEN, "GRK", "john", 1, listOf(1), 1L)
        val counts = BibleCoverage.listenRepeatCounts(json, TranslationId.INTERLINEAR)
        assertEquals(1, counts["genesis"])
        assertEquals(1, counts["john"])
        assertEquals(null, BibleCoverage.listenRepeatCounts(json, TranslationId.SYNODAL)["genesis"])
    }

    @Test
    fun listenRepeatCounts_duplicateVerseKeepsLastCount() {
        val json = "L|SYN|genesis|1|1=5|1;L|SYN|genesis|1|1=2|2;L|SYN|genesis|1|2=3|3"
        assertEquals(3, BibleCoverage.listenRepeatCounts(json, TranslationId.SYNODAL)["genesis"])
        assertEquals(
            PassageActivity.maxCount(json, PassageActivity.LISTEN, "SYN", "genesis"),
            BibleCoverage.listenRepeatCounts(json, TranslationId.SYNODAL)["genesis"],
        )
    }
}
