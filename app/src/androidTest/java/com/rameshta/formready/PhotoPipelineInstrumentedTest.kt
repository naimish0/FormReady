package com.rameshta.formready

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.formready.core.model.NormalizedTransform
import com.rameshta.formready.core.model.ByteUnit
import com.rameshta.formready.core.model.DimensionRule
import com.rameshta.formready.core.model.PhysicalUnit
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.OutputSpecification
import com.rameshta.formready.core.model.ProcessingPlan
import com.rameshta.formready.core.model.Readiness
import com.rameshta.formready.core.model.readiness
import com.rameshta.formready.core.processing.AndroidImageMetadataReader
import com.rameshta.formready.core.processing.AndroidImageTransformEngine
import com.rameshta.formready.core.processing.InputImageFormat
import com.rameshta.formready.core.processing.PhotoProcessingException
import com.rameshta.formready.core.processing.PngDpiMetadata
import com.rameshta.formready.core.processing.PhotoPlanCodec
import com.rameshta.formready.core.processing.PrivateWorkspaceCleaner
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPipelineInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "phase1-photo-tests-${UUID.randomUUID()}").apply {
        check(mkdirs())
    }
    private val reader = AndroidImageMetadataReader()
    private val engine = AndroidImageTransformEngine(reader)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun inspectionUsesMagicBytesAndRejectsCorruptOrAnimatedInputs() = runBlocking {
        val jpeg = syntheticBitmap(hasAlpha = false).write("input.bin", Bitmap.CompressFormat.JPEG)
        val png = syntheticBitmap(hasAlpha = true).write("input.data", Bitmap.CompressFormat.PNG)
        val webp = syntheticBitmap(hasAlpha = true).write("input.webp", Bitmap.CompressFormat.WEBP)

        assertEquals(InputImageFormat.JPEG, reader.inspect(jpeg).format)
        assertEquals(InputImageFormat.PNG, reader.inspect(png).format)
        assertEquals(InputImageFormat.WEBP, reader.inspect(webp).format)

        assertPhotoError(PhotoProcessingException.Code.CORRUPT_INPUT) {
            reader.inspect(File(root, "zero").apply { createNewFile() })
        }
        assertPhotoError(PhotoProcessingException.Code.UNSUPPORTED_FORMAT) {
            reader.inspect(File(root, "garbage").apply { writeText("not an image") })
        }
        assertPhotoError(PhotoProcessingException.Code.ANIMATED_IMAGE_UNSUPPORTED) {
            reader.inspect(
                File(root, "animated.webp").apply {
                    writeBytes(
                        "RIFF\u0000\u0000\u0000\u0000WEBPVP8XANIM".encodeToByteArray(),
                    )
                },
            )
        }
    }

    @Test
    fun allExifOrientationsAreInspectedAndNormalizedIntoExactOutput() = runBlocking {
        val source = syntheticBitmap(width = 96, height = 64, hasAlpha = false)
            .write("orientation.jpg", Bitmap.CompressFormat.JPEG)

        for (orientation in ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270) {
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            val metadata = reader.inspect(source)
            val swapsAxes = orientation in setOf(
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_ROTATE_270,
            )
            assertEquals(if (swapsAxes) 64 else 96, metadata.widthPx)
            assertEquals(if (swapsAxes) 96 else 64, metadata.heightPx)

            val destination = File(root, "orientation-$orientation.jpg")
            val prepared = engine.prepare(source, destination, exactPlan(OutputFormat.JPEG))
            assertEquals(120, prepared.widthPx)
            assertEquals(160, prepared.heightPx)
            assertTrue(prepared.validationResults.readiness() != Readiness.NOT_READY)
        }
    }

    @Test
    fun jpegOutputMeetsActualBytesDimensionsDpiSrgbAndStripsSensitiveMetadata() =
        runBlocking {
            val source = syntheticBitmap(width = 320, height = 240, hasAlpha = false)
                .write("private-source.jpg", Bitmap.CompressFormat.JPEG)
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_MAKE, "Synthetic camera")
                setLatLong(12.34, 56.78)
                saveAttributes()
            }
            val destination = File(root, "prepared.jpg")

            val prepared = engine.prepare(
                source,
                destination,
                exactPlan(OutputFormat.JPEG, maximumBytes = 120_000, dpi = 300),
            )

            assertTrue(destination.length() <= 120_000)
            assertEquals(destination.length(), prepared.byteCount)
            assertEquals(120, prepared.widthPx)
            assertEquals(160, prepared.heightPx)
            assertEquals(300, prepared.dpi)
            assertFalse(prepared.hasAlpha)
            assertEquals(Readiness.READY, prepared.validationResults.readiness())
            ExifInterface(destination).also { outputExif ->
                assertNull(outputExif.getAttribute(ExifInterface.TAG_MAKE))
                assertNull(outputExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                assertEquals(
                    ExifInterface.ORIENTATION_NORMAL,
                    outputExif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED,
                    ),
                )
            }
            Unit
        }

    @Test
    fun pngOutputPreservesAlphaAndReopensDpiMetadata() = runBlocking {
        val source = syntheticBitmap(width = 180, height = 120, hasAlpha = true)
            .write("transparent.png", Bitmap.CompressFormat.PNG)
        val destination = File(root, "prepared.png")
        val plan = ProcessingPlan(
            jobId = UUID.randomUUID(),
            transforms = listOf(NormalizedTransform.FitPad(Color.TRANSPARENT)),
            output = OutputSpecification(
                format = OutputFormat.PNG,
                widthPx = 160,
                heightPx = 160,
                maximumBytes = 500_000,
                dpi = 300,
                backgroundArgb = Color.TRANSPARENT,
            ),
            hardRuleIds = emptySet(),
            advisoryRuleIds = emptySet(),
        )

        val prepared = engine.prepare(source, destination, plan)

        assertTrue(prepared.hasAlpha)
        assertEquals(300, prepared.dpi)
        assertTrue(prepared.validationResults.readiness() != Readiness.NOT_READY)
        assertTrue(destination.length() <= 500_000)
    }

    @Test
    fun nativePngDpiChunkRoundTrips() {
        val png = syntheticBitmap(hasAlpha = true).write("dpi.png", Bitmap.CompressFormat.PNG)

        PngDpiMetadata.write(png, 300)

        assertEquals(300, PngDpiMetadata.read(png))
    }

    @Test
    fun processingRecipeRoundTripsAllRequirementSemantics() {
        val original = ProcessingPlan(
            jobId = UUID.randomUUID(),
            transforms = listOf(
                NormalizedTransform.Rotate(91.5f),
                NormalizedTransform.FitPad(Color.WHITE),
            ),
            output = OutputSpecification(
                format = OutputFormat.PNG,
                widthPx = 413,
                heightPx = 531,
                dimensionRule = DimensionRule.MAXIMUM,
                byteUnit = ByteUnit.KIB,
                physicalWidth = 35.0,
                physicalHeight = 45.0,
                physicalUnit = PhysicalUnit.MILLIMETRES,
                minimumBytes = 100_000,
                maximumBytes = 204_800,
                dpi = 300,
                safetyMarginBytes = 2_048,
                backgroundArgb = Color.WHITE,
            ),
            hardRuleIds = setOf("photo.format", "photo.dpi"),
            advisoryRuleIds = setOf("photo.upscaling"),
        )

        val restored = PhotoPlanCodec.decode(PhotoPlanCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun impossibleFixedDimensionsReturnTypedTargetFailureAndRemoveCandidate() = runBlocking {
        val source = syntheticBitmap(width = 500, height = 500, hasAlpha = false)
            .write("large.jpg", Bitmap.CompressFormat.JPEG)
        val destination = File(root, "impossible.jpg")

        assertPhotoError(PhotoProcessingException.Code.TARGET_UNREACHABLE) {
            engine.prepare(
                source,
                destination,
                exactPlan(OutputFormat.JPEG, maximumBytes = 100, dpi = null),
            )
        }
        assertFalse(destination.exists())
    }

    @Test
    fun maximumDimensionsDoNotUpscaleOrDistortCropFrame() = runBlocking {
        val source = syntheticBitmap(width = 300, height = 200, hasAlpha = false)
            .write("small.jpg", Bitmap.CompressFormat.JPEG)
        val destination = File(root, "maximum.jpg")
        val plan = ProcessingPlan(
            jobId = UUID.randomUUID(),
            transforms = listOf(
                NormalizedTransform.Crop(0.25f, 0f, 0.75f, 1f),
            ),
            output = OutputSpecification(
                format = OutputFormat.JPEG,
                widthPx = 600,
                heightPx = 800,
                dimensionRule = DimensionRule.MAXIMUM,
                maximumBytes = 500_000,
            ),
            hardRuleIds = emptySet(),
            advisoryRuleIds = emptySet(),
        )

        val prepared = engine.prepare(source, destination, plan)

        assertEquals(150, prepared.widthPx)
        assertEquals(200, prepared.heightPx)
        assertEquals(0.75f, prepared.widthPx.toFloat() / prepared.heightPx, 0.0001f)
        assertFalse(prepared.wasSubstantiallyUpscaled)
    }

    @Test
    fun startupCleanupRemovesOnlyExpiredPartialFiles() {
        val directory = File(context.noBackupFilesDir, "staged-inputs").apply { mkdirs() }
        val oldPartial = File(directory, "${UUID.randomUUID()}.part").apply {
            writeText("partial")
            setLastModified(System.currentTimeMillis() - PrivateWorkspaceCleaner.RETENTION_MILLIS)
        }
        val recentPartial = File(directory, "${UUID.randomUUID()}.part").apply {
            writeText("partial")
        }
        val completed = File(directory, UUID.randomUUID().toString()).apply {
            writeText("complete")
            setLastModified(System.currentTimeMillis() - PrivateWorkspaceCleaner.RETENTION_MILLIS)
        }

        PrivateWorkspaceCleaner(context).removeAbandonedPartials()

        assertFalse(oldPartial.exists())
        assertTrue(recentPartial.exists())
        assertTrue(completed.exists())
        recentPartial.delete()
        completed.delete()
    }

    @Test
    fun synthetic48MegapixelInputIsBoundedDecodedForPortalSizedOutput() = runBlocking {
        val sourceBitmap = Bitmap.createBitmap(8_000, 6_000, Bitmap.Config.RGB_565).apply {
            eraseColor(Color.rgb(40, 100, 170))
        }
        val source = sourceBitmap.write("synthetic-48mp.jpg", Bitmap.CompressFormat.JPEG)
        val metadata = reader.inspect(source)
        assertEquals(48_000_000L, metadata.widthPx.toLong() * metadata.heightPx)
        val destination = File(root, "synthetic-48mp-output.jpg")
        val started = System.currentTimeMillis()

        val prepared = engine.prepare(
            source,
            destination,
            exactPlan(OutputFormat.JPEG, maximumBytes = 200_000, dpi = 300),
        )

        assertEquals(120, prepared.widthPx)
        assertEquals(160, prepared.heightPx)
        assertTrue(prepared.byteCount <= 200_000)
        assertTrue(System.currentTimeMillis() - started < 30_000)
    }

    private fun exactPlan(
        format: OutputFormat,
        maximumBytes: Long = 500_000,
        dpi: Int? = null,
    ) = ProcessingPlan(
        jobId = UUID.randomUUID(),
        transforms = listOf(
            NormalizedTransform.Crop(
                left = 0.125f,
                top = 0f,
                right = 0.875f,
                bottom = 1f,
            ),
        ),
        output = OutputSpecification(
            format = format,
            widthPx = 120,
            heightPx = 160,
            maximumBytes = maximumBytes,
            dpi = dpi,
        ),
        hardRuleIds = emptySet(),
        advisoryRuleIds = emptySet(),
    )

    private fun syntheticBitmap(
        width: Int = 96,
        height: Int = 64,
        hasAlpha: Boolean,
    ): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        eraseColor(if (hasAlpha) Color.TRANSPARENT else Color.rgb(24, 80, 160))
        for (x in 0 until width step 3) {
            for (y in 0 until height step 3) {
                setPixel(
                    x,
                    y,
                    if (hasAlpha) Color.argb(128, x % 255, y % 255, 120) else {
                        Color.rgb(x % 255, y % 255, (x + y) % 255)
                    },
                )
            }
        }
    }

    private fun Bitmap.write(name: String, format: Bitmap.CompressFormat): File =
        File(root, name).also { file ->
            file.outputStream().use { output -> assertTrue(compress(format, 92, output)) }
            recycle()
        }

    private suspend fun assertPhotoError(
        expected: PhotoProcessingException.Code,
        block: suspend () -> Unit,
    ) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue(error is PhotoProcessingException)
        assertEquals(expected, (error as PhotoProcessingException).code)
    }
}
