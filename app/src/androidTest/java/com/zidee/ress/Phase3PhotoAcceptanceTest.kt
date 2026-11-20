package com.zidee.ress

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.zidee.ress.core.coroutines.DefaultDispatcherProvider
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.PhotoAdvancedConfig
import com.zidee.ress.domain.model.PhotoEncodingSpec
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.ScalingAlgorithm
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.engine.StagingDirectoryCleaner
import com.zidee.ress.engine.photo.ColorSpaceNormalizer
import com.zidee.ress.engine.photo.LanczosResampler
import com.zidee.ress.engine.photo.PhotoProcessingPipeline
import com.zidee.ress.engine.photo.PixelBuffer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class Phase3PhotoAcceptanceTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val calculate = CalculateOptimalSpecUseCase()

    @Test
    fun t31_largePhoto12000x9000CropUsesBoundedHeapAndMatchesFullDecodeCrop() =
        runBlocking {
            val workDir = File(checkNotNull(context.externalCacheDir), "phase3_photo_work").apply { mkdirs() }
            val hugeFile = File(workDir, "input_12000x9000_rot90.jpg")
            val report = StringBuilder()
            report.appendLine("=== T-3.1 Large Photo Crop & Memory Profile (${Build.MODEL}, Android ${Build.VERSION.RELEASE}) ===")

            try {
                val genSession =
                    FFmpegKit.executeWithArguments(
                        arrayOf(
                            "-y", "-f", "lavfi", "-i", "testsrc2=s=12000x9000:d=1",
                            "-frames:v", "1", "-q:v", "4", hugeFile.absolutePath,
                        ),
                    )
                assertTrue(
                    "Failed generating 12000x9000 JPEG: ${genSession.allLogsAsString}",
                    ReturnCode.isSuccess(genSession.returnCode) && hugeFile.length() > 0L,
                )
                ExifInterface(hugeFile.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                    saveAttributes()
                }

                val rawBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(hugeFile.absolutePath, rawBounds)
                assertEquals(12_000, rawBounds.outWidth)
                assertEquals(9_000, rawBounds.outHeight)
                val inputExif =
                    ExifInterface(hugeFile.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                assertEquals(ExifInterface.ORIENTATION_ROTATE_90, inputExif)

                val cropRect = PixelRect(left = 3_420, top = 4_080, width = 2_160, height = 3_840)
                val spec =
                    OptimalMediaSpec(
                        platform = TargetPlatform.WA_STORY,
                        aspectMode = CanvasAspectMode.CROP_9_16,
                        mediaType = MediaType.PHOTO,
                        sourceSize = PixelSize(9_000, 12_000),
                        outputSize = PixelSize(1_080, 1_920),
                        geometry = CanvasGeometry.Crop(cropRect),
                        photo =
                            PhotoEncodingSpec(
                                scalingAlgorithm = ScalingAlgorithm.LANCZOS3,
                                sharpnessPercent = 28,
                                unsharpAmount = 0.28,
                                jpegQuality = 94,
                                convertToSrgb = true,
                            ),
                        video = null,
                        estimatedSizeBytes = null,
                    )

                System.gc()
                Thread.sleep(100)
                val baselineJavaBytes = currentJavaHeapBytes()
                val baselineNativeBytes = Debug.getNativeHeapAllocatedSize()
                val peakJavaBytes = AtomicLong(baselineJavaBytes)
                val peakNativeBytes = AtomicLong(baselineNativeBytes)
                val sampling = AtomicBoolean(true)
                val samplerThread =
                    Thread {
                        while (sampling.get()) {
                            peakJavaBytes.accumulateAndGet(currentJavaHeapBytes(), ::maxOf)
                            peakNativeBytes.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
                            Thread.sleep(10)
                        }
                    }.apply { start() }

                val pipeline = PhotoProcessingPipeline(context, DefaultDispatcherProvider)
                val events =
                    try {
                        pipeline.process(MediaUri(Uri.fromFile(hugeFile).toString()), spec).toList()
                    } finally {
                        sampling.set(false)
                        samplerThread.join(1_000)
                    }

                val completed = events.filterIsInstance<EngineProgress.Completed>().single()
                val output: StagedOutput = completed.outputs.single()
                val outFile = File(output.cachePath)
                assertTrue("Output JPEG missing", outFile.isFile && outFile.length() > 0L)

                val outOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(outFile.absolutePath, outOpts)
                val outExif =
                    ExifInterface(outFile.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )

                val peakJavaMiB = peakJavaBytes.get() / (1024.0 * 1024.0)
                val deltaJavaMiB = (peakJavaBytes.get() - baselineJavaBytes) / (1024.0 * 1024.0)
                val peakNativeMiB = peakNativeBytes.get() / (1024.0 * 1024.0)
                val deltaNativeMiB = (peakNativeBytes.get() - baselineNativeBytes) / (1024.0 * 1024.0)

                report.appendLine("Input: 12000x9000 raw (108 MP), EXIF=ROTATE_90 (upright 9000x12000), fileBytes=${hugeFile.length()}")
                report.appendLine(
                    "Crop: left=${cropRect.left}, top=${cropRect.top}, " +
                        "w=${cropRect.width}, h=${cropRect.height} -> 1080x1920",
                )
                report.appendLine(
                    String.format(
                        Locale.US,
                        "JavaHeap: baseline=%.2f MiB, peak=%.2f MiB, delta=+%.2f MiB (old full-decode IntArray alone = 411.99 MiB)",
                        baselineJavaBytes / (1024.0 * 1024.0),
                        peakJavaMiB,
                        deltaJavaMiB,
                    ),
                )
                report.appendLine(
                    String.format(
                        Locale.US,
                        "NativeHeap: baseline=%.2f MiB, peak=%.2f MiB, delta=+%.2f MiB",
                        baselineNativeBytes / (1024.0 * 1024.0),
                        peakNativeMiB,
                        deltaNativeMiB,
                    ),
                )
                report.appendLine(
                    "Output JPEG: ${outOpts.outWidth}x${outOpts.outHeight}, bytes=${outFile.length()}, " +
                        "colorSpace=${outOpts.outColorSpace?.name}, exifOrientation=$outExif",
                )

                assertEquals(1_080, outOpts.outWidth)
                assertEquals(1_920, outOpts.outHeight)
                assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), outOpts.outColorSpace)
                assertTrue(
                    "Output EXIF must be upright (NORMAL or UNDEFINED), was $outExif",
                    outExif == ExifInterface.ORIENTATION_NORMAL || outExif == ExifInterface.ORIENTATION_UNDEFINED,
                )
                assertTrue(
                    "Peak Java heap delta ($deltaJavaMiB MiB) must stay far below 412 MiB full-decode allocation",
                    deltaJavaMiB < 180.0,
                )
                outFile.delete()

                verifyRegionCropEquivalence(workDir, report)
            } finally {
                workDir.deleteRecursively()
                StagingDirectoryCleaner(context).deleteOrphanedOlderThan(maxAgeMs = 0L)
            }

            saveTextEvidence("t31_large_photo_crop_report.txt", report.toString())
        }

    @Test
    fun t31_displayP3ExifPhotoConvertsToSrgbUprightAcrossAllModes() =
        runBlocking {
            val workDir = File(checkNotNull(context.externalCacheDir), "phase3_p3_work").apply { mkdirs() }
            val p3File = File(workDir, "input_display_p3_rot90.jpg")
            val report = StringBuilder()
            report.appendLine("=== T-3.1 Display P3 + EXIF Orientation -> sRGB Verification ===")

            try {
                val p3Space = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
                val p3Bmp = Bitmap.createBitmap(1_600, 1_200, Bitmap.Config.ARGB_8888, false, p3Space)
                try {
                    val canvas = Canvas(p3Bmp)
                    val paint = Paint()
                    paint.color = Color.rgb(255, 20, 20)
                    canvas.drawRect(0f, 0f, 800f, 1200f, paint)
                    paint.color = Color.rgb(20, 235, 60)
                    canvas.drawRect(800f, 0f, 1600f, 1200f, paint)
                    FileOutputStream(p3File).use { fos ->
                        check(p3Bmp.compress(Bitmap.CompressFormat.JPEG, 98, fos))
                    }
                } finally {
                    p3Bmp.recycle()
                }
                ExifInterface(p3File.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                    saveAttributes()
                }

                val inputOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(p3File.absolutePath, inputOpts)
                val inputOrientation =
                    ExifInterface(p3File.absolutePath).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                report.appendLine(
                    "Input: raw=${inputOpts.outWidth}x${inputOpts.outHeight}, " +
                        "colorSpace=${inputOpts.outColorSpace?.name}, exif=$inputOrientation",
                )
                assertEquals(p3Space, inputOpts.outColorSpace)
                assertEquals(ExifInterface.ORIENTATION_ROTATE_90, inputOrientation)

                val pipeline = PhotoProcessingPipeline(context, DefaultDispatcherProvider)
                val uri = MediaUri(Uri.fromFile(p3File).toString())
                val metadata =
                    MediaMetadata(
                        uri = uri,
                        mediaType = MediaType.PHOTO,
                        width = 1_200,
                        height = 1_600,
                        sizeBytes = p3File.length(),
                    )

                for (mode in listOf(CanvasAspectMode.ORIGINAL, CanvasAspectMode.CROP_9_16, CanvasAspectMode.BLUR_BG_9_16)) {
                    val spec =
                        calculate(
                            OptimizationRequest(
                                source = metadata,
                                platform = TargetPlatform.WA_STORY,
                                aspectMode = mode,
                                photoConfig = PhotoAdvancedConfig(autoSrgbFix = true),
                            ),
                        )
                    val events = pipeline.process(uri, spec).toList()
                    val staged = events.filterIsInstance<EngineProgress.Completed>().single().outputs.single()
                    val outFile = File(staged.cachePath)

                    val decodedOut = BitmapFactory.decodeFile(outFile.absolutePath)
                    assertNotNull("Failed decoding output JPEG for $mode", decodedOut)
                    try {
                        val outExif =
                            ExifInterface(outFile.absolutePath).getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL,
                            )
                        val topPixel = decodedOut.getPixel(decodedOut.width / 2, decodedOut.height / 4)
                        val bottomPixel = decodedOut.getPixel(decodedOut.width / 2, (decodedOut.height * 3) / 4)
                        report.appendLine(
                            "Mode=$mode -> ${decodedOut.width}x${decodedOut.height}, bytes=${outFile.length()}, " +
                                "colorSpace=${decodedOut.colorSpace?.name}, exif=$outExif, " +
                                "topRGB=(${Color.red(topPixel)},${Color.green(topPixel)},${Color.blue(topPixel)}), " +
                                "bottomRGB=(${Color.red(bottomPixel)},${Color.green(bottomPixel)},${Color.blue(bottomPixel)})",
                        )

                        assertEquals(spec.outputSize.width, decodedOut.width)
                        assertEquals(spec.outputSize.height, decodedOut.height)
                        assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), decodedOut.colorSpace)
                        assertTrue(
                            "Top pixel should be red-dominant after ROTATE_90 for $mode",
                            Color.red(topPixel) > Color.green(topPixel) + 80,
                        )
                        assertTrue(
                            "Bottom pixel should be green-dominant after ROTATE_90 for $mode",
                            Color.green(bottomPixel) > Color.red(bottomPixel) + 80,
                        )
                    } finally {
                        decodedOut.recycle()
                        outFile.delete()
                    }
                }
            } finally {
                workDir.deleteRecursively()
                StagingDirectoryCleaner(context).deleteOrphanedOlderThan(maxAgeMs = 0L)
            }

            saveTextEvidence("t31_display_p3_exif_report.txt", report.toString())
        }

    private fun verifyRegionCropEquivalence(
        workDir: File,
        report: StringBuilder,
    ) {
        val patternFile = File(workDir, "quadrant_pattern.jpg")
        val bmp = Bitmap.createBitmap(1_280, 960, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            val p = Paint()
            p.color = Color.rgb(220, 40, 40)
            c.drawRect(0f, 0f, 640f, 480f, p)
            p.color = Color.rgb(40, 200, 60)
            c.drawRect(640f, 0f, 1280f, 480f, p)
            p.color = Color.rgb(40, 80, 220)
            c.drawRect(0f, 480f, 640f, 960f, p)
            p.color = Color.rgb(235, 190, 30)
            c.drawRect(640f, 480f, 1280f, 960f, p)
            p.color = Color.WHITE
            c.drawRect(560f, 400f, 720f, 560f, p)
            FileOutputStream(patternFile).use { fos ->
                check(bmp.compress(Bitmap.CompressFormat.JPEG, 98, fos))
            }
        } finally {
            bmp.recycle()
        }

        val normalizer = ColorSpaceNormalizer(context)
        val orientations =
            listOf(
                ExifInterface.ORIENTATION_ROTATE_90 to "ROTATE_90",
                ExifInterface.ORIENTATION_ROTATE_180 to "ROTATE_180",
                ExifInterface.ORIENTATION_ROTATE_270 to "ROTATE_270",
            )
        for ((orientation, label) in orientations) {
            ExifInterface(patternFile.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            val uprightSize =
                if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    PixelSize(1_280, 960)
                } else {
                    PixelSize(960, 1_280)
                }
            val cropRect = PixelRect(left = 224, top = 192, width = 512, height = 512)
            val regionDecoded =
                normalizer.decodeAndNormalize(
                    uri = Uri.fromFile(patternFile),
                    targetSize = PixelSize(512, 512),
                    convertToSrgb = true,
                    orientedCropRegion = cropRect,
                    cropReferenceSize = uprightSize,
                )
            assertTrue("Expected BitmapRegionDecoder path (alreadyCropped=true) for $label", regionDecoded.alreadyCropped)

            val fullRef = fullDecodeOrientAndCrop(patternFile, orientation, cropRect)
            val meanDiff = meanRgbDiff(regionDecoded.buffer, fullRef)
            report.appendLine(
                String.format(
                    Locale.US,
                    "CropEquivalence EXIF=%s: region=%dx%d vs full=%dx%d, meanRgbDiff=%.3f (alreadyCropped=%s)",
                    label,
                    regionDecoded.buffer.width,
                    regionDecoded.buffer.height,
                    fullRef.width,
                    fullRef.height,
                    meanDiff,
                    regionDecoded.alreadyCropped,
                ),
            )
            assertEquals(fullRef.width, regionDecoded.buffer.width)
            assertEquals(fullRef.height, regionDecoded.buffer.height)
            assertTrue("Region decode crop diverged for $label (meanRgbDiff=$meanDiff)", meanDiff <= 2.5)
        }
    }

    private fun fullDecodeOrientAndCrop(
        file: File,
        orientation: Int,
        cropRect: PixelRect,
    ): PixelBuffer {
        val opts =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }
        val raw = checkNotNull(BitmapFactory.decodeFile(file.absolutePath, opts))
        val matrix =
            Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
        val oriented = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        return try {
            val pixels = IntArray(oriented.width * oriented.height)
            oriented.getPixels(pixels, 0, oriented.width, 0, 0, oriented.width, oriented.height)
            LanczosResampler.crop(PixelBuffer(oriented.width, oriented.height, pixels), cropRect)
        } finally {
            if (oriented !== raw) oriented.recycle()
            raw.recycle()
        }
    }

    private fun meanRgbDiff(
        a: PixelBuffer,
        b: PixelBuffer,
    ): Double {
        var sum = 0L
        val n = a.pixels.size
        for (i in 0 until n) {
            val pa = a.pixels[i]
            val pb = b.pixels[i]
            sum += abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
            sum += abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
            sum += abs((pa and 0xFF) - (pb and 0xFF))
        }
        return sum.toDouble() / (n * 3.0)
    }

    private fun currentJavaHeapBytes(): Long {
        val rt = Runtime.getRuntime()
        return rt.totalMemory() - rt.freeMemory()
    }

    private fun saveTextEvidence(
        fileName: String,
        content: String,
    ) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase3_evidence")
            }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }
}
