package com.rameshta.formready.core.processing

import com.rameshta.formready.core.model.JobType
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
)

interface ImageTransformEngine

interface TargetSizeEncoder

interface SignatureProcessor

interface PdfEngine

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
