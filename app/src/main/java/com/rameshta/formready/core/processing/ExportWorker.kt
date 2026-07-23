package com.rameshta.formready.core.processing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.model.JobStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.UUID

@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val jobs: JobRepository,
    private val processors: Set<@JvmSuppressWildcards JobProcessor>,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return Result.failure()
        val job = jobs.get(jobId) ?: return Result.failure()
        if (!jobs.transition(jobId, JobStatus.QUEUED, JobStatus.RUNNING)) {
            return if (job.status == JobStatus.CANCELLED) Result.success() else Result.failure()
        }

        val processor = processors.singleOrNull { it.supportedType == job.type }
        if (processor == null) {
            jobs.transition(
                jobId,
                JobStatus.RUNNING,
                JobStatus.FAILED,
                ERROR_PROCESSOR_UNAVAILABLE,
            )
            return Result.success()
        }

        return try {
            when (val result = processor.process(jobId)) {
                ProcessorResult.Success -> {
                    jobs.transition(jobId, JobStatus.RUNNING, JobStatus.SUCCEEDED)
                    Result.success()
                }
                is ProcessorResult.Failure -> {
                    jobs.transition(jobId, JobStatus.RUNNING, JobStatus.FAILED, result.errorCode)
                    // The durable job row is the product result. Completing the WorkManager node
                    // lets a strictly ordered batch continue after an individual item fails.
                    Result.success()
                }
            }
        } catch (cancellation: CancellationException) {
            jobs.transition(jobId, JobStatus.RUNNING, JobStatus.CANCELLED)
            throw cancellation
        } catch (_: Exception) {
            jobs.transition(jobId, JobStatus.RUNNING, JobStatus.FAILED, ERROR_PROCESSING_FAILED)
            Result.success()
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val ERROR_PROCESSOR_UNAVAILABLE = "PROCESSOR_UNAVAILABLE"
        const val ERROR_PROCESSING_FAILED = "PROCESSING_FAILED"
    }
}
