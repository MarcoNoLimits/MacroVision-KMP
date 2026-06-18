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
internal data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val response_format: GroqResponseFormat? = null
)

@Serializable
internal data class GroqResponseFormat(
    val type: String
)

@Serializable
internal data class GroqMessage(
    val role: String,
    val content: List<GroqContentItem>
)

@Serializable
internal data class GroqContentItem(
    val type: String,
    val text: String? = null,
    val image_url: GroqImageUrl? = null
)

@Serializable
internal data class GroqImageUrl(
    val url: String
)

@Serializable
internal data class GroqResponse(
    val choices: List<GroqChoice>
)

@Serializable
internal data class GroqChoice(
    val message: GroqResponseMessage
)

@Serializable
internal data class GroqResponseMessage(
    val content: String
)

class GroqClient(
    var apiKey: String = "",
    var model: String = "llama-3.2-11b-vision-preview"
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
        val request = GroqRequest(
            model = requestModel,
            messages = listOf(
                GroqMessage(
                    role = "system",
                    content = listOf(GroqContentItem(type = "text", text = systemPrompt))
                ),
                GroqMessage(
                    role = "user",
                    content = listOf(
                        GroqContentItem(type = "text", text = "What is in this meal? Please estimate its nutritional contents."),
                        GroqContentItem(
                            type = "image_url",
                            image_url = GroqImageUrl(url = "data:image/jpeg;base64,$base64Image")
                        )
                    )
                )
            ),
            response_format = GroqResponseFormat(type = "json_object")
        )

        val httpResponse = client.post("https://api.groq.com/openai/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            throw Exception("Groq API error: ${httpResponse.status.value} ${httpResponse.status.description}")
        }

        val groqResponse = httpResponse.body<GroqResponse>()
        val rawContent = groqResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from Groq")

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
        val request = GroqRequest(
            model = requestModel,
            messages = listOf(
                GroqMessage(
                    role = "user",
                    content = listOf(GroqContentItem(type = "text", text = prompt))
                )
            ),
            response_format = GroqResponseFormat(type = "json_object")
        )

        val httpResponse = client.post("https://api.groq.com/openai/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            throw Exception("Groq API error: ${httpResponse.status.value} ${httpResponse.status.description}")
        }

        val groqResponse = httpResponse.body<GroqResponse>()
        val rawContent = groqResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from Groq")

        val cleanedJson = cleanJson(rawContent)

        return Json { ignoreUnknownKeys = true }.decodeFromString<NutritionResponse>(cleanedJson)
    }
}
