package com.example.bible.games.checkers

import kotlin.math.abs
import kotlin.math.sign

/**
 * Клетка: светлые квадраты досоки не используются ([Void]);
 * игра только на тёмных клетках, индекс [idx] = row*8+col, где row 0 — верх (чёрные).
 */
enum class CheckersCell {
    /** Светлое поле — пусто и недоступно. */
    Void,
    Empty,
    LightMan,
    LightKing,
    DarkMan,
    DarkKing,
}

enum class CheckersSide {
    Light,
    Dark,
}

enum class CheckersGameMode {
    HUMAN_VS_AI,
    TWO_HUMANS,
}

/** Путь хода: индексы клеток от старта до финиша (вкл. многоходовые взятия). */
data class CheckersPath(val cells: List<Int>) {
    init {
        require(cells.size >= 2)
    }
}

object CheckersEngine {
    const val SIZE = 8

    fun isPlayable(r: Int, c: Int): Boolean =
        r in 0 until SIZE && c in 0 until SIZE && (r + c) % 2 == 1

    fun idx(r: Int, c: Int): Int = r * SIZE + c

    fun row(i: Int): Int = i / SIZE

    fun col(i: Int): Int = i % SIZE

    fun initialBoard(): Array<CheckersCell> {
        val b = Array(SIZE * SIZE) { CheckersCell.Void }
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                if (!isPlayable(r, c)) continue
                b[idx(r, c)] = when {
                    r < 3 -> CheckersCell.DarkMan
                    r > 4 -> CheckersCell.LightMan
                    else -> CheckersCell.Empty
                }
            }
        }
        return b
    }

    fun sideOf(cell: CheckersCell): CheckersSide? = when (cell) {
        CheckersCell.LightMan, CheckersCell.LightKing -> CheckersSide.Light
        CheckersCell.DarkMan, CheckersCell.DarkKing -> CheckersSide.Dark
        else -> null
    }

    fun isKing(c: CheckersCell): Boolean =
        c == CheckersCell.LightKing || c == CheckersCell.DarkKing

    fun promote(row: Int, piece: CheckersCell): CheckersCell {
        return when (piece) {
            CheckersCell.LightMan -> if (row == 0) CheckersCell.LightKing else piece
            CheckersCell.DarkMan -> if (row == SIZE - 1) CheckersCell.DarkKing else piece
            else -> piece
        }
    }

    /** Все составные ходы: при наличии взятий — только они (обязательное взятие). */
    fun legalPaths(board: Array<CheckersCell>, side: CheckersSide): List<CheckersPath> {
        val captures = allCapturePaths(board, side)
        if (captures.isNotEmpty()) return captures
        return simplePaths(board, side)
    }

    fun hasPieces(board: Array<CheckersCell>, side: CheckersSide): Boolean {
        for (c in board) {
            if (c == CheckersCell.Void || c == CheckersCell.Empty) continue
            if (sideOf(c) == side) return true
        }
        return false
    }

    /** Полный ход как цепочка одиночных взятий/шагов с учётом превращения в дамку по пути. */
    fun applyPath(board: Array<CheckersCell>, path: CheckersPath): Array<CheckersCell> {
        var cur = board
        for (i in 0 until path.cells.size - 1) {
            cur = applySingleJump(cur, path.cells[i], path.cells[i + 1])
        }
        return cur
    }

    private fun applySingleJump(board: Array<CheckersCell>, from: Int, to: Int): Array<CheckersCell> {
        val out = board.copyOf()
        val piece = out[from]
        require(sideOf(piece) != null)
        require(board[to] == CheckersCell.Empty)
        val r1 = row(from)
        val c1 = col(from)
        val r2 = row(to)
        val c2 = col(to)
        val dist = abs(r2 - r1)
        require(abs(c2 - c1) == dist) { "not diagonal" }
        out[from] = CheckersCell.Empty
        when {
            dist == 1 -> {
                /* простой ход шашки или дамки на соседнее поле */
            }
            else -> {
                val mid = midpointCaptured(from, to, board)
                if (mid != null) {
                    val (mr, mc) = mid
                    if (isPlayable(mr, mc)) out[idx(mr, mc)] = CheckersCell.Empty
                } else {
                    require(isKing(piece)) { "only king may slide" }
                    require(diagonalInteriorsEmpty(board, from, to)) { "path not empty" }
                }
            }
        }
        out[to] = promote(r2, piece)
        return out
    }

    /** Клетки строго между [from] и [to] на одной диагонали — все пустые (ход дамки без взятия). */
    private fun diagonalInteriorsEmpty(board: Array<CheckersCell>, from: Int, to: Int): Boolean {
        val dr = (row(to) - row(from)).sign
        val dc = (col(to) - col(from)).sign
        var r = row(from) + dr
        var c = col(from) + dc
        while (r != row(to) || c != col(to)) {
            if (board[idx(r, c)] != CheckersCell.Empty) return false
            r += dr
            c += dc
        }
        return true
    }

    private fun simplePaths(board: Array<CheckersCell>, side: CheckersSide): List<CheckersPath> {
        val result = ArrayList<CheckersPath>()
        for (i in board.indices) {
            val p = board[i]
            if (sideOf(p) != side) continue
            if (p == CheckersCell.Void) continue
            val r = row(i)
            val c = col(i)
            if (p == CheckersCell.LightMan || p == CheckersCell.DarkMan) {
                val forward = if (side == CheckersSide.Light) -1 else 1
                for (dc in listOf(-1, 1)) {
                    val nr = r + forward
                    val nc = c + dc
                    if (!isPlayable(nr, nc)) continue
                    val t = idx(nr, nc)
                    if (board[t] != CheckersCell.Empty) continue
                    result.add(CheckersPath(listOf(i, t)))
                }
            } else {
                for (dr in listOf(-1, 1)) {
                    for (dc in listOf(-1, 1)) {
                        var nr = r + dr
                        var nc = c + dc
                        while (isPlayable(nr, nc)) {
                            val t = idx(nr, nc)
                            if (board[t] != CheckersCell.Empty) break
                            result.add(CheckersPath(listOf(i, t)))
                            nr += dr
                            nc += dc
                        }
                    }
                }
            }
        }
        return result
    }

    private fun allCapturePaths(board: Array<CheckersCell>, side: CheckersSide): List<CheckersPath> {
        val all = ArrayList<CheckersPath>()
        for (i in board.indices) {
            val p = board[i]
            if (sideOf(p) != side) continue
            if (p == CheckersCell.Void) continue
            captureDfs(board, side, i, listOf(i), all)
        }
        return all.distinctBy { it.cells }
    }

    private fun captureDfs(
        board: Array<CheckersCell>,
        side: CheckersSide,
        at: Int,
        path: List<Int>,
        sink: MutableList<CheckersPath>,
    ) {
        val jumps = possibleJumpsFrom(board, at)
        if (jumps.isEmpty()) {
            if (path.size > 1) sink.add(CheckersPath(path))
            return
        }
        for (j in jumps) {
            val nextBoard = applySingleJump(board, at, j.landing)
            captureDfs(nextBoard, side, j.landing, path + j.landing, sink)
        }
    }

    private data class Jump(val landing: Int)

    private fun possibleJumpsFrom(board: Array<CheckersCell>, from: Int): List<Jump> {
        val r = row(from)
        val c = col(from)
        val piece = board[from]
        val me = sideOf(piece) ?: return emptyList()
        val out = ArrayList<Jump>()
        when (piece) {
            CheckersCell.LightMan, CheckersCell.DarkMan -> {
                for (dr in listOf(-1, 1)) {
                    for (dc in listOf(-1, 1)) {
                        val er = r + dr * 2
                        val ec = c + dc * 2
                        if (!isPlayable(er, ec)) continue
                        val midR = r + dr
                        val midC = c + dc
                        if (!isPlayable(midR, midC)) continue
                        val mid = idx(midR, midC)
                        val land = idx(er, ec)
                        if (board[land] != CheckersCell.Empty) continue
                        val cap = board[mid]
                        val capSide = sideOf(cap)
                        if (capSide != null && capSide != me) {
                            out.add(Jump(land))
                        }
                    }
                }
            }
            CheckersCell.LightKing, CheckersCell.DarkKing -> {
                for (dr in listOf(-1, 1)) {
                    for (dc in listOf(-1, 1)) {
                        var scanR = r + dr
                        var scanC = c + dc
                        while (isPlayable(scanR, scanC) && board[idx(scanR, scanC)] == CheckersCell.Empty) {
                            scanR += dr
                            scanC += dc
                        }
                        if (!isPlayable(scanR, scanC)) continue
                        val enemyIdx = idx(scanR, scanC)
                        val enemy = board[enemyIdx]
                        val es = sideOf(enemy)
                        if (es == null || es == me) continue
                        var lr = scanR + dr
                        var lc = scanC + dc
                        while (isPlayable(lr, lc)) {
                            val li = idx(lr, lc)
                            if (board[li] != CheckersCell.Empty) break
                            out.add(Jump(li))
                            lr += dr
                            lc += dc
                        }
                    }
                }
            }
            else -> {}
        }
        return out
    }

    /** Для простого шашечного взятия — середина; для длинной дамки — позиция битой фигуры на луче. */
    private fun midpointCaptured(from: Int, to: Int, board: Array<CheckersCell>): Pair<Int, Int>? {
        val r1 = row(from)
        val c1 = col(from)
        val r2 = row(to)
        val c2 = col(to)
        val dr = (r2 - r1).sign
        val dc = (c2 - c1).sign
        if (dr == 0 || dc == 0) return null
        if (abs(r2 - r1) != abs(c2 - c1)) return null
        val dist = abs(r2 - r1)
        if (dist == 2) {
            val mr = (r1 + r2) / 2
            val mc = (c1 + c2) / 2
            return mr to mc
        }
        val piece = board[from]
        if (piece != CheckersCell.LightKing && piece != CheckersCell.DarkKing) return null
        var r = r1 + dr
        var c = c1 + dc
        while (r != r2) {
            val cell = board[idx(r, c)]
            if (sideOf(cell) != null) return r to c
            r += dr
            c += dc
        }
        return null
    }
}

object CheckersAi {
    fun pickPath(
        board: Array<CheckersCell>,
        side: CheckersSide,
        random: kotlin.random.Random,
    ): CheckersPath? {
        val paths = CheckersEngine.legalPaths(board, side)
        if (paths.isEmpty()) return null
        fun score(p: CheckersPath): Int {
            var s = p.cells.size * 10
            val end = board[p.cells.first()]
            val endRow = CheckersEngine.row(p.cells.last())
            if (end == CheckersCell.LightMan && endRow == 0) s += 5
            if (end == CheckersCell.DarkMan && endRow == CheckersEngine.SIZE - 1) s += 5
            return s
        }
        val best = paths.maxByOrNull { score(it) } ?: return null
        val tied = paths.filter { score(it) == score(best) }
        return tied[random.nextInt(tied.size)]
    }
}
