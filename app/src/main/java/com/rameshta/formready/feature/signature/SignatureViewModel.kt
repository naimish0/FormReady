package com.rameshta.formready.feature.signature

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.data.repository.OutputArtifactRepository
import com.rameshta.formready.core.model.ByteUnit
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.NormalizedTransform
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.OutputSpecification
import com.rameshta.formready.core.model.ProcessingPlan
import com.rameshta.formready.core.model.SignatureOptions
import com.rameshta.formready.core.model.ValidationRuleResult
import com.rameshta.formready.core.processing.ImageMetadata
import com.rameshta.formready.core.processing.PhotoOutputAccess
import com.rameshta.formready.core.processing.PhotoPlanCodec
import com.rameshta.formready.core.processing.PhotoPreparationService
import com.rameshta.formready.core.processing.ProcessingScheduler
import com.rameshta.formready.core.processing.SignatureBitmapProcessor
import com.rameshta.formready.core.processing.ValidationResultCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SignatureResultUi(
    val artifact: OutputArtifact,
    val validationResults: List<ValidationRuleResult>,
    val shareUri: Uri,
)

data class SignatureUiState(
    val widthText: String = "300",
    val heightText: String = "100",
    val maximumSizeText: String = "50",
    val dpiText: String = "300",
    val byteUnit: ByteUnit = ByteUnit.KB,
    val outputFormat: OutputFormat = OutputFormat.PNG,
    val grayscale: Boolean = true,
    val contrastPercent: Int = 120,
    val threshold: Int = 190,
    val cleanPaper: Boolean = true,
    val removeSpeckles: Boolean = true,
    val autoCrop: Boolean = true,
    val safeMarginPercent: Int = 6,
    val paddingPercent: Int = 8,
    val inkArgb: Int = SignatureViewModel.INK_BLACK,
    val transparentBackground: Boolean = false,
    val cropLeftPercent: Int = 0,
    val cropTopPercent: Int = 0,
    val cropRightPercent: Int = 100,
    val cropBottomPercent: Int = 100,
    val horizontalOffset: Float = 0f,
    val verticalOffset: Float = 0f,
    val rotationQuarterTurns: Int = 0,
    val deskewDegrees: Float = 0f,
    val metadata: ImageMetadata? = null,
    val originalPreview: ImageBitmap? = null,
    val processedPreview: ImageBitmap? = null,
    val isLoadingInput: Boolean = false,
    val jobStatus: JobStatus? = null,
    val errorCode: String? = null,
    val result: SignatureResultUi? = null,
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
) {
    val hasDraft: Boolean
        get() = metadata != null || isLoadingInput || jobStatus != null
}

@HiltViewModel
class SignatureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val preparationService: PhotoPreparationService,
    private val jobs: JobRepository,
    private val outputs: OutputArtifactRepository,
    private val scheduler: ProcessingScheduler,
    private val outputAccess: PhotoOutputAccess,
    private val cleanupProcessor: SignatureBitmapProcessor,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SignatureUiState(
            widthText = savedStateHandle[KEY_WIDTH] ?: "300",
            heightText = savedStateHandle[KEY_HEIGHT] ?: "100",
            maximumSizeText = savedStateHandle[KEY_MAX_BYTES] ?: "50",
            dpiText = savedStateHandle[KEY_DPI] ?: "300",
            outputFormat = savedStateHandle.get<String>(KEY_FORMAT)
                ?.let { runCatching { OutputFormat.valueOf(it) }.getOrNull() }
                ?: OutputFormat.PNG,
            threshold = savedStateHandle[KEY_THRESHOLD] ?: 190,
            contrastPercent = savedStateHandle[KEY_CONTRAST] ?: 120,
            safeMarginPercent = savedStateHandle[KEY_MARGIN] ?: 6,
            paddingPercent = savedStateHandle[KEY_PADDING] ?: 8,
            grayscale = savedStateHandle[KEY_GRAYSCALE] ?: true,
            cleanPaper = savedStateHandle[KEY_CLEAN_PAPER] ?: true,
            removeSpeckles = savedStateHandle[KEY_SPECKLES] ?: true,
            autoCrop = savedStateHandle[KEY_AUTO_CROP] ?: true,
            transparentBackground = savedStateHandle[KEY_TRANSPARENT] ?: false,
            inkArgb = savedStateHandle[KEY_INK] ?: INK_BLACK,
            cropLeftPercent = savedStateHandle[KEY_CROP_LEFT] ?: 0,
            cropTopPercent = savedStateHandle[KEY_CROP_TOP] ?: 0,
            cropRightPercent = savedStateHandle[KEY_CROP_RIGHT] ?: 100,
            cropBottomPercent = savedStateHandle[KEY_CROP_BOTTOM] ?: 100,
            horizontalOffset = savedStateHandle[KEY_OFFSET_X] ?: 0f,
            verticalOffset = savedStateHandle[KEY_OFFSET_Y] ?: 0f,
            rotationQuarterTurns = savedStateHandle[KEY_ROTATION] ?: 0,
            deskewDegrees = savedStateHandle[KEY_DESKEW] ?: 0f,
        ),
    )
    val uiState: StateFlow<SignatureUiState> = mutableState.asStateFlow()
    private var originalPreviewBitmap: Bitmap? = null
    private var jobObserver: Job? = null
    private var outputObserver: Job? = null
    private var previewJob: Job? = null
    private var captureFile: File? = null

    init {
        savedStateHandle.get<String>(KEY_DRAFT_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(::restoreDraft)
    }

    fun selectSignature(uri: Uri, reportedMimeType: String?) = stage(uri, reportedMimeType, null)

    fun createCaptureUri(): Uri {
        val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "signature-${UUID.randomUUID()}.jpg")
        captureFile = file
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun completeCapture(success: Boolean) {
        val file = captureFile
        captureFile = null
        if (!success || file == null || !file.isFile || file.length() <= 0L) {
            file?.delete()
            if (success) mutableState.update { it.copy(errorCode = "CAPTURE_FAILED") }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        stage(uri, "image/jpeg", file)
    }

    fun useDrawing(bitmap: Bitmap) {
        viewModelScope.launch {
            val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
            val file = File(directory, "drawing-${UUID.randomUUID()}.png")
            withContext(Dispatchers.IO) {
                file.outputStream().buffered().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            }
            bitmap.recycle()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            stage(uri, "image/png", file)
        }
    }

    fun setWidth(value: String) = updateDigits(KEY_WIDTH, value) {
        copy(widthText = it)
    }

    fun setHeight(value: String) = updateDigits(KEY_HEIGHT, value) {
        copy(heightText = it)
    }

    fun setMaximumSize(value: String) = updateDigits(KEY_MAX_BYTES, value) {
        copy(maximumSizeText = it)
    }

    fun setDpi(value: String) = updateDigits(KEY_DPI, value) { copy(dpiText = it) }

    fun setOutputFormat(format: OutputFormat) {
        savedStateHandle[KEY_FORMAT] = format.name
        if (format == OutputFormat.JPEG) savedStateHandle[KEY_TRANSPARENT] = false
        mutableState.update {
            it.copy(
                outputFormat = format,
                transparentBackground = it.transparentBackground && format == OutputFormat.PNG,
            )
        }
        refreshPreview()
    }

    fun setThreshold(value: Int) = updateOption(KEY_THRESHOLD, value.coerceIn(80, 245)) {
        copy(threshold = it)
    }

    fun setContrast(value: Int) = updateOption(KEY_CONTRAST, value.coerceIn(50, 250)) {
        copy(contrastPercent = it)
    }

    fun setSafeMargin(value: Int) = updateOption(KEY_MARGIN, value.coerceIn(0, 25)) {
        copy(safeMarginPercent = it)
    }

    fun setPadding(value: Int) = updateOption(KEY_PADDING, value.coerceIn(0, 30)) {
        copy(paddingPercent = it)
    }

    fun setGrayscale(value: Boolean) =
        updateAndPreview(KEY_GRAYSCALE, value) { copy(grayscale = value) }

    fun setCleanPaper(value: Boolean) =
        updateAndPreview(KEY_CLEAN_PAPER, value) { copy(cleanPaper = value) }

    fun setRemoveSpeckles(value: Boolean) =
        updateAndPreview(KEY_SPECKLES, value) { copy(removeSpeckles = value) }

    fun setAutoCrop(value: Boolean) =
        updateAndPreview(KEY_AUTO_CROP, value) { copy(autoCrop = value) }

    fun setTransparentBackground(value: Boolean) {
        val effective = value && mutableState.value.outputFormat == OutputFormat.PNG
        updateAndPreview(KEY_TRANSPARENT, effective) {
            copy(transparentBackground = effective)
        }
    }

    fun setInkColour(argb: Int) = updateAndPreview(KEY_INK, argb) { copy(inkArgb = argb) }

    fun setCrop(left: Int, top: Int, right: Int, bottom: Int) {
        if (left !in 0..95 || top !in 0..95 || right !in 5..100 || bottom !in 5..100) return
        if (left >= right || top >= bottom) return
        savedStateHandle[KEY_CROP_LEFT] = left
        savedStateHandle[KEY_CROP_TOP] = top
        savedStateHandle[KEY_CROP_RIGHT] = right
        savedStateHandle[KEY_CROP_BOTTOM] = bottom
        mutableState.update {
            it.copy(
                cropLeftPercent = left,
                cropTopPercent = top,
                cropRightPercent = right,
                cropBottomPercent = bottom,
            )
        }
        refreshPreview()
    }

    fun nudge(horizontal: Float, vertical: Float) {
        mutableState.update {
            it.copy(
                horizontalOffset = (it.horizontalOffset + horizontal).coerceIn(-1f, 1f),
                verticalOffset = (it.verticalOffset + vertical).coerceIn(-1f, 1f),
            ).also { updated ->
                savedStateHandle[KEY_OFFSET_X] = updated.horizontalOffset
                savedStateHandle[KEY_OFFSET_Y] = updated.verticalOffset
            }
        }
        refreshPreview()
    }

    fun rotateClockwise() {
        mutableState.update {
            it.copy(rotationQuarterTurns = (it.rotationQuarterTurns + 1) % 4).also { updated ->
                savedStateHandle[KEY_ROTATION] = updated.rotationQuarterTurns
            }
        }
        refreshPreview()
    }

    fun setDeskew(value: Float) {
        savedStateHandle[KEY_DESKEW] = value.coerceIn(-10f, 10f)
        mutableState.update { it.copy(deskewDegrees = value.coerceIn(-10f, 10f)) }
        refreshPreview()
    }

    fun resetCleanup() {
        listOf(
            KEY_GRAYSCALE,
            KEY_CONTRAST,
            KEY_THRESHOLD,
            KEY_CLEAN_PAPER,
            KEY_SPECKLES,
            KEY_AUTO_CROP,
            KEY_MARGIN,
            KEY_PADDING,
            KEY_TRANSPARENT,
            KEY_INK,
            KEY_CROP_LEFT,
            KEY_CROP_TOP,
            KEY_CROP_RIGHT,
            KEY_CROP_BOTTOM,
            KEY_OFFSET_X,
            KEY_OFFSET_Y,
            KEY_ROTATION,
            KEY_DESKEW,
        ).forEach { savedStateHandle.remove<Any>(it) }
        mutableState.update {
            it.copy(
                grayscale = true,
                contrastPercent = 120,
                threshold = 190,
                cleanPaper = true,
                removeSpeckles = true,
                autoCrop = true,
                safeMarginPercent = 6,
                paddingPercent = 8,
                inkArgb = INK_BLACK,
                transparentBackground = false,
                cropLeftPercent = 0,
                cropTopPercent = 0,
                cropRightPercent = 100,
                cropBottomPercent = 100,
                horizontalOffset = 0f,
                verticalOffset = 0f,
                rotationQuarterTurns = 0,
                deskewDegrees = 0f,
            )
        }
        refreshPreview()
    }

    fun startExport() {
        val state = mutableState.value
        if (state.jobStatus != null || state.isLoadingInput || state.metadata == null) return
        val id = currentDraftId() ?: return
        val width = state.widthText.toIntOrNull()
        val height = state.heightText.toIntOrNull()
        val maximum = state.maximumSizeText.toLongOrNull()?.let {
            runCatching { Math.multiplyExact(it, state.byteUnit.bytesPerUnit) }.getOrNull()
        }
        val dpi = state.dpiText.toIntOrNull()
        if (
            width == null || width !in 1..20_000 ||
            height == null || height !in 1..20_000 ||
            maximum == null || maximum <= 0L ||
            dpi == null || dpi !in 1..2_400
        ) {
            mutableState.update { it.copy(errorCode = "INVALID_REQUIREMENTS") }
            return
        }
        val background = if (state.transparentBackground) 0x00FFFFFF else 0xFFFFFFFF.toInt()
        val plan = ProcessingPlan(
            jobId = id,
            transforms = buildList {
                val degrees = state.rotationQuarterTurns * 90f + state.deskewDegrees
                if (degrees != 0f) add(NormalizedTransform.Rotate(degrees))
                add(
                    NormalizedTransform.FitPad(
                        backgroundArgb = background,
                        paddingFraction = state.paddingPercent / 100f,
                        horizontalOffset = state.horizontalOffset,
                        verticalOffset = state.verticalOffset,
                    ),
                )
            },
            output = OutputSpecification(
                format = state.outputFormat,
                widthPx = width,
                heightPx = height,
                maximumBytes = maximum,
                dpi = dpi,
                backgroundArgb = background,
            ),
            hardRuleIds = setOf(
                "signature.format",
                "signature.dimensions",
                "signature.maximum_bytes",
                "signature.dpi",
                "signature.color_space",
            ),
            advisoryRuleIds = setOf("signature.quality_guard", "signature.upscaling"),
            signatureOptions = state.toOptions(),
        )
        mutableState.update { it.copy(jobStatus = JobStatus.QUEUED, errorCode = null) }
        viewModelScope.launch {
            runCatching {
                jobs.create(
                    plan = plan,
                    projectId = null,
                    type = JobType.SIGNATURE,
                    serializedPlan = PhotoPlanCodec.encode(plan),
                    stagedInputRelativePath = preparationService.stagedRelativePath(id),
                )
                observeJob(id)
                scheduler.enqueue(id)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(jobStatus = null, errorCode = error.message ?: "EXPORT_START_FAILED")
                }
            }
        }
    }

    fun saveTo(destination: Uri) {
        val result = mutableState.value.result ?: return
        if (mutableState.value.isSaving) return
        mutableState.update { it.copy(isSaving = true, saveSucceeded = false, errorCode = null) }
        viewModelScope.launch {
            runCatching { outputAccess.copyTo(result.artifact, destination) }
                .onSuccess {
                    mutableState.update { it.copy(isSaving = false, saveSucceeded = true) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            errorCode = error.message ?: "DESTINATION_WRITE_FAILED",
                        )
                    }
                }
        }
    }

    fun cancelExport() {
        currentDraftId()?.let { id -> viewModelScope.launch { scheduler.cancel(id) } }
    }

    fun retryExport() {
        val sourceId = currentDraftId() ?: return
        if (mutableState.value.jobStatus != JobStatus.FAILED) return
        viewModelScope.launch {
            val replacementId = UUID.randomUUID()
            runCatching {
                preparationService.duplicateStaged(sourceId, replacementId)
                preparationService.discard(sourceId)
                savedStateHandle[KEY_DRAFT_ID] = replacementId.toString()
                jobObserver?.cancel()
                outputObserver?.cancel()
                mutableState.update {
                    it.copy(jobStatus = null, errorCode = null, result = null)
                }
                startExport()
            }.onFailure { error ->
                mutableState.update { it.copy(errorCode = error.message ?: "RETRY_FAILED") }
            }
        }
    }

    fun reportExternalActionUnavailable() {
        mutableState.update { it.copy(errorCode = "NO_COMPATIBLE_APP") }
    }

    fun reuseRequirements(serializedPlan: String) {
        val plan = runCatching { PhotoPlanCodec.decode(serializedPlan) }.getOrNull() ?: return
        val options = plan.signatureOptions ?: return
        val output = plan.output
        mutableState.update {
            it.copy(
                widthText = output.widthPx?.toString() ?: it.widthText,
                heightText = output.heightPx?.toString() ?: it.heightText,
                maximumSizeText = output.maximumBytes?.div(1_000L)?.toString()
                    ?: it.maximumSizeText,
                dpiText = output.dpi?.toString() ?: it.dpiText,
                outputFormat = output.format,
                grayscale = options.grayscale,
                contrastPercent = options.contrastPercent,
                threshold = options.threshold,
                cleanPaper = options.cleanPaperBackground,
                removeSpeckles = options.removeSpeckles,
                autoCrop = options.autoCrop,
                safeMarginPercent = options.safeMarginPercent,
                inkArgb = options.inkArgb,
                transparentBackground = options.transparentBackground,
                metadata = null,
                originalPreview = null,
                processedPreview = null,
                jobStatus = null,
                result = null,
            )
        }
    }

    fun prepareAnother() {
        savedStateHandle.remove<String>(KEY_DRAFT_ID)
        originalPreviewBitmap = null
        jobObserver?.cancel()
        outputObserver?.cancel()
        mutableState.update {
            SignatureUiState(
                widthText = it.widthText,
                heightText = it.heightText,
                maximumSizeText = it.maximumSizeText,
                dpiText = it.dpiText,
                outputFormat = it.outputFormat,
            )
        }
    }

    fun discardDraft(onFinished: () -> Unit) {
        val id = currentDraftId()
        if (id == null || mutableState.value.jobStatus == JobStatus.SUCCEEDED) {
            onFinished()
            return
        }
        viewModelScope.launch {
            preparationService.discard(id)
            savedStateHandle.remove<String>(KEY_DRAFT_ID)
            onFinished()
        }
    }

    private fun stage(uri: Uri, mimeType: String?, temporarySource: File?) {
        viewModelScope.launch {
            val previousId = currentDraftId()
            val previousState = mutableState.value
            val id = UUID.randomUUID()
            mutableState.update {
                it.copy(
                    isLoadingInput = true,
                    metadata = null,
                    originalPreview = null,
                    processedPreview = null,
                    jobStatus = null,
                    result = null,
                    errorCode = null,
                )
            }
            runCatching {
                val metadata = preparationService.stageAndInspect(uri, id, mimeType)
                val bitmap = preparationService.loadPreview(id)
                metadata to bitmap
            }.onSuccess { (metadata, bitmap) ->
                previousId?.let { preparationService.discard(it) }
                savedStateHandle[KEY_DRAFT_ID] = id.toString()
                originalPreviewBitmap = bitmap
                mutableState.update {
                    it.copy(
                        isLoadingInput = false,
                        metadata = metadata,
                        originalPreview = bitmap.asImageBitmap(),
                    )
                }
                refreshPreview()
            }.onFailure { error ->
                preparationService.discard(id)
                mutableState.value = previousState.copy(
                    isLoadingInput = false,
                    errorCode = error.message ?: "INPUT_UNAVAILABLE",
                )
                refreshPreview()
            }
            temporarySource?.delete()
        }
    }

    private fun refreshPreview() {
        val source = originalPreviewBitmap ?: return
        val state = mutableState.value
        val options = state.toOptions()
        val width = state.widthText.toIntOrNull()?.coerceIn(1, 20_000) ?: 300
        val height = state.heightText.toIntOrNull()?.coerceIn(1, 20_000) ?: 100
        val background = if (state.transparentBackground) {
            0x00FFFFFF
        } else {
            0xFFFFFFFF.toInt()
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                cleanupProcessor.processPreview(
                    source = source,
                    options = options,
                    rotationDegrees = state.rotationQuarterTurns * 90f + state.deskewDegrees,
                    requestedWidth = width,
                    requestedHeight = height,
                    paddingFraction = state.paddingPercent / 100f,
                    horizontalOffset = state.horizontalOffset,
                    verticalOffset = state.verticalOffset,
                    backgroundArgb = background,
                )
            }
                .onSuccess { bitmap ->
                    ensureActive()
                    mutableState.update { it.copy(processedPreview = bitmap.asImageBitmap()) }
                }
                .onFailure { error ->
                    mutableState.update { it.copy(errorCode = error.message ?: "PREVIEW_FAILED") }
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
                val metadata = preparationService.inspectStaged(id)
                val bitmap = preparationService.loadPreview(id)
                metadata to bitmap
            }.onSuccess { (metadata, bitmap) ->
                originalPreviewBitmap = bitmap
                mutableState.update {
                    it.copy(metadata = metadata, originalPreview = bitmap.asImageBitmap())
                }
                refreshPreview()
            }.onFailure { savedStateHandle.remove<String>(KEY_DRAFT_ID) }
        }
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
                            result = SignatureResultUi(
                                artifact = artifact,
                                validationResults = ValidationResultCodec.decode(
                                    artifact.validationJson,
                                ),
                                shareUri = outputAccess.shareUri(artifact),
                            ),
                            errorCode = null,
                        )
                    }
                }
            }
        }
    }

    private fun SignatureUiState.toOptions() = SignatureOptions(
        grayscale = grayscale,
        contrastPercent = contrastPercent,
        threshold = threshold,
        cleanPaperBackground = cleanPaper,
        removeSpeckles = removeSpeckles,
        autoCrop = autoCrop,
        safeMarginPercent = safeMarginPercent,
        inkArgb = inkArgb,
        transparentBackground = transparentBackground,
        cropLeft = cropLeftPercent / 100f,
        cropTop = cropTopPercent / 100f,
        cropRight = cropRightPercent / 100f,
        cropBottom = cropBottomPercent / 100f,
    )

    private fun updateDigits(
        key: String,
        value: String,
        transform: SignatureUiState.(String) -> SignatureUiState,
    ) {
        val filtered = value.filter(Char::isDigit).take(9)
        savedStateHandle[key] = filtered
        mutableState.update { it.transform(filtered) }
        if (key == KEY_WIDTH || key == KEY_HEIGHT) refreshPreview()
    }

    private fun updateOption(
        key: String,
        value: Int,
        transform: SignatureUiState.(Int) -> SignatureUiState,
    ) {
        savedStateHandle[key] = value
        mutableState.update { it.transform(value) }
        refreshPreview()
    }

    private fun updateAndPreview(
        key: String,
        value: Any,
        transform: SignatureUiState.() -> SignatureUiState,
    ) {
        savedStateHandle[key] = value
        mutableState.update { it.transform() }
        refreshPreview()
    }

    private fun currentDraftId(): UUID? = savedStateHandle.get<String>(KEY_DRAFT_ID)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        const val INK_BLACK = 0xFF111111.toInt()
        const val INK_BLUE = 0xFF0A3D91.toInt()
        private const val CAPTURE_DIRECTORY = "signature-captures"
        private const val KEY_DRAFT_ID = "signature.draftId"
        private const val KEY_WIDTH = "signature.width"
        private const val KEY_HEIGHT = "signature.height"
        private const val KEY_MAX_BYTES = "signature.maximumBytes"
        private const val KEY_DPI = "signature.dpi"
        private const val KEY_FORMAT = "signature.format"
        private const val KEY_THRESHOLD = "signature.threshold"
        private const val KEY_CONTRAST = "signature.contrast"
        private const val KEY_MARGIN = "signature.margin"
        private const val KEY_PADDING = "signature.padding"
        private const val KEY_GRAYSCALE = "signature.grayscale"
        private const val KEY_CLEAN_PAPER = "signature.cleanPaper"
        private const val KEY_SPECKLES = "signature.speckles"
        private const val KEY_AUTO_CROP = "signature.autoCrop"
        private const val KEY_TRANSPARENT = "signature.transparent"
        private const val KEY_INK = "signature.ink"
        private const val KEY_CROP_LEFT = "signature.cropLeft"
        private const val KEY_CROP_TOP = "signature.cropTop"
        private const val KEY_CROP_RIGHT = "signature.cropRight"
        private const val KEY_CROP_BOTTOM = "signature.cropBottom"
        private const val KEY_OFFSET_X = "signature.offsetX"
        private const val KEY_OFFSET_Y = "signature.offsetY"
        private const val KEY_ROTATION = "signature.rotation"
        private const val KEY_DESKEW = "signature.deskew"
    }
}
