# ADR 0002: Hybrid FFmpeg (`libx264` + `MediaCodec`) & High-Precision Photo Pipeline

**Status:** Accepted  
**Date:** 2026-11-18

---

## Context

Penyebab utama foto dan video menjadi buram atau pecah saat diunggah ke WhatsApp dan Instagram (meskipun mode HD aktif) adalah:
1. *Downscaling* kasar (*bilinear*) dari resolusi kamera (`4K` / `4000px+`) ke kanvas Story (`1080×1920`).
2. Tidak adanya kompensasi ketajaman (*Unsharp Mask*) sebelum kompresi platform.
3. Video 60fps dan HDR 10-bit yang memicu pemangkasan *bitrate* per *frame* serta kerusakan warna.
4. Perbedaan kemampuan perangkat pengguna (HP spek tinggi vs HP spek rendah).

---

## Decision

1. **Untuk Foto:** Kami tidak menggunakan `Bitmap.createScaledBitmap` standar untuk hasil akhir, melainkan membangun pipeline yang melakukan normalisasi ruang warna ke **sRGB 8-bit**, *downsampling* **Lanczos-3**, komposisi kanvas (`Original`, `Blur BG 9:16`, `Crop 9:16`), dan filter **Smart Unsharp Mask** sebelum dikodekan ke JPEG (`94%`).
2. **Untuk Video:** Kami menggunakan **FFmpeg Filtergraph** satu jalur (*single-pass*) yang mencakup `scale=flags=lanczos`, `gblur` (untuk Blur BG), `unsharp`, `fps=30`, *tone-mapping* HDR-ke-SDR, serta *segment muxer* (`Auto-Split 30s/60s`).
3. **Dua Opsi Encoder di Menu Advanced:**
   - `CPU_LIBX264` (Default Kualitas Maksimal): Menghasilkan kepadatan visual terbaik di bawah batas *bitrate* WA/IG.
   - `GPU_MEDIACODEC` (Mode Cepat): Memanfaatkan *hardware encoder* bawaan HP untuk pengguna yang memprioritaskan kecepatan *render*.

---

## Consequences

### Positive
- Kualitas visual pasca-upload di WA Story dan IG Story meningkat drastis karena platform menerima file yang sudah berada tepat di titik *sweet-spot*.
- Fleksibel untuk berbagai spesifikasi HP pengguna secara **100% Full Offline**.

### Negative
- Ukuran bundel APK bertambah dari pustaka native FFmpeg (dapat diminimalkan dengan ABI splits `arm64-v8a` dan `armeabi-v7a`).
