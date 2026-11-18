package com.example.bible.games

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Куда смотрят зрачки. Ось X вправо, ось Y вниз — как на экране.
 * Оба компонента в диапазоне −1…1: вверх по горке и в прыжке это отрицательный Y.
 */
data class RedBallGaze(val x: Float, val y: Float)

/**
 * Направление взгляда по скорости и плавный шаг к нему.
 * На земле взгляд идёт вдоль скорости: вправо, влево, вверх по склону, вниз по склону.
 * В воздухе подъём смотрит вверх, падение — вниз. Шаг не догоняет цель за один кадр.
 */
object RedBallGazeMath {
    const val SMOOTH = 6.2f

    fun target(
        vx: Float,
        vy: Float,
        grounded: Boolean,
        rising: Boolean,
    ): RedBallGaze {
        if (!grounded) {
            val lookX = (vx / 460f).coerceIn(-1f, 1f)
            val lookY = when {
                rising || vy < -60f -> (-0.55f + vy / 1600f).coerceIn(-1f, -0.45f)
                vy > 70f -> (0.45f + vy / 1100f).coerceIn(0.45f, 1f)
                else -> (vy / 600f).coerceIn(-1f, 1f)
            }
            return RedBallGaze(lookX, lookY)
        }
        val speed = hypot(vx, vy)
        if (speed < 22f) {
            val idle = if (vx < -8f) -0.2f else 0.22f
            return RedBallGaze(idle, 0f)
        }
        val lookX = (vx / 260f).coerceIn(-1f, 1f)
        val along = (vy / 200f).coerceIn(-1f, 1f)
        val lookY = if (abs(vy) < 18f) along * 0.35f else along
        return RedBallGaze(lookX, lookY)
    }

    fun step(current: RedBallGaze, target: RedBallGaze, dt: Float, rate: Float = SMOOTH): RedBallGaze {
        val k = (1f - exp(-rate * dt.coerceAtLeast(0f))).coerceIn(0f, 1f)
        return RedBallGaze(
            (current.x + (target.x - current.x) * k).coerceIn(-1f, 1f),
            (current.y + (target.y - current.y) * k).coerceIn(-1f, 1f),
        )
    }
}
