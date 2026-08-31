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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_chats` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `title` TEXT NOT NULL,
                  `createdAtMs` INTEGER NOT NULL,
                  `updatedAtMs` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_chat_messages` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `chatId` INTEGER NOT NULL,
                  `role` TEXT NOT NULL,
                  `content` TEXT NOT NULL,
                  `createdAtMs` INTEGER NOT NULL,
                  FOREIGN KEY(`chatId`) REFERENCES `ai_chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ai_chat_messages_chatId` ON `ai_chat_messages` (`chatId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ai_chat_messages_createdAtMs` ON `ai_chat_messages` (`createdAtMs`)",
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `microblog_posts` (
                  `id` TEXT NOT NULL,
                  `body` TEXT NOT NULL,
                  `spansJson` TEXT NOT NULL,
                  `imagesJson` TEXT NOT NULL,
                  `createdAtMs` INTEGER NOT NULL,
                  `updatedAtMs` INTEGER NOT NULL,
                  PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }
}
