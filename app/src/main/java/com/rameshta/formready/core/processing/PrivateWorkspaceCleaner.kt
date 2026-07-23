package com.rameshta.formready.core.processing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class PrivateWorkspaceCleaner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun removeAbandonedPartials(nowEpochMillis: Long = System.currentTimeMillis()) {
        listOf(
            File(context.noBackupFilesDir, STAGED_INPUT_DIRECTORY),
            File(context.filesDir, PrivatePhotoOutputAccess.OUTPUT_DIRECTORY),
        ).forEach { directory ->
            directory.listFiles()
                ?.asSequence()
                ?.filter { file -> file.isFile && file.name.endsWith(PARTIAL_SUFFIX) }
                ?.filter { file -> nowEpochMillis - file.lastModified() >= RETENTION_MILLIS }
                ?.forEach(File::delete)
        }
        File(context.cacheDir, SIGNATURE_CAPTURE_DIRECTORY)
            .listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.filter { file -> nowEpochMillis - file.lastModified() >= RETENTION_MILLIS }
            ?.forEach(File::delete)
    }

    companion object {
        const val RETENTION_MILLIS = 24L * 60L * 60L * 1_000L
        private const val STAGED_INPUT_DIRECTORY = "staged-inputs"
        private const val PARTIAL_SUFFIX = ".part"
        private const val SIGNATURE_CAPTURE_DIRECTORY = "signature-captures"
    }
}
