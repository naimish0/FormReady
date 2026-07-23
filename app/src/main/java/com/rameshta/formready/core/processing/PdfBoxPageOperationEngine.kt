package com.rameshta.formready.core.processing

import android.content.Context
import com.rameshta.formready.core.model.ValidationOutcome
import com.rameshta.formready.core.model.ValidationRuleResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class PdfBoxPageOperationEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val platformPdfEngine: PlatformPdfEngine,
) : StructurePreservingPdfEngine {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override suspend fun create(
        sources: List<File>,
        pages: List<PdfPageSelection>,
        destination: File,
    ): PreparedPdfOperation = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty() && sources.size <= MAX_SOURCE_COUNT)
        require(pages.isNotEmpty() && pages.size <= MAX_PAGE_COUNT)
        require(sources.sumOf(File::length) <= MAX_TOTAL_INPUT_BYTES)
        val sourceMetadata = sources.map { source ->
            platformPdfEngine.inspect(source).also { metadata ->
                if (
                    metadata.encrypted ||
                    metadata.hasForms == true ||
                    metadata.hasDigitalSignatures == true
                ) {
                    throw PdfProcessingException(
                        PdfProcessingException.Code.PROTECTED_STRUCTURE_UNSUPPORTED,
                    )
                }
            }
        }
        pages.forEach { page ->
            require(page.sourceIndex in sources.indices)
            require(page.pageIndex in 0 until sourceMetadata[page.sourceIndex].pageCount)
        }

        destination.parentFile?.mkdirs()
        val part = File(destination.parentFile, "${destination.name}.part")
        part.delete()
        destination.delete()
        val opened = mutableListOf<PDDocument>()
        try {
            sources.forEach { source ->
                val document = PDDocument.load(source)
                if (
                    document.isEncrypted ||
                    document.documentCatalog.acroForm != null ||
                    document.signatureDictionaries.isNotEmpty()
                ) {
                    document.close()
                    throw PdfProcessingException(
                        PdfProcessingException.Code.PROTECTED_STRUCTURE_UNSUPPORTED,
                    )
                }
                opened += document
            }
            val expectedAnnotationCounts = pages.map { selection ->
                opened[selection.sourceIndex].getPage(selection.pageIndex).annotations.size
            }
            val expectedContentPresence = pages.map { selection ->
                opened[selection.sourceIndex].getPage(selection.pageIndex).hasContents()
            }
            PDDocument().use { output ->
                pages.forEach { selection ->
                    coroutineContext.ensureActive()
                    val imported = output.importPage(
                        opened[selection.sourceIndex].getPage(selection.pageIndex),
                    )
                    imported.rotation = Math.floorMod(
                        imported.rotation + selection.rotationQuarterTurns * 90,
                        360,
                    )
                }
                output.save(part)
            }
            check(part.length() in 5..MAX_OUTPUT_BYTES)
            if (!part.renameTo(destination)) {
                part.inputStream().buffered().use { source ->
                    destination.outputStream().buffered().use(source::copyTo)
                }
                part.delete()
            }

            PDDocument.load(destination).use { verified ->
                check(verified.numberOfPages == pages.size)
                pages.indices.forEach { index ->
                    val page = verified.getPage(index)
                    check(page.annotations.size == expectedAnnotationCounts[index])
                    check(page.hasContents() == expectedContentPresence[index])
                }
            }
            val reopened = platformPdfEngine.inspect(destination)
            check(reopened.pageCount == pages.size)
            val expectedPages = pages.map { selection ->
                val sourcePage = sourceMetadata[selection.sourceIndex].pages[selection.pageIndex]
                if (selection.rotationQuarterTurns % 2 == 0) {
                    sourcePage
                } else {
                    PdfPageMetadata(sourcePage.heightPoints, sourcePage.widthPoints)
                }
            }
            check(
                reopened.pages.zip(expectedPages).all { (actual, expected) ->
                    actual.widthPoints == expected.widthPoints &&
                        actual.heightPoints == expected.heightPoints
                },
            )
            reopened.pages.indices.forEach { index ->
                coroutineContext.ensureActive()
                platformPdfEngine.renderPreview(destination, index, VALIDATION_EDGE).recycle()
            }
            PreparedPdfOperation(
                metadata = reopened,
                validationResults = listOf(
                    ValidationRuleResult(
                        ruleId = "pdf.structure_preserved",
                        outcome = ValidationOutcome.PASS,
                        expected = "Page objects copied without raster flattening",
                        actual = "${reopened.pageCount} pages reopened and rendered",
                        explanation = "Text, vector, image, and page annotation objects remain PDF objects.",
                        fixAction = null,
                        isHardRule = true,
                    ),
                    ValidationRuleResult(
                        ruleId = "pdf.page_order",
                        outcome = ValidationOutcome.PASS,
                        expected = "Requested page order, rotation, and count",
                        actual = "${reopened.pageCount} pages with matching page dimensions",
                        explanation = "The output page tree was reopened and checked against the edit plan.",
                        fixAction = null,
                        isHardRule = true,
                    ),
                ),
            )
        } catch (error: Exception) {
            destination.delete()
            part.delete()
            if (error is PdfProcessingException) throw error
            throw PdfProcessingException(PdfProcessingException.Code.OUTPUT_VALIDATION_FAILED, error)
        } finally {
            opened.forEach { runCatching { it.close() } }
            part.delete()
        }
    }

    private companion object {
        const val MAX_SOURCE_COUNT = 10
        const val MAX_PAGE_COUNT = 100
        const val MAX_TOTAL_INPUT_BYTES = 200L * 1024L * 1024L
        const val MAX_OUTPUT_BYTES = 200L * 1024L * 1024L
        const val VALIDATION_EDGE = 128
    }
}
