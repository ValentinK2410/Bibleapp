package com.example.bible.games

import android.content.Context
import java.io.File

data class RedBallLevelMeta(
    val id: String,
    val name: String,
    val updatedAt: Long,
)

/** Свои уровни лежат JSON-файлами в каталоге приложения и переживают перезапуск. */
class RedBallLevelStore(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "redball_levels"))

    fun list(): List<RedBallLevelMeta> {
        val folder = dir
        if (!folder.isDirectory) return emptyList()
        return folder.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { file ->
                val draft = RedBallDraft.fromJson(runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                val id = safeId(draft.id)
                if (id.isBlank()) return@mapNotNull null
                RedBallLevelMeta(
                    id = id,
                    name = draft.name.ifBlank { "Без имени" },
                    updatedAt = draft.updatedAt.takeIf { it > 0L } ?: file.lastModified(),
                )
            }
            .sortedByDescending { it.updatedAt }
    }

    fun load(id: String): RedBallDraft? {
        val file = fileFor(id) ?: return null
        if (!file.isFile) return null
        return RedBallDraft.fromJson(runCatching { file.readText() }.getOrNull() ?: return null)
    }

    fun save(draft: RedBallDraft) {
        val id = safeId(draft.id)
        require(id.isNotBlank()) { "пустой id уровня" }
        dir.mkdirs()
        val clean = draft.copy(
            id = id,
            name = draft.name.trim().ifBlank { "Без имени" }.take(32),
            updatedAt = if (draft.updatedAt > 0L) draft.updatedAt else System.currentTimeMillis(),
        )
        val target = File(dir, "$id.json")
        val tmp = File(dir, "$id.json.tmp")
        tmp.writeText(clean.toJson())
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun rename(id: String, name: String) {
        val draft = load(id) ?: return
        save(draft.copy(name = name.trim().ifBlank { "Без имени" }.take(32), updatedAt = System.currentTimeMillis()))
    }

    fun delete(id: String) {
        fileFor(id)?.delete()
    }

    private fun fileFor(id: String): File? {
        val safe = safeId(id)
        if (safe.isBlank()) return null
        return File(dir, "$safe.json")
    }

    private fun safeId(id: String): String =
        id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
}
