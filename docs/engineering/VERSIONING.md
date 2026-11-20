# Versioning Zidee Ress

Versi awal: `v1.0.0`, `versionCode = 1`. Di `gradle/libs.versions.toml`, naikkan `appVersionCode` setiap rilis. Gunakan `appVersionName` MAJOR.MINOR.PATCH untuk perubahan tidak kompatibel, fitur, atau perbaikan. Tag Git: `v` diikuti versionName.

Bangun APK release dengan sertifikat yang sama agar pembaruan tidak memerlukan uninstall. Jangan commit keystore. Unggah `Zidee-Ress-vX.Y.Z.apk` ke draft release pada tag terkait. Jalankan Actions `Publish signed APK` dengan tag dan SHA-256 APK. Workflow memverifikasi checksum dan sertifikat sebelum publikasi.

Pemrosesan tetap offline; pembaruan opsional. Release privat tidak otomatis menyediakan feed versi. Unduhan hanya untuk akun dengan akses repo.
