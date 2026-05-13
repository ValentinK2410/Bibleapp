package com.example.bible

import com.example.bible.games.checkers.CheckersCell
import com.example.bible.games.checkers.CheckersEngine
import com.example.bible.games.checkers.CheckersSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckersEngineTest {

    @Test
    fun initialBoard_countsPieces() {
        val b = CheckersEngine.initialBoard()
        assertEquals(12, b.count { it == CheckersCell.LightMan })
        assertEquals(12, b.count { it == CheckersCell.DarkMan })
    }

    @Test
    fun start_lightHasLegalMoves() {
        val b = CheckersEngine.initialBoard()
        val moves = CheckersEngine.legalPaths(b, CheckersSide.Light)
        assertTrue(moves.isNotEmpty())
    }
}
