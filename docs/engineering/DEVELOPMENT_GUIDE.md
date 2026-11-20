# Development Guide — Zidee Ress

Panduan penyiapan lingkungan pengembangan (*Development Environment*), aturan kompilasi, serta alur kerja Git untuk kontributor dan AI Agent di proyek **Zidee Ress**.

---

## 1. Prasyarat Perangkat & SDK

- **JDK:** Java 17 LTS (JetBrains Runtime / Eclipse Temurin 17).
- **Android SDK:**
  - `compileSdk = 35` (Android 15)
  - `targetSdk = 35`
  - `minSdk = 26` (Android 8.0 Oreo — mencakup $>97\%$ perangkat Android aktif)
- **Android NDK & CMake:** Diperlukan jika mengompilasi modul C++ native di `:media-engine` (NDK r26d / r27 LTS).
- **Android Studio:** Ladybug / Meerkat atau versi lebih baru dengan dukungan Kotlin 2.x K2 Compiler.

---

## 2. Konfigurasi Lokal

1. Pastikan file `local.properties` di root proyek menunjuk ke lokasi Android SDK di mesin lokal:
   ```properties
   sdk.dir=C\:\\Users\\user\\AppData\\Local\\Android\\Sdk
   ```
   *(Catatan: `local.properties` diabaikan oleh `.gitignore` dan tidak boleh di-commit).*

2. Verifikasi konfigurasi Gradle:
   ```bash
   ./gradlew help
   ```

---

## 3. Alur Kerja Harian (TDD & Verification Loop)

1. **Buat atau Perbarui Unit Test Terlebih Dahulu:**
   Tambahkan pengujian di `src/test/` pada modul terkait (`:domain`, `:data`, `:media-engine`, atau `:presentation`).
2. **Jalankan Unit Test Cepat:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. **Implementasikan Kode Produksi:**
   Ikuti aturan di [AGENTS.md](../../AGENTS.md) (tanpa operator `!!`, gunakan `val`, pisahkan *Stateful* dan *Stateless* Composable).
4. **Build APK Debug & Cek Lint:**
   ```bash
   ./gradlew assembleDebug lintDebug
   ```

---

## 4. Manajemen Versi APK & Sinkronisasi Badge Ikon

Sesuai spesifikasi desain, ikon peluncur **Zidee Ress** menampilkan *badge* versi aplikasi di bagian bawah ikon:
- Versi utama didefinisikan secara terpusat di `gradle/libs.versions.toml` atau `app/build.gradle.kts` (`versionName = "1.0"`, `versionCode = 1`).
- Setiap kenaikan `versionName` wajib diikuti pembaruan label teks pada vektor lapisan *badge* ikon (`ic_launcher_foreground.xml`) agar tampilan ikon selalu sinkron dengan versi APK yang terpasang.

---

## 5. Standar Git & Conventional Commits

Setiap commit harus fokus pada satu perubahan logis dan menggunakan format **Conventional Commits**:

```text
feat(domain): add sweet-spot preset calculator for WA and IG targets
feat(engine): implement lanczos3 downsampler and unsharp mask filter
feat(ui): build interactive left-right before-after split slider
feat(storage): save all optimized media permanently to ZideeResolution album
fix(share): grant read uri permission when forwarding to whatsapp package
test(presentation): add turbine tests for quick share bottom sheet state
docs(prd): document 5-platform resolution and bitrate matrix
```
