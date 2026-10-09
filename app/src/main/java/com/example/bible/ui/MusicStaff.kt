package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Нота на стане: [beats] — 4 целая, 2 половинная, 1 четверть, 0.5 восьмая. */
internal data class StaffNote(val midi: Int, val beats: Float = 1f)

internal val NoteLettersRu = listOf("До", "Ре", "Ми", "Фа", "Соль", "Ля", "Си")
private val NaturalSemitones = listOf(0, 2, 4, 5, 7, 9, 11)
private val PcToLetter = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
private val PcSharp = setOf(1, 3, 6, 8, 10)

/** Ступень на скрипичном стане: 0 — нижняя линия (Ми первой октавы), шаг — линия или промежуток. */
internal fun staffStepOf(midi: Int): Pair<Int, Boolean> {
    val pc = Math.floorMod(midi, 12)
    val octave = Math.floorDiv(midi, 12) - 1
    val diatonic = PcToLetter[pc] + 7 * octave
    return (diatonic - 30) to (pc in PcSharp)
}

internal fun naturalMidiOfStep(step: Int): Int {
    val diatonic = step + 30
    val letter = Math.floorMod(diatonic, 7)
    val octave = Math.floorDiv(diatonic, 7)
    return (octave + 1) * 12 + NaturalSemitones[letter]
}

internal fun noteNameRu(midi: Int): String {
    val pc = Math.floorMod(midi, 12)
    return NoteLettersRu[PcToLetter[pc]] + if (pc in PcSharp) "♯" else ""
}

internal fun isBlackKey(midi: Int) = Math.floorMod(midi, 12) in PcSharp

private val PaperInk = Color(0xFF1E1B4B)

/**
 * Скрипичный стан с ключом. Ширина холста задаётся снаружи; [onTapNote] — нажата нота,
 * [onTapStaff] — нажато пустое место (передаётся натуральная нота этой высоты).
 */
@Composable
internal fun MusicStaff(
    notes: List<StaffNote>,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    selected: Int? = null,
    showNames: Boolean = true,
    noteSpacingPx: Float? = null,
    accent: Color = Color(0xFF7C3AED),
    onTapNote: ((Int) -> Unit)? = null,
    onTapStaff: ((Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val notesState = rememberUpdatedState(notes)
    val tapNote = rememberUpdatedState(onTapNote)
    val tapStaff = rememberUpdatedState(onTapStaff)
    Canvas(
        modifier.pointerInput(onTapNote != null || onTapStaff != null) {
            detectTapGestures { pos ->
                val g = staffGeometry(size.width.toFloat(), size.height.toFloat(), notesState.value.size, noteSpacingPx)
                val idx = notesState.value.indices.minByOrNull { kotlin.math.abs(g.xOf(it) - pos.x) }
                if (idx != null && kotlin.math.abs(g.xOf(idx) - pos.x) < g.spacing * 0.4f && tapNote.value != null) {
                    tapNote.value?.invoke(idx)
                } else {
                    val step = ((g.baseY - pos.y) / g.half).roundToInt().coerceIn(-4, 13)
                    tapStaff.value?.invoke(naturalMidiOfStep(step))
                }
            }
        },
    ) {
        val g = staffGeometry(size.width, size.height, notes.size, noteSpacingPx)
        for (s in 0..8 step 2) {
            val y = g.yOf(s)
            drawLine(PaperInk.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.2f)
        }
        drawTrebleClef(measurer, g)
        notes.forEachIndexed { i, n ->
            val color = when (i) {
                highlight -> Color(0xFFEC4899)
                selected -> accent
                else -> PaperInk
            }
            drawStaffNote(n, g.xOf(i), g, color, big = i == highlight)
            if (showNames) {
                val label = measurer.measure(
                    noteNameRu(n.midi),
                    TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (i == highlight) color else accent),
                )
                drawText(label, topLeft = Offset(g.xOf(i) - label.size.width / 2f, size.height - label.size.height - 2f))
            }
        }
    }
}

internal class StaffGeometry(val half: Float, val baseY: Float, val startX: Float, val spacing: Float) {
    fun yOf(step: Int) = baseY - step * half
    fun xOf(index: Int) = startX + spacing * (index + 0.5f)
}

internal fun staffGeometry(width: Float, height: Float, count: Int, spacingPx: Float?): StaffGeometry {
    val half = height / 22f
    val baseY = height - 6.5f * half
    val startX = half * 8.5f
    val spacing = spacingPx ?: ((width - startX) / count.coerceAtLeast(1)).coerceAtMost(half * 9f)
    val centered = if (spacingPx == null) startX + ((width - startX) - spacing * count.coerceAtLeast(1)) / 2f else startX
    return StaffGeometry(half, baseY, centered, spacing)
}

private fun DrawScope.drawTrebleClef(measurer: TextMeasurer, g: StaffGeometry) {
    val clef = measurer.measure(
        "\uD834\uDD1E",
        TextStyle(fontSize = (g.half * 11f / density / fontScale).sp, color = PaperInk),
    )
    drawText(clef, topLeft = Offset(g.half * 0.6f, g.yOf(2) - clef.size.height * 0.62f))
}

private fun DrawScope.drawStaffNote(note: StaffNote, x: Float, g: StaffGeometry, color: Color, big: Boolean) {
    val (step, sharp) = staffStepOf(note.midi)
    val y = g.yOf(step)
    val h = g.half
    var s = -2
    while (s >= step) {
        drawLine(PaperInk.copy(alpha = 0.8f), Offset(x - h * 2.2f, g.yOf(s)), Offset(x + h * 2.2f, g.yOf(s)), strokeWidth = 3f)
        s -= 2
    }
    s = 10
    while (s <= step) {
        drawLine(PaperInk.copy(alpha = 0.8f), Offset(x - h * 2.2f, g.yOf(s)), Offset(x + h * 2.2f, g.yOf(s)), strokeWidth = 3f)
        s += 2
    }
    val scale = if (big) 1.2f else 1f
    val w = h * 2.7f * scale
    val hh = h * 1.9f * scale
    val hollow = note.beats >= 2f
    rotate(-20f, Offset(x, y)) {
        if (hollow) {
            drawOval(color, Offset(x - w / 2, y - hh / 2), Size(w, hh), style = Stroke(h * 0.45f))
        } else {
            drawOval(color, Offset(x - w / 2, y - hh / 2), Size(w, hh))
        }
    }
    if (sharp) {
        val sx = x - w * 1.05f
        val sw = h * 0.5f
        drawLine(color, Offset(sx - sw, y - h * 1.6f), Offset(sx - sw, y + h * 1.8f), strokeWidth = 2.5f)
        drawLine(color, Offset(sx + sw, y - h * 1.8f), Offset(sx + sw, y + h * 1.6f), strokeWidth = 2.5f)
        drawLine(color, Offset(sx - sw * 2, y - h * 0.3f), Offset(sx + sw * 2, y - h * 0.7f), strokeWidth = 4f)
        drawLine(color, Offset(sx - sw * 2, y + h * 0.7f), Offset(sx + sw * 2, y + h * 0.3f), strokeWidth = 4f)
    }
    if (note.beats < 4f) {
        val up = step < 4
        val stemX = if (up) x + w / 2 - 1.5f else x - w / 2 + 1.5f
        val endY = if (up) y - h * 7f else y + h * 7f
        drawLine(color, Offset(stemX, y), Offset(stemX, endY), strokeWidth = h * 0.3f, cap = StrokeCap.Round)
        if (note.beats <= 0.5f) {
            val flag = Path().apply {
                moveTo(stemX, endY)
                if (up) {
                    cubicTo(stemX + h * 0.5f, endY + h * 2f, stemX + h * 3f, endY + h * 2.5f, stemX + h * 2f, endY + h * 5f)
                } else {
                    cubicTo(stemX + h * 0.5f, endY - h * 2f, stemX + h * 3f, endY - h * 2.5f, stemX + h * 2f, endY - h * 5f)
                }
            }
            drawPath(path = flag, color = color, style = Stroke(h * 0.45f, cap = StrokeCap.Round))
        }
    }
}

/** Клавиатура: нажатие — [onDown], отпускание — [onUp] с той же нотой. */
@Composable
internal fun PianoKeyboard(
    lowMidi: Int,
    highMidi: Int,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    highlightMidi: Int? = null,
    accent: List<Color> = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)),
    onDown: (Int) -> Unit,
    onUp: (Int) -> Unit,
) {
    val whites = remember(lowMidi, highMidi) { (lowMidi..highMidi).filter { !isBlackKey(it) } }
    var pressed by remember { mutableStateOf<Int?>(null) }
    val down = rememberUpdatedState(onDown)
    val up = rememberUpdatedState(onUp)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier.pointerInput(lowMidi, highMidi) {
            detectTapGestures(
                onPress = { pos ->
                    val ww = size.width.toFloat() / whites.size
                    val bh = size.height * 0.6f
                    var key: Int? = null
                    if (pos.y < bh) {
                        whites.forEachIndexed { i, m ->
                            if (m + 1 <= highMidi && isBlackKey(m + 1)) {
                                val cx = (i + 1) * ww
                                if (kotlin.math.abs(pos.x - cx) < ww * 0.32f) key = m + 1
                            }
                        }
                    }
                    if (key == null) key = whites[(pos.x / ww).toInt().coerceIn(0, whites.lastIndex)]
                    val k = key!!
                    pressed = k
                    down.value(k)
                    tryAwaitRelease()
                    pressed = null
                    up.value(k)
                },
            )
        },
    ) {
        val ww = size.width / whites.size
        val bh = size.height * 0.6f
        val active = pressed ?: highlightMidi
        whites.forEachIndexed { i, m ->
            val left = i * ww
            val isOn = m == active
            drawRoundRect(
                brush = if (isOn) Brush.verticalGradient(accent) else Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9))),
                topLeft = Offset(left + 1.5f, 0f),
                size = Size(ww - 3f, size.height),
                cornerRadius = CornerRadius(ww * 0.18f),
            )
            drawRoundRect(
                Color(0xFFCBD5E1),
                topLeft = Offset(left + 1.5f, 0f),
                size = Size(ww - 3f, size.height),
                cornerRadius = CornerRadius(ww * 0.18f),
                style = Stroke(2f),
            )
            if (showLabels) {
                val label = measurer.measure(
                    NoteLettersRu[PcToLetter[Math.floorMod(m, 12)]],
                    TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isOn) Color.White else Color(0xFF64748B)),
                )
                drawText(label, topLeft = Offset(left + (ww - label.size.width) / 2f, size.height - label.size.height - 6f))
            }
        }
        whites.forEachIndexed { i, m ->
            if (m + 1 <= highMidi && isBlackKey(m + 1)) {
                val cx = (i + 1) * ww
                val isOn = m + 1 == active
                drawRoundRect(
                    brush = if (isOn) Brush.verticalGradient(accent) else Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))),
                    topLeft = Offset(cx - ww * 0.3f, 0f),
                    size = Size(ww * 0.6f, bh),
                    cornerRadius = CornerRadius(ww * 0.12f),
                )
            }
        }
    }
}

private val FifthsMajor = listOf("C", "G", "D", "A", "E", "B", "F♯", "D♭", "A♭", "E♭", "B♭", "F")
private val FifthsMinor = listOf("Am", "Em", "Bm", "F♯m", "C♯m", "G♯m", "D♯m", "B♭m", "Fm", "Cm", "Gm", "Dm")
private val FifthsSigns = listOf("", "1♯", "2♯", "3♯", "4♯", "5♯", "6♯", "5♭", "4♭", "3♭", "2♭", "1♭")

/** Квинтовый круг: нажатие на сектор — [onTap] с индексом (0 — До мажор) и признаком минора. */
@Composable
internal fun CircleOfFifths(modifier: Modifier = Modifier, selected: Int? = null, onTap: (Int, Boolean) -> Unit) {
    val measurer = rememberTextMeasurer()
    val tap = rememberUpdatedState(onTap)
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos ->
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = minOf(size.width, size.height) / 2f
                val d = hypot(pos.x - c.x, pos.y - c.y)
                if (d < r * 0.25f || d > r) return@detectTapGestures
                val ang = (Math.toDegrees(atan2((pos.y - c.y).toDouble(), (pos.x - c.x).toDouble())) + 90.0 + 15.0 + 360.0) % 360.0
                tap.value((ang / 30.0).toInt() % 12, d < r * 0.62f)
            }
        },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) / 2f
        for (i in 0 until 12) {
            val start = -90f - 15f + i * 30f
            val hue = (i * 30f) % 360f
            val col = Color.hsv(hue, 0.55f, 0.95f)
            drawArc(
                if (selected == i) col else col.copy(alpha = 0.75f),
                start,
                30f,
                true,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
            )
            drawArc(Color.White, start, 30f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3f))
        }
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.62f, c)
        drawCircle(Color.White, r * 0.62f, c, style = Stroke(3f))
        drawCircle(Color(0xFF1E1B4B), r * 0.25f, c)
        for (i in 0 until 12) {
            val a = (-90.0 + i * 30.0) * PI / 180.0
            fun put(text: String, radius: Float, size: Float, color: Color, bold: Boolean) {
                val l = measurer.measure(text, TextStyle(fontSize = size.sp, fontWeight = if (bold) FontWeight.Black else FontWeight.Bold, color = color))
                val p = c + Offset((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat())
                drawText(l, topLeft = p - Offset(l.size.width / 2f, l.size.height / 2f))
            }
            put(FifthsMajor[i], r * 0.84f, 16f, Color(0xFF1E1B4B), true)
            put(FifthsSigns[i], r * 0.71f, 9f, Color(0xFF1E1B4B).copy(alpha = 0.7f), false)
            put(FifthsMinor[i], r * 0.45f, 11f, Color(0xFF312E81), true)
        }
        val center = measurer.measure("♪", TextStyle(fontSize = 22.sp, color = Color.White))
        drawText(center, topLeft = c - Offset(center.size.width / 2f, center.size.height / 2f))
    }
}

/** Тоника мажора по индексу на квинтовом круге (0 — До). */
internal fun fifthsMajorRootMidi(index: Int): Int = 60 + Math.floorMod(index * 7, 12)
