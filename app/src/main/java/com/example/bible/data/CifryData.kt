package com.example.bible.data

import kotlin.math.abs
import kotlin.random.Random

data class DigitInfo(
    val value: Int,
    /** Как произносит диктор (существительное). */
    val nameRu: String,
    /** Короткая подсказка на карточке. */
    val hintRu: String,
)

object CifryRepository {
    val DIGITS: List<DigitInfo> = listOf(
        DigitInfo(0, "ноль", "Пусто, ничего"),
        DigitInfo(1, "один", "Первый шаг"),
        DigitInfo(2, "два", "Пара"),
        DigitInfo(3, "три", "Троица"),
        DigitInfo(4, "четыре", "Четыре угла"),
        DigitInfo(5, "пять", "Пять пальцев"),
        DigitInfo(6, "шесть", "Шесть дней творения"),
        DigitInfo(7, "семь", "Семь дней недели"),
        DigitInfo(8, "восемь", "Больше семи"),
        DigitInfo(9, "девять", "Почти десять"),
    )

    fun nameForValue(v: Int): String =
        DIGITS.find { it.value == v }?.nameRu ?: v.toString()
}

/** Цифры слева направо как одно число (например 2,0,5 → 205). */
fun digitsChainToInt(digits: List<Int>): Int =
    digits.fold(0) { acc, d -> acc * 10 + d.coerceIn(0, 9) }

/** Режимы вкладки «Математика». */
enum class CifryMathMode {
    PLUS,
    MINUS,
    MULT,
    DIV,
}

/** Верхняя граница слагаемых и т.п. по уровню сложности (как раньше без своих диапазонов). */
fun cifryMathMaxOperand(difficulty: Int): Int =
    when (difficulty.coerceIn(0, 3)) {
        0 -> 10
        1 -> 20
        2 -> 50
        else -> 99
    }

/** Верхняя граница множителей для умножения. */
fun cifryMathMultOperandCap(difficulty: Int): Int =
    when (difficulty.coerceIn(0, 3)) {
        0 -> 10
        1 -> 15
        2 -> 25
        else -> 50
    }

/** Верхняя граница делителя (не ниже 2 для генерации). */
fun cifryMathDivisorCap(difficulty: Int): Int =
    when (difficulty.coerceIn(0, 3)) {
        0 -> 10
        1 -> 15
        2 -> 25
        else -> 30
    }.coerceAtLeast(2)

/** Диапазоны первого и второго числа в примере; [Auto] — как при обычной сложности. */
sealed interface OperandBoundsMode {
    data object Auto : OperandBoundsMode

    data class Custom(
        val minA: Int,
        val maxA: Int,
        val minB: Int,
        val maxB: Int,
        /** Первое число всегда одно и то же ([minA]==[maxA] после нормализации). */
        val pinFirst: Boolean = false,
        /** Второе число всегда одно и то же ([minB]==[maxB]). */
        val pinSecond: Boolean = false,
    ) : OperandBoundsMode {
        fun normalized(mode: CifryMathMode, difficulty: Int): Custom {
            val d = difficulty.coerceIn(0, 3)
            return when (mode) {
                CifryMathMode.PLUS,
                CifryMathMode.MINUS,
                -> {
                    val cap = cifryMathMaxOperand(d)
                    clampBoth(cap, cap, minDivisorOne = false)
                }
                CifryMathMode.MULT -> {
                    val cap = cifryMathMultOperandCap(d)
                    clampBoth(cap, cap, minDivisorOne = false)
                }
                CifryMathMode.DIV -> {
                    val capA = cifryMathMaxOperand(d)
                    val capB = minOf(cifryMathDivisorCap(d), capA).coerceAtLeast(2)
                    var nbMin = minB.coerceIn(1, capB)
                    var nbMax = maxB.coerceIn(1, capB).coerceAtLeast(nbMin)
                    var naMin = minA.coerceIn(0, capA)
                    var naMax = maxA.coerceIn(0, capA).coerceAtLeast(naMin)
                    if (nbMin > nbMax) {
                        nbMax = nbMin
                    }
                    if (naMin > naMax) {
                        naMax = naMin
                    }
                    var faMin = naMin
                    var faMax = naMax
                    var fbMin = nbMin
                    var fbMax = nbMax
                    if (pinFirst) faMax = faMin
                    if (pinSecond) fbMax = fbMin
                    Custom(faMin, faMax, fbMin, fbMax, pinFirst = pinFirst, pinSecond = pinSecond)
                }
            }
        }

        private fun clampBoth(capA: Int, capB: Int, minDivisorOne: Boolean): Custom {
            var naMin = minA.coerceIn(0, capA)
            var naMax = maxA.coerceIn(0, capA).coerceAtLeast(naMin)
            var nbMin = minB.coerceIn(0, capB)
            var nbMax = maxB.coerceIn(0, capB).coerceAtLeast(nbMin)
            if (minDivisorOne && nbMin < 1) nbMin = 1.coerceAtMost(nbMax.coerceAtLeast(1))
            if (nbMin > nbMax) nbMax = nbMin
            if (naMin > naMax) naMax = naMin
            if (pinFirst) {
                naMax = naMin
            }
            if (pinSecond) {
                nbMax = nbMin
            }
            return Custom(naMin, naMax, nbMin, nbMax, pinFirst = pinFirst, pinSecond = pinSecond)
        }
    }
}

fun OperandBoundsMode.encodeToPrefs(): String =
    when (this) {
        OperandBoundsMode.Auto -> "auto"
        is OperandBoundsMode.Custom ->
            "$minA,$maxA,$minB,$maxB,${if (pinFirst) 1 else 0},${if (pinSecond) 1 else 0}"
    }

fun decodeOperandBoundsMode(raw: String?): OperandBoundsMode {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty() || s == "auto") return OperandBoundsMode.Auto
    val p = s.split(',')
    if (p.size < 4) return OperandBoundsMode.Auto
    return try {
        val pinFirst = p.getOrNull(4)?.trim()?.toIntOrNull()?.let { it != 0 } ?: false
        val pinSecond = p.getOrNull(5)?.trim()?.toIntOrNull()?.let { it != 0 } ?: false
        OperandBoundsMode.Custom(
            minA = p[0].toInt(),
            maxA = p[1].toInt(),
            minB = p[2].toInt(),
            maxB = p[3].toInt(),
            pinFirst = pinFirst,
            pinSecond = pinSecond,
        )
    } catch (_: Exception) {
        OperandBoundsMode.Auto
    }
}

/**
 * Тема «картинок» для сложения/вычитания (эмодзи как наглядные объекты).
 */
data class MathVisualTheme(
    val emoji: String,
    val nameRuPlural: String,
)

object MathVisualThemes {
    /** Эмодзи в UTF-16 escape, чтобы файл стабильно компилировался. */
    val all: List<MathVisualTheme> = listOf(
        MathVisualTheme("\uD83E\uDD62", "палочки"),
        MathVisualTheme("\uD83C\uDF66", "эскимо"),
        MathVisualTheme("\uD83C\uDF4E", "яблоки"),
        MathVisualTheme("\uD83E\uDD55", "морковки"),
        MathVisualTheme("\uD83C\uDF6A", "печеньки"),
        MathVisualTheme("\u2B50", "звёздочки"),
        MathVisualTheme("\uD83C\uDF38", "цветочки"),
        MathVisualTheme("\uD83D\uDC1F", "рыбки"),
        MathVisualTheme("\uD83C\uDF53", "клубнички"),
        MathVisualTheme("\uD83C\uDF4C", "бананы"),
    )
}

/**
 * Задача: + и × — сумма и произведение; − — разность; деление — целое частное.
 */
data class CifryMathProblem(
    val mode: CifryMathMode,
    val a: Int,
    val b: Int,
    val result: Int,
    /** Для +/− при a≤10 и b≤10 — ряды эмодзи. */
    val visualTheme: MathVisualTheme?,
) {
    fun promptRu(): String {
        val an = CifryRepository.nameForValue(a)
        val bn = CifryRepository.nameForValue(b)
        return when (mode) {
            CifryMathMode.PLUS -> "Сколько будет $an плюс $bn?"
            CifryMathMode.MINUS -> "Сколько будет $an минус $bn?"
            CifryMathMode.MULT -> "Сколько будет $an умножить на $bn?"
            CifryMathMode.DIV ->
                if (b == 0) "Сколько будет $an разделить на ноль?"
                else "Сколько будет $an разделить на $bn?"
        }
    }

    fun promptShortRu(): String = when (mode) {
        CifryMathMode.PLUS -> "$a + $b"
        CifryMathMode.MINUS -> "$a − $b"
        CifryMathMode.MULT -> "$a × $b"
        CifryMathMode.DIV -> "$a \u00F7 $b"
    }

    fun expressionWithResultRu(): String = when (mode) {
        CifryMathMode.PLUS -> "$a + $b = $result"
        CifryMathMode.MINUS -> "$a − $b = $result"
        CifryMathMode.MULT -> "$a × $b = $result"
        CifryMathMode.DIV -> "$a \u00F7 $b = $result"
    }
}

/**
 * [difficulty]: 0 — до ~10, 1 — до ~20, 2 — до ~50, 3 — до ~99.
 * [operandBounds]: свои минимумы/максимумы для первого и второго числа (пересекаются с потолком сложности).
 */
fun nextCifryMathProblem(
    mode: CifryMathMode,
    difficulty: Int,
    random: Random,
    operandBounds: OperandBoundsMode = OperandBoundsMode.Auto,
): CifryMathProblem {
    val d = difficulty.coerceIn(0, 3)
    val maxOperand = cifryMathMaxOperand(d)
    fun maybeVisual(a: Int, b: Int): MathVisualTheme? =
        if (a <= 10 && b <= 10) MathVisualThemes.all.random(random) else null

    val boundsNorm: OperandBoundsMode.Custom? =
        when (operandBounds) {
            OperandBoundsMode.Auto -> null
            is OperandBoundsMode.Custom ->
                operandBounds.normalized(mode, d)
        }

    if (boundsNorm != null) {
        nextCifryMathProblemCustom(mode, d, maxOperand, boundsNorm, random, ::maybeVisual)?.let { return it }
    }

    return when (mode) {
        CifryMathMode.PLUS -> {
            val a = random.nextInt(0, maxOperand + 1)
            val b = random.nextInt(0, maxOperand + 1)
            CifryMathProblem(mode, a, b, a + b, maybeVisual(a, b))
        }
        CifryMathMode.MINUS -> {
            val a = random.nextInt(0, maxOperand + 1)
            val b = random.nextInt(0, a + 1)
            CifryMathProblem(mode, a, b, a - b, maybeVisual(a, b))
        }
        CifryMathMode.MULT -> {
            val cap = cifryMathMultOperandCap(d)
            val a = random.nextInt(0, cap + 1)
            val b = random.nextInt(0, cap + 1)
            CifryMathProblem(mode, a, b, a * b, null)
        }
        CifryMathMode.DIV -> {
            val bMax = minOf(cifryMathDivisorCap(d), maxOperand).coerceAtLeast(2)
            val b = random.nextInt(1, bMax + 1)
            val maxQ = (maxOperand / b).coerceAtLeast(0)
            val quotient = random.nextInt(0, maxQ + 1)
            val a = quotient * b
            CifryMathProblem(mode, a, b, quotient, null)
        }
    }
}

private fun nextCifryMathProblemCustom(
    mode: CifryMathMode,
    difficulty: Int,
    maxOperand: Int,
    bounds: OperandBoundsMode.Custom,
    random: Random,
    maybeVisual: (Int, Int) -> MathVisualTheme?,
): CifryMathProblem? {
    repeat(140) {
        when (mode) {
            CifryMathMode.PLUS -> {
                val a = random.nextInt(bounds.minA, bounds.maxA + 1)
                val x = random.nextInt(bounds.minB, bounds.maxB + 1)
                if (a <= maxOperand && x <= maxOperand) {
                    return CifryMathProblem(mode, a, x, a + x, maybeVisual(a, x))
                }
            }
            CifryMathMode.MINUS -> {
                val a = random.nextInt(bounds.minA, bounds.maxA + 1)
                val subMin = bounds.minB.coerceAtMost(a)
                val subMax = bounds.maxB.coerceAtMost(a).coerceAtLeast(subMin)
                if (subMin <= subMax) {
                    val x = random.nextInt(subMin, subMax + 1)
                    return CifryMathProblem(mode, a, x, a - x, maybeVisual(a, x))
                }
            }
            CifryMathMode.MULT -> {
                val a = random.nextInt(bounds.minA, bounds.maxA + 1)
                val x = random.nextInt(bounds.minB, bounds.maxB + 1)
                return CifryMathProblem(mode, a, x, a * x, null)
            }
            CifryMathMode.DIV -> {
                val rbMin = bounds.minB
                val rbMax = bounds.maxB
                if (rbMin > rbMax || rbMin < 1) return null
                val raMin = bounds.minA
                val raMax = bounds.maxA
                val dividendFixed = bounds.pinFirst || raMin == raMax
                val divisorFixed = bounds.pinSecond || rbMin == rbMax

                if (dividendFixed && divisorFixed) {
                    val a = raMin
                    val divisor = rbMin
                    if (divisor >= 1 && a % divisor == 0) {
                        val q = a / divisor
                        return CifryMathProblem(mode, a, divisor, q, null)
                    }
                    return null
                }
                if (dividendFixed) {
                    val a = raMin
                    repeat(120) {
                        val divisor = random.nextInt(rbMin, rbMax + 1)
                        if (divisor >= 1 && a % divisor == 0) {
                            val q = a / divisor
                            return CifryMathProblem(mode, a, divisor, q, null)
                        }
                    }
                    return null
                }
                if (divisorFixed) {
                    val divisor = rbMin
                    val maxQ = (raMax / divisor).coerceAtLeast(0)
                    val minQ = if (raMin <= 0) 0 else (raMin + divisor - 1) / divisor
                    if (minQ <= maxQ) {
                        val q = random.nextInt(minQ, maxQ + 1)
                        val a = q * divisor
                        if (a in raMin..raMax) {
                            return CifryMathProblem(mode, a, divisor, q, null)
                        }
                    }
                    return null
                }

                val divisor = random.nextInt(rbMin, rbMax + 1)
                if (divisor == 0) return@repeat
                val maxQ = (raMax / divisor).coerceAtLeast(0)
                val minQ = if (raMin <= 0) 0 else (raMin + divisor - 1) / divisor
                if (minQ <= maxQ) {
                    val q = random.nextInt(minQ, maxQ + 1)
                    val a = q * divisor
                    if (a in raMin..raMax) {
                        return CifryMathProblem(mode, a, divisor, q, null)
                    }
                }
            }
        }
    }
    return null
}

fun buildMathChoices(problem: CifryMathProblem, random: Random): List<Int> {
    val correct = problem.result
    val spread = maxOf(8, abs(correct) + 12)
    val wrong = mutableSetOf<Int>()
    var guard = 0
    while (wrong.size < 3 && guard < 200) {
        guard++
        val guess = random.nextInt(
            (correct - spread).coerceAtLeast(0),
            (correct + spread).coerceAtMost(500) + 1,
        )
        if (guess != correct) wrong.add(guess)
    }
    while (wrong.size < 3) {
        val guess = correct + wrong.size + 1
        if (guess != correct) wrong.add(guess)
    }
    return (wrong.take(3) + correct).shuffled(random)
}
