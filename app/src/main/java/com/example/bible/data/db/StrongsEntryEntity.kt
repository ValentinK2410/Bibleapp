package com.example.bible.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strongs_entries")
data class StrongsEntryEntity(
    @PrimaryKey val code: String,
    val lemma: String,
    val translit: String,
    val pronunciation: String,
    val definition: String,
    val kjvUsage: String,
    val origin: String,
)
