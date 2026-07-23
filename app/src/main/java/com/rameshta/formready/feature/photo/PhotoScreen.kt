package com.rameshta.formready.feature.photo

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.formready.R
import com.rameshta.formready.core.model.CropMode
import com.rameshta.formready.core.model.ByteUnit
import com.rameshta.formready.core.model.DimensionRule
import com.rameshta.formready.core.model.DimensionInputMode
import com.rameshta.formready.core.model.PhysicalUnit
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.ValidationOutcome
import com.rameshta.formready.core.processing.PrintSheetSize
import com.rameshta.formready.ui.format.readableFileSize
import com.rameshta.formready.ui.format.readableGuidance
import com.rameshta.formready.ui.format.readableValidationValue
import com.rameshta.formready.ui.format.userFacingError

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PhotoRoute(
    onBack: () -> Unit,
    viewModel: PhotoViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.action_share)
    var confirmDiscard by remember { mutableStateOf(false) }
    var printSheet by remember { mutableStateOf(PrintSheetSize.FOUR_BY_SIX) }
    var printCopies by remember { mutableStateOf(4) }
    var printCutGuides by remember { mutableStateOf(true) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.selectPhoto(uri, context.contentResolver.getType(uri))
    }
    val idCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        viewModel.completeIdCapture(it)
    }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            state.result?.artifact?.mimeType ?: "image/jpeg",
        ),
    ) { destination ->
        if (destination != null) viewModel.saveTo(destination)
    }
    val printSheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { destination ->
        if (destination != null) {
            viewModel.savePrintSheet(destination, printSheet, printCopies, printCutGuides)
        }
    }

    fun requestBack() {
        if (state.hasDraft && state.result == null) confirmDiscard = true else onBack()
    }
    BackHandler(onBack = ::requestBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isIdPhotoMode) {
                                R.string.id_photo_title
                            } else {
                                R.string.photo_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = ::requestBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.photo_requirements_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                RequirementsEditor(state = state, viewModel = viewModel)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        enabled = !state.isLoadingInput && state.jobStatus == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (state.metadata == null) {
                                    R.string.photo_choose
                                } else {
                                    R.string.photo_choose_different
                                },
                            ),
                        )
                    }
                    if (state.isIdPhotoMode) {
                        OutlinedButton(
                            onClick = {
                                runCatching { viewModel.createIdCaptureUri() }
                                    .onSuccess(idCamera::launch)
                                    .onFailure { viewModel.reportExternalActionUnavailable() }
                            },
                            enabled = !state.isLoadingInput && state.jobStatus == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.id_photo_camera))
                        }
                    }
                }
            }
            if (state.isLoadingInput) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.photo_inspecting))
                    }
                }
            }
            if (state.isIdPhotoMode && state.preview != null) {
                item {
                    IdPhotoGuidanceCard(state)
                }
            }
            state.metadata?.let { metadata ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.photo_source_heading),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.photo_source_details,
                                    metadata.widthPx,
                                    metadata.heightPx,
                                    readableFileSize(metadata.byteCount),
                                    metadata.format.name,
                                ),
                            )
                            state.preview?.let { preview ->
                                Image(
                                    bitmap = preview,
                                    contentDescription = stringResource(
                                        R.string.photo_preview_description,
                                    ),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp),
                                )
                            }
                        }
                    }
                }
                if (state.jobStatus == null) {
                    item {
                        PhotoEditor(state = state, viewModel = viewModel)
                    }
                    item {
                        Button(
                            onClick = viewModel::startExport,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.photo_prepare_action))
                        }
                    }
                }
            }
            if (state.jobStatus == JobStatus.QUEUED || state.jobStatus == JobStatus.RUNNING) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Column {
                                Text(
                                    stringResource(R.string.photo_processing),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(stringResource(R.string.photo_processing_detail))
                                TextButton(onClick = viewModel::cancelExport) {
                                    Text(stringResource(R.string.action_cancel_processing))
                                }
                            }
                        }
                    }
                }
            }
            state.result?.let { result ->
                item {
                    ResultCard(state = state)
                }
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                saveLauncher.launch(result.artifact.displayName)
                            },
                            enabled = !state.isSaving,
                        ) {
                            Text(stringResource(R.string.action_save_copy))
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(
                                            result.shareUri,
                                            result.artifact.mimeType,
                                        )
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    )
                                }.onFailure {
                                    viewModel.reportExternalActionUnavailable()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.action_open))
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType(result.artifact.mimeType)
                                            .putExtra(Intent.EXTRA_STREAM, result.shareUri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                        shareTitle,
                                    ),
                                    )
                                }.onFailure {
                                    viewModel.reportExternalActionUnavailable()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.action_share))
                        }
                        OutlinedButton(onClick = viewModel::prepareAnother) {
                            Text(stringResource(R.string.action_prepare_another))
                        }
                    }
                }
                if (state.isIdPhotoMode) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.id_photo_print_sheet),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PrintSheetSize.entries.forEach { sheet ->
                                        FilterChip(
                                            selected = printSheet == sheet,
                                            onClick = { printSheet = sheet },
                                            label = {
                                                Text(
                                                    stringResource(
                                                        if (sheet == PrintSheetSize.FOUR_BY_SIX) {
                                                            R.string.id_photo_sheet_4x6
                                                        } else {
                                                            R.string.id_photo_sheet_a4
                                                        },
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(2, 4, 6, 8).forEach { count ->
                                        FilterChip(
                                            selected = printCopies == count,
                                            onClick = { printCopies = count },
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.id_photo_copy_count,
                                                        count,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(stringResource(R.string.id_photo_cut_guides))
                                    Switch(
                                        checked = printCutGuides,
                                        onCheckedChange = { printCutGuides = it },
                                    )
                                }
                                Text(stringResource(R.string.id_photo_print_instruction))
                                Button(
                                    onClick = {
                                        printSheetLauncher.launch("FormReady-ID-print-sheet.pdf")
                                    },
                                    enabled = !state.isPrintSaving,
                                ) {
                                    Text(stringResource(R.string.id_photo_save_print_sheet))
                                }
                                if (state.printSaved) {
                                    Text(stringResource(R.string.id_photo_print_saved))
                                }
                            }
                        }
                    }
                }
            }
            state.errorCode?.let { error ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.photo_error_heading),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(stringResource(R.string.photo_error_code, userFacingError(error)))
                            if (
                                state.jobStatus == JobStatus.FAILED ||
                                state.metadata == null
                            ) {
                                Text(stringResource(R.string.photo_error_recovery))
                            }
                            if (state.jobStatus == JobStatus.FAILED) {
                                Button(onClick = viewModel::retryExport) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }
                }
            }
            if (state.saveSucceeded) {
                item { Text(stringResource(R.string.photo_saved_confirmation)) }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.photo_discard_title)) },
            text = { Text(stringResource(R.string.photo_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discardDraft(onBack)
                    },
                ) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun RequirementsEditor(
    state: PhotoUiState,
    viewModel: PhotoViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.photo_generic_presets),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedPresetId == "portrait-600x800-200kb",
                    onClick = { viewModel.applyPreset("portrait-600x800-200kb") },
                    label = { Text(stringResource(R.string.preset_portrait_600_800)) },
                )
                FilterChip(
                    selected = state.selectedPresetId == "photo-35x45mm-300dpi",
                    onClick = { viewModel.applyPreset("photo-35x45mm-300dpi") },
                    label = { Text(stringResource(R.string.preset_photo_35_45)) },
                )
                FilterChip(
                    selected = state.selectedPresetId == "photo-2x2in-300dpi",
                    onClick = { viewModel.applyPreset("photo-2x2in-300dpi") },
                    label = { Text(stringResource(R.string.preset_photo_2_2)) },
                )
            }
            Text(stringResource(R.string.requirement_dimension_rule))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.dimensionRule == DimensionRule.EXACT,
                    onClick = { viewModel.setDimensionRule(DimensionRule.EXACT) },
                    label = { Text(stringResource(R.string.requirement_exact)) },
                )
                FilterChip(
                    selected = state.dimensionRule == DimensionRule.MAXIMUM,
                    onClick = { viewModel.setDimensionRule(DimensionRule.MAXIMUM) },
                    label = { Text(stringResource(R.string.requirement_maximum)) },
                )
            }
            Text(stringResource(R.string.requirement_dimension_input))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.dimensionInputMode == DimensionInputMode.PIXELS,
                    onClick = { viewModel.setDimensionInputMode(DimensionInputMode.PIXELS) },
                    label = { Text(stringResource(R.string.requirement_pixels)) },
                )
                FilterChip(
                    selected = state.dimensionInputMode == DimensionInputMode.PHYSICAL,
                    onClick = { viewModel.setDimensionInputMode(DimensionInputMode.PHYSICAL) },
                    label = { Text(stringResource(R.string.requirement_physical)) },
                )
            }
            if (state.dimensionInputMode == DimensionInputMode.PIXELS) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = state.widthText,
                        onValueChange = viewModel::setWidth,
                        label = stringResource(R.string.requirement_width_px),
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = state.heightText,
                        onValueChange = viewModel::setHeight,
                        label = stringResource(R.string.requirement_height_px),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.requirement_pixels_help),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = state.physicalWidthText,
                        onValueChange = viewModel::setPhysicalWidth,
                        label = stringResource(R.string.requirement_physical_width),
                        modifier = Modifier.weight(1f),
                        allowDecimal = true,
                    )
                    NumberField(
                        value = state.physicalHeightText,
                        onValueChange = viewModel::setPhysicalHeight,
                        label = stringResource(R.string.requirement_physical_height),
                        modifier = Modifier.weight(1f),
                        allowDecimal = true,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhysicalUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = state.physicalUnit == unit,
                            onClick = { viewModel.setPhysicalUnit(unit) },
                            label = {
                                Text(
                                    stringResource(
                                        when (unit) {
                                            PhysicalUnit.MILLIMETRES -> R.string.unit_mm
                                            PhysicalUnit.CENTIMETRES -> R.string.unit_cm
                                            PhysicalUnit.INCHES -> R.string.unit_inches
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                state.resolvedDimensions()?.let { (width, height) ->
                    Text(
                        stringResource(
                            R.string.requirement_physical_conversion,
                            width,
                            height,
                            state.dpiText,
                        ),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = state.maximumKbText,
                    onValueChange = viewModel::setMaximumKb,
                    label = stringResource(R.string.requirement_maximum_kb),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = state.minimumKbText,
                    onValueChange = viewModel::setMinimumKb,
                    label = stringResource(R.string.requirement_minimum_kb),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(stringResource(R.string.requirement_byte_unit))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(ByteUnit.KB, ByteUnit.MB).forEach { unit ->
                    FilterChip(
                        selected = state.byteUnit == unit,
                        onClick = { viewModel.setByteUnit(unit) },
                        label = { Text(unit.label) },
                    )
                }
            }
            Text(
                state.exactMaximumBytes?.let {
                    stringResource(
                        R.string.requirement_exact_byte_cap,
                        readableFileSize(it),
                    )
                } ?: stringResource(R.string.requirement_exact_byte_cap_invalid),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.outputFormat == OutputFormat.JPEG,
                    onClick = { viewModel.setOutputFormat(OutputFormat.JPEG) },
                    label = { Text(stringResource(R.string.format_jpeg)) },
                )
                FilterChip(
                    selected = state.outputFormat == OutputFormat.PNG,
                    onClick = { viewModel.setOutputFormat(OutputFormat.PNG) },
                    label = { Text(stringResource(R.string.format_png)) },
                )
            }
            NumberField(
                value = state.dpiText,
                onValueChange = viewModel::setDpi,
                label = stringResource(R.string.requirement_dpi),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.requirement_dpi_help))
            Text(stringResource(R.string.photo_background_heading))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    0xFFFFFFFF.toInt() to R.string.background_white,
                    *(if (state.isIdPhotoMode) {
                        arrayOf(
                            0xFFF8F6EF.toInt() to R.string.background_off_white,
                            0xFFDDEEFF.toInt() to R.string.background_light_blue,
                        )
                    } else {
                        arrayOf(
                            0xFFF2F2F2.toInt() to R.string.background_light_gray,
                            0xFF000000.toInt() to R.string.background_black,
                        )
                    }),
                ).forEach { (colour, label) ->
                    FilterChip(
                        selected = state.backgroundArgb == colour,
                        onClick = { viewModel.setBackground(colour) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            if (state.isIdPhotoMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.id_photo_background_replacement))
                    Switch(
                        checked = state.replaceBackground,
                        onCheckedChange = viewModel::setBackgroundReplacement,
                    )
                }
                Text(stringResource(R.string.id_photo_background_notice))
            }
        }
    }
}

@Composable
private fun PhotoEditor(
    state: PhotoUiState,
    viewModel: PhotoViewModel,
) {
    var restoreMask by remember { mutableStateOf(true) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        viewModel.transformGesture(
            zoomChange = zoomChange,
            panHorizontal = panChange.x / 300f,
            panVertical = panChange.y / 300f,
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.photo_editor_heading),
                style = MaterialTheme.typography.titleLarge,
            )
            state.preview?.let { preview ->
                val targetRatio = (state.widthText.toFloatOrNull() ?: 1f) /
                    (state.heightText.toFloatOrNull() ?: 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(targetRatio.coerceIn(0.25f, 4f))
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color(state.backgroundArgb))
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.shapes.medium,
                        )
                        .transformable(transformState),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = preview,
                        contentDescription = stringResource(
                            R.string.photo_editor_preview_description,
                        ),
                        contentScale = if (state.cropMode == CropMode.CROP_FILL) {
                            ContentScale.Crop
                        } else {
                            ContentScale.Fit
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = state.zoom
                                scaleY = state.zoom
                                translationX = state.panX * size.width / 2f
                                translationY = state.panY * size.height / 2f
                                rotationZ = state.rotationQuarterTurns * 90f +
                                    state.straightenDegrees
                            },
                    )
                }
                Text(stringResource(R.string.photo_gesture_hint))
                if (state.isIdPhotoMode && state.replaceBackground) {
                    Text(stringResource(R.string.id_photo_mask_editor_help))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                (preview.width.toFloat() / preview.height)
                                    .coerceIn(0.25f, 4f),
                            )
                            .clip(MaterialTheme.shapes.medium)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.medium,
                            )
                            .pointerInput(restoreMask) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    viewModel.addMaskStroke(
                                        x = change.position.x / size.width,
                                        y = change.position.y / size.height,
                                        radius = 0.03f,
                                        restore = restoreMask,
                                    )
                                }
                            },
                    ) {
                        Image(
                            bitmap = preview,
                            contentDescription = stringResource(
                                R.string.id_photo_mask_editor_description,
                            ),
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = restoreMask,
                            onClick = { restoreMask = true },
                            label = { Text(stringResource(R.string.id_photo_mask_restore)) },
                        )
                        FilterChip(
                            selected = !restoreMask,
                            onClick = { restoreMask = false },
                            label = { Text(stringResource(R.string.id_photo_mask_erase)) },
                        )
                        TextButton(onClick = viewModel::clearMaskStrokes) {
                            Text(stringResource(R.string.id_photo_mask_clear))
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::previewIdBackground,
                        enabled = !state.isLoadingInput,
                    ) {
                        Text(stringResource(R.string.id_photo_preview_background))
                    }
                    state.idProcessedPreview?.let { processed ->
                        Image(
                            bitmap = processed,
                            contentDescription = stringResource(
                                R.string.id_photo_processed_preview,
                            ),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.cropMode == CropMode.CROP_FILL,
                    onClick = { viewModel.setCropMode(CropMode.CROP_FILL) },
                    label = { Text(stringResource(R.string.photo_crop_fill)) },
                )
                FilterChip(
                    selected = state.cropMode == CropMode.FIT_PAD,
                    onClick = { viewModel.setCropMode(CropMode.FIT_PAD) },
                    label = { Text(stringResource(R.string.photo_fit_pad)) },
                )
            }
            Text(stringResource(R.string.photo_zoom_value, state.zoom))
            Slider(
                value = state.zoom,
                onValueChange = viewModel::setZoom,
                valueRange = 1f..3f,
            )
            Text(stringResource(R.string.photo_pan_x_value, state.panX * 100f))
            Slider(
                value = state.panX,
                onValueChange = viewModel::setPanX,
                valueRange = -1f..1f,
            )
            Text(stringResource(R.string.photo_pan_y_value, state.panY * 100f))
            Slider(
                value = state.panY,
                onValueChange = viewModel::setPanY,
                valueRange = -1f..1f,
            )
            Text(stringResource(R.string.photo_nudge_heading))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { viewModel.nudge(0f, -0.1f) }) {
                    Text(stringResource(R.string.direction_up))
                }
                OutlinedButton(onClick = { viewModel.nudge(-0.1f, 0f) }) {
                    Text(stringResource(R.string.direction_left))
                }
                OutlinedButton(onClick = { viewModel.nudge(0.1f, 0f) }) {
                    Text(stringResource(R.string.direction_right))
                }
                OutlinedButton(onClick = { viewModel.nudge(0f, 0.1f) }) {
                    Text(stringResource(R.string.direction_down))
                }
            }
            OutlinedButton(onClick = viewModel::rotateClockwise) {
                Text(
                    stringResource(
                        R.string.photo_rotate_value,
                        state.rotationQuarterTurns * 90,
                    ),
                )
            }
            Text(stringResource(R.string.photo_straighten_value, state.straightenDegrees))
            Slider(
                value = state.straightenDegrees,
                onValueChange = viewModel::setStraighten,
                valueRange = -10f..10f,
            )
            TextButton(onClick = viewModel::resetEdits) {
                Text(stringResource(R.string.action_reset))
            }
            HorizontalDivider()
            state.metadata?.let { metadata ->
                Text(
                    stringResource(
                        R.string.photo_before_after,
                        metadata.widthPx,
                        metadata.heightPx,
                        state.widthText,
                        state.heightText,
                        state.outputFormat.name,
                        readableFileSize(state.exactMaximumBytes ?: 0),
                    ),
                )
            }
        }
    }
}

@Composable
private fun IdPhotoGuidanceCard(state: PhotoUiState) {
    val resources = LocalContext.current.resources
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.id_photo_guidance_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            state.faceGuidance?.let { guidance ->
                Text(stringResource(R.string.id_photo_face_count, guidance.faceCount))
                Text(
                    if (guidance.warnings.isEmpty()) {
                        stringResource(R.string.id_photo_guidance_ok)
                    } else {
                        stringResource(
                            R.string.id_photo_guidance_warning,
                            guidance.warnings.joinToString {
                                resources.getString(idPhotoWarningResource(it))
                            },
                        )
                    },
                )
            } ?: Text(stringResource(R.string.id_photo_guidance_checking))
        }
    }
}

private fun idPhotoWarningResource(code: String): Int =
    when (code) {
        "NO_FACE_DETECTED" -> R.string.id_photo_warning_no_face
        "MULTIPLE_FACES_DETECTED" -> R.string.id_photo_warning_multiple_faces
        "HEAD_TILTED" -> R.string.id_photo_warning_head_tilted
        "FACE_TOO_SMALL" -> R.string.id_photo_warning_face_small
        "EYE_LINE_REVIEW" -> R.string.id_photo_warning_eye_line
        else -> R.string.id_photo_warning_review
    }

@Composable
private fun ResultCard(state: PhotoUiState) {
    val result = requireNotNull(state.result)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = when (result.artifact.readiness) {
                    com.rameshta.formready.core.model.Readiness.READY ->
                        stringResource(R.string.readiness_ready)
                    com.rameshta.formready.core.model.Readiness.READY_WITH_WARNINGS ->
                        stringResource(R.string.readiness_ready_with_warnings)
                    com.rameshta.formready.core.model.Readiness.NOT_READY ->
                        stringResource(R.string.readiness_not_ready)
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(
                    R.string.photo_result_summary,
                    result.artifact.widthPx ?: 0,
                    result.artifact.heightPx ?: 0,
                    readableFileSize(result.artifact.byteCount),
                    result.artifact.dpi?.let {
                        stringResource(R.string.dpi_value, it)
                    } ?: stringResource(R.string.dpi_not_set),
                ),
            )
            HorizontalDivider()
            result.validationResults.forEach { rule ->
                val status = when (rule.outcome) {
                    ValidationOutcome.PASS -> stringResource(R.string.validation_pass)
                    ValidationOutcome.WARNING -> stringResource(R.string.validation_warning)
                    ValidationOutcome.FAIL -> stringResource(R.string.validation_fail)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(
                            R.string.validation_required,
                            status,
                            readableValidationValue(rule.expected),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            R.string.validation_current,
                            readableValidationValue(rule.actual),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        readableGuidance(rule.explanation),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    rule.fixAction?.let { fix ->
                        Text(
                            stringResource(R.string.validation_fix, readableGuidance(fix)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    allowDecimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}
