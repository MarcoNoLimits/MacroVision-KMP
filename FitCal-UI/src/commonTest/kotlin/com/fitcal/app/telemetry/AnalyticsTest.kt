package com.fitcal.app.telemetry

import com.fitcal.app.privacy.PrivacyConsent
import com.fitcal.shared.data.KeyValueStorage
import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.telemetry.TelemetryUploader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnalyticsTest {

    private class MemoryStorage : KeyValueStorage {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String, defaultValue: String) = map[key] ?: defaultValue
        override fun putString(key: String, value: String) { map[key] = value }
        override fun getInt(key: String, defaultValue: Int) = map[key]?.toIntOrNull() ?: defaultValue
        override fun putInt(key: String, value: Int) { map[key] = value.toString() }
        override fun remove(key: String) { map.remove(key) }
    }

    private class ConsentStore : PrivacyConsent.ConsentStorage {
        val map = mutableMapOf<String, Any>()
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
        override fun putLong(key: String, value: Long) { map[key] = value }
    }

    private val storage = MemoryStorage()
    private var now = 1_760_000_000_000L

    @BeforeTest
    fun setUp() {
        PrivacyConsent.bindStorage(ConsentStore())
        TelemetryUploader.resetForTesting()
        Analytics.resetForTesting()
        Analytics.storageProvider = { storage }
        Analytics.nowMillis = { now }
        TelemetryUploader.isEnabled = Analytics::isActive
    }

    @AfterTest
    fun tearDown() {
        TelemetryUploader.resetForTesting()
        Analytics.resetForTesting()
    }

    private fun acceptPrivacy() =
        PrivacyConsent.recordDecision(acceptTerms = true, adsPersonalized = false, timestampMs = 1L)

    @Test
    fun inactiveBeforeConsent() {
        assertFalse(Analytics.isActive(), "Nothing may be tracked before the privacy notice is accepted")
        acceptPrivacy()
        assertTrue(Analytics.isActive())
    }

    @Test
    fun optOutStopsTracking() = runBlocking {
        acceptPrivacy()
        Analytics.setUserOptedIn(false)
        assertFalse(Analytics.isActive())
        TelemetryUploader.trackSync("app_open")
        assertEquals(0, TelemetryUploader.getBufferSize())

        Analytics.setUserOptedIn(true)
        assertTrue(Analytics.isActive())
    }

    @Test
    fun shortBackgroundKeepsSessionLongBackgroundStartsNewOne() {
        acceptPrivacy()
        val first = Analytics.currentSessionIdForTesting()

        Analytics.onBackground()
        now += 5 * 60 * 1000L
        Analytics.onForeground()
        assertEquals(first, Analytics.currentSessionIdForTesting())

        Analytics.onBackground()
        now += 31 * 60 * 1000L
        Analytics.onForeground()
        assertNotEquals(first, Analytics.currentSessionIdForTesting())
    }

    @Test
    fun propsConvertToJsonTypes() {
        val json = Analytics.toJson(
            mapOf("n" to 3, "b" to true, "s" to "camera", "nil" to null, "list" to listOf("gemini", "groq"))
        )
        assertEquals(JsonPrimitive(3), json["n"])
        assertEquals(JsonPrimitive(true), json["b"])
        assertEquals(JsonPrimitive("camera"), json["s"])
        assertEquals(JsonNull, json["nil"])
        assertEquals(JsonArray(listOf(JsonPrimitive("gemini"), JsonPrimitive("groq"))), json["list"])
    }

    // ── Review corrections ───────────────────────────────────────────────────

    private fun item(name: String, grams: Int, confidence: String = "high") =
        FoodItem(name, grams, calories = 100, protein_g = 1f, carbs_g = 1f, fat_g = 1f, confidence = confidence)

    @Test
    fun untouchedMealIsNotEdited() {
        val ai = listOf(item("Rice", 150), item("Chicken", 120))
        val c = summarizeCorrections(ai, listOf("Rice" to 150, "chicken " to 120))
        assertFalse(c.edited)
        assertEquals(0, c.weightChangePct)
    }

    @Test
    fun countsRemovalsAdditionsAndWeightChanges() {
        val ai = listOf(item("Rice", 200), item("Chicken", 100, "low"), item("Salad", 50))
        val final = listOf("Rice" to 150, "Chicken" to 100, "Tofu" to 80)
        val c = summarizeCorrections(ai, final)

        assertTrue(c.edited)
        assertEquals(3, c.itemsAi)
        assertEquals(3, c.itemsFinal)
        assertEquals(1, c.itemsRemoved)      // Salad
        assertEquals(1, c.itemsAdded)        // Tofu
        assertEquals(1, c.weightsChanged)    // Rice 200 → 150
        assertEquals(17, c.weightChangePct)  // 50 g of 300 g kept
        assertEquals(1, c.lowConfidenceItems)
    }

    @Test
    fun duplicateNamesMatchOneToOne() {
        val ai = listOf(item("Egg", 50), item("Egg", 50))
        val c = summarizeCorrections(ai, listOf("Egg" to 50))
        assertEquals(1, c.itemsRemoved)
        assertEquals(0, c.itemsAdded)
    }
}
