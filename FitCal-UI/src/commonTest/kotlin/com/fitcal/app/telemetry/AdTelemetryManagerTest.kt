package com.fitcal.app.telemetry

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdTelemetryManagerTest {

    private val memoryStore = mutableMapOf<String, String>()

    @BeforeTest
    fun setUp() {
        memoryStore.clear()
        AdTelemetryManager.preferenceReader = { key, default ->
            memoryStore[key] ?: default
        }
        AdTelemetryManager.preferenceWriter = { key, value ->
            memoryStore[key] = value
        }
    }

    @AfterTest
    fun tearDown() {
        AdTelemetryManager.resetForTesting()
        memoryStore.clear()
    }

    @Test
    fun testInitialTelemetryState() {
        assertEquals(0.0, AdTelemetryManager.getTotalRevenueUsd())
        assertEquals(0L, AdTelemetryManager.getTotalImpressions())
        assertEquals(0.0, AdTelemetryManager.calculateEcpm())
        assertEquals(0.0, AdTelemetryManager.calculateArpdau())
    }

    @Test
    fun testImpressionsAcrossFormats() {
        AdTelemetryManager.trackInterstitialImpression()
        AdTelemetryManager.trackRewardedImpression()
        AdTelemetryManager.trackBannerImpression()

        assertEquals(1L, AdTelemetryManager.getInterstitialImpressions())
        assertEquals(1L, AdTelemetryManager.getRewardedImpressions())
        assertEquals(1L, AdTelemetryManager.getBannerImpressions())
        assertEquals(3L, AdTelemetryManager.getTotalImpressions())
    }

    @Test
    fun testRevenueTrackingAndEcpmCalculation() {
        var listenerFired = false
        var capturedPayload: AdRevenuePayload? = null
        AdTelemetryManager.addRevenueListener { payload ->
            listenerFired = true
            capturedPayload = payload
        }

        // Record interstitial revenue: $0.025 with 1 impression
        val payload1 = AdRevenuePayload(
            adUnitId = "interstitial_unit",
            networkName = "AdMob",
            revenue = 0.025,
            format = "INTERSTITIAL",
            placement = "scan_processing"
        )
        AdTelemetryManager.trackInterstitialImpression()
        AdTelemetryManager.trackAdRevenue(payload1)

        assertTrue(listenerFired)
        assertEquals(payload1, capturedPayload)
        assertEquals(0.025, AdTelemetryManager.getTotalRevenueUsd())
        assertEquals(0.025, AdTelemetryManager.getFormatRevenueUsd("INTERSTITIAL"))

        // eCPM for 1 impression earning $0.025 = (0.025 / 1) * 1000 = $25.0
        assertEquals(25.0, AdTelemetryManager.calculateEcpm())
        assertEquals(25.0, AdTelemetryManager.calculateEcpm("INTERSTITIAL"))

        // Record rewarded revenue: $0.050 with 1 impression
        val payload2 = AdRevenuePayload(
            adUnitId = "rewarded_unit",
            networkName = "AdMob",
            revenue = 0.050,
            format = "REWARDED",
            placement = "quota_unlock"
        )
        AdTelemetryManager.trackRewardedImpression()
        AdTelemetryManager.trackAdRevenue(payload2)
        assertEquals(0.075, AdTelemetryManager.getTotalRevenueUsd(), 0.0001)
        assertEquals(0.050, AdTelemetryManager.getFormatRevenueUsd("REWARDED"), 0.0001)
        assertEquals(2L, AdTelemetryManager.getTotalImpressions())

        // Cumulative eCPM: ($0.075 / 2) * 1000 = $37.5
        assertEquals(37.5, AdTelemetryManager.calculateEcpm(), 0.0001)
    }

    @Test
    fun testArpdauCalculation() {
        AdTelemetryManager.trackAdRevenue(
            AdRevenuePayload(
                adUnitId = "unit_1",
                networkName = "Meta",
                revenue = 0.10,
                format = "INTERSTITIAL"
            )
        )

        // 10 active users -> ARPDAU = $0.10 / 10 = $0.01
        assertEquals(0.01, AdTelemetryManager.calculateArpdau(dailyActiveUsers = 10))

        // Edge case: 0 active users defaults safely to 1
        assertEquals(0.10, AdTelemetryManager.calculateArpdau(dailyActiveUsers = 0))
    }

    @Test
    fun testTelemetrySummaryGeneration() {
        AdTelemetryManager.trackBannerImpression()
        AdTelemetryManager.trackAdRevenue(
            AdRevenuePayload(
                adUnitId = "banner_1",
                networkName = "AdMob",
                revenue = 0.02,
                format = "BANNER"
            )
        )

        val summary = AdTelemetryManager.getTelemetrySummary(dailyActiveUsers = 2)
        assertEquals(0.02, summary.totalRevenueUsd)
        assertEquals(1L, summary.totalImpressions)
        assertEquals(20.0, summary.estimatedEcpm)
        assertEquals(0.01, summary.estimatedArpdau)
    }

    @Test
    fun testPersistenceSurvivesManagerReset() {
        AdTelemetryManager.trackRewardedImpression()
        AdTelemetryManager.trackAdRevenue(
            AdRevenuePayload(
                adUnitId = "u1",
                networkName = "Unity",
                revenue = 0.05,
                format = "REWARDED"
            )
        )

        // Simulate new app session attaching to existing persistence
        AdTelemetryManager.resetForTesting()
        AdTelemetryManager.preferenceReader = { key, default -> memoryStore[key] ?: default }
        AdTelemetryManager.preferenceWriter = { key, value -> memoryStore[key] = value }

        assertEquals(1L, AdTelemetryManager.getRewardedImpressions())
        assertEquals(0.05, AdTelemetryManager.getTotalRevenueUsd())
    }
}
