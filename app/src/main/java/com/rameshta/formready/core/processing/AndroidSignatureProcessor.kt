package com.rameshta.formready.core.processing

import com.rameshta.formready.core.model.ProcessingPlan
import java.io.File
import javax.inject.Inject

class AndroidSignatureProcessor @Inject constructor(
    private val imageTransformEngine: ImageTransformEngine,
) : SignatureProcessor {
    override suspend fun prepare(
        input: File,
        destination: File,
        plan: ProcessingPlan,
    ): PreparedPhoto {
        require(plan.signatureOptions != null)
        return imageTransformEngine.prepare(input, destination, plan)
    }
}
