package com.fitter.shared.api

import com.fitter.shared.model.NutritionResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NutritionResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    private fun cleanJson(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").trim()
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }
        return clean
    }

    @Test
    fun testStandardMockJsonParsing() {
        val mockData = """
        {
          "meal_name": "Grilled Salmon and Quinoa Pilaf",
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
            }
          ],
          "totals": {
            "calories": 500,
            "protein_g": 44.0,
            "carbs_g": 26.0,
            "fat_g": 24.0
          },
          "estimation_notes": "Calibrated with 10.5-inch plate."
        }
        """.trimIndent()

        val response = json.decodeFromString<NutritionResponse>(mockData)
        assertEquals("Grilled Salmon and Quinoa Pilaf", response.meal_name)
        assertEquals(2, response.items.size)
        assertEquals(500, response.totals.calories)
        assertEquals(44.0f, response.totals.protein_g)
    }

    @Test
    fun testMinimaxMarkdownFencedAndExtraFieldsParsing() {
        val rawMinimaxOutput = """
        ```json
        {
          "meal_name": "Chicken Avocado Salad",
          "items": [
            {
              "item": "Grilled Chicken Breast",
              "weight_est_g": 150,
              "calories": 240,
              "protein_g": 45.0,
              "carbs_g": 0.0,
              "fat_g": 5.0,
              "confidence": "high",
              "extra_info": "Lean cut"
            },
            {
              "item": "Avocado Slices",
              "weight_est_g": 60,
              "calories": 96,
              "protein_g": 1.2,
              "carbs_g": 5.1,
              "fat_g": 8.8,
              "confidence": "medium"
            }
          ],
          "totals": {
            "calories": 336,
            "protein_g": 46.2,
            "carbs_g": 5.1,
            "fat_g": 13.8,
            "fiber_g": 4.0
          },
          "estimation_notes": "Accurate portion based on visual depth.",
          "provider": "minimax/minimax-m3:free"
        }
        ```
        """.trimIndent()

        val cleaned = cleanJson(rawMinimaxOutput)
        val response = json.decodeFromString<NutritionResponse>(cleaned)

        assertEquals("Chicken Avocado Salad", response.meal_name)
        assertEquals(2, response.items.size)
        assertEquals(336, response.totals.calories)
        assertEquals(46.2f, response.totals.protein_g)
        assertTrue(response.estimation_notes.isNotEmpty())
    }

    @Test
    fun testClientConfigurationDefaults() {
        val gemini = GeminiClient()
        assertEquals("gemini-3.5-flash-lite", gemini.model, "Primary model must be gemini-3.5-flash-lite")
        assertEquals("gemini-3.1-flash-lite", gemini.fallbackModel, "Fallback model must be gemini-3.1-flash-lite")

        val openRouter = OpenRouterClient()
        assertEquals("minimax/minimax-m3:free", openRouter.model, "OpenRouter model must be minimax/minimax-m3:free")
    }
}
