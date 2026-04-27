package com.example.bible.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bible_verses ADD COLUMN searchNorm TEXT NOT NULL DEFAULT ''")
        // FTS5 отключён: на части устройств SQLite без модуля fts5.
        // Раньше здесь создавалась bible_verses_fts — см. MIGRATION_2_3.
    }
}

/** Удаление виртуальной таблицы FTS5 (если была) — быстрый поиск только через searchNorm + LIKE. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS bible_verses_fts")
    }
}
