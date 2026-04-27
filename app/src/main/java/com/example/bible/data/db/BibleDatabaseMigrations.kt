package com.example.bible.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bible_verses ADD COLUMN searchNorm TEXT NOT NULL DEFAULT ''")
        db.execSQL(BibleFtsSql.CREATE_FTS_TABLE)
    }
}
