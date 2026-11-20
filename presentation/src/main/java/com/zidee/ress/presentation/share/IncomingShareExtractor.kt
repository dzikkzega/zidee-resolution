package com.zidee.ress.presentation.share

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.zidee.ress.domain.model.MediaUri
import java.util.Locale

/**
 * Extracts and validates external `content://` media URIs from incoming `ACTION_SEND` and
 * `ACTION_SEND_MULTIPLE` share intents while blocking confused-deputy `file://`, internal paths,
 * and non-media MIME types.
 */
object IncomingShareExtractor {
    private val ALLOWED_MEDIA_MIME_REGEX = Regex("^(image|video)/[a-z0-9][a-z0-9._+-]{0,126}$")

    fun extract(
        context: Context,
        intent: Intent?,
    ): List<MediaUri> {
        if (intent == null) return emptyList()
        val appDataDir = context.applicationInfo.dataDir
        val resolver = context.contentResolver
        val rawUris = extractRawUris(intent)
        val mimeFiltered =
            rawUris.filter { uri ->
                val actualMime = runCatching { resolver.getType(uri) }.getOrNull()
                isAllowedMediaMime(actualMime)
            }
        return filterSafeContentUris(
            candidates = mimeFiltered.map { Triple(it.scheme, it.path, it.toString()) },
            appDataDir = appDataDir,
        )
    }

    internal fun isAllowedMediaMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        val baseType = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (!ALLOWED_MEDIA_MIME_REGEX.matches(baseType)) return false
        val subtype = baseType.substringAfter('/')
        return !subtype.contains("..")
    }

    internal fun filterSafeContentUris(
        candidates: List<Triple<String?, String?, String>>,
        appDataDir: String,
    ): List<MediaUri> {
        val seen = LinkedHashSet<String>(candidates.size)
        val result = ArrayList<MediaUri>(candidates.size)
        for ((scheme, path, rawValue) in candidates) {
            if (!isSafeContentUri(scheme, path, appDataDir)) continue
            if (seen.add(rawValue)) {
                result += MediaUri(rawValue)
            }
        }
        return result
    }

    internal fun isSafeContentUri(
        scheme: String?,
        path: String?,
        appDataDir: String,
    ): Boolean {
        if (scheme?.lowercase(Locale.ROOT) != ContentResolver.SCHEME_CONTENT) return false
        if (path.isNullOrBlank() || path.contains("..")) return false
        val normalizedDir = appDataDir.trimEnd('/')
        if (normalizedDir.isNotBlank() && path.replace(Regex("/+"), "/").contains(normalizedDir)) return false
        return true
    }

    private fun extractRawUris(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                parcelableExtra(intent, Intent.EXTRA_STREAM)?.let(uris::add)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                parcelableArrayListExtra(intent, Intent.EXTRA_STREAM)?.let(uris::addAll)
            }
        }
        if (uris.isEmpty()) {
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let(uris::add)
                }
            }
        }
        return uris
    }

    @Suppress("DEPRECATION")
    private fun parcelableExtra(
        intent: Intent,
        key: String,
    ): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra(key)
        }

    @Suppress("DEPRECATION")
    private fun parcelableArrayListExtra(
        intent: Intent,
        key: String,
    ): ArrayList<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(key)
        }
}
