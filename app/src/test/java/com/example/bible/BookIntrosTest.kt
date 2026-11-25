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
        BibleCanon.allBooks.forEach { book ->
            val intro = intros[book.id]
            assertNotNull(book.id, intro)
            val info = intro!!
            assertTrue("${book.id} summary", info.summary.isNotBlank())
            assertTrue("${book.id} lopukhin", !info.summary.contains("Лопухин"))
            assertTrue("${book.id} brockhaus", !info.summary.contains("Брокгауз"))
            assertTrue("${book.id} source lopukhin", !info.source.contains("Лопухин"))
            assertTrue("${book.id} source brockhaus", !info.source.contains("Брокгауз"))
            val lines = info.displayLines()
            assertTrue("${book.id} lines", lines.isNotEmpty())
            assertEquals(info.summary, lines.first())
            if (info.source.isBlank()) {
                assertEquals(1, lines.size)
            } else {
                assertTrue(lines.last().startsWith("Источник справки:"))
                assertTrue("${book.id} source url", info.source.contains("http"))
            }
            assertTrue("${book.id} manuscripts", info.manuscripts.isNotEmpty())
            assertTrue("${book.id} manuscript count", info.manuscripts.size <= 3)
            info.manuscripts.forEach { manuscript ->
                assertTrue("${book.id} ms name", manuscript.name.isNotBlank())
                assertTrue("${book.id} ms date", manuscript.date.isNotBlank())
                assertTrue("${book.id} ms location", manuscript.location.isNotBlank())
                assertTrue("${book.id} ms url", manuscript.url.startsWith("https://"))
            }
        }
    }
}
