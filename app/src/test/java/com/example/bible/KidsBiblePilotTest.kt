package com.example.bible

import com.example.bible.data.BibleCanon
import com.example.bible.data.KidsBibleCatalog
import com.example.bible.data.KidsBiblePin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KidsBiblePilotTest {

    private fun asset(name: String): String {
        val file = listOf(
            File("src/main/assets/kids_bible/$name"),
            File("app/src/main/assets/kids_bible/$name"),
        ).first { it.exists() }
        return file.readText()
    }

    @Test
    fun pilotStoriesMatchSynodalVersesAndShelfKeepsAllBooks() {
        val stories = KidsBibleCatalog.parseStories(asset("stories.json"))
        assertEquals(
            listOf(
                "gen_creation",
                "gen_fall",
                "luke_nativity",
                "sam_goliath",
                "luke_prodigal",
                "psalms_overview",
                "romans_overview",
            ),
            stories.map { it.id },
        )
        val byId = stories.associateBy { it.id }
        assertEquals("В начале сотворил Бог небо и землю.", byId.getValue("gen_creation").keyVerse)
        assertEquals("Бытие 1:1", byId.getValue("gen_creation").keyVerseRef)
        assertTrue(byId.getValue("gen_fall").keyVerse.contains("оно будет поражать тебя в голову"))
        assertEquals(
            "А Давид отвечал Филистимлянину: ты идешь против меня с мечом и копьем и щитом, а я иду против тебя во имя Господа Саваофа, Бога воинств Израильских, которые ты поносил;",
            byId.getValue("sam_goliath").keyVerse,
        )
        assertEquals(
            "Встал и пошел к отцу своему. И когда он был еще далеко, увидел его отец его и сжалился; и, побежав, пал ему на шею и целовал его.",
            byId.getValue("luke_prodigal").keyVerse,
        )
        assertEquals("Господь - Пастырь мой; я ни в чем не буду нуждаться:", byId.getValue("psalms_overview").keyVerse)
        assertTrue(byId.getValue("romans_overview").keyVerse.startsWith("Ибо я не стыжусь благовествования Христова"))
        stories.forEach { story ->
            assertTrue(story.paragraphs.isNotEmpty())
            assertTrue(story.meaning.isNotBlank())
            assertTrue(story.christLink.isNotBlank())
            assertTrue(story.forOlder.isNotBlank())
            assertTrue(story.questions.isNotEmpty())
            assertTrue(story.prayer.isNotBlank())
            assertTrue(story.activity.isNotBlank())
            val cues = KidsBibleCatalog.parseCues(asset("audio/${story.id}.timings.json"))
            val parts = cues.map { it.part }
            story.paragraphs.indices.forEach { index -> assertTrue(parts.contains("p$index")) }
            assertTrue(parts.containsAll(listOf("verse", "meaning", "christ", "prayer")))
            cues.zipWithNext().forEach { (a, b) -> assertTrue(a.endMs <= b.startMs) }
            assertTrue(File("src/main/assets/${story.audioAsset}").exists() || File("app/src/main/assets/${story.audioAsset}").exists())
        }
        val shelf = KidsBibleCatalog.shelf(stories)
        assertEquals(66, shelf.size)
        assertEquals(BibleCanon.allBooks.map { it.id }, shelf.map { it.book.id })
        assertFalse(shelf.first { it.book.id == "genesis" }.comingSoon)
        assertEquals(2, shelf.first { it.book.id == "genesis" }.stories.size)
        assertTrue(shelf.first { it.book.id == "exodus" }.comingSoon)
        assertEquals(listOf("luke_nativity", "luke_prodigal"), shelf.first { it.book.id == "luke" }.stories.map { it.id })
    }

    @Test
    fun pinCannotBeSkippedAndLocksAfterRepeatedMisses() {
        assertEquals(KidsBiblePin.SetupProblem.PIN, KidsBiblePin.setupProblem("12", "12", "бабушка", "анна"))
        assertEquals(KidsBiblePin.SetupProblem.MISMATCH, KidsBiblePin.setupProblem("1234", "1235", "бабушка", "анна"))
        assertEquals(KidsBiblePin.SetupProblem.HINT, KidsBiblePin.setupProblem("1234", "1234", " ", "анна"))
        assertNull(KidsBiblePin.setupProblem("1234", "1234", "Как зовут бабушку", "Анна"))
        val record = KidsBiblePin.create("1234", "Как зовут бабушку", "Анна", salt = "salt")
        assertTrue(KidsBiblePin.matchesPin(record, "1234"))
        assertFalse(KidsBiblePin.matchesPin(record, "0000"))
        assertTrue(KidsBiblePin.matchesHint(record, " анна "))
        assertFalse(KidsBiblePin.matchesHint(record, "мария"))
        var gate = KidsBiblePin.Gate()
        repeat(4) { gate = KidsBiblePin.fail(gate, now = 1_000L) }
        assertFalse(KidsBiblePin.locked(gate, 1_000L))
        gate = KidsBiblePin.fail(gate, now = 1_000L)
        assertTrue(KidsBiblePin.locked(gate, 1_000L))
        assertFalse(KidsBiblePin.locked(gate, 1_000L + KidsBiblePin.LOCK_MS))
        assertEquals(gate, KidsBiblePin.fail(gate, now = 1_500L))
    }
}
