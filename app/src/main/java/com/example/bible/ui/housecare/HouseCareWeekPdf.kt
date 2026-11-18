package com.example.bible.ui.housecare

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.bible.data.housecare.CleaningWeekDay
import com.example.bible.data.housecare.CleaningWeekDocument
import com.example.bible.data.housecare.cleaningWeekBodyFontPt
import java.io.OutputStream

/** Альбомный A4 в пунктах PDF: 297×210 мм. */
private const val PAGE_W = 842
private const val PAGE_H = 595

private val ink = 0xFF1C2430.toInt()
private val muted = 0xFF5C6B7A.toInt()
private val line = 0xFF1E3348.toInt()
private val headerFill = 0xFFE7F0F8.toInt()

/**
 * Тот же лист, что уходит в печать, но сразу в PDF.
 * Нужен, когда на телефоне нет системной печати: файл потом открывается через Intent.
 */
internal fun writeCleaningWeekPdf(document: CleaningWeekDocument, output: OutputStream) {
    val pdf = PdfDocument()
    try {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        drawWeek(page.canvas, document)
        pdf.finishPage(page)
        pdf.writeTo(output)
    } finally {
        pdf.close()
    }
}

private fun drawWeek(canvas: Canvas, document: CleaningWeekDocument) {
    val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val kicker = textPaint(sans, 8f, muted, Paint.Align.CENTER)
    val title = textPaint(sansBold, 16f, ink, Paint.Align.CENTER)
    val subtitle = textPaint(sans, 10f, ink, Paint.Align.CENTER)
    var y = 20f
    canvas.drawText("ПЛАН УБОРКИ", PAGE_W / 2f, y, kicker)
    y += 18f
    canvas.drawText(document.title, PAGE_W / 2f, y, title)
    if (document.subtitle.isNotBlank()) {
        y += 14f
        canvas.drawText(document.subtitle, PAGE_W / 2f, y, subtitle)
    }
    val tableTop = y + 12f
    val tableBottom = PAGE_H - 16f
    val margin = 16f
    val days = document.days.ifEmpty {
        listOf(CleaningWeekDay("—", "", emptyList()))
    }
    val colW = (PAGE_W - margin * 2f) / days.size
    val headerH = 28f
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = line
    }
    val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = headerFill
    }
    val bodySize = (cleaningWeekBodyFontPt(document.days) - 1f).coerceAtLeast(6f)
    val body = textPaint(sans, bodySize, ink, Paint.Align.LEFT)
    val name = textPaint(sansBold, bodySize, ink, Paint.Align.LEFT)
    val quiet = textPaint(sans, bodySize, muted, Paint.Align.LEFT)
    val head = textPaint(sansBold, 11f, ink, Paint.Align.CENTER)
    val date = textPaint(sans, 8.5f, ink, Paint.Align.CENTER)
    val contentHeight = tableBottom - tableTop - headerH - 8f

    days.forEachIndexed { index, day ->
        val left = margin + index * colW
        val right = left + colW
        canvas.drawRect(left, tableTop, right, tableTop + headerH, headerBg)
        canvas.drawRect(left, tableTop, right, tableBottom, border)
        canvas.drawLine(left, tableTop + headerH, right, tableTop + headerH, border)
        val cx = left + colW / 2f
        canvas.drawText(day.weekday, cx, tableTop + 13f, head)
        canvas.drawText(day.dateLabel, cx, tableTop + 24f, date)

        val contentWidth = (colW - 8f).toInt().coerceAtLeast(1)
        canvas.save()
        canvas.translate(left + 4f, tableTop + headerH + 4f)
        canvas.clipRect(0f, 0f, contentWidth.toFloat(), contentHeight)
        var dy = 0f
        if (day.habits.isNotEmpty()) {
            var kind = ""
            day.habits.forEach { habit ->
                if (habit.kindLabel != kind) {
                    kind = habit.kindLabel
                    dy += drawBlock(canvas, kind, name, contentWidth, dy)
                }
                dy += drawCheckedLine(canvas, habit.name, body, contentWidth, dy, habit.done)
            }
            dy += 3f
            canvas.drawLine(0f, dy, contentWidth.toFloat(), dy, border)
            dy += 4f
        }
        if (day.rooms.isEmpty()) {
            drawBlock(canvas, "Нет уборки", quiet, contentWidth, dy)
        } else {
            day.rooms.forEachIndexed { roomIndex, room ->
                if (roomIndex > 0) dy += 5f
                dy += drawBlock(canvas, room.name, name, contentWidth, dy)
                if (room.floorName.isNotBlank()) {
                    dy += drawBlock(canvas, room.floorName, quiet, contentWidth, dy)
                }
                if (room.tasks.isEmpty()) {
                    dy += drawBlock(canvas, "Задачи не заданы", quiet, contentWidth, dy)
                } else {
                    room.tasks.forEach { task ->
                        dy += drawCheckedLine(canvas, task, body, contentWidth, dy, checked = false)
                    }
                }
            }
        }
        canvas.restore()
    }
}

private fun drawCheckedLine(
    canvas: Canvas,
    text: String,
    paint: TextPaint,
    width: Int,
    dy: Float,
    checked: Boolean,
): Int {
    val box = (paint.textSize * 0.85f).coerceAtLeast(6f)
    val gap = 2.5f
    val textWidth = (width - box - gap).toInt().coerceAtLeast(1)
    val layout = layoutOf(text, paint, textWidth)
    val rowH = maxOf(layout.height, (box + 1f).toInt())
    val boxTop = dy + ((rowH - box) / 2f).coerceAtLeast(0f)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = ink
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawRect(0.4f, boxTop, box, boxTop + box, stroke)
    if (checked) {
        stroke.strokeWidth = 1.1f
        canvas.drawLine(box * 0.18f, boxTop + box * 0.55f, box * 0.42f, boxTop + box * 0.82f, stroke)
        canvas.drawLine(box * 0.42f, boxTop + box * 0.82f, box * 0.84f, boxTop + box * 0.2f, stroke)
    }
    canvas.save()
    canvas.translate(box + gap, dy)
    layout.draw(canvas)
    canvas.restore()
    return rowH
}

private fun drawBlock(canvas: Canvas, text: String, paint: TextPaint, width: Int, dy: Float): Int {
    val layout = layoutOf(text, paint, width)
    canvas.save()
    canvas.translate(0f, dy)
    layout.draw(canvas)
    canvas.restore()
    return layout.height
}

private fun layoutOf(text: String, paint: TextPaint, width: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(1f, 1f)
        .build()

private fun textPaint(typeface: Typeface, size: Float, color: Int, align: Paint.Align): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        this.typeface = typeface
        textAlign = align
    }

