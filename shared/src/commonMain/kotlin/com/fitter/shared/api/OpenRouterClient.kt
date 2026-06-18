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
internal data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 1000
)

@Serializable
internal data class Message(
    val role: String,
    val content: List<ContentItem>
)

@Serializable
internal data class ContentItem(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

@Serializable
internal data class ImageUrl(
    val url: String
)

@Serializable
internal data class OpenRouterResponse(
    val choices: List<Choice>
)

@Serializable
internal data class Choice(
    val message: ResponseMessage
)

@Serializable
internal data class ResponseMessage(
    val content: String
)

class OpenRouterClient(
    var apiKey: String = "",
    var model: String = "openrouter/free"
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
            systemPrompt += "\n\nNOTE: The user's plate size is exactly $plateSizeInches inches. Use this reference dimension to calibrate your spatial/volume calculations of portion sizes."
        }

        val requestModel = model ?: this.model
        val request = OpenRouterRequest(
            model = requestModel,
            messages = listOf(
                Message(
                    role = "system",
                    content = listOf(ContentItem(type = "text", text = systemPrompt))
                ),
                Message(
                    role = "user",
                    content = listOf(
                        ContentItem(type = "text", text = "What is in this meal? Please estimate its nutritional contents."),
                        ContentItem(
                            type = "image_url",
                            image_url = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                        )
                    )
                )
            )
        )

        val httpResponse = client.post("https://openrouter.ai/api/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            throw Exception("OpenRouter API error: ${httpResponse.status.value} ${httpResponse.status.description}")
        }

        val openRouterResponse = httpResponse.body<OpenRouterResponse>()
        val rawContent = openRouterResponse.choices.firstOrNull()?.message?.content 
            ?: throw Exception("Empty response from OpenRouter")
        
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
        val request = OpenRouterRequest(
            model = requestModel,
            messages = listOf(
                Message(
                    role = "user",
                    content = listOf(ContentItem(type = "text", text = prompt))
                )
            )
        )

        val httpResponse = client.post("https://openrouter.ai/api/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            throw Exception("OpenRouter API error: ${httpResponse.status.value} ${httpResponse.status.description}")
        }

        val openRouterResponse = httpResponse.body<OpenRouterResponse>()
        val rawContent = openRouterResponse.choices.firstOrNull()?.message?.content 
            ?: throw Exception("Empty response from OpenRouter")
        
        val cleanedJson = cleanJson(rawContent)
        
        return Json { ignoreUnknownKeys = true }.decodeFromString<NutritionResponse>(cleanedJson)
    }
}
