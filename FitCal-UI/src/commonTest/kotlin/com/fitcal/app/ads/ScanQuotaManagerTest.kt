package com.fitcal.app.ads

import com.fitcal.app.telemetry.AdTelemetryManager
import com.fitcal.shared.quota.QuotaSnapshot
import com.fitcal.shared.quota.SupabaseQuotaManager
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

        AdTelemetryManager.preferenceReader = { key, default ->
            memoryStore[key] ?: default
        }
        AdTelemetryManager.preferenceWriter = { key, value ->
            memoryStore[key] = value
        }
        AdTelemetryManager.currentTimeMillisProvider = { simulatedTimeMillis }

        // TelemetryUploader: inject no-op userIdProvider so trackAdImpression() coroutine
        // doesn't touch Android platform preferences in JVM unit tests.
        com.fitcal.shared.telemetry.TelemetryUploader.userIdProvider = { "test-user-id" }
    }

    @AfterTest
    fun tearDown() {
        ScanQuotaManager.resetToDefaults()
        AdTelemetryManager.resetForTesting()
        com.fitcal.shared.subscription.SubscriptionManager.resetForTesting()
        com.fitcal.shared.telemetry.TelemetryUploader.userIdProvider = { null }
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
    fun testFitCalPremiumEntitlementGrantsUnlimitedScansAndSuppressesAds() {
        // Given a user with exhausted daily quota (Week 2+, 3 used scans)
        repeat(3) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))

        // When user purchases FitCal Premium ($4.99/mo or $39.99/yr)
        com.fitcal.shared.subscription.SubscriptionManager.setPremiumStatus(true, com.fitcal.shared.subscription.SubscriptionManager.PRODUCT_MONTHLY)

        // Then quota is unlimited and all forced ads are completely suppressed
        assertTrue(com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser())
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))
        assertFalse(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))
        assertEquals(999, ScanQuotaManager.getRemainingScans(testDateToday))

        // Even after additional scans, ads remain suppressed
        ScanQuotaManager.consumeScan(testDateToday)
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))
        assertFalse(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))

        // When subscription expires/cancels
        com.fitcal.shared.subscription.SubscriptionManager.setPremiumStatus(false)
        assertFalse(com.fitcal.shared.subscription.SubscriptionManager.isPremiumUser())
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
        assertTrue(ScanQuotaManager.shouldForceInterstitialAd(testDateToday))
    }

    private class FakeSupabaseQuotaManager(
        var consumeResult: Boolean? = true,
        var snapshotResult: QuotaSnapshot? = null,
        var bonusResult: Int? = 2
    ) : SupabaseQuotaManager() {
        override suspend fun consumeScan(allowance: Int): Boolean? = consumeResult
        override suspend fun fetchQuota(allowance: Int): QuotaSnapshot? = snapshotResult
        override suspend fun grantBonusScan(amount: Int): Int? = bonusResult
    }

    @Test
    fun testOfflineUnknownReturnsNullAndMarksOffline() = runBlocking {
        val fakeRemote = FakeSupabaseQuotaManager(
            consumeResult = null,
            snapshotResult = null
        )
        ScanQuotaManager.remoteQuotaManager = fakeRemote

        // 1. consumeScanServer returns null (unknown state) on network error
        val result = ScanQuotaManager.consumeScanServer(testDateToday)
        assertNull(result)
        // Local counter was still incremented (fail-closed counting)
        assertEquals(1, ScanQuotaManager.getUsedScans(testDateToday))

        // 2. syncQuotaFromServer returns cached value and marks isOffline = true
        val remaining = ScanQuotaManager.syncQuotaFromServer(testDateToday)
        assertEquals(2, remaining) // 3 limit - 1 used
        assertTrue(ScanQuotaManager.isOffline())
    }

    @Test
    fun testHardDenialReturnsFalseWhenQuotaExhausted() = runBlocking {
        val fakeRemote = FakeSupabaseQuotaManager(
            consumeResult = false
        )
        ScanQuotaManager.remoteQuotaManager = fakeRemote

        // consumeScanServer returns false on hard server denial
        val result = ScanQuotaManager.consumeScanServer(testDateToday)
        assertEquals(false, result)
        // Scan was still counted locally
        assertEquals(1, ScanQuotaManager.getUsedScans(testDateToday))
    }

    @Test
    fun testBonusReconciliationOverwritesLocalOvercount() = runBlocking {
        // Local has stale overcount (e.g. 4 used, 0 bonus)
        memoryStore["quota_used_$testDateToday"] = "4"
        memoryStore["quota_bonus_$testDateToday"] = "0"
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))

        // Server authoritative snapshot says: 1 used, 2 bonus, 4 remaining (3 limit + 2 bonus - 1 used)
        val serverSnapshot = QuotaSnapshot(
            used = 1,
            bonus = 2,
            remaining = 4,
            first_install_date = "2026-09-01T00:00:00Z"
        )
        val fakeRemote = FakeSupabaseQuotaManager(
            snapshotResult = serverSnapshot
        )
        ScanQuotaManager.remoteQuotaManager = fakeRemote

        val remaining = ScanQuotaManager.syncQuotaFromServer(testDateToday)
        assertEquals(4, remaining)
        // Stale local overcount was overwritten with server truth
        assertEquals(1, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(2, ScanQuotaManager.getBonusScans(testDateToday))
        assertFalse(ScanQuotaManager.isOffline())
    }

    @Test
    fun testDebugBuildRoutesAllAdUnitsToGoogleTestIds() {
        assertTrue(com.fitcal.app.isDebugBuild)
        assertEquals(AdConfig.ANDROID_TEST_BANNER, AdConfig.ANDROID_BANNER)
        assertEquals(AdConfig.ANDROID_TEST_INTERSTITIAL, AdConfig.ANDROID_INTERSTITIAL)
        assertEquals(AdConfig.ANDROID_TEST_REWARDED, AdConfig.ANDROID_REWARDED)
    }
}

