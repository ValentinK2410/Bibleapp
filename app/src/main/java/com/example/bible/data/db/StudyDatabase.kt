package com.example.bible.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StudyChapterCommentaryEntity::class,
        StudyVerseBlobEntity::class,
        StrongsEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        private const val DB_NAME = "study_content.db"

        @Volatile
        private var instance: StudyDatabase? = null

        fun getInstance(context: Context): StudyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudyDatabase::class.java,
                    DB_NAME,
                )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
