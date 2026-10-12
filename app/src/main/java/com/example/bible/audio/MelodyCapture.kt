package com.example.bible.audio

import com.example.bible.music.MusicTheoryUtils
import kotlin.math.abs

/** Одна нота, снятая с микрофона. [beats]: 4 целая, 2 половинная, 1 четверть, 0.5 восьмая. */
data class CapturedMelodyNote(val midi: Int, val beats: Float)

/**
 * Складывает поток частот в мелодию: вибрато не дробит ноту,
 * короткие всплески отбрасываются, длина ноты берётся из того, сколько её держали.
 */
class MelodyCapture(
    private val minMidi: Int = 36,
    private val maxMidi: Int = 88,
) {
    private val closed = mutableListOf<CapturedMelodyNote>()
    private var noteMidi = -1
    private var noteStart = 0L
    private var lastSound = 0L
    private var pendingMidi = -1
    private var pendingSince = 0L

    fun feed(timeMs: Long, hz: Double?) {
        if (hz == null || hz <= 0.0) {
            if (noteMidi >= 0 && timeMs - lastSound >= SILENCE_MS) close(lastSound)
            pendingMidi = -1
            return
        }
        lastSound = timeMs
        val nearest = MusicTheoryUtils.midiFromHz(hz).coerceIn(minMidi, maxMidi)
        if (noteMidi < 0) {
            arm(nearest, timeMs)
            return
        }
        val cents = abs(MusicTheoryUtils.centsFromEqualTemperament(hz, noteMidi))
        if (cents <= HOLD_CENTS) {
            pendingMidi = -1
            return
        }
        if (pendingMidi != nearest) {
            pendingMidi = nearest
            pendingSince = timeMs
            return
        }
        if (timeMs - pendingSince >= ATTACK_MS) {
            close(pendingSince)
            open(nearest, pendingSince)
        }
    }

    fun finish(timeMs: Long) {
        if (noteMidi >= 0) close(maxOf(timeMs, lastSound))
        pendingMidi = -1
    }

    fun reset() {
        closed.clear()
        noteMidi = -1
        pendingMidi = -1
    }

    /** Нота, поставленная кнопкой, а не с микрофона. */
    fun addQuarter(midi: Int) {
        if (noteMidi >= 0) close(lastSound)
        pendingMidi = -1
        closed += CapturedMelodyNote(midi.coerceIn(minMidi, maxMidi), 1f)
    }

    fun dropLast() {
        if (noteMidi >= 0) {
            noteMidi = -1
            pendingMidi = -1
            return
        }
        if (closed.isNotEmpty()) closed.removeAt(closed.lastIndex)
    }

    fun notes(): List<CapturedMelodyNote> {
        val open = openNote() ?: return closed.toList()
        return closed + open
    }

    private fun arm(midi: Int, timeMs: Long) {
        if (pendingMidi != midi) {
            pendingMidi = midi
            pendingSince = timeMs
            return
        }
        if (timeMs - pendingSince >= ATTACK_MS) open(midi, pendingSince)
    }

    private fun open(midi: Int, startMs: Long) {
        noteMidi = midi
        noteStart = startMs
        pendingMidi = -1
    }

    private fun close(endMs: Long) {
        val midi = noteMidi
        val start = noteStart
        noteMidi = -1
        if (midi < 0) return
        val held = endMs - start
        if (held < MIN_NOTE_MS) return
        closed += CapturedMelodyNote(midi, beatsForHeldMs(held))
    }

    private fun openNote(): CapturedMelodyNote? {
        if (noteMidi < 0) return null
        val held = (lastSound - noteStart).coerceAtLeast(MIN_NOTE_MS)
        return CapturedMelodyNote(noteMidi, beatsForHeldMs(held))
    }

    companion object {
        private const val ATTACK_MS = 130L
        private const val SILENCE_MS = 260L
        private const val MIN_NOTE_MS = 140L
        private const val HOLD_CENTS = 60.0

        fun beatsForHeldMs(ms: Long): Float = when {
            ms < 320L -> 0.5f
            ms < 700L -> 1f
            ms < 1300L -> 2f
            else -> 4f
        }
    }
}
