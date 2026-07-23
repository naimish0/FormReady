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
import com.rameshta.formready.core.processing.PdfPageSelection
import com.rameshta.formready.core.processing.PhotoOutputAccess
import com.rameshta.formready.core.processing.PhotoPlanCodec
import com.rameshta.formready.core.processing.PrivateInputStager
import com.rameshta.formready.core.processing.PrivatePhotoOutputAccess
import com.rameshta.formready.core.processing.ProcessingScheduler
import com.rameshta.formready.core.processing.StructurePreservingPdfEngine
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

data class PdfOperationSourceUi(
    val id: UUID,
    val metadata: PdfMetadata,
)

data class PdfOperationPageUi(
    val sourceIndex: Int,
    val pageIndex: Int,
    val rotationQuarterTurns: Int = 0,
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
    val operationSources: List<PdfOperationSourceUi> = emptyList(),
    val operationPages: List<PdfOperationPageUi> = emptyList(),
    val isOperationMode: Boolean = false,
) {
    val hasDraft: Boolean
        get() = metadata != null || operationSources.isNotEmpty() || isBusy || jobStatus != null
}

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val preparation: PdfPreparationService,
    private val pdfEngine: PdfEngine,
    private val structureEngine: StructurePreservingPdfEngine,
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
    private var pageOperationJob: Job? = null
    private val restoredOperationIds: List<UUID> =
        savedStateHandle.get<Array<String>>(KEY_OPERATION_IDS)
            ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .orEmpty()

    init {
        if (restoredOperationIds.isNotEmpty()) {
            restoreOperations(restoredOperationIds)
        } else {
            draftId?.let(::restoreDraft)
        }
    }

    fun selectPdf(uri: Uri) {
        pageOperationJob?.cancel()
        pageOperationJob = viewModelScope.launch {
            val previous = currentStagedIds()
            val id = UUID.randomUUID()
            mutableState.update { it.copy(isBusy = true, errorCode = null, result = null) }
            runCatching {
                val metadata = preparation.stageAndInspect(uri, id)
                val preview = preparation.renderPreview(id, 0)
                metadata to preview
            }.onSuccess { (metadata, preview) ->
                previous.forEach { preparation.discard(it) }
                draftId = id
                savedStateHandle[KEY_DRAFT_ID] = id.toString()
                savedStateHandle.remove<Array<String>>(KEY_OPERATION_IDS)
                savedStateHandle.remove<Array<String>>(KEY_OPERATION_PAGES)
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

    fun selectOperationPdfs(uris: List<Uri>) {
        if (uris.isEmpty() || uris.size > MAX_OPERATION_SOURCES) {
            mutableState.update { it.copy(errorCode = "PDF_OPERATION_SOURCE_LIMIT") }
            return
        }
        pageOperationJob?.cancel()
        pageOperationJob = viewModelScope.launch {
            val previousIds = currentStagedIds()
            val staged = mutableListOf<PdfOperationSourceUi>()
            mutableState.update { it.copy(isBusy = true, errorCode = null, result = null) }
            runCatching {
                uris.forEach { uri ->
                    val id = UUID.randomUUID()
                    val metadata = preparation.stageAndInspect(uri, id)
                    if (
                        metadata.encrypted ||
                        metadata.hasForms == true ||
                        metadata.hasDigitalSignatures == true
                    ) {
                        preparation.discard(id)
                        error("PDF_PROTECTED_STRUCTURE_UNSUPPORTED")
                    }
                    staged += PdfOperationSourceUi(id, metadata)
                    check(staged.sumOf { it.metadata.pageCount } <= MAX_OPERATION_PAGES) {
                        "PDF_OPERATION_PAGE_LIMIT"
                    }
                    check(staged.sumOf { it.metadata.byteCount } <= MAX_OPERATION_INPUT_BYTES) {
                        "PDF_OPERATION_SIZE_LIMIT"
                    }
                }
                val first = staged.first()
                val preview = preparation.renderPreview(first.id, 0)
                first to preview
            }.onSuccess { (_, preview) ->
                previousIds.forEach { preparation.discard(it) }
                val first = staged.first()
                draftId = first.id
                savedStateHandle[KEY_DRAFT_ID] = first.id.toString()
                savedStateHandle[KEY_OPERATION_IDS] = staged.map { it.id.toString() }.toTypedArray()
                mutableState.value = PdfUiState(
                    metadata = aggregateMetadata(staged),
                    preview = preview.asImageBitmap(),
                    maximumSizeText = mutableState.value.maximumSizeText,
                    maximumPagesText = mutableState.value.maximumPagesText,
                    operationSources = staged.toList(),
                    operationPages = staged.flatMapIndexed { sourceIndex, source ->
                        (0 until source.metadata.pageCount).map { pageIndex ->
                            PdfOperationPageUi(sourceIndex, pageIndex)
                        }
                    },
                    isOperationMode = true,
                )
                persistOperationPages(mutableState.value.operationPages)
            }.onFailure { error ->
                staged.forEach { preparation.discard(it.id) }
                mutableState.update {
                    it.copy(
                        isBusy = false,
                        errorCode = error.message ?: "PDF_OPERATION_INPUT_FAILED",
                    )
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

    fun showOperationPage(index: Int) {
        val state = mutableState.value
        val selection = state.operationPages.getOrNull(index) ?: return
        val source = state.operationSources.getOrNull(selection.sourceIndex) ?: return
        if (state.isBusy) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorCode = null) }
            runCatching { preparation.renderPreview(source.id, selection.pageIndex) }
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

    fun moveOperationPage(index: Int, offset: Int) {
        val pages = mutableState.value.operationPages.toMutableList()
        val destination = index + offset
        if (index !in pages.indices || destination !in pages.indices) return
        val moved = pages.removeAt(index)
        pages.add(destination, moved)
        mutableState.update {
            it.copy(
                operationPages = pages,
                previewPage = destination,
                result = null,
                jobStatus = null,
            )
        }
        persistOperationPages(pages)
        showOperationPage(destination)
    }

    fun rotateOperationPage(index: Int) {
        mutableState.update { state ->
            val pages = state.operationPages.toMutableList()
            val page = pages.getOrNull(index) ?: return@update state
            pages[index] = page.copy(
                rotationQuarterTurns = (page.rotationQuarterTurns + 1) % 4,
            )
            state.copy(operationPages = pages, result = null, jobStatus = null)
        }
        persistOperationPages(mutableState.value.operationPages)
    }

    fun deleteOperationPage(index: Int) {
        mutableState.update { state ->
            if (index !in state.operationPages.indices) return@update state
            val pages = state.operationPages.toMutableList().apply { removeAt(index) }
            state.copy(
                operationPages = pages,
                previewPage = state.previewPage.coerceAtMost((pages.size - 1).coerceAtLeast(0)),
                result = null,
                jobStatus = null,
            )
        }
        persistOperationPages(mutableState.value.operationPages)
    }

    fun resetOperationPages() {
        val sources = mutableState.value.operationSources
        mutableState.update {
            it.copy(
                operationPages = sources.flatMapIndexed { sourceIndex, source ->
                    (0 until source.metadata.pageCount).map { pageIndex ->
                        PdfOperationPageUi(sourceIndex, pageIndex)
                    }
                },
                previewPage = 0,
                result = null,
                jobStatus = null,
            )
        }
        persistOperationPages(mutableState.value.operationPages)
        if (sources.isNotEmpty()) showOperationPage(0)
    }

    fun startPageOperation() {
        val state = mutableState.value
        if (
            !state.isOperationMode ||
            state.operationSources.isEmpty() ||
            state.operationPages.isEmpty() ||
            state.isBusy
        ) {
            mutableState.update { it.copy(errorCode = "PDF_OPERATION_PAGES_REQUIRED") }
            return
        }
        val operationId = UUID.randomUUID()
        val plan = ProcessingPlan(
            jobId = operationId,
            transforms = emptyList(),
            output = OutputSpecification(format = OutputFormat.PDF),
            hardRuleIds = setOf("pdf.structure_preserved", "pdf.page_order"),
            advisoryRuleIds = emptySet(),
            pdfOptions = PdfOptions(),
        )
        mutableState.update {
            it.copy(
                isBusy = true,
                jobStatus = JobStatus.QUEUED,
                errorCode = null,
                result = null,
            )
        }
        pageOperationJob?.cancel()
        pageOperationJob = viewModelScope.launch {
            val outputDirectory = File(
                context.filesDir,
                PrivatePhotoOutputAccess.OUTPUT_DIRECTORY,
            ).apply { mkdirs() }
            val destination = File(outputDirectory, "$operationId.pdf")
            runCatching {
                jobs.create(
                    plan = plan,
                    projectId = null,
                    type = JobType.PDF,
                    serializedPlan = PhotoPlanCodec.encode(plan),
                    stagedInputRelativePath = preparation.stagedRelativePath(
                        state.operationSources.first().id,
                    ),
                )
                check(jobs.transition(operationId, JobStatus.QUEUED, JobStatus.RUNNING))
                val prepared = structureEngine.create(
                    sources = state.operationSources.map { preparation.stagedFile(it.id) },
                    pages = state.operationPages.map {
                        PdfPageSelection(
                            sourceIndex = it.sourceIndex,
                            pageIndex = it.pageIndex,
                            rotationQuarterTurns = it.rotationQuarterTurns,
                        )
                    },
                    destination = destination,
                )
                val artifact = OutputArtifact(
                    id = UUID.randomUUID(),
                    jobId = operationId,
                    uri = "${PrivatePhotoOutputAccess.OUTPUT_DIRECTORY}/${destination.name}",
                    displayName = "FormReady-pages-$operationId.pdf",
                    mimeType = "application/pdf",
                    byteCount = prepared.metadata.byteCount,
                    widthPx = null,
                    heightPx = null,
                    dpi = null,
                    readiness = prepared.validationResults.readiness(),
                    validationJson = ValidationResultCodec.encode(prepared.validationResults),
                    createdAtEpochMillis = timeProvider.currentTimeMillis(),
                )
                outputs.add(artifact)
                check(jobs.transition(operationId, JobStatus.RUNNING, JobStatus.SUCCEEDED))
                artifact to prepared.validationResults
            }.onSuccess { (artifact, validations) ->
                mutableState.update {
                    it.copy(
                        isBusy = false,
                        jobStatus = JobStatus.SUCCEEDED,
                        result = PdfResultUi(
                            artifact,
                            validations,
                            outputAccess.shareUri(artifact),
                        ),
                    )
                }
            }.onFailure { error ->
                destination.delete()
                jobs.transition(
                    operationId,
                    JobStatus.RUNNING,
                    JobStatus.FAILED,
                    error.message,
                )
                mutableState.update {
                    it.copy(
                        isBusy = false,
                        jobStatus = JobStatus.FAILED,
                        errorCode = error.message ?: "PDF_OPERATION_FAILED",
                    )
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
        if (mutableState.value.isOperationMode) {
            pageOperationJob?.cancel()
            mutableState.update {
                it.copy(isBusy = false, jobStatus = JobStatus.CANCELLED)
            }
        } else {
            draftId?.let { id -> viewModelScope.launch { scheduler.cancel(id) } }
        }
    }

    fun prepareAnother() {
        pageOperationJob?.cancel()
        val stagedIds = currentStagedIds()
        if (stagedIds.isNotEmpty()) {
            viewModelScope.launch { stagedIds.forEach { preparation.discard(it) } }
        }
        draftId = null
        savedStateHandle.remove<String>(KEY_DRAFT_ID)
        savedStateHandle.remove<Array<String>>(KEY_OPERATION_IDS)
        savedStateHandle.remove<Array<String>>(KEY_OPERATION_PAGES)
        jobObserver?.cancel()
        outputObserver?.cancel()
        mutableState.value = PdfUiState()
    }

    fun discard(onFinished: () -> Unit) {
        pageOperationJob?.cancel()
        val ids = currentStagedIds()
        viewModelScope.launch {
            ids.forEach { preparation.discard(it) }
            draftId = null
            savedStateHandle.remove<String>(KEY_DRAFT_ID)
            savedStateHandle.remove<Array<String>>(KEY_OPERATION_IDS)
            savedStateHandle.remove<Array<String>>(KEY_OPERATION_PAGES)
            onFinished()
        }
    }

    fun reportNoApp() {
        mutableState.update { it.copy(errorCode = "NO_COMPATIBLE_APP") }
    }

    fun reuseRequirements(serializedPlan: String) {
        val plan = runCatching { PhotoPlanCodec.decode(serializedPlan) }.getOrNull() ?: return
        if (plan.pdfOptions == null) return
        val size = plan.output.maximumBytes?.div(1_000L)?.toString() ?: "1000"
        prepareAnother()
        setMaximumSize(size)
        setAcknowledged(false)
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

    private fun restoreOperations(ids: List<UUID>) {
        viewModelScope.launch {
            runCatching {
                val sources = ids.map { id ->
                    PdfOperationSourceUi(id, preparation.inspectStaged(id))
                }
                val first = sources.first()
                val preview = preparation.renderPreview(first.id, 0)
                sources to preview
            }.onSuccess { (sources, preview) ->
                draftId = sources.first().id
                mutableState.update {
                    it.copy(
                        metadata = aggregateMetadata(sources),
                        preview = preview.asImageBitmap(),
                        operationSources = sources,
                        operationPages = restoreOperationPages(sources),
                        isOperationMode = true,
                    )
                }
            }.onFailure {
                ids.forEach { preparation.discard(it) }
                draftId = null
                savedStateHandle.remove<String>(KEY_DRAFT_ID)
                savedStateHandle.remove<Array<String>>(KEY_OPERATION_IDS)
                savedStateHandle.remove<Array<String>>(KEY_OPERATION_PAGES)
            }
        }
    }

    private fun currentStagedIds(): List<UUID> =
        (mutableState.value.operationSources.map { it.id } + listOfNotNull(draftId)).distinct()

    private fun aggregateMetadata(sources: List<PdfOperationSourceUi>): PdfMetadata = PdfMetadata(
        byteCount = sources.sumOf { it.metadata.byteCount },
        pageCount = sources.sumOf { it.metadata.pageCount },
        pages = sources.flatMap { it.metadata.pages },
        encrypted = sources.any { it.metadata.encrypted },
        hasForms = aggregateFeature(sources) { it.hasForms },
        hasLinks = aggregateFeature(sources) { it.hasLinks },
        hasAnnotations = aggregateFeature(sources) { it.hasAnnotations },
        hasDigitalSignatures = aggregateFeature(sources) { it.hasDigitalSignatures },
    )

    private fun aggregateFeature(
        sources: List<PdfOperationSourceUi>,
        selector: (PdfMetadata) -> Boolean?,
    ): Boolean? = when {
        sources.any { selector(it.metadata) == true } -> true
        sources.any { selector(it.metadata) == null } -> null
        else -> false
    }

    private fun persistOperationPages(pages: List<PdfOperationPageUi>) {
        savedStateHandle[KEY_OPERATION_PAGES] = pages.map {
            "${it.sourceIndex}:${it.pageIndex}:${it.rotationQuarterTurns}"
        }.toTypedArray()
    }

    private fun restoreOperationPages(
        sources: List<PdfOperationSourceUi>,
    ): List<PdfOperationPageUi> {
        val restored = savedStateHandle.get<Array<String>>(KEY_OPERATION_PAGES)
            ?.mapNotNull { encoded ->
                val values = encoded.split(':').mapNotNull(String::toIntOrNull)
                if (values.size != 3) return@mapNotNull null
                PdfOperationPageUi(values[0], values[1], values[2]).takeIf { page ->
                    page.sourceIndex in sources.indices &&
                        page.pageIndex in 0 until sources[page.sourceIndex].metadata.pageCount &&
                        page.rotationQuarterTurns in 0..3
                }
            }
            .orEmpty()
        return restored.ifEmpty {
            sources.flatMapIndexed { sourceIndex, source ->
                (0 until source.metadata.pageCount).map { pageIndex ->
                    PdfOperationPageUi(sourceIndex, pageIndex)
                }
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_PAGES = 100
        const val MAX_OPERATION_SOURCES = 10
        const val MAX_OPERATION_PAGES = 100
        const val MAX_OPERATION_INPUT_BYTES = 200L * 1024L * 1024L
        const val KEY_DRAFT_ID = "pdf.draftId"
        const val KEY_OPERATION_IDS = "pdf.operationIds"
        const val KEY_OPERATION_PAGES = "pdf.operationPages"
        const val KEY_MAXIMUM_SIZE = "pdf.maximumSize"
        const val KEY_MAXIMUM_PAGES = "pdf.maximumPages"
        const val KEY_ACKNOWLEDGED = "pdf.acknowledged"
    }
}
