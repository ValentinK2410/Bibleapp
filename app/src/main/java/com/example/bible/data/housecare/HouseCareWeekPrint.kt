package com.example.bible.data.housecare

import java.time.DayOfWeek
import java.time.LocalDate

/** Пустой квадратик для отметки ручкой и уже отмеченный в приложении. */
internal const val PRINT_BOX_EMPTY = "☐"
internal const val PRINT_BOX_DONE = "☑"

/** Привычка в клетке дня: список один на все дни, отметка — на эту дату. */
internal data class CleaningWeekHabit(
    val name: String,
    val kindLabel: String,
    val done: Boolean,
)

/** Один день печатной недели: подпись «Пн» и дата, привычки и комнаты. */
internal data class CleaningWeekDay(
    val weekday: String,
    val dateLabel: String,
    val rooms: List<CleaningWeekRoom>,
    val habits: List<CleaningWeekHabit> = emptyList(),
)

/** Комната в клетке дня и конкретные разделы, которые в ней убирают. */
internal data class CleaningWeekRoom(
    val name: String,
    val floorName: String,
    val tasks: List<String>,
)

/**
 * Лист печати: заголовок с диапазоном дат и семь клеток Пн–Вс.
 * Собирается из реального плана [HouseCareData]: комнаты этой даты и ежедневные привычки объекта.
 */
internal data class CleaningWeekDocument(
    val title: String,
    val subtitle: String,
    val jobName: String,
    val days: List<CleaningWeekDay>,
) {
    fun toHtml(): String = cleaningWeekHtml(this)
}

private val monthsGenitive = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private fun LocalDate.ruDayMonth(): String = "$dayOfMonth ${monthsGenitive[monthValue - 1]}"

private fun LocalDate.ruFull(): String = "${ruDayMonth()} $year"

private fun LocalDate.dotted(): String = "%02d.%02d".format(dayOfMonth, monthValue)

private fun LocalDate.dottedYear(): String = "%02d.%02d.%04d".format(dayOfMonth, monthValue, year)

/** Заголовок листа: «Неделя 16 — 22 ноября 2026». Месяц и год дописываются там, где они меняются. */
internal fun cleaningWeekTitle(start: LocalDate, end: LocalDate): String {
    val left = when {
        start.year != end.year -> start.ruFull()
        start.month != end.month -> start.ruDayMonth()
        else -> start.dayOfMonth.toString()
    }
    return "Неделя $left — ${end.ruFull()}"
}

/**
 * План уборки на календарную неделю, в которой лежит [weekStart].
 * [floorId] ограничивает лист одним этажом — так печать с экрана этажа совпадает с тем, что на нём видно.
 */
internal fun HouseCareData.cleaningWeekDocument(
    placeId: String,
    weekStart: LocalDate,
    floorId: String? = null,
): CleaningWeekDocument {
    val monday = weekStart.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    val selectedFloor = floorId?.let { floorById(it) }
    val resolvedPlaceId = placeId.ifBlank { selectedFloor?.placeId.orEmpty() }
    val place = placeById(resolvedPlaceId)
    val floors = when {
        selectedFloor != null -> listOf(selectedFloor)
        resolvedPlaceId.isNotBlank() -> floorsOf(resolvedPlaceId)
        else -> emptyList()
    }
    val floorIndex = floors.withIndex().associate { it.value.id to it.index }
    val showFloor = floors.size > 1
    val crossYear = monday.year != sunday.year
    val days = (0..6).map { offset ->
        val date = monday.plusDays(offset.toLong())
        val iso = date.toString()
        val rooms = zones
            .asSequence()
            .filter { it.floorId in floorIndex && it.date == iso }
            .sortedWith(compareBy({ floorIndex[it.floorId] ?: 0 }, { it.createdAt }, { it.name }))
            .map { zone ->
                val tasks = tasksOf(zone.id).map { it.name.trim() }.filter { it.isNotEmpty() }
                CleaningWeekRoom(
                    name = zone.name.trim().ifEmpty { "Комната" },
                    floorName = if (showFloor) floorById(zone.floorId)?.name?.trim().orEmpty() else "",
                    tasks = tasks,
                )
            }
            .toList()
        CleaningWeekDay(
            weekday = WeekDays.shortLabel(date.dayOfWeek.value),
            dateLabel = if (crossYear) date.dottedYear() else date.dotted(),
            rooms = rooms,
            habits = habitsForPrint(resolvedPlaceId, iso),
        )
    }
    val subtitle = buildList {
        place?.name?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        place?.address?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        if (selectedFloor != null) {
            selectedFloor.name.trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
    }.joinToString(" · ")
    val range = "${monday.dottedYear()}–${sunday.dottedYear()}"
    val who = buildList {
        place?.name?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        if (selectedFloor != null) {
            selectedFloor.name.trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
    }.joinToString(", ")
    val job = if (who.isEmpty()) "План уборки $range" else "План уборки · $who · $range"
    return CleaningWeekDocument(
        title = cleaningWeekTitle(monday, sunday),
        subtitle = subtitle,
        jobName = job.take(80),
        days = days,
    )
}

/** Сколько строк займёт день: привычки, комнаты и разделы. Пустая уборка — одна строка «Нет уборки». */
internal fun cleaningWeekDensity(day: CleaningWeekDay): Int {
    val habitLines = if (day.habits.isEmpty()) {
        0
    } else {
        day.habits.map { it.kindLabel }.distinct().size + day.habits.size
    }
    val roomLines = if (day.rooms.isEmpty()) {
        1
    } else {
        day.rooms.sumOf { room ->
            1 + (if (room.floorName.isNotBlank()) 1 else 0) + room.tasks.size.coerceAtLeast(1)
        }
    }
    return habitLines + roomLines
}

/** Кегль текста в клетках: обычная неделя читается крупно, плотный день ужимается, чтобы чаще влезть на один лист. */
internal fun cleaningWeekBodyFontPt(days: List<CleaningWeekDay>): Int {
    val peak = days.maxOfOrNull { cleaningWeekDensity(it) } ?: 1
    return when {
        peak <= 16 -> 10
        peak <= 24 -> 9
        peak <= 34 -> 8
        peak <= 46 -> 7
        else -> 6
    }
}

internal fun escapeHtml(raw: String): String {
    val text = raw.replace("\r\n", "\n").replace('\r', '\n')
    return buildString(text.length) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                '\n' -> append("<br>")
                else -> if (ch.code >= 32) append(ch)
            }
        }
    }
}

internal fun cleaningWeekHtml(document: CleaningWeekDocument): String {
    val fontPt = cleaningWeekBodyFontPt(document.days)
    val headPt = (fontPt + 2).coerceAtMost(12)
    val cells = document.days.joinToString("\n") { day -> dayCell(day, fontPt) }
    val subtitle = if (document.subtitle.isBlank()) {
        ""
    } else {
        """<p class="sub">${escapeHtml(document.subtitle)}</p>"""
    }
    return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
        <meta charset="UTF-8">
        <title>${escapeHtml(document.jobName)}</title>
        <style>
          @page { size: A4 landscape; margin: 8mm 7mm; }
          * { box-sizing: border-box; }
          html, body { margin: 0; padding: 0; }
          body {
            font-family: "Noto Sans", "Roboto", "DejaVu Sans", sans-serif;
            color: #1c2430;
            -webkit-print-color-adjust: exact;
            print-color-adjust: exact;
          }
          .sheet { width: 100%; }
          h1 {
            margin: 0;
            text-align: center;
            font-size: 16pt;
            line-height: 1.2;
            font-weight: 700;
          }
          .kicker {
            margin: 0 0 0.6mm;
            text-align: center;
            font-size: 9pt;
            letter-spacing: 0.04em;
            text-transform: uppercase;
            color: #3e5166;
          }
          .sub {
            margin: 1mm 0 0;
            text-align: center;
            font-size: 10.5pt;
            line-height: 1.25;
          }
          table {
            width: 100%;
            margin-top: 3mm;
            border-collapse: collapse;
            table-layout: fixed;
          }
          td {
            width: 14.28%;
            border: 1.15pt solid #1e3348;
            vertical-align: top;
            height: 158mm;
            padding: 0;
            word-wrap: break-word;
            overflow-wrap: anywhere;
          }
          .head {
            background: #e7f0f8;
            text-align: center;
            padding: 1.5mm 1mm 1.3mm;
            border-bottom: 0.8pt solid #1e3348;
          }
          .dow { font-weight: 700; font-size: ${headPt}pt; line-height: 1.15; }
          .date { font-size: ${fontPt}pt; line-height: 1.2; color: #243044; }
          .body { padding: 1.8mm 1.5mm 2mm; font-size: ${fontPt}pt; line-height: 1.28; }
          .habits { margin: 0 0 1.4mm; padding-bottom: 1.1mm; border-bottom: 0.45pt solid #c5d0dc; }
          .kind { font-weight: 700; font-size: 0.82em; color: #3e5166; margin-top: 0.8mm; }
          .habits .kind:first-child { margin-top: 0; }
          .room { margin-top: 1.8mm; }
          .room:first-child { margin-top: 0; }
          .room-name { font-weight: 700; }
          .floor { color: #3e5166; font-size: 0.86em; }
          .box {
            font-family: "DejaVu Sans", "Noto Sans Symbols 2", "Noto Sans Symbols", "Segoe UI Symbol", sans-serif;
            margin-right: 0.15em;
          }
          .empty, .notasks { color: #5c6b7a; }
          .notasks { font-style: italic; }
        </style>
        </head>
        <body>
        <div class="sheet">
          <p class="kicker">План уборки</p>
          <h1>${escapeHtml(document.title)}</h1>
          $subtitle
          <table>
            <tr>
              $cells
            </tr>
          </table>
        </div>
        </body>
        </html>
    """.trimIndent()
}

private fun dayCell(day: CleaningWeekDay, fontPt: Int): String {
    val habits = habitsBlock(day)
    val rooms = roomsBlock(day)
    val body = listOf(habits, rooms).filter { it.isNotBlank() }.joinToString("\n")
    return """
        <td>
          <div class="head">
            <div class="dow">${escapeHtml(day.weekday)}</div>
            <div class="date">${escapeHtml(day.dateLabel)}</div>
          </div>
          <div class="body" style="font-size:${fontPt}pt">$body</div>
        </td>
    """.trimIndent()
}

/** Утро, затем вечер: один и тот же список в каждом дне, квадратик — на эту дату. */
private fun HouseCareData.habitsForPrint(placeId: String, isoDate: String): List<CleaningWeekHabit> {
    if (placeId.isBlank()) return emptyList()
    val morning = habitsOf(placeId, CleaningHabit.KIND_MORNING)
    val evening = habitsOf(placeId, CleaningHabit.KIND_EVENING)
    val known = setOf(CleaningHabit.KIND_MORNING, CleaningHabit.KIND_EVENING)
    val other = habits.filter { it.placeId == placeId && it.kind !in known }
        .sortedWith(compareBy({ it.order }, { it.createdAt }))
    return (morning + evening + other).mapNotNull { habit ->
        val name = habit.name.trim()
        if (name.isEmpty()) {
            null
        } else {
            CleaningWeekHabit(
                name = name,
                kindLabel = habitKindLabel(habit.kind),
                done = habit.isDoneOn(isoDate),
            )
        }
    }
}

private fun habitKindLabel(kind: String): String = when (kind) {
    CleaningHabit.KIND_MORNING -> "Утро"
    CleaningHabit.KIND_EVENING -> "Вечер"
    else -> "Привычки"
}

private fun habitsBlock(day: CleaningWeekDay): String {
    if (day.habits.isEmpty()) return ""
    val lines = buildString {
        var kind = ""
        day.habits.forEach { habit ->
            if (habit.kindLabel != kind) {
                kind = habit.kindLabel
                append("""<div class="kind">${escapeHtml(kind)}</div>""")
            }
            val box = if (habit.done) PRINT_BOX_DONE else PRINT_BOX_EMPTY
            append("""<div class="habit"><span class="box">$box</span> ${escapeHtml(habit.name)}</div>""")
        }
    }
    return """<div class="habits">$lines</div>"""
}

private fun roomsBlock(day: CleaningWeekDay): String {
    if (day.rooms.isEmpty()) return """<p class="empty">Нет уборки</p>"""
    return day.rooms.joinToString("\n") { room ->
        val floor = if (room.floorName.isBlank()) {
            ""
        } else {
            """<div class="floor">${escapeHtml(room.floorName)}</div>"""
        }
        val tasks = if (room.tasks.isEmpty()) {
            """<div class="notasks">Задачи не заданы</div>"""
        } else {
            room.tasks.joinToString("\n") { task ->
                """<div class="task"><span class="box">$PRINT_BOX_EMPTY</span> ${escapeHtml(task)}</div>"""
            }
        }
        """
        <div class="room">
          <div class="room-name">${escapeHtml(room.name)}</div>
          $floor
          $tasks
        </div>
        """.trimIndent()
    }
}
