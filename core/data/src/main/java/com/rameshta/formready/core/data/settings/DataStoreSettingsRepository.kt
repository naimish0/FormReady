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
            )
        }

    override suspend fun setTheme(theme: ThemePreference) {
        context.formReadySettings.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setDynamicColour(enabled: Boolean) {
        context.formReadySettings.edit { it[Keys.DYNAMIC_COLOUR] = enabled }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOUR = booleanPreferencesKey("dynamic_colour")
        val REMOVE_METADATA = booleanPreferencesKey("remove_metadata")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    }
}
