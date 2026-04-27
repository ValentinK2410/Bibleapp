package com.example.bible.data.db

import androidx.room.Entity

@Entity(
    tableName = "study_chapter_commentary",
    primaryKeys = ["slug", "bookId", "chapter"],
)
data class StudyChapterCommentaryEntity(
    val slug: String,
    val bookId: String,
    val chapter: Int,
    val text: String,
)
