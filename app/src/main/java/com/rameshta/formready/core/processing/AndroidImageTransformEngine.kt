package com.rameshta.formready.core.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import android.os.Build
import com.rameshta.formready.core.model.DimensionRule
import com.rameshta.formready.core.model.NormalizedTransform
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.PhotoFileMetadata
import com.rameshta.formready.core.model.PhotoRequirementValidator
import com.rameshta.formready.core.model.PhotoRequirements
import com.rameshta.formready.core.model.ProcessingPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AndroidImageTransformEngine @Inject constructor(
    private val metadataReader: ImageMetadataReader,
) : ImageTransformEngine {
    override suspend fun prepare(
        input: File,
        destination: File,
        plan: ProcessingPlan,
    ): PreparedPhoto = withContext(Dispatchers.Default) {
        val metadata = metadataReader.inspect(input)
        val (targetWidth, targetHeight) = resolveTargetDimensions(metadata, plan)
        if (targetWidth.toLong() * targetHeight.toLong() > AndroidImageMetadataReader.MAX_IMAGE_PIXELS) {
            throw PhotoProcessingException(PhotoProcessingException.Code.IMAGE_DIMENSIONS_UNSAFE)
        }
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var rotated: Bitmap? = null
        var transformed: Bitmap? = null
        try {
            decoded = decodeNearTarget(input, targetWidth, targetHeight)
            oriented = applyExifOrientation(decoded, metadata.exifOrientation)
            if (oriented !== decoded) decoded.recycle()
            decoded = null

            val degrees = plan.transforms
                .filterIsInstance<NormalizedTransform.Rotate>()
                .sumOf { it.degreesClockwise.toDouble() }
                .toFloat()
            rotated = rotate(oriented, degrees)
            if (rotated !== oriented) oriented.recycle()
            oriented = null

            val upscalingFactor = max(
                targetWidth.toFloat() / rotated.width,
                targetHeight.toFloat() / rotated.height,
            )
            transformed = transformToFrame(
                source = rotated,
                width = targetWidth,
                height = targetHeight,
                transforms = plan.transforms,
                outputFormat = plan.output.format,
                backgroundArgb = plan.output.backgroundArgb,
            )
            if (transformed !== rotated) rotated.recycle()
            rotated = null

            encodeAndValidate(
                bitmap = transformed,
                destination = destination,
                plan = plan,
                wasSubstantiallyUpscaled = upscalingFactor > SUBSTANTIAL_UPSCALE_FACTOR,
            )
        } finally {
            decoded?.takeUnless { it.isRecycled }?.recycle()
            oriented?.takeUnless { it.isRecycled }?.recycle()
            rotated?.takeUnless { it.isRecycled }?.recycle()
            transformed?.takeUnless { it.isRecycled }?.recycle()
        }
    }

    private fun decodeNearTarget(input: File, targetWidth: Int, targetHeight: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PhotoProcessingException(PhotoProcessingException.Code.DECODE_FAILED)
        }
        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= targetWidth &&
            bounds.outHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            input.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: throw PhotoProcessingException(PhotoProcessingException.Code.DECODE_FAILED)
    }

    private fun applyExifOrientation(source: Bitmap, orientation: Int?): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun rotate(source: Bitmap, degrees: Float): Bitmap {
        val normalized = ((degrees % 360f) + 360f) % 360f
        if (abs(normalized) < 0.01f) return source
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { setRotate(normalized) },
            true,
        )
    }

    private fun transformToFrame(
        source: Bitmap,
        width: Int,
        height: Int,
        transforms: List<NormalizedTransform>,
        outputFormat: OutputFormat,
        backgroundArgb: Int,
    ): Bitmap {
        val output = createSrgbBitmap(width, height)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val fitPad = transforms.filterIsInstance<NormalizedTransform.FitPad>().lastOrNull()
        if (outputFormat == OutputFormat.JPEG) {
            canvas.drawColor(backgroundArgb)
        }
        if (fitPad != null) {
            canvas.drawColor(fitPad.backgroundArgb)
            val scale = minOf(width.toFloat() / source.width, height.toFloat() / source.height)
            val drawnWidth = source.width * scale
            val drawnHeight = source.height * scale
            val destination = RectF(
                (width - drawnWidth) / 2f,
                (height - drawnHeight) / 2f,
                (width + drawnWidth) / 2f,
                (height + drawnHeight) / 2f,
            )
            canvas.drawBitmap(source, null, destination, paint)
        } else {
            val crop = transforms.filterIsInstance<NormalizedTransform.Crop>().lastOrNull()
            val sourceRect = crop?.let {
                Rect(
                    (it.left * source.width).roundToInt().coerceIn(0, source.width - 1),
                    (it.top * source.height).roundToInt().coerceIn(0, source.height - 1),
                    (it.right * source.width).roundToInt().coerceIn(1, source.width),
                    (it.bottom * source.height).roundToInt().coerceIn(1, source.height),
                )
            } ?: centredCrop(source.width, source.height, width, height)
            if (sourceRect.width() <= 0 || sourceRect.height() <= 0) {
                output.recycle()
                throw PhotoProcessingException(PhotoProcessingException.Code.DECODE_FAILED)
            }
            canvas.drawBitmap(
                source,
                sourceRect,
                Rect(0, 0, width, height),
                paint,
            )
        }
        return output
    }

    private suspend fun encodeAndValidate(
        bitmap: Bitmap,
        destination: File,
        plan: ProcessingPlan,
        wasSubstantiallyUpscaled: Boolean,
    ): PreparedPhoto {
        destination.parentFile?.mkdirs()
        destination.delete()
        val format = plan.output.format
        val byteCap = plan.output.maximumBytes?.let { maximum ->
            val margin = plan.output.safetyMarginBytes
                ?: com.rameshta.formready.core.model.defaultSafetyMargin(maximum)
            (maximum - margin.coerceAtMost(maximum - 1))
                .let { guarded -> plan.output.minimumBytes?.let(guarded::coerceAtLeast) ?: guarded }
        }
        val quality = when (format) {
            OutputFormat.JPEG -> encodeJpeg(bitmap, destination, byteCap, plan.output.dpi)
            OutputFormat.PNG -> {
                writeBitmap(
                    bitmap,
                    destination,
                    Bitmap.CompressFormat.PNG,
                    100,
                    plan.output.dpi,
                )
                if (byteCap != null && destination.length() > byteCap) {
                    destination.delete()
                    throw PhotoProcessingException(PhotoProcessingException.Code.TARGET_UNREACHABLE)
                }
                null
            }
            OutputFormat.PDF ->
                throw PhotoProcessingException(PhotoProcessingException.Code.UNSUPPORTED_FORMAT)
        }
        val outputMetadata = inspectOutput(destination, format)
        val requirements = PhotoRequirements(
            allowedFormats = setOf(format),
            widthPx = plan.output.widthPx,
            heightPx = plan.output.heightPx,
            dimensionRule = plan.output.dimensionRule,
            minimumBytes = plan.output.minimumBytes,
            maximumBytes = plan.output.maximumBytes,
            dpi = plan.output.dpi,
            safetyMarginBytes = plan.output.safetyMarginBytes,
        )
        val results = PhotoRequirementValidator.validate(
            metadata = outputMetadata,
            requirements = requirements,
            jpegQuality = quality,
            wasSubstantiallyUpscaled = wasSubstantiallyUpscaled,
        )
        if (results.any { it.isHardRule && it.outcome == com.rameshta.formready.core.model.ValidationOutcome.FAIL }) {
            destination.delete()
            throw PhotoProcessingException(
                PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED,
                IllegalStateException(
                    results
                        .filter { it.isHardRule && it.outcome == com.rameshta.formready.core.model.ValidationOutcome.FAIL }
                        .joinToString { "${it.ruleId}:${it.actual}" },
                ),
            )
        }
        return PreparedPhoto(
            format = format,
            widthPx = outputMetadata.widthPx,
            heightPx = outputMetadata.heightPx,
            byteCount = outputMetadata.byteCount,
            dpi = outputMetadata.dpi,
            hasAlpha = outputMetadata.hasAlpha,
            jpegQuality = quality,
            wasSubstantiallyUpscaled = wasSubstantiallyUpscaled,
            validationResults = results,
        )
    }

    private suspend fun encodeJpeg(
        bitmap: Bitmap,
        destination: File,
        byteCap: Long?,
        dpi: Int?,
    ): Int {
        if (byteCap == null) {
            writeBitmap(bitmap, destination, Bitmap.CompressFormat.JPEG, DEFAULT_JPEG_QUALITY, dpi)
            return DEFAULT_JPEG_QUALITY
        }
        val candidateDirectory = destination.parentFile
            ?: throw PhotoProcessingException(PhotoProcessingException.Code.OUTPUT_WRITE_FAILED)
        val evaluated = mutableMapOf<Int, File>()
        fun evaluate(quality: Int): File {
            return evaluated.getOrPut(quality) {
                File(candidateDirectory, "${destination.name}.q$quality.part").also { candidate ->
                    writeBitmap(bitmap, candidate, Bitmap.CompressFormat.JPEG, quality, dpi)
                }
            }
        }

        try {
            val solution = JpegQualitySolver.solve(byteCap) { quality ->
                evaluate(quality).length()
            }
                ?: throw PhotoProcessingException(PhotoProcessingException.Code.TARGET_UNREACHABLE)
            moveCandidate(
                evaluated.getValue(solution.quality),
                destination,
            )
            return solution.quality
        } finally {
            evaluated.values.forEach { if (it != destination) it.delete() }
        }
    }

    private fun writeBitmap(
        bitmap: Bitmap,
        file: File,
        format: Bitmap.CompressFormat,
        quality: Int,
        dpi: Int?,
    ) {
        try {
            file.outputStream().buffered().use { output ->
                if (!bitmap.compress(format, quality, output)) {
                    throw PhotoProcessingException(
                        PhotoProcessingException.Code.OUTPUT_WRITE_FAILED,
                    )
                }
            }
            if (dpi != null && format == Bitmap.CompressFormat.JPEG) {
                ExifInterface(file.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_X_RESOLUTION, "$dpi/1")
                    setAttribute(ExifInterface.TAG_Y_RESOLUTION, "$dpi/1")
                    setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, "2")
                    setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL.toString(),
                    )
                    saveAttributes()
                }
            } else if (dpi != null && format == Bitmap.CompressFormat.PNG) {
                PngDpiMetadata.write(file, dpi)
            }
        } catch (error: Exception) {
            file.delete()
            if (error is PhotoProcessingException) throw error
            throw PhotoProcessingException(
                if (dpi != null) {
                    PhotoProcessingException.Code.DPI_UNSUPPORTED
                } else {
                    PhotoProcessingException.Code.OUTPUT_WRITE_FAILED
                },
                error,
            )
        }
    }

    private fun inspectOutput(file: File, format: OutputFormat): PhotoFileMetadata {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0 || file.length() <= 0) {
            throw PhotoProcessingException(PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED)
        }
        val actualFormat = when (options.outMimeType) {
            "image/jpeg" -> OutputFormat.JPEG
            "image/png" -> OutputFormat.PNG
            else -> throw PhotoProcessingException(
                PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED,
            )
        }
        if (actualFormat != format) {
            throw PhotoProcessingException(PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED)
        }
        val dpi = if (actualFormat == OutputFormat.PNG) {
            PngDpiMetadata.read(file)
        } else {
            runCatching {
                ExifInterface(file.absolutePath)
                    .getAttributeDouble(ExifInterface.TAG_X_RESOLUTION, 0.0)
                    .takeIf { it > 0.0 }
                    ?.roundToInt()
            }.getOrNull()
        }
        val reopened = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = calculateValidationSample(options) },
        ) ?: throw PhotoProcessingException(PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED)
        val hasAlpha = reopened.hasAlpha()
        val isSrgb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            reopened.colorSpace?.isSrgb == true
        } else {
            true
        }
        reopened.recycle()
        return PhotoFileMetadata(
            format = actualFormat,
            widthPx = options.outWidth,
            heightPx = options.outHeight,
            byteCount = file.length(),
            dpi = dpi,
            hasAlpha = hasAlpha,
            isSrgb = isSrgb,
        )
    }

    private fun resolveTargetDimensions(
        metadata: ImageMetadata,
        plan: ProcessingPlan,
    ): Pair<Int, Int> {
        val requestedWidth = plan.output.widthPx ?: return metadata.widthPx to metadata.heightPx
        val requestedHeight = plan.output.heightPx ?: return metadata.widthPx to metadata.heightPx
        if (plan.output.dimensionRule == DimensionRule.EXACT) {
            return requestedWidth to requestedHeight
        }
        val targetRatio = requestedWidth.toFloat() / requestedHeight
        val fitPad = plan.transforms.any { it is NormalizedTransform.FitPad }
        val scale = if (fitPad) {
            val imageScale = min(
                requestedWidth.toFloat() / metadata.widthPx,
                requestedHeight.toFloat() / metadata.heightPx,
            )
            if (imageScale <= 1f) 1f else 1f / imageScale
        } else {
            val cropWidth: Float
            val cropHeight: Float
            if (metadata.widthPx.toFloat() / metadata.heightPx > targetRatio) {
                cropHeight = metadata.heightPx.toFloat()
                cropWidth = cropHeight * targetRatio
            } else {
                cropWidth = metadata.widthPx.toFloat()
                cropHeight = cropWidth / targetRatio
            }
            min(
                1f,
                min(cropWidth / requestedWidth, cropHeight / requestedHeight),
            )
        }
        return max(1, (requestedWidth * scale).roundToInt()) to
            max(1, (requestedHeight * scale).roundToInt())
    }

    private fun calculateValidationSample(options: BitmapFactory.Options): Int {
        var sample = 1
        while (max(options.outWidth, options.outHeight) / (sample * 2) >= 512) sample *= 2
        return sample
    }

    private fun createSrgbBitmap(width: Int, height: Int): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888,
                true,
                ColorSpace.get(ColorSpace.Named.SRGB),
            )
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

    private fun centredCrop(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Rect {
        val sourceRatio = sourceWidth.toFloat() / sourceHeight
        val targetRatio = targetWidth.toFloat() / targetHeight
        return if (sourceRatio > targetRatio) {
            val cropWidth = (sourceHeight * targetRatio).roundToInt()
            val left = (sourceWidth - cropWidth) / 2
            Rect(left, 0, left + cropWidth, sourceHeight)
        } else {
            val cropHeight = (sourceWidth / targetRatio).roundToInt()
            val top = (sourceHeight - cropHeight) / 2
            Rect(0, top, sourceWidth, top + cropHeight)
        }
    }

    private fun moveCandidate(source: File, destination: File) {
        destination.delete()
        if (!source.renameTo(destination)) {
            source.copyTo(destination, overwrite = true)
            source.delete()
        }
    }

    companion object {
        private const val DEFAULT_JPEG_QUALITY = 95
        private const val SUBSTANTIAL_UPSCALE_FACTOR = 1.25f
    }
}
