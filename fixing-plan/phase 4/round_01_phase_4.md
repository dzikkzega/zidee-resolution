Round 1 - Request Changes: Phase 4
=================================

**Belum lulus.** Acuan: `docs/product/TASK_BREAKDOWN.md:83-90`. Pemeriksaan lokal `:data:testDebugUnitTest --rerun :data:connectedDebugAndroidTest :data:lintDebug :data:ktlintCheck :data:detekt :app:assembleDebug` sukses. 11 tes JVM dalam empat suite. Struktur Room (database, DAO, entity) tersedia; DataStore membaca/menulis platform, rasio, dan encoder. Ini bukan bukti file terlihat di Galeri.

## T-4.1 — Publikasi MediaStore bisa dilaporkan sukses tanpa baris terpublikasi

`ZideeMediaStoreSaver.kt:91-97` mengabaikan jumlah baris hasil `resolver.update` untuk `IS_PENDING=0`. Jika baris hilang setelah penulisan, update dapat mengembalikan 0 tanpa exception. Baris 230-240 tetap mengembalikan URI sukses dan menghapus staging. Temuan inspeksi, bukan kegagalan yang direproduksi pada perangkat.

**Syarat penutupan:** sukses hanya jika satu baris terpublikasi. Uji regresi update 0: hasil gagal, tanpa riwayat sukses atau share intent.

## T-4.1 — Izin penyimpanan pada Android 8–9

Jalur legacy menulis ke direktori publik (`ZideeMediaStoreSaver.kt:67-74,159-170`). Manifest mendeklarasikan `WRITE_EXTERNAL_STORAGE`, tetapi tidak ditemukan permintaan runtime untuk izin itu. `MainActivity.kt:315-330` hanya meminta izin notifikasi.

**Syarat penutupan:** buktikan foto/video tersimpan dari instalasi baru API 26–28; penolakan izin harus ditangani tanpa melaporkan sukses.

## DoD Galeri — belum terverifikasi

`adb devices -l` kosong; tidak ditemukan tes dalam `data/src/androidTest`. Task connected selesai bukan bukti penerimaan perangkat. Untuk menutup round, uji foto dan video dari Studio dan Quick Share. Catat URI, direktori `Pictures/ZideeResolution` atau `Movies/ZideeResolution`, `IS_PENDING=0` pada API 29+, serta kemunculan di album Galeri. Pada alur berbagi, buktikan penyimpanan mendahului intent. Periksa riwayat Room dan preferensi setelah aplikasi dibuka ulang.
