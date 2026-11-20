package com.zidee.ress.data.mediastore

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.repository.MediaMetadataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Extracts dimensions, EXIF rotation, wide-gamut flag, video FPS, duration, and HDR transfer
 * metadata from gallery URIs on `Dispatchers.IO`.
 */
class MediaMetadataReader(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : MediaMetadataRepository {
    override suspend fun inspect(uri: MediaUri): AppResult<MediaMetadata> =
        withContext(dispatchers.io) {
            val androidUri = Uri.parse(uri.value)
            if (!ExternalUriValidator.isSafeExternalContentUri(context, androidUri)) {
                return@withContext AppResult.Failure(
                    AppError.PermissionDenied("Blocked unsafe or non-content URI scheme: ${androidUri.scheme}"),
                )
            }

            val resolver = context.contentResolver
            val mimeType = resolver.getType(androidUri)
            if (!ExternalUriValidator.isSupportedMime(mimeType)) {
                return@withContext AppResult.Failure(AppError.UnsupportedMedia(mimeType))
            }

            try {
                val sizeBytes = querySizeBytes(androidUri)
                val metadata =
                    if (mimeType?.startsWith("video/") == true) {
                        readVideoMetadata(uri, androidUri, mimeType, sizeBytes)
                    } else {
                        readPhotoMetadata(uri, androidUri, mimeType, sizeBytes)
                    }
                AppResult.Success(metadata)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                AppResult.Failure(AppError.PermissionDenied("Read permission denied for source media", e))
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Processing(t.message ?: "Failed reading source media metadata", t))
            }
        }

    private fun readPhotoMetadata(
        uri: MediaUri,
        androidUri: Uri,
        mimeType: String?,
        sizeBytes: Long,
    ): MediaMetadata {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(androidUri) ?: throw IOException("Unable to open image stream")
        boundsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) {
            throw IOException("Image bounds could not be decoded")
        }

        val rotation =
            resolver.openInputStream(androidUri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
            } ?: 0

        val wideGamut = options.outColorSpace?.isWideGamut == true

        return MediaMetadata(
            uri = uri,
            mediaType = MediaType.PHOTO,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            rotationDegrees = rotation,
            mimeType = mimeType,
            isWideGamut = wideGamut,
        )
    }

    private fun readVideoMetadata(
        uri: MediaUri,
        androidUri: Uri,
        mimeType: String?,
        sizeBytes: Long,
    ): MediaMetadata {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            retriever.setDataSource(context, androidUri)
            val width =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    ?: throw IOException("Missing video width metadata")
            val height =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    ?: throw IOException("Missing video height metadata")
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val retrieverFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()

            val (extractorFps, extractorHdr) = extractVideoTrackHints(extractor, androidUri)
            val isHdr = extractorHdr || isHdrFromRetriever(retriever)
            val resolvedFps = (extractorFps ?: retrieverFps)?.takeIf { it > 0.0 }

            return MediaMetadata(
                uri = uri,
                mediaType = MediaType.VIDEO,
                width = width,
                height = height,
                sizeBytes = sizeBytes,
                rotationDegrees = rotation,
                mimeType = mimeType,
                durationMs = durationMs,
                frameRate = resolvedFps,
                isHdr = isHdr,
            )
        } finally {
            runCatching { retriever.release() }
            runCatching { extractor.release() }
        }
    }

    private fun extractVideoTrackHints(
        extractor: MediaExtractor,
        androidUri: Uri,
    ): Pair<Double?, Boolean> =
        try {
            extractor.setDataSource(context, androidUri, null)
            val videoFormat =
                (0 until extractor.trackCount)
                    .asSequence()
                    .map { extractor.getTrackFormat(it) }
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }

            val fps =
                if (videoFormat != null && videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                } else {
                    null
                }
            val hdr =
                if (videoFormat != null && videoFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    isHdrTransfer(videoFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER))
                } else {
                    false
                }
            fps to hdr
        } catch (_: Throwable) {
            // MediaExtractor hints are best-effort; MediaMetadataRetriever remains primary.
            null to false
        }

    private fun isHdrFromRetriever(retriever: MediaMetadataRetriever): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
        return transfer != null && isHdrTransfer(transfer)
    }

    private fun isHdrTransfer(transfer: Int): Boolean =
        transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG

    private fun querySizeBytes(uri: Uri): Long {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) {
                    val value = cursor.getLong(idx)
                    if (value >= 0L) return value
                }
            }
        }
        return try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.takeIf { it >= 0L } ?: 0L
            } ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }
}
