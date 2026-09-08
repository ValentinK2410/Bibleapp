package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.InstrumentChordShapes
import com.example.bible.data.InstrumentChordShapes.Instrument

private val GuitarStringLabels = listOf("e", "H", "G", "D", "A", "E")
private val WhitePitchClasses = listOf(0, 2, 4, 5, 7, 9, 11, 12)
private val BlackOnWhiteIndex = listOf(0, 1, 3, 4, 5)

@Composable
fun InstrumentFingeringGuide(
    chordName: String,
    modifier: Modifier = Modifier,
    instrument: Instrument? = null,
    onInstrumentChange: ((Instrument) -> Unit)? = null,
) {
    var localInstrument by rememberSaveable { mutableStateOf(Instrument.GUITAR.name) }
    val selected = instrument ?: runCatching {
        Instrument.valueOf(localInstrument)
    }.getOrDefault(Instrument.GUITAR)
    val setInstrument = onInstrumentChange ?: { next -> localInstrument = next.name }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selected == Instrument.GUITAR,
                onClick = { setInstrument(Instrument.GUITAR) },
                label = { Text("Гитара") },
            )
            FilterChip(
                selected = selected == Instrument.PIANO,
                onClick = { setInstrument(Instrument.PIANO) },
                label = { Text("Пианино") },
            )
        }
        Spacer(Modifier.height(8.dp))
        ChordFingeringCard(name = chordName, instrument = selected)
    }
}

@Composable
private fun ChordFingeringCard(
    name: String,
    instrument: Instrument,
) {
    val notes = InstrumentChordShapes.noteLabels(name)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.width(if (instrument == Instrument.PIANO) 168.dp else 112.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            when (instrument) {
                Instrument.GUITAR -> {
                    val shape = InstrumentChordShapes.guitarShape(name)
                    if (shape != null) {
                        GuitarChordDiagram(shape)
                    } else {
                        Text("нет схемы", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Instrument.PIANO -> PianoChordDiagram(
                    pressed = InstrumentChordShapes.pianoPitchClasses(name),
                    bass = InstrumentChordShapes.parse(name)?.bass,
                )
            }
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun GuitarChordDiagram(shape: InstrumentChordShapes.GuitarShape) {
    val grid = MaterialTheme.colorScheme.onSurface
    val dot = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val startFret = guitarWindowStart(shape)
    val density = LocalDensity.current
    val labelSize = with(density) { 9.dp.toPx() }

    Canvas(Modifier.size(width = 92.dp, height = 118.dp)) {
        val left = 18.dp.toPx()
        val top = 16.dp.toPx()
        val fretCount = 4
        val stringCount = 6
        val cellW = (size.width - left - 4.dp.toPx()) / fretCount
        val cellH = (size.height - top - 4.dp.toPx()) / (stringCount - 1)

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (grid.alpha * 255).toInt(),
                (grid.red * 255).toInt(),
                (grid.green * 255).toInt(),
                (grid.blue * 255).toInt(),
            )
            textSize = labelSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        if (startFret > 1) {
            drawContext.canvas.nativeCanvas.drawText(
                startFret.toString(),
                left + cellW / 2f,
                top - 4.dp.toPx(),
                paint,
            )
        }

        for (s in 0 until stringCount) {
            val y = top + s * cellH
            val label = GuitarStringLabels[s]
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(label, left - 6.dp.toPx(), y + labelSize / 3f, paint)
            drawLine(grid.copy(alpha = 0.55f), Offset(left, y), Offset(left + cellW * fretCount, y), 1.4.dp.toPx())
        }
        for (f in 0..fretCount) {
            val x = left + f * cellW
            val thick = if (startFret == 1 && f == 0) 4.dp.toPx() else 1.2.dp.toPx()
            drawLine(grid, Offset(x, top), Offset(x, top + cellH * (stringCount - 1)), thick)
        }

        val barreFret = detectBarre(shape)
        if (barreFret != null && barreFret >= startFret && barreFret < startFret + fretCount) {
            val strings = shape.frets.mapIndexedNotNull { idx, fret ->
                if (fret == barreFret) 5 - idx else null
            }
            if (strings.size >= 3) {
                val y1 = top + strings.min() * cellH
                val y2 = top + strings.max() * cellH
                val cx = left + (barreFret - startFret + 0.5f) * cellW
                drawRoundRect(
                    color = dot,
                    topLeft = Offset(cx - 5.dp.toPx(), y1 - 5.dp.toPx()),
                    size = Size(10.dp.toPx(), y2 - y1 + 10.dp.toPx()),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                )
            }
        }

        shape.frets.forEachIndexed { stringFromBass, fret ->
            val s = 5 - stringFromBass
            val y = top + s * cellH
            when {
                fret < 0 -> {
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    paint.color = android.graphics.Color.argb(
                        (muted.alpha * 255).toInt(),
                        (muted.red * 255).toInt(),
                        (muted.green * 255).toInt(),
                        (muted.blue * 255).toInt(),
                    )
                    drawContext.canvas.nativeCanvas.drawText("×", left - 11.dp.toPx(), y + labelSize / 3f, paint)
                    paint.color = android.graphics.Color.argb(
                        (grid.alpha * 255).toInt(),
                        (grid.red * 255).toInt(),
                        (grid.green * 255).toInt(),
                        (grid.blue * 255).toInt(),
                    )
                }
                fret == 0 -> {
                    drawCircle(
                        color = grid,
                        radius = 4.dp.toPx(),
                        center = Offset(left - 11.dp.toPx(), y),
                        style = Stroke(width = 1.6.dp.toPx()),
                    )
                }
                fret >= startFret && fret < startFret + fretCount && fret != barreFret -> {
                    val cx = left + (fret - startFret + 0.5f) * cellW
                    drawCircle(color = dot, radius = 5.dp.toPx(), center = Offset(cx, y))
                }
            }
        }
    }
}

@Composable
private fun PianoChordDiagram(
    pressed: List<Int>,
    bass: Int?,
) {
    val keyBorder = MaterialTheme.colorScheme.outline
    val whiteFill = MaterialTheme.colorScheme.surface
    val blackFill = MaterialTheme.colorScheme.onSurface
    val press = MaterialTheme.colorScheme.primary
    val bassColor = MaterialTheme.colorScheme.tertiary
    val pressedSet = pressed.map { Math.floorMod(it, 12) }.toSet()

    Canvas(Modifier.size(width = 152.dp, height = 64.dp)) {
        val whiteCount = 8
        val whiteW = size.width / whiteCount
        val whiteH = size.height
        val blackW = whiteW * 0.62f
        val blackH = whiteH * 0.62f

        WhitePitchClasses.forEachIndexed { index, pc ->
            val norm = Math.floorMod(pc, 12)
            val fill = when {
                bass != null && norm == bass -> bassColor
                norm in pressedSet -> press
                else -> whiteFill
            }
            val x = index * whiteW
            drawRect(fill, Offset(x, 0f), Size(whiteW, whiteH))
            drawRect(keyBorder, Offset(x, 0f), Size(whiteW, whiteH), style = Stroke(1.2.dp.toPx()))
        }
        BlackOnWhiteIndex.forEach { afterWhite ->
            val pc = WhitePitchClasses[afterWhite] + 1
            val norm = Math.floorMod(pc, 12)
            val fill = when {
                bass != null && norm == bass -> bassColor
                norm in pressedSet -> press
                else -> blackFill
            }
            val x = (afterWhite + 1) * whiteW - blackW / 2f
            drawRoundRect(
                color = fill,
                topLeft = Offset(x, 0f),
                size = Size(blackW, blackH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                color = keyBorder,
                topLeft = Offset(x, 0f),
                size = Size(blackW, blackH),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(1.dp.toPx()),
            )
        }
    }
}

private fun guitarWindowStart(shape: InstrumentChordShapes.GuitarShape): Int {
    val pressed = shape.frets.filter { it > 0 }
    if (pressed.isEmpty()) return 1
    val maxF = pressed.max()
    val minF = pressed.min()
    return if (maxF <= 4) 1 else minF
}

private fun detectBarre(shape: InstrumentChordShapes.GuitarShape): Int? {
    val pressed = shape.frets.filter { it > 0 }
    if (pressed.size < 4) return null
    val minF = pressed.min()
    return if (shape.frets.count { it == minF } >= 4) minF else null
}
