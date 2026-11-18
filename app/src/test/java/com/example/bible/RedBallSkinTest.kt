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
        assertEquals(RedBallSkinId.SUNNY_DESERT, RedBallSkinId.fromStored("sunny_desert"))
        assertTrue(RedBallSkinId.CLASSIC.procedural)
        assertFalse(RedBallSkinId.GREEN_HILLS.procedural)
        assertFalse(RedBallSkinId.DEEP_FOREST.procedural)
        assertFalse(RedBallSkinId.SUNNY_DESERT.procedural)
        assertEquals(null, RedBallSkins.chapter(RedBallSkinId.CLASSIC))
        assertEquals(RedBallSkinId.GREEN_HILLS, RedBallSkins.selectable.first())
        assertEquals(
            listOf(
                RedBallSkinId.GREEN_HILLS,
                RedBallSkinId.DEEP_FOREST,
                RedBallSkinId.SUNNY_DESERT,
                RedBallSkinId.CLASSIC,
            ),
            RedBallSkins.selectable,
        )
        assertEquals(
            listOf(RedBallSkinId.GREEN_HILLS, RedBallSkinId.DEEP_FOREST, RedBallSkinId.SUNNY_DESERT),
            RedBallSkins.chapters.map { it.id },
        )
        assertEquals(0L, RedBallSkinId.GREEN_HILLS.menuInk)
        assertEquals(0L, RedBallSkinId.CLASSIC.menuInk)
        assertEquals(0L, RedBallSkinId.GREEN_HILLS.menuInkSoft)
        assertEquals(0L, RedBallSkinId.GREEN_HILLS.menuHalo)
        assertEquals(0L, RedBallSkinId.DEEP_FOREST.menuHalo)
        assertEquals(0L, RedBallSkinId.CLASSIC.menuHalo)
        assertLightInk(RedBallSkinId.DEEP_FOREST.menuInk)
        assertLightInk(RedBallSkinId.DEEP_FOREST.menuInkSoft)
        assertEquals(0xFF281E1E, RedBallSkinId.SUNNY_DESERT.menuInk)
        assertDarkInk(RedBallSkinId.SUNNY_DESERT.menuInk)
        assertDarkInk(RedBallSkinId.SUNNY_DESERT.menuInkSoft)
        assertLightInk(RedBallSkinId.SUNNY_DESERT.menuHalo)
        assertFalse(RedBallSkinId.GREEN_HILLS.flatSun)
        assertFalse(RedBallSkinId.DEEP_FOREST.flatSun)
        assertFalse(RedBallSkinId.CLASSIC.flatSun)
        assertTrue(RedBallSkinId.SUNNY_DESERT.flatSun)
        val forest = RedBallSkins.chapter(RedBallSkinId.DEEP_FOREST)
        assertEquals(2, forest!!.chapter)
        assertEquals("Тёмный лес", forest.title)
        assertEquals("redball/deep_forest", forest.assetRoot)
        val desert = RedBallSkins.chapter(RedBallSkinId.SUNNY_DESERT)
        assertEquals(3, desert!!.chapter)
        assertEquals("Солнечная пустыня", desert.title)
        assertEquals("redball/sunny_desert", desert.assetRoot)
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
                if (slot.optional && slot !in chapter.files) continue
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
        assertFalse(partial.missingSlots().contains(RedBallSkinSlot.CART))
        assertEquals(RedBallSkinSlot.entries.count { !it.optional } - 1, partial.missingSlots().size)
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
        val required = RedBallSkinSlot.entries.filter { !it.optional }.toSet()
        assertEquals(required, expectedPng.keys)
        val packed = RedBallSkins.chapters.associate { it.id to fileBytes(assets, it) }
        assertEquals(
            setOf(RedBallSkinId.GREEN_HILLS, RedBallSkinId.DEEP_FOREST, RedBallSkinId.SUNNY_DESERT),
            packed.keys,
        )
        val green = packed.getValue(RedBallSkinId.GREEN_HILLS)
        for (chapter in RedBallSkins.chapters) {
            val files = packed.getValue(chapter.id)
            assertEquals(
                "у ${chapter.title} не хватает слота",
                required,
                files.keys,
            )
            for (slot in required) {
                val spec = expectedPng.getValue(slot)
                val file = files.getValue(slot)
                val expectedBytes = when (chapter.id) {
                    RedBallSkinId.GREEN_HILLS -> spec.greenBytes
                    RedBallSkinId.DEEP_FOREST -> spec.forestBytes
                    RedBallSkinId.SUNNY_DESERT -> spec.desertBytes
                    else -> error("нет ожидаемых байтов для ${chapter.id}")
                }
                assertEquals("$slot ${chapter.id} width", spec.width, file.width)
                assertEquals("$slot ${chapter.id} height", spec.height, file.height)
                assertEquals("$slot ${chapter.id} bytes", expectedBytes, file.bytes)
                if (chapter.id == RedBallSkinId.GREEN_HILLS) continue
                val copied = slot in copiedFromGreenHills
                val greenFile = green.getValue(slot)
                val same = greenFile.bytes == file.bytes && greenFile.sha == file.sha
                assertEquals(
                    if (copied) {
                        "$slot ${chapter.title} должен совпадать с зелёными холмами"
                    } else {
                        "$slot ${chapter.title} должен быть своим рисунком"
                    },
                    copied,
                    same,
                )
            }
        }
    }

    @Test
    fun levelSaveDoesNotMentionTheSkin() {
        val json = RedBallDraft.fresh().toJson()
        assertFalse(json.contains("green_hills"))
        assertFalse(json.contains("deep_forest"))
        assertFalse(json.contains("sunny_desert"))
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
        val out = HashMap<RedBallSkinSlot, PngFile>(chapter.files.size)
        for (slot in chapter.files.keys) {
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

    private fun assertDarkInk(argb: Long) {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        assertTrue(
            "чернила меню слишком светлые для закатного неба: %08X".format(argb),
            red <= 0x40 && green <= 0x30 && blue <= 0x30,
        )
    }

    private data class PngFile(val width: Int, val height: Int, val bytes: Long, val sha: String)

    private data class ExpectedPng(
        val width: Int,
        val height: Int,
        val greenBytes: Long,
        val forestBytes: Long,
        val desertBytes: Long,
    )

    /**
     * Пиксели совпадают у всех глав: спрайты и иконки — 2×, крупные тайлы — 1×.
     * Байты леса и пустыни — это как раз те файлы, которые лежат в APK.
     */
    private val expectedPng = mapOf(
        RedBallSkinSlot.HERO_HAPPY to ExpectedPng(128, 128, 19724, 19724, 19724),
        RedBallSkinSlot.HERO_SURPRISED to ExpectedPng(128, 128, 19672, 19672, 19672),
        RedBallSkinSlot.HERO_SCARED to ExpectedPng(128, 128, 19371, 19371, 19371),
        RedBallSkinSlot.ENEMY_SQUARE to ExpectedPng(128, 128, 21880, 26251, 27904),
        RedBallSkinSlot.BOSS to ExpectedPng(256, 256, 58220, 73002, 84725),
        RedBallSkinSlot.BOULDER to ExpectedPng(128, 128, 19545, 22518, 23802),
        RedBallSkinSlot.CRATE to ExpectedPng(256, 256, 59025, 79467, 103070),
        RedBallSkinSlot.CONCRETE to ExpectedPng(256, 256, 12315, 42961, 82810),
        RedBallSkinSlot.GRASS_TOP to ExpectedPng(256, 256, 91524, 90378, 78277),
        RedBallSkinSlot.GRASS_EDGE to ExpectedPng(256, 64, 20558, 22233, 11672),
        RedBallSkinSlot.SOIL to ExpectedPng(256, 256, 116862, 98530, 103900),
        RedBallSkinSlot.LADDER to ExpectedPng(128, 128, 9041, 5004, 4034),
        RedBallSkinSlot.SPIKES to ExpectedPng(128, 128, 17986, 21040, 27383),
        RedBallSkinSlot.SPRING_IDLE to ExpectedPng(128, 128, 19313, 19441, 19201),
        RedBallSkinSlot.SPRING_COMPRESSED to ExpectedPng(128, 128, 14527, 15540, 16645),
        RedBallSkinSlot.WATER_SURFACE to ExpectedPng(256, 64, 17849, 20698, 19982),
        RedBallSkinSlot.WATER_BODY to ExpectedPng(256, 256, 59516, 62014, 94562),
        RedBallSkinSlot.STAR to ExpectedPng(64, 64, 5032, 5032, 5032),
        RedBallSkinSlot.MAGNET to ExpectedPng(64, 64, 5380, 5380, 5380),
        RedBallSkinSlot.HEART to ExpectedPng(64, 64, 4931, 4931, 4931),
        RedBallSkinSlot.FLAG to ExpectedPng(128, 128, 11998, 11998, 11998),
        RedBallSkinSlot.PORTAL_ENTRY to ExpectedPng(128, 128, 28008, 28008, 28008),
        RedBallSkinSlot.PORTAL_EXIT to ExpectedPng(128, 128, 30742, 30742, 30742),
        RedBallSkinSlot.BUSH to ExpectedPng(256, 256, 43723, 58499, 60053),
        RedBallSkinSlot.TREE to ExpectedPng(256, 256, 52559, 57696, 58784),
        RedBallSkinSlot.SKY to ExpectedPng(256, 512, 1589, 5902, 1448),
        RedBallSkinSlot.HILLS_FAR to ExpectedPng(1024, 256, 239534, 265165, 167125),
        RedBallSkinSlot.HILLS_NEAR to ExpectedPng(1024, 256, 312512, 353349, 224355),
        RedBallSkinSlot.CLOUD_A to ExpectedPng(216, 128, 21725, 24407, 24540),
        RedBallSkinSlot.CLOUD_B to ExpectedPng(216, 128, 20728, 22917, 22586),
        RedBallSkinSlot.CLOUD_C to ExpectedPng(218, 128, 20459, 25339, 24474),
        RedBallSkinSlot.ICON_HEART to ExpectedPng(64, 64, 4931, 4931, 4931),
        RedBallSkinSlot.ICON_STAR to ExpectedPng(64, 64, 5032, 5032, 5032),
        RedBallSkinSlot.ICON_PAUSE to ExpectedPng(64, 64, 6194, 6194, 6194),
        RedBallSkinSlot.ICON_COIN to ExpectedPng(64, 64, 8109, 8109, 8109),
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
