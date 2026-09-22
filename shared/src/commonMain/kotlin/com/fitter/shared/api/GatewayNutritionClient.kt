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
 *
 * @param gatewayUrl  e.g. "https://fitter-gateway.workers.dev" (no trailing slash)
 * @param jwtProvider Returns the current Supabase access token, or null if unauthenticated.
 * @param deviceIdProvider Returns a stable device identifier string.
 */
class GatewayNutritionClient(
    private val gatewayUrl: String,
    private val jwtProvider: () -> String?,
    private val deviceIdProvider: () -> String = { "unknown" }
) : NutritionClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient {
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
            is AnalyzeResult.AuthRequired -> throw Exception("Authentication required")
            is AnalyzeResult.Failed -> throw Exception(result.message)
        }
    }

    override suspend fun recalculateMealNutrition(
        items: List<Pair<String, Int>>
    ): NutritionResponse {
        val jwt = jwtProvider()
            ?: throw Exception("No authentication token available")

        val requestBody = RecalculateRequest(
            items = items.map { (name, grams) -> RecalcItem(name = name, grams = grams) }
        )

        val response = httpClient.post("${gatewayUrl.trimEnd('/')}/v1/recalculate") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $jwt")
            header("x-device-id", deviceIdProvider())
            setBody(requestBody)
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
        val jwt = jwtProvider()
            ?: return AnalyzeResult.AuthRequired

        return try {
            val requestBody = AnalyzeMealRequest(
                image_base64 = base64Image,
                plate_size_inches = plateSizeInches,
                daily_allowance = 3  // default; ScanQuotaManager will pass the real allowance
            )

            val response = httpClient.post("${gatewayUrl.trimEnd('/')}/v1/analyze-meal") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $jwt")
                header("x-device-id", deviceIdProvider())
                setBody(requestBody)
            }

            when (response.status) {
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
        } catch (e: Exception) {
            AnalyzeResult.Failed(message = e.message ?: "Network error")
        }
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
        val jwt = jwtProvider() ?: return AnalyzeResult.AuthRequired

        return try {
            val requestBody = AnalyzeMealRequest(
                image_base64 = base64Image,
                plate_size_inches = plateSizeInches,
                daily_allowance = dailyAllowance.coerceIn(1, 5)
            )

            val response = httpClient.post("${gatewayUrl.trimEnd('/')}/v1/analyze-meal") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $jwt")
                header("x-device-id", deviceIdProvider())
                setBody(requestBody)
            }

            when (response.status) {
                HttpStatusCode.OK -> AnalyzeResult.Success(response.body())
                HttpStatusCode.Unauthorized -> AnalyzeResult.AuthRequired
                HttpStatusCode(402, "Payment Required") -> AnalyzeResult.QuotaExhausted
                else -> AnalyzeResult.Failed(
                    message = "Gateway error (${response.status.value}): ${response.bodyAsText()}",
                    httpStatus = response.status.value
                )
            }
        } catch (e: Exception) {
            AnalyzeResult.Failed(message = e.message ?: "Network error")
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
