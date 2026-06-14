package com.fitter.shared.api

import com.fitter.shared.model.NutritionResponse

interface NutritionClient {
    suspend fun analyzeMealImage(base64Image: String): NutritionResponse
    suspend fun recalculateMealNutrition(items: List<Pair<String, Int>>): NutritionResponse
}
