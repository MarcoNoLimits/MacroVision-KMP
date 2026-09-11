package com.fitter.app.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.applovin.mediation.ads.MaxAdView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
actual fun AdBanner(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (AdConfig.isProductionMediationEnabled && AdConfig.maxAndroidBannerId.isNotBlank()) {
            AndroidView(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Transparent),
                factory = { context ->
                    MaxAdView(AdConfig.maxAndroidBannerId, context).apply {
                        setRevenueListener { maxAd ->
                            com.fitter.app.telemetry.AdTelemetryManager.trackAdRevenue(
                                com.fitter.app.telemetry.AdRevenuePayload(
                                    adUnitId = maxAd.adUnitId,
                                    networkName = maxAd.networkName,
                                    revenue = maxAd.revenue,
                                    format = "BANNER",
                                    placement = maxAd.placement ?: "",
                                    creativeId = maxAd.creativeId ?: "",
                                    timestampMillis = System.currentTimeMillis()
                                )
                            )
                        }
                        setListener(object : com.applovin.mediation.MaxAdViewAdListener {
                            override fun onAdLoaded(ad: com.applovin.mediation.MaxAd) {
                                com.fitter.app.telemetry.AdTelemetryManager.trackBannerImpression()
                            }
                            override fun onAdLoadFailed(adUnitId: String, error: com.applovin.mediation.MaxError) {
                                com.fitter.app.telemetry.DiagnosticsCrashHook.logAdError("MAX", "BANNER", error.code.toString(), error.message)
                            }
                            override fun onAdDisplayed(ad: com.applovin.mediation.MaxAd) {}
                            override fun onAdHidden(ad: com.applovin.mediation.MaxAd) {}
                            override fun onAdClicked(ad: com.applovin.mediation.MaxAd) {}
                            override fun onAdDisplayFailed(ad: com.applovin.mediation.MaxAd, error: com.applovin.mediation.MaxError) {}
                            override fun onAdExpanded(ad: com.applovin.mediation.MaxAd) {}
                            override fun onAdCollapsed(ad: com.applovin.mediation.MaxAd) {}
                        })
                        loadAd()
                    }
                },
                onRelease = { maxAdView ->
                    maxAdView.destroy()
                }
            )
        } else {
            AndroidView(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Transparent),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdConfig.ANDROID_TEST_BANNER
                        adListener = object : com.google.android.gms.ads.AdListener() {
                            override fun onAdLoaded() {
                                com.fitter.app.telemetry.AdTelemetryManager.trackBannerImpression()
                            }
                            override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                                com.fitter.app.telemetry.DiagnosticsCrashHook.logAdError("AdMob", "BANNER", loadAdError.code.toString(), loadAdError.message)
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { adView ->
                    adView.destroy()
                }
            )
        }
    }
}
