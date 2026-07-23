package com.rameshta.formready.core.monetization

import org.junit.Assert.assertFalse
import org.junit.Test

class NoOpAdManagerTest {
    @Test
    fun launchBindingNeverEnablesOrLoadsAdvertising() {
        val manager = NoOpAdManager()

        manager.onAppSessionStarted()
        manager.onSuccessfulExport()
        manager.onNaturalBreakAfterResult()

        assertFalse(manager.isEnabled)
    }
}
