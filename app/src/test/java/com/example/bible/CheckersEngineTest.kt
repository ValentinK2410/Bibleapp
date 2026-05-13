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
    fun applySimpleMove_keepsVoidSquaresAndPieceCount() {
        val b0 = CheckersEngine.initialBoard()
        val move = CheckersEngine.legalPaths(b0, CheckersSide.Light).first()
        assertEquals(2, move.cells.size)
        val b1 = CheckersEngine.applyPath(b0, move)
        for (i in b1.indices) {
            val r = CheckersEngine.row(i)
            val c = CheckersEngine.col(i)
            if (!CheckersEngine.isPlayable(r, c)) {
                assertEquals(CheckersCell.Void, b1[i])
            }
        }
        assertEquals(12, b1.count { it == CheckersCell.LightMan })
        assertEquals(12, b1.count { it == CheckersCell.DarkMan })
    }

    @Test
    fun start_lightHasLegalMoves() {
        val b = CheckersEngine.initialBoard()
        val moves = CheckersEngine.legalPaths(b, CheckersSide.Light)
        assertTrue(moves.isNotEmpty())
    }
}
