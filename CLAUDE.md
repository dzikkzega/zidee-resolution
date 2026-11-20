# CLAUDE.md — Zidee Ress Quick Context & Commands

Panduan ringkas untuk AI Agent dan engineer yang bekerja pada proyek **Zidee Ress** (Aplikasi Android Optimasi Resolusi Foto & Video Offline untuk WhatsApp & Instagram).

---

## 1. Ringkasan Proyek

- **Nama App:** Zidee Ress (`com.zidee.ress`)
- **Platform:** Android Native (Min SDK 26 / Android 8.0, Target & Compile SDK 35 / Android 15)
- **Tech Stack Utama:**
  - **UI & Design:** Kotlin 2.x, Jetpack Compose, Material 3 Custom Theme (*Pro Darkroom* + *Amber Gold* `#F59E0B` & *Crisp White* `#F8FAFC`)
  - **Architecture:** Clean Architecture (`domain`, `data`, `presentation`, `design-system`, `media-engine`, `core`) + Hilt/Koin DI + Coroutines & Flow
  - **Video Engine:** FFmpeg Native Pipeline (`libx264` CPU High-Quality & `h264_mediacodec` GPU Fast Mode, Lanczos scaling, Unsharp Mask, HDR-to-SDR tone-mapping, Segment Auto-Split)
  - **Photo Engine:** Native C++ / OpenCV Image Pipeline (sRGB Color Normalizer, Lanczos-3 Resampling, Gaussian Blur BG 9:16 Compositor, Smart Unsharp Mask, 94% Optimized JPEG Encoder)
  - **Storage:** Android `MediaStore` (`Pictures/ZideeResolution` & `Movies/ZideeResolution`), `Room` (Optimization History), `DataStore` (User Preferences)

---

## 2. Perintah Gradle Utama

```bash
# Cek kompilasi seluruh modul (Debug)
./gradlew assembleDebug

# Jalankan seluruh Unit Test (Domain, Data, ViewModel, Preset Calculator)
./gradlew testDebugUnitTest

# Jalankan pemeriksaan kode statis & format
./gradlew detekt ktlintCheck

# Jalankan Instrumented Test di perangkat/emulator Android
./gradlew connectedDebugAndroidTest
```

---

## 3. Peta Dokumentasi Utama

Seluruh keputusan produk, desain, dan teknis terdokumentasi di folder `docs/`:

- **Aturan Kerja Agent:** [AGENTS.md](AGENTS.md)
- **Aturan Lokal ECC:** [.agents/rules/kotlin-android-ecc.md](.agents/rules/kotlin-android-ecc.md)
- **Sistem Desain & UI/UX:** [DESIGN.md](DESIGN.md) & [docs/design/DESIGN.md](docs/design/DESIGN.md)
- **Spesifikasi Produk (PRD):** [docs/product/PRD.md](docs/product/PRD.md)
- **User Stories & Kriteria Uji:** [docs/product/USER_STORIES.md](docs/product/USER_STORIES.md)
- **Task Breakdown & Roadmap:** [docs/product/TASK_BREAKDOWN.md](docs/product/TASK_BREAKDOWN.md)
- **Arsitektur Sistem:** [docs/technical/SYSTEM_ARCHITECTURE.md](docs/technical/SYSTEM_ARCHITECTURE.md)
- **Struktur Proyek & Modul:** [docs/technical/PROJECT_STRUCTURE.md](docs/technical/PROJECT_STRUCTURE.md)
- **Spesifikasi Mesin Media (Foto & Video):** [docs/technical/MEDIA_ENGINE_SPEC.md](docs/technical/MEDIA_ENGINE_SPEC.md)
- **Database & Scoped Storage:** [docs/technical/DATABASE_AND_STORAGE.md](docs/technical/DATABASE_AND_STORAGE.md)
- **Strategi Testing:** [docs/engineering/TESTING_STRATEGY.md](docs/engineering/TESTING_STRATEGY.md)
- **Panduan Developer:** [docs/engineering/DEVELOPMENT_GUIDE.md](docs/engineering/DEVELOPMENT_GUIDE.md)
- **Panduan Code Review:** [docs/engineering/CODE_REVIEW_GUIDE.md](docs/engineering/CODE_REVIEW_GUIDE.md)
- **Panduan Keamanan & Privasi:** [docs/security/SECURITY_GUIDE.md](docs/security/SECURITY_GUIDE.md)

---

## 4. Pengingat Kritis Saat Implementasi

1. **Jangan biarkan WA/IG melakukan resize kanvas:** Untuk mode `BLUR_BG_9_16` dan `CROP_9_16`, dimensi piksel keluar wajib mutlak `1080x1920` (genap/modulo-2 aman untuk encoder H.264).
2. **Simpan permanen di setiap alur:** Baik pengguna menekan *"Simpan Saja"* maupun *"Simpan & Kirim ke WA/IG"* melalui *Quick Share Sheet*, file hasil wajib masuk ke album Galeri `ZideeResolution` sebelum Intent dikirim.
3. **Gunakan Split Slider Kiri-Kanan:** Komponen pembanding *Before vs After* pada Studio Editor menggunakan garis pemisah geser horizontal (*Split Slider*), bukan hanya *tap-and-hold*.
4. **Badge Versi pada Ikon:** Ikon peluncur aplikasi (*Launcher Icon*) wajib memiliki elemen visual lensa/prisma HD persuasif dengan *badge* versi aplikasi (contoh: `v1.0`) di bagian bawah ikon.

---

## 5. Aturan Git, Atribusi & Larangan Jejak Claude (WAJIB)

1. **Dilarang menambahkan `Co-Authored-By` atau trailer Git apa pun** yang menyebut Claude, Anthropic, AI, atau alamat seperti `noreply@anthropic.com` pada pesan commit (`git commit`), merge commit, maupun tag.
2. **Dilarang mencantumkan Claude/Anthropic/AI sebagai contributor, author, committer, atau reviewer** di riwayat Git (`--author`, `GIT_AUTHOR_*`, `GIT_COMMITTER_*`), `CONTRIBUTORS`, `AUTHORS`, `README`, `package`/`build` metadata, maupun daftar kontributor repositori.
3. **Dilarang menambahkan footer, badge, atau watermark AI** pada deskripsi Pull Request, Issue, komentar kode, maupun dokumentasi (contoh yang dilarang keras: *"Generated with Claude Code"*, *"Assisted by Claude"*, *"AI-generated"*, atau sejenisnya).
4. **Gunakan murni identitas Git lokal milik developer** saat membuat commit atau PR sehingga seluruh riwayat repositori dan kode bersih tanpa jejak penggunaan engine Claude.

---

## 6. Aturan Lokal Workspace ECC (Auto-Loaded)

@.agents/rules/kotlin-android-ecc.md
