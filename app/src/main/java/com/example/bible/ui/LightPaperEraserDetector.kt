package com.example.bible.ui

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

/**
 * Ищет на кадре крупный светлый связный регион (лист/карточка) и возвращает его ось-ориентированный * прямоугольник в координатах ML Kit / позы ([mlKitW] × [mlKitH]).
 */
object LightPaperEraserDetector {

    private const val LUM_THRESHOLD = 188
    private const val GRID_COLS = 42
    private const val GRID_ROWS = 36
    private const val MIN_FILL_RATIO = 0.028f
    private const val MAX_FILL_RATIO = 0.52f
    private const val MAX_ASPECT = 4.2f
    private const val MIN_ASPECT = 0.24f
    private const val MIN_CELLS = 26
    private const val MIN_BOX_CELLS = 3

    fun detectInMlKitSpace(imageProxy: ImageProxy, mlKitW: Int, mlKitH: Int): RectF? {
        val proxyW = imageProxy.width
        val proxyH = imageProxy.height
        if (proxyW < 8 || proxyH < 8 || mlKitW < 8 || mlKitH < 8) return null

        val plane = imageProxy.planes.getOrNull(0) ?: return null
        val buf = plane.buffer.duplicate()
        buf.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rot = imageProxy.imageInfo.rotationDegrees

        val gc = GRID_COLS
        val gr = GRID_ROWS
        val bright = Array(gr) { BooleanArray(gc) }

        for (gy in 0 until gr) {
            for (gx in 0 until gc) {
                val bx = ((gx + 0.5f) * proxyW / gc).toInt().coerceIn(0, proxyW - 1)
                val by = ((gy + 0.5f) * proxyH / gr).toInt().coerceIn(0, proxyH - 1)
                val lum = luminance(buf, rowStride, pixelStride, bx, by, buf.limit())
                bright[gy][gx] = lum >= LUM_THRESHOLD
            }
        }

        val blob = largestBrightBlob(bright) ?: return null
        val totalCells = gc * gr
        val fillRatio = blob.count.toFloat() / totalCells
        if (fillRatio < MIN_FILL_RATIO || fillRatio > MAX_FILL_RATIO) return null

        val boxW = blob.maxGx - blob.minGx + 1
        val boxH = blob.maxGy - blob.minGy + 1
        val aspect = max(boxW, boxH).toFloat() / min(boxW, boxH)
        if (aspect > MAX_ASPECT || aspect < MIN_ASPECT) return null

        val pad = 1
        val mgx0 = (blob.minGx - pad).coerceAtLeast(0)
        val mgy0 = (blob.minGy - pad).coerceAtLeast(0)
        val mgx1 = (blob.maxGx + pad).coerceAtMost(gc - 1)
        val mgy1 = (blob.maxGy + pad).coerceAtMost(gr - 1)

        val leftBuf = mgx0 * proxyW / gc.toFloat()
        val topBuf = mgy0 * proxyH / gr.toFloat()
        val rightBuf = (mgx1 + 1) * proxyW / gc.toFloat()
        val bottomBuf = (mgy1 + 1) * proxyH / gr.toFloat()

        val bufRect = RectF(leftBuf, topBuf, rightBuf, bottomBuf)
        val mlRect = bufferRectToMlRect(bufRect, rot, proxyW, proxyH)
        val padPx = max(mlKitW, mlKitH) * 0.01f
        mlRect.left = (mlRect.left - padPx).coerceIn(0f, mlKitW - 1f)
        mlRect.top = (mlRect.top - padPx).coerceIn(0f, mlKitH - 1f)
        mlRect.right = (mlRect.right + padPx).coerceIn(0f, mlKitW - 1f)
        mlRect.bottom = (mlRect.bottom + padPx).coerceIn(0f, mlKitH - 1f)
        if (mlRect.width() < 12f || mlRect.height() < 12f) return null
        return mlRect
    }

    private data class Blob(
        val minGx: Int,
        val minGy: Int,
        val maxGx: Int,
        val maxGy: Int,
        val count: Int,
    )

    private fun largestBrightBlob(bright: Array<BooleanArray>): Blob? {
        val rows = bright.size
        val cols = bright[0].size
        val vis = Array(rows) { BooleanArray(cols) }
        var best: Blob? = null
        val dq = ArrayDeque<Pair<Int, Int>>(64)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!bright[r][c] || vis[r][c]) continue
                dq.clear()
                dq.add(r to c)
                vis[r][c] = true
                var minX = c
                var maxX = c
                var minY = r
                var maxY = r
                var cnt = 0
                while (dq.isNotEmpty()) {
                    val (cr, cc) = dq.removeFirst()
                    cnt++
                    minX = min(minX, cc)
                    maxX = max(maxX, cc)
                    minY = min(minY, cr)
                    maxY = max(maxY, cr)
                    val nr1 = cr - 1
                    if (nr1 >= 0 && bright[nr1][cc] && !vis[nr1][cc]) {
                        vis[nr1][cc] = true
                        dq.add(nr1 to cc)
                    }
                    val nr2 = cr + 1
                    if (nr2 < rows && bright[nr2][cc] && !vis[nr2][cc]) {
                        vis[nr2][cc] = true
                        dq.add(nr2 to cc)
                    }
                    val nc1 = cc - 1
                    if (nc1 >= 0 && bright[cr][nc1] && !vis[cr][nc1]) {
                        vis[cr][nc1] = true
                        dq.add(cr to nc1)
                    }
                    val nc2 = cc + 1
                    if (nc2 < cols && bright[cr][nc2] && !vis[cr][nc2]) {
                        vis[cr][nc2] = true
                        dq.add(cr to nc2)
                    }
                }
                if (cnt < MIN_CELLS) continue
                val bw = maxX - minX + 1
                val bh = maxY - minY + 1
                if (bw < MIN_BOX_CELLS || bh < MIN_BOX_CELLS) continue
                val b = Blob(minX, minY, maxX, maxY, cnt)
                if (best == null || cnt > best.count) best = b
            }
        }
        return best
    }

    private fun luminance(
        buf: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        bx: Int,
        by: Int,
        limit: Int,
    ): Int {
        val off = by * rowStride + bx * pixelStride
        if (off < 0 || off >= limit) return 0
        return buf.get(off).toInt() and 0xFF
    }

    private fun bufferRectToMlRect(rect: RectF, rotation: Int, proxyW: Int, proxyH: Int): RectF {
        val corners = arrayOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.right to rect.bottom,
            rect.left to rect.bottom,
        )
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        corners.forEachIndexed { i, (bx, by) ->
            val (mx, my) = bufferToMl(bx, by, rotation, proxyW, proxyH)
            xs[i] = mx
            ys[i] = my
        }
        val l = xs.minOrNull()!!
        val r = xs.maxOrNull()!!
        val t = ys.minOrNull()!!
        val b = ys.maxOrNull()!!
        return RectF(l, t, r, b)
    }

    /** Буфер камеры → координаты того же кадра, что у ML Kit [InputImage.fromMediaImage]. */
    private fun bufferToMl(
        bx: Float,
        by: Float,
        rotation: Int,
        proxyW: Int,
        proxyH: Int,
    ): Pair<Float, Float> {
        return when (rotation) {
            0 -> bx to by
            90 -> by to (proxyW - 1 - bx)
            180 -> (proxyW - 1 - bx) to (proxyH - 1 - by)
            270 -> (proxyH - 1 - by) to bx
            else -> bx to by
        }
    }
}
