package com.fitter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitter.app.ads.AndroidAdManager
import com.google.android.gms.ads.MobileAds
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        AndroidAdManager.currentActivityRef = WeakReference(this)

        // Initialize Google Mobile Ads SDK on a background thread
        MobileAds.initialize(this) {
            getPlatformAdManager().preloadAds()
        }

        setContent {
            App()
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

