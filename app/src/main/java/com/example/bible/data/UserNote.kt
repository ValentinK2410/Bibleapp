package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NoteSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontSize: Int = 16,
    val colorArgb: Int = 0,
    val bgColorArgb: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("s", start)
        put("e", end)
        if (bold) put("b", true)
        if (italic) put("i", true)
        if (underline) put("u", true)
        if (fontSize != 16) put("fs", fontSize)
        if (colorArgb != 0) put("c", colorArgb)
        if (bgColorArgb != 0) put("bg", bgColorArgb)
    }

    companion object {
        fun fromJson(j: JSONObject): NoteSpan = NoteSpan(
            start = j.getInt("s"),
            end = j.getInt("e"),
            bold = j.optBoolean("b", false),
            italic = j.optBoolean("i", false),
            underline = j.optBoolean("u", false),
            fontSize = j.optInt("fs", 16),
            colorArgb = j.optInt("c", 0),
            bgColorArgb = j.optInt("bg", 0),
        )
    }
}

/** Тип личной записи при изучении Писания. */
enum class UserNoteKind {
    /** Обычная заметка */
    NOTE,
    /** Вопрос к тексту */
    QUESTION,
    /** Ответ / размышление как ответ (можно связать с вопросом) */
    ANSWER,
    /** Размышление, впечатление */
    REFLECTION,
    ;

    fun toJson(): String = name

    companion object {
        fun fromJson(s: String?): UserNoteKind = when (s?.trim()?.takeIf { it.isNotEmpty() }) {
            null -> NOTE
            "QUESTION" -> QUESTION
            "ANSWER" -> ANSWER
            "REFLECTION" -> REFLECTION
            else -> NOTE
        }
    }
}

/**
 * Запись в хронологии «как думал тогда» — обычный текст (без форматирования spans).
 */
data class NoteJournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("ca", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): NoteJournalEntry = NoteJournalEntry(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            text = j.optString("text", ""),
            createdAt = j.optLong("ca", 0L),
        )
    }
}

data class UserNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val spans: List<NoteSpan> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val kind: UserNoteKind = UserNoteKind.NOTE,
    /** Для [UserNoteKind.ANSWER] — id заметки-вопроса */
    val linkedQuestionId: String? = null,
    val verseTranslationCode: String? = null,
    val verseBookId: String? = null,
    val verseChapter: Int? = null,
    val verseVerse: Int? = null,
    val verseBookName: String? = null,
    /** Снимок текста стиха на момент создания заметки (для отображения в редакторе). */
    val verseTextSnapshot: String? = null,
    val journalEntries: List<NoteJournalEntry> = emptyList(),
) {
    /** Привязка к каноническому месту: книга, глава, стих (одинаково для всех переводов). */
    fun hasVerseRef(): Boolean =
        verseBookId != null && verseChapter != null && verseVerse != null

    /** Для навигации; если перевод не сохранён — берётся синодальный. */
    fun verseRefOrNull(): VerseRef? {
        val b = verseBookId ?: return null
        val c = verseChapter ?: return null
        val v = verseVerse ?: return null
        val tid = verseTranslationCode?.let { TranslationId.fromCode(it) } ?: TranslationId.SYNODAL
        return try {
            VerseRef(tid, b, c, v)
        } catch (_: Exception) {
            null
        }
    }

    fun displayVerseLabel(): String? {
        if (!hasVerseRef()) return null
        val name = verseBookName?.trim().orEmpty()
        val ch = verseChapter ?: return null
        val vs = verseVerse ?: return null
        return if (name.isNotEmpty()) "$name $ch:$vs" else "$ch:$vs"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        val spansArr = JSONArray()
        spans.forEach { spansArr.put(it.toJson()) }
        put("spans", spansArr)
        put("ca", createdAt)
        put("ua", updatedAt)
        put("kind", kind.toJson())
        if (linkedQuestionId != null) put("lq", linkedQuestionId)
        if (verseTranslationCode != null) put("vt", verseTranslationCode)
        if (verseBookId != null) put("vb", verseBookId)
        if (verseChapter != null) put("vc", verseChapter)
        if (verseVerse != null) put("vv", verseVerse)
        if (verseBookName != null) put("vbn", verseBookName)
        if (verseTextSnapshot != null) put("vts", verseTextSnapshot)
        if (journalEntries.isNotEmpty()) {
            val ja = JSONArray()
            journalEntries.forEach { ja.put(it.toJson()) }
            put("journal", ja)
        }
    }

    companion object {
        fun fromJson(j: JSONObject): UserNote {
            val spansArr = j.optJSONArray("spans")
            val spans = if (spansArr != null) {
                (0 until spansArr.length()).map { NoteSpan.fromJson(spansArr.getJSONObject(it)) }
            } else {
                emptyList()
            }
            val journalArr = j.optJSONArray("journal")
            val journal = if (journalArr != null) {
                (0 until journalArr.length()).map { NoteJournalEntry.fromJson(journalArr.getJSONObject(it)) }
            } else {
                emptyList()
            }
            return UserNote(
                id = j.getString("id"),
                title = j.optString("title", ""),
                body = j.optString("body", ""),
                spans = spans,
                createdAt = j.optLong("ca", 0L),
                updatedAt = j.optLong("ua", 0L),
                kind = UserNoteKind.fromJson(j.optString("kind", "")),
                linkedQuestionId = if (j.has("lq")) j.getString("lq") else null,
                verseTranslationCode = if (j.has("vt")) j.getString("vt") else null,
                verseBookId = if (j.has("vb")) j.getString("vb") else null,
                verseChapter = if (j.has("vc")) j.getInt("vc") else null,
                verseVerse = if (j.has("vv")) j.getInt("vv") else null,
                verseBookName = if (j.has("vbn")) j.getString("vbn") else null,
                verseTextSnapshot = if (j.has("vts")) j.optString("vts") else null,
                journalEntries = journal,
            )
        }

        fun parseList(json: String): List<UserNote> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<UserNote>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/**
 * Тот же отрывок, что и в [ref] (книга, глава, стих).
 * Код перевода в заметке может отличаться — индикатор «есть заметка» общий для всех переводов.
 */
fun UserNote.matchesVerseLocation(ref: VerseRef): Boolean =
    hasVerseRef() &&
        verseBookId == ref.bookId &&
        verseChapter == ref.chapter &&
        verseVerse == ref.verse

/** Номера стихов главы с заметками; перевод не учитывается — одно место Писания во всех переводах. */
fun List<UserNote>.verseNumbersWithNotesInChapter(
    bookId: String,
    chapter: Int,
): Set<Int> = asSequence()
    .filter { n ->
        n.hasVerseRef() &&
            n.verseBookId == bookId &&
            n.verseChapter == chapter
    }
    .mapNotNull { it.verseVerse }
    .toSet()

/** Краткий текст для списка заметок. */
fun UserNote.previewText(): String {
    if (body.isNotBlank()) return body.trim().take(160)
    val last = journalEntries.maxByOrNull { it.createdAt }
    return last?.text?.trim()?.take(160).orEmpty()
}
