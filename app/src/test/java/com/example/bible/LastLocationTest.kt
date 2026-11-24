package com.example.bible

import com.example.bible.data.LastLocation
import com.example.bible.data.LastLocationSession
import com.example.bible.data.LastLocationStore
import com.example.bible.data.ReadingAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastLocationTest {
    @Test
    fun jsonRoundTripKeepsRouteArgsAndVerse() {
        val original = LastLocation.capture(
            routePattern = "read/{bookId}/{chapter}/{scrollVerse}",
            args = mapOf("bookId" to "john", "chapter" to "3", "scrollVerse" to "16"),
            translationCode = "SYN",
        )!!
        val restored = LastLocation.fromJson(original.toJson())
        assertEquals(original, restored)
        assertEquals("john", restored!!.reading?.bookId)
        assertEquals(3, restored.reading?.chapter)
        assertEquals(16, restored.reading?.verse)
        assertEquals("SYN", restored.reading?.translationCode)
        assertEquals("read/john/3/16", restored.navigableRoute())
    }

    @Test
    fun blankAndBrokenJsonAreIgnored() {
        assertNull(LastLocation.fromJson(""))
        assertNull(LastLocation.fromJson("   "))
        assertNull(LastLocation.fromJson("not-json"))
        assertNull(LastLocation.fromJson("""{"args":{}}"""))
        assertNull(LastLocation.fromJson("""{"route":"books"}"""))
    }

    @Test
    fun homeAndChapterRedirectsAreNotRecorded() {
        assertFalse(LastLocation.shouldRecord("books"))
        assertNull(
            LastLocation.capture("books", emptyMap(), "SYN"),
        )
        assertNull(
            LastLocation.capture(
                "chapters/{bookId}",
                mapOf("bookId" to "gen"),
                "SYN",
            ),
        )
        assertNull(
            LastLocation.capture(
                "verses/{bookId}/{chapter}",
                mapOf("bookId" to "gen", "chapter" to "1"),
                "SYN",
            ),
        )
        assertTrue(LastLocation.shouldRecord("kids_dino"))
        assertTrue(LastLocation.shouldRecord("my_note/{noteId}?fresh={fresh}"))
        assertTrue(LastLocation.shouldRecord("house_care"))
        assertTrue(LastLocation.shouldRecord("note_edit/{noteId}"))
    }

    @Test
    fun queryAndPathArgumentsAreEncoded() {
        val note = LastLocation.capture(
            "my_note/{noteId}?fresh={fresh}",
            mapOf("noteId" to "a/b c", "fresh" to "true"),
            "",
        )!!
        assertEquals("my_note/a%2Fb%20c?fresh=true", note.navigableRoute())

        val hymn = LastLocation.capture(
            "songs_pv_hymn/{hymnId}",
            mapOf("hymnId" to "псалмы/1"),
            "",
        )!!
        assertEquals(
            "songs_pv_hymn/%D0%BF%D1%81%D0%B0%D0%BB%D0%BC%D1%8B%2F1",
            hymn.navigableRoute(),
        )
    }

    @Test
    fun missingPathArgumentCannotBeOpened() {
        assertNull(
            LastLocation.capture(
                "note_edit/{noteId}",
                emptyMap(),
                "",
            ),
        )
    }

    @Test
    fun mostRecentScreenWinsAndHomeDoesNotClearIt() {
        val session = LastLocationSession()
        session.onDestination("kids", place("kids"))
        session.onDestination("game", place("kids_dino"))
        session.onDestination("notes", place("my_notes"))
        val note = LastLocation.capture(
            "my_note/{noteId}?fresh={fresh}",
            mapOf("noteId" to "n1", "fresh" to "false"),
            "",
        )
        session.onDestination("note", note)
        assertEquals("my_note/n1?fresh=false", session.current?.navigableRoute())
        val changed = session.onDestination("books", null)
        assertNull(changed)
        assertEquals("my_note/n1?fresh=false", session.current?.navigableRoute())
    }

    @Test
    fun scrolledVerseIsDebouncedAndOpensThatVerseNotChapterStart() {
        val session = LastLocationSession()
        val opened = LastLocation.capture(
            "read/{bookId}/{chapter}/{scrollVerse}",
            mapOf("bookId" to "john", "chapter" to "3", "scrollVerse" to "0"),
            "SYN",
        )!!
        session.onDestination("read-1", opened)
        assertEquals("read/john/3/0", session.current?.navigableRoute())
        assertNull(session.current?.reading)

        assertTrue(session.noteReading(anchor("john", 3, 16), nowMs = 1_000))
        assertTrue(session.noteReading(anchor("john", 3, 17), nowMs = 1_200))
        assertEquals("read/john/3/0", session.current?.navigableRoute())
        assertNull(session.flushDue(1_200 + LastLocationSession.DEBOUNCE_MS - 1))

        val saved = session.flushDue(1_200 + LastLocationSession.DEBOUNCE_MS)
        assertEquals(17, saved?.reading?.verse)
        assertEquals("SYN", saved?.reading?.translationCode)
        assertEquals("john", saved?.reading?.bookId)
        assertEquals(3, saved?.reading?.chapter)
        assertEquals("read/john/3/17", saved?.navigableRoute())
        assertTrue(saved!!.restoresGlobalTranslation())
    }

    @Test
    fun flushNowKeepsVerseWhenLeavingReaderBeforeDebounce() {
        val session = LastLocationSession()
        session.onDestination(
            "read-1",
            LastLocation.capture(
                "read/{bookId}/{chapter}/{scrollVerse}",
                mapOf("bookId" to "gen", "chapter" to "1", "scrollVerse" to "1"),
                "NRT",
            ),
        )
        session.noteReading(anchor("gen", 1, 8, translation = "NRT"), nowMs = 50)
        val flushed = session.onDestination("books", null)
        assertEquals(8, flushed?.reading?.verse)
        assertEquals("read/gen/1/8", session.current?.navigableRoute())
    }

    @Test
    fun sameReaderEntryKeepsScrolledVerseOverOriginalRouteArgument() {
        val session = LastLocationSession()
        val opened = LastLocation.capture(
            "read/{bookId}/{chapter}/{scrollVerse}",
            mapOf("bookId" to "john", "chapter" to "3", "scrollVerse" to "1"),
            "SYN",
        )!!
        session.onDestination("read-1", opened)
        session.noteReading(anchor("john", 3, 16), nowMs = 0)
        session.flushNow()
        session.onDestination("search", place("search"))
        assertEquals("search", session.current?.routePattern)

        val back = session.onDestination("read-1", opened)
        assertEquals(16, back?.reading?.verse)
        assertEquals("read/john/3/16", back?.navigableRoute())
    }

    @Test
    fun newReaderNavigationUsesTheRequestedVerse() {
        val session = LastLocationSession()
        session.onDestination(
            "read-1",
            LastLocation.capture(
                "read/{bookId}/{chapter}/{scrollVerse}",
                mapOf("bookId" to "john", "chapter" to "3", "scrollVerse" to "1"),
                "SYN",
            ),
        )
        session.noteReading(anchor("john", 3, 16), nowMs = 0)
        session.flushNow()
        val jumped = session.onDestination(
            "read-2",
            LastLocation.capture(
                "read/{bookId}/{chapter}/{scrollVerse}",
                mapOf("bookId" to "john", "chapter" to "3", "scrollVerse" to "5"),
                "WEB",
            ),
        )
        assertEquals(5, jumped?.reading?.verse)
        assertEquals("WEB", jumped?.translationCode)
        assertEquals("read/john/3/5", jumped?.navigableRoute())
    }

    @Test
    fun readingUpdateIgnoredOnOtherScreens() {
        val session = LastLocationSession()
        session.onDestination("games", place("kids_games"))
        assertFalse(session.noteReading(anchor("john", 3, 16), nowMs = 0))
        assertEquals("kids_games", session.current?.navigableRoute())
        assertNull(session.flushNow())
    }

    @Test
    fun dualVerseAndCompanionSurviveRoundTrip() {
        val session = LastLocationSession()
        session.onDestination(
            "dual-1",
            LastLocation.capture(
                DUAL_PATTERN,
                mapOf("bookId" to "gen", "chapter" to "1"),
                "SYN",
            ),
        )
        val anchor = ReadingAnchor(
            translationCode = "NRT",
            bookId = "john",
            chapter = 3,
            verse = 16,
            companionTranslationCode = "WEB",
            companionBookId = "luk",
            companionChapter = 2,
            companionVerse = 4,
        )
        assertTrue(session.noteReading(anchor, nowMs = 0))
        val saved = session.flushDue(LastLocationSession.DEBOUNCE_MS)
        val route = saved?.navigableRoute().orEmpty()
        assertTrue(route.startsWith("dual?"))
        assertTrue(route.contains("bookId=john"))
        assertTrue(route.contains("chapter=3"))
        assertTrue(route.contains("verse=16"))
        assertTrue(route.contains("primary=NRT"))
        assertTrue(route.contains("companion=WEB"))
        assertTrue(route.contains("cBook=luk"))
        assertTrue(route.contains("cChapter=2"))
        assertTrue(route.contains("cVerse=4"))
        val restored = LastLocation.fromJson(saved!!.toJson())
        assertEquals(saved.reading, restored?.reading)
        assertEquals(route, restored?.navigableRoute())
    }

    @Test
    fun storeSavesAndLoadsVerseRecord() {
        val store = MemoryLastLocationStore()
        val session = LastLocationSession(store.load())
        session.onDestination(
            "read-1",
            LastLocation.capture(
                "read/{bookId}/{chapter}/{scrollVerse}",
                mapOf("bookId" to "psa", "chapter" to "22", "scrollVerse" to "1"),
                "SYN",
            ),
        )?.let(store::save)
        session.noteReading(anchor("psa", 22, 4), nowMs = 10)
        session.flushNow()?.let(store::save)

        val loaded = LastLocationSession(store.load())
        assertEquals("read/psa/22/4", loaded.current?.navigableRoute())
        assertEquals(4, loaded.current?.reading?.verse)
        assertEquals("psa", loaded.current?.reading?.bookId)
        assertEquals(22, loaded.current?.reading?.chapter)
        assertEquals("SYN", loaded.current?.reading?.translationCode)
    }

    private fun place(route: String): LastLocation =
        LastLocation.capture(route, emptyMap(), "")!!

    private fun anchor(
        bookId: String,
        chapter: Int,
        verse: Int,
        translation: String = "SYN",
    ) = ReadingAnchor(
        translationCode = translation,
        bookId = bookId,
        chapter = chapter,
        verse = verse,
    )

    private class MemoryLastLocationStore : LastLocationStore {
        private var raw: String = ""

        override fun load(): LastLocation? = LastLocation.fromJson(raw)

        override fun save(location: LastLocation) {
            raw = location.toJson()
        }
    }

    private companion object {
        const val DUAL_PATTERN =
            "dual?bookId={bookId}&chapter={chapter}&verse={verse}&primary={primary}" +
                "&companion={companion}&cBook={cBook}&cChapter={cChapter}&cVerse={cVerse}"
    }
}
