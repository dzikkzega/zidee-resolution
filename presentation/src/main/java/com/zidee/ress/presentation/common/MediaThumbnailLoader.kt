@file:Suppress("MatchingDeclarationName")

package com.zidee.ress.presentation.common

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object MediaPreviewCache {
    private const val MAX_CACHE_KB = 16 * 1024

    private val bitmapCache =
        object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
        }

    fun get(key: String): Bitmap? = bitmapCache.get(key)

    fun put(
        key: String,
        bitmap: Bitmap,
    ) {
        if (!bitmap.isRecycled) {
            bitmapCache.put(key, bitmap)
        }
    }
}

@Composable
fun rememberMediaThumbnail(
    uri: String?,
    mediaType: MediaType?,
    maxDimensionPx: Int = 512,
): ImageBitmap? {
    val isInspection = LocalInspectionMode.current
    if (isInspection || uri.isNullOrBlank()) return null

    val context = LocalContext.current
    var imageBitmap by remember(uri, mediaType, maxDimensionPx) {
        val cached = MediaPreviewCache.get("$uri#$maxDimensionPx")?.asImageBitmap()
        mutableStateOf(cached)
    }

    LaunchedEffect(uri, mediaType, maxDimensionPx) {
        val cacheKey = "$uri#$maxDimensionPx"
        val cached = MediaPreviewCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            imageBitmap = cached.asImageBitmap()
            return@LaunchedEffect
        }
        val decoded =
            withContext(Dispatchers.IO) {
                loadSafePreviewBitmap(
                    context = context,
                    rawUri = uri,
                    mediaType = mediaType ?: MediaType.PHOTO,
                    maxDimensionPx = maxDimensionPx,
                )
            }
        if (decoded != null) {
            MediaPreviewCache.put(cacheKey, decoded)
            imageBitmap = decoded.asImageBitmap()
        }
    }
    return imageBitmap
}

fun resolveUriDisplayName(
    context: Context,
    uri: MediaUri,
): String {
    val parsed = Uri.parse(uri.value)
    if (parsed.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
    }
    return uri.extractDisplayName()
}

private fun loadSafePreviewBitmap(
    context: Context,
    rawUri: String,
    mediaType: MediaType,
    maxDimensionPx: Int,
): Bitmap? {
    val parsed = Uri.parse(rawUri)
    if (parsed.scheme != ContentResolver.SCHEME_CONTENT) return null
    return try {
        when (mediaType) {
            MediaType.VIDEO -> loadVideoFrame(context, parsed, maxDimensionPx)
            MediaType.PHOTO -> loadPhotoPreview(context, parsed, maxDimensionPx)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

private fun loadVideoFrame(
    context: Context,
    uri: Uri,
    maxDimensionPx: Int,
): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val raw = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        scaleDownIfNeeded(raw, maxDimensionPx)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun loadPhotoPreview(
    context: Context,
    uri: Uri,
    maxDimensionPx: Int,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && maxDimensionPx <= 512) {
        val quick =
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(maxDimensionPx, maxDimensionPx), null)
            }.getOrNull()
        if (quick != null) return quick
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > maxDimensionPx * 2 || bounds.outHeight / sample > maxDimensionPx * 2) {
        sample *= 2
    }

    val decodeOpts =
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    val decoded =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

    return scaleDownIfNeeded(decoded, maxDimensionPx)
}

private fun scaleDownIfNeeded(
    bitmap: Bitmap,
    maxDimensionPx: Int,
): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= maxDimensionPx || maxSide <= 0) return bitmap
    val scale = maxDimensionPx.toFloat() / maxSide.toFloat()
    val matrix = Matrix().apply { setScale(scale, scale) }
    val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (scaled !== bitmap) {
        bitmap.recycle()
    }
    return scaled
}
