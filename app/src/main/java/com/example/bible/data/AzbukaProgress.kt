package com.example.bible.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val STORIES_READ = stringSetPreferencesKey("stories_read")
    val KIDS_LOCK = booleanPreferencesKey("kids_lock")
    fun wins(game: String) = intPreferencesKey("wins_$game")
}

/** Игры раздела «Детям», за победы в которых начисляются очки. */
object KidsGames {
    const val TIC_TAC_TOE = "tictactoe"
    const val CHECKERS = "checkers"
    const val GO = "go"
    const val PIPES = "pipes"
    val all = listOf(TIC_TAC_TOE, CHECKERS, GO, PIPES)
}

/** Очки, открытые буквы и начисления для азбуки. */
class AzbukaProgressRepository(private val context: Context) {

    val points: Flow<Int> = context.azbukaDataStore.data.map { prefs ->
        prefs[AzbukaKeys.POINTS] ?: 0
    }

    val lettersOpenedCount: Flow<Int> = context.azbukaDataStore.data.map { prefs ->
        prefs[AzbukaKeys.LETTERS_OPENED]?.size ?: 0
    }

    val storiesRead: Flow<Set<String>> = context.azbukaDataStore.data.map { prefs ->
        prefs[AzbukaKeys.STORIES_READ] ?: emptySet()
    }

    val gameWins: Flow<Map<String, Int>> = context.azbukaDataStore.data.map { prefs ->
        KidsGames.all.associateWith { prefs[AzbukaKeys.wins(it)] ?: 0 }
    }

    val kidsLock: Flow<Boolean> = context.azbukaDataStore.data.map { prefs ->
        prefs[AzbukaKeys.KIDS_LOCK] ?: false
    }

    suspend fun setKidsLock(enabled: Boolean) {
        context.azbukaDataStore.edit { prefs -> prefs[AzbukaKeys.KIDS_LOCK] = enabled }
    }

    suspend fun registerGameWin(game: String, points: Int = 20) {
        context.azbukaDataStore.edit { prefs ->
            val key = AzbukaKeys.wins(game)
            prefs[key] = (prefs[key] ?: 0) + 1
            prefs[AzbukaKeys.POINTS] = (prefs[AzbukaKeys.POINTS] ?: 0) + points
        }
    }

    /** Первое прочтение истории: +15 очков. Возвращает true, если история была новой. */
    suspend fun markStoryRead(id: String): Boolean {
        var isNew = false
        context.azbukaDataStore.edit { prefs ->
            val set = (prefs[AzbukaKeys.STORIES_READ] ?: emptySet()).toMutableSet()
            if (set.add(id)) {
                isNew = true
                prefs[AzbukaKeys.STORIES_READ] = set
                prefs[AzbukaKeys.POINTS] = (prefs[AzbukaKeys.POINTS] ?: 0) + 15
            }
        }
        return isNew
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

/** Звёзды раздела «Детям»: одна за каждые 10 очков. */
fun kidsStars(points: Int): Int = (points / 10).coerceAtLeast(0)
