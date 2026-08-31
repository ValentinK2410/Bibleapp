package com.example.bible.data

import android.content.Context
import java.io.File

/**
 * Единый каталог пользовательских медиа в [Context.filesDir] и три вложенных типа:
 * картинки, видео, аудио.
 */
object MediaCatalogPaths {
    const val ROOT = "media_catalog"
    const val PICTURES = "media_catalog/pictures"
    const val VIDEOS = "media_catalog/videos"
    const val AUDIOS = "media_catalog/audios"
    const val MICROBLOG = "media_catalog/microblog"

    fun picturesDir(context: Context): File = File(context.filesDir, PICTURES).apply { mkdirs() }
    fun videosDir(context: Context): File = File(context.filesDir, VIDEOS).apply { mkdirs() }
    fun audiosDir(context: Context): File = File(context.filesDir, AUDIOS).apply { mkdirs() }
    fun microblogDir(context: Context): File = File(context.filesDir, MICROBLOG).apply { mkdirs() }

    fun pictureFile(context: Context, fileName: String): File = File(picturesDir(context), fileName)
    fun videoFile(context: Context, fileName: String): File = File(videosDir(context), fileName)
    fun audioFile(context: Context, fileName: String): File = File(audiosDir(context), fileName)

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "3gp", "ogv", "mpeg", "mpg", "m4v", "avi")
    private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "m4a", "opus", "aac", "wav", "flac", "m4b")

    fun extensionOfFileName(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    /** Для списков «Медиа → Картинки»: только файлы изображений. */
    fun isLikelyImageFileName(fileName: String): Boolean =
        extensionOfFileName(fileName) in IMAGE_EXTENSIONS

    /** Для списков «Медиа → Видео»: только видеофайлы. */
    fun isLikelyVideoFileName(fileName: String): Boolean =
        extensionOfFileName(fileName) in VIDEO_EXTENSIONS

    /** Для списков «Медиа → Аудио»: только аудиофайлы. */
    fun isLikelyAudioFileName(fileName: String): Boolean =
        extensionOfFileName(fileName) in AUDIO_EXTENSIONS

    fun isLikelyVideoFile(file: File): Boolean = isLikelyVideoFileName(file.name)
    fun isLikelyAudioFile(file: File): Boolean = isLikelyAudioFileName(file.name)
}
