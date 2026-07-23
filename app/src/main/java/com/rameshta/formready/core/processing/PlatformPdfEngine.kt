package com.rameshta.formready.core.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.rameshta.formready.core.model.PdfCompressionMode
import com.rameshta.formready.core.model.ProcessingPlan
import com.rameshta.formready.core.model.ValidationOutcome
import com.rameshta.formready.core.model.ValidationRuleResult
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class PlatformPdfEngine @Inject constructor() : PdfEngine {
    override suspend fun inspect(input: File): PdfMetadata = withContext(Dispatchers.IO) {
        inspectInternal(input)
    }

    override suspend fun renderPreview(
        input: File,
        pageIndex: Int,
        maximumEdgePx: Int,
    ): Bitmap = withContext(Dispatchers.Default) {
        require(maximumEdgePx in 128..2_048)
        openRenderer(input).use { opened ->
            require(pageIndex in 0 until opened.renderer.pageCount)
            opened.renderer.openPage(pageIndex).use { page ->
                val scale = maximumEdgePx.toFloat() / max(page.width, page.height)
                renderPage(page, scale.coerceAtMost(1f))
            }
        }
    }

    override suspend fun flatten(
        input: File,
        destination: File,
        plan: ProcessingPlan,
    ): PreparedPdf = withContext(Dispatchers.IO) {
        val options = plan.pdfOptions
            ?: throw PdfProcessingException(PdfProcessingException.Code.CORRUPT_INPUT)
        if (
            options.compressionMode != PdfCompressionMode.STRONG_FLATTEN ||
            !options.flatteningAcknowledged
        ) {
            throw PdfProcessingException(
                PdfProcessingException.Code.FLATTENING_ACKNOWLEDGEMENT_REQUIRED,
            )
        }
        val source = inspectInternal(input)
        val maximumBytes = plan.output.maximumBytes
        destination.parentFile?.mkdirs()
        destination.delete()

        var dpi = options.initialDpi
        var quality = DEFAULT_JPEG_QUALITY
        var best: File? = null
        var bestBytes = Long.MAX_VALUE
        try {
            repeat(options.maximumPasses) { index ->
                coroutineContext.ensureActive()
                val candidate = File(
                    destination.parentFile,
                    "${destination.name}.pass-${index + 1}.part",
                )
                candidate.delete()
                writeFlattened(input, candidate, dpi, quality)
                val reopened = inspectInternal(candidate)
                if (!samePages(source.pages, reopened.pages)) {
                    candidate.delete()
                    throw PdfProcessingException(
                        PdfProcessingException.Code.OUTPUT_VALIDATION_FAILED,
                    )
                }
                validateEveryPage(candidate)
                if (candidate.length() < bestBytes) {
                    best?.delete()
                    best = candidate
                    bestBytes = candidate.length()
                } else {
                    candidate.delete()
                }
                if (maximumBytes == null || bestBytes <= maximumBytes) {
                    move(best!!, destination)
                    best = null
                    return@withContext prepared(
                        metadata = inspectInternal(destination),
                        maximumBytes = maximumBytes,
                        dpi = dpi,
                        quality = quality,
                        passes = index + 1,
                    )
                }
                if (quality > options.minimumJpegQuality) {
                    quality = max(options.minimumJpegQuality, quality - QUALITY_STEP)
                } else if (dpi > options.minimumDpi) {
                    dpi = max(options.minimumDpi, (dpi * DPI_REDUCTION).roundToInt())
                }
            }
            throw PdfProcessingException(PdfProcessingException.Code.TARGET_UNREACHABLE)
        } finally {
            best?.delete()
            destination.parentFile?.listFiles()
                ?.filter { it.name.startsWith("${destination.name}.pass-") }
                ?.forEach(File::delete)
        }
    }

    override suspend fun imagesToPdf(
        images: List<File>,
        destination: File,
        jpegQuality: Int,
    ): PreparedPdf = withContext(Dispatchers.IO) {
        require(images.isNotEmpty() && images.size <= MAX_PAGE_COUNT)
        require(jpegQuality in MIN_JPEG_QUALITY..95)
        destination.parentFile?.mkdirs()
        destination.delete()
        val document = PdfDocument()
        try {
            images.forEachIndexed { index, image ->
                coroutineContext.ensureActive()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(image.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw PdfProcessingException(PdfProcessingException.Code.CORRUPT_INPUT)
                }
                var sample = 1
                while (
                    bounds.outWidth / (sample * 2) >= IMAGE_PDF_MAX_EDGE ||
                    bounds.outHeight / (sample * 2) >= IMAGE_PDF_MAX_EDGE
                ) {
                    sample *= 2
                }
                val bitmap = BitmapFactory.decodeFile(
                    image.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: throw PdfProcessingException(PdfProcessingException.Code.CORRUPT_INPUT)
                try {
                    val width = bitmap.width.coerceAtMost(IMAGE_PDF_MAX_EDGE)
                    val height = (bitmap.height * (width.toFloat() / bitmap.width))
                        .roundToInt()
                        .coerceAtLeast(1)
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(width, height, index + 1).create(),
                    )
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(
                        bitmap,
                        null,
                        android.graphics.Rect(0, 0, width, height),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                    )
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            destination.outputStream().buffered().use(document::writeTo)
        } catch (error: Exception) {
            destination.delete()
            if (error is PdfProcessingException) throw error
            throw PdfProcessingException(PdfProcessingException.Code.OUTPUT_WRITE_FAILED, error)
        } finally {
            document.close()
        }
        validateEveryPage(destination)
        prepared(
            metadata = inspectInternal(destination),
            maximumBytes = null,
            dpi = 72,
            quality = jpegQuality,
            passes = 1,
        )
    }

    private suspend fun writeFlattened(input: File, output: File, dpi: Int, quality: Int) {
        val document = PdfDocument()
        try {
            openRenderer(input).use { opened ->
                for (index in 0 until opened.renderer.pageCount) {
                    coroutineContext.ensureActive()
                    opened.renderer.openPage(index).use { page ->
                        val scale = dpi / PDF_POINTS_PER_INCH
                        val pixels = page.width.toLong() * page.height.toLong() *
                            scale.toDouble() * scale.toDouble()
                        if (pixels > MAX_RENDER_PIXELS) {
                            throw PdfProcessingException(
                                PdfProcessingException.Code.PAGE_DIMENSIONS_UNSAFE,
                            )
                        }
                        val rendered = renderPage(page, scale)
                        val jpeg = File(output.parentFile, "${output.name}.page-$index.jpg")
                        try {
                            jpeg.outputStream().buffered().use {
                                check(rendered.compress(Bitmap.CompressFormat.JPEG, quality, it))
                            }
                            val compressed = BitmapFactory.decodeFile(jpeg.absolutePath)
                                ?: throw PdfProcessingException(
                                    PdfProcessingException.Code.OUTPUT_WRITE_FAILED,
                                )
                            try {
                                val outputPage = document.startPage(
                                    PdfDocument.PageInfo.Builder(
                                        page.width,
                                        page.height,
                                        index + 1,
                                    ).create(),
                                )
                                outputPage.canvas.drawColor(Color.WHITE)
                                outputPage.canvas.drawBitmap(
                                    compressed,
                                    null,
                                    android.graphics.Rect(0, 0, page.width, page.height),
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                                )
                                document.finishPage(outputPage)
                            } finally {
                                compressed.recycle()
                            }
                        } finally {
                            rendered.recycle()
                            jpeg.delete()
                        }
                    }
                }
            }
            output.outputStream().buffered().use(document::writeTo)
        } catch (error: Exception) {
            output.delete()
            if (error is PdfProcessingException) throw error
            throw PdfProcessingException(PdfProcessingException.Code.OUTPUT_WRITE_FAILED, error)
        } finally {
            document.close()
        }
    }

    private fun inspectInternal(input: File): PdfMetadata {
        if (!input.isFile || input.length() !in 5..MAX_PDF_BYTES) {
            throw PdfProcessingException(PdfProcessingException.Code.CORRUPT_INPUT)
        }
        input.inputStream().use { stream ->
            ByteArray(5).also {
                if (stream.read(it) != it.size || !it.contentEquals("%PDF-".encodeToByteArray())) {
                    throw PdfProcessingException(PdfProcessingException.Code.CORRUPT_INPUT)
                }
            }
        }
        val tokens = scanTokens(input)
        return try {
            openRenderer(input).use { opened ->
                val count = opened.renderer.pageCount
                if (count !in 1..MAX_PAGE_COUNT) {
                    throw PdfProcessingException(PdfProcessingException.Code.PAGE_COUNT_UNSAFE)
                }
                val pages = buildList {
                    for (index in 0 until count) {
                        opened.renderer.openPage(index).use { page ->
                            if (page.width !in 1..MAX_PAGE_POINTS ||
                                page.height !in 1..MAX_PAGE_POINTS
                            ) {
                                throw PdfProcessingException(
                                    PdfProcessingException.Code.PAGE_DIMENSIONS_UNSAFE,
                                )
                            }
                            add(PdfPageMetadata(page.width, page.height))
                        }
                    }
                }
                PdfMetadata(
                    byteCount = input.length(),
                    pageCount = count,
                    pages = pages,
                    encrypted = tokens.contains("/Encrypt"),
                    hasForms = tokens.contains("/AcroForm"),
                    hasLinks = tokens.contains("/Subtype/Link") ||
                        tokens.contains("/Subtype /Link"),
                    hasAnnotations = tokens.contains("/Annots"),
                    hasDigitalSignatures = tokens.contains("/Type/Sig") ||
                        tokens.contains("/Type /Sig"),
                )
            }
        } catch (security: SecurityException) {
            throw PdfProcessingException(
                PdfProcessingException.Code.ENCRYPTED_UNSUPPORTED,
                security,
            )
        } catch (error: PdfProcessingException) {
            throw error
        } catch (error: Exception) {
            val code = if (tokens.contains("/Encrypt")) {
                PdfProcessingException.Code.ENCRYPTED_UNSUPPORTED
            } else {
                PdfProcessingException.Code.CORRUPT_INPUT
            }
            throw PdfProcessingException(code, error)
        }
    }

    private fun validateEveryPage(file: File) {
        openRenderer(file).use { opened ->
            for (index in 0 until opened.renderer.pageCount) {
                opened.renderer.openPage(index).use { page ->
                    renderPage(
                        page,
                        (VALIDATION_EDGE.toFloat() / max(page.width, page.height))
                            .coerceAtMost(1f),
                    ).recycle()
                }
            }
        }
    }

    private fun prepared(
        metadata: PdfMetadata,
        maximumBytes: Long?,
        dpi: Int,
        quality: Int,
        passes: Int,
    ): PreparedPdf {
        val results = buildList {
            add(
                ValidationRuleResult(
                    ruleId = "pdf.readable",
                    outcome = ValidationOutcome.PASS,
                    expected = "Reopenable PDF with every page renderable",
                    actual = "${metadata.pageCount} pages reopened and rendered",
                    explanation = "The generated PDF was reopened and every page was rendered.",
                    fixAction = null,
                    isHardRule = true,
                ),
            )
            maximumBytes?.let { maximum ->
                add(
                    ValidationRuleResult(
                        ruleId = "pdf.maximum_bytes",
                        outcome = if (metadata.byteCount <= maximum) {
                            ValidationOutcome.PASS
                        } else {
                            ValidationOutcome.FAIL
                        },
                        expected = "At most $maximum bytes",
                        actual = "${metadata.byteCount} bytes",
                        explanation = "The entire reopened PDF is measured.",
                        fixAction = "Increase the size limit or accept stronger flattening.",
                        isHardRule = true,
                    ),
                )
            }
            add(
                ValidationRuleResult(
                    ruleId = "pdf.flattened",
                    outcome = ValidationOutcome.WARNING,
                    expected = "Explicitly acknowledged compatibility export",
                    actual = "Pages recreated as images",
                    explanation = "Text, links, forms, annotations, accessibility, and digital signatures may be removed.",
                    fixAction = null,
                    isHardRule = false,
                ),
            )
        }
        return PreparedPdf(
            byteCount = metadata.byteCount,
            pageCount = metadata.pageCount,
            pages = metadata.pages,
            renderDpi = dpi,
            jpegQuality = quality,
            passes = passes,
            validationResults = results,
        )
    }

    private fun renderPage(page: PdfRenderer.Page, scale: Float): Bitmap {
        val width = max(1, (page.width * scale).roundToInt())
        val height = max(1, (page.height * scale).roundToInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(
            bitmap,
            null,
            Matrix().apply { setScale(scale, scale) },
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
        )
        return bitmap
    }

    private fun scanTokens(input: File): String {
        val bytes = input.inputStream().buffered().use { stream ->
            val buffer = ByteArray(minOf(TOKEN_SCAN_BYTES.toLong(), input.length()).toInt())
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            buffer.copyOf(total)
        }
        return bytes.toString(Charsets.ISO_8859_1)
    }

    private fun samePages(
        expected: List<PdfPageMetadata>,
        actual: List<PdfPageMetadata>,
    ): Boolean = expected == actual

    private fun openRenderer(input: File): OpenedRenderer {
        val descriptor = ParcelFileDescriptor.open(
            input,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        return try {
            OpenedRenderer(descriptor, PdfRenderer(descriptor))
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }

    private fun move(source: File, destination: File) {
        destination.delete()
        if (!source.renameTo(destination)) {
            source.copyTo(destination, overwrite = true)
            source.delete()
        }
    }

    private class OpenedRenderer(
        private val descriptor: ParcelFileDescriptor,
        val renderer: PdfRenderer,
    ) : AutoCloseable {
        override fun close() {
            renderer.close()
            descriptor.close()
        }
    }

    companion object {
        const val DEFAULT_INITIAL_DPI = 150
        const val DEFAULT_MINIMUM_DPI = 120
        const val DEFAULT_JPEG_QUALITY = 85
        const val MIN_JPEG_QUALITY = 40
        const val MAXIMUM_PASSES = 6
        private const val QUALITY_STEP = 15
        private const val DPI_REDUCTION = 0.8f
        private const val PDF_POINTS_PER_INCH = 72f
        private const val MAX_PAGE_COUNT = 250
        private const val MAX_PAGE_POINTS = 20_000
        private const val MAX_RENDER_PIXELS = 40_000_000L
        private const val MAX_PDF_BYTES = 200L * 1024L * 1024L
        private const val TOKEN_SCAN_BYTES = 8 * 1024 * 1024
        private const val VALIDATION_EDGE = 128
        private const val IMAGE_PDF_MAX_EDGE = 2_048
    }
}
