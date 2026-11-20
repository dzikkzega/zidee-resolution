package com.zidee.ress.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

/**
 * Guards external Share Intents (`ACTION_SEND`) against confused-deputy attacks and
 * unsupported MIME payloads as specified in SECURITY_GUIDE §2.
 */
object ExternalUriValidator {
    fun isSafeExternalContentUri(
        context: Context,
        uri: Uri,
    ): Boolean = isSafeUri(uri.scheme, uri.path, context.applicationInfo.dataDir)

    internal fun isSafeUri(
        scheme: String?,
        path: String?,
        appDataDir: String,
    ): Boolean {
        if (scheme != ContentResolver.SCHEME_CONTENT) return false
        if (path.isNullOrBlank()) return false
        if (path.contains("..")) return false
        if (appDataDir.isNotBlank() && path.contains(appDataDir)) return false
        return true
    }

    fun isSupportedMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        return mimeType.startsWith("image/") || mimeType.startsWith("video/")
    }
}
