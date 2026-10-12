package com.example.bible

import com.example.bible.audio.MelodyCapture
import com.example.bible.audio.PitchEstimator
import com.example.bible.music.MusicTheoryUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchMelodyTest {

    @Test
    fun sineComesBackAsItself() {
        assertHz(440.0, listOf(1.0))
        assertHz(261.63, listOf(1.0))
        assertHz(82.41, listOf(1.0))
    }

    @Test
    fun voiceHarmonicsStayOnTheFundamental() {
        assertHz(196.0, listOf(1.0, 0.75, 0.5, 0.35, 0.2))
        assertHz(261.63, listOf(0.4, 1.0, 0.7, 0.45))
        assertHz(130.81, listOf(1.0, 0.8, 0.55, 0.3))
    }

    @Test
    fun sungPhraseKeepsPitchesAndLengths() {
        val capture = MelodyCapture()
        // До первой октавы ~262 Гц, четверть, затем Ми ~330 Гц, половинная.
        sing(capture, 261.63, 0, 500)
        sing(capture, null, 500, 800)
        sing(capture, 329.63, 800, 1700)
        capture.finish(2000)
        val notes = capture.notes()
        assertEquals(2, notes.size)
        assertEquals(60, notes[0].midi)
        assertEquals(1f, notes[0].beats)
        assertEquals(64, notes[1].midi)
        assertEquals(2f, notes[1].beats)
    }

    @Test
    fun vibratoDoesNotSplitTheNote() {
        val capture = MelodyCapture()
        var t = 0L
        while (t <= 600) {
            val wobble = if (t / 40 % 2L == 0L) 261.63 else 268.0
            capture.feed(t, wobble)
            t += 40
        }
        capture.finish(900)
        val notes = capture.notes()
        assertEquals(1, notes.size)
        assertEquals(60, notes[0].midi)
    }

    @Test
    fun shortBlipIsDropped() {
        val capture = MelodyCapture()
        sing(capture, 440.0, 0, 80)
        capture.finish(400)
        assertTrue(capture.notes().isEmpty())
    }

    private fun assertHz(hz: Double, harmonics: List<Double>) {
        val est = PitchEstimator.estimateHz(tone(hz, harmonics), 44100)
        assertTrue("ожидали ~$hz Гц, получили $est", est != null)
        val ratio = est!! / hz
        assertTrue("ожидали ~$hz Гц, получили $est", ratio in 0.97..1.03)
        assertEquals(MusicTheoryUtils.midiFromHz(hz), MusicTheoryUtils.midiFromHz(est.toDouble()))
    }

    private fun sing(capture: MelodyCapture, hz: Double?, from: Long, until: Long) {
        var t = from
        while (t < until) {
            capture.feed(t, hz)
            t += 40
        }
    }

    private fun tone(hz: Double, harmonics: List<Double>, n: Int = 4096, rate: Int = 44100): ShortArray {
        val amp = harmonics.sum().coerceAtLeast(0.001)
        return ShortArray(n) { i ->
            var s = 0.0
            harmonics.forEachIndexed { k, a ->
                s += a * sin(2.0 * PI * hz * (k + 1) * i / rate)
            }
            (s / amp * 14000.0).toInt().toShort()
        }
    }
}
