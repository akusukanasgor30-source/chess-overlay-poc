package app.chessoverlay.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture SINTETIS: menguji logika Analyzer, BUKAN format asli Chess.com
 * (format aslinya justru yang akan diungkap oleh dump dari perangkat).
 */
class AnalyzerTest {
    private fun node(
        id: Int, parent: Int, depth: Int, text: String? = null, desc: String? = null,
        l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, kids: Int = 0
    ) = NodeRec(id, parent, depth, "android.view.View", null, text, desc, null, null, l, t, r, b, false, true, kids)

    private fun startPieces(): Map<String, String> {
        val m = HashMap<String, String>()
        val back = "rnbqkbnr"
        for (i in 0 until 8) {
            val f = 'a' + i
            m["${f}1"] = "w" + back[i]; m["${f}2"] = "wp"
            m["${f}7"] = "bp"; m["${f}8"] = "b" + back[i]
        }
        return m
    }

    private val names = mapOf('p' to "pawn", 'n' to "knight", 'b' to "bishop", 'r' to "rook", 'q' to "queen", 'k' to "king")

    private fun board(flipped: Boolean, labelFor: (String, String?) -> String): List<NodeRec> {
        val out = ArrayList<NodeRec>()
        out.add(node(0, -1, 0, kids = 1, r = 1080, b = 2000))
        out.add(node(1, 0, 1, l = 40, t = 300, r = 840, b = 1100, kids = 64))
        val pcs = startPieces()
        for (f in 0 until 8) for (rk in 0 until 8) {
            val sq = "${'a' + f}${rk + 1}"
            val col = if (flipped) 7 - f else f
            val row = if (flipped) rk else 7 - rk
            val l = 40 + col * 100
            val t = 300 + row * 100
            out.add(node(out.size, 1, 2, desc = labelFor(sq, pcs[sq]), l = l, t = t, r = l + 100, b = t + 100))
        }
        return out
    }

    private val label = { sq: String, p: String? ->
        if (p == null) "Empty square $sq"
        else (if (p[0] == 'w') "White " else "Black ") + names[p[1]] + " on " + sq
    }

    @Test fun fullBoardIsReadable() {
        val r = Analyzer.analyze(board(false, label), 1080, 2000, 0)
        assertEquals(Verdict.POSITION_READABLE, r.verdict)
        assertEquals(64, r.squaresSeen); assertEquals(32, r.pieceCount); assertTrue(r.kingsOk)
        assertEquals("wk", r.position["e1"]); assertEquals("bq", r.position["d8"])
        val g = r.grid; assertNotNull(g)
        assertEquals(40, g!!.left); assertEquals(300, g.top); assertEquals(100, g.size); assertEquals(false, g.flipped)
    }

    @Test fun flippedBoardIsDetected() {
        val g = Analyzer.analyze(board(true, label), 1080, 2000, 0).grid
        assertNotNull(g); assertEquals(true, g!!.flipped); assertEquals(40, g.left); assertEquals(300, g.top)
    }

    @Test fun canvasBoardIsNotReadable() {
        val nodes = listOf(
            node(0, -1, 0, kids = 4, r = 1080, b = 2000),
            node(1, 0, 1, l = 40, t = 300, r = 1040, b = 1300),
            node(2, 0, 1, text = "Play"), node(3, 0, 1, text = "Analysis"), node(4, 0, 1, text = "Settings")
        )
        val r = Analyzer.analyze(nodes, 1080, 2000, 0)
        assertEquals(Verdict.NOT_READABLE, r.verdict)
        assertTrue(r.notes.any { it.contains("canvas") })
        assertNull(r.grid)
    }

    @Test fun moveListIsDetected() {
        val texts = listOf("1.", "e4", "e5", "2.", "Nf3", "Nc6", "3.", "Bb5")
        val nodes = ArrayList<NodeRec>()
        nodes.add(node(0, -1, 0, kids = 1)); nodes.add(node(1, 0, 1, kids = texts.size))
        texts.forEach { nodes.add(node(nodes.size, 1, 2, text = it)) }
        val r = Analyzer.analyze(nodes, 1080, 2000, 0)
        assertEquals(Verdict.MOVES_ONLY, r.verdict); assertEquals(3, r.sanStrong)
    }

    @Test fun pieceChildInheritsSquareFromParent() {
        val nodes = listOf(
            node(0, -1, 0, kids = 1), node(1, 0, 1, desc = "e4", kids = 1), node(2, 1, 2, desc = "White pawn")
        )
        assertEquals("wp", Analyzer.analyze(nodes, 1080, 2000, 0).position["e4"])
    }

    @Test fun indonesianLabels() {
        assertEquals("wp", Analyzer.parsePiece("Pion putih di e4"))
        assertEquals("bn", Analyzer.parsePiece("Kuda hitam"))
        assertEquals("wq", Analyzer.parsePiece("\u2655"))
        assertEquals("e4", Analyzer.parseSquare("Pion putih di e4"))
        assertNull(Analyzer.parseSquare("dari e2 ke e4"))
        assertNull(Analyzer.parsePiece("Settings"))
    }

    @Test fun conflictingPiecesAreReported() {
        val nodes = listOf(
            node(0, -1, 0), node(1, 0, 1, desc = "White pawn on e4"), node(2, 0, 1, desc = "Black rook on e4"),
            node(3, 0, 1, text = "x"), node(4, 0, 1, text = "y")
        )
        assertEquals(1, Analyzer.analyze(nodes, 1080, 2000, 0).conflicts)
    }
}
