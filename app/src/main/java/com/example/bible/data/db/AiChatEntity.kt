package com.example.bible.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ai_chats", indices = [Index(value = ["provider"])])
data class AiChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val provider: String = "deepseek",
)
