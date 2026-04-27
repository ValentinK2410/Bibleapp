package com.example.bible.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cifryMathDataStore: DataStore<Preferences> by preferencesDataStore(name = "cifry_math")

private object CifryMathKeys {
    val DIFFICULTY = intPreferencesKey("difficulty_level")
    val HISTORY = stringPreferencesKey("solved_history_v1")
}

private const val HISTORY_SEP = "\u001f"
private const val MAX_HISTORY_LINES = 400

data class CifryMathSolvedEntry(
    val timestampMs: Long,
    val mode: CifryMathMode,
    val a: Int,
    val b: Int,
    val result: Int,
    val difficulty: Int,
    val visualEmoji: String?,
    val expressionText: String,
) {
    fun encode(): String = listOf(
        timestampMs.toString(),
        mode.name,
        a.toString(),
        b.toString(),
        result.toString(),
        difficulty.toString(),
        visualEmoji ?: "",
        expressionText.replace(HISTORY_SEP, " ").replace("\n", " "),
    ).joinToString(HISTORY_SEP)

    companion object {
        fun decode(line: String): CifryMathSolvedEntry? {
            val p = line.split(HISTORY_SEP)
            if (p.size < 8) return null
            return try {
                CifryMathSolvedEntry(
                    timestampMs = p[0].toLong(),
                    mode = CifryMathMode.valueOf(p[1]),
                    a = p[2].toInt(),
                    b = p[3].toInt(),
                    result = p[4].toInt(),
                    difficulty = p[5].toInt(),
                    visualEmoji = p[6].ifEmpty { null },
                    expressionText = p[7],
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Сложность примеров и история верных ответов (вкладка «Мои достижения»). */
class CifryMathRepository(private val context: Context) {

    val difficulty: Flow<Int> = context.cifryMathDataStore.data.map { prefs ->
        (prefs[CifryMathKeys.DIFFICULTY] ?: 0).coerceIn(0, 3)
    }

    val solvedHistory: Flow<List<CifryMathSolvedEntry>> = context.cifryMathDataStore.data.map { prefs ->
        val raw = prefs[CifryMathKeys.HISTORY] ?: return@map emptyList()
        raw.lines().mapNotNull { CifryMathSolvedEntry.decode(it) }.asReversed()
    }

    suspend fun setDifficulty(level: Int) {
        context.cifryMathDataStore.edit { prefs ->
            prefs[CifryMathKeys.DIFFICULTY] = level.coerceIn(0, 3)
        }
    }

    suspend fun appendSolved(entry: CifryMathSolvedEntry) {
        context.cifryMathDataStore.edit { prefs ->
            val old = prefs[CifryMathKeys.HISTORY] ?: ""
            val lines = (old.lines().filter { it.isNotBlank() } + entry.encode())
                .takeLast(MAX_HISTORY_LINES)
            prefs[CifryMathKeys.HISTORY] = lines.joinToString("\n")
        }
    }
}
