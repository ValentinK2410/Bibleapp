package com.example.bible

import com.example.bible.data.housecare.CleaningTask
import com.example.bible.data.housecare.CleaningWeekDocument
import com.example.bible.data.housecare.CleaningWeekRoom
import com.example.bible.data.housecare.CleaningZone
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.HouseFloor
import com.example.bible.data.housecare.HousePlace
import com.example.bible.data.housecare.cleaningWeekBodyFontPt
import com.example.bible.data.housecare.cleaningWeekDocument
import com.example.bible.data.housecare.cleaningWeekTitle
import com.example.bible.data.housecare.escapeHtml
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseCareWeekPrintTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun titleMentionsRangeInsideOneMonth() {
        assertEquals(
            "Неделя 16 — 22 ноября 2026",
            cleaningWeekTitle(monday, monday.plusDays(6)),
        )
    }

    @Test
    fun titleMentionsBothMonthsAndYears() {
        val start = LocalDate.of(2026, 11, 30)
        assertEquals(
            "Неделя 30 ноября — 6 декабря 2026",
            cleaningWeekTitle(start, start.plusDays(6)),
        )
        val yearEnd = LocalDate.of(2026, 12, 28)
        assertEquals(
            "Неделя 28 декабря 2026 — 3 января 2027",
            cleaningWeekTitle(yearEnd, yearEnd.plusDays(6)),
        )
    }

    @Test
    fun weekTableUsesRealRoomsAndTasks() {
        val html = sample().cleaningWeekDocument("p1", monday.plusDays(2)).toHtml()
        assertTrue(html.contains("Неделя 16 — 22 ноября 2026"))
        assertTrue(html.contains("Дом · ул. Лесная, 5"))
        assertTrue(html.contains("Пн"))
        assertTrue(html.contains("Вс"))
        assertTrue(html.contains("16.11"))
        assertTrue(html.contains("22.11"))
        assertTrue(html.contains("Кухня"))
        assertTrue(html.contains("1 этаж"))
        assertTrue(html.contains("Пол"))
        assertTrue(html.contains("Плита &amp; духовка"))
        assertTrue(html.contains("Ванная"))
        assertTrue(html.contains("2 этаж"))
        assertTrue(html.contains("Зеркало"))
        assertTrue(html.contains("Прихожая"))
        assertTrue(html.contains("Коврик"))
        assertTrue(html.contains("Детская &lt;script&gt;"))
        assertTrue(html.contains("Подоконник"))
        assertTrue(html.contains("Нет уборки"))
        assertTrue(html.contains("Задачи не заданы"))
        assertTrue(html.contains("charset=\"UTF-8\""))
        assertTrue(html.contains("size: A4 landscape"))
        assertFalse(html.contains("Веранда"))
        assertFalse(html.contains("Чулан"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun mondayComesBeforeBathroomFloorOnTheSameDay() {
        val doc = sample().cleaningWeekDocument("p1", monday)
        val mondayCell = doc.days.first()
        assertEquals(listOf("Кухня", "Ванная"), mondayCell.rooms.map { it.name })
        assertEquals(listOf("Пол", "Плита & духовка"), mondayCell.rooms.first().tasks)
        assertEquals("Вс", doc.days.last().weekday)
        assertTrue(doc.days.last().rooms.isEmpty())
        assertEquals(7, doc.days.size)
    }

    @Test
    fun floorScreenPrintsOnlyThatFloor() {
        val doc = sample().cleaningWeekDocument("p1", monday, floorId = "f1")
        assertTrue(doc.subtitle.contains("1 этаж"))
        val names = doc.days.flatMap { it.rooms }.map { it.name }
        assertTrue("Кухня" in names)
        assertTrue("Прихожая" in names)
        assertFalse("Ванная" in names)
        assertTrue(doc.days.flatMap { it.rooms }.all { it.floorName.isEmpty() })
    }

    @Test
    fun emptyWeekStillHasSevenDays() {
        val doc = HouseCareData.Empty.cleaningWeekDocument("missing", monday)
        assertEquals(7, doc.days.size)
        assertTrue(doc.days.all { it.rooms.isEmpty() })
        assertEquals("", doc.subtitle)
        val html = doc.toHtml()
        assertEquals(7, Regex("Нет уборки").findAll(html).count())
    }

    @Test
    fun denseWeekUsesSmallerType() {
        val light = CleaningWeekDocument("", "", "", sample().cleaningWeekDocument("p1", monday).days)
        val heavyRooms = List(8) { index ->
            CleaningWeekRoom(
                name = "Комната $index",
                floorName = "1 этаж",
                tasks = List(6) { "Дело $it" },
            )
        }
        val heavyDay = light.days.first().copy(rooms = heavyRooms)
        val heavy = light.copy(days = listOf(heavyDay) + light.days.drop(1))
        assertTrue(cleaningWeekBodyFontPt(heavy.days) < cleaningWeekBodyFontPt(light.days))
        assertTrue(heavy.toHtml().contains("overflow-wrap: anywhere"))
    }

    @Test
    fun escapeKeepsCyrillicAndBreaksLines() {
        assertEquals("Кухня &amp; зал<br>пол", escapeHtml("Кухня & зал\nпол"))
    }

    private fun sample(): HouseCareData {
        val wednesday = "2026-11-18"
        return HouseCareData(
            places = listOf(
                HousePlace(id = "p1", name = "Дом", address = "ул. Лесная, 5"),
                HousePlace(id = "p2", name = "Дача", address = ""),
            ),
            floors = listOf(
                HouseFloor(id = "f1", name = "1 этаж", placeId = "p1"),
                HouseFloor(id = "f2", name = "2 этаж", placeId = "p1"),
                HouseFloor(id = "f3", name = "Терраса", placeId = "p2"),
            ),
            zones = listOf(
                CleaningZone(id = "z1", floorId = "f1", date = monday.toString(), name = "Кухня", createdAt = 1),
                CleaningZone(id = "z2", floorId = "f2", date = monday.toString(), name = "Ванная", createdAt = 2),
                CleaningZone(id = "z3", floorId = "f1", date = "2026-11-21", name = "Прихожая", createdAt = 3),
                CleaningZone(id = "z4", floorId = "f3", date = monday.toString(), name = "Веранда", createdAt = 4),
                CleaningZone(id = "z5", floorId = "f1", date = "2026-11-09", name = "Чулан", createdAt = 5),
                CleaningZone(id = "z6", floorId = "f1", date = wednesday, name = "Детская <script>", createdAt = 6),
                CleaningZone(id = "z7", floorId = "f1", date = "2026-11-20", name = "  ", createdAt = 7),
            ),
            tasks = listOf(
                CleaningTask(id = "t1", zoneId = "z1", name = "Пол", order = 0),
                CleaningTask(id = "t2", zoneId = "z1", name = "Плита & духовка", order = 1),
                CleaningTask(id = "t3", zoneId = "z1", name = "   ", order = 2),
                CleaningTask(id = "t4", zoneId = "z2", name = "Зеркало", order = 0),
                CleaningTask(id = "t5", zoneId = "z3", name = "Коврик", order = 0),
                CleaningTask(id = "t6", zoneId = "z6", name = "Подоконник", order = 0),
            ),
        )
    }
}
