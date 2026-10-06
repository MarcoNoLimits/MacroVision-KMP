package com.fitcal.app.telemetry

import com.fitcal.app.getCurrentEpochMillis
import com.fitcal.app.loadPreference
import com.fitcal.app.savePreference
import kotlinx.serialization.Serializable

/**
 * Revenue for one ad impression, from AdMob paid-event callbacks.
 */
@Serializable
data class AdRevenuePayload(
    val adUnitId: String,
    val networkName: String,
    val revenue: Double, // Revenue in USD (e.g. 0.0035)
    val format: String, // "BANNER", "INTERSTITIAL", "REWARDED"
    val placement: String = "",
    val creativeId: String = "",
    val timestampMillis: Long = 0L
)

/**
 * Snapshot summary of ad telemetry and monetization metrics.
 */
data class AdTelemetrySummary(
    val totalRevenueUsd: Double,
    val totalImpressions: Long,
    val estimatedEcpm: Double,
    val estimatedArpdau: Double
)

/**
 * Central telemetry manager for ad delivery performance and monetization analytics.
 * Tracks impressions per format and AdMob paid-event revenue.
 * Computes live ARPDAU and eCPM metrics with persistent storage support.
 */
object AdTelemetryManager {


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

    // --- Format Impressions Tracking ---

    fun trackInterstitialImpression() {
        val current = getInterstitialImpressions()
        preferenceWriter(KEY_INTERSTITIAL_IMPRESSIONS, (current + 1).toString())
        incrementTotalImpressions()
        com.fitcal.shared.telemetry.TelemetryUploader.trackAdImpression("INTERSTITIAL")
    }

    fun trackRewardedImpression() {
        val current = getRewardedImpressions()
        preferenceWriter(KEY_REWARDED_IMPRESSIONS, (current + 1).toString())
        incrementTotalImpressions()
        com.fitcal.shared.telemetry.TelemetryUploader.trackAdImpression("REWARDED")
    }

    fun trackBannerImpression() {
        val current = getBannerImpressions()
        preferenceWriter(KEY_BANNER_IMPRESSIONS, (current + 1).toString())
        incrementTotalImpressions()
        com.fitcal.shared.telemetry.TelemetryUploader.trackAdImpression("BANNER")
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

    // --- Revenue Tracking & ARPDAU / eCPM ---

    /**
     * Records AdMob `OnPaidEventListener` revenue
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
        com.fitcal.shared.telemetry.TelemetryUploader.trackAdRevenue(
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
            totalRevenueUsd = getTotalRevenueUsd(),
            totalImpressions = getTotalImpressions(),
            estimatedEcpm = calculateEcpm(),
            estimatedArpdau = calculateArpdau(dailyActiveUsers)
        )
    }
}
