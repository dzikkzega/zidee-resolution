# Design System & UI/UX Specification — Zidee Ress

Dokumen ini adalah salinan kanonis arsitektur desain di dalam folder `docs/design/` yang merinci spesifikasi antarmuka **Zidee Ress** (selaras dengan [../../DESIGN.md](../../DESIGN.md)).

---

## 1. Identitas Visual & Keputusan Desain Terkunci

| Dimensi Desain | Keputusan Terkunci | Alasan Ergonomi & Teknis |
| :--- | :--- | :--- |
| **Nama Aplikasi** | **Zidee Ress** | Ringkas, mudah diingat, dan muat dalam 1 baris label di bawah ikon peluncur Android. |
| **Nama Album Galeri** | **`ZideeResolution`** | Tersimpan rapi di `Pictures/ZideeResolution` dan `Movies/ZideeResolution`. |
| **Tema Warna** | **Opsi A — Exclusive Pro Darkroom** | Permukaan gelap `#0B0D10` dan `#13171D` menjaga akurasi persepsi mata terhadap ketajaman dan kontras warna foto/video. |
| **Warna Aksen** | **Amber Gold (`#F59E0B`) & Crisp White (`#F8FAFC`)** | Terinspirasi cincin fokus lensa kamera profesional; memberikan kontras tinggi tanpa mengganggu warna media. |
| **Mode Before/After** | **Garis Geser Kiri-Kanan (*Split Slider*)** | Memungkinkan inspeksi mikro pada garis tepi objek (mata, teks, tekstur) sebelum dan sesudah *Lanczos + Unsharp Mask*. |
| **Desain Ikon APK** | **Persuasive Split-Lens + Version Badge** | Menampilkan transformasi visual dari piksel buram ke fokus kristal HD, dilengkapi pita versi (contoh: `v1.0`) di bagian bawah ikon. |

---

## 2. Spesifikasi Komponen Kunci

### 2.1. Komponen `BeforeAfterSplitSlider`
Digunakan pada layar *Studio Editor* untuk membandingkan media asli dengan hasil pratinjau Zidee Ress:
- **State:** `splitFraction: Float` (`0.0f` hingga `1.0f`, default `0.5f`).
- **Rendering:**
  - Lapisan Bawah: Bitmap hasil optimasi (*Lanczos-3 + Unsharp Mask + sRGB*).
  - Lapisan Atas: Bitmap asli yang dipotong (*clipped*) menggunakan `Modifier.drawWithContent` dari `x = 0` hingga `x = width * splitFraction`.
- **Divider Handle:**
  - Garis vertikal `2dp` berwarna `contrastWhite` (`#F8FAFC`) dengan bayangan tipis `4dp` (`#00000080`).
  - Tombol lingkaran di tengah garis berdiameter `36dp` (area sentuh diperluas ke `48dp x 48dp`) berwarna `accentAmber` (`#F59E0B`) dengan ikon panah ganda `◄ ►` berwarna `#0B0D10`.
- **Floating Badges:**
  - Pojok Kiri Atas: Kapsul transparan `#13171DCC` bertuliskan `ASLI`.
  - Pojok Kanan Atas: Kapsul transparan `#13171DCC` dengan teks `ZIDEE HD` berwarna `accentAmberBright` (`#FBBF24`).

### 2.2. Komponen `CanvasModeSelector` (3 Mode Rasio)
Menggunakan *Segmented Control* 3 kolom dengan radius luar `14dp` dan radius indikator aktif `10dp` (*concentric radius* dengan padding `4dp`):
1. **`Rasio Asli`** — Ikon bingkai proporsional (`AspectRatioOriginal`).
2. **`Blur BG 9:16`** — Ikon bingkai vertikal dengan bayangan kabur (`AspectRatioBlurBg`).
3. **`Crop 9:16`** — Ikon pemotong rasio vertikal (`AspectRatioCrop`).

### 2.3. Komponen `AdvancedSettingsSheet`
Panel *expandable* yang menampilkan kontrol granular dengan angka `tabular-nums`:
- Setiap slider memiliki tombol **Reset ke Optimal (`↺`)** di sebelah kanan nilainya jika pengguna mengubah nilai dari *preset* bawaan.
- Untuk opsi *Encoder Video*, ditampilkan informasi ringkas di bawah pilihan:
  - `Kualitas Maksimal (CPU libx264)`: *"Ketajaman terbaik di WA/IG Story. Proses lebih lama pada HP spek rendah."*
  - `Mode Cepat (GPU MediaCodec)`: *"Render 3x–5x lebih cepat menggunakan chip hardware HP."*
