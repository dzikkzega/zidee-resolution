package com.zidee.ress

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.presentation.share.DirectShareIntentRouter
import com.zidee.ress.presentation.share.DirectShareTarget
import com.zidee.ress.presentation.share.IncomingShareExtractor
import com.zidee.ress.presentation.share.PackageAvailabilityChecker
import com.zidee.ress.presentation.share.SharePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class Phase6QuickShareAcceptanceTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun p6_incomingShareExtractorAndDirectShareRouterVerifyOnDevice() {
        val workDir = File(checkNotNull(context.externalCacheDir), "phase6_share_work").apply { mkdirs() }
        val tempUris = mutableListOf<Uri>()
        val report = StringBuilder()
        report.appendLine("=== Phase 6 Quick Share & Direct Share Device Verification on ${Build.MODEL} (API ${Build.VERSION.SDK_INT}) ===")

        try {
            val photoFile = createTinyPhoto(File(workDir, "p6_photo.jpg"))
            val photoUri1 = insertInputIntoMediaStore(context, photoFile, MediaType.PHOTO).also { tempUris += it }
            val photoUri2 = insertInputIntoMediaStore(context, photoFile, MediaType.PHOTO).also { tempUris += it }
            val textUri = insertPlainTextDocument(context).also { tempUris += it }

            val singlePhotoIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, photoUri1)
                }
            val singleExtracted = IncomingShareExtractor.extract(context, singlePhotoIntent)
            assertEquals(1, singleExtracted.size)
            assertEquals(photoUri1.toString(), singleExtracted.first().value)
            report.appendLine("ACTION_SEND valid photo: extracted=${singleExtracted.size} uri=${singleExtracted.first().value}")

            val mixedIntent =
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        arrayListOf(photoUri1, photoUri2, textUri, Uri.fromFile(photoFile)),
                    )
                }
            val mixedExtracted = IncomingShareExtractor.extract(context, mixedIntent)
            assertEquals(2, mixedExtracted.size)
            report.appendLine("ACTION_SEND_MULTIPLE mixed (2 valid photos + 1 text/plain + 1 file://): accepted=${mixedExtracted.size}")

            val textOnlyIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/"
                    putExtra(Intent.EXTRA_STREAM, textUri)
                }
            val textExtracted = IncomingShareExtractor.extract(context, textOnlyIntent)
            assertTrue(textExtracted.isEmpty())
            report.appendLine("ACTION_SEND disguised text/plain with 'image/' intent type: accepted=${textExtracted.size} (rejected)")

            val deviceRouter = DirectShareIntentRouter(context)
            val payload =
                SharePayload(
                    uris = listOf(MediaUri(photoUri1.toString())),
                    mimeType = "image/jpeg",
                    targetPackage = DirectShareTarget.WHATSAPP.packageName,
                )
            val (deviceShareIntent, resolvedPkg) = deviceRouter.createShareIntent(payload)
            assertTrue((deviceShareIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            if (resolvedPkg != null) {
                assertEquals(Intent.ACTION_SEND, deviceShareIntent.action)
                assertNotNull(deviceShareIntent.clipData)
            } else {
                assertEquals(Intent.ACTION_CHOOSER, deviceShareIntent.action)
            }
            report.appendLine(
                "Device DirectShareIntentRouter: action=${deviceShareIntent.action} grantRead=true resolvedPkg=$resolvedPkg",
            )

            val allTargetsRouter =
                DirectShareIntentRouter(
                    object : PackageAvailabilityChecker {
                        override fun isPackageAvailable(packageName: String): Boolean =
                            packageName in setOf("com.whatsapp", "com.whatsapp.w4b", "com.instagram.android")
                    },
                )
            val (waIntent, waPkg) = allTargetsRouter.createShareIntent(payload)
            assertEquals("com.whatsapp", waPkg)
            assertEquals("com.whatsapp", waIntent.`package`)
            assertEquals(Intent.ACTION_SEND, waIntent.action)
            assertTrue((waIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            assertNotNull(waIntent.clipData)

            val (igIntent, igPkg) =
                allTargetsRouter.createShareIntent(
                    SharePayload(
                        uris = listOf(MediaUri(photoUri1.toString()), MediaUri(photoUri2.toString())),
                        mimeType = "image/jpeg",
                        targetPackage = DirectShareTarget.INSTAGRAM.packageName,
                    ),
                )
            assertEquals("com.instagram.android", igPkg)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, igIntent.action)
            assertEquals(2, igIntent.clipData?.itemCount)
            assertTrue((igIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

            val (chooserIntent, chooserPkg) =
                deviceRouter.createShareIntent(
                    SharePayload(
                        uris = listOf(MediaUri(photoUri1.toString()), MediaUri(photoUri2.toString())),
                        mimeType = "image/jpeg",
                        targetPackage = null,
                    ),
                )
            assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
            assertNull(chooserPkg)
            assertTrue((chooserIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            report.appendLine("Direct targets verified: WA=$waPkg (SEND), IG=$igPkg (SEND_MULTIPLE clip=2), Chooser=ACTION_CHOOSER")
        } finally {
            tempUris.forEach { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
            workDir.deleteRecursively()
        }

        savePhase6TextEvidence(context, "p6_quickshare_device_report.txt", report.toString())
    }

    private fun createTinyPhoto(target: File): File {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(245, 158, 11))
        FileOutputStream(target).use { fos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        }
        bmp.recycle()
        return target
    }

    private fun insertPlainTextDocument(context: Context): Uri {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "p6_non_media_${System.currentTimeMillis()}.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase6_tmp")
            }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)?.use { it.write("not a media file".toByteArray(Charsets.UTF_8)) }
        return uri
    }
}

private fun savePhase6TextEvidence(
    context: Context,
    fileName: String,
    content: String,
) {
    val resolver = context.contentResolver
    val values =
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase6_evidence")
        }
    val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
}
