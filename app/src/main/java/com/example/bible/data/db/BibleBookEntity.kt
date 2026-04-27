package com.example.bible.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "bible_books",
    primaryKeys = ["translationCode", "bookId"],
    indices = [Index("translationCode")],
)
data class BibleBookEntity(
    val translationCode: String,
    val bookId: String,
    val name: String,
)
