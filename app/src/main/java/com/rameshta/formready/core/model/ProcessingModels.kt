package com.rameshta.formready.core.model

import java.util.UUID

enum class JobType {
    PHOTO,
    SIGNATURE,
    PDF,
    VALIDATION,
}

enum class JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    fun canTransitionTo(next: JobStatus): Boolean = when (this) {
        QUEUED -> next == RUNNING || next == CANCELLED
        RUNNING -> next == SUCCEEDED || next == FAILED || next == CANCELLED
        SUCCEEDED, FAILED, CANCELLED -> false
    }
}

enum class OutputFormat {
    JPEG,
    PNG,
    PDF,
}

data class OutputSpecification(
    val format: OutputFormat,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val dimensionRule: DimensionRule = DimensionRule.EXACT,
    val byteUnit: ByteUnit = ByteUnit.KB,
    val physicalWidth: Double? = null,
    val physicalHeight: Double? = null,
    val physicalUnit: PhysicalUnit? = null,
    val maximumBytes: Long? = null,
    val minimumBytes: Long? = null,
    val dpi: Int? = null,
    val safetyMarginBytes: Long? = null,
    val backgroundArgb: Int = 0xFFFFFFFF.toInt(),
) {
    init {
        require(widthPx == null || widthPx > 0)
        require(heightPx == null || heightPx > 0)
        require((widthPx == null) == (heightPx == null))
        require((physicalWidth == null) == (physicalHeight == null))
        require((physicalWidth == null) == (physicalUnit == null))
        require(physicalWidth == null || physicalWidth > 0.0)
        require(physicalHeight == null || physicalHeight > 0.0)
        require(maximumBytes == null || maximumBytes > 0)
        require(minimumBytes == null || minimumBytes >= 0)
        require(maximumBytes == null || minimumBytes == null || minimumBytes <= maximumBytes)
        require(dpi == null || dpi > 0)
        require(safetyMarginBytes == null || safetyMarginBytes >= 0)
    }
}

sealed interface NormalizedTransform {
    data class Rotate(val degreesClockwise: Float) : NormalizedTransform

    data class Crop(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : NormalizedTransform {
        init {
            require(left in 0f..1f && top in 0f..1f)
            require(right in 0f..1f && bottom in 0f..1f)
            require(left < right && top < bottom)
        }
    }

    data class FitPad(val backgroundArgb: Int) : NormalizedTransform
}

data class ProcessingPlan(
    val jobId: UUID,
    val transforms: List<NormalizedTransform>,
    val output: OutputSpecification,
    val hardRuleIds: Set<String>,
    val advisoryRuleIds: Set<String>,
)

data class ProcessingJob(
    val id: UUID,
    val projectId: UUID?,
    val type: JobType,
    val status: JobStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val errorCode: String?,
    val serializedPlan: String,
    val stagedInputRelativePath: String?,
)

data class OutputArtifact(
    val id: UUID,
    val jobId: UUID,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val byteCount: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val dpi: Int?,
    val readiness: Readiness,
    val validationJson: String,
    val createdAtEpochMillis: Long,
)

data class StagedInput(
    val privateRelativePath: String,
    val byteCount: Long,
    val reportedMimeType: String?,
)
