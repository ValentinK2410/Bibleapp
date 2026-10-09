package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Фигуры для детей: [findRu] — «Найди …» (винительный), [ofRu] — «у …» (родительный),
 * [corners] — число углов для игры «Сколько углов» (null — фигура в эту игру не входит).
 */
internal enum class KidsShape(
    val nameRu: String,
    val findRu: String,
    val ofRu: String,
    val corners: Int? = null,
    val solid: Boolean = false,
) {
    CIRCLE("Круг", "круг", "круга", 0),
    OVAL("Овал", "овал", "овала", 0),
    TRIANGLE("Треугольник", "треугольник", "треугольника", 3),
    SQUARE("Квадрат", "квадрат", "квадрата", 4),
    RECTANGLE("Прямоугольник", "прямоугольник", "прямоугольника", 4),
    RHOMBUS("Ромб", "ромб", "ромба", 4),
    TRAPEZOID("Трапеция", "трапецию", "трапеции", 4),
    PARALLELOGRAM("Параллелограмм", "параллелограмм", "параллелограмма", 4),
    PENTAGON("Пятиугольник", "пятиугольник", "пятиугольника", 5),
    HEXAGON("Шестиугольник", "шестиугольник", "шестиугольника", 6),
    OCTAGON("Восьмиугольник", "восьмиугольник", "восьмиугольника", 8),
    SEMICIRCLE("Полукруг", "полукруг", "полукруга"),
    STAR("Звезда", "звезду", "звезды"),
    HEART("Сердце", "сердце", "сердца"),
    CRESCENT("Полумесяц", "полумесяц", "полумесяца"),
    CROSS("Крест", "крест", "креста"),
    ARROW("Стрелка", "стрелку", "стрелки"),
    RING("Кольцо", "кольцо", "кольца"),
    CUBE("Куб", "куб", "куба", solid = true),
    SPHERE("Шар", "шар", "шара", solid = true),
    CYLINDER("Цилиндр", "цилиндр", "цилиндра", solid = true),
    CONE("Конус", "конус", "конуса", solid = true),
    PYRAMID("Пирамида", "пирамиду", "пирамиды", solid = true),
    ;

    companion object {
        val basic = listOf(CIRCLE, SQUARE, TRIANGLE, RECTANGLE, OVAL, STAR, HEART, RHOMBUS)
        val flat = entries.filter { !it.solid }
        val solids = entries.filter { it.solid }
        val withCorners = entries.filter { it.corners != null }
    }
}

internal val KidsShapeColors = listOf(
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B), Color(0xFF22C55E),
    Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899),
)

/**
 * Глянцевая фигура. [cornersProgress] от 0 до 1 по очереди зажигает пронумерованные уголки.
 */
@Composable
internal fun KidsShapeImage(
    shape: KidsShape,
    color: Color,
    modifier: Modifier = Modifier,
    cornersProgress: Float = 0f,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.86f
        val c = Offset(size.width / 2f, size.height / 2f)
        if (shape.solid) {
            drawSolid(shape, color, c, r)
        } else {
            drawFlat(shape, color, c, r)
        }
        val pts = shapeVertices(shape) ?: return@Canvas
        if (cornersProgress <= 0f) return@Canvas
        val shown = (cornersProgress * pts.size + 0.001f).toInt().coerceAtMost(pts.size)
        val dotR = r * 0.15f
        for (i in 0 until shown) {
            val p = c + pts[i] * r
            drawCircle(Color.White, dotR, p)
            drawCircle(color, dotR, p, style = Stroke(dotR * 0.28f))
            val label = measurer.measure(
                "${i + 1}",
                TextStyle(color = color, fontSize = (dotR * 1.2f / density / fontScale).sp, fontWeight = FontWeight.Black),
            )
            drawText(label, topLeft = p - Offset(label.size.width / 2f, label.size.height / 2f))
        }
    }
}

private operator fun Offset.times(r: Float) = Offset(x * r, y * r)

private fun regular(n: Int, startDeg: Double, radius: Float = 0.92f): List<Offset> =
    List(n) { i ->
        val a = (startDeg + i * 360.0 / n) * PI / 180.0
        Offset((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat())
    }

/** Вершины в единичных координатах (от −1 до 1, ось y вниз). */
private fun shapeVertices(shape: KidsShape): List<Offset>? = when (shape) {
    KidsShape.TRIANGLE -> listOf(Offset(0f, -0.9f), Offset(0.95f, 0.75f), Offset(-0.95f, 0.75f))
    KidsShape.SQUARE -> listOf(Offset(-0.8f, -0.8f), Offset(0.8f, -0.8f), Offset(0.8f, 0.8f), Offset(-0.8f, 0.8f))
    KidsShape.RECTANGLE -> listOf(Offset(-0.98f, -0.58f), Offset(0.98f, -0.58f), Offset(0.98f, 0.58f), Offset(-0.98f, 0.58f))
    KidsShape.RHOMBUS -> listOf(Offset(0f, -0.98f), Offset(0.72f, 0f), Offset(0f, 0.98f), Offset(-0.72f, 0f))
    KidsShape.TRAPEZOID -> listOf(Offset(-0.5f, -0.6f), Offset(0.5f, -0.6f), Offset(0.98f, 0.6f), Offset(-0.98f, 0.6f))
    KidsShape.PARALLELOGRAM -> listOf(Offset(-0.5f, -0.58f), Offset(0.98f, -0.58f), Offset(0.5f, 0.58f), Offset(-0.98f, 0.58f))
    KidsShape.PENTAGON -> regular(5, -90.0, 0.95f)
    KidsShape.HEXAGON -> regular(6, -90.0, 0.95f)
    KidsShape.OCTAGON -> regular(8, -112.5, 0.95f)
    else -> null
}

private fun polygonPath(points: List<Offset>, c: Offset, r: Float) = Path().apply {
    points.forEachIndexed { i, p ->
        val q = c + p * r
        if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
    }
    close()
}

private fun ovalPath(c: Offset, rx: Float, ry: Float) = Path().apply {
    addOval(Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry))
}

private fun flatPath(shape: KidsShape, c: Offset, r: Float): Path {
    shapeVertices(shape)?.let { return polygonPath(it, c, r) }
    return when (shape) {
        KidsShape.CIRCLE -> ovalPath(c, r * 0.95f, r * 0.95f)
        KidsShape.OVAL -> ovalPath(c, r * 0.98f, r * 0.64f)
        KidsShape.SEMICIRCLE -> Path().apply {
            val cy = c.y + r * 0.42f
            moveTo(c.x - r * 0.98f, cy)
            arcTo(Rect(Offset(c.x, cy), r * 0.98f), 180f, 180f, false)
            close()
        }
        KidsShape.STAR -> polygonPath(
            List(10) { i ->
                val rr = if (i % 2 == 0) 1f else 0.42f
                val a = (-90.0 + i * 36.0) * PI / 180.0
                Offset((cos(a) * rr).toFloat(), (sin(a) * rr + 0.08).toFloat())
            },
            c,
            r,
        )
        KidsShape.HEART -> Path().apply {
            fun pt(x: Float, y: Float) = c + Offset(x, y) * r
            val s = pt(0f, 0.9f)
            moveTo(s.x, s.y)
            val a1 = pt(-1.15f, 0.05f); val a2 = pt(-0.9f, -1.05f); val a3 = pt(0f, -0.42f)
            cubicTo(a1.x, a1.y, a2.x, a2.y, a3.x, a3.y)
            val b1 = pt(0.9f, -1.05f); val b2 = pt(1.15f, 0.05f)
            cubicTo(b1.x, b1.y, b2.x, b2.y, s.x, s.y)
            close()
        }
        KidsShape.CRESCENT -> Path.combine(
            PathOperation.Difference,
            ovalPath(c, r * 0.95f, r * 0.95f),
            ovalPath(c + Offset(r * 0.42f, -r * 0.2f), r * 0.78f, r * 0.78f),
        )
        KidsShape.CROSS -> {
            val a = 0.32f
            val b = 0.95f
            polygonPath(
                listOf(
                    Offset(-a, -b), Offset(a, -b), Offset(a, -a), Offset(b, -a), Offset(b, a), Offset(a, a),
                    Offset(a, b), Offset(-a, b), Offset(-a, a), Offset(-b, a), Offset(-b, -a), Offset(-a, -a),
                ),
                c,
                r,
            )
        }
        KidsShape.ARROW -> polygonPath(
            listOf(
                Offset(-0.95f, -0.28f), Offset(0.12f, -0.28f), Offset(0.12f, -0.7f), Offset(0.98f, 0f),
                Offset(0.12f, 0.7f), Offset(0.12f, 0.28f), Offset(-0.95f, 0.28f),
            ),
            c,
            r,
        )
        KidsShape.RING -> Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(c, r * 0.95f))
            addOval(Rect(c, r * 0.5f))
        }
        else -> ovalPath(c, r, r)
    }
}

private fun Color.lighter(f: Float) = lerp(this, Color.White, f)
private fun Color.darker(f: Float) = lerp(this, Color.Black, f)

private fun DrawScope.drawFlat(shape: KidsShape, color: Color, c: Offset, r: Float) {
    val path = flatPath(shape, c, r)
    translate(0f, r * 0.07f) { drawPath(path, Color.Black.copy(alpha = 0.18f)) }
    drawPath(
        path,
        Brush.linearGradient(
            listOf(color.lighter(0.3f), color, color.darker(0.18f)),
            start = c - Offset(r, r),
            end = c + Offset(r, r),
        ),
    )
    clipPath(path) {
        drawOval(
            Color.White.copy(alpha = 0.28f),
            topLeft = c - Offset(r * 1.1f, r * 1.45f),
            size = Size(r * 1.6f, r * 1.25f),
        )
    }
    drawPath(path, Color.White, style = Stroke(width = r * 0.07f, join = StrokeJoin.Round))
}

private fun DrawScope.outline(path: Path, r: Float) =
    drawPath(path, Color.White, style = Stroke(width = r * 0.05f, join = StrokeJoin.Round))

private fun DrawScope.drawSolid(shape: KidsShape, color: Color, c: Offset, r: Float) {
    fun pt(x: Float, y: Float) = c + Offset(x, y) * r
    val shadow = Color.Black.copy(alpha = 0.16f)
    when (shape) {
        KidsShape.CUBE -> {
            drawOval(shadow, topLeft = pt(-0.9f, 0.72f), size = Size(r * 1.8f, r * 0.3f))
            val front = polygonPath(listOf(Offset(-0.8f, -0.38f), Offset(0.4f, -0.38f), Offset(0.4f, 0.82f), Offset(-0.8f, 0.82f)), c, r)
            val top = polygonPath(listOf(Offset(-0.8f, -0.38f), Offset(-0.4f, -0.8f), Offset(0.8f, -0.8f), Offset(0.4f, -0.38f)), c, r)
            val side = polygonPath(listOf(Offset(0.4f, -0.38f), Offset(0.8f, -0.8f), Offset(0.8f, 0.4f), Offset(0.4f, 0.82f)), c, r)
            drawPath(front, Brush.linearGradient(listOf(color.lighter(0.15f), color), start = pt(-0.8f, -0.4f), end = pt(0.4f, 0.8f)))
            drawPath(top, color.lighter(0.4f))
            drawPath(side, color.darker(0.25f))
            outline(front, r); outline(top, r); outline(side, r)
        }
        KidsShape.SPHERE -> {
            drawOval(shadow, topLeft = pt(-0.75f, 0.72f), size = Size(r * 1.5f, r * 0.28f))
            drawCircle(
                Brush.radialGradient(
                    listOf(color.lighter(0.65f), color, color.darker(0.35f)),
                    center = pt(-0.32f, -0.38f),
                    radius = r * 1.35f,
                ),
                r * 0.85f,
                pt(0f, -0.05f),
            )
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.14f, pt(-0.35f, -0.42f))
        }
        KidsShape.CYLINDER -> {
            drawOval(shadow, topLeft = pt(-0.8f, 0.68f), size = Size(r * 1.6f, r * 0.3f))
            val body = Path().apply {
                val tl = pt(-0.62f, -0.6f)
                moveTo(tl.x, tl.y)
                val bl = pt(-0.62f, 0.62f)
                lineTo(bl.x, bl.y)
                arcTo(Rect(pt(-0.62f, 0.44f), pt(0.62f, 0.8f)), 180f, -180f, false)
                val tr = pt(0.62f, -0.6f)
                lineTo(tr.x, tr.y)
                close()
            }
            drawPath(
                body,
                Brush.horizontalGradient(
                    listOf(color.darker(0.2f), color.lighter(0.35f), color, color.darker(0.3f)),
                    startX = pt(-0.62f, 0f).x,
                    endX = pt(0.62f, 0f).x,
                ),
            )
            val top = ovalPath(pt(0f, -0.6f), r * 0.62f, r * 0.18f)
            drawPath(top, color.lighter(0.45f))
            outline(body, r); outline(top, r)
        }
        KidsShape.CONE -> {
            drawOval(shadow, topLeft = pt(-0.85f, 0.66f), size = Size(r * 1.7f, r * 0.3f))
            val body = Path().apply {
                val apex = pt(0f, -0.92f)
                moveTo(apex.x, apex.y)
                val bl = pt(-0.72f, 0.6f)
                lineTo(bl.x, bl.y)
                arcTo(Rect(pt(-0.72f, 0.4f), pt(0.72f, 0.8f)), 180f, -180f, false)
                close()
            }
            drawPath(
                body,
                Brush.horizontalGradient(
                    listOf(color.darker(0.2f), color.lighter(0.4f), color, color.darker(0.3f)),
                    startX = pt(-0.72f, 0f).x,
                    endX = pt(0.72f, 0f).x,
                ),
            )
            outline(body, r)
        }
        KidsShape.PYRAMID -> {
            drawOval(shadow, topLeft = pt(-0.95f, 0.7f), size = Size(r * 1.9f, r * 0.3f))
            val left = polygonPath(listOf(Offset(0f, -0.92f), Offset(-0.9f, 0.58f), Offset(0.25f, 0.85f)), c, r)
            val right = polygonPath(listOf(Offset(0f, -0.92f), Offset(0.25f, 0.85f), Offset(0.9f, 0.45f)), c, r)
            drawPath(left, Brush.linearGradient(listOf(color.lighter(0.3f), color), start = pt(-0.5f, -0.5f), end = pt(0.2f, 0.8f)))
            drawPath(right, color.darker(0.28f))
            outline(left, r); outline(right, r)
        }
        else -> drawFlat(shape, color, c, r)
    }
}
