package com.fitter.shared.quota

import com.fitter.shared.auth.SupabaseClientFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class QuotaSnapshot(
    val used: Int = 0,
    val bonus: Int = 0,
    val remaining: Int = 0,
    val first_install_date: String? = null
)

open class SupabaseQuotaManager(
    client: SupabaseClient? = null
) {
    protected val client: SupabaseClient by lazy {
        client ?: SupabaseClientFactory.getOrCreate()
    }

    /**
     * Calls atomic consume_scan(p_allowance) RPC.
     * Overload for backwards compatibility and subclassing.
     */
    open suspend fun consumeScan(allowance: Int = 3): Boolean? {
        return consumeScan(allowance, null)
    }

    /**
     * Calls atomic consume_scan(p_user_id, p_allowance) RPC.
     *
     * Returns:
     *   - `true`  if within daily allowance + bonus (scan allowed)
     *   - `false` if quota exhausted (hard denial — do NOT proceed)
     *   - `null`  if a network/DB error occurred (unknown state — surface offline banner)
     *
     * NEVER returns `true` on error. Fail-closed.
     */
    open suspend fun consumeScan(allowance: Int, userId: String?): Boolean? {
        return try {
            val uid = userId ?: try { client.auth.currentSessionOrNull()?.user?.id } catch (_: Exception) { null }
            client.postgrest.rpc(
                function = "consume_scan",
                parameters = buildJsonObject {
                    put("p_allowance", allowance)
                    if (uid != null) {
                        put("p_user_id", uid)
                    }
                }
            ).decodeAs<Boolean>()
        } catch (e: Exception) {
            println("SupabaseQuotaManager.consumeScan network/DB error: ${e.message}")
            null  // Unknown — caller must handle (show offline banner, do NOT grant quota)
        }
    }

    /**
     * Grants bonus scans for a completed rewarded ad.
     *
     * SECURITY: this used to call the `grant_bonus_scan` PostgREST RPC directly
     * from the client, which let any signed-in user grant themselves unlimited
     * bonus scans — defeating both the daily quota and the Premium upgrade.
     * Migration 0005 revokes that RPC from `authenticated`; the grant now goes
     * through the authenticated gateway endpoint, which derives the user from the
     * verified JWT (never from client input) and caps claims per day.
     *
     * Returns the new server-confirmed bonus count, or null on error.
     */
    open suspend fun grantBonusScan(amount: Int = 2): Int? {
        val gateway = gatewayClient ?: return null
        return gateway.claimRewardedAdBonus(amount)
    }

    /**
     * Optional gateway used for server-authoritative bonus grants. Injected by the
     * app so this manager stays free of networking concerns.
     */
    var gatewayClient: com.fitter.shared.api.GatewayNutritionClient? = null

    /**
     * Fetches current quota ledger snapshot from server.
     * Overload for backwards compatibility and subclassing.
     */
    open suspend fun fetchQuota(allowance: Int = 3): QuotaSnapshot? {
        return fetchQuota(allowance, null)
    }

    /**
     * Fetches current quota ledger snapshot from server with optional explicit userId.
     * Returns null on network error so caller can show an offline banner.
     */
    open suspend fun fetchQuota(allowance: Int, userId: String?): QuotaSnapshot? {
        return try {
            val uid = userId ?: try { client.auth.currentSessionOrNull()?.user?.id } catch (_: Exception) { null }
            client.postgrest.rpc(
                function = "get_scan_quota",
                parameters = buildJsonObject {
                    put("p_allowance", allowance)
                    if (uid != null) {
                        put("p_user_id", uid)
                    }
                }
            ).decodeAs<QuotaSnapshot>()
        } catch (e: Exception) {
            println("SupabaseQuotaManager.fetchQuota failed: ${e.message}")
            null  // Unknown state; caller uses cached values with offline banner
        }
    }
}
