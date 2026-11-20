# ADR 0003: Mandatory Permanent Storage in Gallery Album `ZideeResolution`

**Status:** Accepted  
**Date:** 2026-11-18

---

## Context

Pengguna sering membutuhkan hasil optimasi foto/video yang sama untuk dikirim ke lebih dari satu tempat (misalnya: membuat WA Story melalui jalur cepat, lalu beberapa menit kemudian ingin mengunggah file yang sama ke IG Story atau mengirimkannya ke grup WA Chat). Jika jalur cepat hanya menyimpan file di *cache* sementara, file tersebut akan hilang atau sulit dicari kembali di Galeri HP.

---

## Decision

Seluruh hasil optimasi—baik yang diproses melalui aplikasi utama (*Main Studio*) maupun melalui jalur cepat (*Quick Share Bottom Sheet* dari Galeri)—**wajib disimpan secara permanen** ke dalam album Galeri khusus bernama **`ZideeResolution`** menggunakan Android `MediaStore` (`Pictures/ZideeResolution` untuk foto dan `Movies/ZideeResolution` untuk video) serta dicatat di database `Room` lokal.

---

## Consequences

### Positive
- File hasil optimasi langsung muncul di aplikasi Galeri bawaan HP di dalam album **`ZideeResolution`**.
- Pengguna dapat melakukan *1-Click Re-Share* ke platform lain dari halaman utama Zidee Ress tanpa perlu melakukan *render* ulang.
- Mematuhi aturan *Scoped Storage* Android 10–15 tanpa memerlukan izin penyimpanan penuh yang invasif.

### Negative
- Menggunakan ruang penyimpanan internal pengguna untuk setiap file yang dioptimasi (diimbangi dengan ukuran file output yang sudah ramping, rata-rata `400 KB – 1.5 MB` untuk foto dan `4 MB – 9 MB` untuk video Story).
