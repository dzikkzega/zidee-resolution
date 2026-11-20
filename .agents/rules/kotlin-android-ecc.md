# Zidee Ress — Workspace Rules (Kotlin, Jetpack Compose, Clean Architecture & Offline Media Engine)

Aturan ini berlaku secara otomatis di seluruh workspace `zidee-resolution` dengan prioritas tertinggi:

1. **Identitas & Penyimpanan Wajib:**
   - Nama Aplikasi: **Zidee Ress** (`com.zidee.ress`)
   - Setiap output foto wajib disimpan ke `MediaStore` dengan `RELATIVE_PATH = "Pictures/ZideeResolution"`.
   - Setiap output video wajib disimpan ke `MediaStore` dengan `RELATIVE_PATH = "Movies/ZideeResolution"`.
   - Aturan penyimpanan permanen ini berlaku baik pada alur aplikasi utama maupun alur *Quick Share Bottom Sheet*.

2. **Desain Visual Terkunci ("Pro Darkroom Optics"):**
   - Hanya gunakan **Exclusive Pro Darkroom Theme** (`#0B0D10` / `#13171D`).
   - Warna aksen utama: **Amber Gold (`#F59E0B`)** dan **Crisp White (`#F8FAFC`)**.
   - Pembanding *Before vs After* wajib menggunakan **Interactive Left-Right Split Slider** (`BeforeAfterSplitSlider`).
   - Ikon aplikasi menampilkan elemen visual lensa HD persuasif disertai **Badge Versi (`v1.0`)** di bagian bawah ikon.

3. **Kualitas Kode Kotlin & Arsitektur:**
   - Modul `:domain` wajib murni Kotlin (tanpa ketergantungan Android SDK).
   - Dilarang menggunakan operator `!!` di seluruh kode produksi.
   - Seluruh lebar dan tinggi piksel output wajib dibulatkan ke bilangan genap terdekat (`roundEven`) agar aman bagi encoder H.264.
   - Seluruh `runCatching` di dalam *coroutine* wajib meneruskan (`throw e`) jika menangkap `CancellationException`.
