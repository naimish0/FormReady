package com.rameshta.formready

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import com.rameshta.formready.core.model.PdfCompressionMode
import com.rameshta.formready.core.model.PdfOptions
import com.rameshta.formready.core.model.Readiness
import com.rameshta.formready.core.model.SignatureOptions
import com.rameshta.formready.core.model.readiness
import com.rameshta.formready.core.processing.AndroidImageMetadataReader
import com.rameshta.formready.core.processing.AndroidImageTransformEngine
import com.rameshta.formready.core.processing.InputImageFormat
import com.rameshta.formready.core.processing.PhotoProcessingException
import com.rameshta.formready.core.processing.PngDpiMetadata
import com.rameshta.formready.core.processing.PhotoPlanCodec
import com.rameshta.formready.core.processing.IdPhotoPrintSheetEngine
import com.rameshta.formready.core.processing.PrintSheetSize
import com.rameshta.formready.core.model.IdPhotoOptions
import com.rameshta.formready.core.model.MaskStroke
import com.rameshta.formready.core.processing.PrivateWorkspaceCleaner
import com.rameshta.formready.core.processing.PdfProcessingException
import com.rameshta.formready.core.processing.PlatformPdfEngine
import com.rameshta.formready.core.processing.SignatureBitmapProcessor
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPipelineInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "phase1-photo-tests-${UUID.randomUUID()}").apply {
        check(mkdirs())
    }
    private val reader = AndroidImageMetadataReader()
    private val engine = AndroidImageTransformEngine(reader, SignatureBitmapProcessor())

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
                NormalizedTransform.FitPad(
                    backgroundArgb = Color.TRANSPARENT,
                    paddingFraction = 0.1f,
                    horizontalOffset = -0.2f,
                    verticalOffset = 0.3f,
                ),
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
            signatureOptions = SignatureOptions(
                threshold = 175,
                safeMarginPercent = 9,
                inkArgb = Color.BLUE,
                transparentBackground = true,
                cropLeft = 0.1f,
                cropTop = 0.2f,
                cropRight = 0.9f,
                cropBottom = 0.8f,
            ),
            pdfOptions = PdfOptions(
                compressionMode = PdfCompressionMode.STRONG_FLATTEN,
                flatteningAcknowledged = true,
                initialDpi = 180,
                minimumDpi = 120,
                minimumJpegQuality = 45,
                maximumPasses = 5,
            ),
            idPhotoOptions = IdPhotoOptions(
                replaceBackground = true,
                backgroundArgb = Color.WHITE,
                maskStrokes = listOf(
                    MaskStroke(x = 0.5f, y = 0.5f, radius = 0.03f, restore = true),
                ),
            ),
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
    fun idPhotoPrintSheetReopensAtExactFourBySixPageSize() = runBlocking {
        val source = syntheticBitmap(width = 600, height = 800, hasAlpha = false)
            .write("id-photo.jpg", Bitmap.CompressFormat.JPEG)
        val destination = File(root, "id-sheet.pdf")

        IdPhotoPrintSheetEngine().create(
            source = source,
            destination = destination,
            sheet = PrintSheetSize.FOUR_BY_SIX,
            copies = 4,
        )

        assertTrue(destination.length() > 0L)
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
        val expiredImagesToPdf = File(
            context.noBackupFilesDir,
            "images-to-pdf/${UUID.randomUUID()}",
        ).apply {
            mkdirs()
            File(this, "page").writeText("synthetic")
            setLastModified(System.currentTimeMillis() - PrivateWorkspaceCleaner.RETENTION_MILLIS)
        }

        PrivateWorkspaceCleaner(context).removeAbandonedPartials()

        assertFalse(oldPartial.exists())
        assertTrue(recentPartial.exists())
        assertTrue(completed.exists())
        assertFalse(expiredImagesToPdf.exists())
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

    @Test
    fun signatureCleanupAutoCropsRecoloursAndKeepsTransparentPng() = runBlocking {
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(25, 25, 25)
                strokeWidth = 10f
                style = Paint.Style.STROKE
            }
            drawLine(140f, 100f, 280f, 65f, paint)
            drawLine(280f, 65f, 440f, 115f, paint)
            drawPoint(20f, 20f, Paint().apply { color = Color.BLACK })
        }
        val source = bitmap.write("signature-paper.png", Bitmap.CompressFormat.PNG)
        val destination = File(root, "signature-clean.png")
        val plan = ProcessingPlan(
            jobId = UUID.randomUUID(),
            transforms = listOf(
                NormalizedTransform.FitPad(
                    backgroundArgb = Color.TRANSPARENT,
                    paddingFraction = 0.08f,
                ),
            ),
            output = OutputSpecification(
                format = OutputFormat.PNG,
                widthPx = 300,
                heightPx = 100,
                maximumBytes = 100_000,
                dpi = 300,
                backgroundArgb = Color.TRANSPARENT,
            ),
            hardRuleIds = emptySet(),
            advisoryRuleIds = emptySet(),
            signatureOptions = SignatureOptions(
                threshold = 180,
                inkArgb = Color.rgb(10, 61, 145),
                transparentBackground = true,
            ),
        )

        val prepared = engine.prepare(source, destination, plan)
        val reopened = BitmapFactory.decodeFile(destination.absolutePath)
        assertEquals(300, prepared.widthPx)
        assertEquals(100, prepared.heightPx)
        assertEquals(0, Color.alpha(reopened.getPixel(0, 0)))
        assertTrue(
            (0 until reopened.width step 3).any { x ->
                (0 until reopened.height step 3).any { y ->
                    Color.blue(reopened.getPixel(x, y)) > Color.red(reopened.getPixel(x, y)) &&
                        Color.alpha(reopened.getPixel(x, y)) > 0
                }
            },
        )
        reopened.recycle()
    }

    @Test
    fun signaturePreviewUsesExactFrameRotationPaddingAndPosition() {
        val source = Bitmap.createBitmap(180, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        Canvas(source).drawRect(
            70f,
            20f,
            110f,
            100f,
            Paint().apply { color = Color.BLACK },
        )

        val preview = SignatureBitmapProcessor().processPreview(
            source = source,
            options = SignatureOptions(safeMarginPercent = 0),
            rotationDegrees = 90f,
            requestedWidth = 240,
            requestedHeight = 120,
            paddingFraction = 0.1f,
            horizontalOffset = 1f,
            verticalOffset = 0f,
            backgroundArgb = Color.WHITE,
        )

        assertEquals(240, preview.width)
        assertEquals(120, preview.height)
        val inkPixels = buildList {
            for (y in 0 until preview.height) {
                for (x in 0 until preview.width) {
                    if (Color.red(preview.getPixel(x, y)) < 80) add(x)
                }
            }
        }
        assertTrue(inkPixels.isNotEmpty())
        assertTrue(inkPixels.average() > preview.width / 2f)
        assertEquals(Color.WHITE, preview.getPixel(0, 0))
        preview.recycle()
        source.recycle()
    }

    @Test
    fun blankSignatureIsRejectedWithTypedFailure() = runBlocking {
        val source = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }.write("blank-signature.png", Bitmap.CompressFormat.PNG)
        val destination = File(root, "blank-output.png")
        val plan = ProcessingPlan(
            jobId = UUID.randomUUID(),
            transforms = listOf(NormalizedTransform.FitPad(Color.WHITE)),
            output = OutputSpecification(
                format = OutputFormat.PNG,
                widthPx = 300,
                heightPx = 100,
                maximumBytes = 100_000,
            ),
            hardRuleIds = emptySet(),
            advisoryRuleIds = emptySet(),
            signatureOptions = SignatureOptions(),
        )

        assertPhotoError(PhotoProcessingException.Code.EMPTY_SIGNATURE) {
            engine.prepare(source, destination, plan)
        }
        assertFalse(destination.exists())
    }

    @Test
    fun pdfInspectionPreviewFlatteningAndReopenValidation() = runBlocking {
        val source = writeSyntheticPdf("mixed.pdf", listOf(612 to 792, 792 to 612))
        val pdfEngine = PlatformPdfEngine()
        val inspected = pdfEngine.inspect(source)
        assertEquals(2, inspected.pageCount)
        assertFalse(inspected.pages[0].isLandscape)
        assertTrue(inspected.pages[1].isLandscape)

        val preview = pdfEngine.renderPreview(source, 1, 512)
        assertTrue(maxOf(preview.width, preview.height) <= 512)
        preview.recycle()

        val destination = File(root, "flattened.pdf")
        val prepared = pdfEngine.flatten(
            source,
            destination,
            ProcessingPlan(
                jobId = UUID.randomUUID(),
                transforms = emptyList(),
                output = OutputSpecification(
                    format = OutputFormat.PDF,
                    maximumBytes = 2_000_000,
                ),
                hardRuleIds = setOf("pdf.readable", "pdf.maximum_bytes"),
                advisoryRuleIds = setOf("pdf.flattened"),
                pdfOptions = PdfOptions(
                    compressionMode = PdfCompressionMode.STRONG_FLATTEN,
                    flatteningAcknowledged = true,
                ),
            ),
        )
        assertEquals(2, prepared.pageCount)
        assertEquals(inspected.pages, prepared.pages)
        assertTrue(destination.length() <= 2_000_000)
        assertEquals(2, pdfEngine.inspect(destination).pageCount)
    }

    @Test
    fun pdfStrongCompressionRejectsUnreachableTarget() = runBlocking {
        val source = writeSyntheticPdf("unreachable.pdf", listOf(612 to 792))
        val destination = File(root, "unreachable-output.pdf")
        try {
            PlatformPdfEngine().flatten(
                source,
                destination,
                ProcessingPlan(
                    jobId = UUID.randomUUID(),
                    transforms = emptyList(),
                    output = OutputSpecification(
                        format = OutputFormat.PDF,
                        maximumBytes = 100,
                    ),
                    hardRuleIds = emptySet(),
                    advisoryRuleIds = emptySet(),
                    pdfOptions = PdfOptions(
                        compressionMode = PdfCompressionMode.STRONG_FLATTEN,
                        flatteningAcknowledged = true,
                    ),
                ),
            )
            fail("Expected target-unreachable failure")
        } catch (error: PdfProcessingException) {
            assertEquals(PdfProcessingException.Code.TARGET_UNREACHABLE, error.code)
        }
        assertFalse(destination.exists())
    }

    @Test
    fun imagesToPdfPreservesImageOrderAndPageCount() = runBlocking {
        val first = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }.write("page-one.png", Bitmap.CompressFormat.PNG)
        val second = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }.write("page-two.png", Bitmap.CompressFormat.PNG)
        val destination = File(root, "images.pdf")

        val result = PlatformPdfEngine().imagesToPdf(
            listOf(first, second),
            destination,
        )

        assertEquals(2, result.pageCount)
        assertTrue(result.pages[0].isLandscape)
        assertFalse(result.pages[1].isLandscape)
        assertEquals(2, PlatformPdfEngine().inspect(destination).pageCount)
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

    private fun writeSyntheticPdf(
        name: String,
        pages: List<Pair<Int, Int>>,
    ): File {
        val file = File(root, name)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, (width, height) ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(width, height, index + 1).create(),
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(
                    "Synthetic page ${index + 1}",
                    40f,
                    80f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 24f
                    },
                )
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
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
