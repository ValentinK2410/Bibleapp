package com.example.bible

import com.example.bible.data.BibleVerse
import com.example.bible.data.InterlinearWord
import com.example.bible.data.remapTtsPlaylist
import com.example.bible.data.verseNumberForFlatInterlinearWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsPlaylistMapperTest {

    private fun verses(numbers: List<Int>) = numbers.map { BibleVerse(it, "стих $it") }

    @Test
    fun keepsAnchorAndRestOfSelection() {
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = (1..10).toList(),
            anchorVerse = 4,
            targetVerses = verses((1..10).toList()),
            continueToChapterEndIfUnmapped = true,
        )
        requireNotNull(mapped)
        assertEquals((1..10).toList(), mapped.verses.map { it.number })
        assertEquals(3, mapped.startIndex)
    }

    @Test
    fun missingAnchor_continuesFromNextSelectedVerse() {
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = listOf(5, 6, 7, 8),
            anchorVerse = 6,
            targetVerses = verses(listOf(5, 7, 8)),
            continueToChapterEndIfUnmapped = true,
        )
        requireNotNull(mapped)
        assertEquals(listOf(5, 7, 8), mapped.verses.map { it.number })
        assertEquals(1, mapped.startIndex)
    }

    @Test
    fun tiePrefersVerseAhead() {
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = listOf(3, 5),
            anchorVerse = 4,
            targetVerses = verses(listOf(3, 5)),
            continueToChapterEndIfUnmapped = false,
        )
        requireNotNull(mapped)
        assertEquals(1, mapped.startIndex)
        assertEquals(5, mapped.verses[mapped.startIndex].number)
    }

    @Test
    fun anchorBeforeChapter_startsAtFirstVerse() {
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = listOf(1),
            anchorVerse = 1,
            targetVerses = verses(listOf(3, 4)),
            continueToChapterEndIfUnmapped = false,
        )
        requireNotNull(mapped)
        assertEquals(listOf(3), mapped.verses.map { it.number })
        assertEquals(0, mapped.startIndex)
    }

    @Test
    fun anchorAfterChapter_usesLastVerseWhenSelectionMisses() {
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = listOf(30),
            anchorVerse = 30,
            targetVerses = verses((1..5).toList()),
            continueToChapterEndIfUnmapped = false,
        )
        requireNotNull(mapped)
        assertEquals(listOf(5), mapped.verses.map { it.number })
    }

    @Test
    fun unmappedRange_continuesFromNearestThroughChapterEnd() {
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = listOf(40, 41),
            anchorVerse = 40,
            targetVerses = verses((1..4).toList()),
            continueToChapterEndIfUnmapped = true,
        )
        requireNotNull(mapped)
        assertEquals(listOf(4), mapped.verses.map { it.number })
        assertEquals(0, mapped.startIndex)
    }

    @Test
    fun singleSelectedVerse_isNotExpandedToTheChapter() {
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = listOf(8),
            anchorVerse = 8,
            targetVerses = verses((1..12).toList()),
            continueToChapterEndIfUnmapped = false,
        )
        requireNotNull(mapped)
        assertEquals(listOf(8), mapped.verses.map { it.number })
    }

    @Test
    fun singleMissingNumber_speaksNearestOnly() {
        val mapped = remapTtsPlaylist(
            selectedVerseNumbers = listOf(8),
            anchorVerse = 8,
            targetVerses = verses(listOf(1, 2, 6, 10)),
            continueToChapterEndIfUnmapped = false,
        )
        requireNotNull(mapped)
        assertEquals(listOf(10), mapped.verses.map { it.number })
    }

    @Test
    fun emptyChapter_returnsNull() {
        assertNull(
            remapTtsPlaylist(
                selectedVerseNumbers = listOf(1),
                anchorVerse = 1,
                targetVerses = emptyList(),
                continueToChapterEndIfUnmapped = true,
            ),
        )
    }

    @Test
    fun interlinearWordIndex_mapsToVerse() {
        val word = InterlinearWord("א", "a", "а")
        val verses = listOf(
            BibleVerse(1, "а", interlinearWords = listOf(word, word)),
            BibleVerse(2, "б", interlinearWords = listOf(word)),
            BibleVerse(4, "в", interlinearWords = listOf(word, word, word)),
        )
        assertEquals(1, verseNumberForFlatInterlinearWord(verses, 0))
        assertEquals(1, verseNumberForFlatInterlinearWord(verses, 1))
        assertEquals(2, verseNumberForFlatInterlinearWord(verses, 2))
        assertEquals(4, verseNumberForFlatInterlinearWord(verses, 3))
        assertEquals(4, verseNumberForFlatInterlinearWord(verses, 99))
    }
}
