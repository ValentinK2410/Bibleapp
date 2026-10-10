package com.example.bible.data

import android.content.Context
import java.io.File
import java.util.UUID

data class StudioTrack(
    val id: String,
    val loop: Boolean,
    val file: File,
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
            else StudioTrack(parts[0], parts[1] == "1", file)
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
            tracks.joinToString("\n") { "${it.id}|${if (it.loop) 1 else 0}" },
        )
    }
}
