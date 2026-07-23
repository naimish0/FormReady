package com.rameshta.formready.core.data.settings

import kotlinx.coroutines.flow.Flow

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class DefaultDimensionUnit {
    PIXELS,
    MILLIMETRES,
    CENTIMETRES,
    INCHES,
}

enum class DefaultByteUnit {
    DECIMAL,
    BINARY,
}

enum class DefaultImageFormat {
    JPEG,
    PNG,
}

enum class DefaultOutputDestination {
    ASK_EVERY_TIME,
}

data class UserSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColour: Boolean = false,
    val removeMetadataByDefault: Boolean = true,
    val historyEnabled: Boolean = true,
    val privacyModeEnabled: Boolean = false,
    val reducedMotion: Boolean = false,
    val dimensionUnit: DefaultDimensionUnit = DefaultDimensionUnit.PIXELS,
    val byteUnit: DefaultByteUnit = DefaultByteUnit.DECIMAL,
    val defaultImageFormat: DefaultImageFormat = DefaultImageFormat.JPEG,
    val defaultOutputDestination: DefaultOutputDestination = DefaultOutputDestination.ASK_EVERY_TIME,
    val qualityGuardEnabled: Boolean = true,
    val safetyMarginEnabled: Boolean = true,
    val thumbnailsEnabled: Boolean = false,
    val automaticCleanupEnabled: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setDynamicColour(enabled: Boolean)

    suspend fun update(transform: (UserSettings) -> UserSettings)

    suspend fun restoreDefaults()
}
