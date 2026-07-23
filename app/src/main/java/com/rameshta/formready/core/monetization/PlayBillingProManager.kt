package com.rameshta.formready.core.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.rameshta.formready.BuildConfig
import com.rameshta.formready.core.data.entitlement.ProEntitlementStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class PlayBillingProManager @Inject constructor(
    @ApplicationContext context: Context,
    private val entitlementStore: ProEntitlementStore,
) : ProManager, PurchasesUpdatedListener {
    private val productId = BuildConfig.PRO_PRODUCT_ID.trim()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(
        ProState(
            isConfigured = productId.isNotEmpty(),
            status = if (productId.isEmpty()) {
                ProStatus.UNCONFIGURED
            } else {
                ProStatus.CONNECTING
            },
        ),
    )
    override val state: StateFlow<ProState> = mutableState.asStateFlow()
    private var started = false
    private var connecting = false
    private val acknowledgingTokens = mutableSetOf<String>()

    private val billingClient: BillingClient? = if (productId.isEmpty()) {
        null
    } else {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .enableAutoServiceReconnection()
            .build()
    }

    override fun start() {
        if (started) return
        started = true
        scope.launch {
            entitlementStore.entitled.collectLatest { entitled ->
                mutableState.update { current ->
                    current.copy(
                        isEntitled = entitled,
                        status = when {
                            entitled -> ProStatus.PURCHASED
                            current.status == ProStatus.PURCHASED &&
                                current.formattedPrice != null -> ProStatus.AVAILABLE
                            current.status == ProStatus.PURCHASED -> ProStatus.CONNECTING
                            else -> current.status
                        },
                    )
                }
            }
        }
        connectIfNeeded()
    }

    override fun refresh() {
        if (productId.isEmpty()) return
        val client = billingClient ?: return
        if (client.isReady) {
            queryProductAndPurchases()
        } else {
            connectIfNeeded()
        }
    }

    override fun purchase(activity: Activity) {
        if (productId.isEmpty() || mutableState.value.isEntitled) return
        val client = billingClient ?: return
        if (!client.isReady) {
            mutableState.update { it.copy(status = ProStatus.OFFLINE) }
            connectIfNeeded()
            return
        }
        queryProductDetails { details ->
            val currentDetails = details ?: return@queryProductDetails
            val offer = currentDetails.oneTimePurchaseOfferDetailsList?.firstOrNull()
                ?: return@queryProductDetails mutableState.update {
                    it.copy(status = ProStatus.ERROR)
                }
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(currentDetails)
                .apply { offer.offerToken?.let(::setOfferToken) }
                .build()
            val result = client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .build(),
            )
            when (result.responseCode) {
                BillingResponseCode.OK -> Unit
                BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
                BillingResponseCode.SERVICE_DISCONNECTED,
                BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingResponseCode.NETWORK_ERROR,
                BillingResponseCode.BILLING_UNAVAILABLE,
                -> mutableState.update { it.copy(status = ProStatus.OFFLINE) }
                else -> mutableState.update { it.copy(status = ProStatus.ERROR) }
            }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?,
    ) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> processPurchases(purchases.orEmpty(), authoritative = false)
            BillingResponseCode.USER_CANCELED ->
                mutableState.update { it.copy(status = ProStatus.CANCELLED) }
            BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.NETWORK_ERROR,
            BillingResponseCode.BILLING_UNAVAILABLE,
            -> mutableState.update { it.copy(status = ProStatus.OFFLINE) }
            else -> mutableState.update { it.copy(status = ProStatus.ERROR) }
        }
    }

    private fun connectIfNeeded() {
        val client = billingClient ?: return
        if (client.isReady) {
            queryProductAndPurchases()
            return
        }
        if (connecting) return
        connecting = true
        mutableState.update { it.copy(status = ProStatus.CONNECTING) }
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingResponseCode.OK) {
                        queryProductAndPurchases()
                    } else {
                        mutableState.update { it.copy(status = ProStatus.OFFLINE) }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    mutableState.update { it.copy(status = ProStatus.OFFLINE) }
                }
            },
        )
    }

    private fun queryProductAndPurchases() {
        queryProductDetails()
        queryPurchases()
    }

    private fun queryProductDetails(onResult: ((ProductDetails?) -> Unit)? = null) {
        val client = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            val details = if (result.responseCode == BillingResponseCode.OK) {
                detailsResult.productDetailsList.singleOrNull { it.productId == productId }
            } else {
                null
            }
            if (details != null) {
                val price = details.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()
                    ?.formattedPrice
                mutableState.update { current ->
                    current.copy(
                        formattedPrice = price,
                        status = when {
                            current.isEntitled || current.status == ProStatus.PURCHASED ->
                                ProStatus.PURCHASED
                            current.status == ProStatus.PENDING -> ProStatus.PENDING
                            else -> ProStatus.AVAILABLE
                        },
                    )
                }
            } else if (result.responseCode != BillingResponseCode.OK) {
                mutableState.update { it.copy(status = ProStatus.OFFLINE) }
            } else {
                mutableState.update { it.copy(status = ProStatus.ERROR) }
            }
            onResult?.invoke(details)
        }
    }

    private fun queryPurchases() {
        val client = billingClient ?: return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                processPurchases(purchases, authoritative = true)
            } else {
                mutableState.update { it.copy(status = ProStatus.OFFLINE) }
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>, authoritative: Boolean) {
        val relevant = purchases.filter { productId in it.products }
        val decision = ProEntitlementPolicy.evaluate(
            relevant.mapNotNull { purchase ->
                PurchaseRecord(
                    includesConfiguredProduct = true,
                    state = when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> PurchaseRecordState.PURCHASED
                        Purchase.PurchaseState.PENDING -> PurchaseRecordState.PENDING
                        else -> return@mapNotNull null
                    },
                )
            },
        )
        val unacknowledged = relevant.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
        }
        if (decision.entitled) {
            scope.launch {
                entitlementStore.setEntitled(true)
                unacknowledged.forEach(::acknowledge)
            }
        } else if (authoritative) {
            scope.launch { entitlementStore.setEntitled(false) }
        }
        mutableState.update { current ->
            current.copy(
                status = when {
                    decision.entitled -> ProStatus.PURCHASED
                    decision.pending -> ProStatus.PENDING
                    current.formattedPrice != null -> ProStatus.AVAILABLE
                    else -> ProStatus.CONNECTING
                },
            )
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val client = billingClient ?: return
        if (!acknowledgingTokens.add(purchase.purchaseToken)) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        ) { result ->
            acknowledgingTokens.remove(purchase.purchaseToken)
            if (result.responseCode != BillingResponseCode.OK) {
                mutableState.update { it.copy(status = ProStatus.OFFLINE) }
            }
        }
    }
}
