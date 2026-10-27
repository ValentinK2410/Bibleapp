package com.example.bible

import com.example.bible.data.BibleCanon
import com.example.bible.data.KidsBibleAge
import com.example.bible.data.KidsBibleCatalog
import com.example.bible.data.KidsBibleNarration
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
        val ids = stories.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf(
                "gen_creation",
                "gen_fall",
                "gen_noah",
                "gen_abraham",
                "exodus_sea",
                "sam_goliath",
                "jonah",
                "luke_nativity",
                "luke_prodigal",
                "luke_easter",
                "psalms_overview",
                "romans_overview",
            ),
            ids.take(12),
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
        assertEquals(
            "Я полагаю радугу Мою в облаке, чтоб она была знамением [вечного] завета между Мною и между землею.",
            byId.getValue("gen_noah").keyVerse,
        )
        assertEquals(
            "Авраам сказал: Бог усмотрит Себе агнца для всесожжения, сын мой. И шли далее оба вместе.",
            byId.getValue("gen_abraham").keyVerse,
        )
        assertEquals("Господь будет поборать за вас, а вы будьте спокойны.", byId.getValue("exodus_sea").keyVerse)
        assertEquals("И сказал Господь киту, и он изверг Иону на сушу.", byId.getValue("jonah").keyVerse)
        assertTrue(byId.getValue("luke_easter").keyVerse.startsWith("Его нет здесь: Он воскрес"))
        if ("mt_storm" in byId) {
            assertEquals(
                "И говорит им: что вы так боязливы, маловерные? Потом, встав, запретил ветрам и морю, и сделалась великая тишина.",
                byId.getValue("mt_storm").keyVerse,
            )
        }
        if ("eph_grace" in byId) {
            assertEquals(
                "Ибо благодатью вы спасены через веру, и сие не от вас, Божий дар:",
                byId.getValue("eph_grace").keyVerse,
            )
        }
        stories.forEach { story ->
            assertEquals(KidsBibleAge.entries.toSet(), story.tracks.keys)
            assertTrue(story.images.isNotEmpty())
            assertEquals(story.art, story.images.first())
            KidsBibleAge.entries.forEach { age ->
                val track = story.track(age)
                assertTrue(track.paragraphs.isNotEmpty())
                assertTrue(track.meaning.isNotBlank())
                assertTrue(track.christLink.isNotBlank())
                assertTrue(track.questions.isNotEmpty())
                assertTrue(track.prayer.isNotBlank())
                assertTrue(track.activity.isNotBlank())
                val audio = story.audioAsset(age)
                val audioFile = listOf(File("src/main/assets/$audio"), File("app/src/main/assets/$audio")).firstOrNull { it.exists() }
                val timingName = "audio/${story.id}_${age.id}.timings.json"
                val timingFile = listOf(
                    File("src/main/assets/kids_bible/$timingName"),
                    File("app/src/main/assets/kids_bible/$timingName"),
                ).firstOrNull { it.exists() }
                if (audioFile != null) {
                    val cues = KidsBibleCatalog.parseCues(timingFile!!.readText())
                    val parts = cues.map { it.part }
                    track.paragraphs.indices.forEach { index -> assertTrue(parts.contains("p$index")) }
                    assertTrue(parts.containsAll(listOf("verse", "meaning", "christ", "prayer")))
                    cues.zipWithNext().forEach { (a, b) -> assertTrue(a.endMs <= b.startMs) }
                } else {
                    assertTrue(timingFile == null)
                }
                assertEquals("kids_bible/audio/family/${story.id}_${age.id}.m4a", story.familyAudioAsset(age))
                assertEquals(story.audioAsset(age), story.narrationAsset(age, KidsBibleNarration.App))
            }
            assertTrue(story.track(KidsBibleAge.Y5_7).paragraphs.sumOf { it.length } < story.track(KidsBibleAge.Y8_10).paragraphs.sumOf { it.length })
        }
        ids.drop(12).forEach { id ->
            KidsBibleAge.entries.forEach { age ->
                val audio = "kids_bible/audio/${id}_${age.id}.m4a"
                assertFalse(File("src/main/assets/$audio").exists() || File("app/src/main/assets/$audio").exists())
            }
        }
        val shelf = KidsBibleCatalog.shelf(stories)
        assertEquals(66, shelf.size)
        assertEquals(BibleCanon.allBooks.map { it.id }, shelf.map { it.book.id })
        assertFalse(shelf.first { it.book.id == "genesis" }.comingSoon)
        assertEquals(4, shelf.first { it.book.id == "genesis" }.stories.size)
        assertFalse(shelf.first { it.book.id == "exodus" }.comingSoon)
        assertEquals(stories.none { it.bookId == "daniel" }, shelf.first { it.book.id == "daniel" }.comingSoon)
        listOf(
            "matthew", "mark", "john", "acts", "james", "1_peter", "2_peter", "1_john", "2_john", "3_john",
            "jude", "1_corinthians", "2_corinthians", "galatians", "ephesians", "philippians", "colossians",
            "1_thessalonians", "2_thessalonians", "1_timothy", "2_timothy", "titus", "philemon", "hebrews", "revelation",
        ).forEach { bookId ->
            assertFalse(shelf.first { it.book.id == bookId }.comingSoon)
        }
        assertEquals(
            listOf("luke_nativity", "luke_prodigal", "luke_easter"),
            shelf.first { it.book.id == "luke" }.stories.map { it.id },
        )
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
