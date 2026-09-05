package com.fitter.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
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
        private const val TAG = "MacroVision_AdMob"
        var currentActivityRef: WeakReference<Activity>? = null
    }

    override fun preloadAds() {
        preloadInterstitial()
        preloadRewarded()
    }

    private fun preloadInterstitial() {
        if (interstitialAd != null || isInterstitialLoading) return
        val context = contextProvider()
        isInterstitialLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdConfig.ANDROID_TEST_INTERSTITIAL,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "AdMob Interstitial loaded successfully.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "AdMob Interstitial failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    private fun preloadRewarded() {
        if (rewardedAd != null || isRewardedLoading) return
        val context = contextProvider()
        isRewardedLoading = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AdConfig.ANDROID_TEST_REWARDED,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.d(TAG, "AdMob Rewarded Ad loaded successfully.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "AdMob Rewarded Ad failed to load: ${loadAdError.message}")
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
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed.")
                        interstitialAd = null
                        preloadInterstitial()
                        onFinished()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Interstitial ad failed to show: ${adError.message}")
                        interstitialAd = null
                        preloadInterstitial()
                        onFinished()
                    }
                }
                ad.show(activity)
            }
        } else {
            // If ad is not ready, don't stall the user experience
            Log.d(TAG, "Interstitial not ready or activity unavailable, continuing scan directly.")
            preloadInterstitial()
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
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Rewarded ad dismissed.")
                        rewardedAd = null
                        preloadRewarded()
                        if (rewardGranted) {
                            onRewarded()
                        }
                        onDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                        rewardedAd = null
                        preloadRewarded()
                        onDismissed()
                    }
                }
                ad.show(activity) { rewardItem ->
                    Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                    rewardGranted = true
                }
            }
        } else {
            Log.w(TAG, "Rewarded ad not ready yet. Preloading and dismissing.")
            preloadRewarded()
            onDismissed()
        }
    }

    override fun isInterstitialReady(): Boolean = interstitialAd != null

    override fun isRewardedReady(): Boolean = rewardedAd != null
}
