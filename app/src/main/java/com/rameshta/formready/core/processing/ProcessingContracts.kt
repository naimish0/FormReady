package com.rameshta.formready.core.processing

import android.graphics.Bitmap
import android.net.Uri
import com.rameshta.formready.core.model.JobType
import com.rameshta.formready.core.model.OutputArtifact
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.ProcessingPlan
import com.rameshta.formready.core.model.ValidationRuleResult
import java.io.File
import java.util.UUID

interface ImageMetadataReader {
    suspend fun inspect(input: File): ImageMetadata
}

data class ImageMetadata(
    val widthPx: Int,
    val heightPx: Int,
    val byteCount: Long,
    val mimeType: String,
    val exifOrientation: Int?,
    val format: InputImageFormat,
    val dpi: Int?,
    val hasAlpha: Boolean,
)

enum class InputImageFormat {
    JPEG,
    PNG,
    WEBP,
    HEIF,
}

class PhotoProcessingException(
    val code: Code,
    cause: Throwable? = null,
) : Exception(code.name, cause) {
    enum class Code {
        CORRUPT_INPUT,
        UNSUPPORTED_FORMAT,
        ANIMATED_IMAGE_UNSUPPORTED,
        EMPTY_SIGNATURE,
        IMAGE_DIMENSIONS_UNSAFE,
        DECODE_FAILED,
        TARGET_UNREACHABLE,
        DPI_UNSUPPORTED,
        OUTPUT_WRITE_FAILED,
        OUTPUT_VALIDATION_FAILED,
        DESTINATION_WRITE_FAILED,
    }
}

interface PhotoPreparationService {
    suspend fun stageAndInspect(source: Uri, jobId: UUID, reportedMimeType: String?): ImageMetadata

    suspend fun inspectStaged(jobId: UUID): ImageMetadata

    suspend fun loadPreview(jobId: UUID, maximumSidePx: Int = 1_024): Bitmap

    fun stagedRelativePath(jobId: UUID): String

    suspend fun duplicateStaged(sourceJobId: UUID, destinationJobId: UUID)

    suspend fun discard(jobId: UUID)
}

interface PhotoOutputAccess {
    fun outputFile(artifact: OutputArtifact): File

    fun shareUri(artifact: OutputArtifact): Uri

    suspend fun copyTo(artifact: OutputArtifact, destination: Uri)

    suspend fun deleteOwnedOutput(artifact: OutputArtifact): Boolean
}

interface ImageTransformEngine {
    suspend fun prepare(input: File, destination: File, plan: ProcessingPlan): PreparedPhoto
}

data class PreparedPhoto(
    val format: OutputFormat,
    val widthPx: Int,
    val heightPx: Int,
    val byteCount: Long,
    val dpi: Int?,
    val hasAlpha: Boolean,
    val jpegQuality: Int?,
    val wasSubstantiallyUpscaled: Boolean,
    val validationResults: List<ValidationRuleResult>,
)

interface TargetSizeEncoder

interface SignatureProcessor {
    suspend fun prepare(input: File, destination: File, plan: ProcessingPlan): PreparedPhoto
}

data class PdfPageMetadata(
    val widthPoints: Int,
    val heightPoints: Int,
) {
    val isLandscape: Boolean get() = widthPoints > heightPoints
}

data class PdfMetadata(
    val byteCount: Long,
    val pageCount: Int,
    val pages: List<PdfPageMetadata>,
    val encrypted: Boolean,
    val hasForms: Boolean?,
    val hasLinks: Boolean?,
    val hasAnnotations: Boolean?,
    val hasDigitalSignatures: Boolean?,
)

data class PreparedPdf(
    val byteCount: Long,
    val pageCount: Int,
    val pages: List<PdfPageMetadata>,
    val renderDpi: Int,
    val jpegQuality: Int,
    val passes: Int,
    val validationResults: List<ValidationRuleResult>,
)

class PdfProcessingException(
    val code: Code,
    cause: Throwable? = null,
) : Exception(code.name, cause) {
    enum class Code {
        CORRUPT_INPUT,
        ENCRYPTED_UNSUPPORTED,
        PAGE_COUNT_UNSAFE,
        PAGE_DIMENSIONS_UNSAFE,
        FLATTENING_ACKNOWLEDGEMENT_REQUIRED,
        TARGET_UNREACHABLE,
        OUTPUT_WRITE_FAILED,
        OUTPUT_VALIDATION_FAILED,
    }
}

interface PdfEngine {
    suspend fun inspect(input: File): PdfMetadata

    suspend fun renderPreview(input: File, pageIndex: Int, maximumEdgePx: Int = 1_024): Bitmap

    suspend fun flatten(
        input: File,
        destination: File,
        plan: ProcessingPlan,
    ): PreparedPdf

    suspend fun imagesToPdf(
        images: List<File>,
        destination: File,
        jpegQuality: Int = 85,
    ): PreparedPdf
}

interface PdfPreparationService {
    suspend fun stageAndInspect(source: Uri, jobId: UUID): PdfMetadata

    suspend fun inspectStaged(jobId: UUID): PdfMetadata

    suspend fun renderPreview(jobId: UUID, pageIndex: Int): Bitmap

    fun stagedRelativePath(jobId: UUID): String

    suspend fun discard(jobId: UUID)
}

interface OutputValidator {
    suspend fun validate(jobId: UUID, candidate: File): ValidationSummary
}

data class ValidationSummary(
    val isReadable: Boolean,
    val byteCount: Long,
    val failureCode: String?,
)

interface ExportRepository

interface JobProcessor {
    val supportedType: JobType

    suspend fun process(jobId: UUID): ProcessorResult
}

sealed interface ProcessorResult {
    data object Success : ProcessorResult

    data class Failure(val errorCode: String) : ProcessorResult
}
