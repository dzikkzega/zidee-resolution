package com.zidee.ress.presentation.common

import com.zidee.ress.core.result.AppError

/** Maps internal [AppError] instances to concise Indonesian copy for presentation state. */
fun AppError.toUserMessage(): String =
    when (this) {
        is AppError.InvalidInput -> "Input media tidak valid: $message"
        is AppError.UnsupportedMedia -> "Format media tidak didukung (${mimeType ?: "tidak diketahui"})."
        is AppError.PermissionDenied -> "Izin akses ke file media ditolak atau kedaluwarsa."
        is AppError.InsufficientStorage -> {
            val bytes = requiredBytes
            if (bytes != null && bytes > 0L) {
                val requiredMb = (bytes / (1_024L * 1_024L)).coerceAtLeast(1L)
                "Ruang penyimpanan penuh. Minimal dibutuhkan ~$requiredMb MB ruang kosong."
            } else {
                "Ruang penyimpanan perangkat tidak mencukupi untuk menyimpan ke ZideeResolution."
            }
        }
        is AppError.StorageWrite -> "Gagal menyimpan file ke album Galeri ZideeResolution."
        is AppError.Processing -> "Pemrosesan media gagal dijalankan di perangkat."
        is AppError.Unknown -> "Terjadi kendala tak terduga saat memproses media."
    }
