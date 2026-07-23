package com.rameshta.formready.feature.batch

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.formready.R
import com.rameshta.formready.core.model.CropMode
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.model.OutputFormat

@Composable
fun BatchRoute(
    onBack: () -> Unit,
    viewModel: BatchViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PRO_ITEMS),
    ) { uris -> viewModel.selectImages(uris) }
    val saveZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::saveZip) }

    fun back() {
        if (state.hasDraft) confirmDiscard = true else onBack()
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
                    stringResource(R.string.batch_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        item {
            Text(stringResource(R.string.batch_privacy_and_limit, state.itemLimit))
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.batch_requirements),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = state.widthText,
                            onValueChange = viewModel::setWidth,
                            label = stringResource(R.string.requirement_width_px),
                            modifier = Modifier.weight(1f),
                            enabled = !state.isRunning,
                        )
                        NumberField(
                            value = state.heightText,
                            onValueChange = viewModel::setHeight,
                            label = stringResource(R.string.requirement_height_px),
                            modifier = Modifier.weight(1f),
                            enabled = !state.isRunning,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = state.maximumKbText,
                            onValueChange = viewModel::setMaximumKb,
                            label = stringResource(R.string.requirement_maximum_kb),
                            modifier = Modifier.weight(1f),
                            enabled = !state.isRunning,
                        )
                        NumberField(
                            value = state.dpiText,
                            onValueChange = viewModel::setDpi,
                            label = stringResource(R.string.requirement_dpi),
                            modifier = Modifier.weight(1f),
                            enabled = !state.isRunning,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(OutputFormat.JPEG, OutputFormat.PNG).forEach { format ->
                            FilterChip(
                                selected = state.outputFormat == format,
                                onClick = { viewModel.setFormat(format) },
                                enabled = !state.isRunning,
                                label = { Text(format.name) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.cropMode == CropMode.CROP_FILL,
                            onClick = { viewModel.setCropMode(CropMode.CROP_FILL) },
                            enabled = !state.isRunning,
                            label = { Text(stringResource(R.string.photo_crop_fill)) },
                        )
                        FilterChip(
                            selected = state.cropMode == CropMode.FIT_PAD,
                            onClick = { viewModel.setCropMode(CropMode.FIT_PAD) },
                            enabled = !state.isRunning,
                            label = { Text(stringResource(R.string.photo_fit_pad)) },
                        )
                    }
                    Text(stringResource(R.string.batch_center_crop_notice))
                }
            }
        }
        item {
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !state.isLoading && !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.items.isEmpty()) {
                            R.string.batch_choose
                        } else {
                            R.string.batch_replace
                        },
                    ),
                )
            }
        }
        if (state.isLoading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        state.items.forEachIndexed { index, item ->
            item(key = item.id.toString()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.batch_item_number, index + 1),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        item.metadata?.let {
                            Text(
                                stringResource(
                                    R.string.batch_item_source,
                                    it.widthPx,
                                    it.heightPx,
                                    it.byteCount,
                                ),
                            )
                        }
                        Text(
                            stringResource(
                                R.string.batch_item_status,
                                batchStatusText(item.status),
                            ),
                        )
                        item.artifact?.let {
                            Text(stringResource(R.string.batch_item_output, it.byteCount))
                        }
                        item.errorCode?.let {
                            Text(
                                stringResource(R.string.pdf_error, it),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (item.status == null && !state.isRunning) {
                            TextButton(onClick = { viewModel.removeItem(index) }) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
        if (state.items.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.batch_progress,
                                state.finishedCount,
                                state.items.size,
                            ),
                        )
                        LinearProgressIndicator(
                            progress = {
                                state.finishedCount.toFloat() / state.items.size
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.isRunning) {
                            OutlinedButton(onClick = viewModel::cancel) {
                                Text(stringResource(R.string.batch_cancel_all))
                            }
                        } else if (state.items.all { it.status == null }) {
                            Button(
                                onClick = viewModel::start,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.batch_start))
                            }
                        }
                        if (state.items.any { it.status == JobStatus.FAILED }) {
                            OutlinedButton(onClick = viewModel::retryFailed) {
                                Text(stringResource(R.string.batch_retry_failed))
                            }
                        }
                        if (state.succeededCount > 0 && !state.isRunning) {
                            Button(
                                onClick = {
                                    saveZip.launch("FormReady-batch.zip")
                                },
                                enabled = !state.isSavingZip,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.batch_save_zip,
                                        state.succeededCount,
                                    ),
                                )
                            }
                        }
                        if (state.zipSaved) {
                            Text(stringResource(R.string.batch_zip_saved))
                        }
                        if (state.finishedCount == state.items.size) {
                            OutlinedButton(onClick = viewModel::prepareAnother) {
                                Text(stringResource(R.string.action_prepare_another))
                            }
                        }
                    }
                }
            }
        }
        state.errorCode?.let { error ->
            item {
                Text(
                    stringResource(R.string.pdf_error, error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.batch_discard_title)) },
            text = { Text(stringResource(R.string.batch_discard_body)) },
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
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun batchStatusText(status: JobStatus?): String = stringResource(
    when (status) {
        null -> R.string.batch_status_ready
        JobStatus.QUEUED -> R.string.job_status_queued
        JobStatus.RUNNING -> R.string.job_status_running
        JobStatus.SUCCEEDED -> R.string.job_status_succeeded
        JobStatus.FAILED -> R.string.job_status_failed
        JobStatus.CANCELLED -> R.string.job_status_cancelled
    },
)

private const val MAX_PRO_ITEMS = 50
