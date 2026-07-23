package com.rameshta.formready.core.processing

import android.content.Context
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.data.repository.OutputArtifactRepository
import com.rameshta.formready.core.data.repository.TimeProvider
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.Readiness
import com.rameshta.formready.core.model.readiness
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class PhotoJobProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val jobs: JobRepository,
    private val outputs: OutputArtifactRepository,
    private val imageTransformEngine: ImageTransformEngine,
    private val inputStager: InputStager,
    private val timeProvider: TimeProvider,
    private val processingGate: ImageProcessingGate,
) : JobProcessor {
    override val supportedType: JobType = JobType.PHOTO

    override suspend fun process(jobId: UUID): ProcessorResult = processingGate.run {
        withContext(Dispatchers.IO) {
        val job = jobs.get(jobId) ?: return@withContext ProcessorResult.Failure(ERROR_JOB_MISSING)
        val relativeInput = job.stagedInputRelativePath
            ?: return@withContext ProcessorResult.Failure(ERROR_INPUT_MISSING)
        val input = resolvePrivateInput(relativeInput)
            ?: return@withContext ProcessorResult.Failure(ERROR_INPUT_MISSING)
        val plan = runCatching { PhotoPlanCodec.decode(job.serializedPlan) }.getOrElse {
            return@withContext ProcessorResult.Failure(ERROR_PLAN_INVALID)
        }
        if (plan.jobId != jobId) {
            return@withContext ProcessorResult.Failure(ERROR_PLAN_INVALID)
        }

        val outputDirectory = File(context.filesDir, PrivatePhotoOutputAccess.OUTPUT_DIRECTORY)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            return@withContext ProcessorResult.Failure(
                PhotoProcessingException.Code.OUTPUT_WRITE_FAILED.name,
            )
        }
        val extension = when (plan.output.format) {
            OutputFormat.JPEG -> "jpg"
            OutputFormat.PNG -> "png"
            OutputFormat.PDF -> return@withContext ProcessorResult.Failure(ERROR_PLAN_INVALID)
        }
        val partial = File(outputDirectory, "$jobId.$extension.part")
        val final = File(outputDirectory, "$jobId.$extension")
        partial.delete()
        final.delete()

        try {
            val prepared = imageTransformEngine.prepare(input, partial, plan)
            if (!partial.isFile || partial.length() != prepared.byteCount) {
                throw PhotoProcessingException(
                    PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED,
                )
            }
            if (!partial.renameTo(final)) {
                partial.copyTo(final, overwrite = true)
                partial.delete()
            }
            val readiness = prepared.validationResults.readiness()
            if (readiness == Readiness.NOT_READY) {
                final.delete()
                return@withContext ProcessorResult.Failure(
                    PhotoProcessingException.Code.OUTPUT_VALIDATION_FAILED.name,
                )
            }
            outputs.add(
                OutputArtifact(
                    id = UUID.randomUUID(),
                    jobId = jobId,
                    uri = "${PrivatePhotoOutputAccess.OUTPUT_DIRECTORY}/${final.name}",
                    displayName = "FormReady-photo-$jobId.$extension",
                    mimeType = if (prepared.format == OutputFormat.JPEG) {
                        "image/jpeg"
                    } else {
                        "image/png"
                    },
                    byteCount = prepared.byteCount,
                    widthPx = prepared.widthPx,
                    heightPx = prepared.heightPx,
                    dpi = prepared.dpi,
                    readiness = readiness,
                    validationJson = ValidationResultCodec.encode(prepared.validationResults),
                    createdAtEpochMillis = timeProvider.currentTimeMillis(),
                ),
            )
            inputStager.remove(jobId)
            ProcessorResult.Success
        } catch (error: PhotoProcessingException) {
            partial.delete()
            final.delete()
            ProcessorResult.Failure(error.code.name)
        } catch (cancellation: CancellationException) {
            partial.delete()
            final.delete()
            inputStager.remove(jobId)
            throw cancellation
        } catch (_: Exception) {
            partial.delete()
            final.delete()
            ProcessorResult.Failure(ExportWorker.ERROR_PROCESSING_FAILED)
        }
        }
    }

    private fun resolvePrivateInput(relativePath: String): File? {
        val root = context.noBackupFilesDir.canonicalFile
        val file = File(root, relativePath).canonicalFile
        return file.takeIf {
            it.path.startsWith("${root.path}${File.separator}") && it.isFile
        }
    }

    companion object {
        const val ERROR_JOB_MISSING = "JOB_MISSING"
        const val ERROR_INPUT_MISSING = "INPUT_MISSING"
        const val ERROR_PLAN_INVALID = "PLAN_INVALID"
    }
}
