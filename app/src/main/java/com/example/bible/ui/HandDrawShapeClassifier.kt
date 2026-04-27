package com.example.bible.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Грубая классификация нарисованного жестом контура: линия, треугольник, прямоугольник, круг, многоугольник.
 * Подходит для эксперимента; не претендует на ML-точность.
 */
object HandDrawShapeClassifier {

    enum class Shape {
        LINE,
        TRIANGLE,
        RECTANGLE,
        CIRCLE,
        POLYGON,
        UNKNOWN,
    }

    fun classify(
        leftSegments: List<List<Offset>>,
        rightSegments: List<List<Offset>>,
    ): Shape {
        val segments = leftSegments + rightSegments
        val candidates = segments.filter { it.size >= 2 }
        if (candidates.isEmpty()) return Shape.UNKNOWN

        val main = candidates.maxByOrNull { polylineLength(it) } ?: return Shape.UNKNOWN
        val diag = boundingDiagonal(main).coerceAtLeast(1f)

        if (main.size == 2) {
            return Shape.LINE
        }

        val closed = isClosed(main, diag)

        if (!closed) {
            return if (isApproximatelyStraight(main, diag)) Shape.LINE else Shape.UNKNOWN
        }

        if (main.size >= 8 && isCircleLike(main)) {
            return Shape.CIRCLE
        }

        val eps = max(diag * 0.05f, 8f)
        val simplified = rdpOpen(main, eps)
        val verts = dedupeEndpoints(simplified, eps * 0.75f)

        if (verts.size < 3) {
            return if (isCircleLike(main)) Shape.CIRCLE else Shape.UNKNOWN
        }

        return when (verts.size) {
            3 -> Shape.TRIANGLE
            4 -> if (isRectangular(verts)) Shape.RECTANGLE else Shape.POLYGON
            in 5..7 -> if (isCircleLike(main)) Shape.CIRCLE else Shape.POLYGON
            else -> if (isCircleLike(main)) Shape.CIRCLE else Shape.POLYGON
        }
    }

    private fun polylineLength(pts: List<Offset>): Float {
        var s = 0f
        for (i in 0 until pts.lastIndex) {
            s += hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
        }
        return s
    }

    private fun boundingDiagonal(pts: List<Offset>): Float {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in pts) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
        return hypot(maxX - minX, maxY - minY)
    }

    private fun isClosed(pts: List<Offset>, diag: Float): Boolean {
        val a = pts.first()
        val b = pts.last()
        return hypot(b.x - a.x, b.y - a.y) < diag * 0.14f
    }

    private fun isApproximatelyStraight(pts: List<Offset>, diag: Float): Boolean {
        val a = pts.first()
        val b = pts.last()
        val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f)
        var maxD = 0f
        for (i in 1 until pts.lastIndex) {
            maxD = max(maxD, pointToSegmentDistance(pts[i], a, b))
        }
        return maxD < len * 0.12f && len > diag * 0.15f
    }

    private fun pointToSegmentDistance(p: Offset, a: Offset, b: Offset): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val apx = p.x - a.x
        val apy = p.y - a.y
        val ab2 = abx * abx + aby * aby
        if (ab2 < 1e-6f) return hypot(p.x - a.x, p.y - a.y)
        var t = (apx * abx + apy * aby) / ab2
        t = t.coerceIn(0f, 1f)
        val cx = a.x + t * abx
        val cy = a.y + t * aby
        return hypot(p.x - cx, p.y - cy)
    }

    private fun isCircleLike(pts: List<Offset>): Boolean {
        if (pts.size < 10) return false
        var cx = 0f
        var cy = 0f
        for (p in pts) {
            cx += p.x
            cy += p.y
        }
        val n = pts.size.toFloat()
        cx /= n
        cy /= n
        var sum = 0.0
        var sumSq = 0.0
        for (p in pts) {
            val r = hypot((p.x - cx).toDouble(), (p.y - cy).toDouble())
            sum += r
            sumSq += r * r
        }
        val mean = sum / n
        if (mean < 1e-3) return false
        val variance = (sumSq / n) - mean * mean
        val std = sqrt(variance.coerceAtLeast(0.0))
        val cv = std / mean
        return cv < 0.22
    }

    private fun rdpOpen(points: List<Offset>, epsilon: Float): List<Offset> {
        if (points.size < 3) return points
        var dmax = 0f
        var index = 0
        val end = points.size - 1
        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points.first(), points.last())
            if (d > dmax) {
                index = i
                dmax = d
            }
        }
        return if (dmax > epsilon) {
            val left = rdpOpen(points.subList(0, index + 1), epsilon)
            val right = rdpOpen(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(points.first(), points.last())
        }
    }

    private fun perpendicularDistance(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy).coerceAtLeast(1e-6f)
        return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / len
    }

    private fun dedupeEndpoints(pts: List<Offset>, eps: Float): List<Offset> {
        if (pts.isEmpty()) return pts
        val out = mutableListOf(pts.first())
        for (i in 1 until pts.size) {
            val q = pts[i]
            val last = out.last()
            if (hypot(q.x - last.x, q.y - last.y) >= eps) {
                out.add(q)
            }
        }
        if (out.size >= 3) {
            val f = out.first()
            val l = out.last()
            if (hypot(f.x - l.x, f.y - l.y) < eps) {
                out.removeAt(out.lastIndex)
            }
        }
        return out
    }

    private fun isRectangular(verts: List<Offset>): Boolean {
        if (verts.size != 4) return false
        val v = verts + verts.first()
        for (i in 1..4) {
            val a = v[i - 1]
            val b = v[i]
            val c = v[i + 1]
            val deg = angleDeg(a, b, c)
            if (deg < 65f || deg > 115f) return false
        }
        return true
    }

    private fun angleDeg(a: Offset, b: Offset, c: Offset): Float {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val l1 = hypot(v1x, v1y).coerceAtLeast(1e-6f)
        val l2 = hypot(v2x, v2y).coerceAtLeast(1e-6f)
        var cos = ((v1x * v2x + v1y * v2y) / (l1 * l2)).coerceIn(-1f, 1f)
        return (acos(cos) * 180.0 / Math.PI).toFloat()
    }
}
