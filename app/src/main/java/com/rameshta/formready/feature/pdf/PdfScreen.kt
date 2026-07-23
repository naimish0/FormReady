package com.rameshta.formready.feature.pdf

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.formready.R
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.ValidationOutcome
import com.rameshta.formready.ui.component.BeginnerGuidanceCard
import com.rameshta.formready.ui.component.OptionalSection
import com.rameshta.formready.ui.format.readableFileSize
import com.rameshta.formready.ui.format.readableValidationValue
import com.rameshta.formready.ui.format.userFacingError

@Composable
fun PdfRoute(
    onBack: () -> Unit,
    viewModel: PdfViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.pdf_share)
    var confirmDiscard by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::selectPdf)
    }
    val images = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.imagesToPdf(uris) }
    val pageOperations = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.selectOperationPdfs(uris) }
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let(viewModel::saveTo) }

    fun back() {
        if (state.hasDraft && state.jobStatus != JobStatus.SUCCEEDED) {
            confirmDiscard = true
        } else if (state.isOperationMode) {
            viewModel.discard(onBack)
        } else {
            onBack()
        }
    }
    BackHandler(onBack = ::back)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = ::back) { Text(stringResource(R.string.action_back)) }
                Text(
                    stringResource(R.string.pdf_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        item { Text(stringResource(R.string.pdf_privacy)) }
        item {
            BeginnerGuidanceCard(
                title = stringResource(R.string.pdf_beginner_title),
                body = stringResource(R.string.pdf_beginner_help),
            )
        }
        item {
            Section(stringResource(R.string.pdf_input)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                        Text(stringResource(R.string.pdf_choose))
                    }
                    OutlinedButton(onClick = { images.launch(arrayOf("image/*")) }) {
                        Text(stringResource(R.string.pdf_images_to_pdf))
                    }
                    OutlinedButton(
                        onClick = {
                            pageOperations.launch(arrayOf("application/pdf"))
                        },
                    ) {
                        Text(stringResource(R.string.pdf_page_operations_choose))
                    }
                }
            }
        }
        if (state.isBusy) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.metadata?.let { metadata ->
            item {
                Section(stringResource(R.string.pdf_inspection)) {
                    Text(
                        stringResource(
                            R.string.pdf_summary,
                            readableFileSize(metadata.byteCount),
                            metadata.pageCount,
                        ),
                    )
                    OptionalSection(
                        title = stringResource(R.string.pdf_details_optional_title),
                        summary = stringResource(R.string.pdf_details_optional_help),
                    ) {
                        metadata.pages.forEachIndexed { index, page ->
                            Text(
                                stringResource(
                                    R.string.pdf_page_summary,
                                    index + 1,
                                    page.widthPoints,
                                    page.heightPoints,
                                    if (page.isLandscape) {
                                        stringResource(R.string.pdf_landscape)
                                    } else {
                                        stringResource(R.string.pdf_portrait)
                                    },
                                ),
                            )
                        }
                        Text(
                            stringResource(
                                R.string.pdf_encryption_status,
                                if (metadata.encrypted) {
                                    stringResource(R.string.pdf_encrypted)
                                } else {
                                    stringResource(R.string.pdf_not_encrypted)
                                },
                            ),
                        )
                        Text(featureText(R.string.pdf_forms, metadata.hasForms))
                        Text(featureText(R.string.pdf_links, metadata.hasLinks))
                        Text(featureText(R.string.pdf_annotations, metadata.hasAnnotations))
                        Text(featureText(R.string.pdf_signatures, metadata.hasDigitalSignatures))
                    }
                    if (!state.isOperationMode) {
                        OutlinedTextField(
                            value = state.maximumPagesText,
                            onValueChange = viewModel::setMaximumPages,
                            label = { Text(stringResource(R.string.pdf_maximum_pages)) },
                            singleLine = true,
                        )
                        val maximumBytes = state.maximumSizeText.toLongOrNull()?.times(1_000L)
                        val maximumPages = state.maximumPagesText.toIntOrNull()
                        val ready = maximumBytes != null && maximumPages != null &&
                            metadata.byteCount <= maximumBytes &&
                            metadata.pageCount <= maximumPages
                        Text(
                            if (ready) {
                                stringResource(R.string.pdf_input_ready)
                            } else {
                                stringResource(R.string.pdf_input_not_ready)
                            },
                            color = if (ready) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
            if (state.isOperationMode) {
                item {
                    Section(stringResource(R.string.pdf_page_operations)) {
                        Text(stringResource(R.string.pdf_page_operations_notice))
                        Text(
                            stringResource(
                                R.string.pdf_operation_source_summary,
                                state.operationSources.size,
                                state.operationPages.size,
                            ),
                        )
                        state.operationPages.forEachIndexed { index, page ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(
                                        R.string.pdf_operation_page,
                                        index + 1,
                                        page.sourceIndex + 1,
                                        page.pageIndex + 1,
                                        page.rotationQuarterTurns * 90,
                                    ),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = {
                                            viewModel.moveOperationPage(index, -1)
                                        },
                                        enabled = index > 0 && !state.isBusy,
                                    ) { Text(stringResource(R.string.action_move_up)) }
                                    TextButton(
                                        onClick = {
                                            viewModel.moveOperationPage(index, 1)
                                        },
                                        enabled = index + 1 < state.operationPages.size &&
                                            !state.isBusy,
                                    ) { Text(stringResource(R.string.action_move_down)) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { viewModel.rotateOperationPage(index) },
                                        enabled = !state.isBusy,
                                    ) { Text(stringResource(R.string.action_rotate)) }
                                    TextButton(
                                        onClick = { viewModel.deleteOperationPage(index) },
                                        enabled = state.operationPages.size > 1 && !state.isBusy,
                                    ) { Text(stringResource(R.string.action_delete)) }
                                    TextButton(
                                        onClick = { viewModel.showOperationPage(index) },
                                        enabled = !state.isBusy,
                                    ) { Text(stringResource(R.string.pdf_preview_action)) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::resetOperationPages,
                                enabled = !state.isBusy,
                            ) { Text(stringResource(R.string.pdf_reset_pages)) }
                            Button(
                                onClick = viewModel::startPageOperation,
                                enabled = state.operationPages.isNotEmpty() && !state.isBusy,
                            ) { Text(stringResource(R.string.pdf_create_edited_copy)) }
                        }
                    }
                }
            }
            state.preview?.let { preview ->
                item {
                    Section(stringResource(R.string.pdf_preview)) {
                        Image(
                            bitmap = preview,
                            contentDescription = stringResource(
                                R.string.pdf_preview_page,
                                state.previewPage + 1,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    rotationZ = if (state.isOperationMode) {
                                        (
                                            state.operationPages
                                                .getOrNull(state.previewPage)
                                                ?.rotationQuarterTurns ?: 0
                                            ) * 90f
                                    } else {
                                        0f
                                    }
                                },
                            contentScale = ContentScale.Fit,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (state.isOperationMode) {
                                        viewModel.showOperationPage(state.previewPage - 1)
                                    } else {
                                        viewModel.showPage(state.previewPage - 1)
                                    }
                                },
                                enabled = state.previewPage > 0 && !state.isBusy,
                            ) { Text(stringResource(R.string.pdf_previous_page)) }
                            OutlinedButton(
                                onClick = {
                                    if (state.isOperationMode) {
                                        viewModel.showOperationPage(state.previewPage + 1)
                                    } else {
                                        viewModel.showPage(state.previewPage + 1)
                                    }
                                },
                                enabled = state.previewPage + 1 <
                                    (if (state.isOperationMode) {
                                        state.operationPages.size
                                    } else {
                                        metadata.pageCount
                                    }) &&
                                    !state.isBusy,
                            ) { Text(stringResource(R.string.pdf_next_page)) }
                        }
                    }
                }
            }
            if (!state.isOperationMode) item {
                Section(stringResource(R.string.pdf_compression)) {
                    Text(stringResource(R.string.pdf_safe_unavailable))
                    Text(stringResource(R.string.pdf_flatten_warning))
                    OutlinedTextField(
                        value = state.maximumSizeText,
                        onValueChange = viewModel::setMaximumSize,
                        label = { Text(stringResource(R.string.pdf_maximum_kb)) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.pdf_acknowledge),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.flatteningAcknowledged,
                            onCheckedChange = viewModel::setAcknowledged,
                        )
                    }
                    when (state.jobStatus) {
                        JobStatus.QUEUED, JobStatus.RUNNING -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            OutlinedButton(onClick = viewModel::cancel) {
                                Text(stringResource(R.string.action_cancel_processing))
                            }
                        }
                        else -> if (state.result == null) {
                            Button(
                                onClick = viewModel::startStrongCompression,
                                enabled = state.flatteningAcknowledged,
                            ) { Text(stringResource(R.string.pdf_strong_compress)) }
                        }
                    }
                }
            }
        }
        state.errorCode?.let { error ->
            item {
                Text(
                    stringResource(R.string.pdf_error, userFacingError(error)),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        state.result?.let { result ->
            item {
                Section(stringResource(R.string.pdf_result)) {
                    Text(
                        stringResource(
                            R.string.pdf_result_summary,
                            readableFileSize(result.artifact.byteCount),
                        ),
                    )
                    result.validations.forEach { validation ->
                        val status = when (validation.outcome) {
                            ValidationOutcome.PASS -> stringResource(R.string.validation_pass)
                            ValidationOutcome.WARNING -> stringResource(R.string.validation_warning)
                            ValidationOutcome.FAIL -> stringResource(R.string.validation_fail)
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
                        Button(
                            onClick = { save.launch(result.artifact.displayName) },
                            enabled = !state.isSaving,
                        ) { Text(stringResource(R.string.action_save_copy)) }
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(
                                                result.shareUri,
                                                result.artifact.mimeType,
                                            )
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    viewModel.reportNoApp()
                                }
                            },
                        ) { Text(stringResource(R.string.action_open)) }
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND)
                                                .setType("application/pdf")
                                                .putExtra(Intent.EXTRA_STREAM, result.shareUri)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                            shareTitle,
                                        ),
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    viewModel.reportNoApp()
                                }
                            },
                        ) { Text(stringResource(R.string.action_share)) }
                    }
                    OutlinedButton(onClick = viewModel::prepareAnother) {
                        Text(stringResource(R.string.action_prepare_another))
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.pdf_discard_title)) },
            text = { Text(stringResource(R.string.pdf_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discard(onBack)
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
private fun featureText(label: Int, detected: Boolean?): String = stringResource(
    R.string.pdf_feature_status,
    stringResource(label),
    when (detected) {
        true -> stringResource(R.string.pdf_detected)
        false -> stringResource(R.string.pdf_not_detected)
        null -> stringResource(R.string.pdf_unknown)
    },
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
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
