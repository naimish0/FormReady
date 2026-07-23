package com.rameshta.formready.core.model

import kotlin.math.roundToInt

enum class ByteUnit(
    val bytesPerUnit: Long,
    val label: String,
) {
    KB(1_000L, "KB"),
    MB(1_000_000L, "MB"),
    KIB(1_024L, "KiB"),
    MIB(1_048_576L, "MiB"),
}

enum class PhysicalUnit {
    MILLIMETRES,
    CENTIMETRES,
    INCHES,
}

enum class DimensionRule {
    EXACT,
    MAXIMUM,
}

enum class DimensionInputMode {
    PIXELS,
    PHYSICAL,
}

enum class CropMode {
    CROP_FILL,
    FIT_PAD,
}

data class PhotoRequirements(
    val allowedFormats: Set<OutputFormat>,
    val widthPx: Int?,
    val heightPx: Int?,
    val dimensionRule: DimensionRule = DimensionRule.EXACT,
    val minimumBytes: Long? = null,
    val maximumBytes: Long? = null,
    val dpi: Int? = null,
    val cropMode: CropMode = CropMode.CROP_FILL,
    val backgroundArgb: Int = -1,
    val safetyMarginBytes: Long? = null,
) {
    init {
        require(allowedFormats.isNotEmpty())
        require(allowedFormats.all { it == OutputFormat.JPEG || it == OutputFormat.PNG })
        require(widthPx == null || widthPx > 0)
        require(heightPx == null || heightPx > 0)
        require((widthPx == null) == (heightPx == null))
        require(minimumBytes == null || minimumBytes >= 0)
        require(maximumBytes == null || maximumBytes > 0)
        require(minimumBytes == null || maximumBytes == null || minimumBytes <= maximumBytes)
        require(dpi == null || dpi in 1..2_400)
        require(safetyMarginBytes == null || safetyMarginBytes >= 0)
    }

    fun encodingByteCap(): Long? {
        val cap = maximumBytes ?: return null
        val requestedMargin = safetyMarginBytes ?: defaultSafetyMargin(cap)
        val margin = requestedMargin.coerceAtMost(cap - 1)
        val guardedCap = cap - margin
        return minimumBytes?.let { minimum -> guardedCap.coerceAtLeast(minimum) } ?: guardedCap
    }
}

data class PhotoFileMetadata(
    val format: OutputFormat,
    val widthPx: Int,
    val heightPx: Int,
    val byteCount: Long,
    val dpi: Int?,
    val hasAlpha: Boolean,
    val isSrgb: Boolean = true,
)

data class GenericPhotoPreset(
    val id: String,
    val widthPx: Int,
    val heightPx: Int,
    val dpi: Int?,
    val maximumValue: Long,
    val byteUnit: ByteUnit,
    val outputFormat: OutputFormat,
    val cropMode: CropMode,
    val physicalWidth: Double? = null,
    val physicalHeight: Double? = null,
    val physicalUnit: PhysicalUnit? = null,
)

object PhotoPresetCatalog {
    val presets = listOf(
        GenericPhotoPreset(
            id = "portrait-600x800-200kb",
            widthPx = 600,
            heightPx = 800,
            dpi = 300,
            maximumValue = 200,
            byteUnit = ByteUnit.KB,
            outputFormat = OutputFormat.JPEG,
            cropMode = CropMode.CROP_FILL,
        ),
        GenericPhotoPreset(
            id = "photo-35x45mm-300dpi",
            widthPx = RequirementConversions.pixels(
                35.0,
                PhysicalUnit.MILLIMETRES,
                300,
            ),
            heightPx = RequirementConversions.pixels(
                45.0,
                PhysicalUnit.MILLIMETRES,
                300,
            ),
            dpi = 300,
            maximumValue = 500,
            byteUnit = ByteUnit.KB,
            outputFormat = OutputFormat.JPEG,
            cropMode = CropMode.CROP_FILL,
            physicalWidth = 35.0,
            physicalHeight = 45.0,
            physicalUnit = PhysicalUnit.MILLIMETRES,
        ),
        GenericPhotoPreset(
            id = "photo-2x2in-300dpi",
            widthPx = RequirementConversions.pixels(2.0, PhysicalUnit.INCHES, 300),
            heightPx = RequirementConversions.pixels(2.0, PhysicalUnit.INCHES, 300),
            dpi = 300,
            maximumValue = 500,
            byteUnit = ByteUnit.KB,
            outputFormat = OutputFormat.JPEG,
            cropMode = CropMode.CROP_FILL,
            physicalWidth = 2.0,
            physicalHeight = 2.0,
            physicalUnit = PhysicalUnit.INCHES,
        ),
    )

    fun find(id: String): GenericPhotoPreset? = presets.firstOrNull { it.id == id }
}

object RequirementConversions {
    fun bytes(value: Long, unit: ByteUnit): Long = Math.multiplyExact(value, unit.bytesPerUnit)

    fun pixels(value: Double, unit: PhysicalUnit, dpi: Int): Int {
        require(value > 0.0)
        require(dpi > 0)
        val inches = when (unit) {
            PhysicalUnit.MILLIMETRES -> value / 25.4
            PhysicalUnit.CENTIMETRES -> value / 2.54
            PhysicalUnit.INCHES -> value
        }
        return (inches * dpi).roundToInt()
    }
}

fun defaultSafetyMargin(byteCap: Long): Long {
    require(byteCap > 0)
    return minOf(4_096L, byteCap / 100L)
}

object PhotoRequirementValidator {
    const val RULE_FORMAT = "photo.format"
    const val RULE_DIMENSIONS = "photo.dimensions"
    const val RULE_MAXIMUM_BYTES = "photo.maximum_bytes"
    const val RULE_MINIMUM_BYTES = "photo.minimum_bytes"
    const val RULE_DPI = "photo.dpi"
    const val RULE_QUALITY_GUARD = "photo.quality_guard"
    const val RULE_UPSCALING = "photo.upscaling"
    const val RULE_COLOR_SPACE = "photo.color_space"

    fun validate(
        metadata: PhotoFileMetadata,
        requirements: PhotoRequirements,
        jpegQuality: Int? = null,
        wasSubstantiallyUpscaled: Boolean = false,
    ): List<ValidationRuleResult> = buildList {
        add(
            result(
                ruleId = RULE_FORMAT,
                passes = metadata.format in requirements.allowedFormats,
                expected = requirements.allowedFormats.joinToString { it.name },
                actual = metadata.format.name,
                explanation = "The exported format must be one of the selected formats.",
                fixAction = "Choose an allowed output format.",
            ),
        )
        add(
            result(
                ruleId = RULE_COLOR_SPACE,
                passes = metadata.isSrgb,
                expected = "sRGB",
                actual = if (metadata.isSrgb) "sRGB" else "Non-sRGB or unknown",
                explanation = "The reopened output colour space is checked for portal compatibility.",
                fixAction = "Convert the output to sRGB.",
            ),
        )
        if (requirements.widthPx != null && requirements.heightPx != null) {
            val passes = when (requirements.dimensionRule) {
                DimensionRule.EXACT ->
                    metadata.widthPx == requirements.widthPx &&
                        metadata.heightPx == requirements.heightPx
                DimensionRule.MAXIMUM ->
                    metadata.widthPx <= requirements.widthPx &&
                        metadata.heightPx <= requirements.heightPx
            }
            add(
                result(
                    ruleId = RULE_DIMENSIONS,
                    passes = passes,
                    expected = "${requirements.dimensionRule.name}: " +
                        "${requirements.widthPx} × ${requirements.heightPx} px",
                    actual = "${metadata.widthPx} × ${metadata.heightPx} px",
                    explanation = "Pixel dimensions are checked on the reopened output.",
                    fixAction = "Adjust the output dimensions.",
                ),
            )
        }
        requirements.maximumBytes?.let { maximum ->
            add(
                result(
                    ruleId = RULE_MAXIMUM_BYTES,
                    passes = metadata.byteCount <= maximum,
                    expected = "At most $maximum bytes",
                    actual = "${metadata.byteCount} bytes",
                    explanation = "The actual encoded file must not exceed the byte cap.",
                    fixAction = "Reduce quality or dimensions.",
                ),
            )
        }
        requirements.minimumBytes?.let { minimum ->
            add(
                result(
                    ruleId = RULE_MINIMUM_BYTES,
                    passes = metadata.byteCount >= minimum,
                    expected = "At least $minimum bytes",
                    actual = "${metadata.byteCount} bytes",
                    explanation = "The actual encoded file must meet the minimum byte size.",
                    fixAction = "Increase legitimate quality when the other rules permit it.",
                ),
            )
        }
        requirements.dpi?.let { requiredDpi ->
            add(
                result(
                    ruleId = RULE_DPI,
                    passes = metadata.dpi == requiredDpi,
                    expected = "$requiredDpi DPI",
                    actual = metadata.dpi?.let { "$it DPI" } ?: "Missing",
                    explanation = "Embedded DPI metadata is validated separately from pixels.",
                    fixAction = "Use JPEG output with supported DPI metadata.",
                ),
            )
        }
        jpegQuality?.takeIf { it < TargetSizePolicy.QUALITY_GUARD }?.let { quality ->
            add(
                ValidationRuleResult(
                    ruleId = RULE_QUALITY_GUARD,
                    outcome = ValidationOutcome.WARNING,
                    expected = "Quality ${TargetSizePolicy.QUALITY_GUARD} or higher",
                    actual = "Quality $quality",
                    explanation = "The byte target required visibly stronger JPEG compression.",
                    fixAction = "Allow a larger file or smaller dimensions.",
                    isHardRule = false,
                ),
            )
        }
        if (wasSubstantiallyUpscaled) {
            add(
                ValidationRuleResult(
                    ruleId = RULE_UPSCALING,
                    outcome = ValidationOutcome.WARNING,
                    expected = "Little or no upscaling",
                    actual = "Substantial upscaling",
                    explanation = "The requested dimensions exceed the source resolution.",
                    fixAction = "Choose a higher-resolution source.",
                    isHardRule = false,
                ),
            )
        }
    }

    private fun result(
        ruleId: String,
        passes: Boolean,
        expected: String,
        actual: String,
        explanation: String,
        fixAction: String,
    ) = ValidationRuleResult(
        ruleId = ruleId,
        outcome = if (passes) ValidationOutcome.PASS else ValidationOutcome.FAIL,
        expected = expected,
        actual = actual,
        explanation = explanation,
        fixAction = if (passes) null else fixAction,
        isHardRule = true,
    )
}

object TargetSizePolicy {
    const val MAXIMUM_FULL_ENCODE_PASSES = 6
    const val QUALITY_GUARD = 55
    const val HARD_QUALITY_FLOOR = 25
}
