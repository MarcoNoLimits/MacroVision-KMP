package com.fitter.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class NutritionResponse(
    val meal_name: String,
    val items: List<FoodItem>,
    val totals: Totals,
    val estimation_notes: String
)

@Serializable
data class FoodItem(
    val item: String,
    val weight_est_g: Int,
    val calories: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
    val confidence: String
)

@Serializable
data class Totals(
    val calories: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float
)
