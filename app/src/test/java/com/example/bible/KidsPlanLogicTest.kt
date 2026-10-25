package com.example.bible

import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsAssetPlaceholders
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
    fun storyFramesArePresentAndSongsStayPlaceholders() {
        assertTrue(KidsAssetPlaceholders.missingStoryFrames.isEmpty())
        assertEquals(8, KidsAssetPlaceholders.songAudio.size)
        val drawable = listOf(
            File("src/main/res/drawable"),
            File("app/src/main/res/drawable"),
        ).first { it.isDirectory }
        val fresh = KidsBibleStories.all.filter { it.stage > 0 }
        assertEquals(30, fresh.size)
        fresh.forEach { story ->
            assertTrue(story.id, File(drawable, "kids_story_${story.id}.webp").exists())
            story.plannedArtFiles().forEach { name ->
                assertTrue(name, File(drawable, name).exists())
            }
        }
    }

    @Test
    fun coloringOutlinesLoadSmallAndDetailed() {
        val root = listOf(
            File("src/main/assets/kids/coloring"),
            File("app/src/main/assets/kids/coloring"),
        ).first { it.isDirectory }
        val files = root.listFiles { file -> file.extension == "json" }!!.sortedBy { it.name }
        assertEquals(6, files.size)
        assertEquals(12, root.listFiles { file -> file.extension == "svg" }!!.size)
        files.forEach { file ->
            val raw = file.readText()
            assertTrue(file.name, raw.contains("\"format\": \"kids-coloring/v1\""))
            val small = raw.substringAfter("\"small\"").substringBefore("\"detailed\"")
            val detailed = raw.substringAfter("\"detailed\"")
            val smallRegions = Regex("\"d\"\\s*:").findAll(small).count()
            val detailedRegions = Regex("\"d\"\\s*:").findAll(detailed).count()
            assertTrue(file.name, smallRegions > 0)
            assertTrue("$file detailed=$detailedRegions small=$smallRegions", detailedRegions > smallRegions)
        }
        val sheep = files.first { it.name == "kids_coloring_lost_sheep_1.json" }.readText()
        assertTrue(sheep.contains("\"id\": \"sheep_1\""))
        assertTrue(sheep.contains("kids_coloring_lost_sheep_1.json"))
    }

    @Test
    fun verseChunksKeepWords() {
        val tokens = verseTokens("Господь Пастырь мой")
        assertEquals(listOf("Господь Пастырь", "мой"), verseChunks(tokens, 2))
    }
}
