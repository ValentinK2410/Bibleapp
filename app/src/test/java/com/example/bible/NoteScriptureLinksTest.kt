package com.example.bible

import com.example.bible.data.NoteScriptureLinks
import com.example.bible.data.ParsedScriptureNavigation
import com.example.bible.data.ScriptureAudioNavigation
import com.example.bible.data.ScriptureAudioPlayMode
import com.example.bible.data.TranslationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteScriptureLinksTest {

    @Test
    fun findsFirstJohnSynCopyFormat() {
        val text = "1 Иоанна 1:2\nжизнь была явлена"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertEquals("1_john", links[0].bookId)
        assertEquals(1, links[0].chapter)
        assertEquals(setOf(2), links[0].verses)
        assertEquals("1 Иоанна 1:2", text.substring(links[0].start, links[0].end))
    }

    @Test
    fun findsFirstSamuelSynCopyFormat() {
        val text = "1 Царств 3:10 и Господь пришёл"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertEquals("1_samuel", links[0].bookId)
        assertEquals(3, links[0].chapter)
        assertEquals(setOf(10), links[0].verses)
    }

    @Test
    fun findsSpacedAbbrNumberedBook() {
        val text = "1 Ин 1:2 жизнь была явлена"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertEquals("1_john", links[0].bookId)
        assertEquals(setOf(2), links[0].verses)
    }

    @Test
    fun findsIsaiahVerseWithTrailingPeriod() {
        val text = "Исаия 41:3. Он гонит их, идет спокойно"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertEquals("isaiah", links[0].bookId)
        assertEquals(41, links[0].chapter)
        assertEquals(setOf(3), links[0].verses)
        assertEquals("Исаия 41:3.", text.substring(links[0].start, links[0].end))
        assertFalse(links[0].isAudioLink)
    }

    @Test
    fun findsVerseRange() {
        val text = "См. Исаия 4:3-4 для контекста"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertEquals(setOf(3, 4), links[0].verses)
        assertEquals("Исаия 4:3-4", text.substring(links[0].start, links[0].end))
    }

    @Test
    fun findsCommaSeparatedVerses() {
        val text = "Исаия 41:3,5,9 и далее текст"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertEquals(setOf(3, 5, 9), links[0].verses)
        assertEquals("Исаия 41:3,5,9", text.substring(links[0].start, links[0].end))
    }

    @Test
    fun findsMultipleReferences() {
        val text = "См. Исаия 41:3 и Быт 1:1"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(2, links.size)
        assertEquals("isaiah", links[0].bookId)
        assertEquals("genesis", links[1].bookId)
        assertTrue(links[0].start < links[1].start)
    }

    @Test
    fun navigationAnnotationRoundTrip() {
        val encoded = NoteScriptureLinks.formatNavigationAnnotation("isaiah", 41, setOf(3, 5, 9))
        val parsed = NoteScriptureLinks.parseNavigationAnnotation(encoded)
        assertNotNull(parsed)
        assertEquals("isaiah", parsed!!.bookId)
        assertEquals(41, parsed.chapter)
        assertEquals(setOf(3, 5, 9), parsed.verses)
        assertEquals(null, parsed.audio)
    }

    @Test
    fun audioNavigationAnnotationRoundTrip() {
        val encoded = NoteScriptureLinks.formatNavigationAnnotation(
            "romans",
            6,
            setOf(22, 23),
            audio = ScriptureAudioNavigation(
                mode = ScriptureAudioPlayMode.RANGE,
                translationCode = "rbo",
                segmentSpec = "22-23",
            ),
        )
        val parsed = NoteScriptureLinks.parseNavigationAnnotation(encoded)
        assertNotNull(parsed)
        assertEquals("romans", parsed!!.bookId)
        assertEquals(6, parsed.chapter)
        assertEquals(setOf(22, 23), parsed.verses)
        assertEquals(ScriptureAudioPlayMode.RANGE, parsed.audio!!.mode)
        assertEquals("rbo", parsed.audio!!.translationCode)
        assertEquals("22-23", parsed.audio!!.segmentSpec)
    }

    @Test
    fun findsAudioVerseLinkWithTranslation() {
        val text = "Слушайте: 🔊 Рим 6:22@RBO и размышляйте"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertTrue(links[0].isAudioLink)
        assertEquals("romans", links[0].bookId)
        assertEquals(6, links[0].chapter)
        assertEquals(setOf(22), links[0].verses)
        assertEquals(ScriptureAudioPlayMode.VERSE, links[0].audioPlayMode)
        assertEquals("RBO", links[0].translationCode)
    }

    @Test
    fun findsAudioRangeLink() {
        val text = "🔊 Рим 6:22-23@RBO"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertTrue(links[0].isAudioLink)
        assertEquals(setOf(22, 23), links[0].verses)
        assertEquals(ScriptureAudioPlayMode.RANGE, links[0].audioPlayMode)
    }

    @Test
    fun findsAudioToChapterEndLink() {
        val text = "🔊 Рим 6:22→@RBO"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertTrue(links[0].isAudioLink)
        assertEquals(ScriptureAudioPlayMode.TO_CHAPTER_END, links[0].audioPlayMode)
        assertEquals(setOf(22), links[0].verses)
    }

    @Test
    fun findsAudioSegmentLinkWithStar() {
        val text = "🔊 Рим 6:1-3,8,11-12,*@RBO"
        val links = NoteScriptureLinks.findInText(text)
        assertEquals(1, links.size)
        assertTrue(links[0].isAudioLink)
        assertEquals(ScriptureAudioPlayMode.SEGMENTS, links[0].audioPlayMode)
        assertEquals("1-3,8,11-12,*", links[0].segmentSpec)
        assertEquals(4, NoteScriptureLinks.segmentCountForNavigation(links[0].toParsedNavigation()))
    }

    @Test
    fun parseSegmentSpec_withStar() {
        val segments = NoteScriptureLinks.parseSegmentSpec("1-3,8,11-12,*", 25)
        assertEquals(4, segments.size)
        assertEquals(1, segments[0].startVerse)
        assertEquals(3, segments[0].endVerseInclusive)
        assertEquals(8, segments[1].startVerse)
        assertEquals(8, segments[1].endVerseInclusive)
        assertEquals(11, segments[2].startVerse)
        assertEquals(12, segments[2].endVerseInclusive)
        assertEquals(13, segments[3].startVerse)
        assertEquals(null, segments[3].endVerseInclusive)
    }

    @Test
    fun allAudioSegments_commaListPlaysEachVerse() {
        val nav = NoteScriptureLinks.findInText("🔊 Мат 3:7,9,10@RBO").first().toParsedNavigation()
        val segments = NoteScriptureLinks.allAudioSegmentsForNavigation(nav, 17)
        assertEquals(2, segments.size)
        assertEquals(7, segments[0].startVerse)
        assertEquals(7, segments[0].endVerseInclusive)
        assertEquals(9, segments[1].startVerse)
        assertEquals(10, segments[1].endVerseInclusive)
        assertEquals(setOf(7, 9, 10), nav.verses)
        assertTrue(NoteScriptureLinks.shouldAutoPlayAllSegments(nav.audio!!.mode))
    }

    @Test
    fun allAudioSegments_adjacentCommaVersesAreOneRange() {
        val nav = NoteScriptureLinks.findInText("🔊 2Фес 3:12,13@BTI").first().toParsedNavigation()
        val segments = NoteScriptureLinks.allAudioSegmentsForNavigation(nav, 18)
        assertEquals(1, segments.size)
        assertEquals(12, segments[0].startVerse)
        assertEquals(13, segments[0].endVerseInclusive)
        assertEquals(setOf(12, 13), nav.verses)
        assertEquals("2_thessalonians", nav.bookId)
    }

    @Test
    fun allAudioSegments_rangeIsSingleContinuousSegment() {
        val nav = NoteScriptureLinks.findInText("🔊 Рим 6:22-25@RBO").first().toParsedNavigation()
        val segments = NoteScriptureLinks.allAudioSegmentsForNavigation(nav, 23)
        assertEquals(1, segments.size)
        assertEquals(22, segments[0].startVerse)
        assertEquals(25, segments[0].endVerseInclusive)
        assertEquals(setOf(22, 23, 24, 25), nav.verses)
    }

    @Test
    fun resolveAudioSegment_stopsAfterSingleVerse() {
        val nav = ParsedScriptureNavigation(
            bookId = "romans",
            chapter = 6,
            verses = setOf(22),
            audio = ScriptureAudioNavigation(ScriptureAudioPlayMode.VERSE, "RBO"),
        )
        val segment = NoteScriptureLinks.resolveAudioSegment(nav, 0, 25)
        assertEquals(22, segment.startVerse)
        assertEquals(22, segment.endVerseInclusive)
    }

    @Test
    fun formatAudioLinkWithVerseTexts_appendsNumberedText() {
        val out = NoteScriptureLinks.formatAudioLinkWithVerseTexts(
            bookId = "matthew",
            chapter = 3,
            verses = setOf(7, 9),
            mode = ScriptureAudioPlayMode.SEGMENTS,
            translation = TranslationId.RBO,
            verseTextsByNumber = mapOf(
                7 to "Змеиное отродье!",
                9 to "и не думайте говорить себе",
            ),
            chapterVerseCount = 17,
            segmentSpec = "7,9",
        )
        assertTrue(out.startsWith("🔊 Мат 3:7,9@RBO"))
        assertTrue(out.contains("7. Змеиное отродье!"))
        assertTrue(out.contains("9. и не думайте говорить себе"))
    }

    @Test
    fun formatAudioLinkTextRoundTrip() {
        val link = NoteScriptureLinks.formatAudioLinkText(
            bookId = "romans",
            chapter = 6,
            verses = setOf(22),
            mode = ScriptureAudioPlayMode.VERSE,
            translation = TranslationId.RBO,
        )
        assertTrue(link.contains("🔊"))
        assertTrue(link.contains("@RBO"))
        val links = NoteScriptureLinks.findInText(link)
        assertEquals(1, links.size)
        assertTrue(links[0].isAudioLink)
        assertEquals("romans", links[0].bookId)
    }

    @Test
    fun showFullChapterForToEndAudio() {
        val nav = NoteScriptureLinks.parseNavigationAnnotation(
            NoteScriptureLinks.formatNavigationAnnotation(
                "romans",
                6,
                setOf(22),
                audio = ScriptureAudioNavigation(
                    ScriptureAudioPlayMode.TO_CHAPTER_END,
                    "RBO",
                    segmentSpec = "22",
                ),
            ),
        )!!
        assertTrue(NoteScriptureLinks.showFullChapterForAudio(nav))
    }
}
