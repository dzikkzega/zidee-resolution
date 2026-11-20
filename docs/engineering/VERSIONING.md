# Versioning Zidee Ress

Versi awal: `v1.0.0`, `versionCode = 1`. Di `gradle/libs.versions.toml`, naikkan `appVersionCode` setiap rilis. Gunakan `appVersionName` MAJOR.MINOR.PATCH untuk perubahan tidak kompatibel, fitur, atau perbaikan. Tag Git: `v` diikuti versionName.

Bangun APK release dengan sertifikat yang sama agar pembaruan tidak memerlukan uninstall. Jangan commit keystore. Sejak v1.0.1, unggah `Zidee-Ress-vX.Y.Z-arm64-v8a.apk` dan `Zidee-Ress-vX.Y.Z-x86_64.apk` ke draft release pada tag terkait. Jalankan Actions `Publish signed APKs` dengan tag dan SHA-256 masing-masing APK. Workflow memverifikasi checksum dan sertifikat sebelum publikasi.

Pemrosesan tetap offline; pembaruan opsional. Release privat tidak otomatis menyediakan feed versi. Unduhan hanya untuk akun dengan akses repo.
