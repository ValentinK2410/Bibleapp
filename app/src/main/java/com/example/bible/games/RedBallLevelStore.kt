package com.example.bible.games

import android.content.Context
import java.io.File

data class RedBallLevelMeta(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val sceneKind: RedBallSceneKind = RedBallSceneKind.MAIN,
    val parentId: String = "",
) {
    fun familyRootId(): String =
        if (sceneKind == RedBallSceneKind.NESTED && parentId.isNotBlank()) parentId else id
}

/**
 * Куда можно вести портал из сцены [editingId].
 * Семья (главная и её вложенные) плюс остальные главные и встроенная горка.
 * Вложенные чужих семей сюда не входят.
 */
fun allowedPortalTargetIds(
    editingId: String,
    sceneKind: RedBallSceneKind,
    parentId: String,
    catalog: List<RedBallLevelMeta>,
): Set<String> {
    val root = if (sceneKind == RedBallSceneKind.NESTED && parentId.isNotBlank()) parentId else editingId
    val ids = mutableSetOf(editingId, root, RedBallScene.BUILTIN_ID)
    for (level in catalog) {
        val main = level.sceneKind != RedBallSceneKind.NESTED || level.parentId.isBlank()
        if (main) ids += level.id
        if (level.sceneKind == RedBallSceneKind.NESTED && level.parentId == root) ids += level.id
    }
    return ids
}

/** Главная сцена и её вложенные — список, который виден только внутри этой семьи. */
fun familyScenes(rootId: String, catalog: List<RedBallLevelMeta>): List<RedBallLevelMeta> {
    return catalog.filter { level ->
        level.id == rootId || (level.sceneKind == RedBallSceneKind.NESTED && level.parentId == rootId)
    }
}

/** Свои уровни лежат JSON-файлами в каталоге приложения и переживают перезапуск. */
class RedBallLevelStore(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "redball_levels"))

    /** Общий список: только главные сцены. Вложенные сюда не попадают. */
    fun list(): List<RedBallLevelMeta> = listAll().filter { it.sceneKind == RedBallSceneKind.MAIN }

    /** Все сцены, уже с починенной иерархией: сирота без родителя становится главной. */
    fun listAll(): List<RedBallLevelMeta> {
        val drafts = readDrafts()
        val byId = drafts.associateBy { it.id }
        return drafts.map { draft ->
            val norm = draft.normalizeHierarchy(byId[draft.parentId])
            RedBallLevelMeta(
                id = norm.id,
                name = norm.name.ifBlank { "Без имени" },
                updatedAt = norm.updatedAt,
                sceneKind = norm.sceneKind,
                parentId = norm.parentId,
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun load(id: String): RedBallDraft? {
        val parsed = readRaw(id) ?: return null
        val parent = if (parsed.sceneKind == RedBallSceneKind.NESTED && parsed.parentId.isNotBlank()) {
            readRaw(parsed.parentId)
        } else {
            null
        }
        return parsed.normalizeHierarchy(parent)
    }

    fun save(draft: RedBallDraft) {
        val id = safeId(draft.id)
        require(id.isNotBlank()) { "пустой id уровня" }
        dir.mkdirs()
        val parent = if (draft.sceneKind == RedBallSceneKind.NESTED && draft.parentId.isNotBlank() && draft.parentId != id) {
            readRaw(draft.parentId)
        } else {
            null
        }
        val norm = draft.copy(id = id).normalizeHierarchy(parent)
        val clean = norm.copy(
            id = id,
            name = norm.name.trim().ifBlank { "Без имени" }.take(32),
            updatedAt = if (norm.updatedAt > 0L) norm.updatedAt else System.currentTimeMillis(),
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
        val safe = safeId(id)
        if (safe.isBlank()) return
        val all = listAll()
        val self = all.firstOrNull { it.id == safe }
        fileFor(safe)?.delete()
        if (self == null || self.sceneKind == RedBallSceneKind.MAIN) {
            for (child in all) {
                if (child.sceneKind == RedBallSceneKind.NESTED && child.parentId == safe) {
                    fileFor(child.id)?.delete()
                }
            }
        }
    }

    private fun readDrafts(): List<RedBallDraft> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { file ->
                val draft = RedBallDraft.fromJson(runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                val id = safeId(draft.id)
                if (id.isBlank()) return@mapNotNull null
                draft.copy(
                    id = id,
                    updatedAt = draft.updatedAt.takeIf { it > 0L } ?: file.lastModified(),
                )
            }
    }

    private fun readRaw(id: String): RedBallDraft? {
        val file = fileFor(id) ?: return null
        if (!file.isFile) return null
        val draft = RedBallDraft.fromJson(runCatching { file.readText() }.getOrNull() ?: return null) ?: return null
        val safe = safeId(draft.id)
        if (safe.isBlank()) return null
        return draft.copy(id = safe)
    }

    private fun fileFor(id: String): File? {
        val safe = safeId(id)
        if (safe.isBlank()) return null
        return File(dir, "$safe.json")
    }

    private fun safeId(id: String): String =
        id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
}
