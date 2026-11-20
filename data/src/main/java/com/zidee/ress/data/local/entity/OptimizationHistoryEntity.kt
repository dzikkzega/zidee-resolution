package com.zidee.ress.data.local.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "optimization_history",
    indices = [
        Index(value = ["created_at_epoch_ms"], name = "idx_history_created_at"),
    ],
)
data class OptimizationHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "media_store_uri")
    val mediaStoreUri: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "media_type")
    val mediaType: String,
    @ColumnInfo(name = "target_platform")
    val targetPlatform: String,
    @ColumnInfo(name = "aspect_mode")
    val aspectMode: String,
    @ColumnInfo(name = "original_width")
    val originalWidth: Int,
    @ColumnInfo(name = "original_height")
    val originalHeight: Int,
    @ColumnInfo(name = "output_width")
    val outputWidth: Int,
    @ColumnInfo(name = "output_height")
    val outputHeight: Int,
    @ColumnInfo(name = "original_size_bytes")
    val originalSizeBytes: Long,
    @ColumnInfo(name = "output_size_bytes")
    val outputSizeBytes: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    @ColumnInfo(name = "part_index")
    val partIndex: Int,
    @ColumnInfo(name = "total_parts")
    val totalParts: Int,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
)
