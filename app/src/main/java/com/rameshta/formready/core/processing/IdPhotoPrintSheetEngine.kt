package com.rameshta.formready.core.processing

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

enum class PrintSheetSize(val widthPoints: Int, val heightPoints: Int) {
    FOUR_BY_SIX(288, 432),
    A4(595, 842),
}

class IdPhotoPrintSheetEngine @Inject constructor() {
    suspend fun create(
        source: File,
        destination: File,
        sheet: PrintSheetSize,
        copies: Int,
        photoWidthMm: Double = 35.0,
        photoHeightMm: Double = 45.0,
        cutGuides: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        require(copies in setOf(2, 4, 6, 8))
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("ID_PHOTO_SOURCE_UNREADABLE")
        val photoWidth = (photoWidthMm * POINTS_PER_MM).toFloat()
        val photoHeight = (photoHeightMm * POINTS_PER_MM).toFloat()
        val columns = if (sheet == PrintSheetSize.FOUR_BY_SIX) 2 else 3
        val rows = ceil(copies / columns.toDouble()).toInt()
        val gap = 8f
        val contentWidth = columns * photoWidth + (columns - 1) * gap
        val contentHeight = rows * photoHeight + (rows - 1) * gap
        require(contentWidth <= sheet.widthPoints - 24 && contentHeight <= sheet.heightPoints - 48) {
            "PRINT_SHEET_DOES_NOT_FIT"
        }
        val startX = (sheet.widthPoints - contentWidth) / 2f
        val startY = (sheet.heightPoints - contentHeight) / 2f
        val document = PdfDocument()
        try {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(sheet.widthPoints, sheet.heightPoints, 1).create(),
            )
            page.canvas.drawColor(Color.WHITE)
            repeat(copies) { index ->
                val column = index % columns
                val row = index / columns
                val left = startX + column * (photoWidth + gap)
                val top = startY + row * (photoHeight + gap)
                val destinationRect = RectF(left, top, left + photoWidth, top + photoHeight)
                page.canvas.drawBitmap(
                    bitmap,
                    null,
                    destinationRect,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
                if (cutGuides) {
                    page.canvas.drawRect(
                        destinationRect,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.GRAY
                            style = Paint.Style.STROKE
                            strokeWidth = 0.5f
                        },
                    )
                }
            }
            page.canvas.drawText(
                "Print at Actual size / 100%. Disable Fit to page.",
                16f,
                sheet.heightPoints - 16f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 9f
                },
            )
            document.finishPage(page)
            destination.parentFile?.mkdirs()
            destination.outputStream().buffered().use(document::writeTo)
        } finally {
            document.close()
            bitmap.recycle()
        }
        ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                check(renderer.pageCount == 1) { "PRINT_SHEET_VALIDATION_FAILED" }
                renderer.openPage(0).use { page ->
                    check(
                        page.width == sheet.widthPoints && page.height == sheet.heightPoints,
                    ) { "PRINT_SHEET_SIZE_MISMATCH" }
                }
            }
        }
    }

    private companion object {
        const val POINTS_PER_MM = 72.0 / 25.4
    }
}
