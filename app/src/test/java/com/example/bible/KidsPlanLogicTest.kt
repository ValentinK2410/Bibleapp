package com.example.bible

import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsMemoryVerses
import com.example.bible.data.gameIsSubstringOfFull
import com.example.bible.data.orthodoxEaster
import com.example.bible.data.plannedArtFiles
import com.example.bible.data.scenesFor
import com.example.bible.data.suggestedKidsAgeMode
import com.example.bible.data.verseChunks
import com.example.bible.data.verseTokens
import com.example.bible.data.westernEaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class KidsPlanLogicTest {
    @Test
    fun fortyOneUniqueStories() {
        val stories = KidsBibleStories.all
        assertEquals(41, stories.size)
        assertEquals(stories.size, stories.map { it.id }.distinct().size)
    }

    @Test
    fun easterHasSixAndFourScenes() {
        val easter = KidsBibleStories.byId("easter")!!
        assertEquals(6, easter.scenesFor(KidsAgeMode.BIG).size)
        assertEquals(4, easter.scenesFor(KidsAgeMode.SMALL).size)
        assertEquals(12, easter.plannedArtFiles().size)
    }

    @Test
    fun newStoriesHaveTextAndQuestions() {
        val fresh = KidsBibleStories.all.filter { it.stage > 0 }
        assertEquals(30, fresh.size)
        fresh.forEach { story ->
            assertTrue(story.id, story.paragraphs.all { it.isNotBlank() })
            assertTrue(story.id, story.quiz.isNotEmpty())
        }
    }

    @Test
    fun gameFragmentsSitInsideFullVerses() {
        KidsMemoryVerses.all.forEach { verse ->
            assertTrue(verse.id, verse.gameIsSubstringOfFull())
        }
    }

    @Test
    fun gameFragmentsMatchSynodalAssetsWhenPresent() {
        val root = listOf(
            File("src/main/assets/bible/SYN"),
            File("app/src/main/assets/bible/SYN"),
        ).firstOrNull { it.isDirectory } ?: return
        KidsMemoryVerses.all.forEach { verse ->
            val file = File(root, "${verse.bookId}.json")
            assertTrue(file.path, file.exists())
            val raw = file.readText()
            assertTrue(verse.id, raw.contains(verse.fullText))
        }
    }

    @Test
    fun easterDates2026() {
        assertEquals(LocalDate.of(2026, 4, 5), westernEaster(2026))
        assertEquals(LocalDate.of(2026, 4, 12), orthodoxEaster(2026))
    }

    @Test
    fun ageSuggestsMode() {
        assertEquals(KidsAgeMode.SMALL, suggestedKidsAgeMode(3))
        assertEquals(KidsAgeMode.SMALL, suggestedKidsAgeMode(5))
        assertEquals(KidsAgeMode.BIG, suggestedKidsAgeMode(6))
        assertEquals(KidsAgeMode.BIG, suggestedKidsAgeMode(10))
    }

    @Test
    fun verseChunksKeepWords() {
        val tokens = verseTokens("Господь Пастырь мой")
        assertEquals(listOf("Господь Пастырь", "мой"), verseChunks(tokens, 2))
    }
}
