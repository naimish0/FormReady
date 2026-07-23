package com.rameshta.formready.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ProcessingModelsTest {
    @Test
    fun jobStateMachine_allowsOnlyDocumentedTransitions() {
        assertTrue(JobStatus.QUEUED.canTransitionTo(JobStatus.RUNNING))
        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.SUCCEEDED))
        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.FAILED))
        assertFalse(JobStatus.SUCCEEDED.canTransitionTo(JobStatus.RUNNING))
        assertFalse(JobStatus.CANCELLED.canTransitionTo(JobStatus.QUEUED))
    }

    @Test
    fun hardFailure_isNotReady() {
        val result = listOf(
            ValidationRuleResult(
                ruleId = "maximum-bytes",
                outcome = ValidationOutcome.FAIL,
                expected = "200000 bytes",
                actual = "210000 bytes",
                explanation = "The file exceeds the selected maximum.",
                fixAction = "Reduce file size",
                isHardRule = true,
            ),
        )

        assertEquals(Readiness.NOT_READY, result.readiness())
    }

    @Test
    fun advisoryWarning_isReadyWithWarnings() {
        val result = listOf(
            ValidationRuleResult(
                ruleId = "quality",
                outcome = ValidationOutcome.WARNING,
                expected = "Quality 55 or higher",
                actual = "Quality 50",
                explanation = "Fine detail may be reduced.",
                fixAction = "Increase quality",
                isHardRule = false,
            ),
        )

        assertEquals(Readiness.READY_WITH_WARNINGS, result.readiness())
    }

    @Test
    fun signatureOptionsRejectContradictoryCropAndUnsafeControls() {
        assertThrows(IllegalArgumentException::class.java) {
            SignatureOptions(cropLeft = 0.8f, cropRight = 0.2f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SignatureOptions(threshold = 255)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedTransform.FitPad(
                backgroundArgb = 0,
                paddingFraction = 0.5f,
            )
        }
    }

    @Test
    fun strongPdfCompressionRequiresAcknowledgementAndBoundedPolicy() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfOptions(compressionMode = PdfCompressionMode.STRONG_FLATTEN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfOptions(flatteningAcknowledged = true, maximumPasses = 7)
        }
        assertEquals(
            PdfCompressionMode.STRONG_FLATTEN,
            PdfOptions(
                compressionMode = PdfCompressionMode.STRONG_FLATTEN,
                flatteningAcknowledged = true,
            ).compressionMode,
        )
    }
}
