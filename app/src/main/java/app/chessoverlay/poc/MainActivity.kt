package app.chessoverlay.poc

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var report: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        fun label(t: String, size: Float = 14f) = TextView(this).apply { text = t; textSize = size }
        col.addView(label("Chess Overlay PoC - modul 1", 20f))
        col.addView(label(
            "Menguji apakah papan Chess.com bisa dibaca lewat AccessibilityService.\n\n" +
                "1. Aktifkan service (tombol di bawah).\n" +
                "2. Buka Chess.com pada REPLAY / analisis permainan yang sudah selesai atau mode latihan.\n" +
                "3. Ketuk tombol DUMP yang mengambang di kanan layar. Lakukan di 3 keadaan: posisi awal, setelah beberapa langkah, dan papan dibalik.\n" +
                "4. Kembali ke sini, lihat hasil, lalu Bagikan file hasilnya.\n\n" +
                "Tidak ada screenshot. Pohon layar dibaca hanya saat DUMP diketuk dan disimpan lokal."
        ))
        status = label("")
        col.addView(status)
        col.addView(Button(this).apply {
            text = "Buka pengaturan Aksesibilitas"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        report = label("").apply { setTextIsSelectable(true) }
        col.addView(report)
        col.addView(Button(this).apply {
            text = "Bagikan hasil terakhir (JSON)"
            setOnClickListener { share() }
        })
        setContentView(ScrollView(this).apply {
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = "\nService aktif: " + (if (serviceEnabled()) "ya" else "belum") +
            "\nChess.com terpasang: " + (if (chessInstalled()) "ya" else "tidak ditemukan") + "\n"
        val f = latestDump()
        report.text = if (f == null) "\nBelum ada hasil dump." else "\n" + readReportText(f)
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(this, ChessA11yService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    @Suppress("DEPRECATION")
    private fun chessInstalled(): Boolean = try {
        packageManager.getPackageInfo("com.chess", 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    private fun latestDump(): File? =
        File(filesDir, "dumps").listFiles()?.maxByOrNull { it.lastModified() }

    private fun readReportText(f: File): String = try {
        JSONObject(f.readText()).getJSONObject("report").getString("text")
    } catch (e: Exception) { "Gagal membaca " + f.name + ": " + e.message }

    private fun share() {
        val f = latestDump() ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Bagikan hasil uji"))
    }
}
