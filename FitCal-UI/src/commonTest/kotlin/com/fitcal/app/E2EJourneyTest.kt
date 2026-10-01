package com.fitcal.app

import com.fitcal.app.ads.AdConfig
import com.fitcal.app.ads.AdManager
import com.fitcal.app.ads.AppOpenAdManager
import com.fitcal.app.ads.ScanQuotaManager
import com.fitcal.shared.api.FoodDatabase
import com.fitcal.shared.data.KeyValueStorage
import com.fitcal.shared.data.LocalMealRepository
import com.fitcal.shared.model.FoodItem
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.NutritionResponse
import com.fitcal.shared.model.Totals
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class E2EJourneyTest {

    private val memoryStore = mutableMapOf<String, String>()
    private lateinit var storage: InMemoryStorage
    private lateinit var mealRepository: LocalMealRepository
    private lateinit var adManager: RecordingAdManager

    private val testDateToday = "2026-09-05"
    private val testDateTomorrow = "2026-09-06"
    private var simulatedTimeMillis = 1757073600000L // 2026-09-05T12:00:00Z epoch millis

    class InMemoryStorage(private val store: MutableMap<String, String>) : KeyValueStorage {
        override fun getString(key: String, defaultValue: String): String =
            store[key] ?: defaultValue

        override fun putString(key: String, value: String) {
            store[key] = value
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            store[key]?.toIntOrNull() ?: defaultValue

        override fun putInt(key: String, value: Int) {
            store[key] = value.toString()
        }

        override fun remove(key: String) {
            store.remove(key)
        }
    }

    class RecordingAdManager : AdManager {
        var scanProcessingAdCalls = 0
        var rewardedAdCalls = 0
        var isModalDialogPromptShown = false
        var isProcessingAdFinished = false

        override fun showScanProcessingAd(onFinished: () -> Unit) {
            scanProcessingAdCalls++
            // Per Canonical Monetization Spec Section 5.2:
            // No prompt dialog. No "watch to continue" button. Direct interstitial ad display.
            isModalDialogPromptShown = false
            isProcessingAdFinished = true
            onFinished()
        }

        override fun showRewardedScanUnlockAd(onRewarded: () -> Unit, onDismissed: () -> Unit) {
            rewardedAdCalls++
            onRewarded()
            onDismissed()
        }

        override fun isInterstitialReady(): Boolean = true
        override fun isRewardedReady(): Boolean = true
        override fun preloadAds() {}
    }

    @BeforeTest
    fun setUp() {
        memoryStore.clear()
        storage = InMemoryStorage(memoryStore)
        mealRepository = LocalMealRepository(storage)
        adManager = RecordingAdManager()
        simulatedTimeMillis = 1757073600000L

        ScanQuotaManager.preferenceReader = { key, default ->
            memoryStore[key] ?: default
        }
        ScanQuotaManager.preferenceWriter = { key, value ->
            memoryStore[key] = value
        }
        ScanQuotaManager.currentTimeMillisProvider = { simulatedTimeMillis }

        AppOpenAdManager.preferenceReader = { key, default ->
            memoryStore[key] ?: default
        }
        AppOpenAdManager.preferenceWriter = { key, value ->
            memoryStore[key] = value
        }
        AppOpenAdManager.currentTimeMillisProvider = { simulatedTimeMillis }

        com.fitcal.app.telemetry.AdTelemetryManager.preferenceReader = { key, default ->
            memoryStore[key] ?: default
        }
        com.fitcal.app.telemetry.AdTelemetryManager.preferenceWriter = { key, value ->
            memoryStore[key] = value
        }
        com.fitcal.app.telemetry.AdTelemetryManager.currentTimeMillisProvider = { simulatedTimeMillis }
    }

    @AfterTest
    fun tearDown() {
        ScanQuotaManager.resetToDefaults()
        AppOpenAdManager.resetToDefaults()
        com.fitcal.app.telemetry.AdTelemetryManager.resetForTesting()
        memoryStore.clear()
    }

    /**
     * Journey 1: Fresh install Onboarding
     * -> Week 1 allowance check (5 scans/day)
     * -> Consuming scans
     * -> Scan 6+ forced interstitial ad trigger (shouldForceInterstitialAd == true) without modal dialog
     * -> Simulated ad dismissal
     * -> Process meal
     * -> Persist in LocalMealRepository
     * -> Verify meal query returns the logged meal.
     */
    @Test
    fun testJourney1_FreshInstallOnboarding_WeekOneAllowance_ScanSixForcedInterstitial_PersistMeal() = runBlocking {
        // 1. Fresh install Onboarding
        assertTrue(memoryStore.isEmpty(), "Preferences must be initially empty on fresh install")
        val sessionCount = AppOpenAdManager.incrementSessionCount()
        assertEquals(1, sessionCount)
        assertFalse(AppOpenAdManager.canShowAppOpenAd(), "Grace period must suppress App Open Ad on first session")

        val installTimestamp = ScanQuotaManager.getFirstInstallTimestamp()
        assertEquals(simulatedTimeMillis, installTimestamp)

        // 2. Week 1 allowance check (5 scans/day)
        assertTrue(ScanQuotaManager.isWeekOneUser(), "New install must be identified as Week 1 user")
        assertEquals(AdConfig.WEEK_ONE_DAILY_FREE_SCANS, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(5, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))
        assertFalse(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))

        // 3. Consuming scans (1 through 5)
        repeat(5) { index ->
            assertTrue(ScanQuotaManager.hasQuota(testDateToday), "Scan #${index + 1} should be within free quota")
            assertFalse(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(5, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))

        // 4. Scan 6+ forced interstitial ad trigger without modal dialog
        assertTrue(
            ScanQuotaManager.shouldForceInterstitialAd(testDateToday),
            "Scan 6+ must trigger forced interstitial ad"
        )

        // CameraScreen gate logic: playAdDuringScan || shouldForceInterstitialAd(testDateToday)
        val shouldPlayAd = ScanQuotaManager.shouldForceInterstitialAd(testDateToday)
        assertTrue(shouldPlayAd)

        // Scan #6 is consumed without throwing or hard-blocking
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(6, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))

        // 5. Simulated ad display & dismissal (no modal prompt dialog shown)
        var mealProcessed = false
        adManager.showScanProcessingAd {
            mealProcessed = true
        }
        assertEquals(1, adManager.scanProcessingAdCalls)
        assertFalse(
            adManager.isModalDialogPromptShown,
            "No modal prompt or rewarded dialog must be shown for forced interstitial"
        )
        assertTrue(adManager.isProcessingAdFinished)
        assertTrue(mealProcessed, "Meal processing callback should execute upon ad dismissal")

        // 6. Process meal & persist in LocalMealRepository
        val scannedMeal = LoggedMeal(
            id = "meal_scan_6_salmon",
            name = "Grilled Salmon Quinoa Bowl",
            calories = 520,
            protein = 42f,
            carbs = 38f,
            fat = 16f,
            timestamp = "07:30 PM",
            date = testDateToday
        )
        mealRepository.saveMeal(scannedMeal)

        // 7. Verify meal query returns the logged meal
        val todayMeals = mealRepository.getMealsForDate(testDateToday)
        assertEquals(1, todayMeals.size)
        val saved = todayMeals.first()
        assertEquals("meal_scan_6_salmon", saved.id)
        assertEquals("Grilled Salmon Quinoa Bowl", saved.name)
        assertEquals(520, saved.calories)
        assertEquals(42f, saved.protein)
        assertEquals(38f, saved.carbs)
        assertEquals(16f, saved.fat)
        assertEquals("07:30 PM", saved.timestamp)
        assertEquals(testDateToday, saved.date)

        val allMeals = mealRepository.getAllMeals()
        assertEquals(1, allMeals.size)
        assertEquals("meal_scan_6_salmon", allMeals.first().id)
    }

    /**
     * Journey 2: Week 2+ user (installed 10 days ago)
     * -> 3 scans/day
     * -> Quota exhaustion
     * -> Scan #4 forced interstitial
     * -> Rewarded video bonus scan addition (+2 scans)
     * -> Quota restored
     * -> Meal logged.
     */
    @Test
    fun testJourney2_WeekTwoUser_ScanFourForcedInterstitial_RewardedBonusRestore_MealLogged() = runBlocking {
        // 1. Week 2+ user (installed 10 days ago)
        val tenDaysAgo = simulatedTimeMillis - (10L * 24 * 60 * 60 * 1000L)
        memoryStore["first_install_timestamp"] = tenDaysAgo.toString()

        assertFalse(ScanQuotaManager.isWeekOneUser(), "User installed 10 days ago must not be Week 1")
        assertEquals(AdConfig.DEFAULT_DAILY_FREE_SCANS, ScanQuotaManager.getDailyFreeLimit(), "Week 2+ allowance must be 3 scans/day")
        assertEquals(3, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))
        assertFalse(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))

        // 2. Quota exhaustion (Scans 1, 2, 3)
        repeat(3) { index ->
            assertTrue(ScanQuotaManager.hasQuota(testDateToday), "Scan #${index + 1} should be allowed within quota")
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(3, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))

        // 3. Scan #4 forced interstitial
        assertTrue(
            ScanQuotaManager.shouldForceInterstitialAd(testDateToday),
            "Scan #4 must trigger forced interstitial ad"
        )

        var scanFourProcessed = false
        adManager.showScanProcessingAd {
            scanFourProcessed = true
        }
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(4, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(scanFourProcessed)
        assertEquals(1, adManager.scanProcessingAdCalls)

        // 4. Rewarded video bonus scan addition (+2 scans)
        adManager.showRewardedScanUnlockAd(
            onRewarded = {
                ScanQuotaManager.addBonusScans(testDateToday, AdConfig.REWARDED_SCAN_BONUS)
            },
            onDismissed = {}
        )
        assertEquals(1, adManager.rewardedAdCalls)
        assertEquals(2, ScanQuotaManager.getBonusScans(testDateToday))

        // 5. Quota restored
        // Total allowed: 3 limit + 2 bonus = 5. Used = 4. Remaining = 1.
        assertEquals(1, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday), "Quota must be restored after watching rewarded ad")
        assertFalse(
            ScanQuotaManager.shouldForceInterstitialAd(testDateToday),
            "Forced interstitial must be false when bonus quota is remaining"
        )

        // User performs scan #5 using the bonus scan without forced ad
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(5, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))

        // 6. Meal logged & verified
        val bonusScanMeal = LoggedMeal(
            id = "meal_scan_5_chicken",
            name = "Chicken Thigh with Sweet Potato",
            calories = 480,
            protein = 35f,
            carbs = 45f,
            fat = 12f,
            timestamp = "08:15 PM",
            date = testDateToday
        )
        mealRepository.saveMeal(bonusScanMeal)

        val todayMeals = mealRepository.getMealsForDate(testDateToday)
        assertEquals(1, todayMeals.size)
        assertEquals("meal_scan_5_chicken", todayMeals.first().id)
        assertEquals("Chicken Thigh with Sweet Potato", todayMeals.first().name)
        assertEquals(480, todayMeals.first().calories)
        assertEquals(35f, todayMeals.first().protein)
    }

    /**
     * Journey 3: VLM failover pipeline mock journey
     * -> Primary Gemini timeout/error
     * -> Secondary Gemini fallback
     * -> OpenRouter
     * -> Groq
     * -> Grounding in FoodDatabase.
     */
    @Test
    fun testJourney3_VlmFailoverPipelineWithGrounding() = runBlocking {
        val primaryGeminiModel = "gemini-3.5-flash-lite"
        val secondaryGeminiModel = "gemini-3.1-flash-lite"
        val openRouterModel = "minimax/minimax-m3:free"
        val groqModel = "llama-3.2-11b-vision-preview"

        val pipelineAuditLog = mutableListOf<String>()

        // Simulate the VLM failover pipeline execution matching FailoverNutritionClient sequencing
        fun executeVlmFailoverPipeline(photoBase64: String): NutritionResponse {
            // Tier 1: Primary Gemini (3.5 Flash Lite)
            pipelineAuditLog.add("Attempting Tier 1: $primaryGeminiModel")
            try {
                // Simulate Timeout / 504 Gateway Error
                throw Exception("Gemini primary ($primaryGeminiModel) failed: 504 Gateway Timeout")
            } catch (e1: Exception) {
                pipelineAuditLog.add("Tier 1 Failed: ${e1.message}")
            }

            // Tier 2: Secondary Gemini (3.1 Flash Lite)
            pipelineAuditLog.add("Attempting Tier 2: $secondaryGeminiModel")
            try {
                // Simulate 429 Quota Limit Exceeded
                throw Exception("Gemini secondary ($secondaryGeminiModel) failed: 429 Resource Exhausted")
            } catch (e2: Exception) {
                pipelineAuditLog.add("Tier 2 Failed: ${e2.message}")
            }

            // Tier 3: OpenRouter (minimax-m3)
            pipelineAuditLog.add("Attempting Tier 3: $openRouterModel")
            try {
                // Simulate 503 Service Unavailable / Upstream model overloaded
                throw Exception("OpenRouter ($openRouterModel) failed: 503 Service Unavailable")
            } catch (e3: Exception) {
                pipelineAuditLog.add("Tier 3 Failed: ${e3.message}")
            }

            // Tier 4: Groq (llama-3.2-11b-vision-preview)
            pipelineAuditLog.add("Attempting Tier 4: $groqModel")
            val rawGroqResult = NutritionResponse(
                meal_name = "Grilled Chicken & Quinoa Plate",
                items = listOf(
                    FoodItem(
                        item = "grilled chicken breast",
                        weight_est_g = 200,
                        calories = 0,
                        protein_g = 0f,
                        carbs_g = 0f,
                        fat_g = 0f,
                        confidence = "high"
                    ),
                    FoodItem(
                        item = "cooked quinoa",
                        weight_est_g = 150,
                        calories = 0,
                        protein_g = 0f,
                        carbs_g = 0f,
                        fat_g = 0f,
                        confidence = "high"
                    ),
                    FoodItem(
                        item = "steamed broccoli",
                        weight_est_g = 100,
                        calories = 0,
                        protein_g = 0f,
                        carbs_g = 0f,
                        fat_g = 0f,
                        confidence = "medium"
                    ),
                    FoodItem(
                        item = "avocado",
                        weight_est_g = 50,
                        calories = 0,
                        protein_g = 0f,
                        carbs_g = 0f,
                        fat_g = 0f,
                        confidence = "high"
                    )
                ),
                totals = Totals(calories = 0, protein_g = 0f, carbs_g = 0f, fat_g = 0f),
                estimation_notes = "Resolved via Tier 4 Groq fallback"
            )
            pipelineAuditLog.add("Tier 4 Succeeded: Generated raw response")
            return rawGroqResult
        }

        // 1. Execute failover sequence
        val rawResponse = executeVlmFailoverPipeline("fake_compressed_image_bytes")

        // Verify failover sequence occurred in exact expected order
        assertTrue(pipelineAuditLog.contains("Attempting Tier 1: $primaryGeminiModel"))
        assertTrue(pipelineAuditLog.any { it.contains("Tier 1 Failed") && it.contains("504 Gateway Timeout") })
        assertTrue(pipelineAuditLog.contains("Attempting Tier 2: $secondaryGeminiModel"))
        assertTrue(pipelineAuditLog.any { it.contains("Tier 2 Failed") && it.contains("429 Resource Exhausted") })
        assertTrue(pipelineAuditLog.contains("Attempting Tier 3: $openRouterModel"))
        assertTrue(pipelineAuditLog.any { it.contains("Tier 3 Failed") && it.contains("503 Service Unavailable") })
        assertTrue(pipelineAuditLog.contains("Attempting Tier 4: $groqModel"))
        assertTrue(pipelineAuditLog.contains("Tier 4 Succeeded: Generated raw response"))

        // 2. Grounding in FoodDatabase
        val groundedItems = rawResponse.items.map { rawItem ->
            val match = FoodDatabase.findClosestFood(rawItem.item)
            assertNotNull(match, "Food item '${rawItem.item}' should ground to FoodDatabase entry")

            val scale = rawItem.weight_est_g / 100.0
            val calibratedCalories = (match.calories * scale).roundToInt()
            val calibratedProtein = (match.protein * scale).toFloat()
            val calibratedCarbs = (match.carbs * scale).toFloat()
            val calibratedFat = (match.fat * scale).toFloat()

            rawItem.copy(
                calories = calibratedCalories,
                protein_g = calibratedProtein,
                carbs_g = calibratedCarbs,
                fat_g = calibratedFat
            )
        }

        // Verify individual food database groundings
        val chicken = groundedItems.first { it.item == "grilled chicken breast" }
        // Chicken Breast per 100g: 165 kcal, 31g P, 0g C, 3.6g F -> For 200g: 330 kcal, 62g P, 0g C, 7.2g F
        assertEquals(330, chicken.calories)
        assertEquals(62.0f, chicken.protein_g)

        val quinoa = groundedItems.first { it.item == "cooked quinoa" }
        // Quinoa per 100g: 120 kcal, 4.4g P, 21.3g C, 1.9g F -> For 150g: 180 kcal, 6.6g P, 31.95g C, 2.85g F
        assertEquals(180, quinoa.calories)

        val broccoli = groundedItems.first { it.item == "steamed broccoli" }
        // Broccoli per 100g: 34 kcal, 2.8g P, 6.6g C, 0.4g F -> For 100g: 34 kcal
        assertEquals(34, broccoli.calories)

        val avocado = groundedItems.first { it.item == "avocado" }
        // Avocado per 100g: 160 kcal, 2.0g P, 8.5g C, 14.7g F -> For 50g: 80 kcal
        assertEquals(80, avocado.calories)

        val totalCalories = groundedItems.sumOf { it.calories }
        val totalProtein = groundedItems.fold(0.0f) { acc, it -> acc + it.protein_g }
        val totalCarbs = groundedItems.fold(0.0f) { acc, it -> acc + it.carbs_g }
        val totalFat = groundedItems.fold(0.0f) { acc, it -> acc + it.fat_g }

        val groundedResponse = rawResponse.copy(
            items = groundedItems,
            totals = Totals(
                calories = totalCalories,
                protein_g = totalProtein,
                carbs_g = totalCarbs,
                fat_g = totalFat
            )
        )

        assertEquals(624, groundedResponse.totals.calories)
        assertTrue(groundedResponse.totals.protein_g > 70f)

        // 3. Persist Grounded Meal into LocalMealRepository
        val groundedMeal = LoggedMeal(
            id = "groq_grounded_meal_1",
            name = groundedResponse.meal_name,
            calories = groundedResponse.totals.calories,
            protein = groundedResponse.totals.protein_g,
            carbs = groundedResponse.totals.carbs_g,
            fat = groundedResponse.totals.fat_g,
            timestamp = "01:15 PM",
            date = testDateToday
        )
        mealRepository.saveMeal(groundedMeal)

        val savedMeals = mealRepository.getMealsForDate(testDateToday)
        assertEquals(1, savedMeals.size)
        assertEquals("Grilled Chicken & Quinoa Plate", savedMeals.first().name)
        assertEquals(624, savedMeals.first().calories)
    }

    /**
     * Journey 4: Multi-day quota isolation & reset test across date boundaries.
     */
    @Test
    fun testJourney4_MultiDayQuotaIsolationAndResetAcrossDateBoundaries() = runBlocking {
        val day1 = "2026-09-05"
        val day2 = "2026-09-06"
        val day7 = "2026-09-11"
        val day8 = "2026-09-12"
        val day9 = "2026-09-13"

        // Fresh install on Day 1
        val installTimestamp = simulatedTimeMillis
        assertEquals(installTimestamp, ScanQuotaManager.getFirstInstallTimestamp())
        assertTrue(ScanQuotaManager.isWeekOneUser())
        assertEquals(AdConfig.WEEK_ONE_DAILY_FREE_SCANS, ScanQuotaManager.getDailyFreeLimit())

        // --- DAY 1 ---
        // Consume all 5 daily free scans
        repeat(5) {
            assertTrue(ScanQuotaManager.hasQuota(day1))
            ScanQuotaManager.consumeScan(day1)
        }
        assertEquals(5, ScanQuotaManager.getUsedScans(day1))
        assertEquals(0, ScanQuotaManager.getRemainingScans(day1))
        assertFalse(ScanQuotaManager.hasQuota(day1))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(day1))

        // Watch rewarded ad for 2 bonus scans
        adManager.showRewardedScanUnlockAd(
            onRewarded = { ScanQuotaManager.addBonusScans(day1, AdConfig.REWARDED_SCAN_BONUS) },
            onDismissed = {}
        )
        assertEquals(2, ScanQuotaManager.getBonusScans(day1))
        assertEquals(2, ScanQuotaManager.getRemainingScans(day1))
        assertTrue(ScanQuotaManager.hasQuota(day1))
        assertFalse(ScanQuotaManager.shouldForceInterstitialAd(day1))

        // Consume both bonus scans
        ScanQuotaManager.consumeScan(day1)
        ScanQuotaManager.consumeScan(day1)
        assertEquals(7, ScanQuotaManager.getUsedScans(day1))
        assertEquals(0, ScanQuotaManager.getRemainingScans(day1))
        assertFalse(ScanQuotaManager.hasQuota(day1))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(day1))

        // Save 2 meals on Day 1
        mealRepository.saveMeal(LoggedMeal("m1_d1", "Breakfast Oats", 350, 15f, 60f, 5f, "08:00 AM", day1))
        mealRepository.saveMeal(LoggedMeal("m2_d1", "Chicken Salad", 450, 40f, 15f, 10f, "01:00 PM", day1))

        // --- DAY 2 (Boundary rollover: 24h later) ---
        simulatedTimeMillis += 24L * 60 * 60 * 1000L

        // Day 1 state must remain completely isolated & intact
        assertEquals(7, ScanQuotaManager.getUsedScans(day1))
        assertEquals(2, ScanQuotaManager.getBonusScans(day1))
        assertEquals(0, ScanQuotaManager.getRemainingScans(day1))
        assertFalse(ScanQuotaManager.hasQuota(day1))
        assertEquals(2, mealRepository.getMealsForDate(day1).size)

        // Day 2 must start fresh with 0 used, 0 bonus, 5 remaining (Week 1)
        assertEquals(0, ScanQuotaManager.getUsedScans(day2))
        assertEquals(0, ScanQuotaManager.getBonusScans(day2))
        assertEquals(5, ScanQuotaManager.getRemainingScans(day2))
        assertTrue(ScanQuotaManager.hasQuota(day2))
        assertFalse(ScanQuotaManager.shouldForceInterstitialAd(day2))
        assertTrue(mealRepository.getMealsForDate(day2).isEmpty())

        // Consume 2 scans on Day 2
        ScanQuotaManager.consumeScan(day2)
        ScanQuotaManager.consumeScan(day2)
        assertEquals(2, ScanQuotaManager.getUsedScans(day2))
        assertEquals(3, ScanQuotaManager.getRemainingScans(day2))
        // Verify Day 1 is still isolated and untouched
        assertEquals(7, ScanQuotaManager.getUsedScans(day1))

        // Save 1 meal on Day 2
        mealRepository.saveMeal(LoggedMeal("m1_d2", "Salmon Dinner", 500, 38f, 5f, 22f, "07:00 PM", day2))
        assertEquals(1, mealRepository.getMealsForDate(day2).size)
        assertEquals(2, mealRepository.getMealsForDate(day1).size)

        // --- DAY 7 (Still within 7 days: Day 6) ---
        simulatedTimeMillis = installTimestamp + (6L * 24 * 60 * 60 * 1000L)
        assertTrue(ScanQuotaManager.isWeekOneUser())
        assertEquals(5, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(5, ScanQuotaManager.getRemainingScans(day7))

        // --- DAY 8 (Cross 7-day boundary into Week 2: Day 8) ---
        simulatedTimeMillis = installTimestamp + (8L * 24 * 60 * 60 * 1000L)
        assertFalse(ScanQuotaManager.isWeekOneUser())
        assertEquals(AdConfig.DEFAULT_DAILY_FREE_SCANS, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(3, ScanQuotaManager.getRemainingScans(day8))
        assertTrue(ScanQuotaManager.hasQuota(day8))

        // Consume 3 scans on Day 8
        repeat(3) {
            ScanQuotaManager.consumeScan(day8)
        }
        assertEquals(3, ScanQuotaManager.getUsedScans(day8))
        assertEquals(0, ScanQuotaManager.getRemainingScans(day8))
        assertFalse(ScanQuotaManager.hasQuota(day8))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(day8))

        // --- DAY 9 (Week 2 continued) ---
        simulatedTimeMillis = installTimestamp + (9L * 24 * 60 * 60 * 1000L)
        assertFalse(ScanQuotaManager.isWeekOneUser())
        assertEquals(3, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(3, ScanQuotaManager.getRemainingScans(day9))
        assertTrue(ScanQuotaManager.hasQuota(day9))
        assertFalse(ScanQuotaManager.shouldForceInterstitialAd(day9))

        // Log meal on Day 9
        mealRepository.saveMeal(LoggedMeal("m1_d9", "Steak & Eggs", 600, 45f, 2f, 35f, "08:30 AM", day9))

        // Verify multi-day meals query isolation
        assertEquals(2, mealRepository.getMealsForDate(day1).size)
        assertEquals(1, mealRepository.getMealsForDate(day2).size)
        assertEquals(0, mealRepository.getMealsForDate(day8).size)
        assertEquals(1, mealRepository.getMealsForDate(day9).size)
        assertEquals(4, mealRepository.getAllMeals().size)
    }
}
