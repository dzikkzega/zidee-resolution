Round 1 - Request Changes: Phase 6
=================================

**Belum lulus.** Acuan: `docs/product/TASK_BREAKDOWN.md:106-113`.

## T-6.3 — ktlint seluruh proyek gagal

`:app:ktlintAndroidTestSourceSetCheck` mendeteksi baris terlalu panjang dan pemenggalan argumen, antara lain `Phase3PhotoAcceptanceTest.kt:164` serta `Phase3VideoAndServiceAcceptanceTest.kt:73,82` di `app/src/androidTest/java/com/zidee/ress/`. Meski berasal dari tes Phase 3, kegagalan masuk pemeriksaan final Phase 6. Laporan: `app/build/reports/ktlint/ktlintAndroidTestSourceSetCheck/ktlintAndroidTestSourceSetCheck.txt`.

**Syarat penutupan:** perbaiki seluruh temuan laporan, lalu sertakan hasil `./gradlew ktlintCheck detekt lintDebug test assembleDebug assembleRelease` tanpa galat.

## T-6.3 — Validasi MIME belum ketat

`IncomingShareExtractor.kt:35-40` memeriksa prefiks dan dua wildcard persis, tetapi tidak mewajibkan subtipe. Misalnya `image/` akan diterima walau bukan MIME media lengkap. Syarat penutupan: tambahkan tes subtipe kosong/tidak valid dan pastikan ditolak tanpa memblokir tipe foto/video sah.

## Bagian terverifikasi

- T-6.1/T-6.2 (inspeksi): activity bertema bottom sheet, ekstraksi SEND/SEND_MULTIPLE, handoff URI, tiga paket tujuan, grant baca dan fallback chooser tersedia. Tidak melakukan pengiriman ke aplikasi pihak ketiga atau E2E dari Galeri dalam round ini.
- Pengujian terpisah: 159/159 tes unit JVM/debug, tanpa error atau skip. `detekt`, `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleRelease` sukses.
- `apksigner verify --print-certs` sukses untuk kedua APK. SHA-1 identik dan sesuai roadmap: `b085af4ad6820c4b2ce8636c297752c6cf8d83f0`. Sertifikat bertuliskan `CN=Android Debug`; kecocokan untuk QA lokal bukan persetujuan distribusi produksi.
