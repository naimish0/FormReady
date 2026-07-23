package com.rameshta.formready.core.data.settings

import kotlinx.coroutines.flow.Flow

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

data class UserSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColour: Boolean = false,
    val removeMetadataByDefault: Boolean = true,
    val historyEnabled: Boolean = true,
    val privacyModeEnabled: Boolean = false,
    val reducedMotion: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setDynamicColour(enabled: Boolean)
}
