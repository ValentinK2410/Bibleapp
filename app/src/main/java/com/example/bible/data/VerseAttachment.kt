package com.example.bible.data

/**
 * Файл, прикреплённый пользователем к стиху; хранится в каталоге приложения.
 */
data class VerseAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    /** Путь относительно [android.content.Context.getFilesDir]. */
    val relativePath: String,
) {
    fun kind(): AttachmentKind = classifyMime(mimeType)
}

enum class AttachmentKind {
    Audio,
    Image,
    Video,
    Text,
    Other,
}

fun classifyMime(mime: String): AttachmentKind {
    val m = mime.lowercase()
    return when {
        m.startsWith("audio/") -> AttachmentKind.Audio
        m.startsWith("image/") -> AttachmentKind.Image
        m.startsWith("video/") -> AttachmentKind.Video
        m.startsWith("text/") ||
            m == "application/json" ||
            m == "application/xml" ||
            m.endsWith("+xml") -> AttachmentKind.Text
        else -> AttachmentKind.Other
    }
}
