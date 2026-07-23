package com.rameshta.formready

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.formready.core.data.settings.ThemePreference
import com.rameshta.formready.core.designsystem.FormReadyTheme
import com.rameshta.formready.feature.photo.PhotoViewModel
import com.rameshta.formready.ui.FormReadyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val photoViewModel: PhotoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (state.settings.theme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            FormReadyTheme(
                darkTheme = darkTheme,
                useDynamicColour = state.settings.useDynamicColour,
            ) {
                FormReadyApp(
                    settings = state.settings,
                    onThemeSelected = viewModel::setTheme,
                    onDynamicColourChanged = viewModel::setDynamicColour,
                    photoViewModel = photoViewModel,
                    recentJobs = state.recentJobs,
                    recentArtifactsByJob = state.recentArtifactsByJob,
                )
            }
        }
    }
}
