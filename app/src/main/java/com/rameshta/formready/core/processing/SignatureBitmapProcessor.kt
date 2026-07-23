package com.rameshta.formready.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import com.rameshta.formready.core.model.SignatureOptions
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic, local-only signature cleanup. The returned bitmap contains no source metadata.
 */
class SignatureBitmapProcessor @Inject constructor() {
    fun process(source: Bitmap, options: SignatureOptions): Bitmap {
        val left = (options.cropLeft * source.width).roundToInt().coerceIn(0, source.width - 1)
        val top = (options.cropTop * source.height).roundToInt().coerceIn(0, source.height - 1)
        val right = (options.cropRight * source.width).roundToInt().coerceIn(left + 1, source.width)
        val bottom = (options.cropBottom * source.height).roundToInt()
            .coerceIn(top + 1, source.height)
        val width = right - left
        val height = bottom - top
        val sourcePixels = IntArray(width * height)
        source.getPixels(sourcePixels, 0, width, left, top, width, height)

        val luminance = IntArray(sourcePixels.size)
        val ink = BooleanArray(sourcePixels.size)
        val contrast = options.contrastPercent / 100f
        sourcePixels.indices.forEach { index ->
            val color = sourcePixels[index]
            val raw = (
                Color.red(color) * 0.2126f +
                    Color.green(color) * 0.7152f +
                    Color.blue(color) * 0.0722f
                ).roundToInt()
            val adjusted = (((raw - 128) * contrast) + 128).roundToInt().coerceIn(0, 255)
            luminance[index] = adjusted
            ink[index] = adjusted < options.threshold && Color.alpha(color) > 16
        }
        if (options.removeSpeckles && width > 2 && height > 2) {
            removeIsolatedInk(ink, width, height)
        }

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        ink.indices.forEach { index ->
            if (ink[index]) {
                val x = index % width
                val y = index / width
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        if (maxX < minX || maxY < minY) {
            throw PhotoProcessingException(PhotoProcessingException.Code.EMPTY_SIGNATURE)
        }

        val outputPixels = IntArray(sourcePixels.size)
        sourcePixels.indices.forEach { index ->
            outputPixels[index] = renderPixel(
                source = sourcePixels[index],
                luminance = luminance[index],
                isInk = ink[index],
                options = options,
            )
        }
        var clean = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        clean.setPixels(outputPixels, 0, width, 0, 0, width, height)
        if (!options.autoCrop) return clean

        val margin = (
            max(maxX - minX + 1, maxY - minY + 1) *
                options.safeMarginPercent / 100f
            ).roundToInt()
        val cropLeft = (minX - margin).coerceAtLeast(0)
        val cropTop = (minY - margin).coerceAtLeast(0)
        val cropRight = (maxX + margin + 1).coerceAtMost(width)
        val cropBottom = (maxY + margin + 1).coerceAtMost(height)
        val cropped = Bitmap.createBitmap(
            clean,
            cropLeft,
            cropTop,
            cropRight - cropLeft,
            cropBottom - cropTop,
        )
        if (cropped !== clean) clean.recycle()
        return cropped
    }

    fun processPreview(
        source: Bitmap,
        options: SignatureOptions,
        rotationDegrees: Float,
        requestedWidth: Int,
        requestedHeight: Int,
        paddingFraction: Float,
        horizontalOffset: Float,
        verticalOffset: Float,
        backgroundArgb: Int,
    ): Bitmap {
        require(requestedWidth > 0 && requestedHeight > 0)
        require(paddingFraction in 0f..0.45f)
        require(horizontalOffset in -1f..1f)
        require(verticalOffset in -1f..1f)

        var cleaned: Bitmap? = null
        var rotated: Bitmap? = null
        try {
            cleaned = process(source, options)
            rotated = rotate(cleaned, rotationDegrees)
            if (rotated !== cleaned) {
                cleaned.recycle()
                cleaned = null
            }

            val (previewWidth, previewHeight) = boundedPreviewSize(
                requestedWidth,
                requestedHeight,
            )
            val output = Bitmap.createBitmap(
                previewWidth,
                previewHeight,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(output)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawColor(backgroundArgb)
            val availableWidth = previewWidth * (1f - paddingFraction * 2f)
            val availableHeight = previewHeight * (1f - paddingFraction * 2f)
            val scale = min(
                availableWidth / rotated.width,
                availableHeight / rotated.height,
            )
            val drawnWidth = rotated.width * scale
            val drawnHeight = rotated.height * scale
            val remainingX = previewWidth - drawnWidth
            val remainingY = previewHeight - drawnHeight
            val centreX = previewWidth / 2f + horizontalOffset * remainingX / 2f
            val centreY = previewHeight / 2f + verticalOffset * remainingY / 2f
            canvas.drawBitmap(
                rotated,
                null,
                RectF(
                    centreX - drawnWidth / 2f,
                    centreY - drawnHeight / 2f,
                    centreX + drawnWidth / 2f,
                    centreY + drawnHeight / 2f,
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            return output
        } finally {
            cleaned?.takeUnless(Bitmap::isRecycled)?.recycle()
            rotated?.takeUnless(Bitmap::isRecycled)?.recycle()
        }
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

    private fun boundedPreviewSize(width: Int, height: Int): Pair<Int, Int> {
        val scale = min(
            1f,
            PREVIEW_MAX_EDGE.toFloat() / max(width, height),
        )
        return max(1, (width * scale).roundToInt()) to
            max(1, (height * scale).roundToInt())
    }

    private fun renderPixel(
        source: Int,
        luminance: Int,
        isInk: Boolean,
        options: SignatureOptions,
    ): Int {
        if (options.cleanPaperBackground) {
            if (!isInk) {
                return if (options.transparentBackground) Color.TRANSPARENT else Color.WHITE
            }
            val alpha = if (options.transparentBackground) {
                ((options.threshold - luminance) * 5).coerceIn(48, 255)
            } else {
                255
            }
            return Color.argb(
                alpha,
                Color.red(options.inkArgb),
                Color.green(options.inkArgb),
                Color.blue(options.inkArgb),
            )
        }
        if (options.grayscale) {
            return Color.argb(Color.alpha(source), luminance, luminance, luminance)
        }
        return source
    }

    private fun removeIsolatedInk(ink: BooleanArray, width: Int, height: Int) {
        val original = ink.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (!original[index]) continue
                var neighbours = 0
                for (offsetY in -1..1) {
                    for (offsetX in -1..1) {
                        if (offsetX == 0 && offsetY == 0) continue
                        if (original[(y + offsetY) * width + x + offsetX]) neighbours++
                    }
                }
                if (neighbours < 2) ink[index] = false
            }
        }
    }

    private companion object {
        const val PREVIEW_MAX_EDGE = 960
    }
}
