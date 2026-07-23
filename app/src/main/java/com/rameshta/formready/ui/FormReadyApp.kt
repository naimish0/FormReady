package com.rameshta.formready.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rameshta.formready.R
import com.rameshta.formready.core.data.settings.ThemePreference
import com.rameshta.formready.core.data.settings.UserSettings
import com.rameshta.formready.feature.history.HistoryScreen
import com.rameshta.formready.feature.home.HomeScreen
import com.rameshta.formready.feature.photo.PhotoRoute
import com.rameshta.formready.feature.photo.PhotoViewModel
import com.rameshta.formready.feature.pdf.PdfRoute
import com.rameshta.formready.feature.pdf.PdfViewModel
import com.rameshta.formready.feature.presets.PresetsScreen
import com.rameshta.formready.feature.presets.PresetsViewModel
import com.rameshta.formready.feature.settings.SettingsScreen
import com.rameshta.formready.feature.scanner.ScannerRoute
import com.rameshta.formready.feature.scanner.ScannerViewModel
import com.rameshta.formready.feature.signature.SignatureRoute
import com.rameshta.formready.feature.signature.SignatureViewModel
import com.rameshta.formready.core.model.ProcessingJob

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
) {
    HOME("home", R.string.navigation_home, R.drawable.ic_navigation_home),
    PRESETS("presets", R.string.navigation_presets, R.drawable.ic_navigation_presets),
    HISTORY("history", R.string.navigation_history, R.drawable.ic_navigation_history),
    SETTINGS("settings", R.string.navigation_settings, R.drawable.ic_navigation_settings),
}

@Composable
fun FormReadyApp(
    settings: UserSettings,
    onThemeSelected: (ThemePreference) -> Unit,
    onDynamicColourChanged: (Boolean) -> Unit,
    onSettingsChanged: (UserSettings.() -> UserSettings) -> Unit,
    onRestoreSettings: () -> Unit,
    photoViewModel: PhotoViewModel,
    signatureViewModel: SignatureViewModel,
    pdfViewModel: PdfViewModel,
    presetsViewModel: PresetsViewModel,
    scannerViewModel: ScannerViewModel,
    recentJobs: List<ProcessingJob>,
    recentArtifactsByJob: Map<String, com.rameshta.formready.core.model.OutputArtifact>,
    onJobFavourite: (ProcessingJob, Boolean) -> Unit,
    onDeleteHistory: (ProcessingJob) -> Unit,
    onDeleteOutputAndHistory: (ProcessingJob, com.rameshta.formready.core.model.OutputArtifact) -> Unit,
    onClearHistory: () -> Unit,
    onClearTemporaryFiles: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (
                currentDestination?.route !in
                    setOf(PHOTO_ROUTE, SIGNATURE_ROUTE, PDF_ROUTE, SCANNER_ROUTE)
            ) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(TopLevelDestination.HOME.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = {
                            androidx.compose.material3.Text(stringResource(destination.labelRes))
                        },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.HOME.route,
            ) {
                composable(TopLevelDestination.HOME.route) {
                    HomeScreen(
                        onPreparePhoto = {
                            photoViewModel.enableStandardPhotoMode()
                            navController.navigate(PHOTO_ROUTE)
                        },
                        onPrepareSignature = { navController.navigate(SIGNATURE_ROUTE) },
                        onPreparePdf = { navController.navigate(PDF_ROUTE) },
                        onScanDocument = { navController.navigate(SCANNER_ROUTE) },
                        onPrepareIdPhoto = {
                            photoViewModel.enableIdPhotoMode()
                            navController.navigate(PHOTO_ROUTE)
                        },
                    )
                }
                composable(TopLevelDestination.PRESETS.route) {
                    PresetsScreen(presetsViewModel)
                }
                composable(TopLevelDestination.HISTORY.route) {
                    HistoryScreen(
                        jobs = recentJobs,
                        artifactsByJob = recentArtifactsByJob,
                        onRepeat = { job ->
                            when (job.type) {
                                com.rameshta.formready.core.model.JobType.PHOTO -> {
                                    photoViewModel.reuseRequirements(job.serializedPlan)
                                    navController.navigate(PHOTO_ROUTE)
                                }
                                com.rameshta.formready.core.model.JobType.SIGNATURE -> {
                                    signatureViewModel.reuseRequirements(job.serializedPlan)
                                    navController.navigate(SIGNATURE_ROUTE)
                                }
                                com.rameshta.formready.core.model.JobType.PDF -> {
                                    pdfViewModel.reuseRequirements(job.serializedPlan)
                                    navController.navigate(PDF_ROUTE)
                                }
                                com.rameshta.formready.core.model.JobType.VALIDATION -> Unit
                            }
                        },
                        onFavourite = onJobFavourite,
                        onDelete = onDeleteHistory,
                        onDeleteOutputAndHistory = onDeleteOutputAndHistory,
                        onClear = onClearHistory,
                    )
                }
                composable(TopLevelDestination.SETTINGS.route) {
                    SettingsScreen(
                        settings = settings,
                        onThemeSelected = onThemeSelected,
                        onDynamicColourChanged = onDynamicColourChanged,
                        onSettingsChanged = onSettingsChanged,
                        onRestoreSettings = onRestoreSettings,
                        onClearHistory = onClearHistory,
                        onClearTemporaryFiles = onClearTemporaryFiles,
                    )
                }
                composable(PHOTO_ROUTE) {
                    PhotoRoute(
                        onBack = { navController.popBackStack() },
                        viewModel = photoViewModel,
                    )
                }
                composable(SIGNATURE_ROUTE) {
                    SignatureRoute(
                        onBack = { navController.popBackStack() },
                        viewModel = signatureViewModel,
                    )
                }
                composable(PDF_ROUTE) {
                    PdfRoute(
                        onBack = { navController.popBackStack() },
                        viewModel = pdfViewModel,
                    )
                }
                composable(SCANNER_ROUTE) {
                    ScannerRoute(
                        onBack = { navController.popBackStack() },
                        viewModel = scannerViewModel,
                    )
                }
            }
        }
    }
}

private const val PHOTO_ROUTE = "photo"
private const val SIGNATURE_ROUTE = "signature"
private const val PDF_ROUTE = "pdf"
private const val SCANNER_ROUTE = "scanner"
