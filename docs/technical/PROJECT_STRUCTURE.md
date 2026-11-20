# Project Structure — Zidee Ress

Dokumen ini mendefinisikan struktur direktori multi-modul Gradle Kotlin DSL, pembagian tanggung jawab setiap modul, serta aturan penamaan kode untuk **Zidee Ress** (`com.zidee.ress`).

---

## 1. Pohon Direktori Proyek

```text
zidee-resolution/
├── .agents/
│   └── rules/
│       └── kotlin-android-ecc.md        # Aturan lokal AI Agent (Clean Architecture, Kotlin, Compose)
├── app/                                 # Modul Aplikasi Utama (APK Entrypoint & DI Wiring)
│   ├── src/main/
│   │   ├── java/com/zidee/ress/
│   │   │   ├── ZideeApplication.kt      # Inisialisasi DI & Notification Channel
│   │   │   ├── MainActivity.kt          # Host untuk Dashboard & Studio Editor
│   │   │   ├── QuickShareActivity.kt    # Translucent Bottom Sheet Activity (ACTION_SEND)
│   │   │   └── di/                      # Wiring modul Hilt / Koin
│   │   ├── res/
│   │   │   ├── mipmap-anydpi-v26/       # Adaptive Icon "Zidee Ress" + Version Badge
│   │   │   ├── drawable/                # Lapisan vektor lensa & badge versi
│   │   │   └── values/                  # Strings, Themes (Pro Darkroom & BottomSheet)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── core/                                # Modul Utilitas Dasar & Kontrak Lintas-Layer
│   ├── src/main/java/com/zidee/ress/core/
│   │   ├── result/                      # AppResult<T> & AppError hierarchy
│   │   ├── coroutines/                  # DispatcherProvider
│   │   └── format/                      # Formatter ukuran MB, durasi, & resolusi
│   └── build.gradle.kts
├── domain/                              # Modul Domain (100% Pure Kotlin — Tanpa Android SDK)
│   ├── src/main/kotlin/com/zidee/ress/domain/
│   │   ├── model/
│   │   │   ├── TargetPlatform.kt        # WA_STORY, WA_CHAT, IG_STORY, IG_POST, IG_CHAT
│   │   │   ├── CanvasAspectMode.kt      # ORIGINAL, BLUR_BG_9_16, CROP_9_16
│   │   │   ├── MediaMetadata.kt         # Resolusi, FPS, durasi, HDR flag, ukuran byte
│   │   │   ├── OptimizationConfig.kt    # Preset + Parameter Advanced (Foto & Video)
│   │   │   └── OptimizedMediaItem.kt    # Representasi hasil di folder ZideeResolution
│   │   ├── repository/
│   │   │   ├── MediaOptimizerEngine.kt  # Kontrak mesin pemroses foto & video
│   │   │   └── MediaRepository.kt       # Kontrak penyimpanan MediaStore & riwayat
│   │   └── usecase/
│   │       ├── CalculateOptimalSpecUseCase.kt
│   │       ├── OptimizeMediaUseCase.kt
│   │       └── ObserveOptimizationHistoryUseCase.kt
│   └── build.gradle.kts
├── data/                                # Modul Data (MediaStore "ZideeResolution", Room, DataStore)
│   ├── src/main/java/com/zidee/ress/data/
│   │   ├── mediastore/
│   │   │   ├── ZideeMediaStoreSaver.kt  # Simpan ke Pictures/ & Movies/ZideeResolution
│   │   │   └── MediaMetadataReader.kt   # Ekstraksi EXIF & MediaMetadataRetriever
│   │   ├── local/
│   │   │   ├── ZideeDatabase.kt         # Room Database
│   │   │   ├── dao/OptimizationHistoryDao.kt
│   │   │   └── entity/OptimizationHistoryEntity.kt
│   │   ├── preferences/
│   │   │   └── UserPreferencesDataStore.kt
│   │   └── repository/
│   │       └── MediaRepositoryImpl.kt
│   └── build.gradle.kts
├── media-engine/                        # Modul Mesin Pemroses Offline (Foto & Video)
│   ├── src/main/java/com/zidee/ress/engine/
│   │   ├── photo/
│   │   │   ├── PhotoProcessingPipeline.kt
│   │   │   ├── LanczosResampler.kt
│   │   │   ├── UnsharpMaskFilter.kt
│   │   │   ├── BlurBackgroundCompositor.kt
│   │   │   └── ColorSpaceNormalizer.kt  # Display P3 / Wide Gamut -> sRGB
│   │   ├── video/
│   │   │   ├── VideoProcessingPipeline.kt
│   │   │   ├── FfmpegFiltergraphBuilder.kt
│   │   │   ├── FfmpegProgressParser.kt
│   │   │   └── StorySegmentSplitter.kt  # Auto-Split 30s / 60s
│   │   └── service/
│   │       └── MediaOptimizationForegroundService.kt
│   └── build.gradle.kts
├── design-system/                       # Modul UI Kit & Tema ("Pro Darkroom Optics")
│   ├── src/main/java/com/zidee/ress/designsystem/
│   │   ├── theme/
│   │   │   ├── Color.kt                 # Obsidian #0B0D10, Amber Gold #F59E0B, Crisp White #F8FAFC
│   │   │   ├── Type.kt                  # Typography + tabular-nums
│   │   │   └── Theme.kt                 # ZideeTheme
│   │   └── component/
│   │       ├── BeforeAfterSplitSlider.kt # Garis geser kiri-kanan
│   │       ├── PlatformPresetBar.kt
│   │       ├── CanvasRatioSelector.kt
│   │       └── AdvancedControlSheet.kt
│   └── build.gradle.kts
├── presentation/                        # Modul Layar & ViewModel (UDF)
│   ├── src/main/java/com/zidee/ress/presentation/
│   │   ├── dashboard/                   # Home Screen & Riwayat ZideeResolution
│   │   ├── studio/                      # Interactive Studio Editor & Split-Slider
│   │   ├── quickshare/                  # Quick Share Bottom Sheet UI & ViewModel
│   │   └── share/                       # DirectShareIntentRouter (WA & IG package targeting)
│   └── build.gradle.kts
├── docs/                                # Dokumentasi Lengkap Proyek
├── AGENTS.md
├── CLAUDE.md
├── DESIGN.md
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 2. Tanggung Jawab Setiap Modul

| Modul | Jenis Plugin Gradle | Tanggung Jawab Utama |
| :--- | :--- | :--- |
| `:app` | `com.android.application` | Merakit seluruh modul, mendeklarasikan `AndroidManifest.xml`, `Intent-Filter` untuk *Share Sheet*, ikon peluncur `Zidee Ress` ber-badge versi, dan konfigurasi R8. |
| `:core` | `com.android.library` / `kotlin` | Menyediakan `AppResult`, `AppError`, `DispatcherProvider`, dan utilitas pemformatan bebas UI. |
| `:domain` | `org.jetbrains.kotlin.jvm` | Menyimpan model bisnis, matriks *sweet-spot* 5 platform, rumus perhitungan piksel genap, dan `UseCase`. **Bebas ketergantungan Android.** |
| `:data` | `com.android.library` | Mengelola penyimpanan file permanen ke `MediaStore` (`ZideeResolution`), pencatatan riwayat di `Room`, dan preferensi `DataStore`. |
| `:media-engine` | `com.android.library` | Mengeksekusi pemrosesan piksel foto (*Lanczos-3, Blur BG, Unsharp Mask, sRGB*) dan kompilasi filtergraph video (*FFmpeg*) di dalam `ForegroundService`. |
| `:design-system`| `com.android.library` (Compose) | Menyediakan token tema *Pro Darkroom*, warna *Amber Gold/White*, dan komponen mandiri seperti `BeforeAfterSplitSlider`. |
| `:presentation` | `com.android.library` (Compose) | Mengimplementasikan layar-layar utama (`Dashboard`, `StudioEditor`, `QuickShareSheet`), `ViewModel`, dan `DirectShareIntentRouter`. |

---

## 3. Aturan Penamaan (Naming Conventions)

- **Composable Functions:** `PascalCase` dan berupa kata benda (`BeforeAfterSplitSlider`, `QuickShareBottomSheet`).
- **ViewModel & State:** `<Feature>ViewModel`, `<Feature>UiState`, `<Feature>UiEvent`, `<Feature>UiEffect`.
- **UseCase:** Kata kerja + akhiran `UseCase` (`CalculateOptimalSpecUseCase`, `OptimizeMediaUseCase`).
- **File Output di Galeri (`ZideeResolution`):**
  - Foto: `ZIDEE_IMG_<yyyyMMdd_HHmmss>_<PLATFORM>.jpg`
  - Video: `ZIDEE_VID_<yyyyMMdd_HHmmss>_<PLATFORM>.mp4` (atau `_part01.mp4` jika *Auto-Split* aktif).
