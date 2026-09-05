package com.example.bible

import com.example.bible.data.MediaLibrarySort
import com.example.bible.data.sortedByMediaLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLibrarySortTest {

    private data class Item(val title: String, val addedAt: Long, val lastPlayedAt: Long = 0)

    private val items = listOf(
        Item("Яблоко", addedAt = 30, lastPlayedAt = 1),
        Item("Абрикос", addedAt = 10, lastPlayedAt = 5),
        Item("Вишня", addedAt = 20, lastPlayedAt = 9),
    )

    private fun sort(mode: MediaLibrarySort) = items.sortedByMediaLibrary(
        sort = mode,
        title = { it.title },
        addedAt = { it.addedAt },
        lastPlayedAt = { it.lastPlayedAt },
    ).map { it.title }

    @Test
    fun newestFirst() {
        assertEquals(listOf("Яблоко", "Вишня", "Абрикос"), sort(MediaLibrarySort.NEWEST))
    }

    @Test
    fun titleAzUsesRussianOrder() {
        assertEquals(listOf("Абрикос", "Вишня", "Яблоко"), sort(MediaLibrarySort.TITLE_AZ))
    }

    @Test
    fun lastPlayedThenNewest() {
        assertEquals(listOf("Вишня", "Абрикос", "Яблоко"), sort(MediaLibrarySort.LAST_PLAYED))
    }
}
