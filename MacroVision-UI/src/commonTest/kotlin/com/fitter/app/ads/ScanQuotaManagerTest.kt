package com.fitter.app.ads

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanQuotaManagerTest {

    private val memoryStore = mutableMapOf<String, String>()
    private val testDateToday = "2026-09-05"
    private val testDateTomorrow = "2026-09-06"
    private var simulatedTimeMillis = 1000000000000L

    @BeforeTest
    fun setUp() {
        memoryStore.clear()
        simulatedTimeMillis = 1000000000000L

        // Default to a Week 2+ user (installed 10 days ago) so baseline 3-scan behavior is preserved
        val tenDaysAgo = simulatedTimeMillis - (10L * 24 * 60 * 60 * 1000L)
        memoryStore["first_install_timestamp"] = tenDaysAgo.toString()

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
    }

    @AfterTest
    fun tearDown() {
        ScanQuotaManager.resetToDefaults()
        AppOpenAdManager.resetToDefaults()
        memoryStore.clear()
    }

    @Test
    fun testInitialDailyQuota() {
        assertEquals(3, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(0, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getBonusScans(testDateToday))
        assertEquals(3, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))
    }

    @Test
    fun testConsumeScansDecrementsQuota() {
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(1, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(2, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))

        ScanQuotaManager.consumeScan(testDateToday)
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(3, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
    }

    @Test
    fun testZeroClampingWhenOverConsumed() {
        // Simulate over-consuming past 3 scans
        repeat(5) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(5, ScanQuotaManager.getUsedScans(testDateToday))
        // Remaining scans should clamp to 0 and not become negative
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
    }

    @Test
    fun testRewardedAdBonusScans() {
        // Exhaust initial free daily allowance
        repeat(3) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))

        // Watch rewarded ad (+2 bonus scans)
        ScanQuotaManager.addBonusScans(testDateToday, 2)
        assertEquals(2, ScanQuotaManager.getBonusScans(testDateToday))
        assertEquals(2, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))

        // Consume 1 bonus scan
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(1, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))

        // Consume last bonus scan
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
    }

    @Test
    fun testMultiDayQuotaIsolation() {
        // Exhaust quota on today
        repeat(3) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))

        // Tomorrow should have full fresh allowance
        assertEquals(3, ScanQuotaManager.getRemainingScans(testDateTomorrow))
        assertTrue(ScanQuotaManager.hasQuota(testDateTomorrow))
        assertEquals(0, ScanQuotaManager.getUsedScans(testDateTomorrow))
    }

    @Test
    fun testWeekOneUserGetsFiveScans() {
        // Simulate new user installed 2 days ago (Week 1)
        val twoDaysAgo = simulatedTimeMillis - (2L * 24 * 60 * 60 * 1000L)
        memoryStore["first_install_timestamp"] = twoDaysAgo.toString()

        assertTrue(ScanQuotaManager.isWeekOneUser())
        assertEquals(5, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(5, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))

        // Consume 5 scans
        repeat(5) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(5, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))
    }

    @Test
    fun testWeekTwoUserGetsThreeScans() {
        // Simulate user installed 8 days ago (Week 2+)
        val eightDaysAgo = simulatedTimeMillis - (8L * 24 * 60 * 60 * 1000L)
        memoryStore["first_install_timestamp"] = eightDaysAgo.toString()

        assertFalse(ScanQuotaManager.isWeekOneUser())
        assertEquals(3, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(3, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))

        repeat(3) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(3, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))
    }

    @Test
    fun testScanFourTriggersProcessingAdWithoutBlocking() {
        // Week 2 user with 3 free scans
        repeat(3) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))

        // Scan #4 is allowed: it consumes quota and increments scan count without throwing an error
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(4, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))
    }

    @Test
    fun testAppOpenAdRespectsSessionGracePeriodAndCooldown() {
        // Sessions 1-3 should NOT allow App Open ad (Grace Period)
        assertEquals(0, AppOpenAdManager.getSessionCount())
        assertFalse(AppOpenAdManager.canShowAppOpenAd())

        AppOpenAdManager.incrementSessionCount() // Session 1
        assertFalse(AppOpenAdManager.canShowAppOpenAd())

        AppOpenAdManager.incrementSessionCount() // Session 2
        assertFalse(AppOpenAdManager.canShowAppOpenAd())

        AppOpenAdManager.incrementSessionCount() // Session 3
        assertFalse(AppOpenAdManager.canShowAppOpenAd())

        // Session 4 allows App Open ad
        AppOpenAdManager.incrementSessionCount() // Session 4
        assertTrue(AppOpenAdManager.canShowAppOpenAd())

        // Show ad
        AppOpenAdManager.recordAppOpenAdShown()

        // Immediate check should be false (cooldown active)
        assertFalse(AppOpenAdManager.canShowAppOpenAd())

        // Advance simulated time by 3 hours (less than 4h cooldown)
        simulatedTimeMillis += 3L * 60 * 60 * 1000L
        assertFalse(AppOpenAdManager.canShowAppOpenAd())

        // Advance by another 1.1 hours (total > 4h)
        simulatedTimeMillis += (1L * 60 * 60 * 1000L + 60000L)
        assertTrue(AppOpenAdManager.canShowAppOpenAd())
    }
}
