package com.fitcal.app.telemetry

import com.fitcal.shared.model.FoodItem
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How much the user changed the AI's estimate before logging a meal: the main
 * signal for improving the model. Counts and percentages only, no food names.
 */
data class ReviewCorrections(
    val itemsAi: Int,
    val itemsFinal: Int,
    val itemsRemoved: Int,
    val itemsAdded: Int,
    val weightsChanged: Int,
    /** Total grams changed on kept items, as % of the AI's grams for those items. */
    val weightChangePct: Int,
    val lowConfidenceItems: Int,
) {
    val edited: Boolean get() = itemsRemoved > 0 || itemsAdded > 0 || weightsChanged > 0
}

/**
 * Compares the AI's items with what the user is about to log.
 * Items are matched by name (case-insensitive), so a swap counts as one removal
 * plus one addition.
 */
fun summarizeCorrections(original: List<FoodItem>, final: List<Pair<String, Int>>): ReviewCorrections {
    val unmatched = original.toMutableList()
    var added = 0
    var weightsChanged = 0
    var gramsChanged = 0
    var gramsMatched = 0
    for ((name, grams) in final) {
        val key = name.trim().lowercase()
        val match = unmatched.firstOrNull { it.item.trim().lowercase() == key }
        if (match == null) {
            added++
            continue
        }
        unmatched.remove(match)
        gramsMatched += match.weight_est_g
        val delta = abs(grams - match.weight_est_g)
        if (delta > 0) {
            weightsChanged++
            gramsChanged += delta
        }
    }
    return ReviewCorrections(
        itemsAi = original.size,
        itemsFinal = final.size,
        itemsRemoved = unmatched.size,
        itemsAdded = added,
        weightsChanged = weightsChanged,
        weightChangePct = if (gramsMatched > 0) (gramsChanged * 100f / gramsMatched).roundToInt() else 0,
        lowConfidenceItems = original.count { it.confidence.equals("low", ignoreCase = true) },
    )
}
