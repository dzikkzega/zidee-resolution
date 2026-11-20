package com.zidee.ress.data.mediastore

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.ZideeAlbum
import com.zidee.ress.domain.repository.MediaStorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

data class PendingMediaRequest(
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val mediaType: MediaType,
)

interface MediaStoreGateway {
    fun insertPending(request: PendingMediaRequest): String

    fun openOutputStream(uri: String): OutputStream

    /**
     * Marks a pending MediaStore entry as published and returns the number of published rows.
     * Callers treat the save as successful only when exactly 1 row is published.
     */
    fun markCommitted(
        uri: String,
        displayName: String,
        mediaType: MediaType,
    ): Int

    fun scanIfNeeded(
        uri: String,
        displayName: String,
        mediaType: MediaType,
    )

    fun delete(uri: String)

    fun exists(uri: String): Boolean
}

class AndroidMediaStoreGateway(
    private val context: Context,
) : MediaStoreGateway {
    private val resolver: ContentResolver get() = context.contentResolver

    override fun insertPending(request: PendingMediaRequest): String {
        val collection = collectionUri(request.mediaType)
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, request.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, request.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, request.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    ensureLegacyStoragePermission()
                    val legacyFile = legacyTargetFile(request.mediaType, request.displayName, ensureDir = true)
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, legacyFile.absolutePath)
                }
            }
        val inserted =
            resolver.insert(collection, values)
                ?: throw IOException("MediaStore.insert returned null for ${request.displayName}")
        return inserted.toString()
    }

    override fun openOutputStream(uri: String): OutputStream =
        resolver.openOutputStream(Uri.parse(uri), "w")
            ?: throw IOException("Unable to open OutputStream for $uri")

    override fun markCommitted(
        uri: String,
        displayName: String,
        mediaType: MediaType,
    ): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
            resolver.update(Uri.parse(uri), values, null, null)
        } else {
            val legacyFile = legacyTargetFile(mediaType, displayName, ensureDir = false)
            if (legacyFile.isFile && legacyFile.length() > 0L && exists(uri)) 1 else 0
        }

    override fun scanIfNeeded(
        uri: String,
        displayName: String,
        mediaType: MediaType,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = legacyTargetFile(mediaType, displayName, ensureDir = false)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeTypeFor(mediaType)), null)
            return
        }
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        val filePath =
            try {
                resolver.query(Uri.parse(uri), projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (_: Throwable) {
                null
            }
        if (!filePath.isNullOrBlank()) {
            MediaScannerConnection.scanFile(context, arrayOf(filePath), arrayOf(mimeTypeFor(mediaType)), null)
        }
    }

    override fun delete(uri: String) {
        try {
            resolver.delete(Uri.parse(uri), null, null)
        } catch (_: Throwable) {
            // Best-effort rollback cleanup.
        }
    }

    override fun exists(uri: String): Boolean =
        try {
            resolver.query(Uri.parse(uri), arrayOf(BaseColumns._ID), null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    private fun ensureLegacyStoragePermission() {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            throw SecurityException("WRITE_EXTERNAL_STORAGE permission is required to save media on Android 8-9")
        }
    }

    private fun collectionUri(mediaType: MediaType): Uri =
        when (mediaType) {
            MediaType.PHOTO ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            MediaType.VIDEO ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
        }

    @Suppress("DEPRECATION")
    private fun legacyTargetFile(
        mediaType: MediaType,
        displayName: String,
        ensureDir: Boolean,
    ): File {
        val baseDir =
            when (mediaType) {
                MediaType.PHOTO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                MediaType.VIDEO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            }
        val albumDir = File(baseDir, ZideeAlbum.NAME)
        if (ensureDir && !albumDir.exists() && !albumDir.mkdirs()) {
            throw IOException("Unable to create album directory: ${albumDir.absolutePath}")
        }
        return File(albumDir, displayName)
    }

    private fun mimeTypeFor(mediaType: MediaType): String =
        when (mediaType) {
            MediaType.PHOTO -> "image/jpeg"
            MediaType.VIDEO -> "video/mp4"
        }
}

/**
 * Permanently writes optimized outputs to `Pictures/ZideeResolution` or `Movies/ZideeResolution`
 * using the transactional `IS_PENDING` protocol, rolling back on failure or cancellation.
 */
class ZideeMediaStoreSaver(
    private val gateway: MediaStoreGateway,
    private val dispatchers: DispatcherProvider,
) : MediaStorageRepository {
    constructor(
        context: Context,
        dispatchers: DispatcherProvider,
    ) : this(AndroidMediaStoreGateway(context), dispatchers)

    override suspend fun saveToGallery(
        staged: StagedOutput,
        displayName: String,
    ): AppResult<MediaUri> =
        withContext(dispatchers.io) {
            val stagedFile = File(staged.cachePath)
            var pendingUri: String? = null

            try {
                val safeName = sanitizeDisplayName(displayName)
                if (!stagedFile.isFile) {
                    return@withContext AppResult.Failure(
                        AppError.StorageWrite("Staged file does not exist: ${stagedFile.name}"),
                    )
                }

                val request =
                    PendingMediaRequest(
                        displayName = safeName,
                        mimeType =
                            when (staged.mediaType) {
                                MediaType.PHOTO -> "image/jpeg"
                                MediaType.VIDEO -> "video/mp4"
                            },
                        relativePath = ZideeAlbum.relativePathFor(staged.mediaType),
                        mediaType = staged.mediaType,
                    )
                val insertedUri = gateway.insertPending(request)
                pendingUri = insertedUri

                gateway.openOutputStream(insertedUri).use { out ->
                    FileInputStream(stagedFile).use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }

                val publishedRows = gateway.markCommitted(insertedUri, safeName, staged.mediaType)
                if (publishedRows != 1) {
                    throw IOException(
                        "MediaStore failed to publish pending row (updated=$publishedRows) for $insertedUri",
                    )
                }
                gateway.scanIfNeeded(insertedUri, safeName, staged.mediaType)
                AppResult.Success(MediaUri(insertedUri))
            } catch (e: CancellationException) {
                pendingUri?.let(gateway::delete)
                throw e
            } catch (e: SecurityException) {
                pendingUri?.let(gateway::delete)
                AppResult.Failure(AppError.PermissionDenied(e.message ?: "Storage permission denied", e))
            } catch (t: Throwable) {
                pendingUri?.let(gateway::delete)
                AppResult.Failure(AppError.StorageWrite(t.message ?: "Failed saving to ZideeResolution", t))
            } finally {
                deleteStagedAndEmptyParent(stagedFile)
            }
        }

    override suspend fun discardStaged(staged: StagedOutput) {
        withContext(dispatchers.io) {
            deleteStagedAndEmptyParent(File(staged.cachePath))
        }
    }

    override suspend fun exists(uri: MediaUri): Boolean =
        withContext(dispatchers.io) {
            gateway.exists(uri.value)
        }

    private fun deleteStagedAndEmptyParent(stagedFile: File) {
        stagedFile.delete()
        val parent = stagedFile.parentFile
        if (parent != null && parent.name.startsWith("job_") && parent.list().isNullOrEmpty()) {
            parent.delete()
        }
    }

    private fun sanitizeDisplayName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        require(base.isNotEmpty() && !base.contains("..")) { "Invalid output filename: $raw" }
        return base
    }
}
