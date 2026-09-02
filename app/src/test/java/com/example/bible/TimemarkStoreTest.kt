package com.example.bible

import com.example.bible.data.TimemarkStore
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
    }

    /** Минимальный Context с подменённым filesDir для unit-тестов. */
    private class FakeContext(private val filesRoot: File) : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): android.content.Context = this
        override fun getFilesDir(): File = filesRoot
    }
}
