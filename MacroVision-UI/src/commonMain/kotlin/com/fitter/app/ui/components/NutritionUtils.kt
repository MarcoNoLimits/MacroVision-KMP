package com.fitter.app.ui.components

import com.fitter.shared.model.UserProfile

fun calculateBmr(profile: UserProfile): Float {
    return if (profile.gender == "Male") {
        (10f * profile.weight) + (6.25f * profile.height) - (5f * profile.age) + 5f
    } else {
        (10f * profile.weight) + (6.25f * profile.height) - (5f * profile.age) - 161f
    }
}

fun getAiCoachFeedback(p: Float, c: Float, f: Float, cal: Int, profile: UserProfile): String {
    if (cal == 0) {
        return "Coach says: Log your first meal to get personalized daily nutritional feedback!"
    }
    
    val pPct = if (profile.proteinGoal > 0) (p / profile.proteinGoal) else 0f
    val cPct = if (profile.carbsGoal > 0) (c / profile.carbsGoal) else 0f
    val fPct = if (profile.fatGoal > 0) (f / profile.fatGoal) else 0f
    
    return when {
        pPct < 0.4f -> "Coach says: Your protein intake is low today ($p g). Try adding Greek yogurt, eggs, or lean salmon to feed your muscles!"
        fPct > 0.9f -> "Coach says: You are close to your fats limit today ($f g). Keep subsequent meals lean, focusing on vegetables and complex carbs."
        cPct > 0.9f -> "Coach says: Carbs limit reached ($c g). Swap simple starches for proteins and fibers for the rest of today."
        cal > profile.calGoal -> "Coach says: You've exceeded your daily calorie goal. Try to focus on lean proteins and hydration tomorrow."
        else -> "Coach says: Excellent macro distribution! You are keeping a balanced intake today. Keep it up!"
    }
}

fun getMockJson(): String = """
{
  "meal_name": "Grilled Salmon & Quinoa Pilaf",
  "items": [
    {
      "item": "Grilled Salmon Fillet",
      "weight_est_g": 180,
      "calories": 360,
      "protein_g": 39.0,
      "carbs_g": 0.0,
      "fat_g": 22.0,
      "confidence": "high"
    },
    {
      "item": "Quinoa Pilaf",
      "weight_est_g": 120,
      "calories": 140,
      "protein_g": 5.0,
      "carbs_g": 26.0,
      "fat_g": 2.0,
      "confidence": "high"
    },
    {
      "item": "Roasted Asparagus",
      "weight_est_g": 100,
      "calories": 35,
      "protein_g": 2.2,
      "carbs_g": 4.1,
      "fat_g": 1.2,
      "confidence": "medium"
    },
    {
      "item": "Lemon Butter Sauce",
      "weight_est_g": 15,
      "calories": 75,
      "protein_g": 0.1,
      "carbs_g": 0.5,
      "fat_g": 8.3,
      "confidence": "medium"
    }
  ],
  "totals": {
    "calories": 610,
    "protein_g": 46.3,
    "carbs_g": 30.6,
    "fat_g": 33.7
  },
  "estimation_notes": "Estimated portion sizes are based on visual analysis. Salmon is a source of lean protein and Omega-3 fats. Asparagus and quinoa provide fiber and slow-release carbohydrates."
}
""".trimIndent()
