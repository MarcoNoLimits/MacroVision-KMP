package com.fitter.shared.api

import com.fitter.shared.model.NutritionResponse

interface NutritionClient {
    /**
     * Analyzes a meal image and returns nutritional data.
     * Throws on error (quota exhausted, auth failure, network failure).
     * Preserved for backward compatibility with [FailoverNutritionClient] and tests.
     */
    suspend fun analyzeMealImage(base64Image: String, plateSizeInches: Float? = null): NutritionResponse

    /**
     * Recalculates nutrition for a list of (food item name, grams) pairs.
     */
    suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>): NutritionResponse

    /**
     * Typed variant that surfaces quota/auth errors without throwing.
     * Default implementation wraps [analyzeMealImage] — override in [GatewayNutritionClient]
     * to return [AnalyzeResult.QuotaExhausted] / [AnalyzeResult.AuthRequired] directly.
     *
     * UI quota gate should use this method to branch to the interstitial ad path.
     */
    suspend fun analyzeMealImageWithResult(
        base64Image: String,
        plateSizeInches: Float? = null
    ): AnalyzeResult {
        return try {
            AnalyzeResult.Success(analyzeMealImage(base64Image, plateSizeInches))
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            when {
                msg.contains("quota", ignoreCase = true) -> AnalyzeResult.QuotaExhausted
                msg.contains("auth", ignoreCase = true) ||
                    msg.contains("401", ignoreCase = true) -> AnalyzeResult.AuthRequired
                else -> AnalyzeResult.Failed(msg)
            }
        }
    }
}
