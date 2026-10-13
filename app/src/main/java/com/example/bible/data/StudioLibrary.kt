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

/** Кусок записи. [trimEndMs] меньше нуля — играть до конца файла. */
data class StudioClip(
    val id: String,
    val sourceId: String,
    val laneId: String,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,
    val offsetMs: Long = 0L,
    val loop: Boolean = false,
)

/** Дорожки сверху вниз, включая пустые, и куски на них. */
data class StudioBoard(
    val lanes: List<String> = emptyList(),
    val clips: List<StudioClip> = emptyList(),
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
        val board = loadBoard(context)
        if (board.clips.none { it.sourceId == id }) {
            saveBoard(
                context,
                board.copy(
                    lanes = board.lanes + id,
                    clips = board.clips + StudioClip(id = id, sourceId = id, laneId = id),
                ),
            )
        }
        return created
    }

    fun loadBoard(context: Context): StudioBoard {
        val lanesStore = lanesFile(context)
        val clipsStore = clipsFile(context)
        if (!lanesStore.exists() || !clipsStore.exists()) {
            val migrated = migrateBoard(context)
            saveBoard(context, migrated)
            return migrated
        }
        val lanes = lanesStore.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val known = listTracks(context).map { it.id }.toSet()
        val clips = clipsStore.readLines().mapNotNull(::parseClip).filter { it.sourceId in known }
        clips.forEach { clip ->
            if (clip.laneId !in lanes) lanes.add(clip.laneId)
        }
        return StudioBoard(lanes, clips)
    }

    fun sourceFile(context: Context, sourceId: String) = File(tracksDir(context), "$sourceId.m4a")

    fun addEmptyLane(context: Context): String {
        val id = UUID.randomUUID().toString().take(8)
        val board = loadBoard(context)
        saveBoard(context, board.copy(lanes = board.lanes + id))
        return id
    }

    /** Режет кусок по времени шкалы. Возвращает id правой части. */
    fun splitClip(context: Context, clipId: String, atMs: Long, sourceDurationMs: Long): String? {
        if (sourceDurationMs <= 0L) return null
        val board = loadBoard(context)
        val clip = board.clips.find { it.id == clipId } ?: return null
        val fullEnd = if (clip.trimEndMs < 0L) sourceDurationMs else clip.trimEndMs.coerceAtMost(sourceDurationMs)
        val length = fullEnd - clip.trimStartMs
        val local = atMs - clip.offsetMs
        if (local < MIN_PIECE_MS || length - local < MIN_PIECE_MS) return null
        val cut = clip.trimStartMs + local
        val rightId = UUID.randomUUID().toString().take(8)
        val left = clip.copy(trimEndMs = cut)
        val right = clip.copy(
            id = rightId,
            trimStartMs = cut,
            trimEndMs = clip.trimEndMs,
            offsetMs = atMs.coerceIn(0L, MAX_OFFSET_MS),
        )
        saveBoard(
            context,
            board.copy(
                clips = board.clips.flatMap { item ->
                    if (item.id == clipId) listOf(left, right) else listOf(item)
                },
            ),
        )
        return rightId
    }

    fun moveClip(context: Context, clipId: String, laneSteps: Int) {
        if (laneSteps == 0) return
        val board = loadBoard(context)
        val clip = board.clips.find { it.id == clipId } ?: return
        val from = board.lanes.indexOf(clip.laneId)
        if (from < 0 || board.lanes.isEmpty()) return
        val lane = board.lanes[(from + laneSteps).coerceIn(0, board.lanes.lastIndex)]
        if (lane == clip.laneId) return
        saveBoard(
            context,
            board.copy(clips = board.clips.map { if (it.id == clipId) it.copy(laneId = lane) else it }),
        )
    }

    fun setClipOffset(context: Context, clipId: String, offsetMs: Long) {
        val clamped = offsetMs.coerceIn(0L, MAX_OFFSET_MS)
        val board = loadBoard(context)
        saveBoard(
            context,
            board.copy(clips = board.clips.map { if (it.id == clipId) it.copy(offsetMs = clamped) else it }),
        )
    }

    fun setLaneLoop(context: Context, laneId: String, loop: Boolean) {
        val board = loadBoard(context)
        saveBoard(
            context,
            board.copy(clips = board.clips.map { if (it.laneId == laneId) it.copy(loop = loop) else it }),
        )
    }

    fun deleteLane(context: Context, laneId: String) {
        val board = loadBoard(context)
        val removed = board.clips.filter { it.laneId == laneId }.map { it.sourceId }.toSet()
        val clips = board.clips.filter { it.laneId != laneId }
        saveBoard(context, board.copy(lanes = board.lanes.filter { it != laneId }, clips = clips))
        val still = clips.map { it.sourceId }.toSet()
        removed.filter { it !in still }.forEach { deleteSource(context, it) }
    }

    private fun migrateBoard(context: Context): StudioBoard {
        val tracks = listTracks(context)
        return StudioBoard(
            lanes = tracks.map { it.id },
            clips = tracks.map { track ->
                StudioClip(
                    id = track.id,
                    sourceId = track.id,
                    laneId = track.id,
                    offsetMs = track.offsetMs,
                    loop = track.loop,
                )
            },
        )
    }

    private fun parseClip(line: String): StudioClip? {
        val parts = line.split('|')
        if (parts.size < 7) return null
        return StudioClip(
            id = parts[0],
            sourceId = parts[1],
            laneId = parts[2],
            trimStartMs = parts[3].toLongOrNull()?.coerceAtLeast(0L) ?: return null,
            trimEndMs = parts[4].toLongOrNull() ?: -1L,
            offsetMs = parts[5].toLongOrNull()?.coerceIn(0L, MAX_OFFSET_MS) ?: 0L,
            loop = parts[6] == "1",
        )
    }

    private fun saveBoard(context: Context, board: StudioBoard) {
        lanesFile(context).writeText(board.lanes.joinToString("\n"))
        clipsFile(context).writeText(
            board.clips.joinToString("\n") { clip ->
                "${clip.id}|${clip.sourceId}|${clip.laneId}|${clip.trimStartMs}|${clip.trimEndMs}|${clip.offsetMs}|${if (clip.loop) 1 else 0}"
            },
        )
    }

    private fun deleteSource(context: Context, id: String) {
        File(tracksDir(context), "$id.m4a").delete()
        writeIndex(context, listTracks(context).filter { it.id != id })
    }

    private fun lanesFile(context: Context) = File(root(context), "lanes.txt")

    private fun clipsFile(context: Context) = File(root(context), "clips.txt")

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

    const val MIN_PIECE_MS = 120L
}

/** Отрезок шкалы, который повторяется, пока цикл включён. */
data class StudioCycle(
    val enabled: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = 2_000L,
)
