package com.rameshta.formready

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rameshta.formready.core.processing.PrivateWorkspaceCleaner
import com.rameshta.formready.core.data.settings.SettingsRepository
import com.rameshta.formready.core.monetization.AdManager
import com.rameshta.formready.core.monetization.ProManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class FormReadyApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject
    lateinit var workspaceCleaner: PrivateWorkspaceCleaner
    @Inject
    lateinit var settingsRepository: SettingsRepository
    @Inject
    lateinit var adManager: AdManager
    @Inject
    lateinit var proManager: ProManager
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        adManager.onAppSessionStarted()
        proManager.start()
        applicationScope.launch {
            if (settingsRepository.settings.first().automaticCleanupEnabled) {
                workspaceCleaner.removeAbandonedPartials()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
