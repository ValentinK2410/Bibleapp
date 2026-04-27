package com.example.bible.data

import java.util.UUID

/**
 * Вложение к метке таймкода: картинка (локальный путь) или текстовая заметка.
 */
data class TimemarkAttachment(
    val kind: String, // "image" | "text"
    val path: String? = null,
    val text: String? = null,
)

/**
 * Одна метка времени: с какого момента (мс) подсвечивать стихи, опционально заметка и вложения.
 */
data class TimemarkCue(
    val timeMs: Long,
    val verseStart: Int,
    val verseEnd: Int? = null,
    val note: String? = null,
    val attachments: List<TimemarkAttachment> = emptyList(),
)

/**
 * Проект синхронизации: текст главы Библии + файл озвучки + метки.
 */
data class TimemarkProject(
    val id: String = UUID.randomUUID().toString(),
    val translationCode: String,
    val bookId: String,
    val chapter: Int,
    val title: String,
    val audioFilePath: String,
    val cues: List<TimemarkCue> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)
