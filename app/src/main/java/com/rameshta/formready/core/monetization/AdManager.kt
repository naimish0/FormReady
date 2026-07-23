package com.rameshta.formready.core.monetization

/**
 * Boundary for optional future advertising. The launch build binds [NoOpAdManager], so no consent
 * or ad SDK can initialize, request data, or affect a preparation workflow.
 */
interface AdManager {
    val isEnabled: Boolean

    fun onAppSessionStarted()

    fun onSuccessfulExport()

    fun onNaturalBreakAfterResult()
}

class NoOpAdManager : AdManager {
    override val isEnabled: Boolean = false

    override fun onAppSessionStarted() = Unit

    override fun onSuccessfulExport() = Unit

    override fun onNaturalBreakAfterResult() = Unit
}
