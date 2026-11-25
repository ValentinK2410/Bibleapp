package com.example.bible

import com.example.bible.data.BibleCanon
import com.example.bible.data.BookIntros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookIntrosTest {

    @Test
    fun everyCanonBookHasCompleteIntro() {
        val file = listOf(
            File("src/main/assets/bible/book_intros.json"),
            File("app/src/main/assets/bible/book_intros.json"),
        ).first { it.exists() }
        val intros = BookIntros.parse(file.readText())
        assertEquals(BibleCanon.allBooks.size, intros.size)
        val knownSource = listOf("Лопухин", "Брокгауз", "РБО")
        BibleCanon.allBooks.forEach { book ->
            val intro = intros[book.id]
            assertNotNull(book.id, intro)
            val info = intro!!
            assertTrue("${book.id} genre", info.genre.isNotBlank())
            assertTrue("${book.id} author", info.author.isNotBlank())
            assertTrue("${book.id} occasion", info.occasion.isNotBlank())
            assertTrue("${book.id} date", info.date.isNotBlank())
            assertTrue("${book.id} source", info.source.isNotBlank())
            assertTrue(
                "${book.id} source",
                knownSource.any { info.source.contains(it) },
            )
            val lines = info.displayLines()
            assertTrue("${book.id} lines", lines.size in 3..6)
            lines.forEach { line -> assertTrue("${book.id} blank line", line.isNotBlank()) }
            assertTrue(lines.last().startsWith("Источник:"))
        }
    }
}
