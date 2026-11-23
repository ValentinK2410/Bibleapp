package com.example.bible.data.mynotes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.myNotesDataStore: DataStore<Preferences> by preferencesDataStore(name = "my_notes")

private object MyNotesKeys {
    val DOCUMENT = stringPreferencesKey("my_notes_json")
}

/**
 * Блокнот целиком в одном JSON-документе DataStore.
 * Правки живут в [MyNotesDocument], чтобы их можно было проверить без Android.
 * Файл DataStore остаётся в каталоге приложения и переживает перезапуск и обновление.
 */
class MyNotesStore(context: Context) {
    private val app = context.applicationContext

    val document: Flow<MyNotesDocument> = app.myNotesDataStore.data.map { prefs ->
        MyNotesDocument.parse(prefs[MyNotesKeys.DOCUMENT])
    }

    suspend fun update(block: (MyNotesDocument) -> MyNotesDocument) {
        app.myNotesDataStore.edit { prefs ->
            val current = MyNotesDocument.parse(prefs[MyNotesKeys.DOCUMENT])
            prefs[MyNotesKeys.DOCUMENT] = block(current).toJson()
        }
    }
}
