package com.zidee.ress.presentation.share

import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.TargetPlatform

enum class DirectShareTarget(val packageName: String?) {
    WHATSAPP("com.whatsapp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b"),
    INSTAGRAM("com.instagram.android"),
    SYSTEM_CHOOSER(null),
    ;

    companion object {
        fun defaultFor(platform: TargetPlatform): DirectShareTarget =
            when (platform) {
                TargetPlatform.WA_STORY, TargetPlatform.WA_CHAT -> WHATSAPP
                TargetPlatform.IG_STORY, TargetPlatform.IG_POST, TargetPlatform.IG_CHAT -> INSTAGRAM
            }
    }
}

data class SharePayload(
    val uris: List<MediaUri>,
    val mimeType: String,
    val targetPackage: String?,
    val chooserTitle: String = "Bagikan lewat Zidee Ress",
) {
    init {
        require(uris.isNotEmpty()) { "SharePayload requires at least one URI" }
        require(mimeType.isNotBlank()) { "SharePayload requires a MIME type" }
    }

    companion object {
        fun fromItems(
            items: List<OptimizedMediaItem>,
            target: DirectShareTarget,
        ): SharePayload {
            require(items.isNotEmpty()) { "Cannot create SharePayload from empty items" }
            val hasPhoto = items.any { it.mediaType == MediaType.PHOTO }
            val hasVideo = items.any { it.mediaType == MediaType.VIDEO }
            val mimeType =
                when {
                    hasPhoto && hasVideo -> "*/*"
                    hasVideo -> "video/mp4"
                    else -> "image/jpeg"
                }
            return SharePayload(
                uris = items.map { it.mediaStoreUri },
                mimeType = mimeType,
                targetPackage = target.packageName,
            )
        }
    }
}
