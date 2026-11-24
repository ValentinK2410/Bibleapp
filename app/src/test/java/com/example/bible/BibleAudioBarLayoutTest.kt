package com.example.bible

import com.example.bible.ui.LANDSCAPE_AUDIO_BAR_COLLAPSED_DP
import com.example.bible.ui.LANDSCAPE_AUDIO_BAR_EXPANDED_DP
import com.example.bible.ui.LANDSCAPE_READER_TEXT_RESERVE_DP
import com.example.bible.ui.landscapeAudioBarMaxHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleAudioBarLayoutTest {

    @Test
    fun expandedBarLeavesReadableTextOnPhoneLandscape() {
        // Типичная высота контента под аппбаром на телефоне в ландшафте.
        listOf(240, 248, 280, 320, 360, 400, 412).forEach { height ->
            val bar = landscapeAudioBarMaxHeightDp(height, collapsed = false)
            assertTrue("bar=$bar height=$height", bar in 40..LANDSCAPE_AUDIO_BAR_EXPANDED_DP)
            assertTrue(
                "text=${height - bar} height=$height",
                height - bar >= LANDSCAPE_READER_TEXT_RESERVE_DP,
            )
        }
    }

    @Test
    fun collapsedBarIsShorterAndStillLeavesText() {
        listOf(240, 280, 360, 412).forEach { height ->
            val collapsed = landscapeAudioBarMaxHeightDp(height, collapsed = true)
            val expanded = landscapeAudioBarMaxHeightDp(height, collapsed = false)
            assertTrue(collapsed < expanded)
            assertTrue(collapsed <= LANDSCAPE_AUDIO_BAR_COLLAPSED_DP)
            assertTrue(height - collapsed >= LANDSCAPE_READER_TEXT_RESERVE_DP)
        }
    }

    @Test
    fun veryShortLandscapeStillKeepsBothOnScreen() {
        val height = 180
        val bar = landscapeAudioBarMaxHeightDp(height, collapsed = false)
        assertEquals(40, bar)
        assertTrue(height - bar > 0)
        assertTrue(bar < height)
    }

    @Test
    fun unboundedHeightUsesPreferredCap() {
        assertEquals(
            LANDSCAPE_AUDIO_BAR_EXPANDED_DP,
            landscapeAudioBarMaxHeightDp(0, collapsed = false),
        )
        assertEquals(
            LANDSCAPE_AUDIO_BAR_COLLAPSED_DP,
            landscapeAudioBarMaxHeightDp(0, collapsed = true),
        )
    }
}
