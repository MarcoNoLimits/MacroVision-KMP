package com.fitter.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
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

    // AdMob Direct Test Units
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    // AppLovin MAX Mediation Units
    private var maxInterstitialAd: MaxInterstitialAd? = null
    private var maxRewardedAd: MaxRewardedAd? = null

    private var isInterstitialLoading = false
    private var isRewardedLoading = false

    companion object {
        private const val TAG = "Fitter_Ads"
        var currentActivityRef: WeakReference<Activity>? = null
    }

    override fun preloadAds() {
        if (AdConfig.isProductionMediationEnabled && AdConfig.maxAndroidInterstitialId.isNotBlank()) {
            preloadMaxInterstitial()
            preloadMaxRewarded()
        } else {
            preloadAdMobInterstitial()
            preloadAdMobRewarded()
        }
    }

    private fun preloadMaxInterstitial() {
        val activity = currentActivityRef?.get() ?: return
        if (maxInterstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        val ad = MaxInterstitialAd(AdConfig.maxAndroidInterstitialId, activity)
        ad.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                isInterstitialLoading = false
                Log.d(TAG, "MAX Interstitial loaded successfully.")
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                isInterstitialLoading = false
                Log.w(TAG, "MAX Interstitial failed to load: ${error.message}")
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG, "MAX Interstitial displayed.")
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG, "MAX Interstitial hidden.")
                maxInterstitialAd = null
                preloadMaxInterstitial()
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(TAG, "MAX Interstitial clicked.")
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.w(TAG, "MAX Interstitial display failed: ${error.message}")
                maxInterstitialAd = null
                preloadMaxInterstitial()
            }
        })
        ad.loadAd()
        maxInterstitialAd = ad
    }

    private fun preloadMaxRewarded() {
        val activity = currentActivityRef?.get() ?: return
        if (maxRewardedAd != null || isRewardedLoading) return
        isRewardedLoading = true

        val ad = MaxRewardedAd.getInstance(AdConfig.maxAndroidRewardedId, activity)
        ad.setListener(object : MaxRewardedAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                isRewardedLoading = false
                Log.d(TAG, "MAX Rewarded ad loaded successfully.")
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                isRewardedLoading = false
                Log.w(TAG, "MAX Rewarded ad failed to load: ${error.message}")
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG, "MAX Rewarded displayed.")
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG, "MAX Rewarded hidden.")
                maxRewardedAd = null
                preloadMaxRewarded()
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(TAG, "MAX Rewarded clicked.")
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.w(TAG, "MAX Rewarded display failed: ${error.message}")
                maxRewardedAd = null
                preloadMaxRewarded()
            }

            override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                Log.d(TAG, "User rewarded via MAX: ${reward.amount} ${reward.label}")
            }
        })
        ad.loadAd()
        maxRewardedAd = ad
    }

    private fun preloadAdMobInterstitial() {
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

    private fun preloadAdMobRewarded() {
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

        if (AdConfig.isProductionMediationEnabled && maxInterstitialAd?.isReady == true && activity != null) {
            activity.runOnUiThread {
                maxInterstitialAd?.setListener(object : MaxAdListener {
                    override fun onAdLoaded(ad: MaxAd) {}
                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
                    override fun onAdDisplayed(ad: MaxAd) {}
                    override fun onAdHidden(ad: MaxAd) {
                        maxInterstitialAd = null
                        preloadMaxInterstitial()
                        onFinished()
                    }
                    override fun onAdClicked(ad: MaxAd) {}
                    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                        maxInterstitialAd = null
                        preloadMaxInterstitial()
                        onFinished()
                    }
                })
                maxInterstitialAd?.showAd(activity)
            }
            return
        }

        val ad = interstitialAd
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && ad != null) {
            activity.runOnUiThread {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed.")
                        interstitialAd = null
                        preloadAdMobInterstitial()
                        onFinished()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Interstitial ad failed to show: ${adError.message}")
                        interstitialAd = null
                        preloadAdMobInterstitial()
                        onFinished()
                    }
                }
                ad.show(activity)
            }
        } else {
            Log.d(TAG, "Interstitial not ready or activity unavailable, continuing scan directly.")
            preloadAds()
            onFinished()
        }
    }

    override fun showRewardedScanUnlockAd(onRewarded: () -> Unit, onDismissed: () -> Unit) {
        val activity = currentActivityRef?.get()

        if (AdConfig.isProductionMediationEnabled && maxRewardedAd?.isReady == true && activity != null) {
            activity.runOnUiThread {
                var rewardGranted = false
                maxRewardedAd?.setListener(object : MaxRewardedAdListener {
                    override fun onAdLoaded(ad: MaxAd) {}
                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
                    override fun onAdDisplayed(ad: MaxAd) {}
                    override fun onAdHidden(ad: MaxAd) {
                        maxRewardedAd = null
                        preloadMaxRewarded()
                        if (rewardGranted) onRewarded()
                        onDismissed()
                    }
                    override fun onAdClicked(ad: MaxAd) {}
                    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                        maxRewardedAd = null
                        preloadMaxRewarded()
                        onDismissed()
                    }
                    override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                        rewardGranted = true
                    }
                })
                maxRewardedAd?.showAd(activity)
            }
            return
        }

        val ad = rewardedAd
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && ad != null) {
            activity.runOnUiThread {
                var rewardGranted = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Rewarded ad dismissed.")
                        rewardedAd = null
                        preloadAdMobRewarded()
                        if (rewardGranted) {
                            onRewarded()
                        }
                        onDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                        rewardedAd = null
                        preloadAdMobRewarded()
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
            preloadAds()
            onDismissed()
        }
    }

    override fun isInterstitialReady(): Boolean {
        return if (AdConfig.isProductionMediationEnabled) {
            maxInterstitialAd?.isReady == true
        } else {
            interstitialAd != null
        }
    }

    override fun isRewardedReady(): Boolean {
        return if (AdConfig.isProductionMediationEnabled) {
            maxRewardedAd?.isReady == true
        } else {
            rewardedAd != null
        }
    }

    override fun showMediationDebugger() {
        val activity = currentActivityRef?.get() ?: return
        activity.runOnUiThread {
            try {
                com.applovin.sdk.AppLovinSdk.getInstance(activity).showMediationDebugger()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to launch AppLovin MAX Mediation Debugger: ${e.message}", e)
            }
        }
    }
}
