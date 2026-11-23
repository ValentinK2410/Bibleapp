package com.example.bible

import com.example.bible.data.BooksMainMenuOrder
import com.example.bible.data.mynotes.CheckItem
import com.example.bible.data.mynotes.MyNote
import com.example.bible.data.mynotes.MyNoteKind
import com.example.bible.data.mynotes.MyNoteSort
import com.example.bible.data.mynotes.MyNotesDocument
import com.example.bible.data.mynotes.NotePalette
import com.example.bible.data.mynotes.RecipeLine
import com.example.bible.data.mynotes.SketchPoint
import com.example.bible.data.mynotes.SketchStroke
import com.example.bible.data.mynotes.TaskPriority
import com.example.bible.data.mynotes.decimatePoints
import com.example.bible.data.mynotes.moveAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyNotesLogicTest {
    @Test
    fun storeRoundTripKeepsEveryKind() {
        val original = MyNotesDocument(
            notes = listOf(sampleNote()),
            sort = MyNoteSort.UPDATED,
            filterKind = MyNoteKind.RECIPE,
            grid = true,
        )
        val restored = MyNotesDocument.parse(original.toJson())
        assertEquals(original.sort, restored.sort)
        assertEquals(MyNoteKind.RECIPE, restored.filterKind)
        assertTrue(restored.grid)
        assertEquals(original.notes, restored.notes)
    }

    @Test
    fun brokenJsonDoesNotWipeAValidNeighbour() {
        assertEquals(MyNotesDocument.Empty, MyNotesDocument.parse("{"))
        assertEquals(MyNotesDocument.Empty, MyNotesDocument.parse(null))
        val mixed = """
            {"notes":[
              {"title":"без id"},
              {"id":"ok","kind":"weird","palette":"nope","title":"Живая","priority":"nope","sort":"x"}
            ],"sort":"nope","filter":"nope","grid":true}
        """.trimIndent()
        val doc = MyNotesDocument.parse(mixed)
        assertEquals(1, doc.notes.size)
        assertEquals("ok", doc.notes.single().id)
        assertEquals(MyNoteKind.TEXT, doc.notes.single().kind)
        assertEquals(NotePalette.SAND, doc.notes.single().palette)
        assertEquals(TaskPriority.NORMAL, doc.notes.single().priority)
        assertEquals(MyNoteSort.MANUAL, doc.sort)
        assertNull(doc.filterKind)
        assertTrue(doc.grid)
    }

    @Test
    fun pinsStayOnTopForEverySort() {
        val notes = listOf(
            note("late", title = "Яблоко", created = 30, updated = 30, order = 2),
            note("pin", title = "Закреп", pinned = true, created = 1, updated = 1, order = 5, palette = NotePalette.ROSE, kind = MyNoteKind.SKETCH),
            note("early", title = "Арбуз", created = 10, updated = 20, order = 0, palette = NotePalette.SKY, kind = MyNoteKind.TASK),
        )
        MyNoteSort.entries.forEach { sort ->
            assertEquals("pin", MyNotesDocument(notes, sort).visible().first().id)
        }
        val byTitle = MyNotesDocument(notes, MyNoteSort.TITLE).visible().map { it.id }
        assertEquals(listOf("pin", "early", "late"), byTitle)
        val byColor = MyNotesDocument(notes, MyNoteSort.COLOR).visible().map { it.id }
        assertEquals(listOf("pin", "late", "early"), byColor)
        val byType = MyNotesDocument(notes, MyNoteSort.TYPE).visible().drop(1).map { it.kind }
        assertEquals(listOf(MyNoteKind.TEXT, MyNoteKind.TASK), byType)
        val byCreated = MyNotesDocument(notes, MyNoteSort.CREATED).visible().map { it.id }
        assertEquals(listOf("pin", "late", "early"), byCreated)
    }

    @Test
    fun manualOrderAndFilterAndSearch() {
        val doc = MyNotesDocument(
            notes = listOf(
                note("t", title = "Дневник", order = 0, kind = MyNoteKind.TEXT),
                note("a", title = "Магазин", order = 1, kind = MyNoteKind.CHECKLIST, checks = listOf(CheckItem("m", "Молоко"))),
                note("b", title = "Аптека", order = 2, kind = MyNoteKind.CHECKLIST),
            ),
        )
        assertEquals(listOf("t", "a", "b"), doc.visible().map { it.id })
        assertEquals(listOf("a"), doc.copy(filterKind = MyNoteKind.CHECKLIST).visible("МОЛОКО").map { it.id })
        assertTrue(doc.visible("список").any { it.id == "a" })
        assertTrue(doc.notes[1].matchesQuery("высокий").not())
    }

    @Test
    fun reorderKeepsHiddenNotesAndSwitchesToManual() {
        val doc = MyNotesDocument(
            notes = listOf(
                note("t", order = 0),
                note("a", order = 1, kind = MyNoteKind.CHECKLIST),
                note("b", order = 2, kind = MyNoteKind.CHECKLIST),
            ),
            sort = MyNoteSort.TITLE,
            filterKind = MyNoteKind.CHECKLIST,
        )
        val next = doc.reorderTo(listOf("b", "a"))
        assertEquals(MyNoteSort.MANUAL, next.sort)
        assertEquals(listOf("b", "a", "t"), next.copy(filterKind = null).visible().map { it.id })
        assertEquals(listOf("b", "a"), next.visible().map { it.id })
        assertEquals(doc, doc.reorderTo(emptyList()))
    }

    @Test
    fun contentSaveDoesNotDropPinAndPinJumpsToTop() {
        val pinned = note("a", title = "Было", pinned = true, order = 4)
        val other = note("b", order = 1)
        val saved = MyNotesDocument(listOf(pinned, other)).withContent(pinned.copy(title = "Стало", pinned = false, order = 0))
        val stored = saved.notes.first { it.id == "a" }
        assertEquals("Стало", stored.title)
        assertTrue(stored.pinned)
        assertEquals(4, stored.order)

        val jumped = MyNotesDocument(listOf(note("x", order = 1), note("y", order = 2)))
            .setPinned("y", pinned = true, now = 50)
        val moved = jumped.notes.first { it.id == "y" }
        assertTrue(moved.pinned)
        assertEquals(0, moved.order)
        assertEquals(50, moved.updatedAt)
        assertEquals("y", jumped.visible().first().id)
    }

    @Test
    fun deleteRestoreAndCheckToggle() {
        val item = note("a", kind = MyNoteKind.CHECKLIST, checks = listOf(CheckItem("c", "Хлеб")))
        val doc = MyNotesDocument(listOf(item))
        val removed = doc.delete("a")
        assertTrue(removed.notes.isEmpty())
        val back = removed.restore(item)
        assertEquals(listOf("a"), back.notes.map { it.id })
        assertTrue(back.toggleCheck("a", "c", now = 9).notes.single().checks.single().done)
        assertEquals(9, back.toggleCheck("a", "c", now = 9).notes.single().updatedAt)
        assertTrue(back.toggleDone("a", 3).notes.single().done)
    }

    @Test
    fun blankDraftNewLinesAndSketchLimits() {
        assertTrue(note("a", title = "").isBlankContent())
        assertFalse(note("a", title = "Есть").isBlankContent())
        assertFalse(note("a", title = "", pinned = true).isBlankContent())
        assertFalse(note("a", title = "", strokes = listOf(stroke())).isBlankContent())
        val wide = note(
            "a",
            title = "я".repeat(250),
            strokes = listOf(
                SketchStroke(argb = 1, width = 100f, points = emptyList()),
                stroke(width = 80f),
            ),
        ).normalized()
        assertEquals(200, wide.title.length)
        assertEquals(1, wide.strokes.size)
        assertEquals(48f, wide.strokes.single().width)
        val points = (0 until 10).map { SketchPoint(it / 10f, 0.5f) }
        val sampled = decimatePoints(points, 4)
        assertEquals(4, sampled.size)
        assertEquals(points.first(), sampled.first())
        assertEquals(points.last(), sampled.last())
        assertEquals(listOf("b", "a", "c"), moveAt(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("a"), moveAt(listOf("a"), 0, 3))
    }

    @Test
    fun newSectionLandsAfterHouseCareInSavedMenu() {
        val order = BooksMainMenuOrder.normalize(listOf("search", "house_care", "experiment"))
        assertEquals(order.indexOf(BooksMainMenuOrder.HOUSE_CARE) + 1, order.indexOf(BooksMainMenuOrder.MY_NOTES))
        assertEquals("Мои заметки", BooksMainMenuOrder.titleRu(BooksMainMenuOrder.MY_NOTES))
        assertEquals(BooksMainMenuOrder.allIds.distinct().size, BooksMainMenuOrder.allIds.size)
    }

    private fun sampleNote(): MyNote = MyNote(
        id = "full",
        kind = MyNoteKind.RECIPE,
        title = "Оладьи",
        body = "К чаю",
        palette = NotePalette.PEACH,
        pinned = true,
        createdAt = 10,
        updatedAt = 20,
        order = 3,
        checks = listOf(CheckItem("c", "Мука", done = true)),
        dueDate = "2026-11-23",
        priority = TaskPriority.HIGH,
        done = true,
        servings = "4",
        cookTime = "25 мин",
        ingredients = listOf(RecipeLine("i", "Кефир")),
        steps = listOf(RecipeLine("s", "Жарить")),
        strokes = listOf(stroke()),
    ).normalized()

    private fun stroke(width: Float = 5.5f) = SketchStroke(
        argb = 0xFF1D4ED8.toInt(),
        width = width,
        eraser = true,
        points = listOf(SketchPoint(0.25f, 0.5f), SketchPoint(0.75f, 0.25f)),
    )

    private fun note(
        id: String,
        title: String = id,
        kind: MyNoteKind = MyNoteKind.TEXT,
        palette: NotePalette = NotePalette.SAND,
        pinned: Boolean = false,
        created: Long = 0,
        updated: Long = 0,
        order: Int = 0,
        checks: List<CheckItem> = emptyList(),
        strokes: List<SketchStroke> = emptyList(),
    ) = MyNote(
        id = id,
        kind = kind,
        title = title,
        palette = palette,
        pinned = pinned,
        createdAt = created,
        updatedAt = updated,
        order = order,
        checks = checks,
        strokes = strokes,
    )
}
