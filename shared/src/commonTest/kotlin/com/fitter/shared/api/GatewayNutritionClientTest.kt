package com.fitter.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class GatewayNutritionClientTest {

    private val sampleNutritionJson = """
        {
          "meal_name": "Grilled Chicken and Rice",
          "items": [
            {
              "item": "Chicken Breast",
              "weight_est_g": 150,
              "calories": 248,
              "protein_g": 46.5,
              "carbs_g": 0.0,
              "fat_g": 5.4,
              "confidence": "high"
            }
          ],
          "totals": {
            "calories": 248,
            "protein_g": 46.5,
            "carbs_g": 0.0,
            "fat_g": 5.4
          },
          "estimation_notes": "Estimated accurately"
        }
    """.trimIndent()

    private fun createMockHttpClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }


    @Test
    fun test401TriggersReAuthCallbackAndRetrySucceeds() = runBlocking {
        var callCount = 0
        var reAuthCalled = false
        var currentJwt: String? = "expired-token"

        val client = createMockHttpClient { request ->
            callCount++
            if (request.headers["Authorization"] == "Bearer fresh-token") {
                respond(
                    content = sampleNutritionJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"error":"Unauthorized"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val gatewayClient = GatewayNutritionClient(
            gatewayUrl = "https://gateway.fitter.test",
            jwtProvider = { currentJwt },
            reAuthenticator = {
                reAuthCalled = true
                currentJwt = "fresh-token"
            },
            client = client
        )

        val result = gatewayClient.analyzeMealImageWithResult("base64_image_data")

        assertTrue(result is AnalyzeResult.Success, "Expected Success but got $result")
        assertEquals("Grilled Chicken and Rice", (result as AnalyzeResult.Success).response.meal_name)
        assertTrue(reAuthCalled, "reAuthenticator should have been called")

        assertEquals(2, callCount, "Should have made 2 HTTP calls (initial 401 + retry)")
    }

    @Test
    fun testReAuthFailureSurfacesAuthRequired() = runBlocking {
        var callCount = 0
        var reAuthCalled = false

        val client = createMockHttpClient {
            callCount++
            respond(
                content = """{"error":"Unauthorized"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val gatewayClient = GatewayNutritionClient(
            gatewayUrl = "https://gateway.fitter.test",
            jwtProvider = { "invalid-token" },
            reAuthenticator = {
                reAuthCalled = true
                throw RuntimeException("Re-auth failed network timeout")
            },
            client = client
        )

        val result = gatewayClient.analyzeMealImageWithResult("base64_image_data")

        assertEquals(AnalyzeResult.AuthRequired, result)
        assertTrue(reAuthCalled, "reAuthenticator should have been called")
        assertEquals(1, callCount, "Should not retry request when re-auth throws")
    }

    @Test
    fun testNullJwtTriggersReAuthBeforeRequest() = runBlocking {
        var callCount = 0
        var reAuthCalled = false
        var currentJwt: String? = null

        val client = createMockHttpClient { request ->
            callCount++
            assertEquals("Bearer newly-acquired-jwt", request.headers["Authorization"])
            respond(
                content = sampleNutritionJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val gatewayClient = GatewayNutritionClient(
            gatewayUrl = "https://gateway.fitter.test",
            jwtProvider = { currentJwt },
            reAuthenticator = {
                reAuthCalled = true
                currentJwt = "newly-acquired-jwt"
            },
            client = client
        )

        val result = gatewayClient.analyzeMealImageWithResult("base64_image_data")

        assertTrue(result is AnalyzeResult.Success)
        assertTrue(reAuthCalled)
        assertEquals(1, callCount)
    }

    @Test
    fun testNullJwtWithoutReAuthReturnsAuthRequiredImmediately() = runBlocking {
        val client = createMockHttpClient {
            fail("HTTP client should not be called when JWT is null and no reAuthenticator provided")
        }

        val gatewayClient = GatewayNutritionClient(
            gatewayUrl = "https://gateway.fitter.test",
            jwtProvider = { null },
            reAuthenticator = null,
            client = client
        )

        val result = gatewayClient.analyzeMealImageWithResult("base64_image_data")
        assertEquals(AnalyzeResult.AuthRequired, result)
    }

    @Test
    fun testTransientNetworkErrorRetriedOnce() = runBlocking {
        var callCount = 0

        val client = createMockHttpClient {
            callCount++
            if (callCount == 1) {
                throw io.ktor.utils.io.errors.IOException("Simulated transient socket reset")
            } else {
                respond(
                    content = sampleNutritionJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val gatewayClient = GatewayNutritionClient(
            gatewayUrl = "https://gateway.fitter.test",
            jwtProvider = { "valid-jwt" },
            client = client
        )

        val result = gatewayClient.analyzeMealImageWithResult("base64_image_data")

        assertTrue(result is AnalyzeResult.Success)
        assertEquals(2, callCount, "Transient network error should be retried once")
    }

    @Test
    fun testQuotaExhaustedReturnsQuotaExhaustedResult() = runBlocking {
        val client = createMockHttpClient {
            respond(
                content = """{"error":"Payment Required","message":"Daily scan quota exhausted"}""",
                status = HttpStatusCode(402, "Payment Required"),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val gatewayClient = GatewayNutritionClient(
            gatewayUrl = "https://gateway.fitter.test",
            jwtProvider = { "valid-jwt" },
            client = client
        )

        val result = gatewayClient.analyzeMealImageWithResult("base64_image_data")
        assertEquals(AnalyzeResult.QuotaExhausted, result)
    }
}
