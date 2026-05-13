package com.example.bible

import com.example.bible.games.tictactoe.TicMark
import com.example.bible.games.tictactoe.TicTacToeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TicTacToeEngineTest {
    @Test
    fun bottomRowTripleX_winsAndNotDraw() {
        val e = TicTacToeEngine(size = 3, winLength = 3)
        val b = arrayOf(
            TicMark.X, TicMark.O, TicMark.O,
            TicMark.O, TicMark.X, TicMark.O,
            TicMark.X, TicMark.X, TicMark.X,
        )
        assertEquals(TicMark.X, e.winner(b))
        assertFalse(e.isDraw(b))
    }
}
