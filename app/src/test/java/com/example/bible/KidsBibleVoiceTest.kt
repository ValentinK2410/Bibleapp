package com.example.bible

import com.example.bible.data.KidsBibleCatalog
import com.example.bible.data.KidsBibleVoice
import com.example.bible.data.KidsVoiceKind
import com.example.bible.data.kidsClipAt
import com.example.bible.data.kidsGlobalMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class KidsBibleVoiceTest {

    @Test
    fun customFileBeatsPartsAndPartsBeatBundledTrack() {
        val root = Files.createTempDirectory("kids-voice").toFile()
        assertEquals(KidsVoiceKind.SOON, KidsBibleVoice.plan(root, "jonah", "8_10", 2, null).kind)
        assertEquals(
            KidsVoiceKind.BUNDLED,
            KidsBibleVoice.plan(root, "jonah", "8_10", 2, "kids_bible/audio/jonah_8_10.m4a").kind,
        )

        val part = KidsBibleVoice.partFile(root, "jonah", "5_7", "p0")!!
        KidsBibleVoice.place(scratch(root, "part"), part)
        val parts = KidsBibleVoice.plan(root, "jonah", "5_7", 2, "kids_bible/audio/jonah_5_7.m4a")
        assertEquals(KidsVoiceKind.CUSTOM_PARTS, parts.kind)
        assertEquals(listOf("p0"), parts.parts.map { it.partId })

        val whole = KidsBibleVoice.wholeFile(root, "jonah", "5_7")!!
        KidsBibleVoice.place(scratch(root, "whole"), whole)
        assertTrue(whole.parentFile!!.resolve("5_7").isDirectory)
        assertEquals("5_7.m4a", whole.name)
        assertEquals(KidsVoiceKind.CUSTOM_WHOLE, KidsBibleVoice.plan(root, "jonah", "5_7", 2, "asset").kind)

        KidsBibleVoice.deleteWhole(root, "jonah", "5_7")
        assertEquals(KidsVoiceKind.CUSTOM_PARTS, KidsBibleVoice.plan(root, "jonah", "5_7", 2, "asset").kind)
        KidsBibleVoice.deleteAge(root, "jonah", "5_7")
        assertEquals(KidsVoiceKind.BUNDLED, KidsBibleVoice.plan(root, "jonah", "5_7", 2, "asset").kind)
        assertFalse(part.exists())
    }

    @Test
    fun unsafeIdsDoNotLeaveTheVoiceFolder() {
        val root = Files.createTempDirectory("kids-voice").toFile()
        assertNull(KidsBibleVoice.wholeFile(root, "../jonah", "5_7"))
        assertNull(KidsBibleVoice.partFile(root, "jonah", "5_7", "p0/../../x"))
        assertNull(KidsBibleVoice.safeId("5-7"))
        KidsBibleVoice.putNote(root, "jonah", "8_10", "whole", "mic")
        KidsBibleVoice.putNote(root, "jonah", "8_10", "p0", "url")
        assertEquals(listOf("whole", "p0"), KidsBibleVoice.notes(root, "jonah").map { it.part })
        KidsBibleVoice.dropNotes(root, "jonah", "8_10", "whole")
        assertEquals(listOf("p0"), KidsBibleVoice.notes(root, "jonah").map { it.part })
    }

    @Test
    fun sliderMapsAPositionOntoTheRightClip() {
        val durations = intArrayOf(1000, 2000)
        assertEquals(0 to 500, kidsClipAt(durations, 500))
        assertEquals(1 to 0, kidsClipAt(durations, 1000))
        assertEquals(1 to 2000, kidsClipAt(durations, 99_000))
        assertEquals(1500, kidsGlobalMs(durations, 1, 500))
    }

    @Test
    fun storyImagesStayOptional() {
        val raw = """
            {"stories":[{
              "id":"sample","bookId":"matthew","order":1,"title":"Буря","refs":"Мф 8",
              "kind":"story","keyVerse":"стих","keyVerseRef":"Мф 8:26","art":"kids_bible_sample",
              "images":["kids_bible_sample","kids_bible_sample_s2"],
              "tracks":{
                "5_7":{"paragraphs":["а"],"meaning":"м","christLink":"х","questions":["в"],"prayer":"п","activity":"д"},
                "8_10":{"paragraphs":["абзац длиннее"],"meaning":"м","christLink":"х","questions":["в"],"prayer":"п","activity":"д"},
                "11_13":{"paragraphs":["абзац длиннее","ещё"],"meaning":"м","christLink":"х","questions":["в"],"prayer":"п","activity":"д"}
              }
            }]}
        """.trimIndent()
        val story = KidsBibleCatalog.parseStories(raw).single()
        assertEquals(listOf("kids_bible_sample", "kids_bible_sample_s2"), story.images)
        val bare = raw.replace(""""images":["kids_bible_sample","kids_bible_sample_s2"],""", "")
        assertTrue(KidsBibleCatalog.parseStories(bare).single().images.isEmpty())
    }

    private fun scratch(root: java.io.File, name: String): java.io.File =
        java.io.File(root, "$name.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
}
