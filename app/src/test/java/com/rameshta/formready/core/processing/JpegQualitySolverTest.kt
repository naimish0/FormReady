package com.rameshta.formready.core.processing

import com.rameshta.formready.core.model.TargetSizePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegQualitySolverTest {
    @Test
    fun selectsHighestMeasuredQualityThatFits() {
        val solution = requireNotNull(
            JpegQualitySolver.solve(byteCap = 70_000) { quality -> quality * 1_000L },
        )

        assertEquals(69, solution.quality)
        assertTrue(solution.probes.size <= TargetSizePolicy.MAXIMUM_FULL_ENCODE_PASSES)
        assertTrue(solution.probes.first { it.quality == solution.quality }.byteCount <= 70_000)
    }

    @Test
    fun preservesPassingCandidateWhenEncoderIsNonMonotonic() {
        val sizes = mapOf(
            100 to 120_000L,
            75 to 65_000L,
            50 to 85_000L,
            25 to 40_000L,
            88 to 110_000L,
            82 to 95_000L,
        )
        val solution = requireNotNull(
            JpegQualitySolver.solve(byteCap = 70_000) { quality ->
                sizes[quality] ?: 100_000L
            },
        )

        assertEquals(75, solution.quality)
        assertTrue(solution.probes.size <= TargetSizePolicy.MAXIMUM_FULL_ENCODE_PASSES)
    }

    @Test
    fun returnsNullWhenQualityFloorCannotMeetCap() {
        val solution = JpegQualitySolver.solve(byteCap = 10_000) { 20_000L }

        assertNull(solution)
    }
}
