package com.example.bible

import com.example.bible.games.go.GoGame
import com.example.bible.games.go.GoIntersection
import com.example.bible.games.go.GoPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoGameTest {

    @Test
    fun start_blackToPlay_emptyBoard() {
        val g = GoGame(9)
        assertEquals(GoPlayer.Black, g.toPlay)
        assertEquals(81, g.board.count { it == GoIntersection.Empty })
    }

    @Test
    fun firstMove_center_playBlack() {
        val g = GoGame(9)
        val center = 4 * 9 + 4
        assertTrue(g.isLegalPlay(center))
        assertTrue(g.play(center))
        assertEquals(GoIntersection.Black, g.board[center])
        assertEquals(GoPlayer.White, g.toPlay)
    }
}
