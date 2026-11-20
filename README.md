# Zidee Ress — Offline Media Resolution Optimizer for WhatsApp & Instagram

**Zidee Ress** adalah aplikasi Android native **100% Full Offline** yang dirancang untuk mengoptimasi foto dan video sebelum dikirim atau diunggah ke **WhatsApp Story, WhatsApp Chat, Instagram Story, Instagram Post/Reels, dan Instagram DM** agar tidak buram, tidak patah-patah, dan tidak pecah akibat kompresi agresif platform.

---

## 🌟 Masalah yang Diselesaikan

Meskipun fitur **HD** di WhatsApp dan *"Upload at highest quality"* di Instagram sudah diaktifkan, media dari kamera HP modern tetap sering pecah atau buram karena:
1. **Downscaling Kasar oleh Aplikasi:** Kamera HP menghasilkan foto `4000×3000+` dan video `4K`, sedangkan kanvas Story hanya `1080×1920`. WA dan IG mengecilkan resolusi dengan algoritma *bilinear* cepat yang mengorbankan ketajaman.
2. **Jebakan 60 FPS:** Bitrate maksimal WA/IG dibagi dua untuk setiap *frame* pada video 60fps, menyebabkan *macroblocking* (kotak-kotak) saat ada gerakan.
3. **Kegagalan Warna HDR / Display P3:** Video 10-bit HDR atau foto P3 sering terlihat pucat atau *overexposed* setelah diunggah.
4. **Efek "Screenshot Canvas":** Mencubit (*pinch-to-zoom*) foto berasio `4:3` di dalam WA/IG memicu render ulang kanvas beresolusi rendah.

**Solusi Zidee Ress:** Melakukan *Sweet-Spot Pre-Processing* secara lokal di perangkat menggunakan algoritma **Lanczos-3 Downsampling**, **Smart Unsharp Mask (Anti-Blur Compensation)**, **Normalisasi Warna sRGB / HDR-to-SDR**, **Kunci 30 FPS & Bitrate Presisi**, serta **Komposisi Kanvas 9:16 (Blur BG / Interactive Crop)**, lalu menyimpannya secara permanen di album Galeri **`ZideeResolution`**.

---

## 🧭 Peta Dokumentasi Proyek

Seluruh spesifikasi produk, arsitektur, desain, dan pengujian tersedia secara lengkap di repositori ini:

| Domain | Dokumen | Deskripsi |
| :--- | :--- | :--- |
| **AI & Governance** | [AGENTS.md](AGENTS.md) | Aturan kerja wajib bagi AI Agent & standar Clean Architecture. |
| | [CLAUDE.md](CLAUDE.md) | Ringkasan konteks cepat, perintah build/test, dan pengingat kritis. |
| **Design System** | [DESIGN.md](DESIGN.md) | Spesifikasi visual *Pro Darkroom Optics*, palet Amber Gold, *Split Slider*, & Ikon Ber-badge Versi. |
| **Product** | [docs/product/PRD.md](docs/product/PRD.md) | Product Requirements Document & matriks *sweet-spot* 5 platform. |
| | [docs/product/USER_STORIES.md](docs/product/USER_STORIES.md) | User Stories & Acceptance Criteria (Gherkin). |
| | [docs/product/TASK_BREAKDOWN.md](docs/product/TASK_BREAKDOWN.md) | Rincian tahapan pengerjaan teknis (*Epics & Atomic Tasks*). |
| **Technical** | [docs/technical/SYSTEM_ARCHITECTURE.md](docs/technical/SYSTEM_ARCHITECTURE.md) | Arsitektur sistem, aliran data UDF, dan *Foreground Service*. |
| | [docs/technical/PROJECT_STRUCTURE.md](docs/technical/PROJECT_STRUCTURE.md) | Struktur modul Gradle Kotlin DSL & organisasi *package*. |
| | [docs/technical/MEDIA_ENGINE_SPEC.md](docs/technical/MEDIA_ENGINE_SPEC.md) | Spesifikasi algoritma Foto (Lanczos/Unsharp) & Video (FFmpeg filtergraph). |
| | [docs/technical/DATABASE_AND_STORAGE.md](docs/technical/DATABASE_AND_STORAGE.md) | Skema `Room`, `DataStore`, dan penyimpanan permanen `MediaStore` (`ZideeResolution`). |
| **Engineering & QA**| [docs/engineering/TESTING_STRATEGY.md](docs/engineering/TESTING_STRATEGY.md) | Strategi Unit Test, Media Verification Test, UI Test, dan E2E Test. |
| | [docs/engineering/DEVELOPMENT_GUIDE.md](docs/engineering/DEVELOPMENT_GUIDE.md) | Panduan konfigurasi environment, SDK/NDK, dan alur Git. |
| | [docs/engineering/CODE_REVIEW_GUIDE.md](docs/engineering/CODE_REVIEW_GUIDE.md) | Daftar periksa *code review* performa, memori, dan UI. |
| **Security** | [docs/security/SECURITY_GUIDE.md](docs/security/SECURITY_GUIDE.md) | Keamanan *offline processing*, sanitasi URI, dan aturan R8/ProGuard. |
| **Decisions (ADR)** | [docs/adr/](docs/adr/) | Catatan keputusan arsitektur utama proyek. |

---

## 🏗️ Arsitektur Modul Ringkas

```text
zidee-resolution/
├── app/                  # Entrypoint APK, DI wiring, QuickShareActivity, MainActivity, Icon & Manifest
├── core/                 # Utilitas bersama, Result/AppError, DispatcherProvider, Logger
├── domain/               # Pure Kotlin: Models (Preset, AspectMode, AdvancedConfig), UseCases, Repository Interfaces
├── data/                 # Implementasi Repository, Room DB (History), DataStore, MediaStore ("ZideeResolution")
├── media-engine/         # Pipeline Foto (C++/OpenCV Lanczos + Unsharp) & Video (FFmpeg Filtergraph + Auto-Split)
├── design-system/        # Tema Pro Darkroom (Amber Gold/White), Komponen UI, Interactive Split-Slider
├── presentation/         # Layar & ViewModel (Dashboard, StudioEditor, QuickShareBottomSheet, History)
└── docs/                 # Dokumentasi Produk, Teknis, Desain, Engineering, Security, dan ADR
```
