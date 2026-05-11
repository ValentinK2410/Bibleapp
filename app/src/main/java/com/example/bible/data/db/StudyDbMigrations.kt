package com.example.bible.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object StudyDbMigrations {

    /** Только добавление таблиц языкового изучения; Strong's и материалы «Изучение» сохраняются. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `lang_vocab_words` (
                  `wordKey` TEXT NOT NULL,
                  `langCode` TEXT NOT NULL,
                  `sourceStableId` TEXT NOT NULL,
                  `lemma` TEXT NOT NULL,
                  `display` TEXT NOT NULL,
                  `glossRu` TEXT NOT NULL,
                  `ipa` TEXT,
                  `pos` TEXT,
                  `frequencyRank` INTEGER,
                  `exampleL2` TEXT,
                  `exampleRu` TEXT,
                  `mnemonicHint` TEXT,
                  `morphologyNotes` TEXT,
                  `packVersion` TEXT NOT NULL,
                  PRIMARY KEY(`wordKey`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lang_vocab_words_langCode` ON `lang_vocab_words` (`langCode`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_lang_vocab_words_langCode_sourceStableId` " +
                    "ON `lang_vocab_words` (`langCode`, `sourceStableId`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `lang_srs_cards` (
                  `wordKey` TEXT NOT NULL,
                  `easeFactor` REAL NOT NULL,
                  `intervalDays` INTEGER NOT NULL,
                  `repetitions` INTEGER NOT NULL,
                  `nextReviewAtEpochMs` INTEGER NOT NULL,
                  `lastReviewAtEpochMs` INTEGER,
                  `userNote` TEXT,
                  PRIMARY KEY(`wordKey`),
                  FOREIGN KEY(`wordKey`) REFERENCES `lang_vocab_words`(`wordKey`) ON UPDATE NO ACTION ON DELETE CASCADE 
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lang_srs_cards_wordKey` ON `lang_srs_cards` (`wordKey`)")
        }
    }
}
