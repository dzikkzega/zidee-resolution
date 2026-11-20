Round 1 - Request Changes: Phase 0
=================================

## Pemuatan aturan lokal ECC belum terbukti

**Phase 0 · T-0.2 · Terbuka (menunggu bukti).** `docs/product/TASK_BREAKDOWN.md:26` mensyaratkan integrasi aturan lokal dengan ECC.

**Bukti audit:** Plugin `ecc@ecc` aktif dan `.agents/rules/kotlin-android-ecc.md` tersedia. Keduanya belum membuktikan aturan dimuat otomatis. Ini kekurangan bukti, bukan kesimpulan bahwa plugin rusak.

**Syarat penutupan:** Dari sesi terminal baru di root proyek, tunjukkan daftar sumber instruksi aktif atau log yang mencantumkan berkas aturan tanpa menempelkan isinya secara manual. Catat mekanisme pemuatan dan hasilnya di round berikutnya.

## Dependensi unit test belum lengkap

**Phase 0 · T-0.4 · Status: terbuka.** `docs/product/TASK_BREAKDOWN.md:28-29` mensyaratkan JUnit 4, Coroutines Test, dan Turbine di seluruh modul.

| Modul | Tidak ditemukan pada classpath unit test |
|---|---|
| `:core` | Turbine |
| `:app`, `:design-system` | Coroutines Test dan Turbine |

**Bukti audit:** Laporan Gradle `dependencies` diperiksa untuk `testRuntimeClasspath` di `:core` dan `debugUnitTestRuntimeClasspath` di dua modul Android. `ktlintCheck detekt test` lulus; hasil tersebut tidak membuktikan pustaka yang belum digunakan tersedia.

**Syarat penutupan:** Tunjukkan ketiga pustaka tersedia pada classpath unit test ketujuh modul, atau sertakan revisi persyaratan yang disetujui beserta pengecualian modul. Jalankan ulang `./gradlew ktlintCheck detekt test` dan lampirkan hasilnya.
