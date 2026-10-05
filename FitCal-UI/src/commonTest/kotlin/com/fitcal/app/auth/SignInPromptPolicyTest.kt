package com.fitcal.app.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInPromptPolicyTest {

    private val prefs = mutableMapOf<String, String>()
    private var now = 1_000_000_000_000L

    @BeforeTest
    fun setUp() {
        prefs.clear()
        SignInPromptPolicy.preferenceReader = { key, default -> prefs[key] ?: default }
        SignInPromptPolicy.preferenceWriter = { key, value -> prefs[key] = value }
        SignInPromptPolicy.currentTimeMillisProvider = { now }
    }

    @AfterTest
    fun tearDown() {
        SignInPromptPolicy.resetToDefaults()
    }

    @Test
    fun neverPromptsPermanentUsers() {
        assertFalse(SignInPromptPolicy.shouldPromptAfterMealLogged(isPermanentUser = true, totalMealsLogged = 10))
    }

    @Test
    fun waitsUntilGuestHasLoggedEnoughMeals() {
        assertFalse(SignInPromptPolicy.shouldPromptAfterMealLogged(isPermanentUser = false, totalMealsLogged = 1))
        assertTrue(SignInPromptPolicy.shouldPromptAfterMealLogged(isPermanentUser = false, totalMealsLogged = 2))
    }

    @Test
    fun respectsCooldownAfterBeingShown() {
        SignInPromptPolicy.recordShown()
        now += SignInPromptPolicy.COOLDOWN_MILLIS - 1
        assertFalse(SignInPromptPolicy.shouldPromptAfterMealLogged(isPermanentUser = false, totalMealsLogged = 5))
        now += 1
        assertTrue(SignInPromptPolicy.shouldPromptAfterMealLogged(isPermanentUser = false, totalMealsLogged = 5))
    }

    @Test
    fun stopsAfterMaxDismissals() {
        repeat(SignInPromptPolicy.MAX_DISMISSALS) { SignInPromptPolicy.recordDismissed() }
        now += 10 * SignInPromptPolicy.COOLDOWN_MILLIS
        assertFalse(SignInPromptPolicy.shouldPromptAfterMealLogged(isPermanentUser = false, totalMealsLogged = 50))
    }
}
