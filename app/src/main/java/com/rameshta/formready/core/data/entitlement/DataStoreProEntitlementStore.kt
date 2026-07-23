package com.rameshta.formready.core.data.entitlement

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.proEntitlementDataStore by preferencesDataStore(name = "pro_entitlement")

class DataStoreProEntitlementStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ProEntitlementStore {
    override val entitled: Flow<Boolean> = context.proEntitlementDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> preferences[ENTITLED] ?: false }

    override suspend fun setEntitled(entitled: Boolean) {
        context.proEntitlementDataStore.edit { preferences ->
            preferences[ENTITLED] = entitled
        }
    }

    private companion object {
        val ENTITLED = booleanPreferencesKey("is_entitled")
    }
}
