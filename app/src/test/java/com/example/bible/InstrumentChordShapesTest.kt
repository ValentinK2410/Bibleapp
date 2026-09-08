package com.example.bible

import com.example.bible.data.InstrumentChordShapes
import com.example.bible.data.InstrumentChordShapes.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentChordShapesTest {

    @Test
    fun parsesHolyChordsNames() {
        val am = InstrumentChordShapes.parse("Am")
        assertNotNull(am)
        assertEquals(9, am!!.root)
        assertEquals(Family.MINOR, am.family)
        assertEquals(listOf(9, 0, 4), am.pitchClasses)

        val slash = InstrumentChordShapes.parse("D/F#")
        assertNotNull(slash)
        assertEquals(2, slash!!.root)
        assertEquals(6, slash.bass)

        val hm = InstrumentChordShapes.parse("Hm")
        assertEquals(11, hm!!.root)
        assertEquals(Family.MINOR, hm.family)
    }

    @Test
    fun guitarOpenShapes() {
        assertEquals(listOf(-1, 0, 2, 2, 1, 0), InstrumentChordShapes.guitarShape("Am")!!.frets)
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), InstrumentChordShapes.guitarShape("C")!!.frets)
        assertEquals(listOf(0, 2, 2, 1, 0, 0), InstrumentChordShapes.guitarShape("E")!!.frets)
        assertEquals(listOf(-1, 2, 4, 4, 3, 2), InstrumentChordShapes.guitarShape("Hm")!!.frets)
    }

    @Test
    fun pianoKeysIncludeTriadAndBass() {
        assertEquals(listOf(0, 4, 7), InstrumentChordShapes.pianoPitchClasses("C"))
        assertEquals(listOf(9, 0, 4), InstrumentChordShapes.pianoPitchClasses("Am"))
        val slash = InstrumentChordShapes.pianoPitchClasses("D/F#")
        assertTrue(slash.contains(6))
        assertTrue(slash.contains(2))
        assertTrue(InstrumentChordShapes.noteLabels("D/F#").contains("бас F#"))
    }

    @Test
    fun barreFallbackForUnlistedSeventh() {
        val shape = InstrumentChordShapes.guitarShape("C#7")
        assertNotNull(shape)
        assertTrue(shape!!.frets.any { it > 0 })
    }
}
