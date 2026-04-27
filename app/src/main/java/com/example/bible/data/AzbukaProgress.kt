package com.example.bible.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.azbukaDataStore: DataStore<Preferences> by preferencesDataStore(name = "azbuka_gamification")

private object AzbukaKeys {
    val POINTS = intPreferencesKey("points")
    val LETTERS_OPENED = stringSetPreferencesKey("letters_opened")
}

/** Очки, открытые буквы и начисления для азбуки. */
class AzbukaProgressRepository(private val context: Context) {

    val points: Flow<Int> = context.azbukaDataStore.data.map { prefs ->
        prefs[AzbukaKeys.POINTS] ?: 0
    }

    val lettersOpenedCount: Flow<Int> = context.azbukaDataStore.data.map { prefs ->
        prefs[AzbukaKeys.LETTERS_OPENED]?.size ?: 0
    }

    suspend fun addPoints(amount: Int) {
        if (amount <= 0) return
        context.azbukaDataStore.edit { prefs ->
            val p = prefs[AzbukaKeys.POINTS] ?: 0
            prefs[AzbukaKeys.POINTS] = p + amount
        }
    }

    /** Первое открытие буквы в карточке: +5 очков. Возвращает true, если буква была новой. */
    suspend fun tryRegisterLetterOpened(upper: Char): Boolean {
        val key = upper.toString()
        var isNew = false
        context.azbukaDataStore.edit { prefs ->
            val set = (prefs[AzbukaKeys.LETTERS_OPENED] ?: emptySet()).toMutableSet()
            if (set.add(key)) {
                isNew = true
                prefs[AzbukaKeys.LETTERS_OPENED] = set
                prefs[AzbukaKeys.POINTS] = (prefs[AzbukaKeys.POINTS] ?: 0) + 5
            }
        }
        return isNew
    }
}

fun azbukaLevel(points: Int): Int = (points / 60).coerceAtLeast(0) + 1

fun azbukaProgressToNextLevel(points: Int): Float {
    val levelStart = ((points / 60).coerceAtLeast(0)) * 60
    val inLevel = points - levelStart
    return (inLevel / 60f).coerceIn(0f, 1f)
}
