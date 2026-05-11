package com.example.bible.data.languagestudy

import com.example.bible.data.db.LangSrsCardEntity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Упрощённый алгоритм SuperMemo SM-2: качество [quality] по шкале 0–5
 * (Again≈2, Hard=3, Good=4, Easy=5 или иные маппинги с кнопок).
 */
object LanguageStudySm2 {

    private const val EASE_MIN = 1.3
    private const val EASE_MAX = 3.5

    fun schedule(
        prev: LangSrsCardEntity?,
        wordKey: String,
        quality: Int,
    ): LangSrsCardEntity {
        val q = quality.coerceIn(0, 5)
        var ease = prev?.easeFactor ?: 2.5
        var repetitions = prev?.repetitions ?: 0
        var interval = prev?.intervalDays ?: 0

        if (q < 3) {
            repetitions = 0
            interval = 0
        } else {
            repetitions++
            interval = when (repetitions) {
                1 -> 1
                2 -> 6
                else -> max(1, (interval * ease).roundToInt())
            }
        }

        ease = ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        ease = ease.coerceIn(EASE_MIN, EASE_MAX)

        val now = System.currentTimeMillis()
        val nextMs = if (interval <= 0) {
            now
        } else {
            now + interval * 86_400_000L
        }

        return LangSrsCardEntity(
            wordKey = wordKey,
            easeFactor = ease,
            intervalDays = interval,
            repetitions = repetitions,
            nextReviewAtEpochMs = nextMs,
            lastReviewAtEpochMs = now,
            userNote = prev?.userNote,
        )
    }
}
