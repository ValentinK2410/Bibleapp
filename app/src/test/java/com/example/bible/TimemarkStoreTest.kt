package com.example.bible

import com.example.bible.data.TimemarkStore
import com.example.bible.data.scanTimemarkPresence
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

class TimemarkStoreTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun audioFileMatchesChapterNarration_sameCanonicalPath() {
        val root = tempDir.newFolder("files")
        val narratorDir = File(root, "bible_audio/bondarenko").apply { mkdirs() }
        val mp3 = File(narratorDir, "01_01.mp3").apply { writeBytes(ByteArray(512) { 1 }) }
        val context = FakeContext(root)

        assertTrue(
            TimemarkStore.audioFileMatchesChapterNarration(
                context,
                narratorId = "bondarenko",
                bookId = "genesis",
                chapter = 1,
                projectAudioPath = mp3.absolutePath,
            ),
        )
    }

    @Test
    fun audioFileMatchesChapterNarration_sameFileNameDifferentFolder() {
        val root = tempDir.newFolder("files")
        val bondarenko = File(root, "bible_audio/bondarenko").apply { mkdirs() }
        val timemarkDir = File(root, "timemark_audio").apply { mkdirs() }
        File(bondarenko, "01_01.mp3").writeBytes(ByteArray(512) { 1 })
        val imported = File(timemarkDir, "01_01.mp3").apply { writeBytes(ByteArray(512) { 1 }) }
        val context = FakeContext(root)

        assertTrue(
            TimemarkStore.audioFileMatchesChapterNarration(
                context,
                narratorId = "bondarenko",
                bookId = "genesis",
                chapter = 1,
                projectAudioPath = imported.absolutePath,
            ),
        )
    }

    @Test
    fun audioFileMatchesChapterNarration_differentFileName() {
        val root = tempDir.newFolder("files")
        val bondarenko = File(root, "bible_audio/bondarenko").apply { mkdirs() }
        val mp3 = File(bondarenko, "01_02.mp3").apply { writeBytes(ByteArray(512) { 1 }) }
        val context = FakeContext(root)

        assertFalse(
            TimemarkStore.audioFileMatchesChapterNarration(
                context,
                narratorId = "bondarenko",
                bookId = "genesis",
                chapter = 1,
                projectAudioPath = mp3.absolutePath,
            ),
        )
    }

    @Test
    fun shouldReplaceBundled_newerAssetWinsEvenIfShorter() {
        val oldJson = """{"updatedAt": 100, "cues": [1, 2, 3, 4]}"""
        val newerJson = """{"updatedAt": 200, "cues": [9]}"""
        assertTrue(TimemarkStore.shouldReplaceBundled(oldJson, newerJson))
        assertFalse(TimemarkStore.shouldReplaceBundled(newerJson, oldJson))
        assertFalse(TimemarkStore.shouldReplaceBundled(newerJson, newerJson))
        assertTrue(TimemarkStore.shouldReplaceBundled(null, newerJson))
        assertTrue(TimemarkStore.shouldReplaceBundled("не json", newerJson))
    }

    @Test
    fun hasTimemarksForChapter_emptyWhenNoProjects() {
        val root = tempDir.newFolder("files")
        val context = FakeContext(root)
        assertFalse(
            TimemarkStore.hasTimemarksForChapter(context, "rst", "genesis", 1),
        )
    }

    @Test
    fun hasTimemarksForChapter_trueWhenCuesPresent() {
        val root = tempDir.newFolder("files")
        val context = FakeContext(root)
        TimemarkStore.save(
            context,
            com.example.bible.data.TimemarkProject(
                translationCode = "rst",
                bookId = "genesis",
                chapter = 1,
                title = "Test",
                audioFilePath = "",
                cues = listOf(
                    com.example.bible.data.TimemarkCue(timeMs = 0, verseStart = 1),
                ),
            ),
        )
        assertTrue(TimemarkStore.hasTimemarksForChapter(context, "rst", "genesis", 1))
        assertFalse(TimemarkStore.hasTimemarksForChapter(context, "rst", "genesis", 2))
    }

    @Test
    fun chaptersWithTimemarksForBook_returnsChaptersWithCues() {
        val root = tempDir.newFolder("files")
        val context = FakeContext(root)
        TimemarkStore.save(
            context,
            com.example.bible.data.TimemarkProject(
                translationCode = "rst",
                bookId = "genesis",
                chapter = 1,
                title = "A",
                audioFilePath = "",
                cues = listOf(com.example.bible.data.TimemarkCue(0, 1)),
            ),
        )
        TimemarkStore.save(
            context,
            com.example.bible.data.TimemarkProject(
                translationCode = "rst",
                bookId = "genesis",
                chapter = 3,
                title = "B",
                audioFilePath = "",
                cues = listOf(com.example.bible.data.TimemarkCue(0, 1)),
            ),
        )
        assertEquals(setOf(1, 3), TimemarkStore.chaptersWithTimemarksForBook(context, "rst", "genesis"))
    }

    @Test
    fun presenceIndex_listsEveryTranslationForBooksAndChapters() {
        val root = tempDir.newFolder("files")
        val context = FakeContext(root)
        TimemarkStore.save(
            context,
            com.example.bible.data.TimemarkProject(
                translationCode = "RBO",
                bookId = "genesis",
                chapter = 1,
                title = "RBO",
                audioFilePath = "",
                cues = listOf(com.example.bible.data.TimemarkCue(0, 1)),
            ),
        )
        TimemarkStore.save(
            context,
            com.example.bible.data.TimemarkProject(
                translationCode = "SYN",
                bookId = "genesis",
                chapter = 1,
                title = "SYN",
                audioFilePath = "",
                cues = listOf(com.example.bible.data.TimemarkCue(0, 1)),
            ),
        )
        TimemarkStore.save(
            context,
            com.example.bible.data.TimemarkProject(
                translationCode = "RBO",
                bookId = "exodus",
                chapter = 2,
                title = "RBO Ex",
                audioFilePath = "",
                cues = listOf(com.example.bible.data.TimemarkCue(0, 1)),
            ),
        )
        TimemarkStore.save(
            context,
            com.example.bible.data.TimemarkProject(
                translationCode = "NRT",
                bookId = "genesis",
                chapter = 3,
                title = "empty",
                audioFilePath = "",
                cues = emptyList(),
            ),
        )
        val index = TimemarkStore.presenceIndex(context)
        assertEquals(setOf("RBO", "SYN"), index.forBook("genesis"))
        assertEquals(setOf("RBO"), index.forBook("exodus"))
        assertEquals(setOf("RBO", "SYN"), index.forChapter("genesis", 1))
        assertEquals(emptySet<String>(), index.forChapter("genesis", 3))
        assertEquals(setOf("RBO"), index.forChapter("exodus", 2))
        assertTrue(index.forBook("john").isEmpty())
        assertEquals(setOf("RBO", "SYN"), TimemarkStore.peekPresenceIndex(context).forBook("genesis"))
    }

    @Test
    fun presenceIndex_cacheIgnoresFilesWrittenOutsideStoreUntilReload() {
        val root = tempDir.newFolder("files")
        val context = FakeContext(root)
        TimemarkStore.save(
            context,
            com.example.bible.data.TimemarkProject(
                id = "kept",
                translationCode = "SYN",
                bookId = "genesis",
                chapter = 1,
                title = "A",
                audioFilePath = "",
                cues = listOf(com.example.bible.data.TimemarkCue(0, 1)),
            ),
        )
        val cached = TimemarkStore.presenceIndex(context)
        assertEquals(setOf("SYN"), cached.forBook("genesis"))

        val dir = File(root, "timemark_projects")
        File(dir, "extra.json").writeText(
            """
            {"bookId":"exodus","translationCode":"RBO","chapter":2,"title":"see \"cues\": [] inside text","cues":[{"timeMs":1,"verseStart":1}]}
            """.trimIndent(),
        )
        assertEquals(setOf("SYN"), TimemarkStore.presenceIndex(context).forBook("genesis"))
        assertTrue(TimemarkStore.presenceIndex(context).forBook("exodus").isEmpty())

        val fresh = TimemarkStore.reloadPresenceIndex(context)
        assertEquals(setOf("RBO"), fresh.forBook("exodus"))
        assertEquals(setOf("RBO"), fresh.forChapter("exodus", 2))

        TimemarkStore.delete(context, "kept")
        val afterDelete = TimemarkStore.presenceIndex(context)
        assertTrue(afterDelete.forBook("genesis").isEmpty())
        assertEquals(setOf("RBO"), afterDelete.forBook("exodus"))
    }

    @Test
    fun presenceIndex_skipsEmptyCuesAndBrokenJsonWithoutFullCueParse() {
        val root = tempDir.newFolder("files")
        val context = FakeContext(root)
        val dir = File(root, "timemark_projects").apply { mkdirs() }
        File(dir, "empty.json").writeText(
            """{"bookId":"genesis","translationCode":"NRT","chapter":3,"cues":[]}""",
        )
        File(dir, "blank.json").writeText(
            """{"bookId":" ","translationCode":"NRT","chapter":1,"cues":[{"timeMs":1,"verseStart":1}]}""",
        )
        File(dir, "broken.json").writeText("{not json")
        val cues = buildString {
            append("""{"cues":[""")
            repeat(50) { index ->
                if (index > 0) append(',')
                append("""{"timeMs":$index,"verseStart":1,"note":"x","attachments":[]}""")
            }
            append("""],"translationCode":"SYN","bookId":"john","chapter":4}""")
        }
        File(dir, "cues-first.json").writeText(cues)
        val index = TimemarkStore.reloadPresenceIndex(context)
        assertEquals(setOf("SYN"), index.forBook("john"))
        assertEquals(setOf("SYN"), index.forChapter("john", 4))
        assertTrue(index.forBook("genesis").isEmpty())
    }

    @Test
    fun scanTimemarkPresence_matchesParsedAssets() {
        val dir = listOf(
            File("src/main/assets/timemark_projects"),
            File("app/src/main/assets/timemark_projects"),
        ).firstOrNull { it.isDirectory }
        check(dir != null) { "Нет каталога timemark_projects для сверки сканера" }
        val files = dir.listFiles { file -> file.extension == "json" }.orEmpty()
        assertTrue(files.size > 100)
        for (file in files) {
            val text = file.readText()
            val header = scanTimemarkPresence(text)
            val parsed = runCatching { JSONObject(text) }.getOrNull()
            if (parsed == null) {
                assertEquals(file.name, null, header)
                continue
            }
            val cues = parsed.optJSONArray("cues")
            val hasCues = cues != null && cues.length() > 0
            val bookId = parsed.optString("bookId", "")
            val translation = parsed.optString("translationCode", "")
            val chapter = if (parsed.has("chapter")) parsed.optInt("chapter") else null
            if (!hasCues || bookId.isBlank() || translation.isBlank() || chapter == null) {
                assertTrue(
                    file.name,
                    header == null || !header.hasCues || header.bookId.isBlank(),
                )
            } else {
                assertEquals(file.name, bookId, header?.bookId)
                assertEquals(file.name, translation, header?.translationCode)
                assertEquals(file.name, chapter, header?.chapter)
                assertEquals(file.name, true, header?.hasCues)
            }
        }
    }

    /** Минимальный Context с подменённым filesDir для unit-тестов. */
    private class FakeContext(private val filesRoot: File) : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): android.content.Context = this
        override fun getFilesDir(): File = filesRoot
    }
}
