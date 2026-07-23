package com.rameshta.formready.core.monetization

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

enum class ProStatus {
    UNCONFIGURED,
    CONNECTING,
    AVAILABLE,
    PENDING,
    PURCHASED,
    CANCELLED,
    OFFLINE,
    ERROR,
}

data class ProState(
    val isConfigured: Boolean = false,
    val isEntitled: Boolean = false,
    val status: ProStatus = ProStatus.UNCONFIGURED,
    val formattedPrice: String? = null,
)

interface ProManager {
    val state: StateFlow<ProState>

    fun start()

    fun refresh()

    fun purchase(activity: Activity)
}

enum class PurchaseRecordState {
    PURCHASED,
    PENDING,
}

data class PurchaseRecord(
    val includesConfiguredProduct: Boolean,
    val state: PurchaseRecordState,
)

data class EntitlementDecision(
    val entitled: Boolean,
    val pending: Boolean,
)

object ProEntitlementPolicy {
    fun evaluate(records: List<PurchaseRecord>): EntitlementDecision = EntitlementDecision(
        entitled = records.any {
            it.includesConfiguredProduct && it.state == PurchaseRecordState.PURCHASED
        },
        pending = records.any {
            it.includesConfiguredProduct && it.state == PurchaseRecordState.PENDING
        },
    )
}

object ProBenefits {
    const val FREE_BATCH_ITEMS = 10
    const val PRO_BATCH_ITEMS = 50

    fun batchItemLimit(isEntitled: Boolean): Int =
        if (isEntitled) PRO_BATCH_ITEMS else FREE_BATCH_ITEMS
}
