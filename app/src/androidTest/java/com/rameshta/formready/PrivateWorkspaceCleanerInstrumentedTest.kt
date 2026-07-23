package com.rameshta.formready

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.formready.core.processing.PrivatePhotoOutputAccess
import com.rameshta.formready.core.processing.PrivateWorkspaceCleaner
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateWorkspaceCleanerInstrumentedTest {
    @Test
    fun clearTemporaryFilesRemovesPrivateWorkButPreservesCompletedOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val staged = File(context.noBackupFilesDir, "staged-inputs/test-cleaner").apply {
            parentFile?.mkdirs()
            writeText("temporary")
        }
        val outputDirectory =
            File(context.filesDir, PrivatePhotoOutputAccess.OUTPUT_DIRECTORY).apply { mkdirs() }
        val partial = File(outputDirectory, "test-cleaner.part").apply { writeText("temporary") }
        val completed = File(outputDirectory, "test-cleaner.jpg").apply { writeText("completed") }
        val scannerSession =
            File(context.noBackupFilesDir, "scanner-sessions/test/page.jpg").apply {
                parentFile?.mkdirs()
                writeText("scan")
            }
        val scannerCapture = File(context.cacheDir, "scanner-captures/test.jpg").apply {
            parentFile?.mkdirs()
            writeText("capture")
        }
        val idPhotoCapture = File(context.cacheDir, "id-photo-captures/test.jpg").apply {
            parentFile?.mkdirs()
            writeText("capture")
        }

        PrivateWorkspaceCleaner(context).clearTemporaryFiles()

        assertFalse(staged.exists())
        assertFalse(partial.exists())
        assertFalse(scannerSession.exists())
        assertFalse(scannerCapture.exists())
        assertFalse(idPhotoCapture.exists())
        assertTrue(completed.exists())
        completed.delete()
    }
}
