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

    data class FitPad(
        val backgroundArgb: Int,
        val paddingFraction: Float = 0f,
        val horizontalOffset: Float = 0f,
        val verticalOffset: Float = 0f,
    ) : NormalizedTransform {
        init {
            require(paddingFraction in 0f..0.45f)
            require(horizontalOffset in -1f..1f)
            require(verticalOffset in -1f..1f)
        }
    }
}

data class SignatureOptions(
    val grayscale: Boolean = true,
    val contrastPercent: Int = 120,
    val threshold: Int = 190,
    val cleanPaperBackground: Boolean = true,
    val removeSpeckles: Boolean = true,
    val autoCrop: Boolean = true,
    val safeMarginPercent: Int = 6,
    val inkArgb: Int = 0xFF111111.toInt(),
    val transparentBackground: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
) {
    init {
        require(contrastPercent in 50..250)
        require(threshold in 1..254)
        require(safeMarginPercent in 0..25)
        require(cropLeft >= 0f && cropLeft < cropRight && cropRight <= 1f)
        require(cropTop >= 0f && cropTop < cropBottom && cropBottom <= 1f)
    }
}

enum class PdfCompressionMode {
    VALIDATE_ONLY,
    STRONG_FLATTEN,
}

data class PdfOptions(
    val compressionMode: PdfCompressionMode = PdfCompressionMode.VALIDATE_ONLY,
    val flatteningAcknowledged: Boolean = false,
    val initialDpi: Int = 150,
    val minimumDpi: Int = 120,
    val minimumJpegQuality: Int = 40,
    val maximumPasses: Int = 6,
) {
    init {
        require(initialDpi in 120..300)
        require(minimumDpi in 72..initialDpi)
        require(minimumJpegQuality in 40..95)
        require(maximumPasses in 1..6)
        require(
            compressionMode != PdfCompressionMode.STRONG_FLATTEN ||
                flatteningAcknowledged,
        )
    }
}

data class MaskStroke(
    val x: Float,
    val y: Float,
    val radius: Float,
    val restore: Boolean,
) {
    init {
        require(x in 0f..1f && y in 0f..1f)
        require(radius in 0.002f..0.25f)
    }
}

data class IdPhotoOptions(
    val replaceBackground: Boolean = false,
    val backgroundArgb: Int = 0xFFFFFFFF.toInt(),
    val maskStrokes: List<MaskStroke> = emptyList(),
) {
    init {
        require(maskStrokes.size <= 500)
    }
}

data class ProcessingPlan(
    val jobId: UUID,
    val transforms: List<NormalizedTransform>,
    val output: OutputSpecification,
    val hardRuleIds: Set<String>,
    val advisoryRuleIds: Set<String>,
    val signatureOptions: SignatureOptions? = null,
    val pdfOptions: PdfOptions? = null,
    val idPhotoOptions: IdPhotoOptions? = null,
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
    val isFavourite: Boolean = false,
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
