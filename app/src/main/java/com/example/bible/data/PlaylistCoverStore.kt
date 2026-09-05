package com.example.bible.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Копирует обложку плейлиста в [MediaCatalogPaths.PLAYLIST_COVERS]. */
object PlaylistCoverStore {

    suspend fun importFromUri(context: Context, playlistId: String, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = fileNameFor(playlistId)
                val out = MediaCatalogPaths.playlistCoverFile(context, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: error("Не удалось открыть изображение")
                if (!out.exists() || out.length() == 0L) {
                    out.delete()
                    error("Пустой файл")
                }
                name
            }
        }

    suspend fun importFromVideo(context: Context, playlistId: String, videoFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val frame = VideoThumbnailLoader.load(videoFile)
                    ?: error("Не удалось взять кадр из видео")
                val name = fileNameFor(playlistId)
                val out = MediaCatalogPaths.playlistCoverFile(context, name)
                FileOutputStream(out).use { stream ->
                    if (!frame.compress(Bitmap.CompressFormat.JPEG, 88, stream)) {
                        error("Не удалось сохранить кадр")
                    }
                }
                name
            }
        }

    fun delete(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        runCatching { MediaCatalogPaths.playlistCoverFile(context, fileName).delete() }
    }

    private fun fileNameFor(playlistId: String): String =
        "${playlistId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "cover" }}.jpg"
}
