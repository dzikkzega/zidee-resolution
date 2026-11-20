# Code Review Guide — Zidee Ress

Daftar periksa wajib (*Engineering Review Checklist*) sebelum perubahan kode digabungkan (*merged*) ke dalam proyek **Zidee Ress**.

---

## 1. Kepatuhan Arsitektur & Modul

- [ ] Apakah modul `:domain` tetap **100% Pure Kotlin** (tidak ada import `android.*`, `androidx.*`, `Room`, atau `FFmpeg`)?
- [ ] Apakah seluruh operasi berat (decoding Bitmap, Lanczos resize, FFmpeg encoding, penulisan `MediaStore`) dijalankan di luar `Dispatchers.Main` menggunakan `DispatcherProvider`?
- [ ] Apakah `ViewModel` bebas dari referensi `Context`, `Activity`, atau `Resources`?

---

## 2. Kualitas & Keamanan Memori Media Engine

- [ ] **Modulo-2 Check:** Apakah lebar dan tinggi piksel output selalu dibulatkan menjadi bilangan genap sebelum dikirim ke encoder video H.264?
- [ ] **Bitmap Downsampling Guard:** Saat memuat pratinjau foto beresolusi sangat besar (misal 50MP / `8000×6000`), apakah kode menggunakan `inSampleSize` yang tepat untuk tampilan UI sementara pipeline penyimpanan menggunakan resolusi penuh secara bertahap agar tidak memicu `OutOfMemoryError`?
- [ ] **Temp File Hygiene:** Apakah seluruh file sementara di `context.cacheDir` dihapus di dalam blok `finally` setelah berhasil disalin ke folder `ZideeResolution`?
- [ ] **Coroutine Cancellation:** Apakah setiap blok `try/catch` atau `runCatching` di dalam *suspend function* meneruskan (`throw e`) ketika menangkap `CancellationException`?
  - Jika pengguna menekan tombol **Batalkan** saat *render* video, apakah sesi FFmpeg langsung dihentikan (`FFmpegKit.cancel(sessionId)`)?

---

## 3. Kepatuhan Penyimpanan `ZideeResolution`

- [ ] Apakah baik jalur *Studio Editor* maupun jalur *Quick Share Bottom Sheet* menyimpan hasil akhir ke `Pictures/ZideeResolution` atau `Movies/ZideeResolution`?
- [ ] Apakah mekanisme `IS_PENDING = 1` $\rightarrow$ `0` diterapkan dengan benar, disertai penghapusan URI jika proses gagal di tengah jalan?

---

## 4. Kualitas UI/UX & Jetpack Compose ("Pro Darkroom Optics")

- [ ] Apakah seluruh warna menggunakan token dari `ZideeTheme.colors` (latar `#0B0D10`/`#13171D`, aksen Amber Gold `#F59E0B`, teks Crisp White `#F8FAFC`) tanpa warna *hardcoded*?
- [ ] Apakah seluruh angka yang berubah dinamis (slider *Sharpness*, *Bitrate*, ukuran MB, persentase progres) telah menggunakan `tabular-nums` (`fontFeatureSettings = "tnum"`)?
- [ ] Apakah komponen `BeforeAfterSplitSlider` mengizinkan pergeseran kiri-kanan secara mulus tanpa memicu *recomposition* seluruh layar?
- [ ] Apakah seluruh tombol interaktif memiliki area sentuh minimal `48dp x 48dp`?
