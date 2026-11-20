# DESIGN.md — Zidee Ress Design System & UI/UX Specification

> **Design Direction:** *Pro Darkroom Optics*  
> **Brand Name:** Zidee Ress  
> **Gallery Album Output:** `ZideeResolution`

Dokumen ini mendefinisikan sistem visual, anatomi komponen, ergonomi interaksi, desain ikon aplikasi, dan tata letak layar untuk **Zidee Ress**. Seluruh implementasi Jetpack Compose di modul `design-system` dan `presentation` wajib mengacu pada spesifikasi ini.

---

## 1. Filosofi Desain: "Pro Darkroom Optics"

Aplikasi pengolahan foto dan video profesional (seperti kamera Leica, Sony Alpha, dan Adobe Lightroom) menggunakan permukaan gelap netral agar mata pengguna tidak mengalami bias warna (*chromatic adaptation*) saat menilai ketajaman dan akurasi warna media.

### Prinsip Utama:
1. **Exclusive Pro Darkroom (Opsi A):** Menggunakan latar belakang *Obsidian Dark* (`#0B0D10`) dan permukaan kartu *Carbon Slate* (`#13171D`). Tidak ada *Light Mode* yang menyilaukan mata saat memeriksa detail piksel.
2. **Amber Gold & Crisp White Contrast (Opsi B):** Aksen utama menggunakan **Amber Gold (`#F59E0B`)**—terinspirasi dari cincin anodisasi lensa kamera profesional—dipadukan dengan **Crisp White (`#F8FAFC`)** untuk aksi kontras tinggi dan keterbacaan instan.
3. **Interactive Split-Slider Comparison (Opsi C):** Perbandingan *Before vs After* menggunakan garis pemisah vertikal yang dapat digeser ke kiri dan kanan secara mulus (`60fps/120fps`) langsung di atas kanvas media.
4. **One-Handed Thumb Ergonomics:** Seluruh kontrol utama pada *Quick Share Bottom Sheet* maupun *Studio Editor* ditempatkan di paruh bawah layar dengan area sentuh minimal `48dp x 48dp`.

---

## 2. Palet Warna & Token Desain (`ZideeColors`)

| Token | Hex | Kegunaan |
| :--- | :--- | :--- |
| `bgCanvas` | `#0B0D10` | Latar belakang utama layar & latar ruang kanvas pratinjau media. |
| `bgSurface` | `#13171D` | Latar belakang kartu utama, *Bottom Sheet*, dan panel kontrol bawah. |
| `bgElevated` | `#1C222B` | Komponen di dalam kartu (*Segmented Button*, *Chip*, *Slider Track*, *Accordion* terbuka). |
| `bgGlass` | `#13171DCC` | Overlay mengambang di atas pratinjau media (*badge* resolusi, kontrol *Split Slider*). |
| `accentAmber` | `#F59E0B` | Warna aksen utama: indikator aktif, tombol CTA utama, garis cincin fokus, *handle* slider. |
| `accentAmberSoft` | `#F59E0B24` | Latar belakang *chip* atau *preset* yang sedang dipilih (14% opacity). |
| `accentAmberBright`| `#FBBF24` | Warna *highlight* saat tombol ditekan (*pressed state*) atau *badge* HD. |
| `contrastWhite` | `#F8FAFC` | Teks judul utama, angka metrik penting, tombol aksi kontras tinggi, garis pembagi *Split Slider*. |
| `textSecondary` | `#94A3B8` | Label parameter, deskripsi rasio, informasi ukuran file asli. |
| `textMuted` | `#64748B` | Teks *placeholder*, keterangan *disabled*, versi build sekunder. |
| `borderSubtle` | `#FFFFFF14` | Garis tepi kartu dan pemisah antar-bagian (`1dp`, putih 8% opacity). |
| `borderActive` | `#F59E0B80` | Garis tepi kartu/preset yang sedang aktif (`1.5dp`, Amber 50% opacity). |
| `statusSuccess` | `#10B981` | Indikator selesai disimpan ke folder `ZideeResolution` & siap kirim. |
| `statusWarning` | `#F97316` | Peringatan video durasi panjang / mode CPU *encoding* berat. |
| `statusDanger` | `#EF4444` | Peringatan gagal baca file / memori penyimpanan penuh. |

---

## 3. Tipografi & Hierarki Angka (`ZideeTypography`)

- **Font Utama:** `Inter` atau `Plus Jakarta Sans` (Sistem `SansSerif` dengan bobot `Medium`, `SemiBold`, `Bold`).
- **Font Metrik & Resolusi:** `JetBrains Mono` (atau `FontFamily.Monospace` + `fontFeatureSettings = "tnum"`) untuk seluruh indikator teknis seperti `1080×1920`, `30 FPS`, `4.2 MB`, `94%`, agar posisi teks tidak bergeser saat pengguna menggeser *slider*.

| Gaya Teks | Ukuran / Line Height | Bobot | Penggunaan |
| :--- | :--- | :--- | :--- |
| `DisplayTitle` | `22sp / 28sp` | `Bold` | Header halaman utama (`Zidee Ress`). |
| `SectionHeader` | `16sp / 22sp` | `SemiBold` | Judul bagian (*Target Platform*, *Mode Kanvas*, *Album ZideeResolution*). |
| `ControlLabel` | `14sp / 20sp` | `Medium` | Nama tombol preset (`WA Story`, `IG Story`, `Blur BG 9:16`). |
| `MetricBadge` | `12sp / 16sp` | `SemiBold` (`tnum`) | Badge info media (`4K → 1080p • 30fps • 6.8 MB`). |
| `Caption` | `11sp / 15sp` | `Normal` | Penjelasan singkat di bawah parameter *Advanced*. |

---

## 4. Spesifikasi Ikon Aplikasi ("Zidee Ress" Persuasive Icon + Version Badge)

Ikon aplikasi dirancang agar langsung mengomunikasikan manfaat produk (**mengubah media buram/pecah menjadi tajam berstandar optik profesional**) sekaligus menampilkan identitas versi aplikasi.

```text
      ╭───────────────────────────╮
      │   ░░░░░  ┃  ✦ ✦ ✦ ✦      │
      │   ░░░░░  ┃  ╭───────╮     │
      │   ░BLUR░ ┃  │ LENS  │     │  <-- Lensa Kristal Amber Gold:
      │   ░░░░░  ┃  ╰───────╯     │      Sisi kiri piksel kotak-kotak (pecah),
      │          ┃     HD+        │      dibelah garis fokus emas menjadi tajam
      │  ╭─────────────────────╮  │
      │  │     v1.0 • HD       │  │  <-- Badge Versi Kontras di Bagian Bawah Ikon
      ╰──┴─────────────────────┴──╯
```

### Anatomi Adaptive Icon (`ic_launcher`):
1. **Background Layer (`#0B0D10` dengan radial glow `#1E242B`):** Latar gelap pekat dengan tekstur cincin diafragma lensa tipis.
2. **Foreground Layer (Elemen Persuasif "Split Crystal Lens"):**
   - Lingkaran lensa optik berlapis cincin **Amber Gold (`#F59E0B` $\rightarrow$ `#FBBF24`)**.
   - Sisi kiri lensa menampilkan kisi piksel terdegradasi (simbol foto/video pecah di WA/IG), sedangkan sisi kanan menampilkan kilau ketajaman kristal **Crisp White (`#F8FAFC`)** yang dibelah oleh garis vertikal emas.
3. **Bottom Version Pill Badge:**
   - Kapsul horisontal di bagian bawah *safe-zone* ikon dengan latar belakang **Amber Gold (`#F59E0B`)** dan teks tebal **Obsidian (`#0B0D10`)** bertuliskan **`v1.0`** (diatur melalui `manifestPlaceholders` / *build variant* sehingga selalu mencerminkan versi APK).

---

## 5. Geometri, Radius Konsentris & Detail Interaksi

Mengikuti standar *Make Interfaces Feel Better*:
- **Concentric Corner Radius:**
  - *Bottom Sheet* / Panel Luar: `28dp`
  - Kartu Kontainer di dalam layar: `20dp` (dengan *padding* `12dp`)
  - Tombol / Segmented Control di dalam kartu: `12dp` (`20dp - 8dp/12dp` agar sejajar secara optik)
  - Badge Resolusi Kecil: `8dp`
- **Image & Video Canvas Outline:**
  Setiap pratinjau foto/video memiliki garis tepi dalam (*inset border*) `1dp` berwarna `#FFFFFF1A` (putih 10% opacity) agar batas kanvas `9:16` yang berwarna gelap tidak menyatu dengan latar belakang layar `#0B0D10`.
- **Tactile Motion:**
  - Saat tombol atau *chip* preset ditekan, gunakan animasi `scale(0.97f)` berdurasi `120ms` (`FastOutSlowInEasing`) ditambah getaran halus (`HapticFeedbackType.TextHandleMove` saat menggeser *Split Slider* atau memilih preset).

---

## 6. Spesifikasi 3 Layar Utama

### A. Layar 1: *Quick Share Bottom Sheet* (Jalur Cepat dari Galeri HP)
Muncul di atas aplikasi Galeri ketika pengguna menekan tombol **Share $\rightarrow$ Zidee Ress**:

```text
╭──────────────────────────────────────────────────╮
│                       ━━━━                       │
│  ┌──────┐  VID_20261118.mp4         [⚡ Offline] │
│  │ 9:16 │  Asli   : 2160p • 60fps • 64.2 MB      │
│  │ PREV │  Target : 1080p • 30fps • ~7.5 MB (HD) │
│  └──────┘  📁 Simpan ke: Galeri/ZideeResolution  │
├──────────────────────────────────────────────────┤
│  TARGET KIRIM                                    │
│  ╭────────────╮ ╭─────────╮ ╭──────────╮ ╭─────╮ │
│  │ ● WA Story │ │ WA Chat │ │ IG Story │ │ ... │ │
│  ╰────────────╯ ╰─────────╯ ╰──────────╯ ╰─────╯ │
│                                                  │
│  MODE KANVAS & RASIO                             │
│  ┌──────────────┬─────────────────┬────────────┐ │
│  │  Rasio Asli  │ ● Blur BG 9:16  │ Crop 9:16  │ │
│  └──────────────┴─────────────────┴────────────┘ │
│                                                  │
│  ▸ ⚙️ Pengaturan Advanced (Lanczos • Sharp 25%)  │
├──────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌────────────────────────┐ │
│  │  💾 Simpan Saja │  │ 🚀 Simpan & Kirim (WA) │ │
│  └─────────────────┘  └────────────────────────┘ │
╰──────────────────────────────────────────────────╯
```

### B. Layar 2: *Main Studio Dashboard* (Buka dari Ikon App)
1. **Top Bar:** Logo **Zidee Ress** + indikator `100% Offline Engine` + tombol pengaturan umum.
2. **Hero Action Card (Kontras Amber Gold & Obsidian):**
   - Tombol utama **"Pilih Foto / Video"** (mendukung *Single* & *Batch Multi-Select*).
   - Baris *Quick Launch Preset*: 5 kapsul target (`WA Story`, `WA Chat`, `IG Story`, `IG Post`, `IG Chat`).
3. **Section "Tersimpan di ZideeResolution" (History & Instant Re-Share):**
   - Menampilkan *grid* 2 kolom dari seluruh foto/video yang sudah dioptimasi dan tersimpan di folder Galeri `ZideeResolution`.
   - Setiap kartu menampilkan *badge* (`WA Story • 1080×1920 • 3.4 MB`) dan tombol ikon **Share Langsung** ke WA/IG tanpa perlu proses ulang.

### C. Layar 3: *Interactive Studio Editor & Split-Slider*
1. **Area Kanvas Interaktif (60% Tinggi Layar):**
   - **Garis Geser Kiri-Kanan (*Before/After Split Slider*):**
     - Sisi Kiri menampilkan label `[ASLI / SEBELUM]` dan sisi Kanan menampilkan label `[ZIDEE RESS HD]` berwarna Amber Gold.
     - Garis pembagi vertikal berwarna **Crisp White (`2dp`)** dengan tombol bulat **Amber Gold (`36dp`)** berikon panah `◄ ►` di tengahnya.
   - **Mode Kanvas Live:**
     - Jika mode `BLUR_BG_9_16` aktif: latar belakang `1080x1920` langsung menampilkan efek *Gaussian Blur* dengan slider kecil pengatur tingkat *Blur Radius* (`15px – 60px`).
     - Jika mode `CROP_9_16` aktif: pengguna dapat menggeser dan mencubit (*pan & zoom*) area foto/video di dalam bingkai `9:16` berpetak *Rule of Thirds*.
2. **Panel Kontrol Bawah (40% Tinggi Layar):**
   - **Tab 1 — Target & Rasio:** Pilihan 5 platform (`WA Story`, `WA Chat`, `IG Story`, `IG Post`, `IG Chat`) + 3 mode rasio (`Rasio Asli`, `Blur BG 9:16`, `Crop 9:16`).
   - **Tab 2 — ⚙️ Advanced Pro:**
     - **Foto:** Algoritma (`Lanczos-3` / `Bicubic`), *Anti-Blur Sharpening* (`0%–100%`), *Resolusi Target* (`1080px` / `1600px` / `2048px`), *JPEG Quality* (`80%–100%`), *Auto sRGB Fix* (`ON/OFF`).
     - **Video:** Resolusi (`1080p Full HD` / `720p Ultra-Sharp WA`), *Frame Rate* (`Lock 30 FPS` / `60 FPS` / `Original`), *Encoder Mode* (`Kualitas Maksimal CPU libx264` / `Cepat GPU MediaCodec`), *Bitrate Control* (`Auto Sweet-Spot` / `Manual Mbps`), *HDR to SDR Tone-Mapping* (`ON/OFF`), *Auto-Split Story* (`Off` / `Per 30 Detik` / `Per 60 Detik`).

---

## 7. Matriks Status Konten (UI States Contract)

Setiap layar wajib menangani 6 kondisi berikut secara eksplisit:

| State | Perilaku Visual di Zidee Ress |
| :--- | :--- |
| **Loading / Rendering** | Menampilkan *progress bar* Amber Gold dengan persentase `tabular-nums`, estimasi sisa waktu, tahap aktif (`Merender Lanczos...` / `Menyimpan ke ZideeResolution...`), dan tombol `Batalkan`. |
| **Empty (Belum Ada Riwayat)** | Ilustrasi ikon lensa tipis dengan teks *"Belum ada media di folder ZideeResolution"* dan tombol *"Pilih Foto atau Video Pertama"*. |
| **Error (File Rusak / Memori Penuh)** | Dialog/Banner merah lembut dengan alasan spesifik (misal: *"Penyimpanan internal penuh, butuh minimal 25 MB kosong"*) + tombol aksi pemulihan. |
| **Partial (Batch / Auto-Split Video)** | Saat video 90 detik dipotong menjadi 3 bagian WA Story, tampilkan daftar `Part 1`, `Part 2`, `Part 3` dengan status masing-masing dan tombol *"Kirim Semua ke WA"*. |
| **Success** | *Toast/Banner* hijau zamrud *"Tersimpan di Galeri/ZideeResolution"* + langsung membuka *Share Intent* ke WA/IG jika pengguna memilih *Simpan & Kirim*. |
| **Permission** | Penjelasan ramah saat meminta izin baca media (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` atau Android Photo Picker tanpa izin penuh) & izin notifikasi progres. |
