# ADR 0001: Native Kotlin + Jetpack Compose & Multi-Module Clean Architecture

**Status:** Accepted  
**Date:** 2026-11-18

---

## Context

Zidee Ress membutuhkan dua jalur interaksi utama:
1. **Quick Share Bottom Sheet:** Harus terbuka secara instan (`< 200ms`) di atas aplikasi Galeri ketika pengguna menekan tombol *Share*.
2. **Main Studio Editor:** Memerlukan kanvas pratinjau berkinerja tinggi (120Hz), *Before/After Split-Slider* kiri-kanan secara *real-time*, serta komunikasi langsung dengan mesin pemroses media native (C++ / FFmpeg) dan sistem `MediaStore` Android.

Framework *cross-platform* (seperti Flutter atau React Native) memiliki *overhead* inisialisasi *engine* saat membuka *translucent Activity* di atas aplikasi lain dan memerlukan serialisasi data lewat *bridge* untuk operasi *bitmap*/video berat.

---

## Decision

Kami memutuskan menggunakan **100% Native Android (Kotlin 2.x + Jetpack Compose)** dengan arsitektur **Multi-Module Clean Architecture** (`:app`, `:core`, `:domain`, `:data`, `:media-engine`, `:design-system`, `:presentation`).

---

## Consequences

### Positive
- *Cold start* pada `QuickShareActivity` sangat cepat dan ringan di memori.
- Integrasi langsung tanpa *bridge* ke `MediaStore`, `ForegroundService`, `Intent.ACTION_SEND`, dan pustaka JNI/FFmpeg.
- Modul `:domain` murni Kotlin dapat diuji dengan JUnit dalam hitungan milidetik.

### Negative
- Basis kode dikhususkan untuk ekosistem Android (sesuai target produk saat ini).
