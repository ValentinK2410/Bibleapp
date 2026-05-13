package com.example.bible.games.tictactoe

import kotlin.random.Random

enum class TicMark {
    Empty,
    X,
    O,
}

enum class TicGameMode {
    /** Человек (X) против компьютера (O). */
    HUMAN_VS_AI,
    /** Два человека по очереди. */
    TWO_HUMANS,
    /** Два компьютера. */
    AI_VS_AI,
}

/**
 * Классическая игра в «три в ряд» на квадратном поле [size]×[size].
 * [winLength] — сколько подряд нужно (по горизонтали, вертикали, диагонали).
 */
class TicTacToeEngine(
    val size: Int,
    val winLength: Int = 3,
) {
    init {
        require(size in 3..6) { "size 3..6" }
        require(winLength in 3..size) { "winLength" }
    }

    val cellCount: Int = size * size

    private val lines: List<IntArray> = buildList {
        val w = winLength
        // горизонтали
        for (r in 0 until size) {
            for (c in 0 until size - w + 1) {
                add(IntArray(w) { i -> r * size + (c + i) })
            }
        }
        // вертикали
        for (c in 0 until size) {
            for (r in 0 until size - w + 1) {
                add(IntArray(w) { i -> (r + i) * size + c })
            }
        }
        // диагональ \
        for (r in 0 until size - w + 1) {
            for (c in 0 until size - w + 1) {
                add(IntArray(w) { i -> (r + i) * size + (c + i) })
            }
        }
        // диагональ /
        for (r in 0 until size - w + 1) {
            for (c in w - 1 until size) {
                add(IntArray(w) { i -> (r + i) * size + (c - i) })
            }
        }
    }

    fun emptyBoard(): Array<TicMark> = Array(cellCount) { TicMark.Empty }

    fun winner(board: Array<TicMark>): TicMark? {
        for (line in lines) {
            val a = board[line[0]]
            if (a == TicMark.Empty) continue
            var ok = true
            for (i in 1 until line.size) {
                if (board[line[i]] != a) {
                    ok = false
                    break
                }
            }
            if (ok) return a
        }
        return null
    }

    fun isDraw(board: Array<TicMark>): Boolean =
        board.all { it != TicMark.Empty } && winner(board) == null
}

object TicTacToeAi {

    fun pickMove(
        engine: TicTacToeEngine,
        board: Array<TicMark>,
        aiMark: TicMark,
        random: Random,
    ): Int? {
        val empty = board.indices.filter { board[it] == TicMark.Empty }
        if (empty.isEmpty()) return null
        if (engine.size == 3 && engine.winLength == 3) {
            val m = minimaxBest(engine, board, aiMark)
            if (m >= 0) return m
        }
        return heuristicMove(engine, board, aiMark, random)
    }

    private fun minimaxBest(engine: TicTacToeEngine, board: Array<TicMark>, aiMark: TicMark): Int {
        val opp = opponent(aiMark)
        var bestScore = Int.MIN_VALUE
        var bestIdx = -1
        for (i in board.indices) {
            if (board[i] != TicMark.Empty) continue
            board[i] = aiMark
            val sc = minimax(engine, board, engine.winLength, false, aiMark, opp, 0)
            board[i] = TicMark.Empty
            if (sc > bestScore) {
                bestScore = sc
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun minimax(
        engine: TicTacToeEngine,
        board: Array<TicMark>,
        winLen: Int,
        isAiTurn: Boolean,
        aiMark: TicMark,
        humanMark: TicMark,
        depth: Int,
    ): Int {
        engine.winner(board)?.let { w ->
            return when (w) {
                aiMark -> 10 - depth
                humanMark -> depth - 10
                else -> 0
            }
        }
        if (engine.isDraw(board)) return 0
        val mark = if (isAiTurn) aiMark else humanMark
        if (isAiTurn) {
            var best = Int.MIN_VALUE
            outer@ for (i in board.indices) {
                if (board[i] != TicMark.Empty) continue
                board[i] = mark
                val s = minimax(engine, board, winLen, false, aiMark, humanMark, depth + 1)
                board[i] = TicMark.Empty
                best = maxOf(best, s)
                if (best == 10 - depth - 1) break@outer
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (i in board.indices) {
                if (board[i] != TicMark.Empty) continue
                board[i] = mark
                val s = minimax(engine, board, winLen, true, aiMark, humanMark, depth + 1)
                board[i] = TicMark.Empty
                best = minOf(best, s)
                if (best == depth - 9) break
            }
            return best
        }
    }

    private fun heuristicMove(
        engine: TicTacToeEngine,
        board: Array<TicMark>,
        aiMark: TicMark,
        random: Random,
    ): Int {
        val opp = opponent(aiMark)
        fun tryWin(m: TicMark): Int? {
            for (i in board.indices) {
                if (board[i] != TicMark.Empty) continue
                board[i] = m
                val w = engine.winner(board)
                board[i] = TicMark.Empty
                if (w == m) return i
            }
            return null
        }
        tryWin(aiMark)?.let { return it }
        tryWin(opp)?.let { return it }
        val size = engine.size
        val center = size / 2
        val centerIdx = center * size + center
        if (board[centerIdx] == TicMark.Empty) return centerIdx
        val corners = listOf(0, size - 1, size * (size - 1), engine.cellCount - 1)
        val cornerFree = corners.filter { board[it] == TicMark.Empty }.shuffled(random)
        if (cornerFree.isNotEmpty()) return cornerFree.first()
        val rest = board.indices.filter { board[it] == TicMark.Empty }.shuffled(random)
        return rest.firstOrNull() ?: board.indices.first { board[it] == TicMark.Empty }
    }

    private fun opponent(m: TicMark): TicMark = when (m) {
        TicMark.X -> TicMark.O
        TicMark.O -> TicMark.X
        TicMark.Empty -> TicMark.Empty
    }
}
