package com.rameshta.formready.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequirementModelsTest {
    @Test
    fun decimalAndBinaryUnits_areExplicit() {
        assertEquals(200_000L, RequirementConversions.bytes(200, ByteUnit.KB))
        assertEquals(204_800L, RequirementConversions.bytes(200, ByteUnit.KIB))
    }

    @Test
    fun physicalDimensions_roundUsingDpi() {
        assertEquals(413, RequirementConversions.pixels(35.0, PhysicalUnit.MILLIMETRES, 300))
        assertEquals(531, RequirementConversions.pixels(45.0, PhysicalUnit.MILLIMETRES, 300))
        assertEquals(300, RequirementConversions.pixels(2.54, PhysicalUnit.CENTIMETRES, 300))
        assertEquals(600, RequirementConversions.pixels(2.0, PhysicalUnit.INCHES, 300))
    }

    @Test
    fun safetyMargin_isOnePercentCappedAtFourKiB() {
        assertEquals(2_000L, defaultSafetyMargin(200_000L))
        assertEquals(4_096L, defaultSafetyMargin(1_000_000L))
    }

    @Test
    fun safetyMargin_neverPushesEncodingCapBelowMinimum() {
        val requirements = PhotoRequirements(
            allowedFormats = setOf(OutputFormat.JPEG),
            widthPx = 600,
            heightPx = 800,
            minimumBytes = 199_000,
            maximumBytes = 200_000,
        )
        assertEquals(199_000L, requirements.encodingByteCap())
    }

    @Test
    fun hardFailureMakesOutputNotReady() {
        val rules = PhotoRequirementValidator.validate(
            metadata = PhotoFileMetadata(
                format = OutputFormat.JPEG,
                widthPx = 599,
                heightPx = 800,
                byteCount = 180_000,
                dpi = 300,
                hasAlpha = false,
            ),
            requirements = PhotoRequirements(
                allowedFormats = setOf(OutputFormat.JPEG),
                widthPx = 600,
                heightPx = 800,
                maximumBytes = 200_000,
                dpi = 300,
            ),
        )
        assertEquals(Readiness.NOT_READY, rules.readiness())
        assertTrue(rules.any { it.ruleId == PhotoRequirementValidator.RULE_DIMENSIONS })
    }

    @Test
    fun maximumDimensionRuleAcceptsSmallerOutput() {
        val rules = PhotoRequirementValidator.validate(
            metadata = PhotoFileMetadata(
                format = OutputFormat.PNG,
                widthPx = 400,
                heightPx = 500,
                byteCount = 90_000,
                dpi = null,
                hasAlpha = true,
            ),
            requirements = PhotoRequirements(
                allowedFormats = setOf(OutputFormat.PNG),
                widthPx = 600,
                heightPx = 800,
                dimensionRule = DimensionRule.MAXIMUM,
                maximumBytes = 100_000,
            ),
        )

        assertEquals(Readiness.READY, rules.readiness())
    }

    @Test
    fun qualityAndUpscalingAreAdvisoryWarnings() {
        val rules = PhotoRequirementValidator.validate(
            metadata = PhotoFileMetadata(
                format = OutputFormat.JPEG,
                widthPx = 600,
                heightPx = 800,
                byteCount = 90_000,
                dpi = 300,
                hasAlpha = false,
            ),
            requirements = PhotoRequirements(
                allowedFormats = setOf(OutputFormat.JPEG),
                widthPx = 600,
                heightPx = 800,
                maximumBytes = 100_000,
                dpi = 300,
            ),
            jpegQuality = TargetSizePolicy.QUALITY_GUARD - 1,
            wasSubstantiallyUpscaled = true,
        )

        assertEquals(Readiness.READY_WITH_WARNINGS, rules.readiness())
        assertEquals(2, rules.count { it.outcome == ValidationOutcome.WARNING })
    }

    @Test
    fun genericPhysicalPresetsUseExactConversions() {
        val metric = requireNotNull(PhotoPresetCatalog.find("photo-35x45mm-300dpi"))
        val inches = requireNotNull(PhotoPresetCatalog.find("photo-2x2in-300dpi"))

        assertEquals(413, metric.widthPx)
        assertEquals(531, metric.heightPx)
        assertEquals(600, inches.widthPx)
        assertEquals(600, inches.heightPx)
    }
}
