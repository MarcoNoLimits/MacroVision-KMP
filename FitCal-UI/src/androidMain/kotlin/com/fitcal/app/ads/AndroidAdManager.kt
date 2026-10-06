package com.fitcal.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.fitcal.app.telemetry.AdRevenuePayload
import com.fitcal.app.telemetry.AdTelemetryManager
import com.fitcal.app.telemetry.DiagnosticsCrashHook
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.lang.ref.WeakReference

class AndroidAdManager(
    private val contextProvider: () -> Context
) : AdManager {

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    private var isInterstitialLoading = false
    private var isRewardedLoading = false

    companion object {
        private const val TAG = "FitCal_Ads"
        var currentActivityRef: WeakReference<Activity>? = null

        /** Forwards AdMob paid-event revenue (reported in micros) to ad telemetry. */
        fun trackPaidEvent(value: AdValue, adUnitId: String, format: String, responseInfo: ResponseInfo?) {
            AdTelemetryManager.trackAdRevenue(
                AdRevenuePayload(
                    adUnitId = adUnitId,
                    networkName = responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "AdMob",
                    revenue = value.valueMicros / 1_000_000.0,
                    format = format,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
    }

    override fun preloadAds() {
        preloadInterstitial()
        preloadRewarded()
    }

    private fun preloadInterstitial() {
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        InterstitialAd.load(
            contextProvider(),
            AdConfig.ANDROID_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.setOnPaidEventListener { value ->
                        trackPaidEvent(value, ad.adUnitId, "INTERSTITIAL", ad.responseInfo)
                    }
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
                    DiagnosticsCrashHook.logAdError("AdMob", "INTERSTITIAL", loadAdError.code.toString(), loadAdError.message)
                }
            }
        )
    }

    private fun preloadRewarded() {
        if (rewardedAd != null || isRewardedLoading) return
        isRewardedLoading = true

        RewardedAd.load(
            contextProvider(),
            AdConfig.ANDROID_REWARDED,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.setOnPaidEventListener { value ->
                        trackPaidEvent(value, ad.adUnitId, "REWARDED", ad.responseInfo)
                    }
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.d(TAG, "Rewarded ad loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    DiagnosticsCrashHook.logAdError("AdMob", "REWARDED", loadAdError.code.toString(), loadAdError.message)
                }
            }
        )
    }

    override fun showScanProcessingAd(onFinished: () -> Unit) {
        val activity = currentActivityRef?.get()
        val ad = interstitialAd
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && ad != null) {
            activity.runOnUiThread {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        AdTelemetryManager.trackInterstitialImpression()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        preloadInterstitial()
                        onFinished()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                        DiagnosticsCrashHook.logAdError("AdMob", "INTERSTITIAL_DISPLAY", adError.code.toString(), adError.message)
                        interstitialAd = null
                        preloadInterstitial()
                        onFinished()
                    }
                }
                ad.show(activity)
            }
        } else {
            // The scan is never blocked by a missing ad.
            Log.d(TAG, "Interstitial not ready; continuing scan.")
            preloadAds()
            onFinished()
        }
    }

    override fun showRewardedScanUnlockAd(onRewarded: () -> Unit, onDismissed: () -> Unit) {
        val activity = currentActivityRef?.get()
        val ad = rewardedAd
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && ad != null) {
            activity.runOnUiThread {
                var rewardGranted = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        AdTelemetryManager.trackRewardedImpression()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        preloadRewarded()
                        if (rewardGranted) onRewarded()
                        onDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                        DiagnosticsCrashHook.logAdError("AdMob", "REWARDED_DISPLAY", adError.code.toString(), adError.message)
                        rewardedAd = null
                        preloadRewarded()
                        onDismissed()
                    }
                }
                ad.show(activity) { rewardGranted = true }
            }
        } else {
            // Leave the button active so the user can retry once the ad has loaded.
            Log.w(TAG, "Rewarded ad not ready; reloading.")
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    android.widget.Toast.makeText(
                        activity,
                        "Ad loading… please try again in a moment.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            preloadRewarded()
        }
    }

    override fun isInterstitialReady(): Boolean = interstitialAd != null

    override fun isRewardedReady(): Boolean = rewardedAd != null
}
