package com.rameshta.formready.core.processing

import android.content.Context
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.data.repository.OutputArtifactRepository
import com.rameshta.formready.core.data.repository.TimeProvider
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.readiness
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfJobProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val jobs: JobRepository,
    private val outputs: OutputArtifactRepository,
    private val pdfEngine: PdfEngine,
    private val inputStager: InputStager,
    private val timeProvider: TimeProvider,
    private val processingGate: ImageProcessingGate,
) : JobProcessor {
    override val supportedType: JobType = JobType.PDF

    override suspend fun process(jobId: UUID): ProcessorResult = processingGate.run {
        withContext(Dispatchers.IO) {
            val job = jobs.get(jobId)
                ?: return@withContext ProcessorResult.Failure(PhotoJobProcessor.ERROR_JOB_MISSING)
            val input = job.stagedInputRelativePath?.let(::resolvePrivateInput)
                ?: return@withContext ProcessorResult.Failure(PhotoJobProcessor.ERROR_INPUT_MISSING)
            val plan = runCatching { PhotoPlanCodec.decode(job.serializedPlan) }.getOrElse {
                return@withContext ProcessorResult.Failure(PhotoJobProcessor.ERROR_PLAN_INVALID)
            }
            if (
                plan.jobId != jobId ||
                plan.output.format != OutputFormat.PDF ||
                plan.pdfOptions == null
            ) {
                return@withContext ProcessorResult.Failure(PhotoJobProcessor.ERROR_PLAN_INVALID)
            }
            val outputDirectory = File(
                context.filesDir,
                PrivatePhotoOutputAccess.OUTPUT_DIRECTORY,
            ).apply { mkdirs() }
            val partial = File(outputDirectory, "$jobId.pdf.part")
            val final = File(outputDirectory, "$jobId.pdf")
            partial.delete()
            final.delete()
            try {
                val prepared = pdfEngine.flatten(input, partial, plan)
                if (!partial.isFile || partial.length() != prepared.byteCount) {
                    throw PdfProcessingException(
                        PdfProcessingException.Code.OUTPUT_VALIDATION_FAILED,
                    )
                }
                if (!partial.renameTo(final)) {
                    partial.copyTo(final, overwrite = true)
                    partial.delete()
                }
                val readiness = prepared.validationResults.readiness()
                outputs.add(
                    OutputArtifact(
                        id = UUID.randomUUID(),
                        jobId = jobId,
                        uri = "${PrivatePhotoOutputAccess.OUTPUT_DIRECTORY}/${final.name}",
                        displayName = "FormReady-PDF-$jobId.pdf",
                        mimeType = "application/pdf",
                        byteCount = prepared.byteCount,
                        widthPx = null,
                        heightPx = null,
                        dpi = prepared.renderDpi,
                        readiness = readiness,
                        validationJson = ValidationResultCodec.encode(
                            prepared.validationResults,
                        ),
                        createdAtEpochMillis = timeProvider.currentTimeMillis(),
                    ),
                )
                inputStager.remove(jobId)
                ProcessorResult.Success
            } catch (error: PdfProcessingException) {
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
}
