package com.example.bible.data.mynotes

import java.util.ArrayDeque
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Вид записи в разделе «Мои заметки». */
enum class MyNoteKind(val id: String) {
    TEXT("text"),
    CHECKLIST("checklist"),
    TASK("task"),
    RECIPE("recipe"),
    SKETCH("sketch"),
    ;

    fun titleRu(): String = when (this) {
        TEXT -> "Текст"
        CHECKLIST -> "Список"
        TASK -> "Задача"
        RECIPE -> "Рецепт"
        SKETCH -> "Скетч"
    }

    fun hintRu(): String = when (this) {
        TEXT -> "Свободная запись"
        CHECKLIST -> "Пункты с галочками"
        TASK -> "Срок, приоритет и статус"
        RECIPE -> "Ингредиенты и шаги"
        SKETCH -> "Рисунок от руки"
    }

    companion object {
        fun fromId(id: String?): MyNoteKind = fromIdOrNull(id) ?: TEXT

        fun fromIdOrNull(id: String?): MyNoteKind? =
            entries.firstOrNull { it.id == id }
    }
}

enum class MyNoteSort(val id: String) {
    MANUAL("manual"),
    CREATED("created"),
    UPDATED("updated"),
    TITLE("title"),
    COLOR("color"),
    TYPE("type"),
    ;

    fun titleRu(): String = when (this) {
        MANUAL -> "Вручную"
        CREATED -> "По дате создания"
        UPDATED -> "По дате изменения"
        TITLE -> "По названию"
        COLOR -> "По цвету"
        TYPE -> "По типу"
    }

    companion object {
        fun fromId(id: String?): MyNoteSort =
            entries.firstOrNull { it.id == id } ?: MANUAL
    }
}

enum class TaskPriority(val id: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),
    ;

    fun titleRu(): String = when (this) {
        LOW -> "Низкий"
        NORMAL -> "Обычный"
        HIGH -> "Высокий"
    }

    companion object {
        fun fromId(id: String?): TaskPriority =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** Приятная палитра карточки. Порядок совпадает с сортировкой «по цвету». */
enum class NotePalette(val id: String) {
    SAND("sand"),
    SAGE("sage"),
    SKY("sky"),
    LILAC("lilac"),
    PEACH("peach"),
    MINT("mint"),
    ROSE("rose"),
    SLATE("slate"),
    ;

    fun titleRu(): String = when (this) {
        SAND -> "Песок"
        SAGE -> "Шалфей"
        SKY -> "Небо"
        LILAC -> "Сирень"
        PEACH -> "Персик"
        MINT -> "Мята"
        ROSE -> "Роза"
        SLATE -> "Сланец"
    }

    companion object {
        fun fromId(id: String?): NotePalette =
            entries.firstOrNull { it.id == id } ?: SAND
    }
}

data class CheckItem(
    val id: String,
    val text: String = "",
    val done: Boolean = false,
)

data class RecipeLine(
    val id: String,
    val text: String = "",
)

/** Точка штриха в долях холста, от 0 до 1. */
data class SketchPoint(val x: Float, val y: Float)

data class SketchStroke(
    val argb: Int,
    val width: Float,
    val eraser: Boolean = false,
    val points: List<SketchPoint> = emptyList(),
)

data class MyNote(
    val id: String,
    val kind: MyNoteKind,
    val title: String = "",
    val body: String = "",
    val palette: NotePalette = NotePalette.SAND,
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val order: Int = 0,
    val checks: List<CheckItem> = emptyList(),
    val dueDate: String? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val done: Boolean = false,
    val servings: String = "",
    val cookTime: String = "",
    val ingredients: List<RecipeLine> = emptyList(),
    val steps: List<RecipeLine> = emptyList(),
    val strokes: List<SketchStroke> = emptyList(),
) {
    fun displayTitle(): String = title.trim().ifBlank { "Без названия" }

    fun sortTitle(): String = displayTitle().lowercase(Locale("ru"))

    fun isBlankContent(): Boolean {
        if (pinned) return false
        if (title.isNotBlank() || body.isNotBlank()) return false
        if (!dueDate.isNullOrBlank() || servings.isNotBlank() || cookTime.isNotBlank()) return false
        if (done) return false
        if (checks.any { it.text.isNotBlank() }) return false
        if (ingredients.any { it.text.isNotBlank() }) return false
        if (steps.any { it.text.isNotBlank() }) return false
        if (strokes.isNotEmpty()) return false
        return true
    }

    fun matchesQuery(raw: String): Boolean {
        val query = raw.trim()
        if (query.isEmpty()) return true
        val needle = query.lowercase(Locale("ru"))
        fun hit(value: String?): Boolean =
            !value.isNullOrBlank() && value.lowercase(Locale("ru")).contains(needle)
        return hit(title) || hit(body) || hit(kind.titleRu()) || hit(kind.hintRu()) ||
            hit(palette.titleRu()) || hit(priority.titleRu()) || hit(dueDate) ||
            hit(servings) || hit(cookTime) ||
            checks.any { hit(it.text) } ||
            ingredients.any { hit(it.text) } ||
            steps.any { hit(it.text) }
    }

    fun normalized(): MyNote = copy(
        title = title.take(MAX_TITLE),
        body = body.take(MAX_BODY),
        servings = servings.take(MAX_SHORT),
        cookTime = cookTime.take(MAX_SHORT),
        dueDate = dueDate?.takeIf { it.isNotBlank() },
        checks = checks.take(MAX_LINES).map { it.copy(id = it.id, text = it.text.take(MAX_LINE)) },
        ingredients = ingredients.take(MAX_LINES).map { it.copy(text = it.text.take(MAX_LINE)) },
        steps = steps.take(MAX_LINES).map { it.copy(text = it.text.take(MAX_LINE)) },
        strokes = strokes.takeLast(MAX_STROKES).mapNotNull { stroke ->
            val points = decimatePoints(stroke.points, MAX_POINTS_PER_STROKE)
            if (points.isEmpty()) null
            else stroke.copy(points = points, width = stroke.width.coerceIn(MIN_WIDTH, MAX_WIDTH))
        },
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.id)
        put("title", title)
        put("body", body)
        put("palette", palette.id)
        put("pinned", pinned)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("order", order)
        put("dueDate", dueDate ?: JSONObject.NULL)
        put("priority", priority.id)
        put("done", done)
        put("servings", servings)
        put("cookTime", cookTime)
        put("checks", JSONArray().apply { checks.forEach { put(it.toJson()) } })
        put("ingredients", JSONArray().apply { ingredients.forEach { put(it.toJson()) } })
        put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
        put("strokes", JSONArray().apply { strokes.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): MyNote? {
            val id = obj.optString("id").trim()
            if (id.isEmpty()) return null
            return MyNote(
                id = id,
                kind = MyNoteKind.fromId(obj.optString("kind")),
                title = obj.optString("title"),
                body = obj.optString("body"),
                palette = NotePalette.fromId(obj.optString("palette")),
                pinned = obj.optBoolean("pinned", false),
                createdAt = obj.optLong("createdAt", 0L),
                updatedAt = obj.optLong("updatedAt", 0L),
                order = obj.optInt("order", 0),
                checks = obj.optJSONArray("checks").mapObjects().mapNotNull { checkItemFromJson(it) },
                dueDate = obj.optString("dueDate").takeIf { it.isNotBlank() && !obj.isNull("dueDate") },
                priority = TaskPriority.fromId(obj.optString("priority")),
                done = obj.optBoolean("done", false),
                servings = obj.optString("servings"),
                cookTime = obj.optString("cookTime"),
                ingredients = obj.optJSONArray("ingredients").mapObjects().mapNotNull { recipeLineFromJson(it) },
                steps = obj.optJSONArray("steps").mapObjects().mapNotNull { recipeLineFromJson(it) },
                strokes = obj.optJSONArray("strokes").mapObjects().mapNotNull { runCatching { sketchStrokeFromJson(it) }.getOrNull() },
            ).normalized()
        }
    }
}

data class MyNotesDocument(
    val notes: List<MyNote> = emptyList(),
    val sort: MyNoteSort = MyNoteSort.MANUAL,
    val filterKind: MyNoteKind? = null,
    val grid: Boolean = false,
) {
    fun visible(query: String = ""): List<MyNote> =
        sortedNotes(notes, sort).filter { note ->
            (filterKind == null || note.kind == filterKind) && note.matchesQuery(query)
        }

    /** Сохраняет текст заметки и не трогает закреп и ручной порядок. */
    fun withContent(note: MyNote): MyNotesDocument {
        val clean = note.normalized()
        val old = notes.find { it.id == clean.id }
        val stored = if (old == null) {
            clean.copy(order = (notes.minOfOrNull { it.order } ?: 0) - 1)
        } else {
            clean.copy(order = old.order, pinned = old.pinned)
        }
        val next = if (old == null) notes + stored else notes.map { if (it.id == stored.id) stored else it }
        return copy(notes = next)
    }

    fun delete(id: String): MyNotesDocument = copy(notes = notes.filterNot { it.id == id })

    fun restore(note: MyNote): MyNotesDocument =
        if (notes.any { it.id == note.id }) this else copy(notes = notes + note.normalized())

    fun setPinned(id: String, pinned: Boolean, now: Long): MyNotesDocument {
        val note = notes.find { it.id == id } ?: return this
        if (note.pinned == pinned) return this
        val order = if (pinned) {
            (notes.minOfOrNull { it.order } ?: 0) - 1
        } else {
            (notes.maxOfOrNull { it.order } ?: 0) + 1
        }
        return copy(
            notes = notes.map {
                if (it.id == id) it.copy(pinned = pinned, order = order, updatedAt = now) else it
            },
        )
    }

    fun toggleDone(id: String, now: Long): MyNotesDocument = copy(
        notes = notes.map { note ->
            if (note.id != id) note else note.copy(done = !note.done, updatedAt = now)
        },
    )

    fun toggleCheck(noteId: String, itemId: String, now: Long): MyNotesDocument = copy(
        notes = notes.map { note ->
            if (note.id != noteId) note
            else note.copy(
                checks = note.checks.map { item ->
                    if (item.id == itemId) item.copy(done = !item.done) else item
                },
                updatedAt = now,
            )
        },
    )

    /**
     * Расставляет [orderedVisibleIds] в слотах этих же заметок внутри текущего порядка.
     * Скрытые фильтром записи остаются на своих местах. Сортировка становится ручной.
     */
    fun reorderTo(orderedVisibleIds: List<String>): MyNotesDocument {
        val known = notes.associateBy { it.id }
        val desired = orderedVisibleIds.filter { it in known }.distinct()
        if (desired.isEmpty()) return this
        val desiredSet = desired.toSet()
        val queue = ArrayDeque<MyNote>()
        desired.forEach { id -> queue.add(known.getValue(id)) }
        val merged = sortedNotes(notes, sort).map { note ->
            if (note.id in desiredSet) queue.remove() else note
        }.mapIndexed { index, note -> note.copy(order = index) }
        return copy(notes = merged, sort = MyNoteSort.MANUAL)
    }

    fun toJson(): String = JSONObject().apply {
        put("version", 1)
        put("sort", sort.id)
        put("filter", filterKind?.id ?: "")
        put("grid", grid)
        put("notes", JSONArray().apply { notes.forEach { put(it.toJson()) } })
    }.toString()

    companion object {
        val Empty = MyNotesDocument()

        fun parse(json: String?): MyNotesDocument {
            if (json.isNullOrBlank()) return Empty
            return try {
                val root = JSONObject(json)
                MyNotesDocument(
                    notes = root.optJSONArray("notes").mapObjects().mapNotNull { obj ->
                        runCatching { MyNote.fromJson(obj) }.getOrNull()
                    },
                    sort = MyNoteSort.fromId(root.optString("sort")),
                    filterKind = MyNoteKind.fromIdOrNull(root.optString("filter")),
                    grid = root.optBoolean("grid", false),
                )
            } catch (_: Exception) {
                Empty
            }
        }
    }
}

internal const val MAX_TITLE = 200
internal const val MAX_BODY = 20_000
internal const val MAX_SHORT = 40
internal const val MAX_LINE = 500
internal const val MAX_LINES = 300
internal const val MAX_STROKES = 180
internal const val MAX_POINTS_PER_STROKE = 480
internal const val MIN_WIDTH = 1f
internal const val MAX_WIDTH = 48f

internal fun sortedNotes(notes: List<MyNote>, sort: MyNoteSort): List<MyNote> =
    notes.sortedWith { left, right ->
        val pin = right.pinned.compareTo(left.pinned)
        if (pin != 0) {
            pin
        } else {
            val tail = compareNotes(left, right, sort)
            if (tail != 0) tail else left.id.compareTo(right.id)
        }
    }

internal fun compareNotes(left: MyNote, right: MyNote, sort: MyNoteSort): Int = when (sort) {
    MyNoteSort.MANUAL -> left.order.compareTo(right.order).ifZero {
        right.updatedAt.compareTo(left.updatedAt)
    }
    MyNoteSort.CREATED -> right.createdAt.compareTo(left.createdAt).ifZero {
        left.sortTitle().compareTo(right.sortTitle())
    }
    MyNoteSort.UPDATED -> right.updatedAt.compareTo(left.updatedAt).ifZero {
        left.sortTitle().compareTo(right.sortTitle())
    }
    MyNoteSort.TITLE -> left.sortTitle().compareTo(right.sortTitle())
    MyNoteSort.COLOR -> left.palette.ordinal.compareTo(right.palette.ordinal).ifZero {
        left.sortTitle().compareTo(right.sortTitle())
    }
    MyNoteSort.TYPE -> left.kind.ordinal.compareTo(right.kind.ordinal).ifZero {
        left.sortTitle().compareTo(right.sortTitle())
    }
}

private fun Int.ifZero(next: () -> Int): Int = if (this != 0) this else next()

fun <T> moveAt(list: List<T>, from: Int, to: Int): List<T> {
    if (from == to || from !in list.indices || to !in list.indices) return list
    val next = list.toMutableList()
    val item = next.removeAt(from)
    next.add(to, item)
    return next
}

internal fun decimatePoints(points: List<SketchPoint>, max: Int): List<SketchPoint> {
    if (points.size <= max || max < 1) return points
    if (max == 1) return listOf(points.last())
    val lastIndex = points.lastIndex
    return List(max) { index ->
        val source = ((index.toFloat() / (max - 1)) * lastIndex).toInt().coerceIn(0, lastIndex)
        points[source]
    }
}

internal fun encodePoints(points: List<SketchPoint>): String =
    points.joinToString(" ") { point ->
        val x = point.x.coerceIn(0f, 1f)
        val y = point.y.coerceIn(0f, 1f)
        String.format(Locale.US, "%.4f,%.4f", x, y)
    }

internal fun decodePoints(raw: String): List<SketchPoint> =
    raw.split(' ').mapNotNull { token ->
        if (token.isBlank()) return@mapNotNull null
        val parts = token.split(',')
        if (parts.size != 2) return@mapNotNull null
        val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
        val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
        SketchPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

private fun CheckItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("text", text)
    put("done", done)
}

private fun checkItemFromJson(obj: JSONObject): CheckItem? {
    val id = obj.optString("id").trim()
    if (id.isEmpty()) return null
    return CheckItem(id = id, text = obj.optString("text"), done = obj.optBoolean("done", false))
}

private fun RecipeLine.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("text", text)
}

private fun recipeLineFromJson(obj: JSONObject): RecipeLine? {
    val id = obj.optString("id").trim()
    if (id.isEmpty()) return null
    return RecipeLine(id = id, text = obj.optString("text"))
}

private fun SketchStroke.toJson(): JSONObject = JSONObject().apply {
    put("argb", argb)
    put("width", width.toDouble())
    put("eraser", eraser)
    put("points", encodePoints(points))
}

private fun sketchStrokeFromJson(obj: JSONObject): SketchStroke? {
    val points = decodePoints(obj.optString("points"))
    if (points.isEmpty()) return null
    return SketchStroke(
        argb = obj.optInt("argb"),
        width = obj.optDouble("width", 4.0).toFloat(),
        eraser = obj.optBoolean("eraser", false),
        points = points,
    )
}

private fun JSONArray?.mapObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { add(it) }
        }
    }
}
