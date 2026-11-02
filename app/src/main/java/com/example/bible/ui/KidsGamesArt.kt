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

@Composable
internal fun DinoRunnerArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        squareArea { l, t, s ->
            // Desert sky & sun
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A))),
                Offset(l, t),
                Size(s, s),
                CornerRadius(s * 0.14f),
            )
            drawCircle(Color(0xFFF59E0B), s * 0.11f, Offset(l + s * 0.8f, t + s * 0.22f))
            // Cloud
            drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(l + s * 0.14f, t + s * 0.16f), Size(s * 0.28f, s * 0.09f), CornerRadius(s * 0.05f))
            // Ground line
            val groundY = t + s * 0.8f
            drawLine(Color(0xFF78350F), Offset(l + s * 0.08f, groundY), Offset(l + s * 0.92f, groundY), s * 0.035f, StrokeCap.Round)
            // Cactus on the right
            val cx = l + s * 0.72f
            val cy = groundY - s * 0.26f
            val cColor = Color(0xFF15803D)
            drawRoundRect(cColor, Offset(cx, cy), Size(s * 0.08f, s * 0.26f), CornerRadius(s * 0.03f))
            drawRoundRect(cColor, Offset(cx - s * 0.06f, cy + s * 0.07f), Size(s * 0.06f, s * 0.1f), CornerRadius(s * 0.02f))
            drawRoundRect(cColor, Offset(cx + s * 0.08f, cy + s * 0.04f), Size(s * 0.06f, s * 0.11f), CornerRadius(s * 0.02f))
            // Cute pixel T-Rex jumping on the left
            val dx = l + s * 0.2f
            val dy = groundY - s * 0.48f
            val dinoColor = Color(0xFF047857)
            // Head & snout
            drawRoundRect(dinoColor, Offset(dx + s * 0.1f, dy), Size(s * 0.22f, s * 0.15f), CornerRadius(s * 0.03f))
            // Eye
            drawCircle(Color.White, s * 0.025f, Offset(dx + s * 0.17f, dy + s * 0.05f))
            drawCircle(Color(0xFF111827), s * 0.013f, Offset(dx + s * 0.175f, dy + s * 0.05f))
            // Body
            drawRoundRect(dinoColor, Offset(dx + s * 0.05f, dy + s * 0.12f), Size(s * 0.17f, s * 0.2f), CornerRadius(s * 0.03f))
            // Tail
            val tail = Path().apply {
                moveTo(dx + s * 0.06f, dy + s * 0.17f)
                lineTo(dx - s * 0.05f, dy + s * 0.12f)
                lineTo(dx - s * 0.02f, dy + s * 0.27f)
                close()
            }
            drawPath(tail, dinoColor)
            // Tiny arm
            drawRoundRect(dinoColor, Offset(dx + s * 0.21f, dy + s * 0.17f), Size(s * 0.06f, s * 0.035f), CornerRadius(s * 0.01f))
            // Legs
            drawRoundRect(dinoColor, Offset(dx + s * 0.07f, dy + s * 0.3f), Size(s * 0.045f, s * 0.09f), CornerRadius(s * 0.01f))
            drawRoundRect(dinoColor, Offset(dx + s * 0.15f, dy + s * 0.28f), Size(s * 0.045f, s * 0.09f), CornerRadius(s * 0.01f))
        }
    }
}

@Composable
internal fun MosesAdventureArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        squareArea { l, t, s ->
            // Warm desert sky & pyramid silhouette
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFFFDE68A))),
                Offset(l, t),
                Size(s, s),
                CornerRadius(s * 0.14f),
            )
            // Pyramid in the background
            val pyr = Path().apply {
                moveTo(l + s * 0.56f, t + s * 0.78f)
                lineTo(l + s * 0.74f, t + s * 0.44f)
                lineTo(l + s * 0.92f, t + s * 0.78f)
                close()
            }
            drawPath(pyr, Color(0xFFD97706).copy(alpha = 0.45f))

            val groundY = t + s * 0.78f
            // Mario-style floating golden block (?)
            drawRoundRect(
                Color(0xFFF59E0B),
                Offset(l + s * 0.42f, t + s * 0.22f),
                Size(s * 0.16f, s * 0.16f),
                CornerRadius(s * 0.03f),
            )
            drawRoundRect(
                Color(0xFFFEF3C7),
                Offset(l + s * 0.47f, t + s * 0.26f),
                Size(s * 0.06f, s * 0.08f),
                CornerRadius(s * 0.015f),
            )

            // Stone desert well (instead of Mario pipe)
            val wx = l + s * 0.64f
            val wy = groundY - s * 0.24f
            drawRoundRect(Color(0xFF78716C), Offset(wx, wy), Size(s * 0.20f, s * 0.24f), CornerRadius(s * 0.03f))
            drawRoundRect(Color(0xFF57534E), Offset(wx - s * 0.02f, wy), Size(s * 0.24f, s * 0.06f), CornerRadius(s * 0.02f))
            // Water glow on top of well
            drawRoundRect(Color(0xFF38BDF8), Offset(wx + s * 0.03f, wy - s * 0.02f), Size(s * 0.14f, s * 0.03f), CornerRadius(s * 0.015f))

            // Moses with robe & shepherd staff on the left
            val mx = l + s * 0.18f
            val my = groundY - s * 0.42f
            // Staff
            drawLine(
                Color(0xFF78350F),
                Offset(mx + s * 0.22f, my - s * 0.04f),
                Offset(mx + s * 0.22f, groundY),
                s * 0.032f,
                StrokeCap.Round,
            )
            drawCircle(Color(0xFFFEF08A), s * 0.04f, Offset(mx + s * 0.22f, my - s * 0.05f))
            // Robe & cloak
            drawRoundRect(Color(0xFF9A3412), Offset(mx, my + s * 0.14f), Size(s * 0.16f, s * 0.28f), CornerRadius(s * 0.04f))
            // Head & beard
            drawCircle(Color(0xFFFDE68A), s * 0.075f, Offset(mx + s * 0.08f, my + s * 0.08f))
            drawRoundRect(Color(0xFFE5E7EB), Offset(mx + s * 0.03f, my + s * 0.10f), Size(s * 0.10f, s * 0.09f), CornerRadius(s * 0.03f))

            // Desert ground
            drawLine(Color(0xFFB45309), Offset(l + s * 0.06f, groundY), Offset(l + s * 0.94f, groundY), s * 0.04f, StrokeCap.Round)
        }
    }
}

@Composable
internal fun AdamEveGardenArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        squareArea { l, t, s ->
            // Lush Eden sky & golden light of creation
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFBAE6FD), Color(0xFFFEF9C3), Color(0xFFBBF7D0))),
                Offset(l, t),
                Size(s, s),
                CornerRadius(s * 0.14f),
            )
            val groundY = t + s * 0.78f
            // River of Eden
            drawRoundRect(
                Color(0xFF38BDF8),
                Offset(l + s * 0.06f, groundY),
                Size(s * 0.88f, s * 0.14f),
                CornerRadius(s * 0.06f),
            )
            // Tree of Life in the center
            val tx = l + s * 0.50f
            drawRoundRect(
                Color(0xFF78350F),
                Offset(tx - s * 0.045f, groundY - s * 0.36f),
                Size(s * 0.09f, s * 0.36f),
                CornerRadius(s * 0.03f),
            )
            drawCircle(Color(0xFF15803D), s * 0.20f, Offset(tx, groundY - s * 0.44f))
            drawCircle(Color(0xFF22C55E), s * 0.14f, Offset(tx - s * 0.07f, groundY - s * 0.47f))
            drawCircle(Color(0xFF16A34A), s * 0.14f, Offset(tx + s * 0.07f, groundY - s * 0.47f))
            // Golden fruits of life
            drawCircle(Color(0xFFFACC15), s * 0.035f, Offset(tx - s * 0.08f, groundY - s * 0.44f))
            drawCircle(Color(0xFFFACC15), s * 0.035f, Offset(tx + s * 0.06f, groundY - s * 0.48f))
            drawCircle(Color(0xFFFACC15), s * 0.035f, Offset(tx, groundY - s * 0.37f))

            // Adam on the left & Eve on the right
            val ax = l + s * 0.22f
            val ex = l + s * 0.78f
            drawRoundRect(Color(0xFF059669), Offset(ax - s * 0.06f, groundY - s * 0.24f), Size(s * 0.12f, s * 0.24f), CornerRadius(s * 0.04f))
            drawCircle(Color(0xFFFDE68A), s * 0.065f, Offset(ax, groundY - s * 0.30f))
            drawRoundRect(Color(0xFF10B981), Offset(ex - s * 0.06f, groundY - s * 0.23f), Size(s * 0.12f, s * 0.23f), CornerRadius(s * 0.04f))
            drawCircle(Color(0xFFFDE68A), s * 0.062f, Offset(ex, groundY - s * 0.29f))

            // Green grass line
            drawLine(Color(0xFF15803D), Offset(l + s * 0.06f, groundY), Offset(l + s * 0.94f, groundY), s * 0.04f, StrokeCap.Round)
        }
    }
}
