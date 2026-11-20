# Zidee Ress Documentation Hub

Selamat datang di pusat dokumentasi **Zidee Ress** (`com.zidee.ress`). Seluruh spesifikasi bisnis/produk, desain visual, arsitektur teknis, standar pengujian, dan keamanan dikelola di dalam direktori ini.

---

## 📚 Daftar Isi Dokumentasi

| Kategori | Dokumen | Tujuan & Cakupan |
| :--- | :--- | :--- |
| **Governance & AI** | [../AGENTS.md](../AGENTS.md) | Kontrak kerja AI Agent, batas modul Clean Architecture, dan aturan Kotlin/Compose. |
| | [../CLAUDE.md](../CLAUDE.md) | Referensi cepat perintah Gradle, arsitektur, dan pengingat implementasi. |
| **Product** | [product/PRD.md](product/PRD.md) | *Product Requirements Document*: spesifikasi fitur, matriks 5 target platform, dan aturan folder `ZideeResolution`. |
| | [product/USER_STORIES.md](product/USER_STORIES.md) | Skenario pengguna dan *Acceptance Criteria* berbasis Gherkin (`Given/When/Then`). |
| | [product/TASK_BREAKDOWN.md](product/TASK_BREAKDOWN.md) | Rincian tahapan pengerjaan proyek (*Epics*, *Milestones*, *Atomic Tasks*, & *DoD*). |
| **Design System** | [../DESIGN.md](../DESIGN.md) | Ringkasan utama *Design System* di root proyek. |
| | [design/DESIGN.md](design/DESIGN.md) | Spesifikasi mendalam *Pro Darkroom Optics*, token warna Amber Gold/White, *Split-Slider*, dan *Icon Badge*. |
| **Technical** | [technical/SYSTEM_ARCHITECTURE.md](technical/SYSTEM_ARCHITECTURE.md) | Diagram arsitektur, aliran data *Unidirectional Data Flow (UDF)*, dan layanan latar belakang. |
| | [technical/PROJECT_STRUCTURE.md](technical/PROJECT_STRUCTURE.md) | Pohon direktori multi-modul Gradle Kotlin DSL dan tanggung jawab setiap paket. |
| | [technical/MEDIA_ENGINE_SPEC.md](technical/MEDIA_ENGINE_SPEC.md) | Rumus dan parameter pipeline Foto (Lanczos-3 + Unsharp Mask) & Video (FFmpeg Filtergraph). |
| | [technical/DATABASE_AND_STORAGE.md](technical/DATABASE_AND_STORAGE.md) | Skema `Room` Database, `DataStore`, dan kontrak penyimpanan permanen `MediaStore` (`ZideeResolution`). |
| **Engineering & QA**| [engineering/TESTING_STRATEGY.md](engineering/TESTING_STRATEGY.md) | Dokumentasi pengujian lengkap: Unit Test, Pipeline Verification, Compose UI Test, & E2E. |
| | [engineering/DEVELOPMENT_GUIDE.md](engineering/DEVELOPMENT_GUIDE.md) | Panduan *setup* Android Studio, SDK/NDK, konvensi kode, dan alur Git. |
| | [engineering/CODE_REVIEW_GUIDE.md](engineering/CODE_REVIEW_GUIDE.md) | Standar *code review*, pencegahan *memory leak* Bitmap, dan stabilitas Compose. |
| **Security** | [security/SECURITY_GUIDE.md](security/SECURITY_GUIDE.md) | Jaminan privasi 100% *offline*, validasi URI eksternal, keamanan argumen FFmpeg, dan R8. |
| **ADR** | [adr/0001-native-kotlin-compose-architecture.md](adr/0001-native-kotlin-compose-architecture.md) | Keputusan memilih Native Kotlin + Jetpack Compose + Clean Architecture. |
| | [adr/0002-hybrid-ffmpeg-and-native-image-pipeline.md](adr/0002-hybrid-ffmpeg-and-native-image-pipeline.md) | Keputusan memilih pipeline FFmpeg (`libx264` + `MediaCodec`) & Native C++ Image Engine. |
| | [adr/0003-scoped-storage-mediastore-zideeresolution.md](adr/0003-scoped-storage-mediastore-zideeresolution.md) | Keputusan penyimpanan permanen wajib di album Galeri `ZideeResolution`. |
