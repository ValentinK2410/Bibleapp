package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/** Квадратная область по центру холста: картинки игр рисуются в ней без искажений. */
private inline fun DrawScope.squareArea(block: (left: Float, top: Float, side: Float) -> Unit) {
    val side = min(size.width, size.height)
    block((size.width - side) / 2f, (size.height - side) / 2f, side)
}

@Composable
internal fun TicTacToeArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        squareArea { l, t, s ->
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(l, t), Size(s, s), CornerRadius(s * 0.14f))
            val cell = s / 3f
            val line = s * 0.045f
            for (k in 1..2) {
                drawLine(Color.White, Offset(l + cell * k, t + s * 0.1f), Offset(l + cell * k, t + s * 0.9f), line, StrokeCap.Round)
                drawLine(Color.White, Offset(l + s * 0.1f, t + cell * k), Offset(l + s * 0.9f, t + cell * k), line, StrokeCap.Round)
            }
            fun center(r: Int, c: Int) = Offset(l + cell * c + cell / 2, t + cell * r + cell / 2)
            fun x(r: Int, c: Int) {
                val p = center(r, c)
                val d = cell * 0.26f
                val stroke = s * 0.07f
                listOf(Offset(-d, -d) to Offset(d, d), Offset(d, -d) to Offset(-d, d)).forEach { (a, b) ->
                    drawLine(Color(0xFF9D174D).copy(alpha = 0.35f), p + a + Offset(2f, 3f), p + b + Offset(2f, 3f), stroke, StrokeCap.Round)
                    drawLine(Color(0xFFFF4FA3), p + a, p + b, stroke, StrokeCap.Round)
                }
            }
            fun o(r: Int, c: Int) {
                val p = center(r, c)
                drawCircle(Color(0xFF92400E).copy(alpha = 0.35f), cell * 0.27f, p + Offset(2f, 3f), style = Stroke(s * 0.065f))
                drawCircle(Color(0xFFFFD43B), cell * 0.27f, p, style = Stroke(s * 0.065f))
            }
            x(0, 0); o(0, 2); x(1, 1); o(1, 2); x(2, 2); o(2, 0)
            drawLine(
                Color.White.copy(alpha = 0.9f),
                center(0, 0) - Offset(cell * 0.3f, cell * 0.3f),
                center(2, 2) + Offset(cell * 0.3f, cell * 0.3f),
                s * 0.035f,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun CheckersArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        squareArea { l, t, s ->
            val n = 4
            val cell = s / n
            drawRoundRect(Color(0xFF7C2D12), Offset(l - s * 0.03f, t - s * 0.03f), Size(s * 1.06f, s * 1.06f), CornerRadius(s * 0.08f))
            for (r in 0 until n) for (c in 0 until n) {
                val dark = (r + c) % 2 == 1
                drawRect(
                    if (dark) Color(0xFF5B3A29) else Color(0xFFF3E1C7),
                    Offset(l + c * cell, t + r * cell),
                    Size(cell, cell),
                )
            }
            fun piece(r: Int, c: Int, red: Boolean, king: Boolean = false) {
                val p = Offset(l + c * cell + cell / 2, t + r * cell + cell / 2)
                val rad = cell * 0.38f
                val base = if (red) Color(0xFFE11D48) else Color(0xFF1F2937)
                val shine = if (red) Color(0xFFFDA4AF) else Color(0xFF9CA3AF)
                drawCircle(Color.Black.copy(alpha = 0.35f), rad, p + Offset(rad * 0.12f, rad * 0.18f))
                drawCircle(Brush.radialGradient(listOf(shine, base), center = p - Offset(rad * 0.35f, rad * 0.35f), radius = rad * 1.4f), rad, p)
                drawCircle(Color.White.copy(alpha = 0.25f), rad * 0.68f, p, style = Stroke(rad * 0.1f))
                if (king) {
                    val crown = Path().apply {
                        moveTo(p.x - rad * 0.45f, p.y + rad * 0.25f)
                        lineTo(p.x - rad * 0.45f, p.y - rad * 0.2f)
                        lineTo(p.x - rad * 0.2f, p.y + rad * 0.02f)
                        lineTo(p.x, p.y - rad * 0.35f)
                        lineTo(p.x + rad * 0.2f, p.y + rad * 0.02f)
                        lineTo(p.x + rad * 0.45f, p.y - rad * 0.2f)
                        lineTo(p.x + rad * 0.45f, p.y + rad * 0.25f)
                        close()
                    }
                    drawPath(crown, Color(0xFFFFD43B))
                }
            }
            piece(0, 1, red = false); piece(0, 3, red = false); piece(1, 0, red = false)
            piece(2, 1, red = true, king = true); piece(3, 0, red = true); piece(3, 2, red = true)
        }
    }
}

@Composable
internal fun GoArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        squareArea { l, t, s ->
            drawRoundRect(
                Brush.linearGradient(listOf(Color(0xFFF2C27B), Color(0xFFD99A4E))),
                Offset(l, t),
                Size(s, s),
                CornerRadius(s * 0.08f),
            )
            val n = 5
            val pad = s * 0.12f
            val step = (s - pad * 2) / (n - 1)
            for (k in 0 until n) {
                drawLine(Color(0xFF6B3F14), Offset(l + pad + k * step, t + pad), Offset(l + pad + k * step, t + s - pad), s * 0.012f)
                drawLine(Color(0xFF6B3F14), Offset(l + pad, t + pad + k * step), Offset(l + s - pad, t + pad + k * step), s * 0.012f)
            }
            fun stone(x: Int, y: Int, black: Boolean) {
                val p = Offset(l + pad + x * step, t + pad + y * step)
                val r = step * 0.46f
                drawCircle(Color.Black.copy(alpha = 0.3f), r, p + Offset(r * 0.12f, r * 0.18f))
                val colors = if (black) listOf(Color(0xFF6B7280), Color(0xFF0B0F17)) else listOf(Color.White, Color(0xFFCBD5E1))
                drawCircle(Brush.radialGradient(colors, center = p - Offset(r * 0.35f, r * 0.35f), radius = r * 1.5f), r, p)
            }
            stone(1, 1, true); stone(2, 1, false); stone(2, 2, true); stone(3, 2, false)
            stone(1, 3, false); stone(3, 3, true); stone(2, 3, true)
        }
    }
}
