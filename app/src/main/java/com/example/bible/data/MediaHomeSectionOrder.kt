package com.example.bible.data

/** Идентификаторы карточек на экране «Каталог медиа» (порядок задаёт пользователь). */
object MediaHomeSectionOrder {
    const val MICROBLOG = "microblog"
    const val PICTURES = "pictures"
    const val MUSICIAN = "musician"
    const val PESNOPENIE = "pesnopenie"
    const val VIDEOS = "videos"
    const val AUDIOS = "audios"

    val allIds: List<String> = listOf(MICROBLOG, PICTURES, MUSICIAN, PESNOPENIE, VIDEOS, AUDIOS)

    fun defaultOrder(): List<String> = allIds.toList()

    /**
     * Нормализует список: только известные id, без дубликатов, в конец — отсутствующие в каноническом порядке.
     */
    fun normalize(ids: List<String>?): List<String> {
        if (ids.isNullOrEmpty()) return defaultOrder()
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        ids.forEach { id ->
            if (id in allIds && id !in seen) {
                seen.add(id)
                out.add(id)
            }
        }
        allIds.forEach { id ->
            if (id !in seen) out.add(id)
        }
        return out
    }

    fun parseStored(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return defaultOrder()
        return normalize(raw.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun toStored(ids: List<String>): String = normalize(ids).joinToString(",")

    fun titleRu(id: String): String = when (id) {
        MICROBLOG -> "Микроблог"
        PICTURES -> "Картинки"
        MUSICIAN -> "Для музыканта"
        PESNOPENIE -> "Песнопение"
        VIDEOS -> "Видео"
        AUDIOS -> "Аудио"
        else -> id
    }
}
