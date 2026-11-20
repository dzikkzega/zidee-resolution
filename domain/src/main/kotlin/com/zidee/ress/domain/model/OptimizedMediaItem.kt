package com.zidee.ress.domain.model

/** One file saved in the `ZideeResolution` album. Mirrors the `optimization_history` table. */
data class OptimizedMediaItem(
    val id: String,
    val mediaStoreUri: MediaUri,
    val displayName: String,
    val mediaType: MediaType,
    val platform: TargetPlatform,
    val aspectMode: CanvasAspectMode,
    val originalSize: PixelSize,
    val outputSize: PixelSize,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val durationMs: Long?,
    val partIndex: Int,
    val totalParts: Int,
    val createdAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(totalParts >= 1) { "totalParts must be >= 1" }
        require(partIndex in 1..totalParts) { "partIndex must be within 1..$totalParts, was $partIndex" }
        require(originalSizeBytes >= 0 && outputSizeBytes >= 0) { "byte sizes must be non-negative" }
    }
}

/** A rendered file sitting in app cache, waiting to be copied into MediaStore. */
data class StagedOutput(
    val cachePath: String,
    val mediaType: MediaType,
    val size: PixelSize,
    val sizeBytes: Long,
    val durationMs: Long?,
    val partIndex: Int,
    val totalParts: Int,
)
