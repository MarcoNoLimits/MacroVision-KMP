package com.fitcal.app.auth

import com.fitcal.app.getCurrentEpochMillis
import com.fitcal.app.loadPreference
import com.fitcal.app.savePreference

/**
 * Decides when a guest sees the "save your meals — create a free account" suggestion
 * after logging a meal. Suggestions are never blocking: one per [COOLDOWN_MILLIS] at most,
 * only once the guest has logged [MIN_MEALS_LOGGED] meals, and never again after
 * [MAX_DISMISSALS] "Not now" taps.
 */
object SignInPromptPolicy {
    const val MIN_MEALS_LOGGED = 2
    const val MAX_DISMISSALS = 3
    const val COOLDOWN_MILLIS = 3L * 24 * 60 * 60 * 1000

    private const val KEY_LAST_SHOWN = "signin_prompt_last_shown"
    private const val KEY_DISMISSALS = "signin_prompt_dismissals"

    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default -> loadPreference(key, default) }
    var preferenceWriter: (key: String, value: String) -> Unit = { key, value -> savePreference(key, value) }
    var currentTimeMillisProvider: () -> Long = { getCurrentEpochMillis() }

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
        currentTimeMillisProvider = { getCurrentEpochMillis() }
    }

    fun shouldPromptAfterMealLogged(isPermanentUser: Boolean, totalMealsLogged: Int): Boolean {
        if (isPermanentUser) return false
        if (totalMealsLogged < MIN_MEALS_LOGGED) return false
        if (dismissals() >= MAX_DISMISSALS) return false
        val lastShown = preferenceReader(KEY_LAST_SHOWN, "0").toLongOrNull() ?: 0L
        return currentTimeMillisProvider() - lastShown >= COOLDOWN_MILLIS
    }

    fun recordShown() {
        preferenceWriter(KEY_LAST_SHOWN, currentTimeMillisProvider().toString())
    }

    fun recordDismissed() {
        preferenceWriter(KEY_DISMISSALS, (dismissals() + 1).toString())
    }

    private fun dismissals(): Int = preferenceReader(KEY_DISMISSALS, "0").toIntOrNull() ?: 0
}
