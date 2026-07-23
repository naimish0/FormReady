package com.rameshta.formready.feature.presets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.formready.R
import com.rameshta.formready.core.data.repository.PresetRecord
import com.rameshta.formready.core.data.repository.PresetSpecification
import com.rameshta.formready.core.data.repository.PresetTargetType
import org.json.JSONObject

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PresetsScreen(viewModel: PresetsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingPreset by remember { mutableStateOf<PresetRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::importFrom)
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PRESET_MIME_TYPE),
    ) { it?.let(viewModel::exportTo) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.presets_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            Text(stringResource(R.string.presets_confirm_rules))
            Text(
                stringResource(R.string.presets_file_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.presets_search)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    editingPreset = null
                    showEditor = true
                }) {
                    Text(stringResource(R.string.presets_create))
                }
                OutlinedButton(
                    onClick = {
                        importer.launch(
                            arrayOf(
                                PRESET_MIME_TYPE,
                                "application/octet-stream",
                                "application/json",
                                "text/plain",
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.presets_import))
                }
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    presetErrorMessage(error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                    },
                )
            }
        }
        items(state.presets, key = PresetRecord::id) { preset ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.presets_type_revision,
                            presetTypeLabel(preset.targetType),
                            preset.revision,
                        ),
                    )
                    PresetSummary(
                        type = preset.targetType,
                        specification = viewModel.specification(preset),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(onClick = { viewModel.duplicate(preset) }) {
                            Text(stringResource(R.string.presets_duplicate))
                        }
                        TextButton(
                            onClick = {
                                viewModel.requestExport(preset)
                                exporter.launch(presetFileName(preset.name))
                            },
                        ) { Text(stringResource(R.string.presets_export)) }
                    }
                    if (preset.isCustom) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TextButton(onClick = {
                                editingPreset = preset
                                showEditor = true
                            }) {
                                Text(stringResource(R.string.presets_edit))
                            }
                            TextButton(onClick = { viewModel.toggleFavourite(preset) }) {
                                Text(
                                    stringResource(
                                        if (preset.isFavourite) {
                                            R.string.presets_unfavourite
                                        } else {
                                            R.string.presets_favourite
                                        },
                                    ),
                                )
                            }
                            TextButton(onClick = { viewModel.delete(preset) }) {
                                Text(stringResource(R.string.presets_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        CreatePresetDialog(
            existing = editingPreset,
            onDismiss = { showEditor = false },
            onCreate = { name, type, bytes, width, height, pages ->
                viewModel.save(editingPreset, name, type, bytes, width, height, pages)
                showEditor = false
            },
        )
    }
    state.importCandidate?.let { candidate ->
        ImportPresetPreviewDialog(
            preset = candidate,
            specification = viewModel.specification(candidate),
            onDismiss = viewModel::dismissImport,
            onConfirm = viewModel::confirmImport,
        )
    }
}

@Composable
private fun ImportPresetPreviewDialog(
    preset: PresetRecord,
    specification: PresetSpecification,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.presets_import_review_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.presets_import_review_help),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                Text(presetTypeLabel(preset.targetType))
                PresetSummary(preset.targetType, specification)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.presets_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PresetSummary(
    type: PresetTargetType,
    specification: PresetSpecification,
) {
    val maximumSize = formattedFileSize(specification.maximumBytes)
    Text(
        when (type) {
            PresetTargetType.PHOTO, PresetTargetType.SIGNATURE -> stringResource(
                R.string.presets_summary_image,
                specification.widthPx ?: 0,
                specification.heightPx ?: 0,
                maximumSize,
            )
            PresetTargetType.PDF -> stringResource(
                R.string.presets_summary_pdf,
                specification.maximumPages ?: 0,
                maximumSize,
            )
        },
    )
}

@Composable
private fun formattedFileSize(bytes: Long): String =
    if (bytes >= 1_000_000L && bytes % 1_000_000L == 0L) {
        stringResource(R.string.presets_size_mb, bytes / 1_000_000L)
    } else {
        stringResource(R.string.presets_size_kb, (bytes + 999L) / 1_000L)
    }

@Composable
private fun presetErrorMessage(error: PresetUiError): String = stringResource(
    when (error) {
        PresetUiError.FILE_TOO_LARGE -> R.string.presets_error_file_too_large
        PresetUiError.DAMAGED_FILE -> R.string.presets_error_damaged_file
        PresetUiError.UNSUPPORTED_VERSION -> R.string.presets_error_unsupported_version
        PresetUiError.MISSING_NAME -> R.string.presets_error_missing_name
        PresetUiError.NAME_TOO_LONG -> R.string.presets_error_name_too_long
        PresetUiError.UNSUPPORTED_TYPE -> R.string.presets_error_unsupported_type
        PresetUiError.INVALID_MAXIMUM_SIZE -> R.string.presets_error_invalid_size
        PresetUiError.INVALID_DIMENSIONS -> R.string.presets_error_invalid_dimensions
        PresetUiError.INVALID_PAGE_LIMIT -> R.string.presets_error_invalid_pages
        PresetUiError.SOURCE_UNAVAILABLE -> R.string.presets_error_source_unavailable
        PresetUiError.DESTINATION_UNAVAILABLE -> R.string.presets_error_destination_unavailable
        PresetUiError.SAVE_FAILED -> R.string.presets_error_save_failed
        PresetUiError.EXPORT_FAILED -> R.string.presets_error_export_failed
        PresetUiError.DUPLICATE_BUILT_IN_FIRST -> R.string.presets_duplicate_first
    },
)

private fun presetFileName(name: String): String {
    val safeName = name
        .map { character ->
            if (character.isLetterOrDigit() || character in " ._-") character else '_'
        }
        .joinToString("")
        .trim()
        .take(40)
        .ifBlank { "FormReady-preset" }
    return "$safeName.formready"
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CreatePresetDialog(
    existing: PresetRecord?,
    onDismiss: () -> Unit,
    onCreate: (String, PresetTargetType, Long, Int?, Int?, Int?) -> Unit,
) {
    val existingSpec = remember(existing) {
        runCatching { existing?.let { JSONObject(it.specificationJson) } }.getOrNull()
    }
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(existing) {
        mutableStateOf(existing?.targetType ?: PresetTargetType.PHOTO)
    }
    val initialMaximumBytes = existingSpec
        ?.optLong("maximumBytes")
        ?.takeIf { it > 0L }
        ?: 200_000L
    var sizeUnit by remember(existing) {
        mutableStateOf(preferredSizeUnit(initialMaximumBytes))
    }
    var maximumSize by remember(existing) {
        val initialUnit = preferredSizeUnit(initialMaximumBytes)
        mutableStateOf((initialMaximumBytes / initialUnit.bytesPerUnit).toString())
    }
    var width by remember(existing) {
        mutableStateOf(existingSpec?.optInt("widthPx")?.takeIf { it > 0 }?.toString() ?: "600")
    }
    var height by remember(existing) {
        mutableStateOf(existingSpec?.optInt("heightPx")?.takeIf { it > 0 }?.toString() ?: "800")
    }
    var pages by remember(existing) {
        mutableStateOf(existingSpec?.optInt("maximumPages")?.takeIf { it > 0 }?.toString() ?: "100")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.presets_create else R.string.presets_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = {
                    Text(stringResource(R.string.presets_name))
                })
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PresetTargetType.entries.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(presetTypeLabel(candidate)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = maximumSize,
                    onValueChange = { maximumSize = it.filter(Char::isDigit).take(9) },
                    label = { Text(stringResource(R.string.presets_maximum_size)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.presets_size_unit_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PresetSizeUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = sizeUnit == unit,
                            onClick = { sizeUnit = unit },
                            label = {
                                Text(
                                    stringResource(
                                        if (unit == PresetSizeUnit.KB) {
                                            R.string.presets_unit_kb
                                        } else {
                                            R.string.presets_unit_mb
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                if (type != PresetTargetType.PDF) {
                    OutlinedTextField(width, { width = it.filter(Char::isDigit) }, label = {
                        Text(stringResource(R.string.presets_width))
                    })
                    OutlinedTextField(height, { height = it.filter(Char::isDigit) }, label = {
                        Text(stringResource(R.string.presets_height))
                    })
                } else {
                    OutlinedTextField(pages, { pages = it.filter(Char::isDigit) }, label = {
                        Text(stringResource(R.string.presets_maximum_pages))
                    })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        name,
                        type,
                        maximumSize.toPresetBytes(sizeUnit),
                        width.toIntOrNull().takeIf { type != PresetTargetType.PDF },
                        height.toIntOrNull().takeIf { type != PresetTargetType.PDF },
                        pages.toIntOrNull().takeIf { type == PresetTargetType.PDF },
                    )
                },
            ) { Text(stringResource(R.string.presets_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private enum class PresetSizeUnit(val bytesPerUnit: Long) {
    KB(1_000L),
    MB(1_000_000L),
}

private fun preferredSizeUnit(bytes: Long): PresetSizeUnit =
    if (bytes >= PresetSizeUnit.MB.bytesPerUnit &&
        bytes % PresetSizeUnit.MB.bytesPerUnit == 0L
    ) {
        PresetSizeUnit.MB
    } else {
        PresetSizeUnit.KB
    }

private fun String.toPresetBytes(unit: PresetSizeUnit): Long {
    val value = toLongOrNull() ?: return 0L
    return runCatching { Math.multiplyExact(value, unit.bytesPerUnit) }.getOrDefault(0L)
}

@Composable
private fun presetTypeLabel(type: PresetTargetType): String = stringResource(
    when (type) {
        PresetTargetType.PHOTO -> R.string.preset_type_photo
        PresetTargetType.SIGNATURE -> R.string.preset_type_signature
        PresetTargetType.PDF -> R.string.preset_type_pdf
    },
)

private const val PRESET_MIME_TYPE = "application/vnd.formready.preset+json"
