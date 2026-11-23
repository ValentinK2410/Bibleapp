package com.example.bible.ui.mynotes

import androidx.compose.ui.graphics.Color
import com.example.bible.data.mynotes.NotePalette

internal fun NotePalette.container(dark: Boolean): Color = if (dark) darkContainer else lightContainer

internal fun NotePalette.accent(dark: Boolean): Color = if (dark) darkAccent else lightAccent

internal fun inkColor(argb: Int): Color = Color(argb.toLong() and 0xFFFFFFFFL)

private val NotePalette.lightContainer: Color
    get() = when (this) {
        NotePalette.SAND -> Color(0xFFFFF3D6)
        NotePalette.SAGE -> Color(0xFFE4F4E7)
        NotePalette.SKY -> Color(0xFFE3F1FC)
        NotePalette.LILAC -> Color(0xFFF3E8FB)
        NotePalette.PEACH -> Color(0xFFFFE8DA)
        NotePalette.MINT -> Color(0xFFDDF6F2)
        NotePalette.ROSE -> Color(0xFFFFE4EC)
        NotePalette.SLATE -> Color(0xFFE7EDF5)
    }

private val NotePalette.darkContainer: Color
    get() = when (this) {
        NotePalette.SAND -> Color(0xFF3E3422)
        NotePalette.SAGE -> Color(0xFF24362B)
        NotePalette.SKY -> Color(0xFF1C3348)
        NotePalette.LILAC -> Color(0xFF332848)
        NotePalette.PEACH -> Color(0xFF4A2E24)
        NotePalette.MINT -> Color(0xFF1C3936)
        NotePalette.ROSE -> Color(0xFF4A2432)
        NotePalette.SLATE -> Color(0xFF2A3140)
    }

private val NotePalette.lightAccent: Color
    get() = when (this) {
        NotePalette.SAND -> Color(0xFFB8860B)
        NotePalette.SAGE -> Color(0xFF2E7D4F)
        NotePalette.SKY -> Color(0xFF1D6FB8)
        NotePalette.LILAC -> Color(0xFF7E57C2)
        NotePalette.PEACH -> Color(0xFFE06A2C)
        NotePalette.MINT -> Color(0xFF0F8F86)
        NotePalette.ROSE -> Color(0xFFD4537E)
        NotePalette.SLATE -> Color(0xFF5C6B82)
    }

private val NotePalette.darkAccent: Color
    get() = when (this) {
        NotePalette.SAND -> Color(0xFFF0C36A)
        NotePalette.SAGE -> Color(0xFF8FCB9A)
        NotePalette.SKY -> Color(0xFF8EC5F5)
        NotePalette.LILAC -> Color(0xFFD2B4F4)
        NotePalette.PEACH -> Color(0xFFFFB088)
        NotePalette.MINT -> Color(0xFF7EDDD2)
        NotePalette.ROSE -> Color(0xFFFFA4BE)
        NotePalette.SLATE -> Color(0xFFC5D0E0)
    }

internal fun paperColor(dark: Boolean): Color =
    if (dark) Color(0xFF241C16) else Color(0xFFFFFBF5)

internal val SketchInks: List<Int> = listOf(
    0xFF1C1917.toInt(),
    0xFF7F1D1D.toInt(),
    0xFF9A3412.toInt(),
    0xFF166534.toInt(),
    0xFF1D4ED8.toInt(),
    0xFF6B21A8.toInt(),
    0xFF9F1239.toInt(),
    0xFFFFFFFF.toInt(),
)

internal val SketchWidths: List<Pair<Float, String>> = listOf(
    2.5f to "Тонко",
    5.5f to "Средне",
    11f to "Жирно",
)
