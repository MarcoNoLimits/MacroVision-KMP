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
    var model: String = "gemini-1.5-flash"
) : NutritionClient {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                prettyPrint = true
            })
        }
    }

    override suspend fun analyzeMealImage(base64Image: String, plateSizeInches: Float?): NutritionResponse {
        return analyzeMealImage(base64Image, apiKey, model, plateSizeInches)
    }

    suspend fun analyzeMealImage(base64Image: String, apiKey: String, model: String? = null, plateSizeInches: Float? = null): NutritionResponse {
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

        val requestModel = model ?: this.model
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
            throw Exception("Gemini API error: ${httpResponse.status.value} ${httpResponse.status.description}")
        }

        val geminiResponse = httpResponse.body<GeminiResponse>()
        val rawContent = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini")

        val cleanedJson = cleanJson(rawContent)

        return Json { ignoreUnknownKeys = true }.decodeFromString<NutritionResponse>(cleanedJson)
    }

    override suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>): NutritionResponse {
        return recalculateMealNutrition(items, apiKey, model)
    }

    suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>, apiKey: String, model: String? = null): NutritionResponse {
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

        val requestModel = model ?: this.model
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
            throw Exception("Gemini API error: ${httpResponse.status.value} ${httpResponse.status.description}")
        }

        val geminiResponse = httpResponse.body<GeminiResponse>()
        val rawContent = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini")

        val cleanedJson = cleanJson(rawContent)

        return Json { ignoreUnknownKeys = true }.decodeFromString<NutritionResponse>(cleanedJson)
    }
}
