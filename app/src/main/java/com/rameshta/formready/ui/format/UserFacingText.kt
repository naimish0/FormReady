package com.rameshta.formready.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rameshta.formready.R
import java.text.DecimalFormat

fun readableFileSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes < 1_000L -> "< 1 KB"
        safeBytes < 1_000_000L -> {
            "${DecimalFormat("0.#").format(safeBytes / 1_000.0)} KB"
        }
        else -> {
            "${DecimalFormat("0.#").format(safeBytes / 1_000_000.0)} MB"
        }
    }
}

fun readableValidationValue(value: String): String =
    RAW_BYTE_VALUE.replace(value) { match ->
        readableFileSize(match.groupValues[1].toLong())
    }.replace("Substantial upscaling", "The image was enlarged significantly")
        .replace("Little or no upscaling", "Avoid noticeably enlarging the image")

fun readableGuidance(value: String): String =
    readableValidationValue(value)
        .replace(
            "Embedded DPI metadata is validated separately from pixels.",
            "Print resolution is checked separately from image dimensions.",
        )
        .replace(
            "Use JPEG output with supported DPI metadata.",
            "Choose JPEG when the application website requires a DPI value.",
        )
        .replace("upscaling", "enlarging", ignoreCase = true)
        .replace("encoded", "prepared", ignoreCase = true)

@Composable
fun userFacingError(errorCode: String): String = stringResource(errorMessage(errorCode))

@StringRes
private fun errorMessage(errorCode: String): Int {
    val normalized = errorCode.uppercase()
    return when {
        "INVALID_REQUIREMENT" in normalized -> R.string.error_check_requirements
        "CROP" in normalized || "PAGES_REQUIRED" in normalized ->
            R.string.error_check_selection
        "LIMIT" in normalized -> R.string.error_choose_fewer_files
        "OCR" in normalized -> R.string.error_extract_text
        "SCANNER_UNAVAILABLE" in normalized -> R.string.error_scanner_unavailable
        "SOURCE" in normalized || "INPUT" in normalized -> R.string.error_open_source
        "DESTINATION" in normalized || "WRITE" in normalized || "SAVE" in normalized ->
            R.string.error_save_file
        "NO_COMPATIBLE_APP" in normalized -> R.string.error_no_compatible_app
        "CANCEL" in normalized -> R.string.error_processing_cancelled
        "MASK_STROKE_LIMIT" in normalized -> R.string.error_too_many_mask_edits
        "SEGMENT" in normalized -> R.string.error_background_tool_unavailable
        "PRINT" in normalized -> R.string.error_print_sheet
        "PURCHASE" in normalized || "BILLING" in normalized -> R.string.error_purchase
        else -> R.string.error_generic_recovery
    }
}

private val RAW_BYTE_VALUE = Regex("""(\d+)\s+bytes?""", RegexOption.IGNORE_CASE)
