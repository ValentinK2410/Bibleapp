package com.example.bible.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lang_vocab_words",
    indices = [
        Index(value = ["langCode"]),
        Index(value = ["langCode", "sourceStableId"], unique = true),
    ],
)
data class LangVocabWordEntity(
    /** Стабильный ключ вида `"english:001"` или hash из пакета. */
    @PrimaryKey val wordKey: String,
    val langCode: String,
    val sourceStableId: String,
    val lemma: String,
    /** Форма для отображения (напр. с диакритикой). */
    val display: String,
    val glossRu: String,
    /** Транскрипция IPA или практическая. */
    val ipa: String?,
    /** Часть речи (из пакета, предвычисленно). */
    val pos: String?,
    val frequencyRank: Int?,
    val exampleL2: String?,
    val exampleRu: String?,
    /** Редакторская мнемоника / ассоциация-крючок. */
    val mnemonicHint: String?,
    /** Корень/шаблон/заметки морфологии (текст или краткий JSON в одной строке). */
    val morphologyNotes: String?,
    /** Версия пакета, которой принадлежит запись. */
    val packVersion: String,
)
