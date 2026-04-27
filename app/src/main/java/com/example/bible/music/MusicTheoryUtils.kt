package com.example.bible.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

object MusicTheoryUtils {

    private val englishNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** Русские названия (система «До, Ре, Ми…»), диез/бемоль через знак. */
    private val russianBase = listOf("До", "До♯", "Ре", "Ре♯", "Ми", "Фа", "Фа♯", "Соль", "Соль♯", "Ля", "Ля♯", "Си")

    fun midiFromHz(hz: Double): Int =
        (12.0 * log2(hz / 440.0) + 69.0).roundToInt().coerceIn(0, 127)

    fun hzFromMidi(midi: Int): Double =
        440.0 * 2.0.pow((midi - 69) / 12.0)

    fun englishName(midi: Int): String {
        val n = midi.coerceIn(0, 127)
        val noteIndex = (n % 12 + 12) % 12
        val octave = n / 12 - 1
        return "${englishNames[noteIndex]}$octave"
    }

    fun russianName(midi: Int): String {
        val n = midi.coerceIn(0, 127)
        val noteIndex = (n % 12 + 12) % 12
        val octave = n / 12 - 1
        return "${russianBase[noteIndex]}$octave"
    }

    /** Отклонение в центах от ближайшей равномерной высоты по MIDI. */
    fun centsFromEqualTemperament(hz: Double, midi: Int): Double {
        val target = hzFromMidi(midi)
        return 1200.0 * log2(hz / target)
    }
}
