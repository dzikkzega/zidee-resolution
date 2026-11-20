# Security & Privacy Guide — Zidee Ress

Dokumen ini menetapkan standar keamanan aplikasi, perlindungan privasi pengguna, validasi input eksternal (*Share Intent*), dan konfigurasi rilis untuk **Zidee Ress**.

---

## 1. Jaminan Privasi 100% Offline (Zero Data Exfiltration)

- **Tanpa Izin Internet:** Zidee Ress memproses seluruh foto dan video secara lokal di perangkat pengguna. Aplikasi **tidak meminta** `android.permission.INTERNET`, sehingga secara teknis mustahil mengirim foto/video pribadi pengguna ke server mana pun.
- **Izin Penyimpanan Minimal (Scoped Storage):**
  - Menggunakan **Android Photo Picker** (`PickVisualMedia`) untuk memilih media dari dalam aplikasi tanpa meminta izin akses ke seluruh isi memori HP.
  - Penulisan ke folder `Pictures/ZideeResolution` dan `Movies/ZideeResolution` pada Android 10+ (API 29–35) menggunakan `MediaStore` yang tidak memerlukan izin `WRITE_EXTERNAL_STORAGE`.

---

## 2. Keamanan Penanganan URI Eksternal (`QuickShareActivity`)

Karena `QuickShareActivity` diekspos (`android:exported="true"`) untuk menerima `Intent.ACTION_SEND` dan `Intent.ACTION_SEND_MULTIPLE` dari aplikasi lain:

1. **Validasi Skema URI:**
   - Hanya terima URI dengan skema `content://`. Tolak skema `file://` yang mengarah ke direktori internal aplikasi sendiri (`context.filesDir` / `data/data/com.zidee.ress`) untuk mencegah serangan *confused deputy* (pencurian file internal aplikasi melalui *Share Loop*).
   ```kotlin
   fun isSafeExternalContentUri(context: Context, uri: Uri): Boolean {
       if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
       val canonicalAppDir = context.applicationInfo.dataDir
       val path = uri.path ?: return false
       return !path.contains(canonicalAppDir)
   }
   ```
2. **Validasi Tipe MIME:**
   - Periksa tipe MIME aktual melalui `contentResolver.getType(uri)` (hanya izinkan `image/*` dan `video/*`), jangan hanya mempercayai `intent.type`.
3. **Sanitasi Nama File:**
   - Jangan pernah menggunakan nama file asli dari URI eksternal secara mentah untuk membangun *path* file lokal (mencegah *Path Traversal* `../../`). Gunakan generator nama file deterministik: `ZIDEE_IMG_<timestamp>_<platform>.jpg`.

---

## 3. Keamanan Eksekusi Argumen FFmpeg

- Jangan pernah menggabungkan string mentah dari metadata file (seperti judul video atau tag EXIF) ke dalam perintah shell.
- Gunakan *array of arguments* (`Array<String>`) atau *file descriptor* (`saf_protocol` / file *staging* dengan nama aman di `cacheDir`) saat memanggil engine FFmpeg.

---

## 4. Cakupan Izin Saat Meneruskan ke WhatsApp / Instagram

Saat meneruskan file hasil dari `ZideeResolution` ke WhatsApp (`com.whatsapp`) atau Instagram (`com.instagram.android`):
- Berikan izin baca sementara hanya pada URI file hasil menggunakan `Intent.FLAG_GRANT_READ_URI_PERMISSION` dan `ClipData.newRawUri`.
- Jangan pernah memberikan `FLAG_GRANT_WRITE_URI_PERMISSION` kepada aplikasi pihak ketiga.

---

## 5. Aturan R8 / ProGuard pada Build Release

- Aktifkan `isMinifyEnabled = true` dan `isShrinkResources = true` pada build `release` agar ukuran APK tetap ramping dan kode terlindungi.
- Pertahankan kelas entitas `Room` (`@Keep` pada `OptimizationHistoryEntity`) dan jembatan JNI/NDK untuk FFmpeg/OpenCV di `proguard-rules.pro`.
