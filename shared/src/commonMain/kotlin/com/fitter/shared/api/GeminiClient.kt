package com.fitter.shared.api

import com.fitter.shared.model.NutritionResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
internal data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
internal data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
internal data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
internal data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

@Serializable
internal data class GeminiGenerationConfig(
    val responseMimeType: String? = null
)

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiCandidateContent? = null
)

@Serializable
internal data class GeminiCandidateContent(
    val parts: List<GeminiCandidatePart>? = null
)

@Serializable
internal data class GeminiCandidatePart(
    val text: String? = null
)

class GeminiClient(
    var apiKey: String = "",
    var model: String = "gemini-3.5-flash-lite",
    var fallbackModel: String = "gemini-3.1-flash-lite"
) : NutritionClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    override suspend fun analyzeMealImage(base64Image: String, plateSizeInches: Float?): NutritionResponse {
        return analyzeMealImage(base64Image, apiKey, model = null, plateSizeInches = plateSizeInches)
    }

    suspend fun analyzeMealImage(base64Image: String, apiKey: String, model: String? = null, plateSizeInches: Float? = null): NutritionResponse {
        if (model != null) {
            return executeAnalyzeMealImage(base64Image, apiKey, model, plateSizeInches)
        }

        // 1. Try Primary: Gemini 3.5 Flash Lite (up to 500 requests/day free)
        return try {
            executeAnalyzeMealImage(base64Image, apiKey, this.model, plateSizeInches)
        } catch (e1: Exception) {
            println("Gemini primary (${this.model}) error: ${e1.message}. Falling back to ${this.fallbackModel}...")
            // 2. Try Fallback: Gemini 3.1 Flash Lite (another 500 requests/day free = 1,000 total RPD)
            try {
                executeAnalyzeMealImage(base64Image, apiKey, this.fallbackModel, plateSizeInches)
            } catch (e2: Exception) {
                throw Exception("Gemini primary (${this.model}) failed: ${e1.message}; Fallback (${this.fallbackModel}) failed: ${e2.message}")
            }
        }
    }

    private suspend fun executeAnalyzeMealImage(base64Image: String, apiKey: String, requestModel: String, plateSizeInches: Float?): NutritionResponse {
        var systemPrompt = """
            You are a professional nutritionist. Analyze the food in this image.
            Return ONLY a valid JSON object — no prose, no markdown fences, no explanation.
            Use this exact structure:
            {
              "meal_name": "string",
              "items": [
                {
                  "item": "string",
                  "weight_est_g": number,
                  "calories": number,
                  "protein_g": number,
                  "carbs_g": number,
                  "fat_g": number,
                  "confidence": "high" | "medium" | "low"
                }
              ],
              "totals": {
                "calories": number,
                "protein_g": number,
                "carbs_g": number,
                "fat_g": number
              },
              "estimation_notes": "string"
            }
        """.trimIndent()

        if (plateSizeInches != null) {
            systemPrompt += "\n\nNOTE: The user's plate size is exactly ${plateSizeInches} inches. Use this reference dimension to calibrate your spatial/volume calculations of portion sizes."
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = "What is in this meal? Please estimate its nutritional contents."),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = "image/jpeg",
                                data = base64Image
                            )
                        )
                    )
                )
            ),
            systemInstruction = GeminiSystemInstruction(
                parts = listOf(GeminiPart(text = systemPrompt))
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json"
            )
        )

        val httpResponse = client.post("https://generativelanguage.googleapis.com/v1beta/models/$requestModel:generateContent?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            throw Exception("Gemini API error ($requestModel): ${httpResponse.status.value} ${httpResponse.status.description}")
        }

        val geminiResponse = httpResponse.body<GeminiResponse>()
        val rawContent = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini ($requestModel)")

        val cleanedJson = cleanJson(rawContent)

        return json.decodeFromString<NutritionResponse>(cleanedJson)
    }

    override suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>): NutritionResponse {
        return recalculateMealNutrition(items, apiKey, model = null)
    }

    suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>, apiKey: String, model: String? = null): NutritionResponse {
        if (model != null) {
            return executeRecalculateMealNutrition(items, apiKey, model)
        }

        // 1. Try Primary: Gemini 3.5 Flash Lite (up to 500 requests/day free)
        return try {
            executeRecalculateMealNutrition(items, apiKey, this.model)
        } catch (e1: Exception) {
            println("Gemini primary (${this.model}) error: ${e1.message}. Falling back to ${this.fallbackModel}...")
            // 2. Try Fallback: Gemini 3.1 Flash Lite (another 500 requests/day free = 1,000 total RPD)
            try {
                executeRecalculateMealNutrition(items, apiKey, this.fallbackModel)
            } catch (e2: Exception) {
                throw Exception("Gemini primary (${this.model}) failed: ${e1.message}; Fallback (${this.fallbackModel}) failed: ${e2.message}")
            }
        }
    }

    private suspend fun executeRecalculateMealNutrition(items: List<Pair<String, Int>>, apiKey: String, requestModel: String): NutritionResponse {
        val prompt = """
            Analyze these food items and estimate their nutritional contents based on the given weights.
            Items:
            ${items.joinToString("\n") { "- ${it.first}: ${it.second}g" }}

            Return ONLY a valid JSON object — no prose, no markdown fences, no explanation.
            Use this exact structure:
            {
              "meal_name": "string",
              "items": [
                {
                  "item": "string",
                  "weight_est_g": number,
                  "calories": number,
                  "protein_g": number,
                  "carbs_g": number,
                  "fat_g": number,
                  "confidence": "high" | "medium" | "low"
                }
              ],
              "totals": {
                "calories": number,
                "protein_g": number,
                "carbs_g": number,
                "fat_g": number
              },
              "estimation_notes": "string"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt)
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json"
            )
        )

        val httpResponse = client.post("https://generativelanguage.googleapis.com/v1beta/models/$requestModel:generateContent?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            throw Exception("Gemini API error ($requestModel): ${httpResponse.status.value} ${httpResponse.status.description}")
        }

        val geminiResponse = httpResponse.body<GeminiResponse>()
        val rawContent = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini ($requestModel)")

        val cleanedJson = cleanJson(rawContent)

        return json.decodeFromString<NutritionResponse>(cleanedJson)
    }

    private fun cleanJson(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").trim()
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }
        return clean
    }
}
