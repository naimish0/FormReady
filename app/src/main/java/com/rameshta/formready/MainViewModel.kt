package com.rameshta.formready

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.formready.core.data.settings.SettingsRepository
import com.rameshta.formready.core.data.settings.ThemePreference
import com.rameshta.formready.core.data.settings.UserSettings
import com.rameshta.formready.core.data.repository.JobRepository
import com.rameshta.formready.core.data.repository.OutputArtifactRepository
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.ProcessingJob
import com.rameshta.formready.core.model.JobStatus
import com.rameshta.formready.core.processing.PrivateWorkspaceCleaner
import com.rameshta.formready.core.processing.PhotoOutputAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val settings: UserSettings = UserSettings(),
    val recentJobs: List<ProcessingJob> = emptyList(),
    val recentArtifactsByJob: Map<String, OutputArtifact> = emptyMap(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val jobRepository: JobRepository,
    outputArtifactRepository: OutputArtifactRepository,
    private val workspaceCleaner: PrivateWorkspaceCleaner,
    private val outputAccess: PhotoOutputAccess,
) : ViewModel() {
    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                jobRepository.observeRecent(50),
            ) { settings, jobs -> settings.historyEnabled to jobs }
                .collectLatest { (historyEnabled, jobs) ->
                    if (
                        !historyEnabled &&
                        jobs.any {
                            it.status in setOf(
                                JobStatus.SUCCEEDED,
                                JobStatus.FAILED,
                                JobStatus.CANCELLED,
                            )
                        }
                    ) {
                        jobRepository.clear()
                    }
                }
        }
    }

    val uiState = combine(
        settingsRepository.settings,
        jobRepository.observeRecent(50),
        outputArtifactRepository.observeAll(),
    ) { settings, recentJobs, artifacts ->
        MainUiState(
            settings = settings,
            recentJobs = if (settings.historyEnabled) {
                recentJobs.filter {
                    it.status in setOf(
                        JobStatus.SUCCEEDED,
                        JobStatus.FAILED,
                        JobStatus.CANCELLED,
                    )
                }
            } else {
                emptyList()
            },
            recentArtifactsByJob = artifacts.associateBy { it.jobId.toString() },
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(),
        )

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setDynamicColour(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColour(enabled) }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun restoreSettings() {
        viewModelScope.launch { settingsRepository.restoreDefaults() }
    }

    fun setJobFavourite(job: ProcessingJob, favourite: Boolean) {
        viewModelScope.launch { jobRepository.setFavourite(job.id, favourite) }
    }

    fun deleteHistory(job: ProcessingJob) {
        viewModelScope.launch { jobRepository.delete(job.id) }
    }

    fun deleteOutputAndHistory(job: ProcessingJob, artifact: OutputArtifact) {
        viewModelScope.launch {
            if (runCatching { outputAccess.deleteOwnedOutput(artifact) }.getOrDefault(false)) {
                jobRepository.delete(job.id)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { jobRepository.clear() }
    }

    fun clearTemporaryFiles() {
        viewModelScope.launch { workspaceCleaner.clearTemporaryFiles() }
    }
}
