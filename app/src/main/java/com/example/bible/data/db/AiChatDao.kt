package com.example.bible.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AiChatDao {

    @Insert
    fun insertChat(chat: AiChatEntity): Long

    @Insert
    fun insertMessage(message: AiChatMessageEntity): Long

    @Query("SELECT * FROM ai_chats WHERE provider = :provider ORDER BY updatedAtMs DESC")
    fun listChats(provider: String): List<AiChatEntity>

    @Query("SELECT * FROM ai_chats WHERE id = :id LIMIT 1")
    fun getChat(id: Long): AiChatEntity?

    @Query("SELECT * FROM ai_chat_messages WHERE chatId = :chatId ORDER BY createdAtMs ASC, id ASC")
    fun listMessages(chatId: Long): List<AiChatMessageEntity>

    @Query("UPDATE ai_chat_messages SET content = :content WHERE id = :id")
    fun updateMessage(id: Long, content: String)

    @Query("UPDATE ai_chats SET title = :title, updatedAtMs = :updatedAtMs WHERE id = :id")
    fun updateChat(id: Long, title: String, updatedAtMs: Long)

    @Query("UPDATE ai_chats SET updatedAtMs = :updatedAtMs WHERE id = :id")
    fun touchChat(id: Long, updatedAtMs: Long)

    @Query("DELETE FROM ai_chats WHERE id = :id")
    fun deleteChat(id: Long)

    @Query("DELETE FROM ai_chat_messages WHERE id = :id")
    fun deleteMessage(id: Long)
}
