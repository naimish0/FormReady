package com.rameshta.formready

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rameshta.formready.core.data.settings.ThemePreference
import com.rameshta.formready.core.designsystem.FormReadyTheme
import com.rameshta.formready.feature.photo.PhotoViewModel
import com.rameshta.formready.feature.pdf.PdfViewModel
import com.rameshta.formready.feature.presets.PresetsViewModel
import com.rameshta.formready.feature.signature.SignatureViewModel
import com.rameshta.formready.feature.scanner.ScannerViewModel
import com.rameshta.formready.feature.batch.BatchViewModel
import com.rameshta.formready.ui.FormReadyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val photoViewModel: PhotoViewModel by viewModels()
    private val signatureViewModel: SignatureViewModel by viewModels()
    private val pdfViewModel: PdfViewModel by viewModels()
    private val presetsViewModel: PresetsViewModel by viewModels()
    private val scannerViewModel: ScannerViewModel by viewModels()
    private val batchViewModel: BatchViewModel by viewModels()

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
            DisposableEffect(state.settings.privacyModeEnabled) {
                if (state.settings.privacyModeEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose { }
            }
            FormReadyTheme(
                darkTheme = darkTheme,
                useDynamicColour = state.settings.useDynamicColour,
            ) {
                FormReadyApp(
                    settings = state.settings,
                    onThemeSelected = viewModel::setTheme,
                    onDynamicColourChanged = viewModel::setDynamicColour,
                    onSettingsChanged = viewModel::updateSettings,
                    onRestoreSettings = viewModel::restoreSettings,
                    photoViewModel = photoViewModel,
                    signatureViewModel = signatureViewModel,
                    pdfViewModel = pdfViewModel,
                    presetsViewModel = presetsViewModel,
                    scannerViewModel = scannerViewModel,
                    batchViewModel = batchViewModel,
                    recentJobs = state.recentJobs,
                    recentArtifactsByJob = state.recentArtifactsByJob,
                    onJobFavourite = viewModel::setJobFavourite,
                    onDeleteHistory = viewModel::deleteHistory,
                    onDeleteOutputAndHistory = viewModel::deleteOutputAndHistory,
                    onClearHistory = viewModel::clearHistory,
                    onClearTemporaryFiles = viewModel::clearTemporaryFiles,
                )
            }
        }
    }
}
