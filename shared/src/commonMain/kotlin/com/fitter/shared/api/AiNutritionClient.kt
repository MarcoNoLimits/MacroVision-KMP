package com.fitter.shared.api

import com.fitter.shared.model.NutritionResponse

enum class ApiProvider {
    OPEN_ROUTER,
    GEMINI,
    GROQ
}

class AiNutritionClient(
    var provider: ApiProvider = ApiProvider.OPEN_ROUTER,
    var apiKey: String = "",
    var model: String? = null
) : NutritionClient {

    private val openRouterClient = OpenRouterClient()
    private val geminiClient = GeminiClient()
    private val groqClient = GroqClient()

    override suspend fun analyzeMealImage(base64Image: String): NutritionResponse {
        return when (provider) {
            ApiProvider.OPEN_ROUTER -> openRouterClient.analyzeMealImage(base64Image, apiKey, model)
            ApiProvider.GEMINI -> geminiClient.analyzeMealImage(base64Image, apiKey, model ?: "gemini-1.5-flash")
            ApiProvider.GROQ -> groqClient.analyzeMealImage(base64Image, apiKey, model ?: "llama-3.2-11b-vision-preview")
        }
    }

    override suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>): NutritionResponse {
        return when (provider) {
            ApiProvider.OPEN_ROUTER -> openRouterClient.recalculateMealNutrition(items, apiKey, model)
            ApiProvider.GEMINI -> geminiClient.recalculateMealNutrition(items, apiKey, model ?: "gemini-1.5-flash")
            ApiProvider.GROQ -> groqClient.recalculateMealNutrition(items, apiKey, model ?: "llama-3.2-11b-vision-preview")
        }
    }
}
