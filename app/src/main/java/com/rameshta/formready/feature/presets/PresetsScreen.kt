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
        ActivityResultContracts.CreateDocument("application/json"),
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
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json")) }) {
                    Text(stringResource(R.string.presets_import))
                }
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    stringResource(R.string.presets_error, error),
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
                    Text(preset.specificationJson)
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
                                exporter.launch("${preset.name.take(40)}.json")
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
    var maximumKb by remember(existing) {
        mutableStateOf(
            existingSpec?.optLong("maximumBytes")?.div(1_000L)?.toString() ?: "200",
        )
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
                OutlinedTextField(maximumKb, { maximumKb = it.filter(Char::isDigit) }, label = {
                    Text(stringResource(R.string.presets_maximum_kb))
                })
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
                        maximumKb.toLongOrNull()?.times(1_000L) ?: 0L,
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

@Composable
private fun presetTypeLabel(type: PresetTargetType): String = stringResource(
    when (type) {
        PresetTargetType.PHOTO -> R.string.preset_type_photo
        PresetTargetType.SIGNATURE -> R.string.preset_type_signature
        PresetTargetType.PDF -> R.string.preset_type_pdf
    },
)
