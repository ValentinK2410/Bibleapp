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
import java.security.MessageDigest

class RedBallSkinTest {

    @Test
    fun greenHillsIsTheDefaultAndClassicStaysProcedural() {
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.DEFAULT)
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.fromStored(null))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.fromStored(""))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.fromStored("нет-такого"))
        assertEquals(RedBallSkinId.CLASSIC, RedBallSkinId.fromStored("classic"))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkinId.fromStored("green_hills"))
        assertEquals(RedBallSkinId.DEEP_FOREST, RedBallSkinId.fromStored("deep_forest"))
        assertTrue(RedBallSkinId.CLASSIC.procedural)
        assertFalse(RedBallSkinId.GREEN_HILLS.procedural)
        assertFalse(RedBallSkinId.DEEP_FOREST.procedural)
        assertEquals(null, RedBallSkins.chapter(RedBallSkinId.CLASSIC))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkins.selectable.first())
        assertEquals(
            listOf(RedBallSkinId.GREEN_HILLS, RedBallSkinId.DEEP_FOREST, RedBallSkinId.CLASSIC),
            RedBallSkins.selectable,
        )
        assertEquals(0L, RedBallSkinId.GREEN_HILLS.menuInk)
        assertEquals(0L, RedBallSkinId.CLASSIC.menuInk)
        assertEquals(0L, RedBallSkinId.GREEN_HILLS.menuInkSoft)
        assertLightInk(RedBallSkinId.DEEP_FOREST.menuInk)
        assertLightInk(RedBallSkinId.DEEP_FOREST.menuInkSoft)
        val forest = RedBallSkins.chapter(RedBallSkinId.DEEP_FOREST)
        assertEquals(2, forest!!.chapter)
        assertEquals("Тёмный лес", forest.title)
        assertEquals("redball/deep_forest", forest.assetRoot)
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
    fun everyChapterShipsTheSameSlotSizes() {
        val assets = redballAssets()
        assertEquals(RedBallSkinSlot.entries.toSet(), expectedPng.keys)
        val green = fileBytes(assets, RedBallSkins.greenHills)
        val forest = fileBytes(assets, RedBallSkins.deepForest)
        for (slot in RedBallSkinSlot.entries) {
            val (width, height, greenBytes, forestBytes) = expectedPng.getValue(slot)
            val greenFile = green.getValue(slot)
            val forestFile = forest.getValue(slot)
            assertEquals("$slot green width", width, greenFile.width)
            assertEquals("$slot green height", height, greenFile.height)
            assertEquals("$slot forest width", width, forestFile.width)
            assertEquals("$slot forest height", height, forestFile.height)
            assertEquals("$slot green bytes", greenBytes, greenFile.bytes)
            assertEquals("$slot forest bytes", forestBytes, forestFile.bytes)
            val copied = slot in copiedFromGreenHills
            assertEquals(
                if (copied) "$slot должен совпадать с зелёными холмами" else "$slot должен быть новым рисунком леса",
                copied,
                greenFile.bytes == forestFile.bytes && greenFile.sha == forestFile.sha,
            )
        }
    }

    @Test
    fun levelSaveDoesNotMentionTheSkin() {
        val json = RedBallDraft.fresh().toJson()
        assertFalse(json.contains("green_hills"))
        assertFalse(json.contains("deep_forest"))
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

    private fun fileBytes(assets: File, chapter: RedBallSkin): Map<RedBallSkinSlot, PngFile> {
        val out = HashMap<RedBallSkinSlot, PngFile>(RedBallSkinSlot.entries.size)
        for (slot in RedBallSkinSlot.entries) {
            val file = File(assets, chapter.assetPath(slot).removePrefix("redball/"))
            val (width, height) = pngSize(file)
            val raw = file.readBytes()
            val sha = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
            out[slot] = PngFile(width, height, raw.size.toLong(), sha)
        }
        return out
    }

    private fun assertLightInk(argb: Long) {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        assertTrue(
            "чернила меню слишком тёмные для фиолетового неба: %08X".format(argb),
            red >= 0xD0 && green >= 0xC0 && blue >= 0xC0,
        )
    }

    private data class PngFile(val width: Int, val height: Int, val bytes: Long, val sha: String)

    private data class ExpectedPng(
        val width: Int,
        val height: Int,
        val greenBytes: Long,
        val forestBytes: Long,
    )

    /**
     * Пиксели совпадают у обеих глав: спрайты и иконки — 2×, крупные тайлы — 1×.
     * Байты «Тёмного леса» — это как раз тот файл, который лежит в APK.
     */
    private val expectedPng = mapOf(
        RedBallSkinSlot.HERO_HAPPY to ExpectedPng(128, 128, 19724, 19724),
        RedBallSkinSlot.HERO_SURPRISED to ExpectedPng(128, 128, 19672, 19672),
        RedBallSkinSlot.HERO_SCARED to ExpectedPng(128, 128, 19371, 19371),
        RedBallSkinSlot.ENEMY_SQUARE to ExpectedPng(128, 128, 21880, 26251),
        RedBallSkinSlot.BOSS to ExpectedPng(256, 256, 58220, 73002),
        RedBallSkinSlot.BOULDER to ExpectedPng(128, 128, 19545, 22518),
        RedBallSkinSlot.CRATE to ExpectedPng(256, 256, 59025, 79467),
        RedBallSkinSlot.CONCRETE to ExpectedPng(256, 256, 12315, 42961),
        RedBallSkinSlot.GRASS_TOP to ExpectedPng(256, 256, 91524, 90378),
        RedBallSkinSlot.GRASS_EDGE to ExpectedPng(256, 64, 20558, 22233),
        RedBallSkinSlot.SOIL to ExpectedPng(256, 256, 116862, 98530),
        RedBallSkinSlot.LADDER to ExpectedPng(128, 128, 9041, 5004),
        RedBallSkinSlot.SPIKES to ExpectedPng(128, 128, 17986, 21040),
        RedBallSkinSlot.SPRING_IDLE to ExpectedPng(128, 128, 19313, 19441),
        RedBallSkinSlot.SPRING_COMPRESSED to ExpectedPng(128, 128, 14527, 15540),
        RedBallSkinSlot.WATER_SURFACE to ExpectedPng(256, 64, 17849, 20698),
        RedBallSkinSlot.WATER_BODY to ExpectedPng(256, 256, 59516, 62014),
        RedBallSkinSlot.STAR to ExpectedPng(64, 64, 5032, 5032),
        RedBallSkinSlot.MAGNET to ExpectedPng(64, 64, 5380, 5380),
        RedBallSkinSlot.HEART to ExpectedPng(64, 64, 4931, 4931),
        RedBallSkinSlot.FLAG to ExpectedPng(128, 128, 11998, 11998),
        RedBallSkinSlot.PORTAL_ENTRY to ExpectedPng(128, 128, 28008, 28008),
        RedBallSkinSlot.PORTAL_EXIT to ExpectedPng(128, 128, 30742, 30742),
        RedBallSkinSlot.BUSH to ExpectedPng(256, 256, 43723, 58499),
        RedBallSkinSlot.TREE to ExpectedPng(256, 256, 52559, 57696),
        RedBallSkinSlot.SKY to ExpectedPng(256, 512, 1589, 5902),
        RedBallSkinSlot.HILLS_FAR to ExpectedPng(1024, 256, 239534, 265165),
        RedBallSkinSlot.HILLS_NEAR to ExpectedPng(1024, 256, 312512, 353349),
        RedBallSkinSlot.CLOUD_A to ExpectedPng(216, 128, 21725, 24407),
        RedBallSkinSlot.CLOUD_B to ExpectedPng(216, 128, 20728, 22917),
        RedBallSkinSlot.CLOUD_C to ExpectedPng(218, 128, 20459, 25339),
        RedBallSkinSlot.ICON_HEART to ExpectedPng(64, 64, 4931, 4931),
        RedBallSkinSlot.ICON_STAR to ExpectedPng(64, 64, 5032, 5032),
        RedBallSkinSlot.ICON_PAUSE to ExpectedPng(64, 64, 6194, 6194),
        RedBallSkinSlot.ICON_COIN to ExpectedPng(64, 64, 8109, 8109),
    )

    /** Герой, звезда, магнит, сердце, флаг, порталы и иконки — побайтовые копии «Зелёных холмов». */
    private val copiedFromGreenHills = setOf(
        RedBallSkinSlot.HERO_HAPPY,
        RedBallSkinSlot.HERO_SURPRISED,
        RedBallSkinSlot.HERO_SCARED,
        RedBallSkinSlot.STAR,
        RedBallSkinSlot.MAGNET,
        RedBallSkinSlot.HEART,
        RedBallSkinSlot.FLAG,
        RedBallSkinSlot.PORTAL_ENTRY,
        RedBallSkinSlot.PORTAL_EXIT,
        RedBallSkinSlot.ICON_HEART,
        RedBallSkinSlot.ICON_STAR,
        RedBallSkinSlot.ICON_PAUSE,
        RedBallSkinSlot.ICON_COIN,
    )
}
