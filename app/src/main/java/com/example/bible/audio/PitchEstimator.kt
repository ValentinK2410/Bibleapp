package com.example.bible.audio

import kotlin.math.PI
import kotlin.math.cos

/**
 * Основная частота по короткому фрагменту PCM 16-bit.
 * Метод YIN: ищет период, а не самую сильную гармонику, поэтому голос
 * не уезжает на октаву вверх.
 */
object PitchEstimator {

    private const val MIN_HZ = 70.0
    private const val MAX_HZ = 1200.0
    private const val YIN_THRESHOLD = 0.15

    fun estimateHz(samples: ShortArray, sampleRate: Int, minHz: Double = MIN_HZ): Float? {
        val n = samples.size
        if (n < 512) return null

        val minPeriod = (sampleRate / MAX_HZ).toInt().coerceAtLeast(2)
        val maxPeriod = (sampleRate / minHz).toInt().coerceAtMost(n / 2)
        if (maxPeriod <= minPeriod + 4) return null

        val x = DoubleArray(n)
        var mean = 0.0
        for (i in 0 until n) mean += samples[i]
        mean /= n
        val denom = (n - 1).coerceAtLeast(1).toDouble()
        for (i in 0 until n) {
            val window = 0.5 * (1.0 - cos(2.0 * PI * i / denom))
            x[i] = (samples[i] - mean) * window
        }

        val cmnd = DoubleArray(maxPeriod + 1)
        var running = 0.0
        for (tau in 1..maxPeriod) {
            var sum = 0.0
            val limit = n - tau
            for (i in 0 until limit) {
                val diff = x[i] - x[i + tau]
                sum += diff * diff
            }
            running += sum
            cmnd[tau] = if (running > 0.0) sum * tau / running else 1.0
        }

        var tau = minPeriod
        var found = false
        while (tau < maxPeriod) {
            if (cmnd[tau] < YIN_THRESHOLD) {
                while (tau + 1 < maxPeriod && cmnd[tau + 1] < cmnd[tau]) tau++
                found = true
                break
            }
            tau++
        }
        if (!found) {
            var best = minPeriod
            for (t in minPeriod + 1 until maxPeriod) {
                if (cmnd[t] < cmnd[best]) best = t
            }
            if (cmnd[best] > 0.30) return null
            tau = best
            while (tau + 1 < maxPeriod && cmnd[tau + 1] < cmnd[tau]) tau++
        }

        // Если более длинный период (на октаву ниже) заметно яснее, это основной тон,
        // а найденный — гармоника.
        val doubled = tau * 2
        if (doubled + 1 < maxPeriod) {
            val lo = (doubled - 3).coerceAtLeast(minPeriod)
            val hi = (doubled + 3).coerceAtMost(maxPeriod - 1)
            var lower = doubled.coerceIn(lo, hi)
            for (t in lo..hi) {
                if (cmnd[t] < cmnd[lower]) lower = t
            }
            if (cmnd[lower] < 0.12 && cmnd[lower] < cmnd[tau] - 0.04) tau = lower
        }

        if (cmnd[tau] > 0.30) return null
        val refined = parabolic(cmnd, tau)
        if (refined < minPeriod) return null
        val hz = sampleRate / refined
        if (hz < minHz || hz > MAX_HZ) return null
        return hz.toFloat()
    }

    /** Сглаживание показаний (экспоненциальное). */
    fun smooth(previous: Float?, next: Float, alpha: Float = 0.35f): Float {
        if (previous == null) return next
        return previous * (1f - alpha) + next * alpha
    }

    private fun parabolic(cmnd: DoubleArray, tau: Int): Double {
        if (tau <= 0 || tau >= cmnd.lastIndex) return tau.toDouble()
        val s0 = cmnd[tau - 1]
        val s1 = cmnd[tau]
        val s2 = cmnd[tau + 1]
        val denom = 2.0 * (s0 - 2.0 * s1 + s2)
        if (denom == 0.0) return tau.toDouble()
        return tau + (s0 - s2) / denom
    }
}
