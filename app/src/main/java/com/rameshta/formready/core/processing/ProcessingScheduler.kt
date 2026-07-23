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

    suspend fun cancel(jobId: UUID)
}

class WorkManagerProcessingScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val jobs: JobRepository,
) : ProcessingScheduler {
    override fun enqueue(jobId: UUID) {
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(Data.Builder().putString(ExportWorker.KEY_JOB_ID, jobId.toString()).build())
            .addTag(tag(jobId))
            .build()
        workManager.enqueueUniqueWork(
            uniqueName(jobId),
            ExistingWorkPolicy.KEEP,
            request,
        )
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

    private fun uniqueName(jobId: UUID) = "formready-export-$jobId"

    private fun tag(jobId: UUID) = "formready-job-$jobId"
}
