package com.fitter.shared.api

import com.fitter.shared.model.NutritionResponse

class FailoverNutritionClient(
    var openRouterKey: String = "",
    var geminiKey: String = "",
    var groqKey: String = ""
) : NutritionClient {

    private val openRouterClient = OpenRouterClient()
    private val geminiClient = GeminiClient()
    private val groqClient = GroqClient()

    override suspend fun analyzeMealImage(base64Image: String): NutritionResponse {
        val errors = mutableListOf<String>()

        // 1. Try OpenRouter if configured
        if (isOpenRouterConfigured()) {
            try {
                return openRouterClient.analyzeMealImage(base64Image, openRouterKey)
            } catch (e: Exception) {
                errors.add("OpenRouter error: ${e.message}")
            }
        }

        // 2. Try Gemini if configured
        if (isGeminiConfigured()) {
            try {
                return geminiClient.analyzeMealImage(base64Image, geminiKey)
            } catch (e: Exception) {
                errors.add("Gemini error: ${e.message}")
            }
        }

        // 3. Try Groq if configured
        if (isGroqConfigured()) {
            try {
                return groqClient.analyzeMealImage(base64Image, groqKey)
            } catch (e: Exception) {
                errors.add("Groq error: ${e.message}")
            }
        }

        val errorText = if (errors.isEmpty()) {
            "No API keys configured"
        } else {
            errors.joinToString("; ")
        }
        throw Exception("All configured APIs failed: $errorText")
    }

    override suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>): NutritionResponse {
        val errors = mutableListOf<String>()

        // 1. Try OpenRouter if configured
        if (isOpenRouterConfigured()) {
            try {
                return openRouterClient.recalculateMealNutrition(items, openRouterKey)
            } catch (e: Exception) {
                errors.add("OpenRouter error: ${e.message}")
            }
        }

        // 2. Try Gemini if configured
        if (isGeminiConfigured()) {
            try {
                return geminiClient.recalculateMealNutrition(items, geminiKey)
            } catch (e: Exception) {
                errors.add("Gemini error: ${e.message}")
            }
        }

        // 3. Try Groq if configured
        if (isGroqConfigured()) {
            try {
                return groqClient.recalculateMealNutrition(items, groqKey)
            } catch (e: Exception) {
                errors.add("Groq error: ${e.message}")
            }
        }

        val errorText = if (errors.isEmpty()) {
            "No API keys configured"
        } else {
            errors.joinToString("; ")
        }
        throw Exception("All configured APIs failed: $errorText")
    }

    private fun isOpenRouterConfigured(): Boolean {
        return openRouterKey.isNotBlank() && openRouterKey != "your_openrouter_api_key_here"
    }

    private fun isGeminiConfigured(): Boolean {
        return geminiKey.isNotBlank() && geminiKey != "your_gemini_api_key_here"
    }

    private fun isGroqConfigured(): Boolean {
        return groqKey.isNotBlank() && groqKey != "your_groq_api_key_here"
    }
}
