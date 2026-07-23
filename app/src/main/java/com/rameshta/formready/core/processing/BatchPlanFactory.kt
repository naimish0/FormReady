package com.rameshta.formready.core.processing

import com.rameshta.formready.core.model.BatchRequirements
import com.rameshta.formready.core.model.CropMode
import com.rameshta.formready.core.model.NormalizedTransform
import com.rameshta.formready.core.model.OutputSpecification
import com.rameshta.formready.core.model.ProcessingPlan
import java.util.UUID

object BatchPlanFactory {
    fun create(
        jobId: UUID,
        metadata: ImageMetadata,
        requirements: BatchRequirements,
    ): ProcessingPlan {
        val transforms = listOf(
            if (requirements.cropMode == CropMode.FIT_PAD) {
                NormalizedTransform.FitPad(requirements.backgroundArgb)
            } else {
                PhotoCropCalculator.calculate(
                    sourceWidthPx = metadata.widthPx,
                    sourceHeightPx = metadata.heightPx,
                    targetWidthPx = requirements.widthPx,
                    targetHeightPx = requirements.heightPx,
                    zoom = 1f,
                    panX = 0f,
                    panY = 0f,
                    quarterTurns = 0,
                )
            },
        )
        return ProcessingPlan(
            jobId = jobId,
            transforms = transforms,
            output = OutputSpecification(
                format = requirements.outputFormat,
                widthPx = requirements.widthPx,
                heightPx = requirements.heightPx,
                maximumBytes = requirements.maximumBytes,
                dpi = requirements.dpi,
                backgroundArgb = requirements.backgroundArgb,
            ),
            hardRuleIds = buildSet {
                add("photo.format")
                add("photo.dimensions")
                add("photo.maximum_bytes")
                add("photo.color_space")
                if (requirements.dpi != null) add("photo.dpi")
            },
            advisoryRuleIds = setOf("photo.quality_guard", "photo.upscaling"),
        )
    }
}
