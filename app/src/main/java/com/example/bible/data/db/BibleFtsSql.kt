package com.example.bible.data.db

internal object BibleFtsSql {

    const val CREATE_FTS_TABLE: String =
        "CREATE VIRTUAL TABLE IF NOT EXISTS bible_verses_fts USING fts5(" +
            "searchNorm, translationCode UNINDEXED, bookId UNINDEXED, " +
            "chapterNumber UNINDEXED, verseNumber UNINDEXED, tokenize = 'unicode61'" +
            ")"
}
