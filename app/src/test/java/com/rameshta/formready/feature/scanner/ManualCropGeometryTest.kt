package com.rameshta.formready.feature.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualCropGeometryTest {
    @Test
    fun acceptsOrderedConvexFourCornerCrop() {
        assertTrue(
            ManualCropGeometry.isValid(
                listOf(0.05f, 0.1f, 0.9f, 0.05f, 0.95f, 0.9f, 0.1f, 0.95f),
            ),
        )
    }

    @Test
    fun rejectsCrossedCollapsedAndOutOfBoundsCorners() {
        assertFalse(
            ManualCropGeometry.isValid(
                listOf(0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f),
            ),
        )
        assertFalse(ManualCropGeometry.isValid(List(8) { 0.5f }))
        assertFalse(
            ManualCropGeometry.isValid(
                listOf(-0.1f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            ),
        )
    }
}
