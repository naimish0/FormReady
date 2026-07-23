package com.rameshta.formready.core.data.repository

import com.rameshta.formready.core.data.local.ProcessingJobDao
import com.rameshta.formready.core.data.local.ProcessingJobEntity
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.ProcessingJob
import com.rameshta.formready.core.model.ProcessingPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class RoomJobRepository @Inject constructor(
    private val dao: ProcessingJobDao,
    private val timeProvider: TimeProvider,
) : JobRepository {
    override suspend fun create(
        plan: ProcessingPlan,
        projectId: UUID?,
        type: JobType,
        serializedPlan: String,
        stagedInputRelativePath: String,
    ) {
        val now = timeProvider.currentTimeMillis()
        dao.insert(
            ProcessingJobEntity(
                id = plan.jobId.toString(),
                projectId = projectId?.toString(),
                type = type.name,
                status = JobStatus.QUEUED.name,
                processingPlanJson = serializedPlan,
                stagedInputRelativePath = stagedInputRelativePath,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                errorCode = null,
            ),
        )
    }

    override suspend fun get(jobId: UUID): ProcessingJob? = dao.get(jobId.toString())?.toModel()

    override fun observe(jobId: UUID): Flow<ProcessingJob?> =
        dao.observe(jobId.toString()).map { it?.toModel() }

    override suspend fun transition(
        jobId: UUID,
        expected: JobStatus,
        next: JobStatus,
        errorCode: String?,
    ): Boolean {
        require(expected.canTransitionTo(next)) {
            "Invalid job transition: $expected -> $next"
        }
        return dao.transition(
            id = jobId.toString(),
            expectedStatus = expected.name,
            newStatus = next.name,
            updatedAtEpochMillis = timeProvider.currentTimeMillis(),
            errorCode = errorCode,
        ) == 1
    }

    override fun observeRecent(limit: Int): Flow<List<ProcessingJob>> {
        require(limit in 1..100)
        return dao.observeRecent(limit).map { jobs -> jobs.map { it.toModel() } }
    }

    private fun ProcessingJobEntity.toModel() = ProcessingJob(
        id = UUID.fromString(id),
        projectId = projectId?.let(UUID::fromString),
        type = JobType.valueOf(type),
        status = JobStatus.valueOf(status),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        errorCode = errorCode,
        serializedPlan = processingPlanJson,
        stagedInputRelativePath = stagedInputRelativePath,
    )
}

fun interface TimeProvider {
    fun currentTimeMillis(): Long
}
