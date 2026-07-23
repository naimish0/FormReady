package com.rameshta.formready.core.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProEntitlementPolicyTest {
    @Test
    fun purchasedConfiguredProductGrantsEntitlement() {
        val decision = ProEntitlementPolicy.evaluate(
            listOf(PurchaseRecord(true, PurchaseRecordState.PURCHASED)),
        )

        assertTrue(decision.entitled)
        assertFalse(decision.pending)
    }

    @Test
    fun pendingPurchaseNeverGrantsEntitlement() {
        val decision = ProEntitlementPolicy.evaluate(
            listOf(PurchaseRecord(true, PurchaseRecordState.PENDING)),
        )

        assertFalse(decision.entitled)
        assertTrue(decision.pending)
    }

    @Test
    fun absentOrDifferentProductRevokesLocalDecision() {
        assertEquals(
            EntitlementDecision(entitled = false, pending = false),
            ProEntitlementPolicy.evaluate(emptyList()),
        )
        assertFalse(
            ProEntitlementPolicy.evaluate(
                listOf(PurchaseRecord(false, PurchaseRecordState.PURCHASED)),
            ).entitled,
        )
    }

    @Test
    fun proRaisesOnlyTheConvenienceBatchLimit() {
        assertEquals(10, ProBenefits.batchItemLimit(isEntitled = false))
        assertEquals(50, ProBenefits.batchItemLimit(isEntitled = true))
    }
}
