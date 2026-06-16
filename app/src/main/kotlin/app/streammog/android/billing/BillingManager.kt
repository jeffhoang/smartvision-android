package app.streammog.android.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import app.streammog.android.app.AppEntitlements
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging

class BillingManager(
    private val context: Context,
    private val activityProvider: () -> Activity?,
    private val diagnosticsStore: DiagnosticsLogging,
    private val onEntitlementsChanged: (AppEntitlements) -> Unit,
) {
    companion object {
        const val CREATOR_PRODUCT_ID = "creator_monthly"
        private const val PACKAGE_NAME = "app.streammog.android"
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases.orEmpty())
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    diagnosticsStore.log("Billing: connected", DiagnosticsEntry.Category.app)
                    refreshPurchases()
                } else {
                    diagnosticsStore.log("Billing: setup failed (${result.responseCode})", DiagnosticsEntry.Category.app)
                }
            }
            override fun onBillingServiceDisconnected() {
                diagnosticsStore.log("Billing: service disconnected", DiagnosticsEntry.Category.app)
            }
        })
    }

    fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    fun launchUpgradeFlow() {
        val activity = activityProvider() ?: return
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(CREATOR_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        ) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList.isEmpty()) {
                diagnosticsStore.log("Billing: product details unavailable (${result.responseCode})", DiagnosticsEntry.Category.app)
                return@queryProductDetailsAsync
            }
            val productDetails = productDetailsList.first()
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                ?: run {
                    diagnosticsStore.log("Billing: no offer token available", DiagnosticsEntry.Category.app)
                    return@queryProductDetailsAsync
                }
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()
            billingClient.launchBillingFlow(activity, billingFlowParams)
        }
    }

    fun openManageSubscriptions() {
        val url = "https://play.google.com/store/account/subscriptions" +
            "?sku=$CREATOR_PRODUCT_ID&package=$PACKAGE_NAME"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {}
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val hasCreator = purchases.any { purchase ->
            purchase.products.contains(CREATOR_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        onEntitlementsChanged(if (hasCreator) AppEntitlements.creator else AppEntitlements.free)

        purchases
            .filter { !it.isAcknowledged && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach { purchase ->
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ) { ackResult ->
                    if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        diagnosticsStore.log("Billing: acknowledged ${purchase.orderId}", DiagnosticsEntry.Category.app)
                    }
                }
            }
    }
}
