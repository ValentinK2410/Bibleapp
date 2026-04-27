package com.example.bible.audio

/**
 * Оценка основной частоты по короткому фрагменту PCM 16-bit (автокорреляция).
 * Диапазон ~70–1200 Гц — подходит для струн гитары и скрипки.
 */
object PitchEstimator {

    private const val MIN_HZ = 70.0
    private const val MAX_HZ = 1200.0

    fun estimateHz(samples: ShortArray, sampleRate: Int): Float? {
        val n = samples.size
        if (n < 2048) return null

        val x = DoubleArray(n)
        var mean = 0.0
        for (i in 0 until n) {
            val v = samples[i].toDouble()
            x[i] = v
            mean += v
        }
        mean /= n
        for (i in 0 until n) x[i] -= mean

        val minPeriod = (sampleRate / MAX_HZ).toInt().coerceAtLeast(2)
        val maxPeriod = (sampleRate / MIN_HZ).toInt().coerceAtMost(n / 2)
        if (minPeriod >= maxPeriod) return null

        var bestPeriod = minPeriod
        var bestCorr = 0.0
        for (tau in minPeriod..maxPeriod) {
            var c = 0.0
            val limit = n - tau
            for (i in 0 until limit) {
                c += x[i] * x[i + tau]
            }
            if (c > bestCorr) {
                bestCorr = c
                bestPeriod = tau
            }
        }
        if (bestCorr < 1e-8) return null
        val hz = sampleRate.toFloat() / bestPeriod
        if (hz < MIN_HZ || hz > MAX_HZ) return null
        return hz
    }

    /** Сглаживание показаний (экспоненциальное). */
    fun smooth(previous: Float?, next: Float, alpha: Float = 0.35f): Float {
        if (previous == null) return next
        return previous * (1f - alpha) + next * alpha
    }
}
