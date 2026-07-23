package com.rameshta.formready.feature.photo

import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.data.repository.OutputArtifactRepository
import com.rameshta.formready.core.data.settings.DefaultByteUnit
import com.rameshta.formready.core.data.settings.DefaultDimensionUnit
import com.rameshta.formready.core.data.settings.DefaultImageFormat
import com.rameshta.formready.core.data.settings.SettingsRepository
import com.rameshta.formready.core.model.CropMode
import com.rameshta.formready.core.model.ByteUnit
import com.rameshta.formready.core.model.DimensionRule
import com.rameshta.formready.core.model.DimensionInputMode
import com.rameshta.formready.core.model.PhysicalUnit
import com.rameshta.formready.core.model.PhotoPresetCatalog
import com.rameshta.formready.core.model.RequirementConversions
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.IdPhotoOptions
import com.rameshta.formready.core.model.MaskStroke
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.NormalizedTransform
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.OutputSpecification
import com.rameshta.formready.core.model.ProcessingPlan
import com.rameshta.formready.core.model.ValidationRuleResult
import com.rameshta.formready.core.processing.ImageMetadata
import com.rameshta.formready.core.processing.FaceGuidance
import com.rameshta.formready.core.processing.FaceGuidanceEngine
import com.rameshta.formready.core.processing.IdPhotoPrintSheetEngine
import com.rameshta.formready.core.processing.PrintSheetSize
import com.rameshta.formready.core.processing.PersonSegmentationEngine
import com.rameshta.formready.core.processing.PhotoOutputAccess
import com.rameshta.formready.core.processing.PhotoCropCalculator
import com.rameshta.formready.core.processing.PhotoPlanCodec
import com.rameshta.formready.core.processing.PhotoPreparationService
import com.rameshta.formready.core.processing.ProcessingScheduler
import com.rameshta.formready.core.processing.ValidationResultCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File
import javax.inject.Inject

data class PhotoResultUi(
    val artifact: OutputArtifact,
    val validationResults: List<ValidationRuleResult>,
    val shareUri: Uri,
)

data class PhotoUiState(
    val widthText: String = "600",
    val heightText: String = "800",
    val maximumKbText: String = "200",
    val minimumKbText: String = "",
    val dpiText: String = "300",
    val safetyMarginText: String = "",
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val byteUnit: ByteUnit = ByteUnit.KB,
    val dimensionRule: DimensionRule = DimensionRule.EXACT,
    val dimensionInputMode: DimensionInputMode = DimensionInputMode.PIXELS,
    val physicalWidthText: String = "35",
    val physicalHeightText: String = "45",
    val physicalUnit: PhysicalUnit = PhysicalUnit.MILLIMETRES,
    val cropMode: CropMode = CropMode.CROP_FILL,
    val backgroundArgb: Int = 0xFFFFFFFF.toInt(),
    val selectedPresetId: String? = "portrait-600x800-200kb",
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val rotationQuarterTurns: Int = 0,
    val straightenDegrees: Float = 0f,
    val metadata: ImageMetadata? = null,
    val preview: ImageBitmap? = null,
    val isLoadingInput: Boolean = false,
    val jobStatus: JobStatus? = null,
    val errorCode: String? = null,
    val result: PhotoResultUi? = null,
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
    val isIdPhotoMode: Boolean = false,
    val faceGuidance: FaceGuidance? = null,
    val replaceBackground: Boolean = false,
    val maskStrokes: List<MaskStroke> = emptyList(),
    val isPrintSaving: Boolean = false,
    val printSaved: Boolean = false,
    val idProcessedPreview: ImageBitmap? = null,
) {
    val hasDraft: Boolean
        get() = metadata != null || isLoadingInput || jobStatus != null

    val exactMaximumBytes: Long?
        get() = maximumKbText.toLongOrNull()?.let {
            runCatching { Math.multiplyExact(it, byteUnit.bytesPerUnit) }.getOrNull()
        }

    fun resolvedDimensions(): Pair<Int, Int>? {
        if (dimensionInputMode == DimensionInputMode.PIXELS) {
            val width = widthText.toIntOrNull() ?: return null
            val height = heightText.toIntOrNull() ?: return null
            return width to height
        }
        val width = physicalWidthText.toDoubleOrNull() ?: return null
        val height = physicalHeightText.toDoubleOrNull() ?: return null
        val dpi = dpiText.toIntOrNull() ?: return null
        return runCatching {
            RequirementConversions.pixels(width, physicalUnit, dpi) to
                RequirementConversions.pixels(height, physicalUnit, dpi)
        }.getOrNull()
    }
}

@HiltViewModel
class PhotoViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val preparationService: PhotoPreparationService,
    private val jobs: JobRepository,
    private val outputs: OutputArtifactRepository,
    private val scheduler: ProcessingScheduler,
    private val outputAccess: PhotoOutputAccess,
    private val settingsRepository: SettingsRepository,
    private val faceGuidanceEngine: FaceGuidanceEngine,
    private val printSheetEngine: IdPhotoPrintSheetEngine,
    private val personSegmentationEngine: PersonSegmentationEngine,
) : ViewModel() {
    private var pendingIdCapture: File? = null
    private val mutableState = MutableStateFlow(
        PhotoUiState(
            widthText = savedStateHandle[KEY_WIDTH] ?: "600",
            heightText = savedStateHandle[KEY_HEIGHT] ?: "800",
            maximumKbText = savedStateHandle[KEY_MAXIMUM_KB] ?: "200",
            minimumKbText = savedStateHandle[KEY_MINIMUM_KB] ?: "",
            dpiText = savedStateHandle[KEY_DPI] ?: "300",
            safetyMarginText = savedStateHandle[KEY_SAFETY_MARGIN] ?: "",
            outputFormat = savedStateHandle.get<String>(KEY_FORMAT)
                ?.let { runCatching { OutputFormat.valueOf(it) }.getOrNull() }
                ?: OutputFormat.JPEG,
            byteUnit = savedStateHandle.get<String>(KEY_BYTE_UNIT)
                ?.let { runCatching { ByteUnit.valueOf(it) }.getOrNull() }
                ?: ByteUnit.KB,
            dimensionRule = savedStateHandle.get<String>(KEY_DIMENSION_RULE)
                ?.let { runCatching { DimensionRule.valueOf(it) }.getOrNull() }
                ?: DimensionRule.EXACT,
            dimensionInputMode = savedStateHandle.get<String>(KEY_DIMENSION_INPUT_MODE)
                ?.let { runCatching { DimensionInputMode.valueOf(it) }.getOrNull() }
                ?: DimensionInputMode.PIXELS,
            physicalWidthText = savedStateHandle[KEY_PHYSICAL_WIDTH] ?: "35",
            physicalHeightText = savedStateHandle[KEY_PHYSICAL_HEIGHT] ?: "45",
            physicalUnit = savedStateHandle.get<String>(KEY_PHYSICAL_UNIT)
                ?.let { runCatching { PhysicalUnit.valueOf(it) }.getOrNull() }
                ?: PhysicalUnit.MILLIMETRES,
            cropMode = savedStateHandle.get<String>(KEY_CROP_MODE)
                ?.let { runCatching { CropMode.valueOf(it) }.getOrNull() }
                ?: CropMode.CROP_FILL,
            backgroundArgb = savedStateHandle[KEY_BACKGROUND] ?: 0xFFFFFFFF.toInt(),
            selectedPresetId = savedStateHandle[KEY_PRESET_ID]
                ?: "portrait-600x800-200kb",
            zoom = savedStateHandle[KEY_ZOOM] ?: 1f,
            panX = savedStateHandle[KEY_PAN_X] ?: 0f,
            panY = savedStateHandle[KEY_PAN_Y] ?: 0f,
            rotationQuarterTurns = savedStateHandle[KEY_ROTATION] ?: 0,
            straightenDegrees = savedStateHandle[KEY_STRAIGHTEN] ?: 0f,
            isIdPhotoMode = savedStateHandle[KEY_ID_MODE] ?: false,
            replaceBackground = savedStateHandle[KEY_REPLACE_BACKGROUND] ?: false,
        ),
    )
    val uiState: StateFlow<PhotoUiState> = mutableState.asStateFlow()
    private var jobObserver: Job? = null
    private var outputObserver: Job? = null

    init {
        if (
            savedStateHandle.get<String>(KEY_WIDTH) == null &&
            savedStateHandle.get<String>(KEY_FORMAT) == null
        ) {
            viewModelScope.launch {
                val defaults = settingsRepository.settings.first()
                if (!mutableState.value.hasDraft) {
                    mutableState.update { state ->
                        state.copy(
                            selectedPresetId = null,
                            outputFormat = if (
                                defaults.defaultImageFormat == DefaultImageFormat.JPEG
                            ) OutputFormat.JPEG else OutputFormat.PNG,
                            byteUnit = if (defaults.byteUnit == DefaultByteUnit.DECIMAL) {
                                ByteUnit.KB
                            } else {
                                ByteUnit.KIB
                            },
                            dimensionInputMode = if (
                                defaults.dimensionUnit == DefaultDimensionUnit.PIXELS
                            ) DimensionInputMode.PIXELS else DimensionInputMode.PHYSICAL,
                            physicalUnit = when (defaults.dimensionUnit) {
                                DefaultDimensionUnit.PIXELS,
                                DefaultDimensionUnit.MILLIMETRES,
                                -> PhysicalUnit.MILLIMETRES
                                DefaultDimensionUnit.CENTIMETRES -> PhysicalUnit.CENTIMETRES
                                DefaultDimensionUnit.INCHES -> PhysicalUnit.INCHES
                            },
                            safetyMarginText = if (defaults.safetyMarginEnabled) "" else "0",
                        )
                    }
                }
            }
        }
        savedStateHandle.get<String>(KEY_DRAFT_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(::restoreDraft)
    }

    fun selectPhoto(uri: Uri, reportedMimeType: String?) {
        viewModelScope.launch {
            currentDraftId()?.let { existing ->
                if (mutableState.value.jobStatus == null) preparationService.discard(existing)
            }
            val draftId = UUID.randomUUID()
            savedStateHandle[KEY_DRAFT_ID] = draftId.toString()
            mutableState.update {
                it.copy(
                    isLoadingInput = true,
                    metadata = null,
                    preview = null,
                    errorCode = null,
                    result = null,
                    jobStatus = null,
                )
            }
            runCatching {
                val metadata = preparationService.stageAndInspect(
                    uri,
                    draftId,
                    reportedMimeType,
                )
                val bitmap = preparationService.loadPreview(draftId)
                val guidance = if (mutableState.value.isIdPhotoMode) {
                    faceGuidanceEngine.analyze(bitmap)
                } else {
                    null
                }
                Triple(metadata, bitmap.toComposePreview(), guidance)
            }.onSuccess { (metadata, preview, guidance) ->
                mutableState.update {
                    it.copy(
                        isLoadingInput = false,
                        metadata = metadata,
                        preview = preview,
                        faceGuidance = guidance,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isLoadingInput = false,
                        errorCode = error.message ?: "INPUT_UNAVAILABLE",
                    )
                }
            }
        }
    }

    fun createIdCaptureUri(): Uri {
        val directory = File(context.cacheDir, "id-photo-captures").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        pendingIdCapture = file
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun completeIdCapture(success: Boolean) {
        val file = pendingIdCapture.also { pendingIdCapture = null } ?: return
        if (success && file.length() > 0L) {
            selectPhoto(Uri.fromFile(file), "image/jpeg")
        } else {
            file.delete()
        }
    }

    fun setWidth(value: String) =
        updateText(KEY_WIDTH, value) { state, filtered -> state.copy(widthText = filtered) }

    fun enableIdPhotoMode() {
        savedStateHandle[KEY_ID_MODE] = true
        savedStateHandle[KEY_WIDTH] = "600"
        savedStateHandle[KEY_HEIGHT] = "800"
        savedStateHandle[KEY_MAXIMUM_KB] = "200"
        savedStateHandle[KEY_DPI] = "300"
        savedStateHandle[KEY_DIMENSION_INPUT_MODE] = DimensionInputMode.PIXELS.name
        savedStateHandle[KEY_FORMAT] = OutputFormat.JPEG.name
        savedStateHandle[KEY_CROP_MODE] = CropMode.CROP_FILL.name
        savedStateHandle[KEY_BACKGROUND] = 0xFFFFFFFF.toInt()
        mutableState.update {
            it.copy(
                isIdPhotoMode = true,
                widthText = "600",
                heightText = "800",
                maximumKbText = "200",
                dpiText = "300",
                dimensionInputMode = DimensionInputMode.PIXELS,
                outputFormat = OutputFormat.JPEG,
                cropMode = CropMode.CROP_FILL,
                selectedPresetId = null,
                backgroundArgb = 0xFFFFFFFF.toInt(),
                result = null,
            )
        }
    }

    fun enableStandardPhotoMode() {
        savedStateHandle[KEY_ID_MODE] = false
        savedStateHandle[KEY_REPLACE_BACKGROUND] = false
        mutableState.update {
            it.copy(
                isIdPhotoMode = false,
                faceGuidance = null,
                replaceBackground = false,
                maskStrokes = emptyList(),
            )
        }
    }

    fun setBackgroundReplacement(enabled: Boolean) {
        savedStateHandle[KEY_REPLACE_BACKGROUND] = enabled
        mutableState.update {
            it.copy(
                replaceBackground = enabled,
                maskStrokes = emptyList(),
                idProcessedPreview = null,
            )
        }
    }

    fun setIdBackground(argb: Int) {
        setBackground(argb)
        mutableState.update { it.copy(idProcessedPreview = null) }
    }

    fun addMaskStroke(x: Float, y: Float, radius: Float, restore: Boolean) {
        mutableState.update { state ->
            if (state.maskStrokes.size >= 500) {
                state.copy(errorCode = "MASK_STROKE_LIMIT")
            } else {
                state.copy(
                    maskStrokes = state.maskStrokes + MaskStroke(
                        x = x.coerceIn(0f, 1f),
                        y = y.coerceIn(0f, 1f),
                        radius = radius.coerceIn(0.002f, 0.25f),
                        restore = restore,
                    ),
                    idProcessedPreview = null,
                )
            }
        }
    }

    fun clearMaskStrokes() {
        mutableState.update { it.copy(maskStrokes = emptyList(), idProcessedPreview = null) }
    }

    fun previewIdBackground() {
        val state = mutableState.value
        val preview = state.preview ?: return
        if (!state.isIdPhotoMode || !state.replaceBackground || state.isLoadingInput) return
        mutableState.update { it.copy(isLoadingInput = true, errorCode = null) }
        viewModelScope.launch {
            runCatching {
                personSegmentationEngine.replaceBackground(
                    source = preview.asAndroidBitmap(),
                    backgroundArgb = state.backgroundArgb,
                    strokes = state.maskStrokes,
                ).asImageBitmap()
            }.onSuccess { processed ->
                mutableState.update {
                    it.copy(isLoadingInput = false, idProcessedPreview = processed)
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isLoadingInput = false,
                        errorCode = error.message ?: "SEGMENTATION_UNAVAILABLE",
                    )
                }
            }
        }
    }

    fun setHeight(value: String) =
        updateText(KEY_HEIGHT, value) { state, filtered -> state.copy(heightText = filtered) }

    fun setMaximumKb(value: String) =
        updateText(KEY_MAXIMUM_KB, value) { state, filtered ->
            state.copy(maximumKbText = filtered)
        }

    fun setMinimumKb(value: String) =
        updateText(KEY_MINIMUM_KB, value) { state, filtered ->
            state.copy(minimumKbText = filtered)
        }

    fun setDpi(value: String) =
        updateText(KEY_DPI, value) { state, filtered -> state.copy(dpiText = filtered) }

    fun setSafetyMargin(value: String) =
        updateText(KEY_SAFETY_MARGIN, value) { state, filtered ->
            state.copy(safetyMarginText = filtered)
        }

    fun setOutputFormat(format: OutputFormat) {
        savedStateHandle[KEY_FORMAT] = format.name
        mutableState.update { it.copy(outputFormat = format) }
    }

    fun setByteUnit(unit: ByteUnit) {
        savedStateHandle[KEY_BYTE_UNIT] = unit.name
        savedStateHandle[KEY_PRESET_ID] = null
        mutableState.update { it.copy(byteUnit = unit, selectedPresetId = null) }
    }

    fun setDimensionRule(rule: DimensionRule) {
        savedStateHandle[KEY_DIMENSION_RULE] = rule.name
        savedStateHandle[KEY_PRESET_ID] = null
        mutableState.update { it.copy(dimensionRule = rule, selectedPresetId = null) }
    }

    fun setDimensionInputMode(mode: DimensionInputMode) {
        savedStateHandle[KEY_DIMENSION_INPUT_MODE] = mode.name
        mutableState.update { it.copy(dimensionInputMode = mode, selectedPresetId = null) }
    }

    fun setPhysicalUnit(unit: PhysicalUnit) {
        savedStateHandle[KEY_PHYSICAL_UNIT] = unit.name
        mutableState.update { it.copy(physicalUnit = unit, selectedPresetId = null) }
    }

    fun setPhysicalWidth(value: String) =
        updateDecimal(KEY_PHYSICAL_WIDTH, value) { state, filtered ->
            state.copy(physicalWidthText = filtered, selectedPresetId = null)
        }

    fun setPhysicalHeight(value: String) =
        updateDecimal(KEY_PHYSICAL_HEIGHT, value) { state, filtered ->
            state.copy(physicalHeightText = filtered, selectedPresetId = null)
        }

    fun setCropMode(mode: CropMode) {
        savedStateHandle[KEY_CROP_MODE] = mode.name
        savedStateHandle[KEY_PRESET_ID] = null
        mutableState.update { it.copy(cropMode = mode, selectedPresetId = null) }
    }

    fun setBackground(argb: Int) {
        savedStateHandle[KEY_BACKGROUND] = argb
        savedStateHandle[KEY_PRESET_ID] = null
        mutableState.update { it.copy(backgroundArgb = argb, selectedPresetId = null) }
    }

    fun applyPreset(id: String) {
        val preset = PhotoPresetCatalog.find(id) ?: return
        mutableState.update {
            it.copy(
                widthText = preset.widthPx.toString(),
                heightText = preset.heightPx.toString(),
                maximumKbText = preset.maximumValue.toString(),
                minimumKbText = "",
                dpiText = preset.dpi?.toString().orEmpty(),
                safetyMarginText = "",
                outputFormat = preset.outputFormat,
                byteUnit = preset.byteUnit,
                dimensionRule = DimensionRule.EXACT,
                dimensionInputMode = if (preset.physicalUnit == null) {
                    DimensionInputMode.PIXELS
                } else {
                    DimensionInputMode.PHYSICAL
                },
                physicalWidthText = preset.physicalWidth?.toString()?.removeSuffix(".0") ?: "35",
                physicalHeightText = preset.physicalHeight?.toString()?.removeSuffix(".0") ?: "45",
                physicalUnit = preset.physicalUnit ?: PhysicalUnit.MILLIMETRES,
                cropMode = preset.cropMode,
                selectedPresetId = preset.id,
            )
        }
        savedStateHandle[KEY_WIDTH] = preset.widthPx.toString()
        savedStateHandle[KEY_HEIGHT] = preset.heightPx.toString()
        savedStateHandle[KEY_MAXIMUM_KB] = preset.maximumValue.toString()
        savedStateHandle[KEY_MINIMUM_KB] = ""
        savedStateHandle[KEY_DPI] = preset.dpi?.toString().orEmpty()
        savedStateHandle[KEY_SAFETY_MARGIN] = ""
        savedStateHandle[KEY_FORMAT] = preset.outputFormat.name
        savedStateHandle[KEY_BYTE_UNIT] = preset.byteUnit.name
        savedStateHandle[KEY_DIMENSION_RULE] = DimensionRule.EXACT.name
        savedStateHandle[KEY_DIMENSION_INPUT_MODE] = mutableState.value.dimensionInputMode.name
        savedStateHandle[KEY_PHYSICAL_WIDTH] = mutableState.value.physicalWidthText
        savedStateHandle[KEY_PHYSICAL_HEIGHT] = mutableState.value.physicalHeightText
        savedStateHandle[KEY_PHYSICAL_UNIT] = mutableState.value.physicalUnit.name
        savedStateHandle[KEY_CROP_MODE] = preset.cropMode.name
        savedStateHandle[KEY_PRESET_ID] = preset.id
    }

    fun setZoom(value: Float) {
        savedStateHandle[KEY_ZOOM] = value.coerceIn(1f, 3f)
        mutableState.update { it.copy(zoom = value.coerceIn(1f, 3f)) }
    }

    fun nudge(horizontal: Float, vertical: Float) {
        mutableState.update {
            it.copy(
                panX = (it.panX + horizontal).coerceIn(-1f, 1f),
                panY = (it.panY + vertical).coerceIn(-1f, 1f),
            ).also { updated ->
                savedStateHandle[KEY_PAN_X] = updated.panX
                savedStateHandle[KEY_PAN_Y] = updated.panY
            }
        }
    }

    fun transformGesture(zoomChange: Float, panHorizontal: Float, panVertical: Float) {
        mutableState.update {
            it.copy(
                zoom = (it.zoom * zoomChange).coerceIn(1f, 3f),
                panX = (it.panX + panHorizontal).coerceIn(-1f, 1f),
                panY = (it.panY + panVertical).coerceIn(-1f, 1f),
            ).also { updated ->
                savedStateHandle[KEY_ZOOM] = updated.zoom
                savedStateHandle[KEY_PAN_X] = updated.panX
                savedStateHandle[KEY_PAN_Y] = updated.panY
            }
        }
    }

    fun setPanX(value: Float) {
        savedStateHandle[KEY_PAN_X] = value.coerceIn(-1f, 1f)
        mutableState.update { it.copy(panX = value.coerceIn(-1f, 1f)) }
    }

    fun setPanY(value: Float) {
        savedStateHandle[KEY_PAN_Y] = value.coerceIn(-1f, 1f)
        mutableState.update { it.copy(panY = value.coerceIn(-1f, 1f)) }
    }

    fun rotateClockwise() {
        mutableState.update {
            it.copy(rotationQuarterTurns = (it.rotationQuarterTurns + 1) % 4).also { updated ->
                savedStateHandle[KEY_ROTATION] = updated.rotationQuarterTurns
            }
        }
    }

    fun setStraighten(value: Float) {
        savedStateHandle[KEY_STRAIGHTEN] = value.coerceIn(-10f, 10f)
        mutableState.update { it.copy(straightenDegrees = value.coerceIn(-10f, 10f)) }
    }

    fun resetEdits() {
        savedStateHandle[KEY_ZOOM] = 1f
        savedStateHandle[KEY_PAN_X] = 0f
        savedStateHandle[KEY_PAN_Y] = 0f
        savedStateHandle[KEY_ROTATION] = 0
        savedStateHandle[KEY_STRAIGHTEN] = 0f
        mutableState.update {
            it.copy(
                zoom = 1f,
                panX = 0f,
                panY = 0f,
                rotationQuarterTurns = 0,
                straightenDegrees = 0f,
            )
        }
    }

    fun startExport() {
        val state = mutableState.value
        if (state.jobStatus != null || state.isLoadingInput) return
        val draftId = currentDraftId() ?: return
        val metadata = state.metadata ?: return
        val dimensions = state.resolvedDimensions()
        val width = dimensions?.first
        val height = dimensions?.second
        val maximumBytes = state.maximumKbText.toLongOrNull()
            ?.let { Math.multiplyExact(it, state.byteUnit.bytesPerUnit) }
        val minimumBytes = state.minimumKbText.toLongOrNull()
            ?.let { Math.multiplyExact(it, state.byteUnit.bytesPerUnit) }
        val dpi = state.dpiText.toIntOrNull()
        val safetyMarginBytes = state.safetyMarginText.toLongOrNull()
        if (
            width == null || width !in 1..20_000 ||
            height == null || height !in 1..20_000 ||
            maximumBytes == null || maximumBytes <= 0 ||
            (minimumBytes != null && minimumBytes > maximumBytes) ||
            (safetyMarginBytes != null && safetyMarginBytes >= maximumBytes) ||
            (dpi != null && dpi !in 1..2_400)
        ) {
            mutableState.update { it.copy(errorCode = "INVALID_REQUIREMENTS") }
            return
        }

        val transforms = buildList {
            val rotation = state.rotationQuarterTurns * 90f + state.straightenDegrees
            if (rotation != 0f) add(NormalizedTransform.Rotate(rotation))
            if (state.cropMode == CropMode.FIT_PAD) {
                add(NormalizedTransform.FitPad(backgroundArgb = state.backgroundArgb))
            } else {
                add(calculateCrop(metadata, width, height, state))
            }
        }
        val output = OutputSpecification(
            format = state.outputFormat,
            widthPx = width,
            heightPx = height,
            dimensionRule = state.dimensionRule,
            byteUnit = state.byteUnit,
            physicalWidth = if (state.dimensionInputMode == DimensionInputMode.PHYSICAL) {
                state.physicalWidthText.toDoubleOrNull()
            } else {
                null
            },
            physicalHeight = if (state.dimensionInputMode == DimensionInputMode.PHYSICAL) {
                state.physicalHeightText.toDoubleOrNull()
            } else {
                null
            },
            physicalUnit = if (state.dimensionInputMode == DimensionInputMode.PHYSICAL) {
                state.physicalUnit
            } else {
                null
            },
            maximumBytes = maximumBytes,
            minimumBytes = minimumBytes,
            dpi = dpi,
            safetyMarginBytes = safetyMarginBytes,
            backgroundArgb = state.backgroundArgb,
        )
        val plan = ProcessingPlan(
            jobId = draftId,
            transforms = transforms,
            output = output,
            hardRuleIds = setOf(
                "photo.format",
                "photo.dimensions",
                "photo.maximum_bytes",
                "photo.color_space",
                *if (minimumBytes != null) arrayOf("photo.minimum_bytes") else emptyArray(),
                *if (dpi != null) arrayOf("photo.dpi") else emptyArray(),
            ),
            advisoryRuleIds = setOf("photo.quality_guard", "photo.upscaling"),
            idPhotoOptions = if (state.isIdPhotoMode) {
                IdPhotoOptions(
                    replaceBackground = state.replaceBackground,
                    backgroundArgb = state.backgroundArgb,
                    maskStrokes = state.maskStrokes,
                )
            } else {
                null
            },
        )
        mutableState.update { it.copy(jobStatus = JobStatus.QUEUED, errorCode = null) }
        viewModelScope.launch {
            runCatching {
                jobs.create(
                    plan = plan,
                    projectId = null,
                    type = JobType.PHOTO,
                    serializedPlan = PhotoPlanCodec.encode(plan),
                    stagedInputRelativePath = preparationService.stagedRelativePath(draftId),
                )
                observeJob(draftId)
                scheduler.enqueue(draftId)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        jobStatus = null,
                        errorCode = error.message ?: "EXPORT_START_FAILED",
                    )
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
                    mutableState.update {
                        it.copy(isSaving = false, saveSucceeded = true, errorCode = null)
                    }
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

    fun savePrintSheet(
        destination: Uri,
        sheet: PrintSheetSize,
        copies: Int,
        cutGuides: Boolean,
    ) {
        val state = mutableState.value
        val result = state.result ?: return
        if (!state.isIdPhotoMode || state.isPrintSaving) return
        val dimensions = state.resolvedDimensions() ?: return
        val dpi = state.dpiText.toIntOrNull()?.takeIf { it > 0 } ?: return
        val widthMm = dimensions.first.toDouble() / dpi * 25.4
        val heightMm = dimensions.second.toDouble() / dpi * 25.4
        mutableState.update {
            it.copy(isPrintSaving = true, printSaved = false, errorCode = null)
        }
        viewModelScope.launch {
            val candidate = File(context.noBackupFilesDir, "print-sheets/${UUID.randomUUID()}.pdf")
            runCatching {
                printSheetEngine.create(
                    source = outputAccess.outputFile(result.artifact),
                    destination = candidate,
                    sheet = sheet,
                    copies = copies,
                    photoWidthMm = widthMm,
                    photoHeightMm = heightMm,
                    cutGuides = cutGuides,
                )
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                        candidate.inputStream().buffered().use { it.copyTo(output) }
                    } ?: error("DESTINATION_WRITE_FAILED")
                }
            }.onSuccess {
                mutableState.update { it.copy(isPrintSaving = false, printSaved = true) }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isPrintSaving = false,
                        errorCode = error.message ?: "PRINT_SHEET_FAILED",
                    )
                }
            }
            candidate.delete()
        }
    }

    fun cancelExport() {
        val id = currentDraftId() ?: return
        viewModelScope.launch { scheduler.cancel(id) }
    }

    fun reportExternalActionUnavailable() {
        mutableState.update { it.copy(errorCode = "NO_COMPATIBLE_APP") }
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
                mutableState.update {
                    it.copy(errorCode = error.message ?: "RETRY_FAILED")
                }
            }
        }
    }

    fun prepareAnother() {
        val previousId = currentDraftId()
        if (previousId != null && mutableState.value.jobStatus != JobStatus.SUCCEEDED) {
            viewModelScope.launch { preparationService.discard(previousId) }
        }
        savedStateHandle.remove<String>(KEY_DRAFT_ID)
        jobObserver?.cancel()
        outputObserver?.cancel()
        mutableState.update {
            PhotoUiState(
                widthText = it.widthText,
                heightText = it.heightText,
                maximumKbText = it.maximumKbText,
                minimumKbText = it.minimumKbText,
                dpiText = it.dpiText,
                safetyMarginText = it.safetyMarginText,
                outputFormat = it.outputFormat,
                byteUnit = it.byteUnit,
                dimensionRule = it.dimensionRule,
                dimensionInputMode = it.dimensionInputMode,
                physicalWidthText = it.physicalWidthText,
                physicalHeightText = it.physicalHeightText,
                physicalUnit = it.physicalUnit,
                cropMode = it.cropMode,
                backgroundArgb = it.backgroundArgb,
                selectedPresetId = it.selectedPresetId,
                isIdPhotoMode = it.isIdPhotoMode,
                replaceBackground = it.replaceBackground,
            )
        }
    }

    fun reuseRequirements(serializedPlan: String) {
        val plan = runCatching { PhotoPlanCodec.decode(serializedPlan) }.getOrNull() ?: return
        if (plan.output.format !in setOf(OutputFormat.JPEG, OutputFormat.PNG)) return
        val unit = plan.output.byteUnit
        val maximumValue = plan.output.maximumBytes
            ?.div(unit.bytesPerUnit)
            ?.toString()
            .orEmpty()
        val minimumValue = plan.output.minimumBytes
            ?.div(unit.bytesPerUnit)
            ?.toString()
            .orEmpty()
        prepareAnother()
        mutableState.update {
            it.copy(
                widthText = plan.output.widthPx?.toString().orEmpty(),
                heightText = plan.output.heightPx?.toString().orEmpty(),
                maximumKbText = maximumValue,
                minimumKbText = minimumValue,
                dpiText = plan.output.dpi?.toString().orEmpty(),
                safetyMarginText = plan.output.safetyMarginBytes?.toString().orEmpty(),
                outputFormat = plan.output.format,
                byteUnit = unit,
                dimensionRule = plan.output.dimensionRule,
                dimensionInputMode = if (plan.output.physicalUnit == null) {
                    DimensionInputMode.PIXELS
                } else {
                    DimensionInputMode.PHYSICAL
                },
                physicalWidthText = plan.output.physicalWidth?.toString().orEmpty(),
                physicalHeightText = plan.output.physicalHeight?.toString().orEmpty(),
                physicalUnit = plan.output.physicalUnit ?: PhysicalUnit.MILLIMETRES,
                cropMode = if (
                    plan.transforms.any { transform -> transform is NormalizedTransform.FitPad }
                ) {
                    CropMode.FIT_PAD
                } else {
                    CropMode.CROP_FILL
                },
                backgroundArgb = plan.output.backgroundArgb,
                selectedPresetId = null,
                isIdPhotoMode = plan.idPhotoOptions != null,
                replaceBackground = plan.idPhotoOptions?.replaceBackground ?: false,
                maskStrokes = emptyList(),
            )
        }
        savedStateHandle[KEY_WIDTH] = plan.output.widthPx?.toString().orEmpty()
        savedStateHandle[KEY_HEIGHT] = plan.output.heightPx?.toString().orEmpty()
        savedStateHandle[KEY_MAXIMUM_KB] = maximumValue
        savedStateHandle[KEY_MINIMUM_KB] = minimumValue
        savedStateHandle[KEY_DPI] = plan.output.dpi?.toString().orEmpty()
        savedStateHandle[KEY_SAFETY_MARGIN] =
            plan.output.safetyMarginBytes?.toString().orEmpty()
        savedStateHandle[KEY_FORMAT] = plan.output.format.name
        savedStateHandle[KEY_BYTE_UNIT] = unit.name
        savedStateHandle[KEY_DIMENSION_RULE] = plan.output.dimensionRule.name
        savedStateHandle[KEY_DIMENSION_INPUT_MODE] = mutableState.value.dimensionInputMode.name
        savedStateHandle[KEY_PHYSICAL_WIDTH] = mutableState.value.physicalWidthText
        savedStateHandle[KEY_PHYSICAL_HEIGHT] = mutableState.value.physicalHeightText
        savedStateHandle[KEY_PHYSICAL_UNIT] = mutableState.value.physicalUnit.name
        savedStateHandle[KEY_CROP_MODE] = mutableState.value.cropMode.name
        savedStateHandle[KEY_BACKGROUND] = plan.output.backgroundArgb
        savedStateHandle.remove<String>(KEY_PRESET_ID)
        savedStateHandle[KEY_ID_MODE] = plan.idPhotoOptions != null
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

    private fun restoreDraft(draftId: UUID) {
        viewModelScope.launch {
            val existingJob = jobs.get(draftId)
            if (existingJob != null) {
                observeJob(draftId)
                return@launch
            }
            runCatching {
                val metadata = preparationService.inspectStaged(draftId)
                val bitmap = preparationService.loadPreview(draftId)
                val guidance = if (mutableState.value.isIdPhotoMode) {
                    faceGuidanceEngine.analyze(bitmap)
                } else {
                    null
                }
                Triple(metadata, bitmap.toComposePreview(), guidance)
            }.onSuccess { (metadata, preview, guidance) ->
                mutableState.update {
                    it.copy(metadata = metadata, preview = preview, faceGuidance = guidance)
                }
            }.onFailure {
                savedStateHandle.remove<String>(KEY_DRAFT_ID)
            }
        }
    }

    private fun observeJob(jobId: UUID) {
        jobObserver?.cancel()
        outputObserver?.cancel()
        jobObserver = viewModelScope.launch {
            jobs.observe(jobId).collect { job ->
                mutableState.update {
                    it.copy(jobStatus = job?.status, errorCode = job?.errorCode ?: it.errorCode)
                }
            }
        }
        outputObserver = viewModelScope.launch {
            outputs.observeLatestForJob(jobId).collect { artifact ->
                if (artifact != null) {
                    mutableState.update {
                        it.copy(
                            result = PhotoResultUi(
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

    private fun calculateCrop(
        metadata: ImageMetadata,
        targetWidth: Int,
        targetHeight: Int,
        state: PhotoUiState,
    ): NormalizedTransform.Crop = PhotoCropCalculator.calculate(
        sourceWidthPx = metadata.widthPx,
        sourceHeightPx = metadata.heightPx,
        targetWidthPx = targetWidth,
        targetHeightPx = targetHeight,
        zoom = state.zoom,
        panX = state.panX,
        panY = state.panY,
        quarterTurns = state.rotationQuarterTurns,
    )

    private fun currentDraftId(): UUID? = savedStateHandle.get<String>(KEY_DRAFT_ID)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun updateText(
        key: String,
        value: String,
        transform: (PhotoUiState, String) -> PhotoUiState,
    ) {
        val filtered = value.filter(Char::isDigit).take(9)
        savedStateHandle[key] = filtered
        mutableState.update { state -> transform(state, filtered) }
    }

    private fun updateDecimal(
        key: String,
        value: String,
        transform: (PhotoUiState, String) -> PhotoUiState,
    ) {
        val filtered = buildString {
            value.forEach { character ->
                if (character.isDigit() || (character == '.' && '.' !in this)) {
                    append(character)
                }
            }
        }.take(9)
        savedStateHandle[key] = filtered
        mutableState.update { state -> transform(state, filtered) }
    }

    private fun Bitmap.toComposePreview(): ImageBitmap = asImageBitmap()

    override fun onCleared() {
        pendingIdCapture?.delete()
        super.onCleared()
    }

    companion object {
        private const val KEY_DRAFT_ID = "photo.draftId"
        private const val KEY_WIDTH = "photo.width"
        private const val KEY_HEIGHT = "photo.height"
        private const val KEY_MAXIMUM_KB = "photo.maximumKb"
        private const val KEY_MINIMUM_KB = "photo.minimumKb"
        private const val KEY_DPI = "photo.dpi"
        private const val KEY_SAFETY_MARGIN = "photo.safetyMargin"
        private const val KEY_FORMAT = "photo.format"
        private const val KEY_BYTE_UNIT = "photo.byteUnit"
        private const val KEY_DIMENSION_RULE = "photo.dimensionRule"
        private const val KEY_DIMENSION_INPUT_MODE = "photo.dimensionInputMode"
        private const val KEY_PHYSICAL_WIDTH = "photo.physicalWidth"
        private const val KEY_PHYSICAL_HEIGHT = "photo.physicalHeight"
        private const val KEY_PHYSICAL_UNIT = "photo.physicalUnit"
        private const val KEY_CROP_MODE = "photo.cropMode"
        private const val KEY_BACKGROUND = "photo.background"
        private const val KEY_PRESET_ID = "photo.presetId"
        private const val KEY_ZOOM = "photo.zoom"
        private const val KEY_PAN_X = "photo.panX"
        private const val KEY_PAN_Y = "photo.panY"
        private const val KEY_ROTATION = "photo.rotation"
        private const val KEY_STRAIGHTEN = "photo.straighten"
        private const val KEY_ID_MODE = "photo.idMode"
        private const val KEY_REPLACE_BACKGROUND = "photo.replaceBackground"
    }
}
