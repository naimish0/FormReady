package com.rameshta.formready.core.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfPageSelectionTest {
    @Test
    fun indexesAndRotationAreBounded() {
        assertEquals(
            3,
            PdfPageSelection(
                sourceIndex = 0,
                pageIndex = 4,
                rotationQuarterTurns = 3,
            ).rotationQuarterTurns,
        )
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageSelection(sourceIndex = -1, pageIndex = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageSelection(sourceIndex = 0, pageIndex = 0, rotationQuarterTurns = 4)
        }
    }
}
