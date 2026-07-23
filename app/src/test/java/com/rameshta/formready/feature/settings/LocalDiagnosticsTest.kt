package com.rameshta.formready.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDiagnosticsTest {
    @Test
    fun exportContainsOnlyAllowlistedEnvironmentFields() {
        val text = LocalDiagnostics(
            appVersion = "1.0-test",
            androidApi = 36,
            availableMemoryMiB = 256,
            availableStorageMiB = 1_024,
        ).render()

        assertTrue(text.contains("App version: 1.0-test"))
        assertTrue(text.contains("Android API: 36"))
        listOf(
            "filename",
            "path",
            "content://",
            "signature",
            "password",
            "thumbnail",
            "purchase",
        ).forEach { forbidden ->
            assertFalse(text.contains(forbidden, ignoreCase = true))
        }
    }
}
