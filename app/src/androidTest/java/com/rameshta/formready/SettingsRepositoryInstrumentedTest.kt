package com.rameshta.formready

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.formready.core.data.settings.DataStoreSettingsRepository
import com.rameshta.formready.core.data.settings.DefaultByteUnit
import com.rameshta.formready.core.data.settings.DefaultDimensionUnit
import com.rameshta.formready.core.data.settings.DefaultImageFormat
import com.rameshta.formready.core.data.settings.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryInstrumentedTest {
    @Test
    fun expandedDefaultsPersistAndRestoreAtomically() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = DataStoreSettingsRepository(context)
        try {
            repository.restoreDefaults()
            repository.update {
                it.copy(
                    dimensionUnit = DefaultDimensionUnit.INCHES,
                    byteUnit = DefaultByteUnit.BINARY,
                    defaultImageFormat = DefaultImageFormat.PNG,
                    privacyModeEnabled = true,
                    reducedMotion = true,
                )
            }

            val updated = repository.settings.first()
            assertEquals(DefaultDimensionUnit.INCHES, updated.dimensionUnit)
            assertEquals(DefaultByteUnit.BINARY, updated.byteUnit)
            assertEquals(DefaultImageFormat.PNG, updated.defaultImageFormat)
            assertEquals(true, updated.privacyModeEnabled)
            assertEquals(true, updated.reducedMotion)
        } finally {
            repository.restoreDefaults()
        }
        assertEquals(UserSettings(), repository.settings.first())
    }
}
