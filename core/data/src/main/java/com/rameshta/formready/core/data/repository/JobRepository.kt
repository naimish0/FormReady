package com.rameshta.formready.core.data.repository

import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.ProcessingJob
import com.rameshta.formready.core.model.ProcessingPlan
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface JobRepository {
    suspend fun create(
        plan: ProcessingPlan,
        projectId: UUID?,
        type: JobType,
        serializedPlan: String,
    )

    suspend fun get(jobId: UUID): ProcessingJob?

    suspend fun transition(
        jobId: UUID,
        expected: JobStatus,
        next: JobStatus,
        errorCode: String? = null,
    ): Boolean

    fun observeRecent(limit: Int): Flow<List<ProcessingJob>>
}
