package com.example.bible.data.housecare

import java.time.DayOfWeek
import java.time.LocalDate

/** Остальные дни той же недели, Пн–Вс, без дня-источника. */
fun habitWeekTargets(source: LocalDate): List<LocalDate> {
    val monday = source.with(DayOfWeek.MONDAY)
    return (0..6).map { monday.plusDays(it.toLong()) }.filter { it != source }
}

/** Все остальные дни месяца, в котором лежит [source]. */
fun habitMonthTargets(source: LocalDate): List<LocalDate> {
    val first = source.withDayOfMonth(1)
    return (1..source.lengthOfMonth()).map { first.withDayOfMonth(it) }.filter { it != source }
}

/** Тот же день недели в этом месяце, без самого [source]. */
fun habitMonthWeekdayTargets(source: LocalDate): List<LocalDate> =
    habitMonthTargets(source).filter { it.dayOfWeek == source.dayOfWeek }

fun HouseCareData.habitTargetsWithItems(
    placeId: String,
    kind: String,
    targets: List<LocalDate>,
): Int = targets.count { day -> habitsOn(placeId, kind, day).any { it.name.isNotBlank() } }

/**
 * Записывает названия в указанные дни.
 * Отметки источника не переносятся. Если в целевом дне привычка с тем же названием уже отмечена, отметка остаётся.
 */
fun HouseCareData.applyHabitNames(
    placeId: String,
    kind: String,
    names: List<String>,
    targets: List<LocalDate>,
    mode: HabitCopyMode,
): HouseCareData {
    val clean = names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    if (clean.isEmpty() || targets.isEmpty()) return this
    return targets.fold(this) { data, day -> data.writeHabitDay(placeId, kind, day, clean, mode) }
}

/** Копирует список дня [source] на [targets]. Сам источник не меняется. */
fun HouseCareData.copyDayHabits(
    placeId: String,
    kind: String,
    source: LocalDate,
    targets: List<LocalDate>,
    mode: HabitCopyMode,
): HouseCareData {
    val names = habitsOn(placeId, kind, source).map { it.name }
    return applyHabitNames(placeId, kind, names, targets.filter { it != source }, mode)
}

/** Запоминает текущий список дня как шаблон. Отметки в шаблон не попадают. */
fun HouseCareData.saveHabitTemplate(
    placeId: String,
    kind: String,
    day: LocalDate,
    title: String,
): HouseCareData {
    val name = title.trim()
    if (name.isEmpty()) return this
    val items = habitsOn(placeId, kind, day).map { it.name.trim() }.filter { it.isNotEmpty() }
    if (items.isEmpty()) return this
    return copy(
        habitTemplates = habitTemplates + HabitTemplate(
            placeId = placeId,
            kind = kind,
            name = name,
            items = items,
        ),
    )
}

fun HouseCareData.deleteHabitTemplate(id: String): HouseCareData =
    copy(habitTemplates = habitTemplates.filterNot { it.id == id })

fun HouseCareData.applyHabitTemplate(
    templateId: String,
    day: LocalDate,
    mode: HabitCopyMode,
): HouseCareData {
    val template = habitTemplates.firstOrNull { it.id == templateId } ?: return this
    return applyHabitNames(template.placeId, template.kind, template.items, listOf(day), mode)
}

private fun HouseCareData.writeHabitDay(
    placeId: String,
    kind: String,
    day: LocalDate,
    names: List<String>,
    mode: HabitCopyMode,
): HouseCareData {
    val iso = day.toString()
    val existing = habitsOn(placeId, kind, day)
    val doneByName = existing.associate { it.name.trim().lowercase() to it.isDoneOn(iso) }
    val kept = existing.map { it.name.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    val result = when (mode) {
        HabitCopyMode.REPLACE -> names
        HabitCopyMode.MERGE -> {
            val have = kept.map { it.lowercase() }.toMutableSet()
            kept + names.filter { have.add(it.lowercase()) }
        }
    }
    val fresh = result.mapIndexed { index, name ->
        CleaningHabit(
            placeId = placeId,
            kind = kind,
            name = name,
            order = index,
            date = iso,
            doneDates = if (doneByName[name.lowercase()] == true) listOf(iso) else emptyList(),
        )
    }
    val own = HabitOwnDay(placeId = placeId, kind = kind, date = iso)
    return copy(
        habits = habits.filterNot { it.placeId == placeId && it.kind == kind && it.date == iso } + fresh,
        habitOwnDays = habitOwnDays.filterNot { it.placeId == placeId && it.kind == kind && it.date == iso } + own,
    )
}
