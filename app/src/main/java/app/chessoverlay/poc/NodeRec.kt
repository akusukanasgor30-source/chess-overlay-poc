package app.chessoverlay.poc

/**
 * Satu node accessibility yang sudah "dibekukan" menjadi data biasa (tanpa tipe Android),
 * supaya Analyzer bisa diuji di JVM. id == indeks di daftar, parent < id (urutan preorder).
 */
data class NodeRec(
    val id: Int,
    val parent: Int,
    val depth: Int,
    val cls: String?,
    val viewId: String?,
    val text: String?,
    val desc: String?,
    val state: String?,
    val hint: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean,
    val important: Boolean,
    val childCount: Int,
    val row: Int = -1,
    val col: Int = -1
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** Semua teks yang mungkin membawa informasi papan (text, contentDescription, stateDescription, hint). */
    val labels: List<String>
        get() = listOf(text, desc, state, hint)
            .filter { !it.isNullOrBlank() }
            .map { it!!.trim() }
            .distinct()
}
