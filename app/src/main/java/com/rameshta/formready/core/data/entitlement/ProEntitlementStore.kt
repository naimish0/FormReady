package com.rameshta.formready.core.data.entitlement

import kotlinx.coroutines.flow.Flow

interface ProEntitlementStore {
    val entitled: Flow<Boolean>

    suspend fun setEntitled(entitled: Boolean)
}
