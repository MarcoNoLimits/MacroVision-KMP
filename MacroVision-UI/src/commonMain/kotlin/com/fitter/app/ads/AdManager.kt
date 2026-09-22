package com.fitter.app.ads

import com.fitter.app.getCurrentEpochMillis
import com.fitter.app.loadPreference
import com.fitter.app.savePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    /** Preloads an App Open ad in the background. */
    fun preloadAppOpenAd() {}

    /** Shows an App Open ad if session count and cooldown requirements are met. */
    fun showAppOpenAdIfEligible(onDismissed: () -> Unit = {}) {}

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

    var remoteQuotaManager: com.fitter.shared.quota.SupabaseQuotaManager? = null

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
        currentTimeMillisProvider = { getCurrentEpochMillis() }
        remoteQuotaManager = null
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
        if (com.fitter.shared.subscription.SubscriptionManager.isPremiumUser()) {
            return 999
        }
        val used = getUsedScans(dateKey)
        val bonus = getBonusScans(dateKey)
        val totalAllowed = getDailyFreeLimit() + bonus
        return (totalAllowed - used).coerceAtLeast(0)
    }

    fun hasQuota(dateKey: String): Boolean {
        if (com.fitter.shared.subscription.SubscriptionManager.isPremiumUser()) {
            return true
        }
        return getRemainingScans(dateKey) > 0
    }

    /**
     * Determines if a forced interstitial scan processing ad must be shown.
     * When free/bonus quota is exhausted (hasQuota == false), the scan is NOT blocked,
     * but an interstitial ad MUST be shown before processing.
     * Fitter Premium subscribers receive unlimited ad-free scans.
     */
    fun shouldForceInterstitialAd(dateKey: String): Boolean {
        if (com.fitter.shared.subscription.SubscriptionManager.isPremiumUser()) {
            return false
        }
        return !hasQuota(dateKey)
    }

    fun consumeScan(dateKey: String) {
        val currentUsed = getUsedScans(dateKey)
        preferenceWriter("quota_used_$dateKey", (currentUsed + 1).toString())
    }

    /**
     * Consumes one scan:
     * 1. Calls server RPC (fail-closed).
     * 2. Returns:
     *    - `true`  → within allowance; local counter incremented
     *    - `false` → hard server denial; local counter still incremented (scan counted even if denied)
     *    - `null`  → network error; local counter incremented; caller shows offline banner
     *
     * UI should NOT allow VLM spend on `false`. On `null`, use cached values + offline banner.
     */
    suspend fun consumeScanServer(dateKey: String): Boolean? {
        val remote = remoteQuotaManager
        if (remote != null) {
            val result = remote.consumeScan(getDailyFreeLimit())
            consumeScan(dateKey)  // Always increment local counter
            return result         // null=offline, false=denied, true=ok
        }
        // No remote configured (tests / offline-only mode): use local cache
        val hadQuota = hasQuota(dateKey)
        consumeScan(dateKey)
        return hadQuota
    }

    /**
     * Syncs quota from server. On success, reconciles local cache to server snapshot.
     * On null (offline): keeps cached values and sets [isOffline] = true.
     * Returns remaining scans, or cached value if offline.
     */
    suspend fun syncQuotaFromServer(dateKey: String): Int {
        val remote = remoteQuotaManager ?: return getRemainingScans(dateKey)
        val snapshot = remote.fetchQuota(getDailyFreeLimit())
        return if (snapshot != null) {
            // Reconcile: server is authoritative; overwrite local cache
            preferenceWriter("quota_used_$dateKey", snapshot.used.toString())
            preferenceWriter("quota_bonus_$dateKey", snapshot.bonus.toString())
            preferenceWriter("quota_offline", "false")
            snapshot.remaining
        } else {
            // Network failure: mark as offline, use cached values
            preferenceWriter("quota_offline", "true")
            getRemainingScans(dateKey)
        }
    }

    /** Returns true if the last syncQuotaFromServer call failed (offline state). */
    fun isOffline(): Boolean {
        return preferenceReader("quota_offline", "false").toBoolean()
    }

    fun addBonusScans(dateKey: String, amount: Int = AdConfig.REWARDED_SCAN_BONUS) {
        val currentBonus = getBonusScans(dateKey)
        // Optimistic local write
        preferenceWriter("quota_bonus_$dateKey", (currentBonus + amount).toString())
        val remote = remoteQuotaManager
        if (remote != null) {
            CoroutineScope(Dispatchers.Default).launch {
                val serverBonus = try {
                    remote.grantBonusScan(amount)
                } catch (e: Exception) {
                    println("Failed to sync bonus scan to server: ${e.message}")
                    null
                }
                // Reconcile: if server returned a confirmed value, overwrite local
                if (serverBonus != null) {
                    preferenceWriter("quota_bonus_$dateKey", serverBonus.toString())
                }
            }
        }
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

    fun recordAppOpenAdRequested() {
        com.fitter.app.telemetry.AdTelemetryManager.trackAppOpenRequest()
    }

    fun recordAppOpenAdLoaded() {
        com.fitter.app.telemetry.AdTelemetryManager.trackAppOpenLoaded()
    }

    fun recordAppOpenAdFailedToLoad(errorMessage: String? = null) {
        com.fitter.app.telemetry.AdTelemetryManager.trackAppOpenFailedToLoad(errorMessage)
    }

    fun recordAppOpenAdShown() {
        preferenceWriter("last_app_open_ad_timestamp", currentTimeMillisProvider().toString())
        com.fitter.app.telemetry.AdTelemetryManager.trackAppOpenImpression()
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
