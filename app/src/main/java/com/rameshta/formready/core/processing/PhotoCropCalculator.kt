package com.rameshta.formready.core.processing

import com.rameshta.formready.core.model.NormalizedTransform

object PhotoCropCalculator {
    fun calculate(
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
        zoom: Float,
        panX: Float,
        panY: Float,
        quarterTurns: Int,
    ): NormalizedTransform.Crop {
        require(sourceWidthPx > 0 && sourceHeightPx > 0)
        require(targetWidthPx > 0 && targetHeightPx > 0)
        require(zoom in 1f..3f)
        require(panX in -1f..1f && panY in -1f..1f)

        val swapsAxes = quarterTurns.mod(2) != 0
        val sourceWidth = if (swapsAxes) sourceHeightPx else sourceWidthPx
        val sourceHeight = if (swapsAxes) sourceWidthPx else sourceHeightPx
        val sourceRatio = sourceWidth.toFloat() / sourceHeight
        val targetRatio = targetWidthPx.toFloat() / targetHeightPx
        var widthFraction = 1f
        var heightFraction = 1f
        if (sourceRatio > targetRatio) {
            widthFraction = targetRatio / sourceRatio
        } else {
            heightFraction = sourceRatio / targetRatio
        }
        widthFraction = (widthFraction / zoom).coerceIn(MINIMUM_CROP_FRACTION, 1f)
        heightFraction = (heightFraction / zoom).coerceIn(MINIMUM_CROP_FRACTION, 1f)
        val left = ((1f - widthFraction) / 2f) * (panX + 1f)
        val top = ((1f - heightFraction) / 2f) * (panY + 1f)
        return NormalizedTransform.Crop(
            left = left.coerceIn(0f, 1f - widthFraction),
            top = top.coerceIn(0f, 1f - heightFraction),
            right = (left + widthFraction).coerceIn(widthFraction, 1f),
            bottom = (top + heightFraction).coerceIn(heightFraction, 1f),
        )
    }

    private const val MINIMUM_CROP_FRACTION = 0.05f
}
