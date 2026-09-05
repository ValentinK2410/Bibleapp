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
        LangVocabWordEntity::class,
        LangSrsCardEntity::class,
        AiChatEntity::class,
        AiChatMessageEntity::class,
        MicroblogPostEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun microblogDao(): MicroblogDao

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
                    .addMigrations(
                        StudyDbMigrations.MIGRATION_1_2,
                        StudyDbMigrations.MIGRATION_2_3,
                        StudyDbMigrations.MIGRATION_3_4,
                        StudyDbMigrations.MIGRATION_4_5,
                        StudyDbMigrations.MIGRATION_5_6,
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
