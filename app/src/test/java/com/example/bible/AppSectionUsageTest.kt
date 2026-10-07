package com.example.bible

import com.example.bible.data.AppSectionUsage
import com.example.bible.data.BibleReadingStats
import com.example.bible.data.CanonBookGroup
import com.example.bible.data.HistoryEntry
import com.example.bible.data.TranslationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSectionUsageTest {
    @Test
    fun routeMapsToSectionAndSkipsHome() {
        assertEquals("reading", AppSectionUsage.sectionIdForRoute("read/{bookId}/{chapter}/{verse}"))
        assertEquals("songs", AppSectionUsage.sectionIdForRoute("songs_pv"))
        assertEquals("kids_games", AppSectionUsage.sectionIdForRoute("kids_checkers"))
        assertEquals("quran", AppSectionUsage.sectionIdForRoute("quran/{surah}/v/{verse}"))
        assertNull(AppSectionUsage.sectionIdForRoute("books"))
    }

    @Test
    fun everyCatalogSectionIsListedEvenWhenUnused() {
        val rows = AppSectionUsage.rows(mapOf("search" to (3 to 10L)))
        assertEquals(AppSectionUsage.catalog.size, rows.size)
        assertEquals("search", rows.first().section.id)
        assertTrue(rows.any { it.section.id == "azbuka" && it.opens == 0 })
        assertEquals(AppSectionUsage.Band.OFTEN, AppSectionUsage.band(3, 3))
        assertEquals(AppSectionUsage.Band.NEVER, AppSectionUsage.band(0, 3))
        assertEquals(AppSectionUsage.Band.RARE, AppSectionUsage.band(1, 10))
    }

    @Test
    fun canonGroupsIncludeUntouchedSections() {
        val stats = BibleReadingStats.build(
            trackId = TranslationId.SYNODAL.code,
            history = listOf(
                HistoryEntry(TranslationId.SYNODAL.code, "john", "Иоанна", 3, 16, 1_000L, dwellSeconds = 120),
            ),
            trace = emptyList(),
            copies = emptyList(),
            listenKeys = emptySet(),
        )
        assertEquals(CanonBookGroup.entries.size, stats.groups.size)
        val gospels = stats.groups.first { it.group == CanonBookGroup.GOSPELS }
        assertEquals(1, gospels.uniqueVerses)
        assertEquals(120, gospels.dwellSeconds)
        assertTrue(stats.groups.any { it.group == CanonBookGroup.REVELATION && it.activity == 0 })
        assertEquals(CanonBookGroup.GOSPELS, stats.groups.first().group)
    }
}
