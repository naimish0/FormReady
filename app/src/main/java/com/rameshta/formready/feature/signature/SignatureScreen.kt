package com.rameshta.formready.feature.signature

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.formready.R
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.ui.format.readableFileSize
import com.rameshta.formready.ui.format.readableValidationValue
import com.rameshta.formready.ui.format.userFacingError
import kotlin.math.roundToInt

@Composable
fun SignatureRoute(
    onBack: () -> Unit,
    viewModel: SignatureViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.signature_share)
    var confirmDiscard by remember { mutableStateOf(false) }
    var showDrawing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            viewModel.selectSignature(it, context.contentResolver.getType(it))
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.completeCapture(success)
    }
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(state.result?.artifact?.mimeType ?: "image/png"),
    ) { uri -> uri?.let(viewModel::saveTo) }

    fun navigateBack() {
        if (state.hasDraft && state.jobStatus != JobStatus.SUCCEEDED) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = ::navigateBack)

    SignatureScreen(
        state = state,
        showDrawing = showDrawing,
        onBack = ::navigateBack,
        onChoose = { picker.launch(arrayOf("image/*")) },
        onCamera = {
            runCatching { viewModel.createCaptureUri() }
                .onSuccess {
                    camera.launch(it)
                }
                .onFailure { viewModel.reportExternalActionUnavailable() }
        },
        onToggleDrawing = { showDrawing = !showDrawing },
        onUseDrawing = {
            viewModel.useDrawing(it)
            showDrawing = false
        },
        viewModel = viewModel,
        onSave = {
            state.result?.artifact?.let { save.launch(it.displayName) }
        },
        onOpen = {
            state.result?.let { result ->
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(result.shareUri, result.artifact.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                } catch (_: ActivityNotFoundException) {
                    viewModel.reportExternalActionUnavailable()
                }
            }
        },
        onShare = {
            state.result?.let { result ->
                try {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType(result.artifact.mimeType)
                                .putExtra(Intent.EXTRA_STREAM, result.shareUri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            shareTitle,
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    viewModel.reportExternalActionUnavailable()
                }
            }
        },
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.signature_discard_title)) },
            text = { Text(stringResource(R.string.signature_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discardDraft(onBack)
                    },
                ) { Text(stringResource(R.string.action_discard)) }
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
private fun SignatureScreen(
    state: SignatureUiState,
    showDrawing: Boolean,
    onBack: () -> Unit,
    onChoose: () -> Unit,
    onCamera: () -> Unit,
    onToggleDrawing: () -> Unit,
    onUseDrawing: (Bitmap) -> Unit,
    viewModel: SignatureViewModel,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.signature_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
        item {
            Text(
                stringResource(R.string.signature_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SectionCard(stringResource(R.string.signature_input)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onChoose) { Text(stringResource(R.string.signature_gallery)) }
                    OutlinedButton(onClick = onCamera) {
                        Text(stringResource(R.string.signature_camera))
                    }
                }
                OutlinedButton(onClick = onToggleDrawing) {
                    Text(stringResource(R.string.signature_draw))
                }
                if (showDrawing) {
                    SignatureDrawingPad(onUseDrawing)
                }
                if (state.isLoadingInput) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.metadata?.let {
                    Text(
                        stringResource(
                            R.string.signature_input_details,
                            it.widthPx,
                            it.heightPx,
                            readableFileSize(it.byteCount),
                        ),
                    )
                }
            }
        }
        if (state.originalPreview != null) {
            item {
                SectionCard(stringResource(R.string.signature_comparison)) {
                    Text(stringResource(R.string.signature_original))
                    Image(
                        bitmap = state.originalPreview,
                        contentDescription = stringResource(R.string.signature_original),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(ComposeColor.White),
                        contentScale = ContentScale.Fit,
                    )
                    state.processedPreview?.let {
                        Text(stringResource(R.string.signature_processed_preview))
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(
                                R.string.signature_processed_preview,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(
                                    if (state.transparentBackground) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        ComposeColor.White
                                    },
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.signature_requirements)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RequirementField(
                        value = state.widthText,
                        label = stringResource(R.string.requirement_width_px),
                        onValueChange = viewModel::setWidth,
                        modifier = Modifier.weight(1f),
                    )
                    RequirementField(
                        value = state.heightText,
                        label = stringResource(R.string.requirement_height_px),
                        onValueChange = viewModel::setHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.requirement_pixels_help),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RequirementField(
                        value = state.maximumSizeText,
                        label = stringResource(R.string.signature_max_kb),
                        onValueChange = viewModel::setMaximumSize,
                        modifier = Modifier.weight(1f),
                    )
                    RequirementField(
                        value = state.dpiText,
                        label = stringResource(R.string.requirement_dpi),
                        onValueChange = viewModel::setDpi,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.requirement_dpi_help),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.outputFormat == OutputFormat.PNG,
                        onClick = { viewModel.setOutputFormat(OutputFormat.PNG) },
                        label = { Text("PNG") },
                    )
                    FilterChip(
                        selected = state.outputFormat == OutputFormat.JPEG,
                        onClick = { viewModel.setOutputFormat(OutputFormat.JPEG) },
                        label = { Text("JPEG") },
                    )
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.signature_cleanup)) {
                ToggleRow(
                    stringResource(R.string.signature_grayscale),
                    state.grayscale,
                    viewModel::setGrayscale,
                )
                ToggleRow(
                    stringResource(R.string.signature_clean_paper),
                    state.cleanPaper,
                    viewModel::setCleanPaper,
                )
                ToggleRow(
                    stringResource(R.string.signature_remove_speckles),
                    state.removeSpeckles,
                    viewModel::setRemoveSpeckles,
                )
                ToggleRow(
                    stringResource(R.string.signature_auto_crop),
                    state.autoCrop,
                    viewModel::setAutoCrop,
                )
                ToggleRow(
                    stringResource(R.string.signature_transparent),
                    state.transparentBackground,
                    viewModel::setTransparentBackground,
                    enabled = state.outputFormat == OutputFormat.PNG,
                )
                LabelledSlider(
                    stringResource(R.string.signature_threshold, state.threshold),
                    state.threshold.toFloat(),
                    80f..245f,
                ) { viewModel.setThreshold(it.roundToInt()) }
                LabelledSlider(
                    stringResource(R.string.signature_contrast, state.contrastPercent),
                    state.contrastPercent.toFloat(),
                    50f..250f,
                ) { viewModel.setContrast(it.roundToInt()) }
                LabelledSlider(
                    stringResource(R.string.signature_safe_margin, state.safeMarginPercent),
                    state.safeMarginPercent.toFloat(),
                    0f..25f,
                ) { viewModel.setSafeMargin(it.roundToInt()) }
                LabelledSlider(
                    stringResource(R.string.signature_padding, state.paddingPercent),
                    state.paddingPercent.toFloat(),
                    0f..30f,
                ) { viewModel.setPadding(it.roundToInt()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.inkArgb == SignatureViewModel.INK_BLACK,
                        onClick = { viewModel.setInkColour(SignatureViewModel.INK_BLACK) },
                        label = { Text(stringResource(R.string.signature_black_ink)) },
                    )
                    FilterChip(
                        selected = state.inkArgb == SignatureViewModel.INK_BLUE,
                        onClick = { viewModel.setInkColour(SignatureViewModel.INK_BLUE) },
                        label = { Text(stringResource(R.string.signature_blue_ink)) },
                    )
                }
                Text(stringResource(R.string.signature_manual_crop))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CropField(
                        state.cropLeftPercent,
                        stringResource(R.string.signature_crop_left),
                        Modifier.weight(1f),
                    ) {
                        viewModel.setCrop(
                            it,
                            state.cropTopPercent,
                            state.cropRightPercent,
                            state.cropBottomPercent,
                        )
                    }
                    CropField(
                        state.cropTopPercent,
                        stringResource(R.string.signature_crop_top),
                        Modifier.weight(1f),
                    ) {
                        viewModel.setCrop(
                            state.cropLeftPercent,
                            it,
                            state.cropRightPercent,
                            state.cropBottomPercent,
                        )
                    }
                    CropField(
                        state.cropRightPercent,
                        stringResource(R.string.signature_crop_right),
                        Modifier.weight(1f),
                    ) {
                        viewModel.setCrop(
                            state.cropLeftPercent,
                            state.cropTopPercent,
                            it,
                            state.cropBottomPercent,
                        )
                    }
                    CropField(
                        state.cropBottomPercent,
                        stringResource(R.string.signature_crop_bottom),
                        Modifier.weight(1f),
                    ) {
                        viewModel.setCrop(
                            state.cropLeftPercent,
                            state.cropTopPercent,
                            state.cropRightPercent,
                            it,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { viewModel.nudge(-0.1f, 0f) }) {
                        Text(stringResource(R.string.signature_move_left))
                    }
                    OutlinedButton(onClick = { viewModel.nudge(0.1f, 0f) }) {
                        Text(stringResource(R.string.signature_move_right))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { viewModel.nudge(0f, -0.1f) }) {
                        Text(stringResource(R.string.signature_move_up))
                    }
                    OutlinedButton(onClick = { viewModel.nudge(0f, 0.1f) }) {
                        Text(stringResource(R.string.signature_move_down))
                    }
                }
                OutlinedButton(onClick = viewModel::rotateClockwise) {
                    Text(stringResource(R.string.signature_rotate))
                }
                LabelledSlider(
                    stringResource(R.string.signature_deskew, state.deskewDegrees),
                    state.deskewDegrees,
                    -10f..10f,
                    viewModel::setDeskew,
                )
                OutlinedButton(onClick = viewModel::resetCleanup) {
                    Text(stringResource(R.string.signature_restore))
                }
            }
        }
        item {
            state.errorCode?.let { error ->
                Text(
                    stringResource(R.string.signature_error, userFacingError(error)),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when (state.jobStatus) {
                JobStatus.QUEUED, JobStatus.RUNNING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = viewModel::cancelExport) {
                        Text(stringResource(R.string.action_cancel_processing))
                    }
                }
                JobStatus.FAILED -> {
                    Button(
                        onClick = viewModel::retryExport,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_retry)) }
                }
                else -> if (state.result == null) {
                    Button(
                        onClick = viewModel::startExport,
                        enabled = state.metadata != null && !state.isLoadingInput,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.signature_prepare)) }
                }
            }
        }
        state.result?.let { result ->
            item {
                SectionCard(stringResource(R.string.signature_result)) {
                    Text(
                        stringResource(
                            R.string.signature_result_details,
                            result.artifact.widthPx ?: 0,
                            result.artifact.heightPx ?: 0,
                            readableFileSize(result.artifact.byteCount),
                        ),
                    )
                    result.validationResults.forEach { validation ->
                        val status = when (validation.outcome) {
                            com.rameshta.formready.core.model.ValidationOutcome.PASS ->
                                stringResource(R.string.validation_pass)
                            com.rameshta.formready.core.model.ValidationOutcome.WARNING ->
                                stringResource(R.string.validation_warning)
                            com.rameshta.formready.core.model.ValidationOutcome.FAIL ->
                                stringResource(R.string.validation_fail)
                        }
                        Text(
                            stringResource(
                                R.string.validation_required,
                                status,
                                readableValidationValue(validation.expected),
                            ),
                        )
                        Text(
                            stringResource(
                                R.string.validation_current,
                                readableValidationValue(validation.actual),
                            ),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSave, enabled = !state.isSaving) {
                            Text(stringResource(R.string.action_save_copy))
                        }
                        OutlinedButton(onClick = onOpen) {
                            Text(stringResource(R.string.action_open))
                        }
                        OutlinedButton(onClick = onShare) {
                            Text(stringResource(R.string.action_share))
                        }
                    }
                    OutlinedButton(onClick = viewModel::prepareAnother) {
                        Text(stringResource(R.string.action_prepare_another))
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatureDrawingPad(onUse: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var active by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var drawingSize by remember { mutableStateOf(IntSize.Zero) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .background(ComposeColor.White)
                .onSizeChanged { drawingSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { active = listOf(it) },
                        onDragEnd = {
                            if (active.size > 1) strokes.add(active)
                            active = emptyList()
                        },
                    ) { change, _ ->
                        active = active + change.position
                        change.consume()
                    }
                },
        ) {
            (strokes + listOf(active)).forEach { points ->
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, ComposeColor.Black, style = Stroke(width = 5.dp.toPx()))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { strokes.clear(); active = emptyList() }) {
                Text(stringResource(R.string.signature_clear_drawing))
            }
            Button(
                onClick = {
                    val bitmap = Bitmap.createBitmap(900, 300, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        strokeWidth = 6f
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    strokes.forEach { points ->
                        if (points.size > 1) {
                            val path = android.graphics.Path()
                            val scaleX = 900f / drawingSize.width.coerceAtLeast(1)
                            val scaleY = 300f / drawingSize.height.coerceAtLeast(1)
                            path.moveTo(
                                points.first().x * scaleX,
                                points.first().y * scaleY,
                            )
                            points.drop(1).forEach {
                                path.lineTo(it.x * scaleX, it.y * scaleY)
                            }
                            canvas.drawPath(path, paint)
                        }
                    }
                    onUse(bitmap)
                },
                enabled = strokes.isNotEmpty(),
            ) { Text(stringResource(R.string.signature_use_drawing)) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun RequirementField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun CropField(
    value: Int,
    label: String,
    modifier: Modifier,
    onValueChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.filter(Char::isDigit).toIntOrNull()?.let(onValueChange) },
        label = { Text("$label %") },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Text(label)
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
}
