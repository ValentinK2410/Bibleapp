package com.example.bible

import com.example.bible.games.RedBallDraft
import com.example.bible.games.skins.RedBallSkin
import com.example.bible.games.skins.RedBallSkinId
import com.example.bible.games.skins.RedBallSkinSlot
import com.example.bible.games.skins.RedBallSkins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class RedBallSkinTest {

    @Test
    fun greenHillsIsTheDefaultAndClassicStaysProcedural() {
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.DEFAULT)
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.fromStored(null))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.fromStored(""))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.fromStored("нет-такого"))
        assertEquals(RedBallSkinId.CLASSIC, RedBallSkinId.fromStored("classic"))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.fromStored("green_hills"))
        assertTrue(RedBallSkinId.CLASSIC.procedural)
        assertFalse(RedBallSkinId.GREEN_HILLS.procedural)
        assertEquals(null, RedBallSkins.chapter(RedBallSkinId.CLASSIC))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkins.selectable.first())
    }

    @Test
    fun everySlotIsMappedToARealPng() {
        val banned = listOf("src/raw", "src/2x", "src/4x", "preview")
        val assets = redballAssets()
        assertTrue("главы со спрайтами пусты", RedBallSkins.chapters.isNotEmpty())
        val roots = HashSet<String>()
        for (chapter in RedBallSkins.chapters) {
            assertTrue("корень главы повторяется: ${chapter.assetRoot}", roots.add(chapter.assetRoot))
            assertFalse("${chapter.id} не должна быть процедурной", chapter.id.procedural)
            assertEquals("у ${chapter.title} не закрыты слоты: ${chapter.missingSlots()}", emptyList<RedBallSkinSlot>(), chapter.missingSlots())
            val paths = chapter.files.values
            assertEquals("в ${chapter.title} повторяется файл", paths.size, paths.toSet().size)
            for (slot in RedBallSkinSlot.entries) {
                val relative = chapter.files.getValue(slot)
                val full = chapter.assetPath(slot)
                for (piece in banned) {
                    assertFalse("$full смотрит в $piece", relative.contains(piece) || full.contains(piece))
                }
                val file = File(assets, full.removePrefix("redball/"))
                assertTrue("нет файла для $slot: ${file.path}", file.isFile)
                assertTrue("слишком маленький файл $slot", file.length() > 500L)
                val header = ByteArray(8)
                file.inputStream().use { input -> assertEquals(8, input.read(header)) }
                assertEquals("это не PNG: $slot", 0x89.toByte(), header[0])
                assertEquals('P'.code.toByte(), header[1])
                assertEquals('N'.code.toByte(), header[2])
                assertEquals('G'.code.toByte(), header[3])
            }
        }
    }

    @Test
    fun aChapterThatSkipsASlotIsReported() {
        val partial = RedBallSkin(
            id = RedBallSkinId.GREEN_HILLS,
            title = "Дырявая",
            chapter = 9,
            assetRoot = "redball/partial",
            files = mapOf(RedBallSkinSlot.SKY to "tiles/sky_gradient.png"),
        )
        assertTrue(partial.missingSlots().contains(RedBallSkinSlot.HERO_HAPPY))
        assertTrue(partial.missingSlots().contains(RedBallSkinSlot.BOSS))
        assertTrue(partial.missingSlots().contains(RedBallSkinSlot.SPIKES))
        assertTrue(partial.missingSlots().contains(RedBallSkinSlot.SPRING_IDLE))
        assertTrue(partial.missingSlots().contains(RedBallSkinSlot.SPRING_COMPRESSED))
        assertTrue(partial.missingSlots().contains(RedBallSkinSlot.WATER_SURFACE))
        assertTrue(partial.missingSlots().contains(RedBallSkinSlot.WATER_BODY))
        assertEquals(RedBallSkinSlot.entries.size - 1, partial.missingSlots().size)
    }

    @Test
    fun hazardSlotsUseTheShippedPngSizes() {
        val chapter = RedBallSkins.greenHills
        val assets = redballAssets()
        val expected = listOf(
            RedBallSkinSlot.SPIKES to Triple("sprites/spikes.png", 128, 128),
            RedBallSkinSlot.SPRING_IDLE to Triple("sprites/spring_idle.png", 128, 128),
            RedBallSkinSlot.SPRING_COMPRESSED to Triple("sprites/spring_compressed.png", 128, 128),
            RedBallSkinSlot.WATER_SURFACE to Triple("tiles/water_surface.png", 256, 64),
            RedBallSkinSlot.WATER_BODY to Triple("tiles/water_body.png", 256, 256),
            RedBallSkinSlot.CONCRETE to Triple("tiles/concrete.png", 256, 256),
        )
        for ((slot, spec) in expected) {
            val (relative, width, height) = spec
            assertEquals(relative, chapter.files.getValue(slot))
            val file = File(assets, chapter.assetPath(slot).removePrefix("redball/"))
            val (actualW, actualH) = pngSize(file)
            assertEquals("$slot width", width, actualW)
            assertEquals("$slot height", height, actualH)
        }
        val concrete = File(assets, "green_hills/tiles/concrete.png")
        assertTrue(
            "бетон должен быть плоским тайлом v2, а не прежним тяжёлым PNG",
            concrete.length() in 8_000L..30_000L,
        )
        assertEquals(null, RedBallSkins.chapter(RedBallSkinId.CLASSIC))
    }

    @Test
    fun levelSaveDoesNotMentionTheSkin() {
        val json = RedBallDraft.fresh().toJson()
        assertFalse(json.contains("green_hills"))
        assertFalse(json.contains("\"skin\""))
        assertEquals(RedBallDraft.fresh().name, RedBallDraft.fromJson(json)!!.name)
    }

    private fun redballAssets(): File {
        val cwd = System.getProperty("user.dir") ?: "."
        var dir: File? = File(cwd).absoluteFile
        var left = 5
        while (dir != null && left > 0) {
            val direct = File(dir, "src/main/assets/redball")
            if (direct.isDirectory) return direct
            val nested = File(dir, "app/src/main/assets/redball")
            if (nested.isDirectory) return nested
            dir = dir.parentFile
            left--
        }
        error("Не нашёл assets/redball от ${System.getProperty("user.dir")}")
    }

    private fun pngSize(file: File): Pair<Int, Int> {
        val header = ByteArray(24)
        file.inputStream().use { input -> assertEquals(24, input.read(header)) }
        val buffer = ByteBuffer.wrap(header)
        assertEquals(0x89.toByte(), buffer.get())
        val width = buffer.getInt(16)
        val height = buffer.getInt(20)
        return width to height
    }
}
