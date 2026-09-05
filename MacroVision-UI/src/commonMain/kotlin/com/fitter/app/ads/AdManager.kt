package com.fitter.app.ads

import com.fitter.app.loadPreference
import com.fitter.app.savePreference

/**
 * Standard Google AdMob official test ad unit IDs.
 * Replace these with your live AdMob ad unit IDs when ready for production.
 */
object AdConfig {
    // Android Test Ad Units
    const val ANDROID_TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val ANDROID_TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val ANDROID_TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val ANDROID_TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    // iOS Test Ad Units
    const val IOS_TEST_APP_ID = "ca-app-pub-3940256099942544~1458002511"
    const val IOS_TEST_BANNER = "ca-app-pub-3940256099942544/2934735716"
    const val IOS_TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/4411468910"
    const val IOS_TEST_REWARDED = "ca-app-pub-3940256099942544/1712485313"

    // Monetization Quota Defaults
    const val DEFAULT_DAILY_FREE_SCANS = 3
    const val REWARDED_SCAN_BONUS = 2
}

/**
 * Platform-independent abstraction for full-screen and rewarded ads.
 */
interface AdManager {
    /**
     * Shows an ad during food scan processing.
     * Invokes [onFinished] when the ad is closed or fails to display.
     */
    fun showScanProcessingAd(onFinished: () -> Unit)

    /**
     * Shows a rewarded video ad to unlock extra scans.
     * Invokes [onRewarded] if the user successfully watched the ad to completion,
     * and [onDismissed] when the ad is closed.
     */
    fun showRewardedScanUnlockAd(onRewarded: () -> Unit, onDismissed: () -> Unit)

    /** Returns true if an interstitial ad is preloaded and ready to show. */
    fun isInterstitialReady(): Boolean

    /** Returns true if a rewarded ad is preloaded and ready to show. */
    fun isRewardedReady(): Boolean

    /** Preloads ads in the background. */
    fun preloadAds()
}

/**
 * Manages daily scan quotas to protect against unbounded AI VLM token costs.
 * Grants [AdConfig.DEFAULT_DAILY_FREE_SCANS] free scans every day.
 * Users can unlock additional scans with rewarded ads.
 */
object ScanQuotaManager {

    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default ->
        loadPreference(key, default)
    }

    var preferenceWriter: (key: String, value: String) -> Unit = { key, value ->
        savePreference(key, value)
    }

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
    }

    fun getDailyFreeLimit(): Int = AdConfig.DEFAULT_DAILY_FREE_SCANS

    fun getUsedScans(dateKey: String): Int {
        return preferenceReader("quota_used_$dateKey", "0").toIntOrNull() ?: 0
    }

    fun getBonusScans(dateKey: String): Int {
        return preferenceReader("quota_bonus_$dateKey", "0").toIntOrNull() ?: 0
    }

    fun getRemainingScans(dateKey: String): Int {
        val used = getUsedScans(dateKey)
        val bonus = getBonusScans(dateKey)
        val totalAllowed = getDailyFreeLimit() + bonus
        return (totalAllowed - used).coerceAtLeast(0)
    }

    fun hasQuota(dateKey: String): Boolean {
        return getRemainingScans(dateKey) > 0
    }

    fun consumeScan(dateKey: String) {
        val currentUsed = getUsedScans(dateKey)
        preferenceWriter("quota_used_$dateKey", (currentUsed + 1).toString())
    }

    fun addBonusScans(dateKey: String, amount: Int = AdConfig.REWARDED_SCAN_BONUS) {
        val currentBonus = getBonusScans(dateKey)
        preferenceWriter("quota_bonus_$dateKey", (currentBonus + amount).toString())
    }
}
