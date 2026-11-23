package app.chessoverlay.poc

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/** Membekukan pohon accessibility menjadi daftar NodeRec dan menulisnya sebagai JSON. Tanpa screenshot. */
object Snapshot {
    private const val MAX_NODES = 6000
    private const val MAX_DEPTH = 80

    @Suppress("DEPRECATION")
    fun capture(root: AccessibilityNodeInfo): List<NodeRec> {
        val out = ArrayList<NodeRec>()
        fun walk(n: AccessibilityNodeInfo, parent: Int, depth: Int) {
            if (out.size >= MAX_NODES || depth > MAX_DEPTH) return
            val id = out.size
            val r = Rect()
            n.getBoundsInScreen(r)
            val ci = n.collectionItemInfo
            out.add(
                NodeRec(
                    id = id, parent = parent, depth = depth,
                    cls = n.className?.toString(),
                    viewId = n.viewIdResourceName,
                    text = n.text?.toString(),
                    desc = n.contentDescription?.toString(),
                    state = if (Build.VERSION.SDK_INT >= 30) n.stateDescription?.toString() else null,
                    hint = if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString() else null,
                    left = r.left, top = r.top, right = r.right, bottom = r.bottom,
                    clickable = n.isClickable,
                    important = n.isImportantForAccessibility,
                    childCount = n.childCount,
                    row = ci?.rowIndex ?: -1,
                    col = ci?.columnIndex ?: -1
                )
            )
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c, id, depth + 1)
                c.recycle()
            }
        }
        walk(root, -1, 0)
        return out
    }

    fun toJson(meta: Map<String, Any?>, report: Report, nodes: List<NodeRec>, extra: String = ""): String {
        val root = JSONObject()
        root.put("meta", JSONObject(meta))
        val rep = JSONObject()
        rep.put("verdict", report.verdict.name)
        rep.put("text", Analyzer.toText(report) + "\n" + extra)
        rep.put("squaresSeen", report.squaresSeen)
        rep.put("pieceCount", report.pieceCount)
        rep.put("position", JSONObject(report.position as Map<*, *>))
        root.put("report", rep)
        val arr = JSONArray()
        for (n in nodes) {
            val o = JSONObject()
            o.put("i", n.id); o.put("p", n.parent); o.put("d", n.depth)
            o.put("cls", n.cls); o.put("vid", n.viewId)
            o.put("text", n.text); o.put("desc", n.desc); o.put("state", n.state); o.put("hint", n.hint)
            o.put("b", JSONArray(listOf(n.left, n.top, n.right, n.bottom)))
            o.put("clk", n.clickable); o.put("imp", n.important); o.put("kids", n.childCount)
            if (n.row >= 0) { o.put("row", n.row); o.put("col", n.col) }
            arr.put(o)
        }
        root.put("nodes", arr)
        return root.toString()
    }
}
