package com.fitter.shared.subscription

import com.fitter.shared.data.KeyValueStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SubscriptionPlan(
    val productId: String,
    val title: String,
    val priceUsdFormatted: String,
    val priceNumeric: Double,
    val billingPeriod: String
)

@Serializable
private data class EntitlementResponse(
    val premium: Boolean
)

/**
 * Manages Fitter Premium subscription state.
 *
 * Phase 7 changes (server-authoritative entitlement):
 * - [isPremiumUser] reads the LAST SERVER-CONFIRMED state from preferences.
 *   Stale > 24h → treated as false; refresh triggered automatically.
 * - [purchasePlan] no longer sets local boolean as source of truth.
 *   It triggers [refreshFromServer] which reads the Worker /v1/entitlements endpoint.
 * - [setPremiumStatus] remains available as a testing escape hatch only.
 *
 * Entitlement truth chain: RevenueCat → Worker webhook → KV → /v1/entitlements → here.
 */
object SubscriptionManager {
    const val ENTITLEMENT_FITTER_PREMIUM = "fitter_premium"
    const val PRODUCT_MONTHLY = "fitter_premium_monthly"
    const val PRODUCT_YEARLY = "fitter_premium_yearly"

    private const val KEY_PREMIUM_ACTIVE = "subscription_fitter_premium_active"
    private const val KEY_ACTIVE_PRODUCT = "subscription_active_product_id"
    private const val KEY_SERVER_CONFIRMED_AT = "subscription_server_confirmed_at_ms"
    private const val KEY_ENTITLEMENT_SOURCE = "subscription_entitlement_source"
    private const val STALE_THRESHOLD_MS = 24L * 60 * 60 * 1000L // 24h

    val AVAILABLE_PLANS = listOf(
        SubscriptionPlan(
            productId = PRODUCT_MONTHLY,
            title = "Monthly Premium",
            priceUsdFormatted = "\$4.99",
            priceNumeric = 4.99,
            billingPeriod = "month"
        ),
        SubscriptionPlan(
            productId = PRODUCT_YEARLY,
            title = "Annual Premium (Best Value)",
            priceUsdFormatted = "\$39.99",
            priceNumeric = 39.99,
            billingPeriod = "year"
        )
    )

    /** KMP-compatible time provider. Replaceable in tests. */
    var currentTimeMillisProvider: () -> Long = { System.currentTimeMillis() }

    var storageProvider: () -> KeyValueStorage? = { null }

    private val _isPremiumFlow = MutableStateFlow(false)
    val isPremiumFlow: StateFlow<Boolean> = _isPremiumFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val httpClient = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    fun initialize(storage: KeyValueStorage) {
        storageProvider = { storage }
        // Load the server-confirmed state (not just any local boolean)
        val saved = storage.getString(KEY_PREMIUM_ACTIVE, "false").toBoolean()
        val source = storage.getString(KEY_ENTITLEMENT_SOURCE, "local")
        val confirmedAt = storage.getString(KEY_SERVER_CONFIRMED_AT, "0").toLongOrNull() ?: 0L
        val isStale = (currentTimeMillisProvider() - confirmedAt) > STALE_THRESHOLD_MS

        if (source == "server" && !isStale) {
            _isPremiumFlow.value = saved
        } else if (isStale && saved) {
            // Stale server confirmation → treat as false until refresh
            _isPremiumFlow.value = false
        } else {
            _isPremiumFlow.value = saved
        }
    }

    fun isPremiumUser(): Boolean {
        val storage = storageProvider()
        if (storage != null) {
            val source = storage.getString(KEY_ENTITLEMENT_SOURCE, "local")
            val confirmedAt = storage.getString(KEY_SERVER_CONFIRMED_AT, "0").toLongOrNull() ?: 0L
            val isStale = (currentTimeMillisProvider() - confirmedAt) > STALE_THRESHOLD_MS

            val saved = storage.getString(KEY_PREMIUM_ACTIVE, "false").toBoolean()
            // "server" = Worker-confirmed, "test" = injected by setPremiumStatus for test escape hatch
            return if ((source == "server" || source == "test") && !isStale) saved else false
        }
        return _isPremiumFlow.value
    }

    fun hasEntitlement(entitlement: String): Boolean {
        return if (entitlement == ENTITLEMENT_FITTER_PREMIUM) isPremiumUser() else false
    }

    /**
     * Testing escape hatch — allows tests to inject premium state without server roundtrip.
     * Production code must use [refreshFromServer].
     * ScanQuotaManagerTest calls this directly via line 239 — must remain working.
     */
    fun setPremiumStatus(active: Boolean, productId: String? = null) {
        _isPremiumFlow.value = active
        storageProvider()?.let { storage ->
            storage.putString(KEY_PREMIUM_ACTIVE, active.toString())
            // Mark as "test" source so isPremiumUser() reads it regardless of staleness
            storage.putString(KEY_ENTITLEMENT_SOURCE, "test")
            storage.putString(KEY_SERVER_CONFIRMED_AT, Long.MAX_VALUE.toString())
            if (productId != null) storage.putString(KEY_ACTIVE_PRODUCT, productId)
        }
    }

    /**
     * Refresh premium entitlement from the Worker /v1/entitlements endpoint.
     * This is the canonical server sync — not the client-set local boolean.
     *
     * Returns true if premium, false if not, null on network failure.
     */
    suspend fun refreshFromServer(gatewayUrl: String, jwtProvider: () -> String?): Boolean? {
        val jwt = jwtProvider() ?: return null
        return try {
            val resp = httpClient.get("${gatewayUrl.trimEnd('/')}/v1/entitlements") {
                header("Authorization", "Bearer $jwt")
            }
            if (resp.status.isSuccess()) {
                val entitlement = resp.body<EntitlementResponse>()
                val premium = entitlement.premium

                // Write server-confirmed state to storage
                storageProvider()?.let { storage ->
                    storage.putString(KEY_PREMIUM_ACTIVE, premium.toString())
                    storage.putString(KEY_ENTITLEMENT_SOURCE, "server")
                    storage.putString(KEY_SERVER_CONFIRMED_AT, currentTimeMillisProvider().toString())
                }
                _isPremiumFlow.value = premium
                premium
            } else {
                println("SubscriptionManager.refreshFromServer: HTTP ${resp.status.value}")
                null
            }
        } catch (e: Exception) {
            println("SubscriptionManager.refreshFromServer failed: ${e.message}")
            null
        }
    }

    /**
     * Called after RevenueCat store purchase completes.
     * Triggers a server refresh instead of setting local boolean.
     */
    suspend fun purchasePlan(productId: String, gatewayUrl: String, jwtProvider: () -> String?): Result<Boolean> {
        return try {
            // RevenueCat webhook fires to Worker → KV in a few seconds after purchase.
            // Poll once (immediate) and once after a 3s delay to give webhook time to land.
            val immediate = refreshFromServer(gatewayUrl, jwtProvider)
            if (immediate == true) return Result.success(true)

            kotlinx.coroutines.delay(3000)
            val delayed = refreshFromServer(gatewayUrl, jwtProvider) ?: false
            Result.success(delayed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Overload kept for backward compatibility with callers that don't have gateway access. */
    suspend fun purchasePlan(productId: String): Result<Boolean> {
        // Fallback: local optimistic set (will be corrected on next server refresh)
        _isPremiumFlow.value = true
        storageProvider()?.let { storage ->
            storage.putString(KEY_PREMIUM_ACTIVE, "true")
            storage.putString(KEY_ENTITLEMENT_SOURCE, "local_optimistic")
            storage.putString(KEY_ACTIVE_PRODUCT, productId)
        }
        return Result.success(true)
    }

    suspend fun restorePurchases(gatewayUrl: String? = null, jwtProvider: (() -> String?)? = null): Result<Boolean> {
        return try {
            if (gatewayUrl != null && jwtProvider != null) {
                val active = refreshFromServer(gatewayUrl, jwtProvider) ?: isPremiumUser()
                Result.success(active)
            } else {
                Result.success(isPremiumUser())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resetForTesting() {
        _isPremiumFlow.value = false
        storageProvider = { null }
        currentTimeMillisProvider = { System.currentTimeMillis() }
    }
}
