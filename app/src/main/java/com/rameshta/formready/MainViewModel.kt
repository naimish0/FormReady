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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    jobRepository: JobRepository,
    outputArtifactRepository: OutputArtifactRepository,
) : ViewModel() {
    val uiState = combine(
        settingsRepository.settings,
        jobRepository.observeRecent(50),
        outputArtifactRepository.observeAll(),
    ) { settings, recentJobs, artifacts ->
        MainUiState(
            settings = settings,
            recentJobs = recentJobs,
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
}
