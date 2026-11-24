package app.chessoverlay.poc

/** Isi layar Chess.com yang relevan, hasil membaca satu dump. */
data class BoardSnapshot(
    val placement: String,            // bagian pertama FEN ("rnbqkbnr/pppppppp/8/...")
    val pieceCount: Int,
    val grid: GridInfo?,              // kotak papan di layar (untuk overlay)
    val whiteAtBottom: Boolean?,
    val moveTokens: List<String>,     // nomor langkah + notasi yang TERLIHAT di daftar langkah, urut tampilan
    val sideToMoveHint: Char?,        // 'w' / 'b' dari daftar langkah; null jika tidak diketahui
    val practiceMode: Boolean,        // tombol "Petunjuk" dan "Urung" ada => mode latihan melawan komputer
    val inProgress: Boolean           // tombol "Menyerah" ada => permainan sudah berjalan
)

/**
 * Modul 2a: dari NodeRec (hasil dump) ke BoardSnapshot. Berdasarkan format yang TERBUKTI di dump nyata:
 * petak = contentDescription "Putih Pion di e2" / "a6, kosong", daftar langkah = TextView "5." "be5" "Nc6".
 * Mengembalikan null jika posisi tidak terbaca penuh (64 petak, 2 raja).
 */
object DumpParser {
    private val MOVE_NO = Regex("\\d+\\.{1,3}")
    private val SAN = Regex(
        "(?:O-O(?:-O)?|0-0(?:-0)?|[KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBNqrbn])?)[+#]?[!?]{0,2}"
    )

    fun placementOf(pos: Map<String, String>): String {
        val sb = StringBuilder()
        for (rank in 8 downTo 1) {
            var empty = 0
            for (f in 'a'..'h') {
                val p = pos["$f$rank"]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val c = p[1]
                    sb.append(if (p[0] == 'w') c.uppercaseChar() else c)
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 1) sb.append('/')
        }
        return sb.toString()
    }

    /** Token nomor langkah dan notasi dari subpohon yang paling banyak memuatnya (yang terkecil jika sama). */
    fun moveTokens(nodes: List<NodeRec>): List<String> {
        val n = nodes.size
        val end = IntArray(n) { it }
        for (i in n - 1 downTo 1) {
            val p = nodes[i].parent
            if (p >= 0 && end[i] > end[p]) end[p] = end[i]
        }
        var best: List<String> = emptyList()
        var bestSpan = Int.MAX_VALUE
        for (p in nodes) {
            if (p.childCount < 2) continue
            val toks = ArrayList<String>()
            for (i in p.id + 1..end[p.id]) {
                for (l in nodes[i].labels) {
                    if (!l.contains(' ') && (MOVE_NO.matches(l) || SAN.matches(l))) { toks.add(l); break }
                }
            }
            if (toks.size < 2 || toks.none { MOVE_NO.matches(it) } || toks.none { SAN.matches(it) }) continue
            val span = end[p.id] - p.id
            if (toks.size > best.size || (toks.size == best.size && span < bestSpan)) {
                best = toks; bestSpan = span
            }
        }
        return best
    }

    /** Giliran dari daftar langkah: setelah nomor terakhir, 0 langkah = putih, 1 = hitam, 2 = putih. */
    fun sideHint(tokens: List<String>): Char? {
        val lastNo = tokens.indexOfLast { MOVE_NO.matches(it) }
        if (lastNo < 0) return null
        return when (tokens.size - lastNo - 1) {
            0 -> 'w'
            1 -> 'b'
            2 -> 'w'
            else -> null
        }
    }

    fun parse(nodes: List<NodeRec>, screenW: Int, screenH: Int): BoardSnapshot? {
        val r = Analyzer.analyze(nodes, screenW, screenH, 0)
        if (r.verdict != Verdict.POSITION_READABLE) return null
        val all = nodes.flatMap { it.labels }.toHashSet()
        val tokens = moveTokens(nodes)
        return BoardSnapshot(
            placement = placementOf(r.position),
            pieceCount = r.pieceCount,
            grid = r.grid,
            whiteAtBottom = r.grid?.let { !it.flipped },
            moveTokens = tokens,
            sideToMoveHint = sideHint(tokens),
            practiceMode = "Petunjuk" in all && "Urung" in all,
            inProgress = "Menyerah" in all
        )
    }
}
