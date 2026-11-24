package app.chessoverlay.poc

import kotlin.math.abs
import kotlin.math.roundToInt

enum class Verdict { POSITION_READABLE, POSITION_PARTIAL, MOVES_ONLY, NOT_READABLE, NO_DATA }

data class GridInfo(val left: Int, val top: Int, val size: Int, val flipped: Boolean, val matchRatio: Double)

data class Report(
    val verdict: Verdict,
    val nodeCount: Int,
    val maxDepth: Int,
    val labelledNodes: Int,
    val boardCandidates: List<String>,
    val squaresSeen: Int,
    val pieceCount: Int,
    val kingsOk: Boolean,
    val conflicts: Int,
    val grid: GridInfo?,
    val position: Map<String, String>,   // petak -> kode bidak ("wp", "bk", ...)
    val sanSample: List<String>,
    val sanStrong: Int,
    val vocabulary: List<Pair<String, Int>>,
    val contentEvents: Long,
    val notes: List<String>
)

/**
 * Menjawab satu pertanyaan: dari pohon accessibility Chess.com, apa yang bisa dibaca?
 * (petak, bidak, daftar langkah, dan koordinat layar petak untuk overlay).
 * Tidak mengasumsikan format label apa pun: yang dikenali hanya pola umum, dan kosakata label
 * yang sebenarnya muncul dicatat di laporan supaya parser bisa disesuaikan dengan data nyata.
 */
object Analyzer {
    private val SQUARE = Regex("(?<![A-Za-z0-9])([a-hA-H])([1-8])(?![A-Za-z0-9])")
    private val SAN = Regex(
        "(?:O-O(?:-O)?|0-0(?:-0)?|[KQRBNkqrbn\u2654-\u2659\u265A-\u265F]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?)[+#]?[!?]{0,2}"
    )
    private val PURE_SQUARE = Regex("[a-h][1-8]")
    private val MOVE_NO = Regex("\\d+\\.{1,3}")
    private val SPLIT = Regex("[\\s,;]+")
    private val EMPTY = Regex("\\b(empty|kosong|blank)\\b")
    private val WHITE = Regex("\\b(white|putih)\\b")
    private val BLACK = Regex("\\b(black|hitam)\\b")
    private val ROLE = listOf(
        Regex("\\b(pawn|pion)\\b") to 'p',
        Regex("\\b(knight|kuda)\\b") to 'n',
        Regex("\\b(bishop|gajah)\\b") to 'b',
        Regex("\\b(rook|benteng)\\b") to 'r',
        Regex("\\b(queen|ratu|menteri)\\b") to 'q',
        Regex("\\b(king|raja)\\b") to 'k'
    )
    private const val W_SYMS = "\u2654\u2655\u2656\u2657\u2658\u2659"   // K Q R B N P
    private const val B_SYMS = "\u265A\u265B\u265C\u265D\u265E\u265F"
    private const val SYM_ORDER = "kqrbnp"

    /** "White pawn on e4" -> "wp"; juga "Pion putih" dan simbol Unicode. null jika bukan bidak. */
    fun parsePiece(label: String): String? {
        val l = label.lowercase()
        val color: Char? = when {
            WHITE.containsMatchIn(l) -> 'w'
            BLACK.containsMatchIn(l) -> 'b'
            else -> null
        }
        val role: Char? = ROLE.firstOrNull { it.first.containsMatchIn(l) }?.second
        if (color != null && role != null) return "$color$role"
        for (ch in label) {
            val wi = W_SYMS.indexOf(ch)
            if (wi >= 0) return "w" + SYM_ORDER[wi]
            val bi = B_SYMS.indexOf(ch)
            if (bi >= 0) return "b" + SYM_ORDER[bi]
        }
        return null
    }

    /** Tepat satu nama petak di dalam label -> petak itu; selain itu null. */
    fun parseSquare(label: String): String? {
        val found = SQUARE.findAll(label).map { it.groupValues[1].lowercase() + it.groupValues[2] }.toSet()
        return if (found.size == 1) found.first() else null
    }

    private fun ancestorSquare(nodes: List<NodeRec>, node: NodeRec): String? {
        var p = node.parent
        var hops = 0
        while (p >= 0 && hops < 3) {
            val par = nodes[p]
            for (label in par.labels) {
                if (parsePiece(label) == null) {
                    val sq = parseSquare(label)
                    if (sq != null) return sq
                }
            }
            p = par.parent
            hops++
        }
        return null
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return s[s.size / 2]
    }

    /** Mencocokkan grid 8x8 pada node-node petak: hasilnya kotak papan di layar + orientasi. */
    fun fitGrid(sq: Map<String, NodeRec>): GridInfo? {
        val cx = HashMap<String, Double>()
        val cy = HashMap<String, Double>()
        val widths = ArrayList<Double>()
        for ((k, nd) in sq) {
            if (nd.width < 8 || nd.height < 8) continue
            cx[k] = (nd.left + nd.right) / 2.0
            cy[k] = (nd.top + nd.bottom) / 2.0
            widths.add(minOf(nd.width, nd.height).toDouble())
        }
        if (cx.size < 16) return null
        val diffs = ArrayList<Double>()
        for (k in cx.keys) {
            if (k[0] >= 'h') continue
            val next = "${k[0] + 1}${k[1]}"
            val b = cx[next] ?: continue
            diffs.add(abs(b - cx[k]!!))
        }
        val s = if (diffs.size >= 6) median(diffs) else median(widths)
        if (s < 8) return null

        var best: GridInfo? = null
        for (flipped in listOf(false, true)) {
            val keys = ArrayList<String>()
            val lefts = ArrayList<Double>()
            val tops = ArrayList<Double>()
            for (k in cx.keys) {
                val f = k[0] - 'a'
                val r = k[1] - '1'
                val col = if (flipped) 7 - f else f
                val row = if (flipped) r else 7 - r
                keys.add(k)
                lefts.add(cx[k]!! - col * s - s / 2)
                tops.add(cy[k]!! - row * s - s / 2)
            }
            val l = median(lefts)
            val t = median(tops)
            var ok = 0
            for (i in keys.indices) {
                if (abs(lefts[i] - l) <= 0.2 * s && abs(tops[i] - t) <= 0.2 * s) ok++
            }
            val ratio = ok.toDouble() / keys.size
            val cur = best
            if (cur == null || ratio > cur.matchRatio) {
                best = GridInfo(l.roundToInt(), t.roundToInt(), s.roundToInt(), flipped, ratio)
            }
        }
        val b = best
        return if (b != null && b.matchRatio >= 0.9) b else null
    }

    fun analyze(nodes: List<NodeRec>, screenW: Int, screenH: Int, contentEvents: Long): Report {
        val n = nodes.size
        val maxDepth = nodes.maxOfOrNull { it.depth } ?: 0
        val labelled = nodes.count { it.labels.isNotEmpty() }

        val pos = HashMap<String, String>()
        val squareLabelled = HashSet<String>()
        val squareNode = HashMap<String, NodeRec>()
        var conflicts = 0
        for (node in nodes) {
            for (label in node.labels) {
                val piece = parsePiece(label)
                val sq = parseSquare(label)
                if (piece != null) {
                    val target = sq ?: ancestorSquare(nodes, node)
                    if (target != null) {
                        val old = pos[target]
                        if (old != null && old != piece) conflicts++ else pos[target] = piece
                        if (sq != null) squareNode.putIfAbsent(target, node)
                    }
                } else if (sq != null) {
                    squareLabelled.add(sq)
                    squareNode.putIfAbsent(sq, node)
                }
            }
        }
        val squaresSeen = (pos.keys + squareLabelled).size
        val wk = pos.values.count { it == "wk" }
        val bk = pos.values.count { it == "bk" }
        val kingsOk = wk == 1 && bk == 1
        val grid = fitGrid(squareNode)

        // kandidat papan: node hampir persegi dan besar
        val desc = IntArray(n)
        for (i in n - 1 downTo 1) {
            val p = nodes[i].parent
            if (p >= 0) desc[p] += 1 + desc[i]
        }
        val minW = if (screenW > 0) (screenW * 0.45).toInt() else 200
        val candNodes = nodes
            .filter { it.height > 0 && it.width >= minW && it.width.toDouble() / it.height in 0.95..1.05 }
            .sortedByDescending { it.width * it.height }
            .take(3)
        val cands = candNodes.map { c ->
            (c.cls?.substringAfterLast('.') ?: "?") + " id=" + (c.viewId ?: "-") +
                " [" + c.left + "," + c.top + "," + c.right + "," + c.bottom + "]" +
                " anak=" + c.childCount + " turunan=" + desc[c.id] +
                " label=" + (c.labels.firstOrNull()?.take(60) ?: "-")
        }

        // kosakata label yang benar-benar muncul
        val freq = HashMap<String, Int>()
        for (nd in nodes) for (l in nd.labels) if (l.length <= 40) freq[l] = (freq[l] ?: 0) + 1
        val vocab = freq.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(25)
            .map { it.key to it.value }

        // token notasi langkah (SAN)
        val sanSample = LinkedHashSet<String>()
        var strong = 0
        for (nd in nodes) for (l in nd.labels) {
            val toks = l.split(SPLIT).filter { it.isNotEmpty() }
            val listy = toks.size >= 2 && toks.all { SAN.matches(it) || MOVE_NO.matches(it) }
            for (t in toks) {
                if (!SAN.matches(t)) continue
                if (sanSample.size < 40) sanSample.add(t)
                if (listy || (toks.size == 1 && !PURE_SQUARE.matches(t))) strong++
            }
        }

        val verdict = when {
            n < 5 -> Verdict.NO_DATA
            kingsOk && conflicts == 0 && pos.size in 2..32 && squaresSeen >= 64 -> Verdict.POSITION_READABLE
            kingsOk && conflicts == 0 && pos.size in 2..32 -> Verdict.POSITION_PARTIAL
            strong >= 2 -> Verdict.MOVES_ONLY
            else -> Verdict.NOT_READABLE
        }

        val notes = ArrayList<String>()
        if (candNodes.isNotEmpty() && desc[candNodes[0].id] == 0 && candNodes[0].labels.isEmpty()) {
            notes.add("Kandidat papan terbesar tidak punya node anak maupun label: kemungkinan digambar custom (canvas), isinya tidak terlihat oleh accessibility.")
        }
        if (verdict == Verdict.POSITION_PARTIAL) {
            notes.add("Hanya $squaresSeen petak yang dilaporkan; kelengkapan bidak belum terbukti, harus divalidasi dengan legal move.")
        }
        if (conflicts > 0) notes.add("Ada $conflicts konflik (dua bidak berbeda pada satu petak): parser atau label perlu diperiksa.")
        if (grid != null) {
            notes.add("Grid layar cocok " + (grid.matchRatio * 100).roundToInt() + "% (dipakai untuk menggambar panah).")
        } else if (squaresSeen > 0) {
            notes.add("Petak berlabel ada, tetapi koordinat layarnya tidak membentuk grid 8x8 yang konsisten.")
        }
        if (contentEvents > 0) notes.add("Event perubahan konten sejak dump terakhir: $contentEvents.")

        return Report(
            verdict, n, maxDepth, labelled, cands, squaresSeen, pos.size, kingsOk, conflicts, grid,
            pos.toSortedMap(), sanSample.toList(), strong, vocab, contentEvents, notes
        )
    }

    fun toText(r: Report): String {
        val sb = StringBuilder()
        sb.append("HASIL UJI AKSESIBILITAS CHESS.COM\n")
        sb.append("Verdict: ").append(r.verdict.name).append('\n')
        sb.append("Node: ").append(r.nodeCount).append(" (kedalaman maks ").append(r.maxDepth)
            .append("), berlabel: ").append(r.labelledNodes).append('\n')
        sb.append("Petak terbaca: ").append(r.squaresSeen).append("/64 | bidak: ").append(r.pieceCount)
            .append(" | raja putih+hitam: ").append(if (r.kingsOk) "ya" else "tidak")
            .append(" | konflik: ").append(r.conflicts).append('\n')
        val g = r.grid
        if (g != null) {
            sb.append("Papan di layar: kiri=").append(g.left).append(" atas=").append(g.top)
                .append(" petak=").append(g.size).append("px, putih di ")
                .append(if (g.flipped) "ATAS (papan dibalik)" else "BAWAH").append('\n')
        } else {
            sb.append("Papan di layar: tidak bisa dipetakan dari node\n")
        }
        if (r.boardCandidates.isNotEmpty()) {
            sb.append("Kandidat papan:\n")
            r.boardCandidates.forEach { sb.append("  - ").append(it).append('\n') }
        }
        sb.append("Token langkah (SAN): kuat=").append(r.sanStrong)
        if (r.sanSample.isNotEmpty()) sb.append(" sampel: ").append(r.sanSample.take(20).joinToString(" "))
        sb.append('\n')
        if (r.vocabulary.isNotEmpty()) {
            sb.append("Label yang sering muncul:\n")
            r.vocabulary.take(15).forEach { sb.append("  \"").append(it.first).append("\" x").append(it.second).append('\n') }
        }
        r.notes.forEach { sb.append("Catatan: ").append(it).append('\n') }
        sb.append("Kesimpulan: ").append(
            when (r.verdict) {
                Verdict.POSITION_READABLE -> "Posisi dapat dibaca dari accessibility tree. Lanjut modul 2: bangun FEN dan validasi legal move."
                Verdict.POSITION_PARTIAL -> "Bidak terbaca tetapi petak kosong tidak dilaporkan. Cukup untuk FEN bila semua bidak muncul; harus divalidasi."
                Verdict.MOVES_ONLY -> "Papan tidak terbaca, tetapi daftar langkah terbaca: posisi bisa dibangun dengan memutar ulang SAN dengan validasi legal move (kelengkapan perlu diuji)."
                Verdict.NOT_READABLE -> "Papan tidak terbaca dari accessibility tree. Gunakan PGN Import."
                Verdict.NO_DATA -> "Pohon hampir kosong: pastikan Chess.com terbuka di layar dan service aktif, lalu ulangi."
            }
        ).append('\n')
        return sb.toString()
    }
}
