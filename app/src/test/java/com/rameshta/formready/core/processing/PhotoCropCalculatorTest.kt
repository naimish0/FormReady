package com.rameshta.formready.core.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCropCalculatorTest {
    @Test
    fun cropAlwaysRemainsInsideNormalizedBounds() {
        val crop = PhotoCropCalculator.calculate(
            sourceWidthPx = 4_000,
            sourceHeightPx = 3_000,
            targetWidthPx = 600,
            targetHeightPx = 800,
            zoom = 3f,
            panX = 1f,
            panY = -1f,
            quarterTurns = 0,
        )

        assertTrue(crop.left in 0f..1f)
        assertTrue(crop.top in 0f..1f)
        assertTrue(crop.right in 0f..1f)
        assertTrue(crop.bottom in 0f..1f)
        assertTrue(crop.left < crop.right)
        assertTrue(crop.top < crop.bottom)
    }

    @Test
    fun quarterTurnSwapsSourceAxesBeforeCropping() {
        val unrotated = PhotoCropCalculator.calculate(
            1_200,
            800,
            600,
            800,
            1f,
            0f,
            0f,
            0,
        )
        val rotated = PhotoCropCalculator.calculate(
            1_200,
            800,
            600,
            800,
            1f,
            0f,
            0f,
            1,
        )

        assertTrue(unrotated.right - unrotated.left < 1f)
        assertEquals(1f, rotated.right - rotated.left, 0.0001f)
    }
}
