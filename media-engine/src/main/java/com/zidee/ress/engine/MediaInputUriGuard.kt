package com.zidee.ress.engine

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.zidee.ress.core.result.AppError

/**
 * Validates source URIs inside `:media-engine` against confused-deputy attacks and blocks
 * non-offline network schemes (`http`, `https`, `rtsp`, etc.).
 */
internal object MediaInputUriGuard {
    fun ensureSafeInputUri(
        context: Context,
        uri: Uri,
    ) {
        ensureSafeInputUri(
            scheme = uri.scheme,
            path = uri.path,
            appDataDir = context.applicationInfo.dataDir,
        )
    }

    internal fun ensureSafeInputUri(
        scheme: String?,
        path: String?,
        appDataDir: String,
    ) {
        val normalizedScheme = scheme?.lowercase()
        val supportedScheme =
            normalizedScheme == ContentResolver.SCHEME_CONTENT ||
                normalizedScheme == ContentResolver.SCHEME_FILE
        if (!supportedScheme) {
            throw IllegalArgumentException(
                AppError.UnsupportedMedia(null, "Unsupported URI scheme: $scheme").message,
            )
        }
        val normalizedPath = path?.replace(Regex("/+"), "/")
        val normalizedAppData = appDataDir.trimEnd('/')
        val securityViolation =
            when {
                normalizedPath.isNullOrBlank() || normalizedPath.contains("..") ->
                    "Rejected unsafe input URI path"
                normalizedAppData.isNotBlank() && normalizedPath.contains(normalizedAppData) ->
                    "Rejected URI targeting internal app directory"
                else -> null
            }
        if (securityViolation != null) {
            throw SecurityException(securityViolation)
        }
    }
}
