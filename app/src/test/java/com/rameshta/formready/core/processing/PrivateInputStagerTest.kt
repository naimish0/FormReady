package com.rameshta.formready.core.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateInputStagerTest {
    @Test
    fun inputLimit_isExactlyTwoHundredMebibytes() {
        assertEquals(209_715_200L, PrivateInputStager.MAX_STAGED_INPUT_BYTES)
    }
}
