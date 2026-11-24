package app.chessoverlay.poc

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * PoC modul 1. Hanya paket com.chess (lihat accessibility_service_config.xml).
 * - Event konten hanya dihitung (murah). Pohon node dibaca SEKALI per ketukan tombol DUMP.
 * - Tidak ada screenshot. Hasil: dumps/dump_*.json di penyimpanan privat aplikasi (maks 3 terakhir).
 * - Tombol memakai TYPE_ACCESSIBILITY_OVERLAY, jadi tidak perlu izin "tampil di atas aplikasi lain".
 */
class ChessA11yService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val contentEvents = AtomicLong(0)
    private var lastDumpEvents = 0L
    private val tracker = PositionTracker()   // hanya diakses dari thread worker
    private var windowManager: WindowManager? = null
    private var button: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        showButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) contentEvents.incrementAndGet()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideButton()
        worker.shutdown()
        super.onDestroy()
    }

    private fun showButton() {
        if (button != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            text = "DUMP"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC1E3A8A.toInt())
            setPadding(28, 18, 28, 18)
            setOnClickListener { dump() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 8
            y = 220
        }
        wm.addView(tv, lp)
        windowManager = wm
        button = tv
    }

    private fun hideButton() {
        val b = button ?: return
        try { windowManager?.removeView(b) } catch (_: Exception) {}
        button = null
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    /** Modul 2: posisi -> FEN -> langkah sah di antara dump. Teksnya masuk ke laporan. */
    private fun describeModule2(nodes: List<NodeRec>, w: Int, h: Int): String {
        val snap = DumpParser.parse(nodes, w, h) ?: return "MODUL 2: posisi tidak terbaca penuh (perlu 64 petak dan 2 raja)."
        val sb = StringBuilder("MODUL 2\n")
        sb.append("Mode latihan: ").append(if (snap.practiceMode) "ya" else "TIDAK (analisis dinonaktifkan)")
            .append(" | permainan berjalan: ").append(if (snap.inProgress) "ya" else "belum").append('\n')
        sb.append("Placement: ").append(snap.placement).append('\n')
        sb.append("Daftar langkah terlihat: ").append(snap.moveTokens.joinToString(" ").ifEmpty { "-" })
            .append(" | giliran dari daftar: ").append(snap.sideToMoveHint ?: '?').append('\n')
        when (val r = tracker.onSnapshot(snap)) {
            is PositionTracker.Result.Same -> sb.append("Posisi sama dengan dump sebelumnya.\n")
            is PositionTracker.Result.Moves -> sb.append("Langkah sah terdeteksi: ")
                .append(r.moves.joinToString(" ") { it.side + ":" + it.uci })
                .append(if (r.ambiguous) " (AMBIGU)" else "").append('\n')
            is PositionTracker.Result.Synced -> sb.append("Posisi diinisialisasi/disinkronkan ulang (giliran ")
                .append(if (r.sideKnown) "diketahui" else "belum pasti").append(").\n")
            is PositionTracker.Result.Rejected -> sb.append("Ditolak: ").append(r.reason).append('\n')
        }
        tracker.currentFen?.let { sb.append("FEN: ").append(it).append('\n') }
        return sb.toString()
    }

    @Suppress("DEPRECATION")
    private fun dump() {
        val root = rootInActiveWindow
        if (root == null) {
            toast("Tidak ada jendela Chess.com yang bisa dibaca. Buka Chess.com dulu, lalu ketuk DUMP.")
            return
        }
        val pkg = root.packageName?.toString()
        val events = contentEvents.get() - lastDumpEvents
        lastDumpEvents = contentEvents.get()
        val dm = resources.displayMetrics
        worker.execute {
            try {
                val nodes = Snapshot.capture(root)
                root.recycle()
                val report = Analyzer.analyze(nodes, dm.widthPixels, dm.heightPixels, events)
                val module2 = describeModule2(nodes, dm.widthPixels, dm.heightPixels)
                val meta = mapOf<String, Any?>(
                    "time" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                    "package" to pkg,
                    "device" to (Build.MANUFACTURER + " " + Build.MODEL),
                    "sdk" to Build.VERSION.SDK_INT,
                    "screen" to (dm.widthPixels.toString() + "x" + dm.heightPixels)
                )
                val dir = File(filesDir, "dumps").apply { mkdirs() }
                val name = "dump_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"
                File(dir, name).writeText(Snapshot.toJson(meta, report, nodes, module2))
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
                toast("Dump tersimpan: " + nodes.size + " node, verdict " + report.verdict.name + "\n" + module2.lineSequence().firstOrNull { it.startsWith("Langkah") || it.startsWith("Posisi") || it.startsWith("Ditolak") }.orEmpty())
            } catch (e: Exception) {
                toast("Dump gagal: " + e.javaClass.simpleName + " " + (e.message ?: ""))
            }
        }
    }
}
