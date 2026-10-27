package com.example.bible.data

import java.io.File

internal enum class KidsVoiceKind { CUSTOM_WHOLE, CUSTOM_PARTS, BUNDLED, SOON }

internal data class KidsVoiceClip(val partId: String, val file: File)

internal data class KidsVoicePlan(
    val kind: KidsVoiceKind,
    val whole: File? = null,
    val parts: List<KidsVoiceClip> = emptyList(),
    val assetPath: String? = null,
)

internal data class KidsVoiceNote(val ageId: String, val part: String, val source: String)

/**
 * Своя озвучка родителя: файл истории `storyId/age.m4a` и абзацы `storyId/age/partId.m4a`.
 * Файл и каталог с одним именем возраста могут лежать рядом.
 * Без Android и без org.json, чтобы путь проверялся обычным JVM-тестом.
 */
internal object KidsBibleVoice {
    private val ID = Regex("[a-z0-9_]+")

    fun root(filesDir: File): File = File(filesDir, "kids_voice")

    fun safeId(raw: String): String? = raw.takeIf { ID.matches(it) }

    fun wholeFile(root: File, storyId: String, ageId: String): File? {
        val story = safeId(storyId) ?: return null
        val age = safeId(ageId) ?: return null
        return File(File(root, story), "$age.m4a")
    }

    fun partFile(root: File, storyId: String, ageId: String, partId: String): File? {
        val story = safeId(storyId) ?: return null
        val age = safeId(ageId) ?: return null
        val part = safeId(partId) ?: return null
        return File(File(File(root, story), age), "$part.m4a")
    }

    fun hasWhole(root: File, storyId: String, ageId: String): Boolean = ready(wholeFile(root, storyId, ageId))

    fun partClips(root: File, storyId: String, ageId: String, paragraphCount: Int): List<KidsVoiceClip> {
        if (paragraphCount <= 0) return emptyList()
        return (0 until paragraphCount).mapNotNull { index ->
            val partId = "p$index"
            val file = partFile(root, storyId, ageId, partId) ?: return@mapNotNull null
            if (ready(file)) KidsVoiceClip(partId, file) else null
        }
    }

    fun plan(
        root: File,
        storyId: String,
        ageId: String,
        paragraphCount: Int,
        bundledPath: String?,
    ): KidsVoicePlan {
        val whole = wholeFile(root, storyId, ageId)
        if (ready(whole)) return KidsVoicePlan(KidsVoiceKind.CUSTOM_WHOLE, whole = whole)
        val parts = partClips(root, storyId, ageId, paragraphCount)
        if (parts.isNotEmpty()) return KidsVoicePlan(KidsVoiceKind.CUSTOM_PARTS, parts = parts)
        if (!bundledPath.isNullOrBlank()) return KidsVoicePlan(KidsVoiceKind.BUNDLED, assetPath = bundledPath)
        return KidsVoicePlan(KidsVoiceKind.SOON)
    }

    fun place(source: File, dest: File) {
        check(source.isFile && source.length() > 0L)
        dest.parentFile?.mkdirs()
        source.copyTo(dest, overwrite = true)
    }

    fun deleteWhole(root: File, storyId: String, ageId: String) {
        wholeFile(root, storyId, ageId)?.delete()
        dropNotes(root, storyId, ageId, part = "whole")
    }

    fun deletePart(root: File, storyId: String, ageId: String, partId: String) {
        partFile(root, storyId, ageId, partId)?.delete()
        dropNotes(root, storyId, ageId, part = partId)
    }

    /** Вся своя озвучка этого возраста: и целая история, и абзацы. */
    fun deleteAge(root: File, storyId: String, ageId: String) {
        deleteWhole(root, storyId, ageId)
        val story = safeId(storyId) ?: return
        val age = safeId(ageId) ?: return
        val dir = File(File(root, story), age)
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
        dropNotes(root, storyId, ageId, part = null)
    }

    fun notes(root: File, storyId: String): List<KidsVoiceNote> {
        val file = metaFile(root, storyId) ?: return emptyList()
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull { line ->
            val bits = line.split('\t')
            if (bits.size != 3) return@mapNotNull null
            val age = safeId(bits[0]) ?: return@mapNotNull null
            val part = safeId(bits[1]) ?: return@mapNotNull null
            val source = safeId(bits[2]) ?: return@mapNotNull null
            KidsVoiceNote(age, part, source)
        }
    }

    fun putNote(root: File, storyId: String, ageId: String, part: String, source: String) {
        val age = safeId(ageId) ?: return
        val piece = safeId(part) ?: return
        val from = safeId(source) ?: return
        val kept = notes(root, storyId).filterNot { it.ageId == age && it.part == piece }
        writeNotes(root, storyId, kept + KidsVoiceNote(age, piece, from))
    }

    fun dropNotes(root: File, storyId: String, ageId: String, part: String?) {
        val age = safeId(ageId) ?: return
        val kept = notes(root, storyId).filterNot { it.ageId == age && (part == null || it.part == part) }
        writeNotes(root, storyId, kept)
    }

    private fun writeNotes(root: File, storyId: String, notes: List<KidsVoiceNote>) {
        val file = metaFile(root, storyId) ?: return
        if (notes.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(notes.joinToString("\n") { "${it.ageId}\t${it.part}\t${it.source}" } + "\n")
    }

    private fun metaFile(root: File, storyId: String): File? {
        val story = safeId(storyId) ?: return null
        return File(File(root, story), "sources.txt")
    }

    private fun ready(file: File?): Boolean = file != null && file.isFile && file.length() > 0L
}

internal fun kidsClipAt(durations: IntArray, ms: Int): Pair<Int, Int> {
    if (durations.isEmpty()) return 0 to 0
    var acc = 0
    for (index in durations.indices) {
        val len = durations[index].coerceAtLeast(0)
        val last = index == durations.lastIndex
        if (ms < acc + len || last) return index to (ms - acc).coerceIn(0, len)
        acc += len
    }
    return durations.lastIndex to 0
}

internal fun kidsGlobalMs(durations: IntArray, index: Int, local: Int): Int {
    val prev = durations.take(index.coerceAtLeast(0)).sum()
    return prev + local.coerceAtLeast(0)
}
