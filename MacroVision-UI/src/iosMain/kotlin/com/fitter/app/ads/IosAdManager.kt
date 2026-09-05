package com.fitter.app.ads

class IosAdManager : AdManager {

    var onShowInterstitialHandler: ((onFinished: () -> Unit) -> Unit)? = null
    var onShowRewardedHandler: ((onRewarded: () -> Unit, onDismissed: () -> Unit) -> Unit)? = null
    var isInterstitialReadyChecker: (() -> Boolean)? = null
    var isRewardedReadyChecker: (() -> Boolean)? = null
    var onPreloadHandler: (() -> Unit)? = null

    override fun showScanProcessingAd(onFinished: () -> Unit) {
        val handler = onShowInterstitialHandler
        if (handler != null) {
            handler(onFinished)
        } else {
            // Fallback for iOS when native AdMob delegate isn't wired yet
            onFinished()
        }
    }

    override fun showRewardedScanUnlockAd(onRewarded: () -> Unit, onDismissed: () -> Unit) {
        val handler = onShowRewardedHandler
        if (handler != null) {
            handler(onRewarded, onDismissed)
        } else {
            // Fallback: grant reward for testing if no ad SDK attached
            onRewarded()
            onDismissed()
        }
    }

    override fun isInterstitialReady(): Boolean = isInterstitialReadyChecker?.invoke() ?: true

    override fun isRewardedReady(): Boolean = isRewardedReadyChecker?.invoke() ?: true

    override fun preloadAds() {
        onPreloadHandler?.invoke()
    }
}
