package com.rameshta.formready.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class UserFacingTextTest {
    @Test
    fun fileSizesUseFamiliarDecimalUnits() {
        assertEquals("< 1 KB", readableFileSize(999))
        assertEquals("1 KB", readableFileSize(1_000))
        assertEquals("123.5 KB", readableFileSize(123_456))
        assertEquals("1 MB", readableFileSize(1_000_000))
        assertEquals("1.5 MB", readableFileSize(1_500_000))
    }

    @Test
    fun validationValuesHideRawBytesAndUpscalingJargon() {
        assertEquals("At most 200 KB", readableValidationValue("At most 200000 bytes"))
        assertEquals(
            "The image was enlarged significantly",
            readableValidationValue("Substantial upscaling"),
        )
    }
}
