Round 1 - Request Changes: Phase 3
=================================

**Putusan: belum lulus.** Acuan: `docs/product/TASK_BREAKDOWN.md:64-79`. Build dan unit test lulus. Ada risiko memori yang dapat ditelusuri pada jalur foto; hasil pipeline Android belum terverifikasi.

## T-3.1 — Crop kecil masih dapat mendekode seluruh foto besar

`PhotoProcessingPipeline.kt:113-125` menentukan `inSampleSize` dari ukuran wilayah crop, tetapi `ColorSpaceNormalizer.kt:37-49,61-62` mendekode gambar penuh dan membuat `IntArray` untuk semua piksel sebelum crop. Contoh perhitungan: untuk foto 12000×9000 dengan crop 2160×3840 ke 1080×1920, fungsi memilih `sample=1`. Array 108 juta piksel saja membutuhkan sekitar 412 MiB, belum termasuk bitmap yang masih hidup. Ini risiko OOM berdasarkan kode, bukan crash yang diamati pada perangkat. Jalur EXIF, sRGB, Lanczos, komposisi, unsharp, dan JPEG tersedia (`PhotoProcessingPipeline.kt:48-87`).

**Syarat penutupan:** Batasi alokasi terhadap ukuran input penuh saat crop, misalnya melalui decode wilayah yang memperhitungkan orientasi EXIF. Uji foto besar dengan crop di perangkat sasaran; catat peak heap, orientasi, ukuran JPEG, dan profil sRGB. Pastikan area crop tidak berubah akibat optimasi memori.

## T-3.2 dan T-3.3 — Bukti penerimaan di Android belum tersedia

Builder mencakup ketiga mode kanvas, CPU/GPU, HDR-ke-SDR, dan segmentasi (`FfmpegFiltergraphBuilder.kt:40-88,106-137,152-165,182-184`). Runner menggunakan FFmpegKit dan statistik progres; tes `VideoProcessingPipelineTest.kt:38,98` mengganti executor dengan fake. Foreground service dan pembaruan notifikasi terhubung melalui `OfflineMediaOptimizerEngine.kt:41-58`. Tidak ada perangkat pada `adb devices -l`; keluaran video nyata, Cancel lewat notifikasi, dan kondisi `cacheDir` sesudah pekerjaan belum diuji dalam audit ini. Bukan bukti bahwa fitur tersebut rusak.

**Syarat penutupan:** Pada Android, proses foto P3 ber-EXIF dan video HDR; periksa metadata keluaran aktual (dimensi, FPS, ruang warna). Uji kedua encoder pada perangkat yang mendukungnya, termasuk split 30/60 detik. Catat progres dan pembatalan lewat notifikasi saat aplikasi di latar. Bandingkan staging sebelum pekerjaan dan setelah alur sukses selesai disimpan, gagal, serta dibatalkan.

## Verifikasi lokal

`./gradlew --offline :media-engine:testDebugUnitTest --rerun :media-engine:lintDebug :media-engine:detekt :media-engine:ktlintCheck :app:assembleDebug` — **BUILD SUCCESSFUL**. Tes JVM `:media-engine`: **32/32**, tanpa gagal, error, atau skip. Sebagian task Gradle up-to-date. Tidak ada kode aplikasi yang diubah.
