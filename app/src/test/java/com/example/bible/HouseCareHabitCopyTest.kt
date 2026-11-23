package com.example.bible

import com.example.bible.data.housecare.CleaningHabit
import com.example.bible.data.housecare.HabitCopyMode
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.applyHabitTemplate
import com.example.bible.data.housecare.cleaningWeekDocument
import com.example.bible.data.housecare.copyDayHabits
import com.example.bible.data.housecare.habitMonthWeekdayTargets
import com.example.bible.data.housecare.habitWeekTargets
import com.example.bible.data.housecare.saveHabitTemplate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseCareHabitCopyTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val tuesday = monday.plusDays(1)

    @Test
    fun legacySharedListStaysOnEveryDay() {
        val raw = """{"habits":[{"id":"h","placeId":"p","kind":"morning","name":"Помолиться","doneDates":["2026-11-16"]}]}"""
        val data = HouseCareData.parse(raw)
        assertEquals("", data.habits.single().date)
        assertTrue(data.habitsOn("p", CleaningHabit.KIND_MORNING, monday).single().isDoneOn(monday.toString()))
        assertFalse(data.habitsOn("p", CleaningHabit.KIND_MORNING, tuesday).single().isDoneOn(tuesday.toString()))
        val roundTrip = HouseCareData.parse(data.toJson().toString())
        assertEquals("Помолиться", roundTrip.habits.single().name)
        assertEquals(
            listOf("Помолиться"),
            data.cleaningWeekDocument("p", monday).days.map { it.habits.single().name }.distinct(),
        )
    }

    @Test
    fun copyDoesNotMoveDoneMarks() {
        val data = shared("Помолиться", doneOn = monday)
        val copied = data.copyDayHabits(
            "p",
            CleaningHabit.KIND_MORNING,
            monday,
            habitWeekTargets(monday),
            HabitCopyMode.REPLACE,
        )
        val tuesdayHabit = copied.habitsOn("p", CleaningHabit.KIND_MORNING, tuesday).single()
        assertEquals("Помолиться", tuesdayHabit.name)
        assertEquals(tuesday.toString(), tuesdayHabit.date)
        assertTrue(tuesdayHabit.doneDates.isEmpty())
        assertTrue(copied.habitsOn("p", CleaningHabit.KIND_MORNING, monday).single().isDoneOn(monday.toString()))
        assertFalse(copied.ownsHabitDay("p", CleaningHabit.KIND_MORNING, monday))
        assertTrue(copied.ownsHabitDay("p", CleaningHabit.KIND_MORNING, tuesday))
        val sheet = copied.cleaningWeekDocument("p", monday)
        assertTrue(sheet.days.first().habits.single().done)
        assertFalse(sheet.days[1].habits.single().done)
    }

    @Test
    fun replaceKeepsTargetMarkForTheSameName() {
        val data = shared("Помолиться", doneOn = tuesday)
        val copied = data.copyDayHabits("p", CleaningHabit.KIND_MORNING, monday, listOf(tuesday), HabitCopyMode.REPLACE)
        assertTrue(copied.habitsOn("p", CleaningHabit.KIND_MORNING, tuesday).single().isDoneOn(tuesday.toString()))
    }

    @Test
    fun mergeKeepsExtraTargetHabit() {
        val withOwn = shared("Помолиться", doneOn = null)
            .copyDayHabits("p", CleaningHabit.KIND_MORNING, monday, listOf(tuesday), HabitCopyMode.REPLACE)
        val teeth = CleaningHabit(
            id = "teeth",
            placeId = "p",
            kind = CleaningHabit.KIND_MORNING,
            name = "Зубы",
            date = tuesday.toString(),
            doneDates = listOf(tuesday.toString()),
        )
        val source = withOwn.copy(
            habits = withOwn.habits + teeth + CleaningHabit(
                id = "dish",
                placeId = "p",
                kind = CleaningHabit.KIND_MORNING,
                name = "Посуда",
            ),
        )
        val merged = source.copyDayHabits("p", CleaningHabit.KIND_MORNING, monday, listOf(tuesday), HabitCopyMode.MERGE)
        assertEquals(
            listOf("Помолиться", "Зубы", "Посуда"),
            merged.habitsOn("p", CleaningHabit.KIND_MORNING, tuesday).map { it.name },
        )
        val tuesdayHabits = merged.habitsOn("p", CleaningHabit.KIND_MORNING, tuesday)
        assertTrue(tuesdayHabits.first { it.name == "Зубы" }.isDoneOn(tuesday.toString()))
        assertFalse(tuesdayHabits.first { it.name == "Посуда" }.isDoneOn(tuesday.toString()))
    }

    @Test
    fun replaceDropsNamesMissingFromSource() {
        val data = HouseCareData(
            habits = listOf(
                CleaningHabit(id = "a", placeId = "p", kind = CleaningHabit.KIND_EVENING, name = "Чтение", date = monday.toString()),
                CleaningHabit(id = "b", placeId = "p", kind = CleaningHabit.KIND_EVENING, name = "Посуда", date = tuesday.toString()),
                CleaningHabit(id = "c", placeId = "p", kind = CleaningHabit.KIND_EVENING, name = "Дневник", date = tuesday.toString()),
            ),
        )
        val replaced = data.copyDayHabits("p", CleaningHabit.KIND_EVENING, monday, listOf(tuesday), HabitCopyMode.REPLACE)
        assertEquals(listOf("Чтение"), replaced.habitsOn("p", CleaningHabit.KIND_EVENING, tuesday).map { it.name })
        assertEquals(listOf("Чтение"), replaced.habitsOn("p", CleaningHabit.KIND_EVENING, monday).map { it.name })
    }

    @Test
    fun emptyOwnDayDoesNotFallBackToSharedList() {
        val copied = shared("Помолиться", doneOn = null).copyDayHabits(
            "p",
            CleaningHabit.KIND_MORNING,
            monday,
            listOf(tuesday),
            HabitCopyMode.REPLACE,
        )
        val cleared = copied.copy(habits = copied.habits.filterNot { it.date == tuesday.toString() })
        assertTrue(cleared.ownsHabitDay("p", CleaningHabit.KIND_MORNING, tuesday))
        assertTrue(cleared.habitsOn("p", CleaningHabit.KIND_MORNING, tuesday).isEmpty())
        assertEquals(listOf("Помолиться"), cleared.habitsOn("p", CleaningHabit.KIND_MORNING, monday).map { it.name })
    }

    @Test
    fun templateKeepsOnlyNames() {
        val data = shared("Помолиться", doneOn = monday)
            .saveHabitTemplate("p", CleaningHabit.KIND_MORNING, monday, "Будни")
        val template = data.habitTemplates.single()
        assertEquals(listOf("Помолиться"), template.items)
        val applied = data.applyHabitTemplate(template.id, tuesday, HabitCopyMode.REPLACE)
        assertTrue(applied.habitsOn("p", CleaningHabit.KIND_MORNING, tuesday).single().doneDates.isEmpty())
        val marked = applied.copy(
            habits = applied.habits.map {
                if (it.date == tuesday.toString()) it.copy(doneDates = listOf(tuesday.toString())) else it
            },
        )
        val again = marked.applyHabitTemplate(template.id, tuesday, HabitCopyMode.REPLACE)
        assertTrue(again.habitsOn("p", CleaningHabit.KIND_MORNING, tuesday).single().isDoneOn(tuesday.toString()))
    }

    @Test
    fun monthCopiesOnlyTheSameWeekday() {
        val mondays = habitMonthWeekdayTargets(monday)
        assertTrue(mondays.all { it.dayOfWeek == monday.dayOfWeek && it.month == monday.month && it != monday })
        assertTrue(mondays.size >= 3)
        val data = shared("Помолиться", doneOn = null)
            .copyDayHabits("p", CleaningHabit.KIND_MORNING, monday, mondays, HabitCopyMode.REPLACE)
        assertTrue(data.ownsHabitDay("p", CleaningHabit.KIND_MORNING, mondays.first()))
        assertFalse(data.ownsHabitDay("p", CleaningHabit.KIND_MORNING, tuesday))
    }

    private fun shared(name: String, doneOn: LocalDate?): HouseCareData = HouseCareData(
        habits = listOf(
            CleaningHabit(
                id = "shared",
                placeId = "p",
                kind = CleaningHabit.KIND_MORNING,
                name = name,
                date = "",
                doneDates = listOfNotNull(doneOn?.toString()),
            ),
        ),
    )
}
