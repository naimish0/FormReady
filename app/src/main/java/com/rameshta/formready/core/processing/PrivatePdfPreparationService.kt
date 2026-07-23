package com.rameshta.formready.core.processing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class PrivatePdfPreparationService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val inputStager: InputStager,
    private val pdfEngine: PdfEngine,
) : PdfPreparationService {
    override suspend fun stageAndInspect(source: Uri, jobId: UUID): PdfMetadata {
        val staged = inputStager.stage(source, jobId, "application/pdf")
        return try {
            pdfEngine.inspect(resolve(staged.privateRelativePath))
        } catch (error: Exception) {
            inputStager.remove(jobId)
            throw error
        }
    }

    override suspend fun inspectStaged(jobId: UUID): PdfMetadata =
        pdfEngine.inspect(resolve(stagedRelativePath(jobId)))

    override suspend fun renderPreview(jobId: UUID, pageIndex: Int): Bitmap =
        pdfEngine.renderPreview(resolve(stagedRelativePath(jobId)), pageIndex)

    override fun stagedRelativePath(jobId: UUID): String = "staged-inputs/$jobId"

    override suspend fun discard(jobId: UUID) = inputStager.remove(jobId)

    private fun resolve(relativePath: String): File {
        val root = context.noBackupFilesDir.canonicalFile
        val file = File(root, relativePath).canonicalFile
        if (!file.path.startsWith("${root.path}${File.separator}")) {
            throw PdfProcessingException(PdfProcessingException.Code.CORRUPT_INPUT)
        }
        return file
    }
}
