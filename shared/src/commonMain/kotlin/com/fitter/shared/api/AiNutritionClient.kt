package com.fitter.shared.api

import com.fitter.shared.model.NutritionResponse
import com.fitter.shared.model.Totals

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

    override suspend fun analyzeMealImage(base64Image: String, plateSizeInches: Float?): NutritionResponse {
        val original = when (provider) {
            ApiProvider.OPEN_ROUTER -> openRouterClient.analyzeMealImage(base64Image, apiKey, model, plateSizeInches)
            ApiProvider.GEMINI -> geminiClient.analyzeMealImage(base64Image, apiKey, model ?: "gemini-1.5-flash", plateSizeInches)
            ApiProvider.GROQ -> groqClient.analyzeMealImage(base64Image, apiKey, model ?: "llama-3.2-11b-vision-preview", plateSizeInches)
        }
        return groundNutritionResponse(original)
    }

    override suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>): NutritionResponse {
        val original = when (provider) {
            ApiProvider.OPEN_ROUTER -> openRouterClient.recalculateMealNutrition(items, apiKey, model)
            ApiProvider.GEMINI -> geminiClient.recalculateMealNutrition(items, apiKey, model ?: "gemini-1.5-flash")
            ApiProvider.GROQ -> groqClient.recalculateMealNutrition(items, apiKey, model ?: "llama-3.2-11b-vision-preview")
        }
        return groundNutritionResponse(original)
    }

    private fun groundNutritionResponse(response: NutritionResponse): NutritionResponse {
        val updatedItems = response.items.map { item ->
            val matchedFood = FoodDatabase.findClosestFood(item.item)
            if (matchedFood != null) {
                val scale = item.weight_est_g / 100.0
                val newCalories = kotlin.math.round(matchedFood.calories * scale).toInt()
                val newProtein = (matchedFood.protein * scale).toFloat()
                val newCarbs = (matchedFood.carbs * scale).toFloat()
                val newFat = (matchedFood.fat * scale).toFloat()
                
                item.copy(
                    calories = newCalories,
                    protein_g = newProtein,
                    carbs_g = newCarbs,
                    fat_g = newFat
                )
            } else {
                item
            }
        }
        
        val totalCalories = updatedItems.fold(0) { acc, it -> acc + it.calories }
        val totalProtein = updatedItems.fold(0.0f) { acc, it -> acc + it.protein_g }
        val totalCarbs = updatedItems.fold(0.0f) { acc, it -> acc + it.carbs_g }
        val totalFat = updatedItems.fold(0.0f) { acc, it -> acc + it.fat_g }
        
        return response.copy(
            items = updatedItems,
            totals = Totals(
                calories = totalCalories,
                protein_g = totalProtein,
                carbs_g = totalCarbs,
                fat_g = totalFat
            )
        )
    }
}
