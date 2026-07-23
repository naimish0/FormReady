package com.rameshta.formready.core.processing

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.model.JobStatus
import java.util.UUID
import javax.inject.Inject

interface ProcessingScheduler {
    fun enqueue(jobId: UUID)

    fun enqueueSequence(sequenceId: UUID, jobIds: List<UUID>)

    suspend fun cancel(jobId: UUID)

    suspend fun cancelSequence(sequenceId: UUID, jobIds: List<UUID>)
}

class WorkManagerProcessingScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val jobs: JobRepository,
) : ProcessingScheduler {
    override fun enqueue(jobId: UUID) {
        val request = request(jobId)
        workManager.enqueueUniqueWork(
            uniqueName(jobId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun enqueueSequence(sequenceId: UUID, jobIds: List<UUID>) {
        require(jobIds.isNotEmpty() && jobIds.size <= MAX_BATCH_SIZE)
        val requests = jobIds.map(::request)
        var continuation = workManager.beginUniqueWork(
            batchName(sequenceId),
            ExistingWorkPolicy.KEEP,
            requests.first(),
        )
        requests.drop(1).forEach { request ->
            continuation = continuation.then(request)
        }
        continuation.enqueue()
    }

    override suspend fun cancel(jobId: UUID) {
        workManager.cancelUniqueWork(uniqueName(jobId))
        val job = jobs.get(jobId) ?: return
        when (job.status) {
            JobStatus.QUEUED ->
                jobs.transition(jobId, JobStatus.QUEUED, JobStatus.CANCELLED)
            JobStatus.RUNNING ->
                jobs.transition(jobId, JobStatus.RUNNING, JobStatus.CANCELLED)
            JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.CANCELLED -> Unit
        }
    }

    override suspend fun cancelSequence(sequenceId: UUID, jobIds: List<UUID>) {
        workManager.cancelUniqueWork(batchName(sequenceId))
        jobIds.forEach { jobId ->
            val job = jobs.get(jobId) ?: return@forEach
            when (job.status) {
                JobStatus.QUEUED ->
                    jobs.transition(jobId, JobStatus.QUEUED, JobStatus.CANCELLED)
                JobStatus.RUNNING ->
                    jobs.transition(jobId, JobStatus.RUNNING, JobStatus.CANCELLED)
                JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.CANCELLED -> Unit
            }
        }
    }

    private fun request(jobId: UUID) = OneTimeWorkRequestBuilder<ExportWorker>()
        .setInputData(Data.Builder().putString(ExportWorker.KEY_JOB_ID, jobId.toString()).build())
        .addTag(tag(jobId))
        .build()

    private fun uniqueName(jobId: UUID) = "formready-export-$jobId"

    private fun tag(jobId: UUID) = "formready-job-$jobId"

    private fun batchName(sequenceId: UUID) = "formready-batch-$sequenceId"

    private companion object {
        const val MAX_BATCH_SIZE = 50
    }
}
