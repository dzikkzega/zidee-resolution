# System Architecture — Zidee Ress

Dokumen ini menjelaskan arsitektur tingkat tinggi, pemisahan modul **Clean Architecture**, aliran data *Unidirectional Data Flow (UDF)*, serta orkestrasi pemrosesan media lokal pada **Zidee Ress**.

---

## 1. Gaya Arsitektur Utama

1. **100% On-Device Clean Architecture:** Seluruh lapisan (`presentation`, `domain`, `data`, `media-engine`) berjalan secara lokal di dalam perangkat Android tanpa ketergantungan jaringan.
2. **Unidirectional Data Flow (MVI / UDF):** Setiap layar dikendalikan oleh satu aliran `StateFlow<UiState>` yang *immutable*, menerima aksi pengguna melalui `onEvent(UiEvent)`, dan memancarkan efek satu-kali (*one-shot side effects* seperti membuka *Share Intent* ke WA/IG) melalui `Channel<UiEffect>`.
3. **Foreground-Guaranteed Media Pipeline:** Pemrosesan video atau *batch* foto dieksekusi di dalam `MediaOptimizationForegroundService` agar dijamin selesai meskipun pengguna berpindah aplikasi atau menggunakan perangkat berspesifikasi rendah.

---

## 2. Diagram Arsitektur Sistem

```mermaid
flowchart TD
    subgraph UI["Presentation & Entry Points"]
        MA["MainActivity\n(Dashboard & Studio Editor)"]
        QSA["QuickShareActivity\n(Bottom Sheet via Gallery Share)"]
        VM["ViewModels\n(StateFlow + UiEvent + UiEffect)"]
    end

    subgraph DOMAIN["Domain Layer (Pure Kotlin)"]
        UC1["CalculateOptimalSpecUseCase"]
        UC2["OptimizeMediaUseCase"]
        UC3["ObserveZideeAlbumHistoryUseCase"]
        REPO_INT["Repository & Engine Interfaces"]
    end

    subgraph DATA["Data & Storage Layer"]
        REPO_IMPL["MediaRepositoryImpl"]
        MS["MediaStoreSaver\n(Pictures/ & Movies/ZideeResolution)"]
        ROOM["Room Database\n(OptimizationHistoryDao)"]
        DS["Jetpack DataStore\n(Last Preset & User Prefs)"]
    end

    subgraph ENGINE["Offline Media Engine (:media-engine)"]
        FG["MediaOptimizationForegroundService"]
        PE["PhotoEngine\n(sRGB + Lanczos-3 + Blur BG + Unsharp + JPEG 94%)"]
        VE["VideoEngine\n(FFmpeg Filtergraph: Lanczos + 30fps + SDR + x264/MediaCodec + Split)"]
    end

    MA --> VM
    QSA --> VM
    VM --> UC1
    VM --> UC2
    VM --> UC3
    UC2 --> REPO_INT
    UC3 --> REPO_INT
    REPO_IMPL -.implements.-> REPO_INT
    REPO_IMPL --> FG
    FG --> PE
    FG --> VE
    REPO_IMPL --> MS
    REPO_IMPL --> ROOM
    REPO_IMPL --> DS
```

---

## 3. Alur Eksekusi Utama (Sequence Flow)

### 3.1. Alur Jalur Cepat (*Quick Share Bottom Sheet* $\rightarrow$ Album `ZideeResolution` $\rightarrow$ WA/IG)

```mermaid
sequenceDiagram
    actor User
    participant Gallery as Android Gallery
    participant QSA as QuickShareActivity
    participant VM as QuickShareViewModel
    participant Engine as MediaEngine (Photo/Video)
    participant MediaStore as MediaStore (ZideeResolution)
    participant TargetApp as WhatsApp / Instagram

    User->>Gallery: Pilih Foto/Video -> Klik "Share" -> Pilih "Zidee Ress"
    Gallery->>QSA: Intent.ACTION_SEND (content:// media URI)
    QSA->>VM: Inspect Media Metadata (Resolusi, FPS, Durasi, HDR)
    VM-->>QSA: Tampilkan Estimasi Sweet-Spot & Pilihan Target
    User->>QSA: Pilih "WA Story" + "Blur BG 9:16" -> Klik "Simpan & Kirim"
    QSA->>VM: onEvent(ProcessAndShare)
    VM->>Engine: Render Media (Lanczos-3 + Blur BG + Unsharp Mask)
    Engine-->>VM: File staging selesai di cacheDir
    VM->>MediaStore: Simpan permanen ke Pictures/ atau Movies/ZideeResolution
    MediaStore-->>VM: Return Permanent Content URI
    VM->>QSA: Emit Effect(LaunchDirectShareIntent(uri, "com.whatsapp"))
    QSA->>TargetApp: Buka halaman upload WA/IG dengan URI ZideeResolution
```

---

## 4. Manajemen Concurrency & Threading (`DispatcherProvider`)

Seluruh operasi asinkron menggunakan `DispatcherProvider` yang diinjeksikan agar mudah diganti dengan `StandardTestDispatcher` saat pengujian:

| Dispatcher | Penggunaan di Zidee Ress |
| :--- | :--- |
| `Dispatchers.Main.immediate` | Pembaruan `StateFlow` di `ViewModel`, animasi *Split-Slider*, dan navigasi UI. |
| `Dispatchers.Default` | Perhitungan matriks dimensi, pemrosesan piksel *Bitmap/OpenCV* di CPU, dan generasi pratinjau *Unsharp Mask*. |
| `Dispatchers.IO` | Pembacaan metadata `MediaExtractor`/`ExifInterface`, penulisan file ke `MediaStore` (`ZideeResolution`), kueri `Room`, dan eksekusi proses `FFmpeg`. |

---

## 5. Penanganan Error Terstruktur (`AppError`)

Tidak ada *exception* mentah yang boleh bocor ke lapisan UI. Semua kegagalan dipetakan ke `sealed interface AppError`:
- `AppError.SourceUnreadable`: URI dari Galeri tidak dapat dibuka atau izin kadaluarsa.
- `AppError.UnsupportedFormat`: File rusak atau codec tidak dikenali.
- `AppError.InsufficientStorage(requiredMb: Long)`: Ruang penyimpanan HP kurang untuk menyimpan hasil ke `ZideeResolution`.
- `AppError.EngineExecutionFailed(stage: String, reason: String)`: Kegagalan pada tahap pemrosesan foto/FFmpeg.
- `AppError.TargetAppNotInstalled(packageName: String)`: Aplikasi WA/IG belum terpasang (otomatis memicu *fallback* ke menu *Share* bawaan Android).
