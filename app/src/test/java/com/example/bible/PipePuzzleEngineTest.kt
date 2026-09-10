package com.example.bible

import com.example.bible.games.pipes.PipeDir
import com.example.bible.games.pipes.PipeKind
import com.example.bible.games.pipes.PipePuzzleEngine
import com.example.bible.games.pipes.PipeTile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PipePuzzleEngineTest {
    @Test
    fun straightPipe_connectsOppositeNeighbors() {
        val vertical = PipeTile(PipeKind.STRAIGHT, rotation = 0)
        val horizontal = PipeTile(PipeKind.STRAIGHT, rotation = 1)
        assertTrue(PipePuzzleEngine.canConnect(vertical, PipeDir.S, vertical))
        assertTrue(PipePuzzleEngine.canConnect(horizontal, PipeDir.E, horizontal))
    }

    @Test
    fun newGame_isNotAlreadySolved() {
        val random = Random(42)
        repeat(20) {
            val tiles = PipePuzzleEngine.newGame(4, random)
            assertFalse(PipePuzzleEngine.isSolved(tiles, 4))
        }
    }

    @Test
    fun newGame_hasSingleSnakeWithoutBranchTiles() {
        val random = Random(7)
        repeat(15) {
            val tiles = PipePuzzleEngine.newGame(4, random)
            assertTrue(tiles.none { it.kind == PipeKind.TEE || it.kind == PipeKind.CROSS })
        }
    }
}
