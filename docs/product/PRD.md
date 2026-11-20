# Product Requirements Document (PRD) — Zidee Ress

**Nama Produk:** Zidee Ress  
**Package ID:** `com.zidee.ress`  
**Platform:** Android (Min SDK 26 / Android 8.0 — Target SDK 35 / Android 15)  
**Mode Operasi:** 100% Full Offline (On-Device Processing)  
**Album Galeri Permanen:** `ZideeResolution` (`Pictures/ZideeResolution` & `Movies/ZideeResolution`)

---

## 1. Tujuan Produk (Product Objective)

Menyediakan aplikasi utilitas Android yang mampu mengoptimalkan resolusi, ketajaman, profil warna, rasio kanvas, dan *bitrate* dari foto maupun video secara **offline di HP pengguna** sebelum dikirim ke **WhatsApp (Chat & Story)** dan **Instagram (Story, Postingan/Reels, & Chat/DM)**, sehingga hasil unggahan tetap tajam, jernih, dan bebas dari efek *blur* atau kotak-kotak (*macroblocking*).

---

## 2. Target Pengguna & Prinsip Utama

1. **Pengguna Harian & Kreator Mobile:** Pengguna yang sering membuat WA Story / IG Story atau mengirim dokumentasi lewat WA/IG namun kecewa karena hasil foto/video selalu turun kualitasnya walau tombol HD sudah dinyalakan.
2. **Prinsip 100% Offline & Tanpa Biaya Server:** Seluruh proses komputasi berjalan di prosesor/GPU HP pengguna masing-masing. Jika pengguna memakai HP berspesifikasi rendah, proses *render* akan memakan waktu lebih lama (dan dijaga tetap aman oleh *Foreground Service*), namun kualitas keluarannya tetap identik dan presisi.
3. **Penyimpanan Permanen Otomatis:** Setiap file yang selesai diproses—baik dari dalam aplikasi maupun lewat jalur cepat *Share Sheet* dari Galeri—**wajib tersimpan permanen** di folder khusus Galeri bernama **`ZideeResolution`**.

---

## 3. Matriks "Sweet-Spot" 5 Platform Target

Tabel berikut adalah parameter standar (*Default Preset*) yang diterapkan oleh Zidee Ress untuk mengakali algoritma kompresi masing-masing platform:

| Target Platform | Kanvas Ideal (Piksel) | Perlakuan Foto (Default) | Perlakuan Video (Default) |
| :--- | :--- | :--- | :--- |
| **1. WA Story** (`WA_STORY`) | `1080 × 1920` (9:16) | • Resize **Lanczos-3** ke `1080×1920` (Mode Blur BG / Crop) atau sisi panjang `1920px` (Mode Rasio Asli)<br>• **Unsharp Mask** `28%`<br>• Normalisasi **sRGB**<br>• JPEG Quality `94%` | • Resolusi `1080×1920` (Opsi *Advanced*: `720×1280 Ultra-Sharp`)<br>• Kunci **30 FPS**<br>• Codec H.264 High Profile (`libx264` CRF 21 / maxrate `2.4 Mbps`)<br>• **Unsharp Mask** halus + **HDR-to-SDR**<br>• **Auto-Split** per `30 detik` (opsional `60 detik`) |
| **2. WA Chat** (`WA_CHAT`) | Sisi terpanjang `2048px` (Foto) / `1080p` (Video) | • Pertahankan rasio asli (`ORIGINAL` default)<br>• Resize **Lanczos-3** maks `2048px`<br>• **Unsharp Mask** `20%`<br>• Normalisasi **sRGB**, JPEG `94%` | • Resolusi maks `1080p` (Rasio asli)<br>• Kunci **30 FPS** (atau 60 FPS di *Advanced*)<br>• Bitrate target `3.5 Mbps` VBR, Audio AAC `128 kbps` |
| **3. IG Story** (`IG_STORY`) | `1080 × 1920` (9:16) | • Resize **Lanczos-3** ke `1080×1920`<br>• **Unsharp Mask** `25%`<br>• Konversi wajib **Display P3/HDR $\rightarrow$ sRGB 8-bit**<br>• JPEG Quality `95%` | • Resolusi `1080×1920` (9:16)<br>• Kunci **30 FPS**<br>• H.264 High Profile, maxrate `4.0 Mbps` (`bufsize 8.0 Mbps`)<br>• Konversi **HDR10/HLG $\rightarrow$ SDR Rec.709**<br>• **Auto-Split** per `60 detik` |
| **4. IG Postingan** (`IG_POST`) | • Portrait `1080 × 1350` (4:5)<br>• Square `1080 × 1080` (1:1)<br>• Landscape `1080 × 566`<br>• Reels `1080 × 1920` (9:16) | • **Kunci lebar mutlak di `1080px`** dengan **Lanczos-3**<br>• **Unsharp Mask** `25%`<br>• Normalisasi **sRGB**, JPEG `95%` | • Reels/Video Post: `1080×1920` (atau `1080×1350`)<br>• **30 FPS** (opsi 60 FPS)<br>• H.264 High Profile, maxrate `5.0 Mbps`, AAC `128 kbps` (`44.1 kHz`) |
| **5. IG Chat / DM** (`IG_CHAT`) | Sisi terpanjang `1920px` (Foto) / `1080p` (Video) | • Pertahankan rasio asli<br>• Resize **Lanczos-3** maks `1920px`<br>• **Unsharp Mask** `22%`, JPEG `93%` | • Resolusi maks `1080p` (Rasio asli)<br>• Kunci **30 FPS**<br>• Bitrate efisien `2.8 Mbps` agar lolos kompresi jalur DM |

---

## 4. Kebutuhan Fungsional (Functional Requirements)

### F-01: Dua Jalur Masuk Penggunaan (Dual Entry Workflow)
1. **Jalur Cepat (*Quick Share Bottom Sheet*):**
   - Menerima `Intent.ACTION_SEND` dan `Intent.ACTION_SEND_MULTIPLE` untuk tipe MIME `image/*` dan `video/*`.
   - Menampilkan panel bawah (*Bottom Sheet*) transparan di atas aplikasi Galeri tanpa memaksa pengguna berpindah ke halaman utama aplikasi.
   - Menyediakan pilihan 5 Target Platform, 3 Mode Rasio, menu lipat `⚙️ Advanced`, tombol **"Simpan Saja"**, dan tombol **"Simpan & Kirim"**.
2. **Jalur Aplikasi Penuh (*Main Studio App*):**
   - Dibuka melalui ikon peluncur **Zidee Ress**.
   - Menyediakan pemilih media (*Photo/Video Picker* tunggal maupun banyak file/*Batch*), layar *Interactive Studio Editor*, dan galeri riwayat folder `ZideeResolution`.

### F-02: Tiga Mode Kanvas & Rasio (Aspect Ratio Handling)
1. **`Rasio Asli (ORIGINAL)`:** Mempertahankan rasio asli foto/video tanpa memotong dan tanpa menambah bingkai, hanya mengoptimalkan resolusi piksel, ketajaman, warna, dan *bitrate*.
2. **`Blur BG 9:16 (BLUR_BG_9_16)`:** Membentuk kanvas mutlak `1080×1920` (`9:16`). Bagian foto/video asli diletakkan utuh di tengah, sedangkan area kosong di atas/bawah atau kiri/kanan diisi oleh duplikat gambar/video yang diberi efek *Gaussian Blur* halus (dengan opsi intensitas blur di *Studio Editor*).
3. **`Crop 9:16 (CROP_9_16)`:** Memotong area foto/video menjadi ukuran penuh `1080×1920` (`9:16`). Di jalur cepat dilakukan *center-crop* otomatis; di *Studio Editor* pengguna dapat menggeser dan memperbesar area potong secara bebas.

### F-03: Panel Pengaturan `⚙️ Advanced`
Pengguna dapat membuka panel **Advanced** untuk mengubah parameter di luar *preset* otomatis:
- **Parameter Foto:**
  - *Scaling Algorithm:* `Lanczos-3 (Default)` / `Bicubic`.
  - *Anti-Blur Sharpening:* Slider `0%` hingga `100%`.
  - *Target Resolution:* `1080px` / `1600px` / `2048px`.
  - *JPEG Quality:* Slider `80%` hingga `100%`.
  - *Auto sRGB Color Fix:* Toggle `ON/OFF`.
- **Parameter Video:**
  - *Output Resolution:* `1080p Full HD` / `720p Ultra-Sharp`.
  - *Frame Rate:* `Lock 30 FPS` / `60 FPS` / `Sesuai Asli`.
  - *Encoder Engine:* `Kualitas Maksimal (CPU libx264)` / `Cepat (GPU Hardware MediaCodec)`.
  - *Bitrate Control:* `Auto Sweet-Spot` / `Manual Slider (1.5 Mbps – 12.0 Mbps)`.
  - *Anti-Blur Video Sharpening:* Toggle/Slider `Unsharp Mask`.
  - *HDR to SDR Tone-Mapping:* Toggle `ON/OFF`.
  - *Auto-Split Story:* `Tidak Dipotong` / `Per 30 Detik` / `Per 60 Detik`.

### F-04: Pembanding Ketajaman *Split-Slider* (Garis Geser Kiri-Kanan)
- Di layar *Studio Editor*, pengguna dapat menggeser garis pembagi vertikal ke kiri dan ke kanan untuk membandingkan tampilan **Sebelum (Asli)** di sisi kiri dan **Sesudah (Zidee Ress HD)** di sisi kanan secara langsung.

### F-05: Penyimpanan Permanen di Folder `ZideeResolution` & Direct Share
- Semua file hasil wajib ditulis ke `MediaStore`:
  - Foto: `Pictures/ZideeResolution/ZIDEE_IMG_<YYYYMMDD_HHMMSS>_<TARGET>.jpg`
  - Video: `Movies/ZideeResolution/ZIDEE_VID_<YYYYMMDD_HHMMSS>_<TARGET>_part<N>.mp4`
- Setelah tersimpan, aplikasi dapat langsung meneruskan URI file tersebut ke paket aplikasi tujuan (`com.whatsapp`, `com.whatsapp.w4b`, atau `com.instagram.android`).

---

## 5. Kebutuhan Non-Fungsional (Non-Functional Requirements)

1. **Zero Network Dependency:** Aplikasi tidak memerlukan koneksi internet (`android.permission.INTERNET` bahkan tidak diperlukan untuk mesin utama) sehingga menjamin privasi 100%.
2. **Ketahanan di HP Spek Rendah:** Pemrosesan berjalan di *background dispatcher* yang diikat ke `ForegroundService` dengan notifikasi progres agar sistem Android tidak menghentikan proses saat CPU bekerja keras.
3. **Ukuran File & Dimensi Modulo-2:** Seluruh dimensi lebar dan tinggi video wajib bernilai genap (kelipatan 2) agar kompatibel 100% dengan seluruh *decoder/encoder* H.264 di Android.
4. **Identitas Visual & Ikon:** Ikon aplikasi menampilkan elemen visual lensa HD persuasif dengan *badge* versi APK (misal `v1.0`) di bagian bawah ikon.
