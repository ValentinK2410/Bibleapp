package com.example.bible

import com.example.bible.data.TranslationId
import com.example.bible.deeplink.BibleDeepLinks
import com.example.bible.deeplink.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleDeepLinkTest {

    @Test
    fun verseCustomScheme() {
        val target = BibleDeepLinks.parse("bible://verse/SYN/john/3/16")
        assertEquals("read/john/3/16", target.route)
        assertEquals("SYN", target.translationCode)
        assertNull(target.styleKey)
    }

    @Test
    fun verseHttpsWithStyleQuery() {
        val target = BibleDeepLinks.parse(
            "https://bible.example.com/verse/WEB/psalms/23/1?style=starfield",
        )
        assertEquals("read/psalms/23/1", target.route)
        assertEquals("WEB", target.translationCode)
        assertEquals("starfield", target.styleKey)
    }

    @Test
    fun verseKeepsCurrentTranslationWhenOmitted() {
        val target = BibleDeepLinks.parse("bible://verse/john/3/16")
        assertEquals("read/john/3/16", target.route)
        assertNull(target.translationCode)
    }

    @Test
    fun chapterOpensFirstVerse() {
        val target = BibleDeepLinks.parse("bible://chapter/NRT/genesis/1")
        assertEquals("read/genesis/1/1", target.route)
        assertEquals("NRT", target.translationCode)
    }

    @Test
    fun chapterWithoutTranslation() {
        val target = BibleDeepLinks.parse("https://bible.example.com/chapter/1_john/2")
        assertEquals("read/1_john/2/1", target.route)
        assertNull(target.translationCode)
    }

    @Test
    fun httpHostAndTrailingSlash() {
        val target = BibleDeepLinks.parse("http://bible.example.com/verse/RBO/acts/2/4/")
        assertEquals("read/acts/2/4", target.route)
        assertEquals("RBO", target.translationCode)
    }

    @Test
    fun threeSlashAndCase() {
        val target = BibleDeepLinks.parse("bible:///verse/bti/John/3/16?style=PAPYRUS")
        assertEquals("read/john/3/16", target.route)
        assertEquals("BTI", target.translationCode)
        assertEquals("papyrus", target.styleKey)
    }

    @Test
    fun bookByEnglishNameAndRussianAbbr() {
        assertEquals(
            "read/song_of_solomon/1/1",
            BibleDeepLinks.parse("bible://verse/WEB/Song%20of%20Solomon/1/1").route,
        )
        assertEquals(
            "read/john/1/1",
            BibleDeepLinks.parse("bible://verse/SYN/%D0%98%D0%BD/1/1").route,
        )
    }

    @Test
    fun longPsalmVerse() {
        val target = BibleDeepLinks.parse("bible://verse/SYN/psalms/119/176")
        assertEquals("read/psalms/119/176", target.route)
    }

    @Test
    fun sectionSearchBookmarksAndAliases() {
        assertEquals("search", BibleDeepLinks.parse("bible://section/search").route)
        assertEquals("bookmarks", BibleDeepLinks.parse("https://bible.example.com/section/bookmarks").route)
        assertEquals("theme_studio", BibleDeepLinks.parse("bible://section/theme").route)
        assertEquals("timemark_editor", BibleDeepLinks.parse("bible://section/timemark").route)
        assertEquals("my_travels", BibleDeepLinks.parse("bible://section/travel").route)
        assertEquals("app_contacts", BibleDeepLinks.parse("bible://section/contacts").route)
        assertEquals("books", BibleDeepLinks.parse("bible://section/reading").route)
        assertEquals("books", BibleDeepLinks.parse("bible://home").route)
    }

    @Test
    fun kidsSpaceUsesAlbumRoute() {
        assertEquals(
            "kids_album?r=kids_space",
            BibleDeepLinks.parse("bible://section/kids_space").route,
        )
        assertEquals("kids_animals", BibleDeepLinks.parse("bible://section/kids_animals").route)
    }

    @Test
    fun styleLinkOpensThemeStudio() {
        val target = BibleDeepLinks.parse("bible://style/night_garden")
        assertEquals("theme_studio", target.route)
        assertEquals("night_garden", target.styleKey)
        assertEquals(
            Target(route = "theme_studio", translationCode = null, styleKey = "leather"),
            BibleDeepLinks.parse("https://bible.example.com/style/leather"),
        )
    }

    @Test
    fun styleQueryOnSection() {
        val target = BibleDeepLinks.parse("bible://section/notes?style=scroll")
        assertEquals("notes", target.route)
        assertEquals("scroll", target.styleKey)
    }

    @Test
    fun unknownStyleOnVerseIsIgnored() {
        val target = BibleDeepLinks.parse("bible://verse/SYN/john/3/16?style=not-a-style")
        assertEquals("read/john/3/16", target.route)
        assertEquals("SYN", target.translationCode)
        assertNull(target.styleKey)
    }

    @Test
    fun unknownStyleOnlyFallsHome() {
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://style/nope"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("https://bible.example.com/style/"))
    }

    @Test
    fun invalidPassageFallsHome() {
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://verse/NOPE/john/3/16"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://verse/SYN/not_a_book/1/1"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://chapter/SYN/genesis/99"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://verse/SYN/john/3/0"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://verse/SYN/psalms/119/201"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://verse/SYN/obadiah/2/1"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://section/not_a_section"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://nope"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("bible://"))
    }

    @Test
    fun invalidLinkStillAppliesKnownStyle() {
        val target = BibleDeepLinks.parse("bible://section/missing?style=meadow")
        assertEquals("books", target.route)
        assertEquals("meadow", target.styleKey)
    }

    @Test
    fun foreignHostIsNotAnAppLink() {
        assertFalse(BibleDeepLinks.isAppLink("https://evil.example/verse/SYN/john/3/16"))
        assertEquals(Target.HOME, BibleDeepLinks.parse("https://evil.example/verse/SYN/john/3/16"))
        assertFalse(BibleDeepLinks.isAppLink("https://bible.example.com.evil/verse/SYN/john/3/16"))
        assertTrue(BibleDeepLinks.isAppLink("bible://verse/SYN/john/3/16"))
        assertFalse(BibleDeepLinks.isAppLink(null))
        assertFalse(BibleDeepLinks.isAppLink("content://file/a.zip"))
    }

    @Test
    fun configurableHost() {
        val raw = "https://bible.my-domain.test/chapter/PODSTR/genesis/1?style=steel"
        assertFalse(BibleDeepLinks.isAppLink(raw))
        assertTrue(BibleDeepLinks.isAppLink(raw, httpsHost = "bible.my-domain.test"))
        val target = BibleDeepLinks.parse(raw, httpsHost = "bible.my-domain.test")
        assertEquals("read/genesis/1/1", target.route)
        assertEquals("PODSTR", target.translationCode)
        assertEquals("steel", target.styleKey)
    }

    @Test
    fun urlsRoundTrip() {
        val verse = BibleDeepLinks.verseUrl(
            translation = TranslationId.SYNODAL,
            bookId = "John",
            chapter = 3,
            verse = 16,
            styleKey = "Velvet",
            https = false,
        )
        assertEquals("bible://verse/SYN/john/3/16?style=velvet", verse)
        assertEquals(
            Target(route = "read/john/3/16", translationCode = "SYN", styleKey = "velvet"),
            BibleDeepLinks.parse(verse),
        )
        val https = BibleDeepLinks.verseUrl(TranslationId.WEB, "psalms", 23, 1)
        assertEquals("https://bible.example.com/verse/WEB/psalms/23/1", https)
        assertEquals(
            "https://bible.example.com/chapter/SYN/matthew/5",
            BibleDeepLinks.chapterUrl(TranslationId.SYNODAL, "matthew", 5),
        )
        assertEquals(
            "bible://section/search",
            BibleDeepLinks.sectionUrl("search", https = false),
        )
        assertEquals(
            "https://bible.example.com/style/papyrus",
            BibleDeepLinks.styleUrl("papyrus"),
        )
    }

    @Test
    fun shareTextContainsBothLinks() {
        val text = BibleDeepLinks.appendToShareText(
            body = "От Иоанна 3:16\nИбо так возлюбил",
            translation = TranslationId.SYNODAL,
            bookId = "john",
            chapter = 3,
            verse = 16,
        )
        assertTrue(text.startsWith("От Иоанна 3:16\nИбо так возлюбил\n\n"))
        assertTrue(text.contains("https://bible.example.com/verse/SYN/john/3/16"))
        assertTrue(text.contains("bible://verse/SYN/john/3/16"))
    }

    @Test
    fun requestFromPrefersLinkOverStartRouteExtra() {
        val fromLink = BibleDeepLinks.requestFrom(
            dataString = "bible://section/search",
            startRouteExtra = "notes",
            nonce = 7,
        )
        assertEquals("search", fromLink?.route)
        assertEquals(7L, fromLink?.nonce)
        val fromExtra = BibleDeepLinks.requestFrom(
            dataString = null,
            startRouteExtra = "read/john/1/1",
            nonce = 3,
        )
        assertEquals("read/john/1/1", fromExtra?.route)
        assertNull(fromExtra?.translationCode)
        assertNull(
            BibleDeepLinks.requestFrom(
                dataString = "https://example.com",
                startRouteExtra = "  ",
                nonce = 1,
            ),
        )
    }
}
