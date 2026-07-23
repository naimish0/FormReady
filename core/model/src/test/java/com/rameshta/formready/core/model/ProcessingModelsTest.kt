package com.rameshta.formready.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
