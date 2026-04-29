package com.example.bible.data

import androidx.navigation.NavHostController
import org.json.JSONObject

/** Что восстанавливать при следующем запуске приложения после чтения Библии или заметки. */
enum class LastSessionResumeKind {
    READ,
    DUAL,
    NOTE,
}

data class LastSessionResume(
    val kind: LastSessionResumeKind,
    /** Код перевода [TranslationId.code] для читалки «одна колонка». */
    val translationCode: String = TranslationId.SYNODAL.code,
    val bookId: String = "",
    val chapter: Int = 1,
    /** Стих прокрутки (для маршрута read/.../scrollVerse). */
    val scrollVerse: Int = 0,
    val noteId: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("k", kind.name)
        put("tr", translationCode)
        put("b", bookId)
        put("c", chapter)
        put("v", scrollVerse)
        put("n", noteId)
    }

    companion object {
        fun fromJson(json: String): LastSessionResume? {
            if (json.isBlank()) return null
            return try {
                val j = JSONObject(json)
                val kind = LastSessionResumeKind.valueOf(j.getString("k"))
                LastSessionResume(
                    kind = kind,
                    translationCode = j.optString("tr", TranslationId.SYNODAL.code),
                    bookId = j.optString("b", ""),
                    chapter = j.optInt("c", 1),
                    scrollVerse = j.optInt("v", 0),
                    noteId = j.optString("n", ""),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * По текущему верхнему экрану решаем, сохранять ли «возобновление».
 * Экран «Книги» сбрасывает сохранённое место; остальные экраны не трогают запись (например поверх читалки открыты настройки).
 */
fun resumePersistActionForNavDestination(
    navController: NavHostController,
    translation: TranslationId,
): ResumePersistAction {
    val entry = navController.currentBackStackEntry ?: return ResumePersistAction.KeepExisting
    val route = entry.destination.route ?: return ResumePersistAction.KeepExisting
    return when (route) {
        "books" -> ResumePersistAction.ClearStored
        "read/{bookId}/{chapter}/{scrollVerse}" -> {
            val args = entry.arguments ?: return ResumePersistAction.KeepExisting
            val bookId = args.getString("bookId").orEmpty()
            val chapter = args.getString("chapter")?.toIntOrNull() ?: return ResumePersistAction.KeepExisting
            val scrollVerse = args.getInt("scrollVerse", 0)
            if (BibleCanon.byId(bookId) == null || chapter < 1) {
                ResumePersistAction.KeepExisting
            } else {
                ResumePersistAction.Store(
                    LastSessionResume(
                        kind = LastSessionResumeKind.READ,
                        translationCode = translation.code,
                        bookId = bookId,
                        chapter = chapter,
                        scrollVerse = scrollVerse,
                    ),
                )
            }
        }
        "dual" -> ResumePersistAction.Store(
            LastSessionResume(
                kind = LastSessionResumeKind.DUAL,
                translationCode = translation.code,
            ),
        )
        "note_edit/{noteId}" -> {
            val noteId = entry.arguments?.getString("noteId").orEmpty()
            if (noteId.isBlank()) ResumePersistAction.KeepExisting
            else ResumePersistAction.Store(
                LastSessionResume(
                    kind = LastSessionResumeKind.NOTE,
                    noteId = noteId,
                ),
            )
        }
        else -> ResumePersistAction.KeepExisting
    }
}

sealed class ResumePersistAction {
    data object KeepExisting : ResumePersistAction()
    data object ClearStored : ResumePersistAction()
    data class Store(val resume: LastSessionResume) : ResumePersistAction()
}

fun LastSessionResume.isValidForRestore(): Boolean =
    when (kind) {
        LastSessionResumeKind.READ ->
            BibleCanon.byId(bookId) != null && chapter >= 1
        LastSessionResumeKind.DUAL -> true
        LastSessionResumeKind.NOTE -> noteId.isNotBlank()
    }
