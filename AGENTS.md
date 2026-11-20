# AGENTS.md — Zidee Ress Engineering & Agent Operating Rules

Dokumen ini menjadi kontrak kerja utama bagi seluruh AI Agent (Antigravity, Claude Code, Codex, Cursor) yang bekerja di repositori **Zidee Ress**. Setiap perubahan kode dan dokumentasi wajib mematuhi aturan di bawah ini.

---

## 1. Identitas Produk & Invarian Utama

- **Nama Aplikasi:** `Zidee Ress`
- **Package ID:** `com.zidee.ress`
- **Prinsip Eksekusi:** **100% Full Offline (On-Device Processing)**. Dilarang menambahkan ketergantungan backend/cloud untuk pemrosesan foto atau video.
- **Penyimpanan Permanen Wajib:** Setiap hasil optimasi—baik melalui aplikasi utama (*Main Studio*) maupun jalur cepat (*Quick Share Bottom Sheet*)—**wajib** disimpan secara permanen ke Galeri perangkat menggunakan `MediaStore` di dalam folder:
  - Foto: `Pictures/ZideeResolution`
  - Video: `Movies/ZideeResolution`
- **Target Platform Optimasi:**
  1. `WA_STORY` (WhatsApp Status/Story)
  2. `WA_CHAT` (WhatsApp Chat)
  3. `IG_STORY` (Instagram Story)
  4. `IG_POST` (Instagram Feed / Carousel / Reels)
  5. `IG_CHAT` (Instagram Direct Message)
- **Tiga Mode Kanvas/Rasio Wajib:**
  1. `ORIGINAL` (Rasio asli dengan *downscaling* presisi ke batas piksel optimal)
  2. `BLUR_BG_9_16` (Kanvas penuh `1080x1920` dengan *Gaussian blur backdrop* dari media asli)
  3. `CROP_9_16` (Potong interaktif/presisi ke `1080x1920`)

---

## 2. Arsitektur & Batas Modul (Clean Architecture)

Proyek ini menggunakan **Android Clean Architecture** dengan pemisahan layer ketat:

```text
app ──► presentation, domain, data, design-system, media-engine, core
presentation ──► domain, design-system, core
data ──► domain, media-engine, core
media-engine ──► domain, core
design-system ──► core
domain ──► core (MURNI KOTLIN — Dilarang import android.* atau library pihak ketiga)
core ──► (Tidak memiliki dependensi ke modul internal lain)
```

### Aturan Ketat Antar-Layer:
1. **`domain` Layer Murni Kotlin:** Tidak boleh ada `import android.content.Context`, `Bitmap`, `Uri`, `Room`, atau `FFmpeg` di dalam `domain`. Gunakan *value object* atau representasi string URI abstrak (`MediaUri`).
2. **Satu UseCase = Satu Operasi Bisnis:** Gunakan `suspend operator fun invoke(...)` atau `operator fun invoke(...): Flow<T>`.
3. **Unidirectional Data Flow (UDF) di `presentation`:**
   - Setiap layar memiliki satu `UiState` (`data class` atau `sealed interface` *immutable*).
   - Interaksi pengguna dikirim melalui satu pintu `onEvent(event: ScreenEvent)`.
   - `ViewModel` tidak boleh memegang referensi ke `Context`, `Activity`, atau `View`.

---

## 3. Standar Kode Kotlin, Compose & Native Engine

### Kotlin Idioms & Safety
- **Immutability:** Selalu gunakan `val` secara default. Gunakan `data class` dan koleksi *immutable* (`List`, `Set`, `Map`) pada kontrak publik.
- **Null Safety Mutlak:** Operator `!!` **dilarang keras** di seluruh kode produksi. Gunakan `?.`, `?:`, `requireNotNull()`, atau `checkNotNull()` dengan pesan error yang jelas.
- **Error Handling:** Gunakan `Result<T>` atau *sealed error hierarchy* (`AppError`). Saat menggunakan `runCatching`, **wajib** meneruskan (*rethrow*) `CancellationException` agar *structured concurrency* Kotlin Coroutines tidak rusak:
  ```kotlin
  try {
      // operasi suspend
  } catch (e: CancellationException) {
      throw e
  } catch (t: Throwable) {
      // tangani error
  }
  ```

### Jetpack Compose & UI Standards
- **Theme:** Aplikasi menggunakan tema eksklusif **Pro Darkroom (`#0B0D10`)** dengan aksen **Amber Gold (`#F59E0B`)** dan **Crisp White (`#F8FAFC`)**. Jangan menggunakan warna *hardcoded* di dalam Composable; ambil seluruh warna dari `ZideeTheme.colors`.
- **State Hoisting:** Pisahkan *Stateful Screen Composable* (yang berinteraksi dengan `ViewModel`) dari *Stateless Content Composable* (yang hanya menerima `state` dan `onEvent` agar mudah diuji dan di-*preview*).
- **Tabular Numerals:** Semua angka dinamis (slider *Sharpness*, *Bitrate*, ukuran MB, persentase *progress*, penghitung waktu) wajib menggunakan `fontFeatureSettings = "tnum"`.

### Media Engine & Memory Safety
- **Jangan Gunakan `Bitmap.createScaledBitmap` Biasa untuk Output Akhir:** Gunakan pipeline *resampling* **Lanczos-3** diikuti filter **Unsharp Mask** dan normalisasi ruang warna **sRGB**.
- **Lifecycle Bitmap & File Sementara:** Segera lepaskan memori buffer native/Bitmap setelah disalin ke `MediaStore`, dan bersihkan file *staging* di `cacheDir` menggunakan blok `finally`.
- **Foreground Service untuk Video:** Pemrosesan video wajib dijalankan di dalam `MediaOptimizationForegroundService` dengan notifikasi progres aktif agar tidak dimatikan oleh sistem Android pada perangkat berspesifikasi rendah.

---

## 4. Alur Kerja Pengembangan & TDD (ECC Standard)

1. **Plan First:** Baca spesifikasi di [PRD.md](docs/product/PRD.md), [MEDIA_ENGINE_SPEC.md](docs/technical/MEDIA_ENGINE_SPEC.md), dan [DESIGN.md](DESIGN.md) sebelum mengubah arsitektur atau UI.
2. **Test-Driven Development (TDD):**
   - Tulis unit test (`JUnit` + `kotlinx-coroutines-test` + `Turbine`) untuk setiap `UseCase`, kalkulator dimensi/bitrate, dan `ViewModel`.
   - Gunakan **Fake** (`FakeMediaRepository`, `FakeMediaEngine`) dibanding *mocking framework* yang rapuh.
3. **Conventional Commits:**
   Format pesan commit wajib mengikuti standar:
   - `feat(engine): add lanczos3 downscaler and unsharp mask pipeline`
   - `feat(share): implement quick share bottom sheet activity`
   - `fix(mediastore): clear IS_PENDING flag on error recovery`
   - `docs(architecture): define module boundaries and preset matrix`
4. **Checklist Sebelum Selesai (Definition of Done):**
   - Tidak ada warning kompilasi Kotlin atau kebocoran memori `Context`/`Bitmap`.
   - Seluruh unit test di `domain`, `data`, dan `presentation` lulus.
   - Verifikasi visual memenuhi kontrak [DESIGN.md](DESIGN.md) (rasio konsentris, *touch target* minimal `48dp`, *split slider* responsif).
