package com.zidee.ress.engine.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.net.Uri
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.PhotoEncodingSpec
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.engine.StagingDirectoryCleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end offline photo optimization pipeline:
 * EXIF-aware region decode + sRGB normalization -> Lanczos-3/Bicubic geometry -> Unsharp Mask -> JPEG staging file.
 */
class PhotoProcessingPipeline(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val normalizer: ColorSpaceNormalizer = ColorSpaceNormalizer(context),
    private val stagingCleaner: StagingDirectoryCleaner = StagingDirectoryCleaner(context),
) {
    fun process(
        source: MediaUri,
        spec: OptimalMediaSpec,
    ): Flow<EngineProgress> =
        flow {
            val photoSpec = requireNotNull(spec.photo) { "PhotoProcessingPipeline requires photo spec" }
            val androidUri = Uri.parse(source.value)
            normalizer.ensureSupportedScheme(androidUri)

            emit(EngineProgress.Running(0.05f))
            currentCoroutineContext().ensureActive()

            val (canvasBuffer, decodedColorSpace) = decodeAndCompose(androidUri, spec, photoSpec)
            emit(EngineProgress.Running(0.70f))
            currentCoroutineContext().ensureActive()

            val sharpened = UnsharpMaskFilter.apply(canvasBuffer, amount = photoSpec.unsharpAmount)
            emit(EngineProgress.Running(0.88f))
            currentCoroutineContext().ensureActive()

            val stagingFile = stagingCleaner.createStagingFile(extension = "jpg")
            try {
                writeJpeg(sharpened, photoSpec.jpegQuality, photoSpec.convertToSrgb, decodedColorSpace, stagingFile)
                emit(EngineProgress.Running(1.0f))
                emit(
                    EngineProgress.Completed(
                        listOf(
                            StagedOutput(
                                cachePath = stagingFile.absolutePath,
                                mediaType = MediaType.PHOTO,
                                size = PixelSize(sharpened.width, sharpened.height),
                                sizeBytes = stagingFile.length(),
                                durationMs = null,
                                partIndex = 1,
                                totalParts = 1,
                            ),
                        ),
                    ),
                )
            } catch (e: CancellationException) {
                stagingFile.delete()
                throw e
            } catch (t: Throwable) {
                stagingFile.delete()
                throw t
            }
        }.flowOn(dispatchers.default)

    private fun decodeAndCompose(
        uri: Uri,
        spec: OptimalMediaSpec,
        photoSpec: PhotoEncodingSpec,
    ): Pair<PixelBuffer, ColorSpace?> {
        val cropGeometry = spec.geometry as? CanvasGeometry.Crop
        val decoded =
            normalizer.decodeAndNormalize(
                uri = uri,
                targetSize = spec.outputSize,
                convertToSrgb = photoSpec.convertToSrgb,
                orientedCropRegion = cropGeometry?.sourceRegion,
                cropReferenceSize = spec.sourceSize,
            )
        val canvasBuffer =
            when (val geometry = spec.geometry) {
                CanvasGeometry.Scale -> {
                    LanczosResampler.resample(decoded.buffer, spec.outputSize, photoSpec.scalingAlgorithm)
                }
                is CanvasGeometry.Crop -> {
                    if (decoded.alreadyCropped) {
                        LanczosResampler.resample(decoded.buffer, spec.outputSize, photoSpec.scalingAlgorithm)
                    } else {
                        val scaledRegion =
                            ExifRegionMapper.scaleRegion(
                                region = geometry.sourceRegion,
                                fromSize = spec.sourceSize,
                                toSize = PixelSize(decoded.buffer.width, decoded.buffer.height),
                            )
                        val cropped = LanczosResampler.crop(decoded.buffer, scaledRegion)
                        LanczosResampler.resample(cropped, spec.outputSize, photoSpec.scalingAlgorithm)
                    }
                }
                is CanvasGeometry.BlurBackground -> {
                    BlurBackgroundCompositor.compose(
                        source = decoded.buffer,
                        canvasSize = spec.outputSize,
                        foregroundRect = geometry.foreground,
                        algorithm = photoSpec.scalingAlgorithm,
                        blurRadiusPx = geometry.blurRadiusPx,
                    )
                }
            }
        return canvasBuffer to decoded.colorSpace
    }

    private fun writeJpeg(
        buffer: PixelBuffer,
        quality: Int,
        forceSrgb: Boolean,
        sourceColorSpace: ColorSpace?,
        outputFile: File,
    ) {
        val targetColorSpace =
            if (forceSrgb || sourceColorSpace == null) {
                ColorSpace.get(ColorSpace.Named.SRGB)
            } else {
                sourceColorSpace
            }
        val outBitmap =
            Bitmap.createBitmap(
                buffer.width,
                buffer.height,
                Bitmap.Config.ARGB_8888,
                false,
                targetColorSpace,
            )
        try {
            outBitmap.setPixels(buffer.pixels, 0, buffer.width, 0, 0, buffer.width, buffer.height)
            FileOutputStream(outputFile).use { fos ->
                check(outBitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)) {
                    "Bitmap.compress JPEG failed for ${outputFile.name}"
                }
            }
        } finally {
            if (!outBitmap.isRecycled) {
                outBitmap.recycle()
            }
        }
    }
}
