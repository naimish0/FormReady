package com.rameshta.formready.core.processing

import com.rameshta.formready.core.model.BatchRequirements
import com.rameshta.formready.core.model.CropMode
import com.rameshta.formready.core.model.NormalizedTransform
import com.rameshta.formready.core.model.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BatchPlanFactoryTest {
    private val metadata = ImageMetadata(
        widthPx = 1_200,
        heightPx = 800,
        byteCount = 450_000L,
        mimeType = "image/jpeg",
        exifOrientation = null,
        format = InputImageFormat.JPEG,
        dpi = null,
        hasAlpha = false,
    )

    @Test
    fun cropFillCreatesCenteredCropAndExactOutputContract() {
        val plan = BatchPlanFactory.create(
            jobId = UUID.randomUUID(),
            metadata = metadata,
            requirements = BatchRequirements(
                widthPx = 600,
                heightPx = 800,
                maximumBytes = 200_000L,
                dpi = 300,
                outputFormat = OutputFormat.JPEG,
                cropMode = CropMode.CROP_FILL,
            ),
        )

        val crop = plan.transforms.single() as NormalizedTransform.Crop
        val croppedPixelRatio =
            ((crop.right - crop.left) * metadata.widthPx) /
                ((crop.bottom - crop.top) * metadata.heightPx)
        assertEquals(0.75f, croppedPixelRatio, 0.001f)
        assertEquals(600, plan.output.widthPx)
        assertEquals(800, plan.output.heightPx)
        assertEquals(200_000L, plan.output.maximumBytes)
        assertEquals(300, plan.output.dpi)
    }

    @Test
    fun fitPadUsesOpaqueWhitePadding() {
        val plan = BatchPlanFactory.create(
            jobId = UUID.randomUUID(),
            metadata = metadata,
            requirements = BatchRequirements(
                widthPx = 800,
                heightPx = 800,
                maximumBytes = 300_000L,
                dpi = 200,
                outputFormat = OutputFormat.PNG,
                cropMode = CropMode.FIT_PAD,
            ),
        )

        assertTrue(plan.transforms.single() is NormalizedTransform.FitPad)
        assertEquals(OutputFormat.PNG, plan.output.format)
    }
}
