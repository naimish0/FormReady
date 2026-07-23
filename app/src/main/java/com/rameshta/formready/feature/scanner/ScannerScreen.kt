package com.rameshta.formready.feature.scanner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.rameshta.formready.R
import com.rameshta.formready.core.processing.OcrScript
import com.rameshta.formready.ui.component.BeginnerGuidanceCard
import com.rameshta.formready.ui.component.OptionalSection
import com.rameshta.formready.ui.format.userFacingError

@Composable
fun ScannerRoute(
    onBack: () -> Unit,
    viewModel: ScannerViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val scanResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            viewModel.addPages(scanningResult?.pages.orEmpty().map { it.imageUri })
        }
    }
    val importPages = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { viewModel.addPages(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        viewModel.completeCapture(it)
    }
    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { it?.let(viewModel::exportPdf) }
    val saveText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { it?.let(viewModel::exportText) }

    BackHandler(onBack = onBack)
    ScannerScreen(
        state = state,
        onBack = onBack,
        onScan = {
            if (activity == null) {
                viewModel.reportScannerUnavailable()
            } else {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(ScannerViewModel.MAX_PAGES)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                    )
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender ->
                        scanResult.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    .addOnFailureListener { viewModel.reportScannerUnavailable() }
            }
        },
        onImport = { importPages.launch(arrayOf("image/*")) },
        onCamera = {
            try {
                camera.launch(viewModel.createCaptureUri())
            } catch (_: ActivityNotFoundException) {
                viewModel.reportScannerUnavailable()
            }
        },
        onMove = viewModel::movePage,
        onRotate = viewModel::rotatePage,
        onDelete = viewModel::deletePage,
        onEdit = viewModel::editPage,
        onApplyEdit = viewModel::applyManualEdit,
        onCancelEdit = viewModel::cancelEdit,
        onLatinOcr = { viewModel.recognize(OcrScript.LATIN) },
        onDevanagariOcr = { viewModel.recognize(OcrScript.DEVANAGARI) },
        onSavePdf = { savePdf.launch("FormReady-scan.pdf") },
        onSaveText = { saveText.launch("FormReady-scan.txt") },
    )
}

@Composable
private fun ScannerScreen(
    state: ScannerUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onCamera: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRotate: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onApplyEdit: (List<Float>, ScanFilter) -> Unit,
    onCancelEdit: () -> Unit,
    onLatinOcr: () -> Unit,
    onDevanagariOcr: () -> Unit,
    onSavePdf: () -> Unit,
    onSaveText: () -> Unit,
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
                    stringResource(R.string.scanner_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
        }
        item {
            Text(
                stringResource(R.string.scanner_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            BeginnerGuidanceCard(
                title = stringResource(R.string.scanner_beginner_title),
                body = stringResource(R.string.scanner_beginner_help),
            )
        }
        item {
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = onScan, enabled = !state.isBusy) {
                        Text(stringResource(R.string.scanner_start))
                    }
                    Text(
                        stringResource(R.string.scanner_fallback),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImport, enabled = !state.isBusy) {
                            Text(stringResource(R.string.scanner_import))
                        }
                        OutlinedButton(onClick = onCamera, enabled = !state.isBusy) {
                            Text(stringResource(R.string.scanner_camera))
                        }
                    }
                }
            }
        }
        if (state.isBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.errorCode?.let { error ->
            item { Text(userFacingError(error), color = MaterialTheme.colorScheme.error) }
        }
        if (state.pages.isNotEmpty()) {
            item {
                Text(
                    pluralStringResource(
                        R.plurals.scanner_pages,
                        state.pages.size,
                        state.pages.size,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            itemsIndexed(state.pages, key = { _, page -> page.id }) { index, page ->
                Card {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.scanner_page_number, page.number))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { onMove(index, -1) },
                                enabled = index > 0 && !state.isBusy,
                            ) { Text(stringResource(R.string.action_move_up)) }
                            TextButton(
                                onClick = { onMove(index, 1) },
                                enabled = index < state.pages.lastIndex && !state.isBusy,
                            ) { Text(stringResource(R.string.action_move_down)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { onRotate(index) },
                                enabled = !state.isBusy,
                            ) { Text(stringResource(R.string.action_rotate)) }
                            TextButton(
                                onClick = { onDelete(index) },
                                enabled = !state.isBusy,
                            ) { Text(stringResource(R.string.action_delete)) }
                            TextButton(
                                onClick = { onEdit(index) },
                                enabled = !state.isBusy,
                            ) { Text(stringResource(R.string.scanner_manual_edit)) }
                        }
                    }
                }
            }
            state.editingPreview?.let { preview ->
                item {
                    ManualPageEditor(
                        preview = preview,
                        onApply = onApplyEdit,
                        onCancel = onCancelEdit,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSavePdf,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.scanner_save_pdf))
                    }
                    OptionalSection(
                        title = stringResource(R.string.scanner_text_optional_title),
                        summary = stringResource(R.string.scanner_text_optional_help),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onLatinOcr, enabled = !state.isBusy) {
                                Text(stringResource(R.string.scanner_ocr_latin))
                            }
                            OutlinedButton(onClick = onDevanagariOcr, enabled = !state.isBusy) {
                                Text(stringResource(R.string.scanner_ocr_devanagari))
                            }
                        }
                    }
                }
            }
        }
        if (state.extractedText.isNotBlank()) {
            item {
                Text(
                    stringResource(R.string.scanner_extracted_text),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                SelectionContainer {
                    Text(state.extractedText)
                }
            }
            item {
                OutlinedButton(onClick = onSaveText) {
                    Text(stringResource(R.string.scanner_save_text))
                }
            }
        }
    }
}

@Composable
private fun ManualPageEditor(
    preview: androidx.compose.ui.graphics.ImageBitmap,
    onApply: (List<Float>, ScanFilter) -> Unit,
    onCancel: () -> Unit,
) {
    val corners = remember(preview) {
        mutableStateListOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
    }
    var filter by remember(preview) { mutableStateOf(ScanFilter.COLOUR) }
    val cornerNames = listOf(
        stringResource(R.string.scanner_corner_top_left),
        stringResource(R.string.scanner_corner_top_right),
        stringResource(R.string.scanner_corner_bottom_right),
        stringResource(R.string.scanner_corner_bottom_left),
    )
    Card {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.scanner_manual_crop),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.scanner_manual_crop_help),
                style = MaterialTheme.typography.bodySmall,
            )
            Image(
                bitmap = preview,
                contentDescription = stringResource(R.string.scanner_page_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            cornerNames.forEachIndexed { cornerIndex, name ->
                Text(name, style = MaterialTheme.typography.labelLarge)
                listOf(
                    stringResource(R.string.scanner_axis_x),
                    stringResource(R.string.scanner_axis_y),
                ).forEachIndexed { axis, label ->
                    Text(
                        stringResource(
                            R.string.scanner_corner_coordinate,
                            label,
                            (corners[cornerIndex * 2 + axis] * 100).toInt(),
                        ),
                    )
                    Slider(
                        value = corners[cornerIndex * 2 + axis],
                        onValueChange = { corners[cornerIndex * 2 + axis] = it },
                        valueRange = 0f..1f,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ScanFilter.entries.forEach { choice ->
                    FilterChip(
                        selected = filter == choice,
                        onClick = { filter = choice },
                        label = {
                            Text(
                                stringResource(
                                    when (choice) {
                                        ScanFilter.COLOUR -> R.string.scanner_filter_colour
                                        ScanFilter.GRAYSCALE -> R.string.scanner_filter_grayscale
                                        ScanFilter.BLACK_AND_WHITE -> R.string.scanner_filter_bw
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApply(corners.toList(), filter) }) {
                    Text(stringResource(R.string.action_apply))
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.scanner_cancel)) }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
