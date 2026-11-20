package com.zidee.ress.presentation.share

import com.zidee.ress.core.result.AppError
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.presentation.common.toUserMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectShareRouterAndExtractorTest {
    @Test
    fun `router picks whatsapp when installed, falls back to whatsapp business, then chooser`() {
        val bothInstalled = FakePackageChecker(setOf("com.whatsapp", "com.whatsapp.w4b", "com.instagram.android"))
        val businessOnly = FakePackageChecker(setOf("com.whatsapp.w4b"))
        val noneInstalled = FakePackageChecker(emptySet())

        assertEquals(
            "com.whatsapp",
            DirectShareIntentRouter(bothInstalled).resolveTargetPackage("com.whatsapp"),
        )
        assertEquals(
            "com.whatsapp.w4b",
            DirectShareIntentRouter(businessOnly).resolveTargetPackage("com.whatsapp"),
        )
        assertNull(
            DirectShareIntentRouter(noneInstalled).resolveTargetPackage("com.whatsapp"),
        )
        assertEquals(
            "com.instagram.android",
            DirectShareIntentRouter(bothInstalled).resolveTargetPackage("com.instagram.android"),
        )
        assertNull(
            DirectShareIntentRouter(bothInstalled).resolveTargetPackage(null),
        )
    }

    @Test
    fun `share payload derives exact mimeType for photo, video, and mixed batches`() {
        val photo = item("1", MediaType.PHOTO, TargetPlatform.IG_POST)
        val video = item("2", MediaType.VIDEO, TargetPlatform.WA_STORY)

        val photoPayload = SharePayload.fromItems(listOf(photo), DirectShareTarget.defaultFor(TargetPlatform.IG_POST))
        val videoPayload = SharePayload.fromItems(listOf(video), DirectShareTarget.defaultFor(TargetPlatform.WA_STORY))
        val mixedPayload = SharePayload.fromItems(listOf(photo, video), DirectShareTarget.SYSTEM_CHOOSER)

        assertEquals("image/jpeg", photoPayload.mimeType)
        assertEquals("com.instagram.android", photoPayload.targetPackage)
        assertEquals("video/mp4", videoPayload.mimeType)
        assertEquals("com.whatsapp", videoPayload.targetPackage)
        assertEquals("*/*", mixedPayload.mimeType)
        assertNull(mixedPayload.targetPackage)
    }

    @Test
    fun `incoming share extractor keeps unique content URIs and drops unsafe inputs`() {
        val duplicateUri = Triple("content", "/external/images/1", "content://media/external/images/1")
        val candidates =
            listOf(
                duplicateUri,
                duplicateUri,
                Triple("file", "/storage/emulated/0/photo.jpg", "file:///storage/emulated/0/photo.jpg"),
                Triple("http", "/evil.jpg", "http://example.com/evil.jpg"),
                Triple("content", "/data/user/0/com.zidee.ress/databases/db", "content://leak/data/user/0/com.zidee.ress/databases/db"),
                Triple("content", "/external/../secret", "content://media/external/../secret"),
            )

        val extracted =
            IncomingShareExtractor.filterSafeContentUris(
                candidates = candidates,
                appDataDir = "/data/user/0/com.zidee.ress",
            )

        assertEquals(listOf(MediaUri("content://media/external/images/1")), extracted)
    }

    @Test
    fun `incoming share extractor permits valid image and video MIMEs and rejects empty or invalid subtypes`() {
        val allowed =
            listOf(
                "image/jpeg",
                "IMAGE/PNG",
                "image/webp",
                "image/heic",
                "image/heif",
                "image/avif",
                "image/jpeg; charset=binary",
                "  image/png ; q=0.9 ",
                "video/mp4",
                "video/quicktime",
                "video/3gpp",
                "video/x-matroska",
                "video/mp4; codecs=\"avc1.42E01E\"",
            )
        for (mime in allowed) {
            assertTrue("Expected valid media MIME to be allowed: '$mime'", IncomingShareExtractor.isAllowedMediaMime(mime))
        }

        val rejected =
            listOf(
                null,
                "",
                "   ",
                "image",
                "video",
                "image/",
                "video/",
                "image/ ",
                "video/   ",
                "image/; charset=utf-8",
                "video/; codecs=h264",
                "image/*",
                "video/*",
                "image/*; q=0.8",
                "video/*; q=0.8",
                "image/jpeg/extra",
                "image/foo bar",
                "image/-bad",
                "image/..",
                "image/a..b",
                "image/a*b",
                "text/plain",
                "audio/mpeg",
                "application/pdf",
                "application/octet-stream",
            )
        for (mime in rejected) {
            assertFalse("Expected invalid or non-media MIME to be rejected: '$mime'", IncomingShareExtractor.isAllowedMediaMime(mime))
        }
    }

    @Test
    fun `error mapper produces readable messages for every AppError variant`() {
        val errors =
            listOf(
                AppError.InvalidInput("bad"),
                AppError.UnsupportedMedia("video/avi"),
                AppError.PermissionDenied("revoked"),
                AppError.InsufficientStorage(50_000_000L),
                AppError.InsufficientStorage(null),
                AppError.StorageWrite("io"),
                AppError.Processing("ffmpeg"),
                AppError.Unknown("other"),
            )
        for (err in errors) {
            assertTrue(err.toUserMessage().isNotBlank())
        }
    }

    private fun item(
        id: String,
        type: MediaType,
        platform: TargetPlatform,
    ) = OptimizedMediaItem(
        id = id,
        mediaStoreUri = MediaUri("content://media/external/images/$id"),
        displayName = "ZIDEE_$id",
        mediaType = type,
        platform = platform,
        aspectMode = CanvasAspectMode.ORIGINAL,
        originalSize = PixelSize(1920, 1080),
        outputSize = PixelSize(1080, 608),
        originalSizeBytes = 1000,
        outputSizeBytes = 500,
        durationMs = null,
        partIndex = 1,
        totalParts = 1,
        createdAtEpochMs = 1L,
    )

    private class FakePackageChecker(private val installed: Set<String>) : PackageAvailabilityChecker {
        override fun isPackageAvailable(packageName: String): Boolean = packageName in installed
    }
}
