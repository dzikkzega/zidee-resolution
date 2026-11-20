Round 1 - Request Changes: Phase 5
=================================

**Belum lulus.** Acuan: `docs/product/TASK_BREAKDOWN.md:94-102`.

## T-5.2 — Pan crop pada zoom 1× tidak tercermin di pratinjau

Pilih foto lanskap 4000×3000, `CROP_9_16`, zoom 1×. Geser `panX` dari 0 ke 1. `StudioSplitPreviewCanvas.kt:172-191` menghitung perpindahan dari `(zoom - 1)`, sehingga kedua posisi menampilkan crop tengah yang sama. Sebaliknya `CalculateOptimalSpecUseCase.kt:174` menerapkan `panX` pada lebar sumber yang terpotong: hasil ekspor memakai area berbeda. Pada zoom lain, nilai translasi Compose juga memakai dp numerik sebagai piksel. Ini temuan inspeksi, bukan hasil uji UI perangkat.

**Syarat penutupan:** gunakan geometri crop ekspor sebagai acuan pratinjau. Dengan gambar bertanda kiri/kanan, verifikasi posisi kiri, tengah, dan kanan pada zoom 1× dan >1×; bandingkan preview dengan ekspor di dua densitas layar. `StudioEditorViewModelTest.kt:141` menguji event crop pada zoom 2×, bukan piksel preview.

## T-5.2 — Sisi “Asli” mengubah warna sumber

`StudioSplitPreviewCanvas.kt:93-108,143-147,178-182` memakai thumbnail yang sama di kedua sisi dan menurunkan saturasi sisi Asli ke 0,76. Akibatnya sumber tampak lebih pucat daripada aslinya meskipun tidak ada koreksi warna. Pertahankan warna input pada sisi Asli; periksa dengan gambar acuan berwarna dan pisahkan simulasi dari hasil nyata dalam pelabelan.

## Cakupan verifikasi

`:presentation:testDebugUnitTest --rerun :presentation:lintDebug :presentation:ktlintCheck :presentation:detekt :app:assembleDebug` sukses. 25 tes: Dashboard 7, Studio 6, Quick Share 4, mapper 3, router/extractor 5. Dashboard memiliki pengujian picker, pintasan, album, re-share, dan kegagalan riwayat. Jumlah tes bukan bukti semua transisi DoD: sertakan bukti hasil batch parsial (satu berhasil, satu gagal) saat re-review. Tata letak ponsel/tablet dan kesesuaian render aktual tidak diuji dalam round ini. Tidak ada kode aplikasi diubah.
