package app.chessoverlay.poc

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move

/**
 * Modul 2b: menjaga posisi yang valid menurut aturan catur dan mencari LANGKAH yang dimainkan di antara dua
 * snapshot. Setiap perubahan posisi hanya diterima jika ada urutan 1-2 langkah SAH (pustaka chesslib) yang
 * menghasilkan posisi baru persis. Selisih yang tidak bisa dijelaskan => sinkronisasi ulang, bukan menebak.
 * Mode yang tidak dikenali sebagai latihan ditolak (analisis hanya untuk mode yang diizinkan).
 */
class PositionTracker {
    data class PlayedMove(val side: Char, val uci: String)

    sealed class Result {
        object Same : Result()
        data class Moves(val moves: List<PlayedMove>, val ambiguous: Boolean) : Result()
        data class Synced(val sideKnown: Boolean) : Result()
        data class Rejected(val reason: String) : Result()
    }

    private var candidates: List<Board> = emptyList()   // 2 papan jika giliran belum diketahui
    private var last: String? = null

    val currentFen: String? get() = candidates.firstOrNull()?.fen

    fun reset() { candidates = emptyList(); last = null }

    fun onSnapshot(s: BoardSnapshot): Result {
        if (!s.practiceMode) {
            return Result.Rejected("mode tidak dikenali sebagai latihan; analisis dinonaktifkan")
        }
        if (s.placement == last) return Result.Same
        if (candidates.isNotEmpty()) {
            var ambiguous = false
            val matched = ArrayList<Pair<Board, List<Move>>>()
            for (b in candidates) {
                val paths = findPaths(b, s.placement)
                if (paths.isNotEmpty()) {
                    matched.add(b to paths[0])
                    if (paths.size > 1) ambiguous = true
                }
            }
            if (matched.isNotEmpty()) {
                if (matched.size > 1) ambiguous = true
                val reported = ArrayList<PlayedMove>()
                val next = ArrayList<Board>()
                for ((idx, pair) in matched.withIndex()) {
                    val b = pair.first
                    for (m in pair.second) {
                        if (idx == 0) reported.add(PlayedMove(if (b.sideToMove == Side.WHITE) 'w' else 'b', uci(m)))
                        b.doMove(m)
                    }
                    next.add(b)
                }
                candidates = next
                last = s.placement
                return Result.Moves(reported, ambiguous)
            }
        }
        return init(s)
    }

    private fun init(s: BoardSnapshot): Result {
        val hint = s.sideToMoveHint
        val sides: List<Char> = when {
            hint != null -> listOf(hint)
            s.placement == START -> listOf('w')
            else -> listOf('w', 'b')
        }
        val boards = sides.mapNotNull { buildBoard(s.placement, it) }
        if (boards.isEmpty()) return Result.Rejected("posisi tidak valid menurut aturan catur")
        candidates = boards
        last = s.placement
        return Result.Synced(boards.size == 1)
    }

    companion object {
        const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"

        fun placementOf(b: Board): String = b.fen.substringBefore(' ')

        fun uci(m: Move): String {
            val promo = when (m.promotion?.pieceType) {
                PieceType.QUEEN -> "q"
                PieceType.ROOK -> "r"
                PieceType.BISHOP -> "b"
                PieceType.KNIGHT -> "n"
                else -> ""
            }
            return m.from.name.lowercase() + m.to.name.lowercase() + promo
        }

        /** Hak rokade ditebak dari posisi raja dan benteng (riwayat sebelum snapshot pertama tidak diketahui). */
        fun castlingFor(placement: String): String {
            val rows = placement.split('/').map { row ->
                val sb = StringBuilder()
                for (ch in row) if (ch.isDigit()) repeat(ch - '0') { sb.append('.') } else sb.append(ch)
                sb.toString()
            }
            if (rows.size != 8 || rows.any { it.length != 8 }) return "-"
            val s = StringBuilder()
            if (rows[7][4] == 'K' && rows[7][7] == 'R') s.append('K')
            if (rows[7][4] == 'K' && rows[7][0] == 'R') s.append('Q')
            if (rows[0][4] == 'k' && rows[0][7] == 'r') s.append('k')
            if (rows[0][4] == 'k' && rows[0][0] == 'r') s.append('q')
            return if (s.isEmpty()) "-" else s.toString()
        }

        /** Papan hanya dibuat jika posisinya sah: tanpa pion di baris 1/8 dan raja pihak yang tidak jalan tidak di-skak. */
        fun buildBoard(placement: String, side: Char): Board? = try {
            val rows = placement.split('/')
            if (rows.size != 8 || rows[0].any { it == 'p' || it == 'P' } || rows[7].any { it == 'p' || it == 'P' }) {
                null
            } else {
                val castling = castlingFor(placement)
                val b = Board()
                b.loadFromFen("$placement $side $castling - 0 1")
                val other = Board()
                other.loadFromFen("$placement ${if (side == 'w') 'b' else 'w'} - - 0 1")
                if (other.isKingAttacked) null else b
            }
        } catch (e: Exception) {
            null
        }

        /** Urutan langkah sah (1, lalu 2 ply) dari board ke posisi target. Board dikembalikan seperti semula. */
        fun findPaths(board: Board, target: String): List<List<Move>> {
            val out = ArrayList<List<Move>>()
            for (depth in 1..2) {
                search(board, target, depth, ArrayList(), out)
                if (out.isNotEmpty()) break
            }
            return out
        }

        private fun search(board: Board, target: String, depth: Int, path: ArrayList<Move>, out: MutableList<List<Move>>) {
            val moves = ArrayList(board.legalMoves())
            for (m in moves) {
                board.doMove(m)
                path.add(m)
                if (placementOf(board) == target) out.add(ArrayList(path))
                else if (depth > 1) search(board, target, depth - 1, path, out)
                path.removeAt(path.size - 1)
                board.undoMove()
            }
        }
    }
}
