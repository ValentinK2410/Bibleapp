package com.example.bible.games.pipes

import kotlin.random.Random

enum class PipeDir(val bit: Int) {
    N(1),
    E(2),
    S(4),
    W(8),
    ;

    val opposite: PipeDir
        get() = when (this) {
            N -> S
            E -> W
            S -> N
            W -> E
        }

    companion object {
        fun fromDelta(dr: Int, dc: Int): PipeDir? = when {
            dr == -1 && dc == 0 -> N
            dr == 1 && dc == 0 -> S
            dr == 0 && dc == -1 -> W
            dr == 0 && dc == 1 -> E
            else -> null
        }
    }
}

enum class PipeKind {
    STRAIGHT,
    CORNER,
    TEE,
    CROSS,
    SOURCE,
    SINK,
}

data class PipeTile(
    val kind: PipeKind,
    val rotation: Int = 0,
) {
    fun rotated(): PipeTile = copy(rotation = (rotation + 1) % 4)

    fun connectionMask(): Int = PipePuzzleEngine.rotateMask(PipePuzzleEngine.baseMask(kind), rotation)

    fun hasConnection(dir: PipeDir): Boolean = connectionMask() and dir.bit != 0
}

data class PipePuzzleStatus(
    val connectedCount: Int,
    val totalCount: Int,
    val sinkConnected: Boolean,
    val isSolved: Boolean,
    val connectedIndices: Set<Int>,
)

object PipePuzzleEngine {
    const val MIN_SIZE = 4
    const val MAX_SIZE = 5

    fun baseMask(kind: PipeKind): Int = when (kind) {
        PipeKind.STRAIGHT -> PipeDir.N.bit or PipeDir.S.bit
        PipeKind.CORNER -> PipeDir.N.bit or PipeDir.E.bit
        PipeKind.TEE -> PipeDir.N.bit or PipeDir.E.bit or PipeDir.S.bit
        PipeKind.CROSS -> PipeDir.N.bit or PipeDir.E.bit or PipeDir.S.bit or PipeDir.W.bit
        PipeKind.SOURCE -> PipeDir.S.bit
        PipeKind.SINK -> PipeDir.N.bit
    }

    fun rotateMask(mask: Int, quarterTurns: Int): Int {
        var m = mask
        repeat(quarterTurns and 3) {
            var next = 0
            if (m and PipeDir.N.bit != 0) next = next or PipeDir.E.bit
            if (m and PipeDir.E.bit != 0) next = next or PipeDir.S.bit
            if (m and PipeDir.S.bit != 0) next = next or PipeDir.W.bit
            if (m and PipeDir.W.bit != 0) next = next or PipeDir.N.bit
            m = next
        }
        return m
    }

    fun newGame(size: Int, random: Random = Random.Default): List<PipeTile> {
        require(size in MIN_SIZE..MAX_SIZE)
        val solved = buildSolvedBoard(size, random)
        return solved.map { tile ->
            val extraTurns = random.nextInt(1, 4)
            tile.copy(rotation = (tile.rotation + extraTurns) % 4)
        }
    }

    fun isSolved(tiles: List<PipeTile>, size: Int): Boolean = evaluate(tiles, size).isSolved

    fun evaluate(tiles: List<PipeTile>, size: Int): PipePuzzleStatus {
        val total = size * size
        if (tiles.size != total) {
            return PipePuzzleStatus(0, total, sinkConnected = false, isSolved = false, connectedIndices = emptySet())
        }
        val sourceIdx = tiles.indexOfFirst { it.kind == PipeKind.SOURCE }
        val sinkIdx = tiles.indexOfFirst { it.kind == PipeKind.SINK }
        if (sourceIdx < 0 || sinkIdx < 0) {
            return PipePuzzleStatus(0, total, sinkConnected = false, isSolved = false, connectedIndices = emptySet())
        }

        val visited = BooleanArray(total)
        val queue = ArrayDeque<Int>()
        queue.add(sourceIdx)
        visited[sourceIdx] = true

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val r = idx / size
            val c = idx % size
            val tile = tiles[idx]
            for ((dr, dc) in NEIGHBOR_DELTAS) {
                val nr = r + dr
                val nc = c + dc
                if (nr !in 0 until size || nc !in 0 until size) continue
                val nIdx = nr * size + nc
                if (visited[nIdx]) continue
                val dir = PipeDir.fromDelta(dr, dc) ?: continue
                if (!canConnect(tile, dir, tiles[nIdx])) continue
                visited[nIdx] = true
                queue.add(nIdx)
            }
        }

        val connected = visited.indices.filter { visited[it] }.toSet()
        return PipePuzzleStatus(
            connectedCount = connected.size,
            totalCount = total,
            sinkConnected = sinkIdx in connected,
            isSolved = connected.size == total,
            connectedIndices = connected,
        )
    }

    fun canConnect(from: PipeTile, toward: PipeDir, to: PipeTile): Boolean {
        return from.hasConnection(toward) && to.hasConnection(toward.opposite)
    }

    private val NEIGHBOR_DELTAS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    private fun buildSolvedBoard(size: Int, random: Random): List<PipeTile> {
        val path = findHamiltonianPath(size, random)
            ?: throw IllegalStateException("Не удалось построить поле $size×$size")
        val pathIndex = path.withIndex().associate { it.value to it.index }
        val sourceIdx = path.first()
        val sinkIdx = path.last()

        return List(size * size) { idx ->
            val pos = pathIndex[idx] ?: error("Клетка $idx не на пути")
            val dirs = mutableSetOf<PipeDir>()
            if (pos > 0) {
                dirBetween(idx, path[pos - 1], size)?.let { dirs += it }
            }
            if (pos < path.lastIndex) {
                dirBetween(idx, path[pos + 1], size)?.let { dirs += it }
            }
            val kind = when (idx) {
                sourceIdx -> PipeKind.SOURCE
                sinkIdx -> PipeKind.SINK
                else -> kindForDirs(dirs)
            }
            PipeTile(kind = kind, rotation = rotationForDirs(kind, dirs))
        }
    }

    /** Один непрерывный маршрут через все клетки — без ответвлений и «лишних» труб. */
    private fun findHamiltonianPath(size: Int, random: Random): List<Int>? {
        val total = size * size
        val starts = (0 until total).shuffled(random)
        for (start in starts) {
            val path = mutableListOf<Int>()
            val visited = BooleanArray(total)
            if (searchHamiltonianPath(size, start, path, visited, random)) {
                return path
            }
        }
        return null
    }

    private fun searchHamiltonianPath(
        size: Int,
        idx: Int,
        path: MutableList<Int>,
        visited: BooleanArray,
        random: Random,
    ): Boolean {
        path.add(idx)
        visited[idx] = true
        if (path.size == size * size) return true

        val r = idx / size
        val c = idx % size
        val neighbors = NEIGHBOR_DELTAS
            .mapNotNull { (dr, dc) ->
                val nr = r + dr
                val nc = c + dc
                if (nr !in 0 until size || nc !in 0 until size) return@mapNotNull null
                val nIdx = nr * size + nc
                if (visited[nIdx]) return@mapNotNull null
                nIdx
            }
            .shuffled(random)

        for (nIdx in neighbors) {
            if (searchHamiltonianPath(size, nIdx, path, visited, random)) return true
        }

        visited[idx] = false
        path.removeAt(path.lastIndex)
        return false
    }

    private fun dirBetween(from: Int, to: Int, size: Int): PipeDir? {
        val fr = from / size
        val fc = from % size
        val tr = to / size
        val tc = to % size
        return PipeDir.fromDelta(tr - fr, tc - fc)
    }

    private fun kindForDirs(dirs: Set<PipeDir>): PipeKind = when (dirs.size) {
        2 -> {
            val list = dirs.toList()
            val d0 = list[0]
            val d1 = list[1]
            if (d0.opposite == d1) PipeKind.STRAIGHT else PipeKind.CORNER
        }
        else -> PipeKind.STRAIGHT
    }

    private fun rotationForDirs(kind: PipeKind, dirs: Set<PipeDir>): Int {
        val target = dirs.fold(0) { acc, d -> acc or d.bit }
        for (turn in 0..3) {
            if (rotateMask(baseMask(kind), turn) == target) return turn
        }
        return 0
    }
}
