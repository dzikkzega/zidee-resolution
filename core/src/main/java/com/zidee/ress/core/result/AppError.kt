package com.zidee.ress.core.result

/**
 * Closed hierarchy of failures that can cross layer boundaries.
 *
 * Every error carries a developer-facing [message]. User-facing copy is mapped in
 * `:presentation`, never here.
 */
sealed interface AppError {
    val message: String
    val cause: Throwable?

    /** Caller supplied a value outside the documented contract. */
    data class InvalidInput(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** Source media has a MIME type, codec, or container the engine cannot read. */
    data class UnsupportedMedia(
        val mimeType: String?,
        override val message: String = "Unsupported media type: ${mimeType ?: "unknown"}",
        override val cause: Throwable? = null,
    ) : AppError

    /** Required runtime permission is missing or the URI grant was revoked. */
    data class PermissionDenied(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** Device has too little free space to stage or save the output. */
    data class InsufficientStorage(
        val requiredBytes: Long?,
        override val message: String = "Not enough free storage",
        override val cause: Throwable? = null,
    ) : AppError

    /** Writing to MediaStore, cache, or the database failed. */
    data class StorageWrite(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** Photo or video pipeline failed while decoding, filtering, or encoding. */
    data class Processing(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** Anything not covered above. Prefer adding a specific case over using this. */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError
}
