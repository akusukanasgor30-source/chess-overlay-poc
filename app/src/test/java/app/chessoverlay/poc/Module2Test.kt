package app.chessoverlay.poc

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** dump_start.json dan dump_after_moves.json adalah dump NYATA dari Chess.com Android (mode latihan). */
class Module2Test {
    private fun load(name: String): List<NodeRec> {
        val text = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()
        val arr = JSONObject(text).getJSONArray("nodes")
        fun str(o: JSONObject, k: String): String? = if (o.has(k)) o.getString(k) else null
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val b = o.getJSONArray("b")
            NodeRec(
                o.getInt("i"), o.getInt("p"), o.getInt("d"), str(o, "cls"), str(o, "vid"),
                str(o, "text"), str(o, "desc"), str(o, "state"), str(o, "hint"),
                b.getInt(0), b.getInt(1), b.getInt(2), b.getInt(3),
                o.getBoolean("clk"), o.getBoolean("imp"), o.getInt("kids"),
                if (o.has("row")) o.getInt("row") else -1, if (o.has("col")) o.getInt("col") else -1
            )
        }
    }

    private fun snap(placement: String, side: Char? = null, practice: Boolean = true) =
        BoardSnapshot(placement, 0, null, true, emptyList(), side, practice, true)

    private fun placement(b: Board) = PositionTracker.placementOf(b)

    // ---- parser pada dump nyata ----
    @Test fun realDumpStartPosition() {
        val s = DumpParser.parse(load("dump_start.json"), 1080, 2114)
        assertNotNull(s)
        assertEquals(PositionTracker.START, s!!.placement)
        assertEquals(32, s.pieceCount)
        assertEquals(true, s.whiteAtBottom)
        assertEquals(true, s.practiceMode)
        assertEquals(false, s.inProgress)          // tombol "Keluar", belum ada langkah
        assertEquals(emptyList<String>(), s.moveTokens)
        assertEquals(135, s.grid!!.size); assertEquals(690, s.grid!!.top)
    }

    @Test fun realDumpAfterMoves() {
        val s = DumpParser.parse(load("dump_after_moves.json"), 1080, 2114)
        assertNotNull(s)
        assertEquals("r2qkb1r/pppbpppp/2n2B2/3p4/3P4/5N1P/PPP1PPP1/RN1QKB1R", s!!.placement)
        assertEquals(31, s.pieceCount)
        assertEquals(listOf("5.", "be5", "Nc6", "6.", "bxf6"), s.moveTokens)
        assertEquals('b', s.sideToMoveHint)        // putih baru melangkah (6. bxf6), giliran hitam
        assertEquals(true, s.inProgress)           // tombol "Menyerah"
        assertEquals(true, s.practiceMode)
    }

    @Test fun realDumpLateGame() {
        // huruf bidak putih ditulis huruf kecil di daftar langkah ("na6", "nc6"); sebelumnya terbuang dan giliran salah
        val s = DumpParser.parse(load("dump_late.json"), 1080, 2114)!!
        assertEquals("1r1r2k1/pb3pp1/p1N1p2p/8/8/8/P1P1KPPP/8", s.placement)
        assertEquals(17, s.pieceCount)
        assertEquals(listOf("na6", "bxa6", "21.", "nc6", "Bb7"), s.moveTokens)
        assertEquals('w', s.sideToMoveHint)        // 21...Bb7 adalah langkah hitam, jadi giliran putih
    }

    @Test fun realLatePositionTracksRealMoves() {
        val t = PositionTracker()
        val s = DumpParser.parse(load("dump_late.json"), 1080, 2114)!!
        assertEquals(PositionTracker.Result.Synced(true), t.onSnapshot(s))
        assertTrue(t.currentFen!!.contains(" w "))
        // putih Nxd8, lalu hitam Rxd8 (dua langkah di antara dua dump)
        val r = t.onSnapshot(snap("3r2k1/pb3pp1/p3p2p/8/8/8/P1P1KPPP/8"))
        assertEquals(
            PositionTracker.Result.Moves(listOf(PositionTracker.PlayedMove('w', "c6d8"), PositionTracker.PlayedMove('b', "b8d8")), false), r
        )
    }

    @Test fun wrongHintStillRecoversOnNextMove() {
        val t = PositionTracker()
        // hint 'b' salah: sebenarnya putih yang jalan (e2e4)
        t.onSnapshot(snap("4k3/8/8/8/8/8/4P3/4K3", 'b'))
        val r = t.onSnapshot(snap("4k3/8/8/8/4P3/8/8/4K3"))
        assertEquals(PositionTracker.Result.Moves(listOf(PositionTracker.PlayedMove('w', "e2e4")), false), r)
    }

    @Test fun sideHintFromMoveList() {
        assertEquals('w', DumpParser.sideHint(listOf("na6", "bxa6", "21.", "nc6", "Bb7")))
        assertEquals('b', DumpParser.sideHint(listOf("5.", "be5", "Nc6", "6.", "bxf6")))
        assertEquals('w', DumpParser.sideHint(listOf("1.", "e4", "e5")))
        assertEquals('w', DumpParser.sideHint(listOf("1.")))
        assertNull(DumpParser.sideHint(emptyList()))
    }

    // ---- tracker ----
    @Test fun realDumpPositionIsLegalAndSynced() {
        val t = PositionTracker()
        val s = DumpParser.parse(load("dump_after_moves.json"), 1080, 2114)!!
        val r = t.onSnapshot(s)
        assertEquals(PositionTracker.Result.Synced(true), r)
        assertTrue(t.currentFen!!.contains(" b "))
    }

    @Test fun oneAndTwoPliesBetweenSnapshots() {
        val t = PositionTracker()
        assertTrue(t.onSnapshot(snap(PositionTracker.START)) is PositionTracker.Result.Synced)
        val b = Board()
        b.doMove(Move(Square.E2, Square.E4))
        val p1 = placement(b)
        assertEquals(PositionTracker.Result.Moves(listOf(PositionTracker.PlayedMove('w', "e2e4")), false), t.onSnapshot(snap(p1)))
        b.doMove(Move(Square.E7, Square.E5)); b.doMove(Move(Square.G1, Square.F3))
        val r = t.onSnapshot(snap(placement(b)))
        assertEquals(
            PositionTracker.Result.Moves(listOf(PositionTracker.PlayedMove('b', "e7e5"), PositionTracker.PlayedMove('w', "g1f3")), false), r
        )
        assertEquals(PositionTracker.Result.Same, t.onSnapshot(snap(placement(b))))
    }

    @Test fun castling() {
        val t = PositionTracker()
        t.onSnapshot(snap("r3k2r/8/8/8/8/8/8/R3K2R", 'w'))
        val r = t.onSnapshot(snap("r3k2r/8/8/8/8/8/8/R4RK1"))
        assertEquals(PositionTracker.Result.Moves(listOf(PositionTracker.PlayedMove('w', "e1g1")), false), r)
    }

    @Test fun promotion() {
        val t = PositionTracker()
        t.onSnapshot(snap("4k3/P7/8/8/8/8/8/4K3", 'w'))
        val r = t.onSnapshot(snap("Q3k3/8/8/8/8/8/8/4K3"))
        assertEquals(PositionTracker.Result.Moves(listOf(PositionTracker.PlayedMove('w', "a7a8q")), false), r)
    }

    @Test fun enPassant() {
        val t = PositionTracker()
        t.onSnapshot(snap("4k3/3p4/8/4P3/8/8/8/4K3", 'b'))
        assertEquals(
            PositionTracker.Result.Moves(listOf(PositionTracker.PlayedMove('b', "d7d5")), false),
            t.onSnapshot(snap("4k3/8/8/3pP3/8/8/8/4K3"))
        )
        assertEquals(
            PositionTracker.Result.Moves(listOf(PositionTracker.PlayedMove('w', "e5d6")), false),
            t.onSnapshot(snap("4k3/8/3P4/8/8/8/8/4K3"))
        )
    }

    @Test fun unexplainedJumpResyncsInsteadOfGuessing() {
        val t = PositionTracker()
        t.onSnapshot(snap(PositionTracker.START))
        val r = t.onSnapshot(snap("r2qkb1r/pppbpppp/2n2B2/3p4/3P4/5N1P/PPP1PPP1/RN1QKB1R", 'b'))
        assertEquals(PositionTracker.Result.Synced(true), r)
    }

    @Test fun modeThatIsNotPracticeIsRejected() {
        val r = PositionTracker().onSnapshot(snap(PositionTracker.START, practice = false))
        assertTrue(r is PositionTracker.Result.Rejected)
    }

    @Test fun illegalPositionIsRejected() {
        // putih jalan, tetapi raja hitam sedang di-skak: tidak mungkin terjadi dalam permainan sah
        val r = PositionTracker().onSnapshot(snap("4k3/8/8/8/8/8/8/4RK2", 'w'))
        assertTrue(r is PositionTracker.Result.Rejected)
    }
}
