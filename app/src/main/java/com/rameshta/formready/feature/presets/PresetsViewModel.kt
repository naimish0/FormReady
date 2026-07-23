package com.rameshta.formready.feature.presets

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.formready.R
import com.rameshta.formready.core.data.repository.PresetRecord
import com.rameshta.formready.core.data.repository.PresetImportException
import com.rameshta.formready.core.data.repository.PresetImportIssue
import com.rameshta.formready.core.data.repository.PresetRepository
import com.rameshta.formready.core.data.repository.PresetSpecification
import com.rameshta.formready.core.data.repository.PresetTargetType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class PresetsUiState(
    val presets: List<PresetRecord> = emptyList(),
    val query: String = "",
    val error: PresetUiError? = null,
    val exportCandidate: PresetRecord? = null,
    val importCandidate: PresetRecord? = null,
)

enum class PresetUiError {
    FILE_TOO_LARGE,
    DAMAGED_FILE,
    UNSUPPORTED_VERSION,
    MISSING_NAME,
    NAME_TOO_LONG,
    UNSUPPORTED_TYPE,
    INVALID_MAXIMUM_SIZE,
    INVALID_DIMENSIONS,
    INVALID_PAGE_LIMIT,
    SOURCE_UNAVAILABLE,
    DESTINATION_UNAVAILABLE,
    SAVE_FAILED,
    EXPORT_FAILED,
    DUPLICATE_BUILT_IN_FIRST,
}

@HiltViewModel
class PresetsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PresetRepository,
) : ViewModel() {
    private val controls = MutableStateFlow(PresetsUiState())
    private val builtIns by lazy {
        listOf(
            PresetRecord(
                id = "generic-photo-600x800",
                name = context.getString(R.string.preset_builtin_photo_600x800),
                targetType = PresetTargetType.PHOTO,
                specificationJson = """{"maximumBytes":200000,"widthPx":600,"heightPx":800}""",
                isCustom = false,
            ),
            PresetRecord(
                id = "generic-signature-300x100",
                name = context.getString(R.string.preset_builtin_signature_300x100),
                targetType = PresetTargetType.SIGNATURE,
                specificationJson = """{"maximumBytes":50000,"widthPx":300,"heightPx":100}""",
                isCustom = false,
            ),
            PresetRecord(
                id = "generic-pdf-1000kb",
                name = context.getString(R.string.preset_builtin_pdf_1000kb),
                targetType = PresetTargetType.PDF,
                specificationJson = """{"maximumBytes":1000000,"maximumPages":100}""",
                isCustom = false,
            ),
            PresetRecord(
                id = "generic-photo-35x45mm",
                name = context.getString(R.string.preset_builtin_photo_35x45),
                targetType = PresetTargetType.PHOTO,
                specificationJson =
                    """{"maximumBytes":500000,"widthPx":413,"heightPx":531}""",
                isCustom = false,
            ),
            PresetRecord(
                id = "generic-photo-2x2in",
                name = context.getString(R.string.preset_builtin_photo_2x2),
                targetType = PresetTargetType.PHOTO,
                specificationJson =
                    """{"maximumBytes":500000,"widthPx":600,"heightPx":600}""",
                isCustom = false,
            ),
        )
    }
    val uiState: StateFlow<PresetsUiState> = combine(
        repository.observeAll(),
        controls,
    ) { custom, control ->
        val all = (builtIns + custom)
            .distinctBy(PresetRecord::id)
            .sortedWith(
                compareByDescending<PresetRecord> { it.isFavourite }
                    .thenBy { it.name.lowercase() },
            )
        control.copy(
            presets = all.filter {
                control.query.isBlank() ||
                    it.name.contains(control.query, ignoreCase = true) ||
                    it.targetType.name.contains(control.query, ignoreCase = true)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetsUiState(builtIns))

    fun setQuery(value: String) {
        controls.update { it.copy(query = value.take(80)) }
    }

    fun specification(preset: PresetRecord): PresetSpecification =
        repository.specification(preset)

    fun save(
        existing: PresetRecord?,
        name: String,
        targetType: PresetTargetType,
        maximumBytes: Long,
        widthPx: Int?,
        heightPx: Int?,
        maximumPages: Int?,
    ) {
        viewModelScope.launch {
            runCatching {
                val specification = JSONObject()
                    .put("maximumBytes", maximumBytes)
                    .apply {
                        widthPx?.let { put("widthPx", it) }
                        heightPx?.let { put("heightPx", it) }
                        maximumPages?.let { put("maximumPages", it) }
                    }
                    .toString()
                repository.save(
                    PresetRecord(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        revision = existing?.revision ?: 1,
                        name = name.trim(),
                        targetType = targetType,
                        specificationJson = specification,
                        isFavourite = existing?.isFavourite ?: false,
                    ),
                )
            }.onFailure {
                controls.update { state -> state.copy(error = PresetUiError.SAVE_FAILED) }
            }
        }
    }

    fun duplicate(preset: PresetRecord) {
        viewModelScope.launch {
            repository.save(
                preset.copy(
                    id = UUID.randomUUID().toString(),
                    name = context.getString(R.string.presets_copy_name, preset.name),
                    sourceUrl = null,
                    sourceCheckedAtEpochMillis = null,
                    isCustom = true,
                    isFavourite = false,
                ),
            )
        }
    }

    fun toggleFavourite(preset: PresetRecord) {
        if (!preset.isCustom) {
            controls.update { it.copy(error = PresetUiError.DUPLICATE_BUILT_IN_FIRST) }
            return
        }
        viewModelScope.launch {
            repository.setFavourite(preset.id, !preset.isFavourite)
        }
    }

    fun delete(preset: PresetRecord) {
        if (!preset.isCustom) return
        viewModelScope.launch { repository.deleteCustom(preset.id) }
    }

    fun importFrom(uri: Uri) {
        controls.update { it.copy(error = null, importCandidate = null) }
        viewModelScope.launch {
            runCatching {
                val contents = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { reader ->
                        val buffer = CharArray(4_096)
                        val content = StringBuilder()
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            if (content.length + count > MAX_IMPORT_CHARS) {
                                throw PresetImportException(PresetImportIssue.FILE_TOO_LARGE)
                            }
                            content.append(buffer, 0, count)
                        }
                        content.toString()
                    }
                    ?: throw PresetSourceUnavailableException
                repository.parseImport(contents)
            }.onSuccess { candidate ->
                controls.update { it.copy(importCandidate = candidate, error = null) }
            }.onFailure { error ->
                controls.update { it.copy(error = error.toUiError(), importCandidate = null) }
            }
        }
    }

    fun confirmImport() {
        val candidate = controls.value.importCandidate ?: return
        viewModelScope.launch {
            runCatching { repository.save(candidate) }
                .onSuccess {
                    controls.update {
                        it.copy(importCandidate = null, error = null)
                    }
                }
                .onFailure {
                    controls.update {
                        it.copy(
                            importCandidate = null,
                            error = PresetUiError.SAVE_FAILED,
                        )
                    }
                }
        }
    }

    fun dismissImport() {
        controls.update { it.copy(importCandidate = null) }
    }

    fun requestExport(preset: PresetRecord) {
        controls.update { it.copy(exportCandidate = preset, error = null) }
    }

    fun exportTo(uri: Uri) {
        val preset = controls.value.exportCandidate ?: return
        viewModelScope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")
                    ?.bufferedWriter()
                    ?.use { it.write(repository.export(preset)) }
                    ?: throw PresetDestinationUnavailableException
            }.onFailure { error ->
                controls.update {
                    it.copy(
                        error = if (error === PresetDestinationUnavailableException) {
                            PresetUiError.DESTINATION_UNAVAILABLE
                        } else {
                            PresetUiError.EXPORT_FAILED
                        },
                    )
                }
            }
            controls.update { it.copy(exportCandidate = null) }
        }
    }

    fun clearError() {
        controls.update { it.copy(error = null) }
    }

    private companion object {
        const val MAX_IMPORT_CHARS = 64 * 1024
    }

    private fun Throwable.toUiError(): PresetUiError = when (this) {
        PresetSourceUnavailableException -> PresetUiError.SOURCE_UNAVAILABLE
        is PresetImportException -> when (issue) {
            PresetImportIssue.FILE_TOO_LARGE -> PresetUiError.FILE_TOO_LARGE
            PresetImportIssue.DAMAGED_FILE -> PresetUiError.DAMAGED_FILE
            PresetImportIssue.UNSUPPORTED_VERSION -> PresetUiError.UNSUPPORTED_VERSION
            PresetImportIssue.MISSING_NAME -> PresetUiError.MISSING_NAME
            PresetImportIssue.NAME_TOO_LONG -> PresetUiError.NAME_TOO_LONG
            PresetImportIssue.UNSUPPORTED_TYPE -> PresetUiError.UNSUPPORTED_TYPE
            PresetImportIssue.INVALID_MAXIMUM_SIZE -> PresetUiError.INVALID_MAXIMUM_SIZE
            PresetImportIssue.INVALID_DIMENSIONS -> PresetUiError.INVALID_DIMENSIONS
            PresetImportIssue.INVALID_PAGE_LIMIT -> PresetUiError.INVALID_PAGE_LIMIT
        }
        else -> PresetUiError.DAMAGED_FILE
    }
}

private data object PresetSourceUnavailableException : IllegalStateException()

private data object PresetDestinationUnavailableException : IllegalStateException()
