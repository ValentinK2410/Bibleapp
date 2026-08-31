package com.example.bible.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MicroblogDao {

    @Query("SELECT * FROM microblog_posts ORDER BY updatedAtMs DESC")
    fun listPosts(): List<MicroblogPostEntity>

    @Query("SELECT * FROM microblog_posts WHERE id = :id LIMIT 1")
    fun getPost(id: String): MicroblogPostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(post: MicroblogPostEntity)

    @Query("DELETE FROM microblog_posts WHERE id = :id")
    fun delete(id: String)
}
