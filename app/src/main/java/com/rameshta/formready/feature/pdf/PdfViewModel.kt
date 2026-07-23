package com.rameshta.formready.feature.pdf

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.data.repository.OutputArtifactRepository
import com.rameshta.formready.core.data.repository.TimeProvider
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.OutputSpecification
import com.rameshta.formready.core.model.PdfCompressionMode
import com.rameshta.formready.core.model.PdfOptions
import com.rameshta.formready.core.model.ProcessingPlan
import com.rameshta.formready.core.model.ValidationRuleResult
import com.rameshta.formready.core.model.readiness
import com.rameshta.formready.core.processing.PdfEngine
import com.rameshta.formready.core.processing.PdfMetadata
import com.rameshta.formready.core.processing.PdfPreparationService
import com.rameshta.formready.core.processing.PhotoOutputAccess
import com.rameshta.formready.core.processing.PhotoPlanCodec
import com.rameshta.formready.core.processing.PrivateInputStager
import com.rameshta.formready.core.processing.PrivatePhotoOutputAccess
import com.rameshta.formready.core.processing.ProcessingScheduler
import com.rameshta.formready.core.processing.ValidationResultCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfResultUi(
    val artifact: OutputArtifact,
    val validations: List<ValidationRuleResult>,
    val shareUri: Uri,
)

data class PdfUiState(
    val metadata: PdfMetadata? = null,
    val preview: ImageBitmap? = null,
    val previewPage: Int = 0,
    val maximumSizeText: String = "1000",
    val maximumPagesText: String = "100",
    val flatteningAcknowledged: Boolean = false,
    val isBusy: Boolean = false,
    val jobStatus: JobStatus? = null,
    val errorCode: String? = null,
    val result: PdfResultUi? = null,
    val isSaving: Boolean = false,
) {
    val hasDraft: Boolean get() = metadata != null || isBusy || jobStatus != null
}

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val preparation: PdfPreparationService,
    private val pdfEngine: PdfEngine,
    private val jobs: JobRepository,
    private val outputs: OutputArtifactRepository,
    private val scheduler: ProcessingScheduler,
    private val outputAccess: PhotoOutputAccess,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        PdfUiState(
            maximumSizeText = savedStateHandle[KEY_MAXIMUM_SIZE] ?: "1000",
            maximumPagesText = savedStateHandle[KEY_MAXIMUM_PAGES] ?: "100",
            flatteningAcknowledged = savedStateHandle[KEY_ACKNOWLEDGED] ?: false,
        ),
    )
    val uiState: StateFlow<PdfUiState> = mutableState.asStateFlow()
    private var draftId: UUID? = savedStateHandle.get<String>(KEY_DRAFT_ID)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private var jobObserver: Job? = null
    private var outputObserver: Job? = null

    init {
        draftId?.let(::restoreDraft)
    }

    fun selectPdf(uri: Uri) {
        viewModelScope.launch {
            val previous = draftId
            val id = UUID.randomUUID()
            mutableState.update { it.copy(isBusy = true, errorCode = null, result = null) }
            runCatching {
                val metadata = preparation.stageAndInspect(uri, id)
                val preview = preparation.renderPreview(id, 0)
                metadata to preview
            }.onSuccess { (metadata, preview) ->
                previous?.let { preparation.discard(it) }
                draftId = id
                savedStateHandle[KEY_DRAFT_ID] = id.toString()
                mutableState.value = PdfUiState(
                    metadata = metadata,
                    preview = preview.asImageBitmap(),
                    maximumSizeText = mutableState.value.maximumSizeText,
                    maximumPagesText = mutableState.value.maximumPagesText,
                    flatteningAcknowledged = mutableState.value.flatteningAcknowledged,
                )
            }.onFailure { error ->
                preparation.discard(id)
                mutableState.update {
                    it.copy(isBusy = false, errorCode = error.message ?: "PDF_INPUT_FAILED")
                }
            }
        }
    }

    fun showPage(index: Int) {
        val id = draftId ?: return
        val count = mutableState.value.metadata?.pageCount ?: return
        if (index !in 0 until count || mutableState.value.isBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorCode = null) }
            runCatching { preparation.renderPreview(id, index) }
                .onSuccess { bitmap ->
                    mutableState.update {
                        it.copy(
                            preview = bitmap.asImageBitmap(),
                            previewPage = index,
                            isBusy = false,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(isBusy = false, errorCode = error.message ?: "PDF_PREVIEW_FAILED")
                    }
                }
        }
    }

    fun setMaximumSize(value: String) {
        val filtered = value.filter(Char::isDigit).take(9)
        savedStateHandle[KEY_MAXIMUM_SIZE] = filtered
        mutableState.update { it.copy(maximumSizeText = filtered) }
    }

    fun setMaximumPages(value: String) {
        val filtered = value.filter(Char::isDigit).take(3)
        savedStateHandle[KEY_MAXIMUM_PAGES] = filtered
        mutableState.update { it.copy(maximumPagesText = filtered) }
    }

    fun setAcknowledged(value: Boolean) {
        savedStateHandle[KEY_ACKNOWLEDGED] = value
        mutableState.update { it.copy(flatteningAcknowledged = value) }
    }

    fun startStrongCompression() {
        val id = draftId ?: return
        val state = mutableState.value
        val cap = state.maximumSizeText.toLongOrNull()
            ?.let { runCatching { Math.multiplyExact(it, 1_000L) }.getOrNull() }
        if (cap == null || cap <= 0 || !state.flatteningAcknowledged) {
            mutableState.update { it.copy(errorCode = "PDF_ACKNOWLEDGEMENT_OR_SIZE_REQUIRED") }
            return
        }
        val plan = ProcessingPlan(
            jobId = id,
            transforms = emptyList(),
            output = OutputSpecification(
                format = OutputFormat.PDF,
                maximumBytes = cap,
            ),
            hardRuleIds = setOf("pdf.readable", "pdf.maximum_bytes", "pdf.page_count"),
            advisoryRuleIds = setOf("pdf.flattened"),
            pdfOptions = PdfOptions(
                compressionMode = PdfCompressionMode.STRONG_FLATTEN,
                flatteningAcknowledged = true,
            ),
        )
        mutableState.update { it.copy(jobStatus = JobStatus.QUEUED, errorCode = null) }
        viewModelScope.launch {
            runCatching {
                jobs.create(
                    plan = plan,
                    projectId = null,
                    type = JobType.PDF,
                    serializedPlan = PhotoPlanCodec.encode(plan),
                    stagedInputRelativePath = preparation.stagedRelativePath(id),
                )
                observeJob(id)
                scheduler.enqueue(id)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(jobStatus = null, errorCode = error.message ?: "PDF_EXPORT_FAILED")
                }
            }
        }
    }

    fun imagesToPdf(uris: List<Uri>) {
        if (uris.isEmpty() || uris.size > MAX_IMAGE_PAGES) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorCode = null, result = null) }
            val id = UUID.randomUUID()
            val inputDirectory = File(context.noBackupFilesDir, "images-to-pdf/$id")
            val outputDirectory = File(
                context.filesDir,
                PrivatePhotoOutputAccess.OUTPUT_DIRECTORY,
            ).apply { mkdirs() }
            val destination = File(outputDirectory, "$id.pdf")
            try {
                inputDirectory.mkdirs()
                var total = 0L
                val files = uris.mapIndexed { index, uri ->
                    File(inputDirectory, index.toString()).also { file ->
                        context.contentResolver.openInputStream(uri)?.buffered()?.use { source ->
                            file.outputStream().buffered().use { sink ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val read = source.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    check(total <= PrivateInputStager.MAX_STAGED_INPUT_BYTES)
                                    sink.write(buffer, 0, read)
                                }
                            }
                        } ?: error("IMAGE_SOURCE_UNAVAILABLE")
                    }
                }
                val plan = ProcessingPlan(
                    jobId = id,
                    transforms = emptyList(),
                    output = OutputSpecification(format = OutputFormat.PDF),
                    hardRuleIds = setOf("pdf.readable", "pdf.page_count"),
                    advisoryRuleIds = setOf("pdf.flattened"),
                    pdfOptions = PdfOptions(),
                )
                jobs.create(
                    plan,
                    null,
                    JobType.PDF,
                    PhotoPlanCodec.encode(plan),
                    "images-to-pdf/$id",
                )
                check(jobs.transition(id, JobStatus.QUEUED, JobStatus.RUNNING))
                val prepared = pdfEngine.imagesToPdf(files, destination)
                val artifact = OutputArtifact(
                    id = UUID.randomUUID(),
                    jobId = id,
                    uri = "${PrivatePhotoOutputAccess.OUTPUT_DIRECTORY}/${destination.name}",
                    displayName = "FormReady-images-$id.pdf",
                    mimeType = "application/pdf",
                    byteCount = prepared.byteCount,
                    widthPx = null,
                    heightPx = null,
                    dpi = null,
                    readiness = prepared.validationResults.readiness(),
                    validationJson = ValidationResultCodec.encode(prepared.validationResults),
                    createdAtEpochMillis = timeProvider.currentTimeMillis(),
                )
                outputs.add(artifact)
                check(jobs.transition(id, JobStatus.RUNNING, JobStatus.SUCCEEDED))
                mutableState.update {
                    it.copy(
                        isBusy = false,
                        result = PdfResultUi(
                            artifact,
                            prepared.validationResults,
                            outputAccess.shareUri(artifact),
                        ),
                    )
                }
            } catch (error: Exception) {
                destination.delete()
                jobs.transition(id, JobStatus.RUNNING, JobStatus.FAILED, error.message)
                mutableState.update {
                    it.copy(isBusy = false, errorCode = error.message ?: "IMAGES_TO_PDF_FAILED")
                }
            } finally {
                inputDirectory.deleteRecursively()
            }
        }
    }

    fun saveTo(uri: Uri) {
        val artifact = mutableState.value.result?.artifact ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true) }
            runCatching { outputAccess.copyTo(artifact, uri) }
                .onSuccess { mutableState.update { it.copy(isSaving = false) } }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(isSaving = false, errorCode = error.message ?: "SAVE_FAILED")
                    }
                }
        }
    }

    fun cancel() {
        draftId?.let { id -> viewModelScope.launch { scheduler.cancel(id) } }
    }

    fun prepareAnother() {
        draftId = null
        savedStateHandle.remove<String>(KEY_DRAFT_ID)
        jobObserver?.cancel()
        outputObserver?.cancel()
        mutableState.value = PdfUiState()
    }

    fun discard(onFinished: () -> Unit) {
        val id = draftId
        viewModelScope.launch {
            if (id != null && mutableState.value.jobStatus != JobStatus.SUCCEEDED) {
                preparation.discard(id)
            }
            draftId = null
            savedStateHandle.remove<String>(KEY_DRAFT_ID)
            onFinished()
        }
    }

    fun reportNoApp() {
        mutableState.update { it.copy(errorCode = "NO_COMPATIBLE_APP") }
    }

    private fun observeJob(id: UUID) {
        jobObserver?.cancel()
        outputObserver?.cancel()
        jobObserver = viewModelScope.launch {
            jobs.observe(id).collect { job ->
                mutableState.update {
                    it.copy(jobStatus = job?.status, errorCode = job?.errorCode ?: it.errorCode)
                }
            }
        }
        outputObserver = viewModelScope.launch {
            outputs.observeLatestForJob(id).collect { artifact ->
                if (artifact != null) {
                    mutableState.update {
                        it.copy(
                            result = PdfResultUi(
                                artifact,
                                ValidationResultCodec.decode(artifact.validationJson),
                                outputAccess.shareUri(artifact),
                            ),
                            errorCode = null,
                        )
                    }
                }
            }
        }
    }

    private fun restoreDraft(id: UUID) {
        viewModelScope.launch {
            val job = jobs.get(id)
            if (job != null) {
                observeJob(id)
                return@launch
            }
            runCatching {
                preparation.inspectStaged(id) to preparation.renderPreview(id, 0)
            }.onSuccess { (metadata, preview) ->
                mutableState.update {
                    it.copy(metadata = metadata, preview = preview.asImageBitmap())
                }
            }.onFailure {
                draftId = null
                savedStateHandle.remove<String>(KEY_DRAFT_ID)
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_PAGES = 100
        const val KEY_DRAFT_ID = "pdf.draftId"
        const val KEY_MAXIMUM_SIZE = "pdf.maximumSize"
        const val KEY_MAXIMUM_PAGES = "pdf.maximumPages"
        const val KEY_ACKNOWLEDGED = "pdf.acknowledged"
    }
}
