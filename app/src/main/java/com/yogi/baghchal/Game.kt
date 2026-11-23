package com.yogi.baghchal

import kotlin.random.Random

/**
 * Bagh-Chal (बाघ चाल), the traditional board game of Nepal.
 * 5 × 5 points; lines join neighbours across and down, and diagonally from every point whose row + column is even.
 * 4 tigers start in the corners. Goats are placed one per turn (20 in all) and may only move once all are placed.
 * Tigers move one step or jump over a goat in a straight line to capture it.
 * Tigers win by capturing 5 goats; goats win when no tiger can move.
 */
object Rules {
    const val EMPTY = 0
    const val GOAT = 1
    const val TIGER = 2
    const val GOATS_TOTAL = 20
    const val TIGERS_WIN_AT = 5

    /** neighbours of every point (0–24) */
    val adj: Array<IntArray> = Array(25) { p ->
        val r = p / 5
        val c = p % 5
        val out = ArrayList<Int>()
        val dirs = ArrayList(listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
        if ((r + c) % 2 == 0) dirs.addAll(listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
        for ((dr, dc) in dirs) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..4 && nc in 0..4) out.add(nr * 5 + nc)
        }
        out.toIntArray()
    }

    /** jumps from every point: (over, land) pairs along a straight line */
    val jumps: Array<List<Pair<Int, Int>>> = Array(25) { p ->
        val r = p / 5
        val c = p % 5
        val out = ArrayList<Pair<Int, Int>>()
        for (q in adj[p]) {
            val dr = q / 5 - r
            val dc = q % 5 - c
            val lr = r + 2 * dr
            val lc = c + 2 * dc
            if (lr in 0..4 && lc in 0..4) {
                val land = lr * 5 + lc
                if (adj[q].contains(land)) out.add(q to land)
            }
        }
        out
    }
}

/** A move: placing a goat (from = -1), a step, or a tiger's jump (over = captured goat). */
data class Move(val from: Int, val to: Int, val over: Int = -1)

class Board(
    val cells: IntArray = IntArray(25).also { b -> for (p in listOf(0, 4, 20, 24)) b[p] = Rules.TIGER },
    var goatsToPlace: Int = Rules.GOATS_TOTAL,
    var captured: Int = 0,
    /** whose turn: GOAT or TIGER */
    var turn: Int = Rules.GOAT,
    var moves: Int = 0
) {
    fun copy() = Board(cells.copyOf(), goatsToPlace, captured, turn, moves)

    fun legal(): List<Move> {
        val out = ArrayList<Move>()
        if (turn == Rules.GOAT) {
            if (goatsToPlace > 0) {
                for (p in 0 until 25) if (cells[p] == Rules.EMPTY) out.add(Move(-1, p))
            } else {
                for (p in 0 until 25) if (cells[p] == Rules.GOAT) for (q in Rules.adj[p]) if (cells[q] == Rules.EMPTY) out.add(Move(p, q))
            }
        } else {
            for (p in 0 until 25) if (cells[p] == Rules.TIGER) {
                for ((over, land) in Rules.jumps[p]) if (cells[over] == Rules.GOAT && cells[land] == Rules.EMPTY) out.add(Move(p, land, over))
                for (q in Rules.adj[p]) if (cells[q] == Rules.EMPTY) out.add(Move(p, q))
            }
        }
        return out
    }

    fun movesFrom(p: Int): List<Move> = legal().filter { it.from == p }

    fun play(m: Move) {
        if (m.from < 0) {
            cells[m.to] = Rules.GOAT
            goatsToPlace--
        } else {
            cells[m.to] = cells[m.from]
            cells[m.from] = Rules.EMPTY
            if (m.over >= 0) { cells[m.over] = Rules.EMPTY; captured++ }
        }
        turn = if (turn == Rules.GOAT) Rules.TIGER else Rules.GOAT
        moves++
    }

    /** GOAT or TIGER when someone has won, EMPTY while the game goes on */
    fun winner(): Int {
        if (captured >= Rules.TIGERS_WIN_AT) return Rules.TIGER
        if (turn == Rules.TIGER && legal().isEmpty()) return Rules.GOAT
        if (turn == Rules.GOAT && legal().isEmpty()) return Rules.TIGER   // goats can't move: rare, tigers win
        return Rules.EMPTY
    }

    /** tigers with nowhere to go */
    fun trapped(): Int {
        var n = 0
        for (p in 0 until 25) if (cells[p] == Rules.TIGER) {
            var free = false
            for (q in Rules.adj[p]) if (cells[q] == Rules.EMPTY) { free = true; break }
            if (!free) for ((over, land) in Rules.jumps[p]) if (cells[over] == Rules.GOAT && cells[land] == Rules.EMPTY) { free = true; break }
            if (!free) n++
        }
        return n
    }

    fun key(): String = cells.joinToString("") + "|" + goatsToPlace + "|" + turn
}

/** The computer: minimax with alpha-beta pruning. Easy makes mistakes on purpose; Hard looks far ahead. */
object Brain {
    private const val WIN = 100000

    /** from the tigers' point of view: higher is better for tigers */
    private fun score(b: Board): Int {
        when (b.winner()) {
            Rules.TIGER -> return WIN - b.moves
            Rules.GOAT -> return -WIN + b.moves
        }
        var mobility = 0
        var threats = 0
        for (p in 0 until 25) if (b.cells[p] == Rules.TIGER) {
            for (q in Rules.adj[p]) if (b.cells[q] == Rules.EMPTY) mobility++
            for ((over, land) in Rules.jumps[p]) if (b.cells[over] == Rules.GOAT && b.cells[land] == Rules.EMPTY) threats++
        }
        return b.captured * 700 + threats * 120 + mobility * 18 - b.trapped() * 260
    }

    private fun search(b: Board, depth: Int, alphaIn: Int, betaIn: Int): Int {
        if (depth == 0 || b.winner() != Rules.EMPTY) return score(b)
        var alpha = alphaIn
        var beta = betaIn
        val moves = ordered(b)
        if (b.turn == Rules.TIGER) {
            var best = Int.MIN_VALUE
            for (m in moves) {
                val n = b.copy(); n.play(m)
                best = maxOf(best, search(n, depth - 1, alpha, beta))
                alpha = maxOf(alpha, best)
                if (alpha >= beta) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (m in moves) {
                val n = b.copy(); n.play(m)
                best = minOf(best, search(n, depth - 1, alpha, beta))
                beta = minOf(beta, best)
                if (alpha >= beta) break
            }
            return best
        }
    }

    /** captures first: much faster pruning */
    private fun ordered(b: Board): List<Move> = b.legal().sortedByDescending { if (it.over >= 0) 1 else 0 }

    /** level 0 easy · 1 medium · 2 hard */
    fun best(b: Board, level: Int): Move? {
        val moves = b.legal()
        if (moves.isEmpty()) return null
        if (level == 0 && Random.nextInt(100) < 35) return moves.random()
        val depth = when (level) {
            0 -> 1
            1 -> 3
            else -> if (b.goatsToPlace > 12) 4 else 5
        }
        val tiger = b.turn == Rules.TIGER
        var bestScore = if (tiger) Int.MIN_VALUE else Int.MAX_VALUE
        val bestMoves = ArrayList<Move>()
        for (m in ordered(b)) {
            val n = b.copy(); n.play(m)
            val s = search(n, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE)
            val better = if (tiger) s > bestScore else s < bestScore
            if (better) { bestScore = s; bestMoves.clear(); bestMoves.add(m) }
            else if (s == bestScore) bestMoves.add(m)
        }
        return bestMoves.randomOrNull() ?: moves.random()
    }
}
