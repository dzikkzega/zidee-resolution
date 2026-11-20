# Testing Strategy & Quality Assurance Documentation — Zidee Ress

Dokumen ini mendefinisikan strategi pengujian menyeluruh (*Testing Pyramid*) untuk **Zidee Ress** sesuai standar **ECC Test-Driven Development (TDD)** dan **Kotlin Testing Rules**.

---

## 1. Filosofi & Target Cakupan Pengujian (Coverage Targets)

1. **Test-First (TDD):** Setiap aturan perhitungan resolusi, bitrate, dan filtergraph FFmpeg wajib memiliki unit test yang gagal terlebih dahulu (*Red*) sebelum kode implementasi ditulis (*Green*), lalu dirapikan (*Refactor*).
2. **Fakes Over Mocks:** Gunakan implementasi palsu yang deterministik (`FakeMediaRepository`, `FakeMediaOptimizerEngine`, `FakeMediaStoreSaver`) dibanding *mocking framework* yang rapuh.
3. **Target Cakupan Minimum:**
   - `:domain` (Kalkulator Resolusi, Bitrate, & UseCase): **$\ge 90\%$**
   - `:media-engine` (Generator Filtergraph FFmpeg & Parameter Foto): **$\ge 85\%$**
   - `:presentation` (`ViewModel` State & Effect dengan `Turbine`): **$\ge 85\%$**
   - `:data` (`Room` In-Memory DAO & Repository): **$\ge 80\%$**

---

## 2. Piramida Pengujian Zidee Ress

```text
          /\
         /  \         Lapis 4: End-to-End & Share Intent Flow Tests (Instrumented)
        /────\        (Galeri Share -> QuickShareActivity -> ZideeResolution -> WA/IG Intent)
       /      \
      /────────\      Lapis 3: Media Engine Output & Compose UI Tests (AndroidTest)
     /          \     (BeforeAfterSplitSlider Gesture, Resolusi Output Nyata, Modulo-2, sRGB)
    /────────────\
   /              \   Lapis 2: ViewModel & Repository Integration Tests (JVM + Robolectric/Room)
  /────────────────\  (Turbine StateFlow, Coroutines TestDispatcher, Cancellation Recovery)
 /                  \
/____________________\ Lapis 1: Domain & Filtergraph Unit Tests (Pure JVM — Cepat < 2 detik)
                      (5 Platform Preset Matrix, 3 Mode Rasio, Modulo-2 Rounding, FFmpeg Args)
```

---

## 3. Rincian Pengujian per Lapisan

### Lapis 1: Domain & Filtergraph Builder Unit Tests (Pure JVM)
Dijalankan di JVM lokal tanpa membutuhkan emulator (`./gradlew :domain:test :media-engine:testDebugUnitTest`).

#### A. Matriks Pengujian `CalculateOptimalSpecUseCaseTest`:
- **Tes Modulo-2 (Dimensi Genap Wajib):**
  - Input foto aneh `1333 × 1999` pada mode `ORIGINAL` untuk `WA_STORY` $\rightarrow$ Output wajib bernilai genap `1080 × 1620` (bukan `1080 × 1619`).
- **Tes 5 Platform Target:**
  - `WA_STORY` + `BLUR_BG_9_16`: Output wajib mutlak `1080 × 1920`, bitrate maks `2400 kbps`, FPS `30`.
  - `IG_POST` + Foto Portrait `3000 × 4000`: Lebar wajib terkunci di `1080px` (`1080 × 1350` untuk `4:5` atau `1080 × 1440` sesuai rasio).
  - `WA_CHAT` + Foto `6000 × 4000`: Sisi terpanjang wajib dikunci di `2048px` (`2048 × 1366`).
- **Tes Kalkulasi Auto-Split Video:**
  - Video durasi `75 detik` untuk `WA_STORY` (split per `30 detik`) $\rightarrow$ Menghasilkan ekspektasi `3 segmen` (`30s`, `30s`, `15s`).

#### B. Matriks Pengujian `FfmpegFiltergraphBuilderTest`:
- Memastikan argumen FFmpeg yang dihasilkan untuk mode `BLUR_BG_9_16` memuat `flags=lanczos`, `gblur=sigma=32`, `unsharp`, `fps=30`, dan `format=yuv420p`.
- Memastikan video berflag `isHdr = true` otomatis menyertakan filter `tonemap=hable` menuju `bt709` ketika toggle `hdrToSdr` aktif.
- Memastikan pergantian mode encoder dari `CPU_LIBX264` ke `GPU_MEDIACODEC` mengganti `-c:v libx264` menjadi `-c:v h264_mediacodec` tanpa merusak struktur filter.

---

### Lapis 2: ViewModel & StateFlow Testing (`Turbine` + `kotlinx-coroutines-test`)

Contoh standar pengujian `QuickShareViewModelTest` dan `StudioEditorViewModelTest`:

```kotlin
@Test
fun `process and share saves to ZideeResolution first then emits direct share effect`() = runTest {
    val fakeEngine = FakeMediaOptimizerEngine()
    val fakeRepo = FakeMediaRepository()
    val viewModel = QuickShareViewModel(
        calculateOptimalSpec = CalculateOptimalSpecUseCase(),
        optimizeMedia = OptimizeMediaUseCase(fakeEngine, fakeRepo),
         savedStateHandle = SavedStateHandle(mapOf("source_uri" to "content://gallery/video/1"))
    )

    viewModel.state.test {
        val initial = awaitItem()
        assertEquals(TargetPlatform.WA_STORY, initial.selectedPlatform)

        viewModel.onEvent(QuickShareEvent.SelectAspectMode(CanvasAspectMode.BLUR_BG_9_16))
        assertEquals(CanvasAspectMode.BLUR_BG_9_16, awaitItem().selectedAspectMode)

        viewModel.onEvent(QuickShareEvent.ClickSaveAndShare)
        assertTrue(awaitItem().isProcessing)

        val completed = awaitItem()
        assertFalse(completed.isProcessing)
        assertEquals("Pictures/ZideeResolution", fakeRepo.lastSavedRelativePath)
    }

    viewModel.effect.test {
        val effect = awaitItem()
        assertTrue(effect is QuickShareEffect.LaunchTargetAppShare)
        assertEquals("com.whatsapp", (effect as QuickShareEffect.LaunchTargetAppShare).targetPackage)
    }
}
```

---

### Lapis 3: Media Quality & Compose UI Instrumented Tests (`androidTest`)

#### A. Pengujian Kualitas Output Mesin Foto & Video (`PhotoPipelineInstrumentedTest`):
1. **Verifikasi Dimensi Piksel:** Render gambar uji `3000 × 2000` dengan mode `BLUR_BG_9_16`, lalu baca kembali file JPEG hasil menggunakan `BitmapFactory.Options(inJustDecodeBounds = true)` dan pastikan `outWidth == 1080` dan `outHeight == 1920`.
2. **Verifikasi Ruang Warna sRGB:** Pastikan `Bitmap.colorSpace == ColorSpace.get(ColorSpace.Named.SRGB)`.
3. **Verifikasi Ketajaman (Edge Variance Check):** Hitung varians operator *Laplacian* pada gambar sesudah *Unsharp Mask* (`25%`) dan pastikan nilainya lebih tinggi daripada gambar sebelum *Unsharp Mask* (membuktikan efek anti-blur bekerja secara terukur).

#### B. Pengujian Interaksi `BeforeAfterSplitSliderTest` (Compose UI Test):
- Render komponen `BeforeAfterSplitSlider` dengan posisi awal `0.5f` (tengah).
- Lakukan gestur `performTouchInput { swipeLeft() }` dan pastikan `onSplitFractionChange` terpanggil dengan nilai `< 0.2f` tanpa terjadi *crash* atau *frame drop*.

---

### Lapis 4: Pengujian Penyimpanan `MediaStore` & Integrasi `Room`

- **`ZideeDatabaseTest`:** Menggunakan `Room.inMemoryDatabaseBuilder` untuk menguji penyisipan riwayat optimasi, pengurutan berdasarkan `created_at_epoch_ms DESC`, dan penghapusan riwayat.
- **`ZideeMediaStoreSaverTest`:**
  - Memastikan file yang disimpan memiliki `RELATIVE_PATH` bernilai `"Pictures/ZideeResolution/"` (untuk foto) atau `"Movies/ZideeResolution/"` (untuk video).
  - Memastikan saat simulasi kegagalan I/O terjadi di tengah proses tulis, entri `IS_PENDING` langsung dihapus (*rollback*) sehingga tidak ada file rusak di Galeri.

---

## 4. Daftar Periksa Verifikasi Manual (Pre-Release QA Checklist)

Sebelum merilis APK, jalankan daftar periksa berikut pada perangkat fisik:

- [ ] **Cek Ikon & Badge Versi:** Ikon aplikasi **Zidee Ress** di layar utama HP menampilkan visual lensa kristal dan *badge* versi (`v1.0`) yang terbaca jelas di bagian bawah ikon.
- [ ] **Jalur Cepat (Galeri $\rightarrow$ Share):** Pilih foto & video dari Galeri HP $\rightarrow$ klik *Share* $\rightarrow$ pilih **Zidee Ress** $\rightarrow$ *Bottom Sheet* terbuka instan $\rightarrow$ klik *Simpan & Kirim* $\rightarrow$ terbuka langsung di WhatsApp/Instagram.
- [ ] **Cek Album Galeri Permanen:** Buka aplikasi Galeri bawaan HP $\rightarrow$ pastikan terdapat album bernama **`ZideeResolution`** yang berisi hasil optimasi dari jalur cepat maupun dari dalam aplikasi.
- [ ] **Cek *Split-Slider* Kiri-Kanan:** Buka *Studio Editor* $\rightarrow$ geser garis pemisah ke kiri dan kanan $\rightarrow$ pastikan perbedaan sebelum dan sesudah ketajaman terlihat jelas dan pergerakan garis halus.
- [ ] **Cek Ketahanan HP Spek Rendah / Background:** Jalankan optimasi video 4K dengan mode `CPU_LIBX264`, lalu tekan tombol *Home* HP $\rightarrow$ pastikan notifikasi progres tetap berjalan sampai selesai dan file tersimpan utuh di `ZideeResolution`.
