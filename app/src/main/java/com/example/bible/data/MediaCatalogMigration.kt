package com.example.bible.data

import android.content.Context
import java.io.File

/**
 * Переносит файлы из старых каталогов ([media_library], [video_library], [audio_library])
 * в [MediaCatalogPaths].
 */
object MediaCatalogMigration {

    fun migrateIfNeeded(context: Context) {
        val base = context.filesDir
        moveFolderContents(base, "media_library", MediaCatalogPaths.PICTURES)
        moveFolderContents(base, "video_library", MediaCatalogPaths.VIDEOS)
        moveFolderContents(base, "audio_library", MediaCatalogPaths.AUDIOS)
    }

    private fun moveFolderContents(base: File, fromRel: String, toRel: String) {
        val from = File(base, fromRel)
        if (!from.isDirectory) return
        val to = File(base, toRel).apply { mkdirs() }
        from.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val dest = File(to, f.name)
            if (!dest.exists()) {
                f.renameTo(dest)
            } else {
                try {
                    f.delete()
                } catch (_: Exception) {
                }
            }
        }
        try {
            if (from.list().isNullOrEmpty()) {
                from.delete()
            }
        } catch (_: Exception) {
        }
    }
}
