Round 1 - Request Changes: Phase 2
=================================

**Putusan:** Belum lulus. Pemeriksaan kode dan build lulus; ada satu perbedaan ukuran terhadap Figcraft dan bukti penerimaan UI belum lengkap. Acuan: `docs/product/TASK_BREAKDOWN.md:49-60`.

## Perbandingan desain dan implementasi

Referensi dibaca langsung, tanpa mengubah desain, melalui `figcraft-cli mcp --attach 9999` / `inspect_document`: dokumen `zidee-resolution`, halaman `Zidee Ress · Pro Darkroom UI/UX System`. Perbandingan berikut memakai properti node dan kode, bukan tangkapan layar Android.

| Tugas | Bukti | Hasil |
|---|---|---|
| T-2.1 Tema | Brand frame #11: Amber `#F59E0B` (#41), Crisp White `#F8FAFC` (#44), anotasi radius dan `tnum` (#51–52). Tema dan tes token tersedia; tes token tidak mengukur kontras teks terhadap latar aktual. | Terverifikasi sebagian |
| T-2.2 Ikon | Referensi memuat piksel kiri (#13 dst.) dan badge amber `v1.0` (#27). `app/src/main/res/drawable/ic_launcher_foreground.xml:8-99` memuat blok piksel, detail HD+ kanan, garis split, dan badge bawah. | Unsur desain ada; keterbacaan pada mask launcher belum diperiksa secara visual |
| T-2.3 Kontrol | Selector, segmented control, split slider, dan panel advanced memiliki Compose Preview. Studio frame #119 memiliki selector lima target (#149–158), kontrol rasio (#159–164), dan advanced (#165–167). Split slider mendukung drag horizontal dan semantics (`BeforeAfterSplitSlider.kt:115-126,162-171`). | Implementasi tersedia; temuan di bawah tetap terbuka |

## 1. Ukuran visual pegangan split slider berbeda

Pada frame Studio 360×800, node #142 adalah garis putih selebar 2; node #143 adalah pegangan amber **40×40** dengan anotasi hit area 48dp. `design-system/src/main/java/com/zidee/ress/designsystem/component/BeforeAfterSplitSlider.kt:225-250` memakai garis putih 2dp dan pegangan amber **36dp** di dalam kotak 48dp. Selisih ini mengenai gambar pegangan, bukan bukti bahwa area sentuh terlalu kecil.

**Syarat penutupan:** Cocokkan pegangan dengan referensi 40 pada skala desain, atau dokumentasikan deviasi yang disetujui. Pertahankan target sentuh ≥48dp. Bandingkan tangkapan layar Compose pada lebar 360dp dengan frame #119 pada skala yang sama.

## 2. Bukti penerimaan UI belum lengkap

`adb devices -l` tidak menampilkan perangkat; tidak ada emulator/AVD lokal. Tidak dilakukan perbandingan screenshot aplikasi, pengukuran target sentuh aktual, atau pengukuran frame saat drag. Konstanta 48dp dan keberadaan `@Preview` bukan pengganti pemeriksaan tersebut. Ini kekurangan bukti, bukan klaim bahwa kontras gagal atau slider tersendat.

**Syarat penutupan:** Lampirkan hasil render keempat kontrol dan ikon (termasuk badge di bawah mask launcher); rasio kontras pasangan teks/latar dan hasil ukur hit area ≥48dp pada kontrol interaktif. Untuk klaim 120Hz, sertakan profil frame waktu drag pada perangkat yang mendukung 120Hz, beserta mode refresh aktif. Bedakan hasil ukur dari inspeksi kode.

## Hasil pemeriksaan lokal

`./gradlew --offline :design-system:testDebugUnitTest --rerun :design-system:lintDebug :design-system:detekt :design-system:ktlintCheck :app:assembleDebug` — **BUILD SUCCESSFUL**. Unit test `:design-system`: **8/8 lulus**, tanpa skip. Sebagian task Gradle up-to-date. Tidak ada kode aplikasi atau desain Figcraft yang diubah.
