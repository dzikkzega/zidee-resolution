# Database & Scoped Storage Specification — Zidee Ress

Dokumen ini menjelaskan arsitektur penyimpanan permanen di album Galeri **`ZideeResolution`** menggunakan Android `MediaStore`, skema database lokal `Room` untuk riwayat optimasi, serta `Jetpack DataStore` untuk preferensi pengguna.

---

## 1. Penyimpanan Galeri Permanen (`MediaStore` — Folder `ZideeResolution`)

Sesuai keputusan produk, **seluruh hasil optimasi wajib disimpan secara permanen** di folder khusus Galeri perangkat, baik saat pengguna memakai aplikasi penuh maupun saat menggunakan *Quick Share Bottom Sheet* dari Galeri.

### 1.1. Lokasi Path Relatif (Android 10 / API 29 ke Atas)
- **Foto:** `Environment.DIRECTORY_PICTURES + "/ZideeResolution"` (`Pictures/ZideeResolution`)
- **Video:** `Environment.DIRECTORY_MOVIES + "/ZideeResolution"` (`Movies/ZideeResolution`)

### 1.2. Protokol Transaksi `IS_PENDING` (Anti File Korup)
Agar aplikasi Galeri HP atau WhatsApp/Instagram tidak membaca file yang masih setengah ditulis:
1. Buat entri `ContentValues` baru di `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` atau `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` dengan:
   - `MediaStore.MediaColumns.DISPLAY_NAME = fileName`
   - `MediaStore.MediaColumns.MIME_TYPE = "image/jpeg"` atau `"video/mp4"`
   - `MediaStore.MediaColumns.RELATIVE_PATH = "Pictures/ZideeResolution"` (atau `"Movies/ZideeResolution"`)
   - `MediaStore.MediaColumns.IS_PENDING = 1`
2. Buka `OutputStream` dari `ContentResolver` dan salin isi file hasil render ke dalamnya.
3. Setelah *stream* ditutup dengan sukses, perbarui `ContentValues` menjadi `IS_PENDING = 0`.
4. Jika terjadi error atau pembatalan di tengah jalan, panggil `contentResolver.delete(insertedUri, null, null)` di blok `catch` agar tidak meninggalkan file `0 byte` di Galeri.

---

## 2. Skema Database Lokal (`Room` — `ZideeDatabase`)

Database `Room` digunakan untuk menampilkan daftar riwayat di halaman utama (*Section "Tersimpan di ZideeResolution"*) secara cepat beserta metadata lengkapnya.

### Tabel `optimization_history` (`OptimizationHistoryEntity`)

| Kolom | Tipe SQLite / Kotlin | Deskripsi |
| :--- | :--- | :--- |
| `id` | `TEXT PRIMARY KEY` (`String` UUID) | ID unik riwayat optimasi. |
| `media_store_uri` | `TEXT NOT NULL` (`String`) | Content URI permanen di `MediaStore` (`content://media/external/...`). |
| `display_name` | `TEXT NOT NULL` (`String`) | Nama file di folder `ZideeResolution`. |
| `media_type` | `TEXT NOT NULL` (`String`) | `"PHOTO"` atau `"VIDEO"`. |
| `target_platform` | `TEXT NOT NULL` (`String`) | `"WA_STORY"`, `"WA_CHAT"`, `"IG_STORY"`, `"IG_POST"`, atau `"IG_CHAT"`. |
| `aspect_mode` | `TEXT NOT NULL` (`String`) | `"ORIGINAL"`, `"BLUR_BG_9_16"`, atau `"CROP_9_16"`. |
| `original_width` | `INTEGER NOT NULL` (`Int`) | Lebar piksel sebelum dioptimasi. |
| `original_height` | `INTEGER NOT NULL` (`Int`) | Tinggi piksel sebelum dioptimasi. |
| `output_width` | `INTEGER NOT NULL` (`Int`) | Lebar piksel hasil (`1080`, dll.). |
| `output_height` | `INTEGER NOT NULL` (`Int`) | Tinggi piksel hasil (`1920`, `1350`, dll.). |
| `original_size_bytes`| `INTEGER NOT NULL` (`Long`) | Ukuran file asli dalam byte. |
| `output_size_bytes` | `INTEGER NOT NULL` (`Long`) | Ukuran file hasil optimasi dalam byte. |
| `duration_ms` | `INTEGER` (`Long?`) | Durasi video dalam milidetik (`null` untuk foto). |
| `part_index` | `INTEGER NOT NULL` (`Int`) | Nomor urut potongan (`1` untuk file tunggal, `1..N` untuk *Auto-Split*). |
| `total_parts` | `INTEGER NOT NULL` (`Int`) | Total potongan dari satu proses *render*. |
| `created_at_epoch_ms`| `INTEGER NOT NULL` (`Long`) | Waktu selesai optimasi (diindeks secara menurun / `DESC`). |

### Sinkronisasi dengan Galeri HP:
Saat memuat daftar riwayat di `DashboardScreen`, aplikasi memverifikasi apakah `media_store_uri` masih ada di penyimpanan HP (untuk mengantisipasi jika pengguna menghapus file tersebut secara manual lewat aplikasi Galeri bawaan HP).

---

## 3. Preferensi Pengguna (`Jetpack DataStore`)

Menyimpan konfigurasi ringan (`zidee_preferences.pb` / `Preferences DataStore`):
- `last_selected_platform`: Platform terakhir yang dipilih pengguna di *Quick Share Sheet* (mempercepat penggunaan berulang).
- `last_selected_aspect_mode`: Mode rasio terakhir (`ORIGINAL`, `BLUR_BG_9_16`, `CROP_9_16`).
- `preferred_video_encoder`: `"CPU_LIBX264"` (default) atau `"GPU_MEDIACODEC"`.
- `default_sharpness_percent`: Nilai ketajaman pilihan pengguna jika dikustomisasi.
