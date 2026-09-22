package com.fitter.app.telemetry

import com.fitter.app.getCurrentEpochMillis
import com.fitter.app.loadPreference
import com.fitter.app.savePreference
import kotlinx.serialization.Serializable

/**
 * Encapsulates revenue callback data from AppLovin MAX / AdMob Bidding.
 */
@Serializable
data class AdRevenuePayload(
    val adUnitId: String,
    val networkName: String,
    val revenue: Double, // Revenue in USD (e.g. 0.0035)
    val format: String, // "BANNER", "INTERSTITIAL", "REWARDED", "APP_OPEN"
    val placement: String = "",
    val creativeId: String = "",
    val timestampMillis: Long = 0L
)

/**
 * Snapshot summary of ad telemetry and monetization metrics.
 */
data class AdTelemetrySummary(
    val appOpenRequests: Long,
    val appOpenLoaded: Long,
    val appOpenImpressions: Long,
    val appOpenFillRate: Double,
    val appOpenImpressionRate: Double,
    val totalRevenueUsd: Double,
    val totalImpressions: Long,
    val estimatedEcpm: Double,
    val estimatedArpdau: Double
)

/**
 * Central telemetry manager for ad delivery performance and monetization analytics.
 * Tracks App Open ad requests, fill rates, impression triggers, and MAX revenue callbacks.
 * Computes live ARPDAU and eCPM metrics with persistent storage support.
 */
object AdTelemetryManager {

    private const val KEY_APP_OPEN_REQUESTS = "telemetry_ad_app_open_requests"
    private const val KEY_APP_OPEN_LOADED = "telemetry_ad_app_open_loaded"
    private const val KEY_APP_OPEN_FAILED = "telemetry_ad_app_open_failed"
    private const val KEY_APP_OPEN_IMPRESSIONS = "telemetry_ad_app_open_impressions"

    private const val KEY_TOTAL_REVENUE = "telemetry_ad_total_revenue_usd"
    private const val KEY_TOTAL_IMPRESSIONS = "telemetry_ad_total_impressions"

    private const val KEY_BANNER_IMPRESSIONS = "telemetry_ad_banner_impressions"
    private const val KEY_INTERSTITIAL_IMPRESSIONS = "telemetry_ad_interstitial_impressions"
    private const val KEY_REWARDED_IMPRESSIONS = "telemetry_ad_rewarded_impressions"

    private const val KEY_FORMAT_REVENUE_PREFIX = "telemetry_ad_rev_"

    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default ->
        loadPreference(key, default)
    }

    var preferenceWriter: (key: String, value: String) -> Unit = { key, value ->
        savePreference(key, value)
    }

    var currentTimeMillisProvider: () -> Long = { getCurrentEpochMillis() }

    private val revenueListeners = mutableListOf<(AdRevenuePayload) -> Unit>()

    fun addRevenueListener(listener: (AdRevenuePayload) -> Unit) {
        revenueListeners.add(listener)
    }

    fun removeRevenueListener(listener: (AdRevenuePayload) -> Unit) {
        revenueListeners.remove(listener)
    }

    fun resetForTesting() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
        currentTimeMillisProvider = { getCurrentEpochMillis() }
        revenueListeners.clear()
    }

    // --- App Open Ad Tracking ---

    fun trackAppOpenRequest() {
        val current = getAppOpenRequests()
        preferenceWriter(KEY_APP_OPEN_REQUESTS, (current + 1).toString())
    }

    fun trackAppOpenLoaded() {
        val current = getAppOpenLoaded()
        preferenceWriter(KEY_APP_OPEN_LOADED, (current + 1).toString())
    }

    fun trackAppOpenFailedToLoad(errorMessage: String? = null) {
        val current = preferenceReader(KEY_APP_OPEN_FAILED, "0").toLongOrNull() ?: 0L
        preferenceWriter(KEY_APP_OPEN_FAILED, (current + 1).toString())
        DiagnosticsCrashHook.logAdError("MAX/AdMob", "APP_OPEN", null, errorMessage ?: "App Open Ad failed to load")
    }

    fun trackAppOpenImpression() {
        val appOpenImp = getAppOpenImpressions()
        preferenceWriter(KEY_APP_OPEN_IMPRESSIONS, (appOpenImp + 1).toString())
        incrementTotalImpressions()
        com.fitter.shared.telemetry.TelemetryUploader.trackAdImpression("APP_OPEN")
    }

    fun getAppOpenRequests(): Long =
        preferenceReader(KEY_APP_OPEN_REQUESTS, "0").toLongOrNull() ?: 0L

    fun getAppOpenLoaded(): Long =
        preferenceReader(KEY_APP_OPEN_LOADED, "0").toLongOrNull() ?: 0L

    fun getAppOpenFailed(): Long =
        preferenceReader(KEY_APP_OPEN_FAILED, "0").toLongOrNull() ?: 0L

    fun getAppOpenImpressions(): Long =
        preferenceReader(KEY_APP_OPEN_IMPRESSIONS, "0").toLongOrNull() ?: 0L

    /**
     * App Open Fill Rate: loaded / requests.
     * Returns a ratio from 0.0 to 1.0 (or 0.0 if no requests).
     */
    fun getAppOpenFillRate(): Double {
        val requests = getAppOpenRequests()
        if (requests <= 0L) return 0.0
        return getAppOpenLoaded().toDouble() / requests.toDouble()
    }

    /**
     * App Open Impression Rate: impressions / loaded.
     * Evaluates display conversion rate.
     */
    fun getAppOpenImpressionRate(): Double {
        val loaded = getAppOpenLoaded()
        if (loaded <= 0L) return 0.0
        return getAppOpenImpressions().toDouble() / loaded.toDouble()
    }

    // --- Format Impressions Tracking ---

    fun trackInterstitialImpression() {
        val current = getInterstitialImpressions()
        preferenceWriter(KEY_INTERSTITIAL_IMPRESSIONS, (current + 1).toString())
        incrementTotalImpressions()
        com.fitter.shared.telemetry.TelemetryUploader.trackAdImpression("INTERSTITIAL")
    }

    fun trackRewardedImpression() {
        val current = getRewardedImpressions()
        preferenceWriter(KEY_REWARDED_IMPRESSIONS, (current + 1).toString())
        incrementTotalImpressions()
        com.fitter.shared.telemetry.TelemetryUploader.trackAdImpression("REWARDED")
    }

    fun trackBannerImpression() {
        val current = getBannerImpressions()
        preferenceWriter(KEY_BANNER_IMPRESSIONS, (current + 1).toString())
        incrementTotalImpressions()
        com.fitter.shared.telemetry.TelemetryUploader.trackAdImpression("BANNER")
    }

    fun getInterstitialImpressions(): Long =
        preferenceReader(KEY_INTERSTITIAL_IMPRESSIONS, "0").toLongOrNull() ?: 0L

    fun getRewardedImpressions(): Long =
        preferenceReader(KEY_REWARDED_IMPRESSIONS, "0").toLongOrNull() ?: 0L

    fun getBannerImpressions(): Long =
        preferenceReader(KEY_BANNER_IMPRESSIONS, "0").toLongOrNull() ?: 0L

    fun getTotalImpressions(): Long =
        preferenceReader(KEY_TOTAL_IMPRESSIONS, "0").toLongOrNull() ?: 0L

    private fun incrementTotalImpressions() {
        val current = getTotalImpressions()
        preferenceWriter(KEY_TOTAL_IMPRESSIONS, (current + 1).toString())
    }

    // --- MAX Revenue Tracking & ARPDAU / eCPM Bridging ---

    /**
     * Bridges AppLovin MAX `MaxAdRevenueListener.onAdRevenuePaid(ad)` callbacks
     * to cumulative revenue and financial metric tracking.
     */
    fun trackAdRevenue(payload: AdRevenuePayload) {
        val currentTotalRev = getTotalRevenueUsd()
        val updatedTotalRev = currentTotalRev + payload.revenue
        preferenceWriter(KEY_TOTAL_REVENUE, updatedTotalRev.toString())

        // Format-specific revenue
        val normFormat = payload.format.uppercase()
        if (normFormat.isNotBlank()) {
            val formatKey = "$KEY_FORMAT_REVENUE_PREFIX$normFormat"
            val currentFormatRev = preferenceReader(formatKey, "0.0").toDoubleOrNull() ?: 0.0
            preferenceWriter(formatKey, (currentFormatRev + payload.revenue).toString())
        }

        // Notify attached listeners
        revenueListeners.forEach { it.invoke(payload) }

        // Sinks revenue event to Supabase analytics_events table
        com.fitter.shared.telemetry.TelemetryUploader.trackAdRevenue(
            adUnitId = payload.adUnitId,
            networkName = payload.networkName,
            revenue = payload.revenue,
            format = payload.format,
            placement = payload.placement
        )
    }

    fun getTotalRevenueUsd(): Double =
        preferenceReader(KEY_TOTAL_REVENUE, "0.0").toDoubleOrNull() ?: 0.0

    fun getFormatRevenueUsd(format: String): Double {
        val formatKey = "$KEY_FORMAT_REVENUE_PREFIX${format.uppercase()}"
        return preferenceReader(formatKey, "0.0").toDoubleOrNull() ?: 0.0
    }

    fun getFormatImpressions(format: String): Long = when (format.uppercase()) {
        "BANNER" -> getBannerImpressions()
        "INTERSTITIAL" -> getInterstitialImpressions()
        "REWARDED" -> getRewardedImpressions()
        "APP_OPEN" -> getAppOpenImpressions()
        else -> 0L
    }

    /**
     * Estimated eCPM: (Total Revenue / Total Impressions) * 1000.
     * Optionally scoped to a specific ad format (e.g. "INTERSTITIAL").
     */
    fun calculateEcpm(format: String? = null): Double {
        val impressions = if (format != null) getFormatImpressions(format) else getTotalImpressions()
        val revenue = if (format != null) getFormatRevenueUsd(format) else getTotalRevenueUsd()
        if (impressions <= 0L) return 0.0
        return (revenue / impressions.toDouble()) * 1000.0
    }

    /**
     * Average Revenue Per Daily Active User (ARPDAU): Total Daily Ad Revenue / Daily Active Users.
     */
    fun calculateArpdau(dailyActiveUsers: Int = 1): Double {
        val users = dailyActiveUsers.coerceAtLeast(1)
        return getTotalRevenueUsd() / users.toDouble()
    }

    /**
     * Returns an aggregated snapshot of all monetization and telemetry performance metrics.
     */
    fun getTelemetrySummary(dailyActiveUsers: Int = 1): AdTelemetrySummary {
        return AdTelemetrySummary(
            appOpenRequests = getAppOpenRequests(),
            appOpenLoaded = getAppOpenLoaded(),
            appOpenImpressions = getAppOpenImpressions(),
            appOpenFillRate = getAppOpenFillRate(),
            appOpenImpressionRate = getAppOpenImpressionRate(),
            totalRevenueUsd = getTotalRevenueUsd(),
            totalImpressions = getTotalImpressions(),
            estimatedEcpm = calculateEcpm(),
            estimatedArpdau = calculateArpdau(dailyActiveUsers)
        )
    }
}
