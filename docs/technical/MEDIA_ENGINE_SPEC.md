# Media Engine Technical Specification — Zidee Ress

Dokumen ini merinci spesifikasi matematis dan teknis dari **Photo Processing Pipeline** dan **Video Processing Pipeline** di dalam modul `:media-engine`.

---

## 1. Aturan Emas Dimensi & Modulo-2

Encoder video perangkat keras maupun perangkat lunak (H.264 / YUV420p) mengharuskan lebar ($W$) dan tinggi ($H$) piksel bernilai **genap (habis dibagi 2)**. Setiap fungsi kalkulasi dimensi di Zidee Ress wajib menerapkan pembulatan ke bilangan genap terdekat:

$$\text{roundEven}(x) = \max\left(2, \text{round}\left(\frac{x}{2}\right) \times 2\right)$$

---

## 2. Photo Processing Pipeline (`PhotoProcessingPipeline`)

Urutan tahapan pemrosesan foto dari URI sumber hingga menjadi file JPEG tajam di `Pictures/ZideeResolution`:

```text
[Decode Bitmap + EXIF Orientation]
        │
        ▼
[1. Color Space Normalization: Wide Gamut (Display P3 / AdobeRGB) ──► sRGB 8-bit]
        │
        ▼
[2. Canvas Geometry & Lanczos-3 Resampling]
    ├─ Mode ORIGINAL     : Resize sisi panjang ke target (1080px / 1920px / 2048px) dengan Lanczos-3
    ├─ Mode CROP_9_16    : Potong region 9:16 sesuai offset pengguna ──► Lanczos-3 ke 1080×1920
    └─ Mode BLUR_BG_9_16 : Buat backdrop 1080×1920 (Scale-to-Fill + Gaussian Blur r=35 + Gelapkan 12%)
                           lalu tumpuk foto utama hasil Lanczos-3 tepat di tengah kanvas
        │
        ▼
[3. Anti-Blur Compensation: Smart Unsharp Mask (USM)]
        │
        ▼
[4. High-Efficiency JPEG Encoding (Quality 94% Default) ──► MediaStore "Pictures/ZideeResolution"]
```

### Detail Parameter Unsharp Mask (Foto):
Filter *Unsharp Mask* menghitung selisih antara piksel asli $I(x,y)$ dan versi *Gaussian blur* beradius kecil $G_\sigma(x,y)$ untuk menajamkan tepi objek:

$$I_{\text{sharp}}(x,y) = \begin{cases} \text{clamp}\big(I(x,y) + \alpha \cdot (I(x,y) - G_\sigma(x,y)),\, 0,\, 255\big) & \text{jika } |I(x,y) - G_\sigma(x,y)| \ge T \\ I(x,y) & \text{jika } |I(x,y) - G_\sigma(x,y)| < T \end{cases}$$

- **Radius ($\sigma$):** `1.2 px` (fokus pada ketajaman mikro agar tidak menimbulkan efek *halo* putih).
- **Threshold ($T$):** `4` nilai luminansi (mencegah area rata seperti langit atau kulit wajah menjadi bintik-bintik/*noisy*).
- **Amount ($\alpha$):** Dipetakan secara linear dari *slider* pengguna (`0% – 100%` $\rightarrow$ $\alpha \in [0.0,\, 1.5]$, dengan nilai optimal default `25%` $\rightarrow \alpha = 0.38$).

---

## 3. Video Processing Pipeline (`VideoProcessingPipeline`)

Zidee Ress menyusun grafik filter (`-filter_complex`) FFmpeg tunggal (*single-pass*) agar video tidak mengalami degradasi akibat proses *decode-encode* berulang.

### 3.1. Filtergraph untuk 3 Mode Kanvas/Rasio

#### A. Mode `ORIGINAL` (Rasio Asli, Sisi Panjang Maksimal 1920px):
```text
scale='if(gt(iw,ih),min(1920,iw),-2)':'if(gt(iw,ih),-2,min(1920,ih))':flags=lanczos,
fps=30,
unsharp=5:5:0.45:3:3:0.0,
format=yuv420p
```

#### B. Mode `CROP_9_16` (Full Screen `1080×1920` dengan Offset Interaktif):
```text
crop='min(iw,ih*9/16)':'min(ih,iw*16/9)':'(iw-ow)*cropX':'(ih-oh)*cropY',
scale=1080:1920:flags=lanczos,
fps=30,
unsharp=5:5:0.45:3:3:0.0,
format=yuv420p
```

#### C. Mode `BLUR_BG_9_16` (Kanvas `1080×1920` dengan Latar Blur Halus):
```text
[0:v]split=2[bg][fg];
[bg]scale=1080:1920:force_original_aspect_ratio=increase:flags=bilinear,
    crop=1080:1920,
    gblur=sigma=32,
    eq=brightness=-0.06[bg_blurred];
[fg]scale=1080:1920:force_original_aspect_ratio=decrease:flags=lanczos,
    unsharp=5:5:0.45:3:3:0.0[fg_sharp];
[bg_blurred][fg_sharp]overlay=(W-w)/2:(H-h)/2,
fps=30,
format=yuv420p[outv]
```

### 3.2. Normalisasi HDR ke SDR (Tone-Mapping)
Jika metadata video sumber terdeteksi menggunakan *color transfer* `smpte2084` (HDR10) atau `arib-std-b67` (HLG) dan toggle **HDR to SDR** aktif, rantai filter diawali dengan konversi ruang warna ke `bt709` (SDR):
```text
zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p
```

### 3.3. Dua Mode Mesin Encoder (CPU vs GPU)

| Parameter | Mode 1: Kualitas Maksimal (`CPU_LIBX264`) | Mode 2: Mode Cepat (`GPU_MEDIACODEC`) |
| :--- | :--- | :--- |
| **Codec Flag** | `-c:v libx264` | `-c:v h264_mediacodec` |
| **Profile & Level** | `-profile:v high -level 4.1` | `-profile:v high` |
| **Preset & Tuning** | `-preset medium -crf 21` | Hardware VBR |
| **Bitrate Ceiling (WA Story)** | `-maxrate 2400k -bufsize 4800k` | `-b:v 2400k -maxrate 2800k` |
| **Bitrate Ceiling (IG Story)** | `-maxrate 4000k -bufsize 8000k` | `-b:v 4000k -maxrate 4500k` |
| **Keyframe Interval (GOP)** | `-g 60 -keyint_min 30 -sc_threshold 40` | `-g 60` |
| **Audio Codec** | `-c:a aac -b:a 128k -ar 44100 -ac 2` | `-c:a aac -b:a 128k -ar 44100 -ac 2` |
| **Faststart Flag** | `-movflags +faststart` | `-movflags +faststart` |

### 3.4. Auto-Split Story (Pemotong Otomatis Tanpa Kehilangan Kualitas)
Ketika opsi **Auto-Split** diaktifkan (misal `30 detik` untuk WA Story atau `60 detik` untuk IG Story) dan durasi video melebihi batas tersebut:
- Gunakan argumen *segment muxer* langsung pada saat encoding:
  ```text
  -f segment -segment_time 30 -reset_timestamps 1 -force_key_frames "expr:gte(t,n_forced*30)"
  ```
- Setiap potongan (`part01.mp4`, `part02.mp4`, dst.) langsung dipindahkan secara berurutan ke `Movies/ZideeResolution`.
