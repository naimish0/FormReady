package com.rameshta.formready

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rameshta.formready.core.data.settings.SettingsRepository
import com.rameshta.formready.core.data.settings.ThemePreference
import com.rameshta.formready.core.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val settings: UserSettings = UserSettings(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState = settingsRepository.settings
        .map(::MainUiState)
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
