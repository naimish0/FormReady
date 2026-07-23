package com.rameshta.formready.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rameshta.formready.core.processing.OcrEngine
import com.rameshta.formready.core.processing.OcrScript
import com.rameshta.formready.core.processing.PdfEngine
import com.rameshta.formready.core.processing.PrivateInputStager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScannerPageUi(
    val id: String,
    val number: Int,
)

data class ScannerUiState(
    val pages: List<ScannerPageUi> = emptyList(),
    val isBusy: Boolean = false,
    val extractedText: String = "",
    val errorCode: String? = null,
    val editingPageIndex: Int? = null,
    val editingPreview: ImageBitmap? = null,
)

enum class ScanFilter {
    COLOUR,
    GRAYSCALE,
    BLACK_AND_WHITE,
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val ocrEngine: OcrEngine,
    private val pdfEngine: PdfEngine,
) : ViewModel() {
    private val sessionId = savedStateHandle.get<String>(SESSION_ID_KEY)
        ?.let(UUID::fromString)
        ?: UUID.randomUUID().also { savedStateHandle[SESSION_ID_KEY] = it.toString() }
    private val sessionDirectory = File(context.noBackupFilesDir, "scanner-sessions/$sessionId")
    private val pages = restorePages()
    private var pendingCapture: File? = null
    private val mutableState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = mutableState.asStateFlow()

    init {
        if (pages.isNotEmpty()) publish()
    }

    fun addPages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorCode = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    require(pages.size + uris.size <= MAX_PAGES) { "SCANNER_PAGE_LIMIT" }
                    sessionDirectory.mkdirs()
                    uris.forEach { uri ->
                        val id = UUID.randomUUID().toString()
                        val destination = File(sessionDirectory, "$id.jpg")
                        try {
                            copyBounded(uri, destination)
                            validateImage(destination)
                        } catch (error: Exception) {
                            destination.delete()
                            throw error
                        }
                        pages += Page(id, destination)
                    }
                }
            }.onSuccess {
                publish()
            }.onFailure { error ->
                mutableState.update {
                    it.copy(isBusy = false, errorCode = error.message ?: "SCANNER_IMPORT_FAILED")
                }
            }
        }
    }

    fun createCaptureUri(): Uri {
        val directory = File(context.cacheDir, "scanner-captures").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        pendingCapture = file
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun completeCapture(success: Boolean) {
        val file = pendingCapture.also { pendingCapture = null } ?: return
        if (success && file.length() > 0L) addPages(listOf(Uri.fromFile(file))) else file.delete()
    }

    fun movePage(index: Int, delta: Int) {
        val reordered = ScannerPageOrdering.move(pages, index, delta)
        if (reordered === pages) return
        pages.clear()
        pages.addAll(reordered)
        publish()
    }

    fun deletePage(index: Int) {
        if (index !in pages.indices) return
        pages.removeAt(index).file.delete()
        publish()
    }

    fun rotatePage(index: Int) {
        val page = pages.getOrNull(index) ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorCode = null) }
            runCatching { rotateClockwise(page.file) }
                .onSuccess { publish() }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(isBusy = false, errorCode = error.message ?: "SCANNER_ROTATE_FAILED")
                    }
                }
        }
    }

    fun editPage(index: Int) {
        val page = pages.getOrNull(index) ?: return
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { decodeSampled(page.file, 1_200) }
            mutableState.update {
                it.copy(
                    editingPageIndex = index,
                    editingPreview = preview?.asImageBitmap(),
                    errorCode = if (preview == null) "SCANNER_CORRUPT_PAGE" else null,
                )
            }
        }
    }

    fun cancelEdit() {
        mutableState.update { it.copy(editingPageIndex = null, editingPreview = null) }
    }

    fun applyManualEdit(corners: List<Float>, filter: ScanFilter) {
        val index = mutableState.value.editingPageIndex ?: return
        val page = pages.getOrNull(index) ?: return
        if (!ManualCropGeometry.isValid(corners)) {
            mutableState.update { it.copy(errorCode = "INVALID_CROP_CORNERS") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorCode = null) }
            runCatching { correctPerspective(page.file, corners, filter) }
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            isBusy = false,
                            editingPageIndex = null,
                            editingPreview = null,
                            extractedText = "",
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(isBusy = false, errorCode = error.message ?: "SCAN_EDIT_FAILED")
                    }
                }
        }
    }

    fun recognize(script: OcrScript) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorCode = null) }
            runCatching {
                pages.mapIndexed { index, page ->
                    val text = ocrEngine.recognize(page.file, script).trim()
                    "Page ${index + 1}\n$text"
                }.joinToString("\n\n")
            }.onSuccess { text ->
                mutableState.update { it.copy(isBusy = false, extractedText = text) }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(isBusy = false, errorCode = error.message ?: "OCR_FAILED")
                }
            }
        }
    }

    fun exportText(destination: Uri) {
        val text = mutableState.value.extractedText
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destination, "wt")?.bufferedWriter()
                    ?.use { it.write(text) } ?: error("EXPORT_DESTINATION_UNAVAILABLE")
            }.onFailure { error ->
                mutableState.update { it.copy(errorCode = error.message ?: "TEXT_EXPORT_FAILED") }
            }
        }
    }

    fun exportPdf(destination: Uri) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorCode = null) }
            val candidate = File(sessionDirectory, "candidate.pdf")
            runCatching {
                pdfEngine.imagesToPdf(pages.map(Page::file), candidate)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                        candidate.inputStream().buffered().use { it.copyTo(output) }
                    } ?: error("EXPORT_DESTINATION_UNAVAILABLE")
                }
            }.onSuccess {
                mutableState.update { it.copy(isBusy = false) }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(isBusy = false, errorCode = error.message ?: "PDF_EXPORT_FAILED")
                }
            }
            candidate.delete()
        }
    }

    fun reportScannerUnavailable() {
        mutableState.update { it.copy(errorCode = "SCANNER_UNAVAILABLE_USE_FALLBACK") }
    }

    private suspend fun rotateClockwise(file: File) = withContext(Dispatchers.IO) {
        val source = BitmapFactory.decodeFile(file.absolutePath) ?: error("CORRUPT_SCAN_PAGE")
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(90f) },
            true,
        )
        val temporary = File(file.parentFile, "${file.name}.part")
        try {
            temporary.outputStream().buffered().use { output ->
                check(rotated.compress(Bitmap.CompressFormat.JPEG, 94, output))
            }
            temporary.copyTo(file, overwrite = true)
        } finally {
            source.recycle()
            if (rotated !== source) rotated.recycle()
            temporary.delete()
        }
    }

    private suspend fun correctPerspective(
        file: File,
        corners: List<Float>,
        filter: ScanFilter,
    ) = withContext(Dispatchers.IO) {
        val source = decodeSampled(file, MANUAL_EDIT_MAX_EDGE) ?: error("CORRUPT_SCAN_PAGE")
        val sourcePoints = FloatArray(8) { index ->
            val coordinate = corners[index]
            if (index % 2 == 0) coordinate * source.width else coordinate * source.height
        }
        val width = (
            distance(sourcePoints, 0, 2) + distance(sourcePoints, 6, 4)
        ).div(2f).toInt().coerceIn(1, MANUAL_EDIT_MAX_EDGE)
        val height = (
            distance(sourcePoints, 0, 6) + distance(sourcePoints, 2, 4)
        ).div(2f).toInt().coerceIn(1, MANUAL_EDIT_MAX_EDGE)
        val destinationPoints = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat(),
        )
        val matrix = Matrix().apply {
            check(setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4)) {
                "INVALID_CROP_CORNERS"
            }
        }
        val corrected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(corrected).drawBitmap(
            source,
            matrix,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
        )
        applyFilter(corrected, filter)
        val temporary = File(file.parentFile, "${file.name}.part")
        try {
            temporary.outputStream().buffered().use { output ->
                check(corrected.compress(Bitmap.CompressFormat.JPEG, 94, output))
            }
            temporary.copyTo(file, overwrite = true)
        } finally {
            source.recycle()
            corrected.recycle()
            temporary.delete()
        }
    }

    private fun applyFilter(bitmap: Bitmap, filter: ScanFilter) {
        if (filter == ScanFilter.COLOUR) return
        val row = IntArray(bitmap.width)
        repeat(bitmap.height) { y ->
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            row.indices.forEach { index ->
                val colour = row[index]
                val luminance = (
                    0.299f * android.graphics.Color.red(colour) +
                        0.587f * android.graphics.Color.green(colour) +
                        0.114f * android.graphics.Color.blue(colour)
                ).toInt()
                val value = if (filter == ScanFilter.BLACK_AND_WHITE) {
                    if (luminance >= 160) 255 else 0
                } else {
                    luminance
                }
                row[index] = android.graphics.Color.rgb(value, value, value)
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }

    private fun decodeSampled(file: File, maximumEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= maximumEdge ||
            bounds.outHeight / (sample * 2) >= maximumEdge
        ) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun distance(points: FloatArray, first: Int, second: Int): Float {
        val dx = points[first] - points[second]
        val dy = points[first + 1] - points[second + 1]
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun copyBounded(uri: Uri, destination: File) {
        var total = pages.sumOf { it.file.length() }
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= PrivateInputStager.MAX_STAGED_INPUT_BYTES) {
                        "SCANNER_SESSION_TOO_LARGE"
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("SCANNER_SOURCE_UNAVAILABLE")
        check(destination.length() > 0L) { "SCANNER_EMPTY_PAGE" }
    }

    private fun validateImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "SCANNER_CORRUPT_PAGE" }
        check(bounds.outWidth.toLong() * bounds.outHeight <= MAX_PAGE_PIXELS) {
            "SCANNER_PAGE_DIMENSIONS_UNSAFE"
        }
    }

    private fun publish() {
        persistOrder()
        mutableState.update {
            it.copy(
                pages = pages.mapIndexed { index, page -> ScannerPageUi(page.id, index + 1) },
                isBusy = false,
                extractedText = "",
            )
        }
    }

    override fun onCleared() {
        pendingCapture?.delete()
    }

    private data class Page(val id: String, val file: File)

    private fun restorePages(): MutableList<Page> {
        val order = File(sessionDirectory, ORDER_FILE)
        if (!order.isFile) return mutableListOf()
        return order.readLines()
            .mapNotNull { id ->
                File(sessionDirectory, "$id.jpg").takeIf(File::isFile)?.let { Page(id, it) }
            }
            .toMutableList()
    }

    private fun persistOrder() {
        if (!sessionDirectory.exists()) return
        File(sessionDirectory, ORDER_FILE).writeText(pages.joinToString("\n", transform = Page::id))
        sessionDirectory.setLastModified(System.currentTimeMillis())
    }

    companion object {
        const val MAX_PAGES = 50
        private const val MAX_PAGE_PIXELS = 100_000_000L
        private const val MANUAL_EDIT_MAX_EDGE = 4_096
        private const val SESSION_ID_KEY = "scanner.session.id"
        private const val ORDER_FILE = "order.txt"
    }
}
