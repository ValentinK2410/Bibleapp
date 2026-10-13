package com.example.bible.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.UUID

data class StudioTrack(
    val id: String,
    val loop: Boolean,
    val file: File,
    /** Когда дорожка вступает на общей шкале, в миллисекундах от нуля. */
    val offsetMs: Long = 0L,
)

/** Записи студии: отдельные дорожки и восемь плиток лупера. */
object StudioLibrary {
    const val PAD_COUNT = 8

    private fun root(context: Context) = File(context.filesDir, "studio").apply { mkdirs() }

    private fun tracksDir(context: Context) = File(root(context), "tracks").apply { mkdirs() }

    private fun padsDir(context: Context) = File(root(context), "pads").apply { mkdirs() }

    private fun indexFile(context: Context) = File(tracksDir(context), "index.txt")

    fun tempFile(context: Context) = File(root(context), "recording.tmp.m4a")

    fun listTracks(context: Context): List<StudioTrack> {
        val dir = tracksDir(context)
        val index = indexFile(context)
        if (!index.exists()) return emptyList()
        return index.readLines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 2) return@mapNotNull null
            val file = File(dir, "${parts[0]}.m4a")
            if (!file.exists() || file.length() <= 800L) null
            else StudioTrack(
                id = parts[0],
                loop = parts[1] == "1",
                file = file,
                offsetMs = parts.getOrNull(2)?.toLongOrNull()?.coerceIn(0L, MAX_OFFSET_MS) ?: 0L,
            )
        }
    }

    fun createTrack(context: Context, source: File): StudioTrack? {
        if (!source.exists() || source.length() <= 800L) {
            if (source.exists()) source.delete()
            return null
        }
        val id = UUID.randomUUID().toString().take(8)
        val dest = File(tracksDir(context), "$id.m4a")
        if (!source.renameTo(dest)) {
            source.copyTo(dest, overwrite = true)
            source.delete()
        }
        val created = StudioTrack(id, loop = false, file = dest)
        writeIndex(context, listTracks(context) + created)
        return created
    }

    fun setLoop(context: Context, id: String, loop: Boolean) {
        writeIndex(context, listTracks(context).map { if (it.id == id) it.copy(loop = loop) else it })
    }

    fun setOffset(context: Context, id: String, offsetMs: Long) {
        val clamped = offsetMs.coerceIn(0L, MAX_OFFSET_MS)
        writeIndex(context, listTracks(context).map { if (it.id == id) it.copy(offsetMs = clamped) else it })
    }

    fun durationMs(file: File): Long {
        if (!file.exists()) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun deleteTrack(context: Context, id: String) {
        File(tracksDir(context), "$id.m4a").delete()
        writeIndex(context, listTracks(context).filter { it.id != id })
    }

    fun padFile(context: Context, index: Int) = File(padsDir(context), "$index.m4a")

    fun hasPad(context: Context, index: Int): Boolean {
        val file = padFile(context, index)
        return file.exists() && file.length() > 800L
    }

    fun savePad(context: Context, index: Int, source: File): Boolean {
        if (!source.exists() || source.length() <= 800L) {
            if (source.exists()) source.delete()
            return false
        }
        val dest = padFile(context, index)
        if (dest.exists()) dest.delete()
        if (!source.renameTo(dest)) {
            source.copyTo(dest, overwrite = true)
            source.delete()
        }
        return dest.exists() && dest.length() > 800L
    }

    fun deletePad(context: Context, index: Int) {
        padFile(context, index).delete()
    }

    private fun writeIndex(context: Context, tracks: List<StudioTrack>) {
        indexFile(context).writeText(
            tracks.joinToString("\n") { "${it.id}|${if (it.loop) 1 else 0}|${it.offsetMs}" },
        )
    }

    const val MAX_OFFSET_MS = 10L * 60L * 1000L

    fun loadCycle(context: Context): StudioCycle {
        val file = File(root(context), "cycle.txt")
        if (!file.exists()) return StudioCycle()
        val parts = runCatching { file.readText() }.getOrDefault("").split('|')
        val start = parts.getOrNull(1)?.toLongOrNull()?.coerceIn(0L, MAX_OFFSET_MS) ?: 0L
        var end = parts.getOrNull(2)?.toLongOrNull()?.coerceIn(0L, MAX_OFFSET_MS) ?: (start + 2_000L)
        if (end < start + MIN_CYCLE_MS) end = (start + MIN_CYCLE_MS).coerceAtMost(MAX_OFFSET_MS)
        return StudioCycle(enabled = parts.getOrNull(0) == "1", startMs = start, endMs = end)
    }

    fun saveCycle(context: Context, cycle: StudioCycle) {
        val start = cycle.startMs.coerceIn(0L, MAX_OFFSET_MS - MIN_CYCLE_MS)
        val end = cycle.endMs.coerceIn(start + MIN_CYCLE_MS, MAX_OFFSET_MS)
        File(root(context), "cycle.txt").writeText("${if (cycle.enabled) 1 else 0}|$start|$end")
    }

    const val MIN_CYCLE_MS = 300L
}

/** Отрезок шкалы, который повторяется, пока цикл включён. */
data class StudioCycle(
    val enabled: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = 2_000L,
)
