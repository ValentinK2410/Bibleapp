package com.example.bible.games.go

import java.util.ArrayDeque
import java.util.LinkedHashSet

enum class GoIntersection {
    Empty,
    Black,
    White,
}

enum class GoPlayer {
    Black,
    White,
}

enum class GoGameMode {
    HUMAN_VS_AI,
    TWO_HUMANS,
}

/** Го: взятие групп без дыханий, запрет самоубийства, простой ко после взятия ровно одного камня. Два паса — конец. */
class GoGame(
    val size: Int,
) {
    init {
        require(size == 9 || size == 13 || size == 19) { "size 9, 13 or 19" }
    }

    val board: Array<GoIntersection> = Array(size * size) { GoIntersection.Empty }

    var toPlay: GoPlayer = GoPlayer.Black
        private set

    /** Текущий игрок не может сыграть на это пересечение (ко). */
    var koBan: Int? = null
        private set

    var consecutivePasses: Int = 0
        private set

    var gameOver: Boolean = false
        private set

    fun cellCount(): Int = size * size

    fun idx(row: Int, col: Int): Int = row * size + col

    fun row(i: Int): Int = i / size

    fun col(i: Int): Int = i % size

    fun opponent(p: GoPlayer): GoPlayer = when (p) {
        GoPlayer.Black -> GoPlayer.White
        GoPlayer.White -> GoPlayer.Black
    }

    fun stoneOf(p: GoPlayer): GoIntersection = when (p) {
        GoPlayer.Black -> GoIntersection.Black
        GoPlayer.White -> GoIntersection.White
    }

    fun reset() {
        board.fill(GoIntersection.Empty)
        toPlay = GoPlayer.Black
        koBan = null
        consecutivePasses = 0
        gameOver = false
    }

    fun pass() {
        if (gameOver) return
        consecutivePasses++
        koBan = null
        toPlay = opponent(toPlay)
        if (consecutivePasses >= 2) gameOver = true
    }

    fun play(at: Int): Boolean {
        if (gameOver) return false
        if (at !in board.indices) return false
        if (board[at] != GoIntersection.Empty) return false
        if (at == koBan) return false

        val myStone = stoneOf(toPlay)
        val trial = board.copyOf()
        trial[at] = myStone
        val capturedCells = mutableListOf<Int>()
        removeDeadOpponentGroups(trial, at, myStone, capturedCells)

        val myGroup = group(trial, at)
        if (libertyCount(trial, myGroup) == 0) return false

        trial.copyInto(board)

        consecutivePasses = 0
        toPlay = opponent(toPlay)
        koBan = if (capturedCells.size == 1) capturedCells[0] else null
        return true
    }

    fun legalMoves(): List<Int> = (0 until cellCount()).filter { isLegalPlay(it) }

    fun isLegalPlay(at: Int): Boolean {
        if (gameOver) return false
        if (at !in board.indices) return false
        if (board[at] != GoIntersection.Empty) return false
        if (at == koBan) return false

        val myStone = stoneOf(toPlay)
        val trial = board.copyOf()
        trial[at] = myStone
        val cap = mutableListOf<Int>()
        removeDeadOpponentGroups(trial, at, myStone, cap)
        val myGroup = group(trial, at)
        return libertyCount(trial, myGroup) > 0
    }

    private fun oppositeStone(s: GoIntersection): GoIntersection = when (s) {
        GoIntersection.Black -> GoIntersection.White
        GoIntersection.White -> GoIntersection.Black
        GoIntersection.Empty -> GoIntersection.Empty
    }

    private fun neighbors4(i: Int): List<Int> {
        val r = row(i)
        val c = col(i)
        val out = ArrayList<Int>(4)
        if (r > 0) out.add(idx(r - 1, c))
        if (r < size - 1) out.add(idx(r + 1, c))
        if (c > 0) out.add(idx(r, c - 1))
        if (c < size - 1) out.add(idx(r, c + 1))
        return out
    }

    private fun group(b: Array<GoIntersection>, start: Int): Set<Int> {
        val color = b[start]
        if (color == GoIntersection.Empty) return emptySet()
        val set = LinkedHashSet<Int>()
        val q = ArrayDeque<Int>()
        q.add(start)
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            if (i in set) continue
            if (b[i] != color) continue
            set.add(i)
            for (n in neighbors4(i)) {
                if (b[n] == color) q.add(n)
            }
        }
        return set
    }

    private fun libertyCount(b: Array<GoIntersection>, stones: Set<Int>): Int {
        val libs = LinkedHashSet<Int>()
        for (i in stones) {
            for (n in neighbors4(i)) {
                if (b[n] == GoIntersection.Empty) libs.add(n)
            }
        }
        return libs.size
    }

    private fun removeDeadOpponentGroups(
        b: Array<GoIntersection>,
        justPlayed: Int,
        myStone: GoIntersection,
        captured: MutableList<Int>,
    ) {
        captured.clear()
        val op = oppositeStone(myStone)
        val toRemove = LinkedHashSet<Int>()
        val seenRoots = LinkedHashSet<Int>()
        for (n in neighbors4(justPlayed)) {
            if (b[n] != op) continue
            val g = group(b, n)
            val gid = g.minOrNull() ?: continue
            if (gid in seenRoots) continue
            seenRoots.add(gid)
            if (libertyCount(b, g) == 0) toRemove.addAll(g)
        }
        for (i in toRemove) {
            captured.add(i)
            b[i] = GoIntersection.Empty
        }
    }
}

object GoAi {
    fun pickMove(game: GoGame, random: kotlin.random.Random): Int? {
        val legal = game.legalMoves()
        if (legal.isEmpty()) return null
        return legal[random.nextInt(legal.size)]
    }
}
