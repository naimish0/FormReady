package com.rameshta.formready.feature.batch

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.data.repository.OutputArtifactRepository
import com.rameshta.formready.core.model.BatchRequirements
import com.rameshta.formready.core.model.CropMode
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.processing.BatchPlanFactory
import com.rameshta.formready.core.processing.ImageMetadata
import com.rameshta.formready.core.processing.PhotoOutputAccess
import com.rameshta.formready.core.processing.PhotoPlanCodec
import com.rameshta.formready.core.processing.PhotoPreparationService
import com.rameshta.formready.core.processing.ProcessingScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatchItemUi(
    val id: UUID,
    val metadata: ImageMetadata?,
    val status: JobStatus? = null,
    val artifact: OutputArtifact? = null,
    val errorCode: String? = null,
)

data class BatchUiState(
    val items: List<BatchItemUi> = emptyList(),
    val widthText: String = "600",
    val heightText: String = "800",
    val maximumKbText: String = "200",
    val dpiText: String = "300",
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val cropMode: CropMode = CropMode.CROP_FILL,
    val isLoading: Boolean = false,
    val isSavingZip: Boolean = false,
    val zipSaved: Boolean = false,
    val errorCode: String? = null,
) {
    val isRunning: Boolean
        get() = items.any { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
    val succeededCount: Int get() = items.count { it.status == JobStatus.SUCCEEDED }
    val finishedCount: Int
        get() = items.count {
            it.status == JobStatus.SUCCEEDED ||
                it.status == JobStatus.FAILED ||
                it.status == JobStatus.CANCELLED
        }
    val hasDraft: Boolean get() = items.isNotEmpty() || isLoading || isRunning
}

@HiltViewModel
class BatchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val preparation: PhotoPreparationService,
    private val jobs: JobRepository,
    private val outputs: OutputArtifactRepository,
    private val scheduler: ProcessingScheduler,
    private val outputAccess: PhotoOutputAccess,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        BatchUiState(
            widthText = savedStateHandle[KEY_WIDTH] ?: "600",
            heightText = savedStateHandle[KEY_HEIGHT] ?: "800",
            maximumKbText = savedStateHandle[KEY_MAXIMUM_KB] ?: "200",
            dpiText = savedStateHandle[KEY_DPI] ?: "300",
            outputFormat = savedStateHandle.get<String>(KEY_FORMAT)
                ?.let { runCatching { OutputFormat.valueOf(it) }.getOrNull() }
                ?: OutputFormat.JPEG,
            cropMode = savedStateHandle.get<String>(KEY_CROP_MODE)
                ?.let { runCatching { CropMode.valueOf(it) }.getOrNull() }
                ?: CropMode.CROP_FILL,
        ),
    )
    val uiState: StateFlow<BatchUiState> = mutableState.asStateFlow()
    private var batchId: UUID = savedStateHandle.get<String>(KEY_BATCH_ID)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: UUID.randomUUID().also { savedStateHandle[KEY_BATCH_ID] = it.toString() }
    private val observers = mutableMapOf<UUID, MutableList<Job>>()

    init {
        val ids = savedStateHandle.get<Array<String>>(KEY_ITEM_IDS)
            ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .orEmpty()
        if (ids.isNotEmpty()) restore(ids)
    }

    fun selectImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (mutableState.value.isRunning || mutableState.value.isLoading) {
            mutableState.update { it.copy(errorCode = "BATCH_ALREADY_RUNNING") }
            return
        }
        if (uris.size > FREE_BATCH_LIMIT) {
            mutableState.update { it.copy(errorCode = "BATCH_ITEM_LIMIT") }
            return
        }
        viewModelScope.launch {
            val previous = mutableState.value.items
            mutableState.update {
                it.copy(isLoading = true, errorCode = null, zipSaved = false)
            }
            val staged = mutableListOf<BatchItemUi>()
            runCatching {
                var totalBytes = 0L
                uris.forEach { uri ->
                    val id = UUID.randomUUID()
                    val metadata = preparation.stageAndInspect(
                        source = uri,
                        jobId = id,
                        reportedMimeType = context.contentResolver.getType(uri),
                    )
                    staged += BatchItemUi(id = id, metadata = metadata)
                    totalBytes = Math.addExact(totalBytes, metadata.byteCount)
                    check(totalBytes <= MAX_BATCH_INPUT_BYTES) { "BATCH_TOTAL_SIZE_LIMIT" }
                }
            }.onSuccess {
                clearItems(previous)
                batchId = UUID.randomUUID()
                savedStateHandle[KEY_BATCH_ID] = batchId.toString()
                mutableState.update {
                    it.copy(items = staged, isLoading = false, errorCode = null)
                }
                persistIds(staged)
            }.onFailure { error ->
                staged.forEach { preparation.discard(it.id) }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorCode = error.message ?: "BATCH_INPUT_FAILED",
                    )
                }
            }
        }
    }

    fun removeItem(index: Int) {
        val item = mutableState.value.items.getOrNull(index) ?: return
        if (item.status != null || mutableState.value.isRunning) return
        viewModelScope.launch { preparation.discard(item.id) }
        stopObserving(item.id)
        mutableState.update { state ->
            state.copy(items = state.items.toMutableList().apply { removeAt(index) })
        }
        persistIds(mutableState.value.items)
    }

    fun setWidth(value: String) = updateText(KEY_WIDTH, value) { copy(widthText = it) }

    fun setHeight(value: String) = updateText(KEY_HEIGHT, value) { copy(heightText = it) }

    fun setMaximumKb(value: String) =
        updateText(KEY_MAXIMUM_KB, value) { copy(maximumKbText = it) }

    fun setDpi(value: String) = updateText(KEY_DPI, value) { copy(dpiText = it) }

    fun setFormat(format: OutputFormat) {
        if (format == OutputFormat.PDF) return
        savedStateHandle[KEY_FORMAT] = format.name
        mutableState.update { it.copy(outputFormat = format, zipSaved = false) }
    }

    fun setCropMode(mode: CropMode) {
        savedStateHandle[KEY_CROP_MODE] = mode.name
        mutableState.update { it.copy(cropMode = mode, zipSaved = false) }
    }

    fun start() {
        val state = mutableState.value
        if (
            state.items.isEmpty() ||
            state.isLoading ||
            state.isRunning ||
            state.items.any { it.status != null }
        ) {
            return
        }
        val requirements = requirementsOrNull() ?: run {
            mutableState.update { it.copy(errorCode = "INVALID_REQUIREMENTS") }
            return
        }
        viewModelScope.launch {
            val created = mutableListOf<UUID>()
            runCatching {
                state.items.forEach { item ->
                    val metadata = requireNotNull(item.metadata)
                    val plan = BatchPlanFactory.create(item.id, metadata, requirements)
                    jobs.create(
                        plan = plan,
                        projectId = null,
                        type = JobType.PHOTO,
                        serializedPlan = PhotoPlanCodec.encode(plan),
                        stagedInputRelativePath = preparation.stagedRelativePath(item.id),
                    )
                    created += item.id
                    observe(item.id)
                }
                scheduler.enqueueSequence(batchId, created)
            }.onFailure { error ->
                scheduler.cancelSequence(batchId, created)
                mutableState.update {
                    it.copy(errorCode = error.message ?: "BATCH_START_FAILED")
                }
            }
        }
    }

    fun cancel() {
        val ids = mutableState.value.items.map { it.id }
        viewModelScope.launch { scheduler.cancelSequence(batchId, ids) }
    }

    fun retryFailed() {
        val state = mutableState.value
        if (state.isRunning) return
        val failed = state.items.filter { it.status == JobStatus.FAILED && it.metadata != null }
        if (failed.isEmpty()) return
        val requirements = requirementsOrNull() ?: run {
            mutableState.update { it.copy(errorCode = "INVALID_REQUIREMENTS") }
            return
        }
        viewModelScope.launch {
            val replacements = mutableMapOf<UUID, BatchItemUi>()
            val newIds = mutableListOf<UUID>()
            runCatching {
                failed.forEach { old ->
                    val newId = UUID.randomUUID()
                    preparation.duplicateStaged(old.id, newId)
                    newIds += newId
                    val metadata = requireNotNull(old.metadata)
                    val replacement = BatchItemUi(newId, metadata)
                    val plan = BatchPlanFactory.create(newId, metadata, requirements)
                    jobs.create(
                        plan = plan,
                        projectId = null,
                        type = JobType.PHOTO,
                        serializedPlan = PhotoPlanCodec.encode(plan),
                        stagedInputRelativePath = preparation.stagedRelativePath(newId),
                    )
                    replacements[old.id] = replacement
                    observe(newId)
                }
                batchId = UUID.randomUUID()
                savedStateHandle[KEY_BATCH_ID] = batchId.toString()
                mutableState.update { current ->
                    current.copy(
                        items = current.items.map { replacements[it.id] ?: it },
                        errorCode = null,
                        zipSaved = false,
                    )
                }
                persistIds(mutableState.value.items)
                failed.forEach {
                    stopObserving(it.id)
                    preparation.discard(it.id)
                }
                scheduler.enqueueSequence(batchId, newIds)
            }.onFailure { error ->
                scheduler.cancelSequence(batchId, newIds)
                newIds.forEach { preparation.discard(it) }
                newIds.forEach(::stopObserving)
                mutableState.update {
                    it.copy(errorCode = error.message ?: "BATCH_RETRY_FAILED")
                }
            }
        }
    }

    fun saveZip(destination: Uri) {
        val artifacts = mutableState.value.items.mapNotNull { it.artifact }
        if (artifacts.isEmpty() || mutableState.value.isRunning) return
        mutableState.update {
            it.copy(isSavingZip = true, zipSaved = false, errorCode = null)
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destination, "wt")?.buffered()?.use {
                        output ->
                        ZipOutputStream(output).use { zip ->
                            artifacts.forEachIndexed { index, artifact ->
                                val extension = if (artifact.mimeType == "image/png") "png" else "jpg"
                                zip.putNextEntry(
                                    ZipEntry("FormReady-${(index + 1).toString().padStart(2, '0')}.$extension"),
                                )
                                outputAccess.outputFile(artifact).inputStream().buffered().use {
                                    input -> input.copyTo(zip, ZIP_BUFFER_BYTES)
                                }
                                zip.closeEntry()
                            }
                        }
                    } ?: error("DESTINATION_WRITE_FAILED")
                }
            }.onSuccess {
                mutableState.update { it.copy(isSavingZip = false, zipSaved = true) }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isSavingZip = false,
                        errorCode = error.message ?: "BATCH_ZIP_FAILED",
                    )
                }
            }
        }
    }

    fun prepareAnother() {
        val items = mutableState.value.items
        if (mutableState.value.isRunning) cancel()
        clearItems(items)
        batchId = UUID.randomUUID()
        savedStateHandle[KEY_BATCH_ID] = batchId.toString()
        savedStateHandle.remove<Array<String>>(KEY_ITEM_IDS)
        mutableState.value = BatchUiState(
            widthText = mutableState.value.widthText,
            heightText = mutableState.value.heightText,
            maximumKbText = mutableState.value.maximumKbText,
            dpiText = mutableState.value.dpiText,
            outputFormat = mutableState.value.outputFormat,
            cropMode = mutableState.value.cropMode,
        )
    }

    fun discard(onFinished: () -> Unit) {
        val items = mutableState.value.items
        viewModelScope.launch {
            if (mutableState.value.isRunning) {
                scheduler.cancelSequence(batchId, items.map { it.id })
            }
            items.forEach { preparation.discard(it.id) }
            observers.keys.toList().forEach(::stopObserving)
            savedStateHandle.remove<Array<String>>(KEY_ITEM_IDS)
            onFinished()
        }
    }

    private fun restore(ids: List<UUID>) {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }
            val restored = ids.mapNotNull { id ->
                val job = jobs.get(id)
                val metadata = runCatching { preparation.inspectStaged(id) }.getOrNull()
                val artifact = outputs.observeLatestForJob(id).first()
                if (job == null && metadata == null && artifact == null) {
                    null
                } else {
                    BatchItemUi(
                        id = id,
                        metadata = metadata,
                        status = job?.status,
                        artifact = artifact,
                        errorCode = job?.errorCode,
                    ).also { observe(id) }
                }
            }
            mutableState.update { it.copy(items = restored, isLoading = false) }
            persistIds(restored)
        }
    }

    private fun observe(id: UUID) {
        if (observers.containsKey(id)) return
        observers[id] = mutableListOf(
            viewModelScope.launch {
                jobs.observe(id).collect { job ->
                    updateItem(id) {
                        it.copy(status = job?.status, errorCode = job?.errorCode)
                    }
                }
            },
            viewModelScope.launch {
                outputs.observeLatestForJob(id).collect { artifact ->
                    if (artifact != null) updateItem(id) { it.copy(artifact = artifact) }
                }
            },
        )
    }

    private fun updateItem(id: UUID, transform: (BatchItemUi) -> BatchItemUi) {
        mutableState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun stopObserving(id: UUID) {
        observers.remove(id)?.forEach(Job::cancel)
    }

    private fun clearItems(items: List<BatchItemUi>) {
        items.forEach { item ->
            stopObserving(item.id)
            viewModelScope.launch { preparation.discard(item.id) }
        }
    }

    private fun requirementsOrNull(): BatchRequirements? = runCatching {
        BatchRequirements(
            widthPx = mutableState.value.widthText.toInt(),
            heightPx = mutableState.value.heightText.toInt(),
            maximumBytes = Math.multiplyExact(
                mutableState.value.maximumKbText.toLong(),
                1_000L,
            ),
            dpi = mutableState.value.dpiText.toIntOrNull(),
            outputFormat = mutableState.value.outputFormat,
            cropMode = mutableState.value.cropMode,
        )
    }.getOrNull()

    private fun updateText(
        key: String,
        value: String,
        transform: BatchUiState.(String) -> BatchUiState,
    ) {
        val filtered = value.filter(Char::isDigit).take(9)
        savedStateHandle[key] = filtered
        mutableState.update { state -> state.transform(filtered).copy(zipSaved = false) }
    }

    private fun persistIds(items: List<BatchItemUi>) {
        savedStateHandle[KEY_ITEM_IDS] = items.map { it.id.toString() }.toTypedArray()
    }

    private companion object {
        const val FREE_BATCH_LIMIT = 10
        const val MAX_BATCH_INPUT_BYTES = 200L * 1024L * 1024L
        const val ZIP_BUFFER_BYTES = 64 * 1024
        const val KEY_BATCH_ID = "batch.id"
        const val KEY_ITEM_IDS = "batch.itemIds"
        const val KEY_WIDTH = "batch.width"
        const val KEY_HEIGHT = "batch.height"
        const val KEY_MAXIMUM_KB = "batch.maximumKb"
        const val KEY_DPI = "batch.dpi"
        const val KEY_FORMAT = "batch.format"
        const val KEY_CROP_MODE = "batch.cropMode"
    }
}
