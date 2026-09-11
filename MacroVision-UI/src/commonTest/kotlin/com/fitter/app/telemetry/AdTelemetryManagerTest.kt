package com.fitter.app.telemetry

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
        assertEquals(0L, AdTelemetryManager.getAppOpenRequests())
        assertEquals(0L, AdTelemetryManager.getAppOpenLoaded())
        assertEquals(0L, AdTelemetryManager.getAppOpenImpressions())
        assertEquals(0.0, AdTelemetryManager.getAppOpenFillRate())
        assertEquals(0.0, AdTelemetryManager.getAppOpenImpressionRate())
        assertEquals(0.0, AdTelemetryManager.getTotalRevenueUsd())
        assertEquals(0L, AdTelemetryManager.getTotalImpressions())
        assertEquals(0.0, AdTelemetryManager.calculateEcpm())
        assertEquals(0.0, AdTelemetryManager.calculateArpdau())
    }

    @Test
    fun testAppOpenAdRequestAndFillRateTracking() {
        // Request 1
        AdTelemetryManager.trackAppOpenRequest()
        assertEquals(1L, AdTelemetryManager.getAppOpenRequests())
        assertEquals(0L, AdTelemetryManager.getAppOpenLoaded())
        assertEquals(0.0, AdTelemetryManager.getAppOpenFillRate())

        // Load 1
        AdTelemetryManager.trackAppOpenLoaded()
        assertEquals(1L, AdTelemetryManager.getAppOpenLoaded())
        assertEquals(1.0, AdTelemetryManager.getAppOpenFillRate())

        // Request 2 (e.g. fill rate now 50%)
        AdTelemetryManager.trackAppOpenRequest()
        assertEquals(2L, AdTelemetryManager.getAppOpenRequests())
        assertEquals(0.5, AdTelemetryManager.getAppOpenFillRate())

        // Impression 1
        AdTelemetryManager.trackAppOpenImpression()
        assertEquals(1L, AdTelemetryManager.getAppOpenImpressions())
        assertEquals(1L, AdTelemetryManager.getTotalImpressions())
        assertEquals(1.0, AdTelemetryManager.getAppOpenImpressionRate())

        // Failure
        AdTelemetryManager.trackAppOpenFailedToLoad("Timeout loading App Open ad")
        assertEquals(1L, AdTelemetryManager.getAppOpenFailed())
    }

    @Test
    fun testImpressionsAcrossFormats() {
        AdTelemetryManager.trackInterstitialImpression()
        AdTelemetryManager.trackRewardedImpression()
        AdTelemetryManager.trackBannerImpression()
        AdTelemetryManager.trackAppOpenImpression()

        assertEquals(1L, AdTelemetryManager.getInterstitialImpressions())
        assertEquals(1L, AdTelemetryManager.getRewardedImpressions())
        assertEquals(1L, AdTelemetryManager.getBannerImpressions())
        assertEquals(1L, AdTelemetryManager.getAppOpenImpressions())
        assertEquals(4L, AdTelemetryManager.getTotalImpressions())
    }

    @Test
    fun testRevenueTrackingAndEcpmCalculation() {
        var listenerFired = false
        var capturedPayload: AdRevenuePayload? = null
        AdTelemetryManager.addRevenueListener { payload ->
            listenerFired = true
            capturedPayload = payload
        }

        // Record MAX Interstitial revenue: $0.025 with 1 impression
        val payload1 = AdRevenuePayload(
            adUnitId = "max_interstitial_unit",
            networkName = "AppLovin",
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

        // Record MAX Rewarded revenue: $0.050 with 1 impression
        val payload2 = AdRevenuePayload(
            adUnitId = "max_rewarded_unit",
            networkName = "Google Bidding",
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
        AdTelemetryManager.trackAppOpenRequest()
        AdTelemetryManager.trackAppOpenLoaded()
        AdTelemetryManager.trackAppOpenImpression()
        AdTelemetryManager.trackAdRevenue(
            AdRevenuePayload(
                adUnitId = "open_1",
                networkName = "Google",
                revenue = 0.02,
                format = "APP_OPEN"
            )
        )

        val summary = AdTelemetryManager.getTelemetrySummary(dailyActiveUsers = 2)
        assertEquals(1L, summary.appOpenRequests)
        assertEquals(1L, summary.appOpenLoaded)
        assertEquals(1L, summary.appOpenImpressions)
        assertEquals(1.0, summary.appOpenFillRate)
        assertEquals(1.0, summary.appOpenImpressionRate)
        assertEquals(0.02, summary.totalRevenueUsd)
        assertEquals(1L, summary.totalImpressions)
        assertEquals(20.0, summary.estimatedEcpm)
        assertEquals(0.01, summary.estimatedArpdau)
    }

    @Test
    fun testPersistenceSurvivesManagerReset() {
        AdTelemetryManager.trackAppOpenRequest()
        AdTelemetryManager.trackAppOpenLoaded()
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

        assertEquals(1L, AdTelemetryManager.getAppOpenRequests())
        assertEquals(1L, AdTelemetryManager.getAppOpenLoaded())
        assertEquals(0.05, AdTelemetryManager.getTotalRevenueUsd())
    }
}
