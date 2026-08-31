package com.example.bible.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "microblog_posts")
data class MicroblogPostEntity(
    @PrimaryKey val id: String,
    val body: String,
    val spansJson: String,
    val imagesJson: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
