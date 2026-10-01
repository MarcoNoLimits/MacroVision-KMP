package com.fitcal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.fitcal.app.ads.AdConfig
import com.fitcal.app.ads.AndroidAdManager
import com.fitcal.app.privacy.PrivacyConsent
import com.google.android.gms.ads.MobileAds
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        initPrivacyConsentStore()
        AndroidAdManager.currentActivityRef = WeakReference(this)

        setContent {
            App()
        }

        // ── Ad initialization is DEFERRED until consent is resolved. ──
        // Previously MobileAds/AppLovin initialized and preloadAds() ran before (or
        // entirely without) a CMP result, which violates GDPR/ePrivacy Art. 5(3),
        // Google Play's EU consent policy, and ATT. Order matters legally.
        lifecycleScope.launch {
            // 1. Collect the OS/store consent signal FIRST.
            val platformGranted = requestPlatformAdConsent()

            // 2. AND it with the user's explicit in-app choice. Denying either wins.
            val personalized = platformGranted && PrivacyConsent.isAdsPersonalizationEnabled()

            if (!PrivacyConsent.canRequestAds()) {
                // No privacy acceptance at all: never initialize ad SDKs.
                android.util.Log.i("FitCal_Privacy", "Privacy not accepted — skipping ad init")
                return@launch
            }

            // 3. Only now initialize the mediation stack.
            if (AdConfig.isProductionMediationEnabled && AdConfig.maxSdkKey.isNotBlank()) {
                val initConfig = AppLovinSdkInitializationConfiguration.builder(AdConfig.maxSdkKey, this@MainActivity)
                    .setMediationProvider(AppLovinMediationProvider.MAX)
                    .build()

                AppLovinSdk.getInstance(this@MainActivity).initialize(initConfig) {
                    if (personalized) {
                        getPlatformAdManager().preloadAds()
                    } else {
                        android.util.Log.i("FitCal_Privacy", "Ads limited to non-personalized")
                    }
                }
            } else {
                MobileAds.initialize(this@MainActivity) {
                    if (personalized) {
                        getPlatformAdManager().preloadAds()
                    } else {
                        android.util.Log.i("FitCal_Privacy", "Ads limited to non-personalized")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidAdManager.currentActivityRef = WeakReference(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (AndroidAdManager.currentActivityRef?.get() == this) {
            AndroidAdManager.currentActivityRef = null
        }
    }
}

