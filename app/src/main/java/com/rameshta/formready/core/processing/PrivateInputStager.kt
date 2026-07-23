package com.rameshta.formready.core.processing

import android.content.Context
import android.net.Uri
import com.rameshta.formready.core.model.StagedInput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class PrivateInputStager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InputStager {
    override suspend fun stage(
        source: Uri,
        jobId: UUID,
        reportedMimeType: String?,
    ): StagedInput = withContext(Dispatchers.IO) {
        val stagingRoot = File(context.noBackupFilesDir, STAGING_DIRECTORY).apply {
            if (!exists() && !mkdirs()) {
                throw InputStagingException(InputStagingException.Code.WRITE_FAILED)
            }
        }
        val destination = File(stagingRoot, jobId.toString())
        val partial = File(stagingRoot, "${jobId}.part")
        partial.delete()

        try {
            var copied = 0L
            val input = context.contentResolver.openInputStream(source)
                ?: throw InputStagingException(InputStagingException.Code.SOURCE_UNAVAILABLE)
            input.buffered(BUFFER_BYTES).use { sourceStream ->
                partial.outputStream().buffered(BUFFER_BYTES).use { destinationStream ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = sourceStream.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_STAGED_INPUT_BYTES) {
                            throw InputStagingException(InputStagingException.Code.INPUT_TOO_LARGE)
                        }
                        destinationStream.write(buffer, 0, read)
                    }
                }
            }
            if (copied == 0L) {
                throw InputStagingException(InputStagingException.Code.EMPTY_INPUT)
            }
            if (destination.exists() && !destination.delete()) {
                throw InputStagingException(InputStagingException.Code.WRITE_FAILED)
            }
            if (!partial.renameTo(destination)) {
                throw InputStagingException(InputStagingException.Code.WRITE_FAILED)
            }
            StagedInput(
                privateRelativePath = "$STAGING_DIRECTORY/${destination.name}",
                byteCount = copied,
                reportedMimeType = reportedMimeType,
            )
        } catch (cancellation: CancellationException) {
            partial.delete()
            throw cancellation
        } catch (error: InputStagingException) {
            partial.delete()
            throw error
        } catch (error: IOException) {
            partial.delete()
            throw InputStagingException(InputStagingException.Code.WRITE_FAILED, error)
        }
    }

    override suspend fun remove(jobId: UUID) = withContext(Dispatchers.IO) {
        File(File(context.noBackupFilesDir, STAGING_DIRECTORY), jobId.toString()).delete()
        File(File(context.noBackupFilesDir, STAGING_DIRECTORY), "${jobId}.part").delete()
        Unit
    }

    companion object {
        const val MAX_STAGED_INPUT_BYTES = 200L * 1024L * 1024L
        private const val BUFFER_BYTES = 64 * 1024
        private const val STAGING_DIRECTORY = "staged-inputs"
    }
}
