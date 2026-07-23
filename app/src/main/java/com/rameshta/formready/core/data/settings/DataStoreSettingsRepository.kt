package com.rameshta.formready.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private val Context.formReadySettings by preferencesDataStore(name = "formready_settings")

class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SettingsRepository {
    override val settings: Flow<UserSettings> = context.formReadySettings.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            UserSettings(
                theme = preferences[Keys.THEME]
                    ?.let { stored -> runCatching { ThemePreference.valueOf(stored) }.getOrNull() }
                    ?: ThemePreference.SYSTEM,
                useDynamicColour = preferences[Keys.DYNAMIC_COLOUR] ?: false,
                removeMetadataByDefault = preferences[Keys.REMOVE_METADATA] ?: true,
                historyEnabled = preferences[Keys.HISTORY_ENABLED] ?: true,
                privacyModeEnabled = preferences[Keys.PRIVACY_MODE] ?: false,
                reducedMotion = preferences[Keys.REDUCED_MOTION] ?: false,
                dimensionUnit = preferences[Keys.DIMENSION_UNIT]
                    ?.let { runCatching { DefaultDimensionUnit.valueOf(it) }.getOrNull() }
                    ?: DefaultDimensionUnit.PIXELS,
                byteUnit = preferences[Keys.BYTE_UNIT]
                    ?.let { runCatching { DefaultByteUnit.valueOf(it) }.getOrNull() }
                    ?: DefaultByteUnit.DECIMAL,
                defaultImageFormat = preferences[Keys.IMAGE_FORMAT]
                    ?.let { runCatching { DefaultImageFormat.valueOf(it) }.getOrNull() }
                    ?: DefaultImageFormat.JPEG,
                defaultOutputDestination = preferences[Keys.OUTPUT_DESTINATION]
                    ?.let { runCatching { DefaultOutputDestination.valueOf(it) }.getOrNull() }
                    ?: DefaultOutputDestination.ASK_EVERY_TIME,
                qualityGuardEnabled = preferences[Keys.QUALITY_GUARD] ?: true,
                safetyMarginEnabled = preferences[Keys.SAFETY_MARGIN] ?: true,
                thumbnailsEnabled = preferences[Keys.THUMBNAILS] ?: false,
                automaticCleanupEnabled = preferences[Keys.AUTOMATIC_CLEANUP] ?: true,
            )
        }

    override suspend fun setTheme(theme: ThemePreference) {
        context.formReadySettings.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setDynamicColour(enabled: Boolean) {
        context.formReadySettings.edit { it[Keys.DYNAMIC_COLOUR] = enabled }
    }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.formReadySettings.edit { preferences ->
            val current = UserSettings(
                theme = preferences[Keys.THEME]
                    ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                    ?: ThemePreference.SYSTEM,
                useDynamicColour = preferences[Keys.DYNAMIC_COLOUR] ?: false,
                removeMetadataByDefault = preferences[Keys.REMOVE_METADATA] ?: true,
                historyEnabled = preferences[Keys.HISTORY_ENABLED] ?: true,
                privacyModeEnabled = preferences[Keys.PRIVACY_MODE] ?: false,
                reducedMotion = preferences[Keys.REDUCED_MOTION] ?: false,
                dimensionUnit = preferences[Keys.DIMENSION_UNIT]
                    ?.let { runCatching { DefaultDimensionUnit.valueOf(it) }.getOrNull() }
                    ?: DefaultDimensionUnit.PIXELS,
                byteUnit = preferences[Keys.BYTE_UNIT]
                    ?.let { runCatching { DefaultByteUnit.valueOf(it) }.getOrNull() }
                    ?: DefaultByteUnit.DECIMAL,
                defaultImageFormat = preferences[Keys.IMAGE_FORMAT]
                    ?.let { runCatching { DefaultImageFormat.valueOf(it) }.getOrNull() }
                    ?: DefaultImageFormat.JPEG,
                defaultOutputDestination = preferences[Keys.OUTPUT_DESTINATION]
                    ?.let { runCatching { DefaultOutputDestination.valueOf(it) }.getOrNull() }
                    ?: DefaultOutputDestination.ASK_EVERY_TIME,
                qualityGuardEnabled = preferences[Keys.QUALITY_GUARD] ?: true,
                safetyMarginEnabled = preferences[Keys.SAFETY_MARGIN] ?: true,
                thumbnailsEnabled = preferences[Keys.THUMBNAILS] ?: false,
                automaticCleanupEnabled = preferences[Keys.AUTOMATIC_CLEANUP] ?: true,
            )
            val updated = transform(current)
            preferences[Keys.THEME] = updated.theme.name
            preferences[Keys.DYNAMIC_COLOUR] = updated.useDynamicColour
            preferences[Keys.REMOVE_METADATA] = updated.removeMetadataByDefault
            preferences[Keys.HISTORY_ENABLED] = updated.historyEnabled
            preferences[Keys.PRIVACY_MODE] = updated.privacyModeEnabled
            preferences[Keys.REDUCED_MOTION] = updated.reducedMotion
            preferences[Keys.DIMENSION_UNIT] = updated.dimensionUnit.name
            preferences[Keys.BYTE_UNIT] = updated.byteUnit.name
            preferences[Keys.IMAGE_FORMAT] = updated.defaultImageFormat.name
            preferences[Keys.OUTPUT_DESTINATION] = updated.defaultOutputDestination.name
            preferences[Keys.QUALITY_GUARD] = updated.qualityGuardEnabled
            preferences[Keys.SAFETY_MARGIN] = updated.safetyMarginEnabled
            preferences[Keys.THUMBNAILS] = updated.thumbnailsEnabled
            preferences[Keys.AUTOMATIC_CLEANUP] = updated.automaticCleanupEnabled
        }
    }

    override suspend fun restoreDefaults() {
        context.formReadySettings.edit { it.clear() }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOUR = booleanPreferencesKey("dynamic_colour")
        val REMOVE_METADATA = booleanPreferencesKey("remove_metadata")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val DIMENSION_UNIT = stringPreferencesKey("dimension_unit")
        val BYTE_UNIT = stringPreferencesKey("byte_unit")
        val IMAGE_FORMAT = stringPreferencesKey("image_format")
        val OUTPUT_DESTINATION = stringPreferencesKey("output_destination")
        val QUALITY_GUARD = booleanPreferencesKey("quality_guard")
        val SAFETY_MARGIN = booleanPreferencesKey("safety_margin")
        val THUMBNAILS = booleanPreferencesKey("thumbnails")
        val AUTOMATIC_CLEANUP = booleanPreferencesKey("automatic_cleanup")
    }
}
