package com.example.bible.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BibleBookEntity::class, BibleVerseEntity::class, BibleInterlinearWordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BibleDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        private const val DB_NAME = "bible_text.db"

        @Volatile
        private var instance: BibleDatabase? = null

        fun getInstance(context: Context): BibleDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BibleDatabase::class.java,
                    DB_NAME,
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
