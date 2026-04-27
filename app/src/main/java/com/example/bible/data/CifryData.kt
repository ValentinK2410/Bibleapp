package com.example.bible.data

import com.example.bible.R
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
 */
fun nextCifryMathProblem(mode: CifryMathMode, difficulty: Int, random: Random): CifryMathProblem {
    val d = difficulty.coerceIn(0, 3)
    val maxOperand = when (d) {
        0 -> 10
        1 -> 20
        2 -> 50
        else -> 99
    }
    fun maybeVisual(a: Int, b: Int): MathVisualTheme? =
        if (a <= 10 && b <= 10) MathVisualThemes.all.random(random) else null

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
            val cap = when (d) {
                0 -> 10
                1 -> 15
                2 -> 25
                else -> 50
            }
            val a = random.nextInt(0, cap + 1)
            val b = random.nextInt(0, cap + 1)
            CifryMathProblem(mode, a, b, a * b, null)
        }
        CifryMathMode.DIV -> {
            val bMax = when (d) {
                0 -> 10
                1 -> 15
                2 -> 25
                else -> 30
            }.coerceAtLeast(2)
            val b = random.nextInt(1, minOf(bMax, maxOperand).coerceAtLeast(2))
            val maxQ = (maxOperand / b).coerceAtLeast(0)
            val quotient = random.nextInt(0, maxQ + 1)
            val a = quotient * b
            CifryMathProblem(mode, a, b, quotient, null)
        }
    }
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

/** Геометрическая фигура для вкладки «Фигуры» (символ + озвучка). */
data class CifryShapeItem(
    val nameRu: String,
    val glyph: String,
    val speak: String = nameRu,
    /** Векторная картинка вместо символа Unicode (чёткая геометрия). */
    val imageRes: Int? = null,
)

object CifryShapes {
    val all: List<CifryShapeItem> = listOf(
        CifryShapeItem("Круг", "\u25CF"),
        CifryShapeItem("Окружность", "\u25CB"),
        CifryShapeItem("Кольцо", "\u25CE"),
        CifryShapeItem("Квадрат", "\u25A0"),
        CifryShapeItem("Прямоугольник", "\u25AD"),
        CifryShapeItem(
            "Обычный треугольник",
            "",
            speak = "Обычный треугольник",
            imageRes = R.drawable.cifry_shape_triangle_equilateral,
        ),
        CifryShapeItem(
            "Прямой треугольник",
            "",
            speak = "Прямоугольный треугольник",
            imageRes = R.drawable.cifry_shape_triangle_right,
        ),
        CifryShapeItem("Ромб", "\u25C6"),
        CifryShapeItem("Трапеция", "\u29E9"),
        CifryShapeItem("Параллелограмм", "\u25B1"),
        CifryShapeItem("Пятиугольник", "\u2B1F"),
        CifryShapeItem("Шестиугольник", "\u2B22"),
        CifryShapeItem(
            "Восьмиугольник",
            "",
            imageRes = R.drawable.cifry_shape_octagon,
        ),
        CifryShapeItem("Овал", "\u2B2D"),
        CifryShapeItem("Эллипс", "\u2B2E"),
        CifryShapeItem("Звезда", "\u2605"),
        CifryShapeItem("Звезда четырёхконечная", "\u2726"),
        CifryShapeItem("Снежинка", "\u2744"),
        CifryShapeItem("Полумесяц", "\u263D"),
        CifryShapeItem("Крест", "\u271A"),
        CifryShapeItem("Плюс", "\u2715"),
        CifryShapeItem("Стрелка вправо", "\u2192"),
        CifryShapeItem("Стрелка влево", "\u2190"),
        CifryShapeItem("Стрелка вверх", "\u2191"),
        CifryShapeItem("Стрелка вниз", "\u2193"),
        CifryShapeItem("Двойная стрелка", "\u2194"),
        CifryShapeItem("Угол", "\u2220"),
        CifryShapeItem("Прямой угол", "\u221F"),
        CifryShapeItem("Параллель", "\u2225"),
        CifryShapeItem("Перпендикуляр", "\u22A5"),
        CifryShapeItem("Линия", "\u2500"),
        CifryShapeItem("Отрезок", "\u2015"),
        CifryShapeItem("Дуга", "\u2312"),
        CifryShapeItem("Сектор круга", "\u25D4"),
        CifryShapeItem("Полукруг", "\u25D6"),
        CifryShapeItem("Квадрат со скруглением", "\u25A2"),
        CifryShapeItem("Вписанный квадрат", "\u25A3"),
        CifryShapeItem("Куб", "\u25A1", speak = "Куб"),
        CifryShapeItem("Параллелепипед", "\u25A1", speak = "Параллелепипед"),
        CifryShapeItem("Призма", "\u25B3", speak = "Призма"),
        CifryShapeItem("Пирамида", "\u25B3", speak = "Пирамида"),
        CifryShapeItem("Конус", "\u25B3", speak = "Конус"),
        CifryShapeItem("Цилиндр", "\u2294", speak = "Цилиндр"),
        CifryShapeItem("Шар", "\u25CB", speak = "Шар"),
        CifryShapeItem("Сфера", "\u25EF", speak = "Сфера"),
        CifryShapeItem("Тор", "\u2320", speak = "Тор"),
        CifryShapeItem("Спираль", "\u2380", speak = "Спираль"),
        CifryShapeItem("Волна", "\u223F"),
        CifryShapeItem("Зигзаг", "\u26A9"),
        CifryShapeItem("Многоугольник", "\u2B23"),
        CifryShapeItem("Правильный многоугольник", "\u2B24"),
        CifryShapeItem("Выпуклая фигура", "\u25B2", speak = "Выпуклая фигура"),
        CifryShapeItem("Вогнутая фигура", "\u2229", speak = "Вогнутая фигура"),
        CifryShapeItem("Фрактал", "\u2042", speak = "Фрактал"),
        CifryShapeItem("Точка", "\u2022"),
        CifryShapeItem("Луч", "\u2192", speak = "Луч"),
        CifryShapeItem("Сердце", "\u2665"),
        CifryShapeItem("Бесконечность", "\u221E"),
        CifryShapeItem("Симметрия", "\u2194", speak = "Симметрия"),
        CifryShapeItem("Ось симметрии", "\u2225", speak = "Ось симметрии"),
        CifryShapeItem("Центр", "\u2295", speak = "Центр"),
        CifryShapeItem("Диагональ", "\u2571", speak = "Диагональ"),
        CifryShapeItem("Медиана", "\u2225", speak = "Медиана треугольника"),
        CifryShapeItem("Высота", "\u22A5", speak = "Высота треугольника"),
        CifryShapeItem("Биссектриса", "\u2220", speak = "Биссектриса"),
        CifryShapeItem("Касательная", "\u2500", speak = "Касательная"),
        CifryShapeItem("Хорда", "\u2015", speak = "Хорда"),
        CifryShapeItem("Диаметр", "\u2500", speak = "Диаметр"),
        CifryShapeItem("Радиус", "\u2500", speak = "Радиус"),
    )
}
