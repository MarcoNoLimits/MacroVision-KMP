package com.fitter.app.ads

import com.fitter.app.getCurrentEpochMillis
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
    const val ANDROID_TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257390401"

    // iOS Test Ad Units
    const val IOS_TEST_APP_ID = "ca-app-pub-3940256099942544~1458002511"
    const val IOS_TEST_BANNER = "ca-app-pub-3940256099942544/2934735716"
    const val IOS_TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/4411468910"
    const val IOS_TEST_REWARDED = "ca-app-pub-3940256099942544/1712485313"
    const val IOS_TEST_APP_OPEN = "ca-app-pub-3940256099942544/5575463023"

    // AppLovin MAX Unified Mediation Units (Task 3: MAX + AdMob real-time bidding + Meta/Unity/Mintegral)
    var isProductionMediationEnabled: Boolean = false
    var maxSdkKey: String = ""
    var maxAndroidInterstitialId: String = ""
    var maxAndroidRewardedId: String = ""
    var maxAndroidBannerId: String = ""
    var maxAndroidAppOpenId: String = ""

    var maxIosInterstitialId: String = ""
    var maxIosRewardedId: String = ""
    var maxIosBannerId: String = ""
    var maxIosAppOpenId: String = ""

    // Brand Safety Categories (Task 6: Strict Category Filtering)
    val BLOCKED_AD_CATEGORIES = listOf(
        "Gambling",
        "Dating",
        "Politics",
        "Religion",
        "Cosmetic Surgery",
        "Sexual Health",
        "Unverified Apps",
        "Low-quality Clickbait"
    )
    val ALLOWED_AD_CATEGORIES = listOf(
        "Fitness",
        "Food & Beverage",
        "Sports",
        "Technology",
        "Productivity",
        "Mobile Games"
    )

    // Monetization Quota Defaults
    const val WEEK_ONE_DAILY_FREE_SCANS = 5
    const val DEFAULT_DAILY_FREE_SCANS = 3
    const val REWARDED_SCAN_BONUS = 2
    const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000L
    const val APP_OPEN_COOLDOWN_MILLIS = 4L * 60 * 60 * 1000L
    const val APP_OPEN_SESSION_GRACE_COUNT = 3
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

    /** Opens AppLovin MAX Mediation Debugger for on-device testing and certification. */
    fun showMediationDebugger() {}
}

/**
 * Manages daily scan quotas to protect against unbounded AI VLM token costs.
 * Grants [AdConfig.WEEK_ONE_DAILY_FREE_SCANS] (5) scans/day during Week 1 (first 7 days),
 * and [AdConfig.DEFAULT_DAILY_FREE_SCANS] (3) scans/day from Day 8 onward.
 * Scans beyond the daily quota trigger a forced interstitial ad before processing without blocking dialogs.
 */
object ScanQuotaManager {

    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default ->
        loadPreference(key, default)
    }

    var preferenceWriter: (key: String, value: String) -> Unit = { key, value ->
        savePreference(key, value)
    }

    var currentTimeMillisProvider: () -> Long = { getCurrentEpochMillis() }

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
        currentTimeMillisProvider = { getCurrentEpochMillis() }
    }

    fun getFirstInstallTimestamp(): Long {
        val saved = preferenceReader("first_install_timestamp", "")
        if (saved.isNotEmpty()) {
            return saved.toLongOrNull() ?: currentTimeMillisProvider()
        }
        val now = currentTimeMillisProvider()
        preferenceWriter("first_install_timestamp", now.toString())
        return now
    }

    fun isWeekOneUser(): Boolean {
        val installed = getFirstInstallTimestamp()
        val now = currentTimeMillisProvider()
        return (now - installed) < AdConfig.SEVEN_DAYS_MILLIS
    }

    fun getDailyFreeLimit(): Int {
        return if (isWeekOneUser()) {
            AdConfig.WEEK_ONE_DAILY_FREE_SCANS
        } else {
            AdConfig.DEFAULT_DAILY_FREE_SCANS
        }
    }

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

    /**
     * Determines if a forced interstitial scan processing ad must be shown.
     * When free/bonus quota is exhausted (hasQuota == false), the scan is NOT blocked,
     * but an interstitial ad MUST be shown before processing.
     */
    fun shouldForceInterstitialAd(dateKey: String): Boolean {
        return !hasQuota(dateKey)
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

/**
 * Manages App Open Ad eligibility, enforcing a grace period and cooldown.
 * Policy:
 * 1. Grace period: Sessions 1, 2, 3 have zero App Open ads.
 * 2. Cooldown: Minimum 4 hours between impressions.
 */
object AppOpenAdManager {
    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default ->
        loadPreference(key, default)
    }

    var preferenceWriter: (key: String, value: String) -> Unit = { key, value ->
        savePreference(key, value)
    }

    var currentTimeMillisProvider: () -> Long = { getCurrentEpochMillis() }

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
        currentTimeMillisProvider = { getCurrentEpochMillis() }
    }

    fun getSessionCount(): Int {
        return preferenceReader("session_count", "0").toIntOrNull() ?: 0
    }

    fun incrementSessionCount(): Int {
        val newCount = getSessionCount() + 1
        preferenceWriter("session_count", newCount.toString())
        return newCount
    }

    fun getLastAppOpenAdTimestamp(): Long {
        return preferenceReader("last_app_open_ad_timestamp", "0").toLongOrNull() ?: 0L
    }

    fun recordAppOpenAdShown() {
        preferenceWriter("last_app_open_ad_timestamp", currentTimeMillisProvider().toString())
    }

    fun canShowAppOpenAd(): Boolean {
        val sessionCount = getSessionCount()
        if (sessionCount <= AdConfig.APP_OPEN_SESSION_GRACE_COUNT) {
            return false
        }
        val lastShown = getLastAppOpenAdTimestamp()
        val now = currentTimeMillisProvider()
        return (now - lastShown) >= AdConfig.APP_OPEN_COOLDOWN_MILLIS
    }
}
