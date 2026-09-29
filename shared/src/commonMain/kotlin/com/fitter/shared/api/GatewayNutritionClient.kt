package com.fitter.shared.api

import com.fitter.shared.model.NutritionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.delay

/**
 * Typed result for a meal analysis.
 * Allows the UI ad-gate to branch on quota state without catching exceptions.
 */
sealed class AnalyzeResult {
    /** VLM analysis succeeded. */
    data class Success(val response: NutritionResponse) : AnalyzeResult()

    /** Server quota exhausted (HTTP 402). Ad gate must show interstitial. */
    object QuotaExhausted : AnalyzeResult()

    /** JWT missing or invalid (HTTP 401). Auth retry is needed. */
    object AuthRequired : AnalyzeResult()

    /** Any other error (network, 5xx, parse). */
    data class Failed(val message: String, val httpStatus: Int? = null) : AnalyzeResult()
}

@Serializable
private data class AnalyzeMealRequest(
    val image_base64: String,
    val plate_size_inches: Float? = null,
    val model_hint: String? = null,
    val daily_allowance: Int = 3
)

@Serializable
private data class RecalculateRequest(
    val items: List<RecalcItem>
)

@Serializable
private data class RecalcItem(
    val name: String,
    val grams: Int
)

/**
 * Production NutritionClient that routes all AI inference through the Cloudflare Worker gateway.
 *
 * - Adds `Authorization: Bearer <jwt>` to every request.
 * - Sends `x-device-id` for secondary rate-limit tracking.
 * - Maps 401 → [AnalyzeResult.AuthRequired], 402 → [AnalyzeResult.QuotaExhausted].
 * - Timeouts: connect 5s / request 60s.
 * - Phase 10: Automatic re-authentication on 401 or missing JWT + retry once before failing.
 * - Phase 10: 1 bounded network retry for transient transport errors.
 *
 * @param gatewayUrl  e.g. "https://fitter-gateway.workers.dev" (no trailing slash)
 * @param jwtProvider Returns the current Supabase access token, or null if unauthenticated.
 * @param deviceIdProvider Returns a stable device identifier string.
 * @param reAuthenticator Injected callback to refresh or re-acquire the Supabase session on 401.
 * @param client Optional custom HttpClient (e.g. for testing with MockEngine).
 */
class GatewayNutritionClient(
    private val gatewayUrl: String,
    private val jwtProvider: () -> String?,
    private val deviceIdProvider: () -> String = { "unknown" },
    private val reAuthenticator: (suspend () -> Unit)? = null,
    private val dailyAllowanceProvider: () -> Int = { 3 },
    client: HttpClient? = null
) : NutritionClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = client ?: HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    // ── NutritionClient interface (throws on error — keeps legacy callers working) ──

    override suspend fun analyzeMealImage(
        base64Image: String,
        plateSizeInches: Float?
    ): NutritionResponse {
        return when (val result = analyzeMealImageWithResult(base64Image, plateSizeInches)) {
            is AnalyzeResult.Success -> result.response
            is AnalyzeResult.QuotaExhausted -> throw Exception("Daily scan quota exhausted")
            // AuthRequired from the server means the Worker actually rejected the token —
            // surface the real error message so it appears in the diagnostics log.
            is AnalyzeResult.AuthRequired -> throw Exception("Gateway: authentication rejected by server (HTTP 401)")
            is AnalyzeResult.Failed -> throw Exception(result.message)
        }
    }

    override suspend fun recalculateMealNutrition(
        items: List<Pair<String, Int>>
    ): NutritionResponse {
        // Try to get a JWT, but do NOT block the request if one isn't available.
        var jwt = jwtProvider()
        if (jwt == null && reAuthenticator != null) {
            try {
                reAuthenticator.invoke()
                jwt = jwtProvider()
            } catch (_: Throwable) {
                // reAuth failed — proceed without a token; Worker will handle it
            }
        }

        val requestBody = RecalculateRequest(
            items = items.map { (name, grams) -> RecalcItem(name = name, grams = grams) }
        )
        val url = "${gatewayUrl.trimEnd('/')}/v1/recalculate"

        suspend fun postRecalc(token: String?): HttpResponse {
            return httpClient.post(url) {
                contentType(ContentType.Application.Json)
                if (token != null) header("Authorization", "Bearer $token")
                header("x-device-id", deviceIdProvider())
                setBody(requestBody)
            }
        }

        var response = postRecalc(jwt)
        if (response.status == HttpStatusCode.Unauthorized && reAuthenticator != null) {
            try {
                reAuthenticator.invoke()
                val freshJwt = jwtProvider()
                if (freshJwt != null) {
                    response = postRecalc(freshJwt)
                }
            } catch (_: Throwable) {
                // fall through to check status
            }
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("Gateway recalculate error (${response.status.value}): $errorBody")
        }

        return response.body<NutritionResponse>()
    }

    // ── Typed result variant (preferred for UI quota gating) ──────────────────

    override suspend fun analyzeMealImageWithResult(
        base64Image: String,
        plateSizeInches: Float?
    ): AnalyzeResult {
        return executeAnalyzeMeal(
            base64Image = base64Image,
            plateSizeInches = plateSizeInches,
            dailyAllowance = dailyAllowanceProvider().coerceIn(1, 5)
        )
    }

    /**
     * Variant that passes the Week-1/Week-2 allowance from the client to the Worker.
     * Worker caps at 5 server-side.
     */
    suspend fun analyzeMealImageWithAllowance(
        base64Image: String,
        plateSizeInches: Float?,
        dailyAllowance: Int
    ): AnalyzeResult {
        return executeAnalyzeMeal(
            base64Image = base64Image,
            plateSizeInches = plateSizeInches,
            dailyAllowance = dailyAllowance.coerceIn(1, 5)
        )
    }

    private suspend fun executeAnalyzeMeal(
        base64Image: String,
        plateSizeInches: Float?,
        dailyAllowance: Int
    ): AnalyzeResult {
        var jwt = jwtProvider()
        if (jwt == null && reAuthenticator != null) {
            try {
                reAuthenticator.invoke()
                jwt = jwtProvider()
            } catch (_: Throwable) {
                return AnalyzeResult.AuthRequired
            }
        }
        if (jwt == null) {
            return AnalyzeResult.AuthRequired
        }

        val requestBody = AnalyzeMealRequest(
            image_base64 = base64Image,
            plate_size_inches = plateSizeInches,
            daily_allowance = dailyAllowance
        )
        val url = "${gatewayUrl.trimEnd('/')}/v1/analyze-meal"

        suspend fun postWithNetRetry(token: String?): Pair<HttpResponse?, Exception?> {
            var lastNetErr: Exception? = null
            for (attempt in 0..1) {
                try {
                    val resp = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        if (token != null) header("Authorization", "Bearer $token")
                        header("x-device-id", deviceIdProvider())
                        setBody(requestBody)
                    }
                    return Pair(resp, null)
                } catch (e: Exception) {
                    lastNetErr = e
                    if (attempt == 0) {
                        delay(500)
                    }
                }
            }
            return Pair(null, lastNetErr)
        }

        val (firstResp, netErr) = postWithNetRetry(jwt)
        if (netErr != null || firstResp == null) {
            return AnalyzeResult.Failed(message = netErr?.message ?: "Network error")
        }

        // HTTP 401 from the server → try reAuth once more and retry
        if (firstResp.status == HttpStatusCode.Unauthorized) {
            if (reAuthenticator != null) {
                val reAuthSuccess = try {
                    reAuthenticator.invoke()
                    true
                } catch (_: Throwable) {
                    false
                }
                val freshJwt = jwtProvider()
                if (reAuthSuccess && freshJwt != null) {
                    val (retryResp, retryNetErr) = postWithNetRetry(freshJwt)
                    if (retryNetErr != null || retryResp == null) {
                        return AnalyzeResult.Failed(message = retryNetErr?.message ?: "Network error on retry")
                    }
                    return mapResponse(retryResp)
                }
            }
            return AnalyzeResult.AuthRequired
        }

        return mapResponse(firstResp)
    }

    private suspend fun mapResponse(response: HttpResponse): AnalyzeResult {
        return when (response.status) {
            HttpStatusCode.OK -> {
                val nutrition = response.body<NutritionResponse>()
                AnalyzeResult.Success(nutrition)
            }
            HttpStatusCode.Unauthorized -> AnalyzeResult.AuthRequired
            HttpStatusCode(402, "Payment Required") -> AnalyzeResult.QuotaExhausted
            else -> {
                val body = response.bodyAsText()
                AnalyzeResult.Failed(
                    message = "Gateway error (${response.status.value}): $body",
                    httpStatus = response.status.value
                )
            }
        }
    }


    /**
     * Fetch premium entitlement state from the Worker (JWT-authenticated).
     * Returns null on network failure (caller should use cached state).
     */
    suspend fun fetchEntitlements(): EntitlementState? {
        val jwt = jwtProvider() ?: return null
        return try {
            val response = httpClient.get("${gatewayUrl.trimEnd('/')}/v1/entitlements") {
                header("Authorization", "Bearer $jwt")
            }
            if (response.status.isSuccess()) {
                response.body<EntitlementState>()
            } else null
        } catch (e: Exception) {
            println("GatewayNutritionClient.fetchEntitlements failed: ${e.message}")
            null
        }
    }
}

@Serializable
data class EntitlementState(
    val premium: Boolean,
    val user_id: String? = null
)
