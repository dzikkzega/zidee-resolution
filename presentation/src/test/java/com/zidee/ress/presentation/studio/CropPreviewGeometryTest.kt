package com.zidee.ress.presentation.studio

import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.CropAdjustment
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropPreviewGeometryTest {
    private val calculateSpec = CalculateOptimalSpecUseCase()
    private val landscapePhoto =
        MediaMetadata(
            uri = MediaUri("content://gallery/photo/landscape"),
            mediaType = MediaType.PHOTO,
            width = 4000,
            height = 3000,
            sizeBytes = 6_000_000L,
        )

    @Test
    fun `zoom 1x panX left center right maps 1 to 1 with CalculateOptimalSpecUseCase`() {
        val bitmapSize = PixelSize(960, 720)
        val leftSpec = specFor(CropAdjustment(panX = 0.0, panY = 0.5, zoom = 1.0))
        val centerSpec = specFor(CropAdjustment(panX = 0.5, panY = 0.5, zoom = 1.0))
        val rightSpec = specFor(CropAdjustment(panX = 1.0, panY = 0.5, zoom = 1.0))

        val leftRects = CropPreviewGeometry.resolveCropDrawRects(bitmapSize, 540, 960, leftSpec, CropAdjustment(0.0, 0.5, 1.0))
        val centerRects = CropPreviewGeometry.resolveCropDrawRects(bitmapSize, 540, 960, centerSpec, CropAdjustment(0.5, 0.5, 1.0))
        val rightRects = CropPreviewGeometry.resolveCropDrawRects(bitmapSize, 540, 960, rightSpec, CropAdjustment(1.0, 0.5, 1.0))

        assertEquals(0, leftRects.srcLeft)
        assertTrue(centerRects.srcLeft > leftRects.srcLeft)
        assertTrue(rightRects.srcLeft > centerRects.srcLeft)
        assertEquals(bitmapSize.width, rightRects.srcLeft + rightRects.srcWidth)
        assertEquals(leftRects.srcWidth, centerRects.srcWidth)
        assertEquals(centerRects.srcWidth, rightRects.srcWidth)

        val exportLeft = (leftSpec.geometry as CanvasGeometry.Crop).sourceRegion
        val exportCenter = (centerSpec.geometry as CanvasGeometry.Crop).sourceRegion
        val exportRight = (rightSpec.geometry as CanvasGeometry.Crop).sourceRegion
        assertEquals(exportLeft.left.toDouble() / 4000.0, leftRects.srcLeft.toDouble() / 960.0, 0.003)
        assertEquals(exportCenter.left.toDouble() / 4000.0, centerRects.srcLeft.toDouble() / 960.0, 0.003)
        assertEquals(exportRight.left.toDouble() / 4000.0, rightRects.srcLeft.toDouble() / 960.0, 0.003)
    }

    @Test
    fun `zoom 2x matches export geometry across two screen densities without dp px drift`() {
        val crop = CropAdjustment(panX = 1.0, panY = 0.25, zoom = 2.0)
        val spec = specFor(crop)
        val exportRegion = (spec.geometry as CanvasGeometry.Crop).sourceRegion
        val bitmapSize = PixelSize(960, 720)

        // Same logical 200x360 dp container rendered at 2.0x density (400x720 px) vs 3.0x density (600x1080 px)
        val atDensity2x = CropPreviewGeometry.resolveCropDrawRects(bitmapSize, 400, 720, spec, crop)
        val atDensity3x = CropPreviewGeometry.resolveCropDrawRects(bitmapSize, 600, 1080, spec, crop)

        assertEquals(atDensity2x.srcLeft, atDensity3x.srcLeft)
        assertEquals(atDensity2x.srcTop, atDensity3x.srcTop)
        assertEquals(atDensity2x.srcWidth, atDensity3x.srcWidth)
        assertEquals(atDensity2x.srcHeight, atDensity3x.srcHeight)

        assertEquals(exportRegion.left.toDouble() / 4000.0, atDensity2x.srcLeft.toDouble() / 960.0, 0.003)
        assertEquals(exportRegion.top.toDouble() / 3000.0, atDensity2x.srcTop.toDouble() / 720.0, 0.003)
        assertEquals(exportRegion.width.toDouble() / 4000.0, atDensity2x.srcWidth.toDouble() / 960.0, 0.003)
        assertEquals(exportRegion.height.toDouble() / 3000.0, atDensity2x.srcHeight.toDouble() / 720.0, 0.003)

        assertEquals(9.0 / 16.0, atDensity2x.dstWidth.toDouble() / atDensity2x.dstHeight.toDouble(), 0.01)
        assertEquals(9.0 / 16.0, atDensity3x.dstWidth.toDouble() / atDensity3x.dstHeight.toDouble(), 0.01)
    }

    @Test
    fun `computePanDelta converts viewport pixel drag into 1 to 1 normalized pan`() {
        val deltaRight =
            CropPreviewGeometry.computePanDelta(
                dragDeltaPx = -100f,
                viewportSpanPx = 200f,
                sourceSpanPx = 4000,
                cropSpanPx = 2000,
            )
        assertEquals(0.5, deltaRight, 0.0001)

        val lockedAxis =
            CropPreviewGeometry.computePanDelta(
                dragDeltaPx = -100f,
                viewportSpanPx = 200f,
                sourceSpanPx = 3000,
                cropSpanPx = 3000,
            )
        assertEquals(0.0, lockedAxis, 0.0)
    }

    private fun specFor(crop: CropAdjustment) =
        calculateSpec(
            OptimizationRequest(
                source = landscapePhoto,
                platform = TargetPlatform.WA_STORY,
                aspectMode = CanvasAspectMode.CROP_9_16,
                crop = crop,
            ),
        )
}
