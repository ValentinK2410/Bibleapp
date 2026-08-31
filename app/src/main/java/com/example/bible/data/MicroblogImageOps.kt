package com.example.bible.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object MicroblogImageOps {

    private const val DecodeMaxSide = 4096
    private const val OutputMaxSide = 2048

    fun loadBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null
        val sample = max(1, max(srcW, srcH) / DecodeMaxSide)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        return applyExif(file, decoded)
    }

    fun cropAndScale(
        src: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        outputScale: Float,
    ): Bitmap {
        val l = (left.coerceIn(0f, 1f) * src.width).roundToInt().coerceIn(0, src.width - 1)
        val t = (top.coerceIn(0f, 1f) * src.height).roundToInt().coerceIn(0, src.height - 1)
        val r = (right.coerceIn(0f, 1f) * src.width).roundToInt().coerceIn(l + 1, src.width)
        val b = (bottom.coerceIn(0f, 1f) * src.height).roundToInt().coerceIn(t + 1, src.height)
        val cropped = Bitmap.createBitmap(src, l, t, r - l, b - t)
        val scale = outputScale.coerceIn(0.25f, 1f)
        var outW = max(1, (cropped.width * scale).roundToInt())
        var outH = max(1, (cropped.height * scale).roundToInt())
        val longest = max(outW, outH)
        if (longest > OutputMaxSide) {
            val shrink = OutputMaxSide.toFloat() / longest
            outW = max(1, (outW * shrink).roundToInt())
            outH = max(1, (outH * shrink).roundToInt())
        }
        if (outW == cropped.width && outH == cropped.height) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, outW, outH, true)
        if (scaled != cropped && !cropped.isRecycled) cropped.recycle()
        return scaled
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 88) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 100), out)
        }
    }

    private fun applyExif(file: File, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            return bitmap
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap && !bitmap.isRecycled) bitmap.recycle()
        return rotated
    }

    fun fitRect(
        boxW: Float,
        boxH: Float,
        imageW: Float,
        imageH: Float,
    ): FloatArray {
        if (boxW <= 0f || boxH <= 0f || imageW <= 0f || imageH <= 0f) {
            return floatArrayOf(0f, 0f, boxW, boxH)
        }
        val scale = min(boxW / imageW, boxH / imageH)
        val w = imageW * scale
        val h = imageH * scale
        val left = (boxW - w) / 2f
        val top = (boxH - h) / 2f
        return floatArrayOf(left, top, left + w, top + h)
    }
}
