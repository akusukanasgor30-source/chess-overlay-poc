# Chess Overlay PoC - Modul 1 (uji AccessibilityService) + Modul 2 (posisi dan langkah sah)

Tujuan: mengetahui APAKAH papan Chess.com Android bisa dibaca dari accessibility tree (petak, bidak, daftar
langkah, koordinat layar petak). Tidak ada screenshot, tidak ada Stockfish, tidak ada overlay panah di modul ini.

## Cara kerja
- Service hanya aktif untuk paket `com.chess`. Event konten hanya dihitung; pohon node dibaca SEKALI saat tombol
  DUMP (mengambang di kanan layar) diketuk.
- `Analyzer` (logika murni, ada tes JVM) menilai isi dump: verdict `POSITION_READABLE`, `POSITION_PARTIAL`,
  `MOVES_ONLY`, `NOT_READABLE` atau `NO_DATA`, plus kandidat papan, kosakata label asli, dan grid layar.
- Hasil: JSON di penyimpanan privat aplikasi (3 terakhir). Tombol "Bagikan" mengirim JSON terakhir.

## Build
- Android Studio: Open folder ini, Sync, Run. (JDK 17)
- Tanpa PC: unggah folder ini ke repo GitHub, jalankan workflow "Build debug APK" (Actions), unduh artifact `app-debug.apk`.

## Pasang dan uji
1. Pasang APK. Android 13+ untuk APK di luar Play Store: Info aplikasi > menu titik tiga > "Izinkan setelan terbatas",
   baru service bisa diaktifkan.
2. Buka app > "Buka pengaturan Aksesibilitas" > aktifkan "Chess Overlay PoC".
3. Buka Chess.com pada replay/analisis permainan yang SUDAH SELESAI atau mode latihan. Ketuk DUMP pada:
   (a) posisi awal, (b) setelah beberapa langkah maju, (c) papan dibalik, (d) layar daftar langkah jika ada.
4. Buka app lagi, baca hasil, ketuk "Bagikan", kirim JSON-nya ke Claude.
Dump berisi semua teks di layar (misalnya nama pemain). Periksa dulu sebelum dibagikan.

## Dari ekstensi v3_1 ke sini
`parsePieceCode` / `parseSquareClass` -> `Analyzer.parsePiece` / `parseSquare`; `detectFlipped` -> `Analyzer.fitGrid`.
`matrixToFEN` / `validateFEN` (dan nanti `classify.js` dari v5) dipindahkan di modul berikutnya.

## Status verifikasi
Belum dikompilasi dan belum dijalankan di perangkat/emulator (lingkungan penulis tidak punya Android SDK).
Logika `Analyzer` sudah diperiksa lewat tiruan Python dengan skenario yang sama dengan `AnalyzerTest.kt`;
fixture tes sintetis, bukan format asli Chess.com.

## Modul 2 (baru): posisi, FEN, dan langkah sah
- `DumpParser`: dump -> `BoardSnapshot` (placement FEN, kotak papan di layar, daftar langkah yang terlihat,
  giliran dari daftar langkah, mode latihan = ada tombol "Petunjuk" dan "Urung", permainan berjalan = ada "Menyerah").
- `PositionTracker` (pustaka chesslib): perubahan antar dump hanya diterima bila ada 1-2 langkah SAH yang
  menghasilkan posisi baru persis. Selisih yang tidak bisa dijelaskan => sinkronisasi ulang, tidak menebak.
  Posisi tidak sah (mis. raja lawan sedang di-skak padahal giliran Anda) ditolak. Mode yang tidak dikenali
  sebagai latihan ditolak.
- Setiap ketukan DUMP kini menambahkan blok "MODUL 2" di laporan: placement, giliran, langkah sah yang terdeteksi
  dari dump sebelumnya, dan FEN. Ketuk DUMP setelah tiap langkah untuk menguji.
- Tes: `Module2Test` memakai dump NYATA (`app/src/test/resources`) plus kasus rokade, promosi, en passant, loncatan.
  Di CI, tes berjalan tanpa memblokir pembuatan APK.
- Batas yang diketahui: hak rokade ditebak dari posisi raja/benteng saat sinkronisasi pertama; en passant pada
  langkah pertama setelah sinkronisasi tidak diketahui; daftar langkah di layar hanya jendela (langkah awal
  tidak ada di pohon) dan menulis gajah sebagai huruf kecil ("be5"), sehingga tidak dipakai sebagai sumber
  langkah, hanya sebagai petunjuk giliran.
