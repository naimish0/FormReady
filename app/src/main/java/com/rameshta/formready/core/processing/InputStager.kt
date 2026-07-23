package com.rameshta.formready.core.processing

import android.net.Uri
import com.rameshta.formready.core.model.StagedInput
import java.util.UUID

interface InputStager {
    suspend fun stage(
        source: Uri,
        jobId: UUID,
        reportedMimeType: String?,
    ): StagedInput

    suspend fun remove(jobId: UUID)
}

class InputStagingException(
    val code: Code,
    cause: Throwable? = null,
) : Exception(code.name, cause) {
    enum class Code {
        SOURCE_UNAVAILABLE,
        EMPTY_INPUT,
        INPUT_TOO_LARGE,
        WRITE_FAILED,
    }
}
