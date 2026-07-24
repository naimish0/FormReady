package com.rameshta.formready.feature.photo

import com.rameshta.formready.core.model.JobStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoUiStateTest {
    @Test
    fun photoSelectionIsAvailableBeforeAndAfterTerminalJobs() {
        assertTrue(PhotoUiState().canSelectPhoto)
        assertTrue(PhotoUiState(jobStatus = JobStatus.SUCCEEDED).canSelectPhoto)
        assertTrue(PhotoUiState(jobStatus = JobStatus.FAILED).canSelectPhoto)
        assertTrue(PhotoUiState(jobStatus = JobStatus.CANCELLED).canSelectPhoto)
    }

    @Test
    fun photoSelectionIsBlockedOnlyWhileInputOrProcessingIsActive() {
        assertFalse(PhotoUiState(isLoadingInput = true).canSelectPhoto)
        assertFalse(PhotoUiState(jobStatus = JobStatus.QUEUED).canSelectPhoto)
        assertFalse(PhotoUiState(jobStatus = JobStatus.RUNNING).canSelectPhoto)
    }
}
