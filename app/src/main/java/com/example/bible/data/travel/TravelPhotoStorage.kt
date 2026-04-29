package com.example.bible.data.travel

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** Файловые изображения для карты путешествий (серии по маршруту и метки). */
object TravelPhotoStorage {
    const val ROUTE_SUBDIR = "travel_route_photos"
    const val MARKER_SUBDIR = "travel_marker_photos"

    fun routeSessionDir(context: Context, sessionId: String): File =
        File(File(context.filesDir, ROUTE_SUBDIR), sessionId).apply { mkdirs() }

    fun markerPhotosDir(context: Context): File =
        File(context.filesDir, MARKER_SUBDIR).apply { mkdirs() }

    fun createRouteBurstImageFile(context: Context, sessionId: String): File {
        val dir = routeSessionDir(context, sessionId)
        val name = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        return File(dir, name)
    }

    fun createMarkerImageFile(context: Context): File {
        val dir = markerPhotosDir(context)
        val name = "marker_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        return File(dir, name)
    }

    fun copyUriToJpegFile(context: Context, uri: Uri, dest: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            } ?: return false
            true
        } catch (_: Exception) {
            if (dest.exists()) dest.delete()
            false
        }
    }

    fun toFileUriString(absolutePath: String): String = Uri.fromFile(File(absolutePath)).toString()

    /** Удалить каталог файлов серии снимков по маршруту (после удаления сессии из хранилища). */
    fun deleteRouteSessionDir(context: Context, sessionId: String) {
        runCatching { routeSessionDir(context, sessionId).deleteRecursively() }
    }
}
