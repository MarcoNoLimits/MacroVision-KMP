package com.fitter.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.fitter.app.ads.AndroidAdManager
import com.fitter.app.privacy.PrivacyConsent
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

actual val gatewayUrl: String get() = BuildConfig.GATEWAY_URL
actual val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
actual val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
actual val isDebugBuild: Boolean get() = BuildConfig.DEBUG

lateinit var appContext: Context

actual fun savePreference(key: String, value: String) {
    val sharedPref = appContext.getSharedPreferences("fitter_prefs", Context.MODE_PRIVATE)
    sharedPref.edit().putString(key, value).apply()
}

actual fun loadPreference(key: String, defaultValue: String): String {
    val sharedPref = appContext.getSharedPreferences("fitter_prefs", Context.MODE_PRIVATE)
    if (sharedPref.contains(key)) {
        return sharedPref.getString(key, defaultValue) ?: defaultValue
    }
    val legacyPref = appContext.getSharedPreferences("macrovision_prefs", Context.MODE_PRIVATE)
    return legacyPref.getString(key, defaultValue) ?: defaultValue
}

/**
 * Binds SharedPreferences into the shared consent store.
 * Uses the same "fitter_prefs" file so consent persists across app restarts
 * and survives a process death mid-session.
 */
actual fun initPrivacyConsentStore() {
    PrivacyConsent.bindStorage(object : PrivacyConsent.ConsentStorage {
        private fun prefs() =
            appContext.getSharedPreferences("fitter_prefs", Context.MODE_PRIVATE)

        override fun getBoolean(key: String, default: Boolean): Boolean =
            prefs().getBoolean(key, default)

        override fun putBoolean(key: String, value: Boolean) {
            prefs().edit().putBoolean(key, value).apply()
        }

        override fun getLong(key: String, default: Long): Long =
            prefs().getLong(key, default)

        override fun putLong(key: String, value: Long) {
            prefs().edit().putLong(key, value).apply()
        }
    })
}

/**
 * Builds the consent storage. Uses applicationContext when available so it works
 * from any thread with no Activity; falls back to an in-memory store before init
 * so early composition cannot crash the consent gate.
 */
actual fun createConsentStorage(): PrivacyConsent.ConsentStorage {
    val fallback = mutableMapOf<String, Boolean>()

    return object : PrivacyConsent.ConsentStorage {
        private fun ready(): Boolean = ::appContext.isInitialized

        private fun prefs() = appContext.getSharedPreferences("fitter_prefs", Context.MODE_PRIVATE)

        override fun getBoolean(key: String, default: Boolean): Boolean =
            if (ready()) prefs().getBoolean(key, default) else fallback[key] ?: default

        override fun putBoolean(key: String, value: Boolean) {
            if (ready()) prefs().edit().putBoolean(key, value).apply() else fallback[key] = value
        }

        override fun getLong(key: String, default: Long): Long =
            if (ready()) prefs().getLong(key, default) else default

        override fun putLong(key: String, value: Long) {
            if (ready()) prefs().edit().putLong(key, value).apply()
        }
    }
}

/**
 * Android consent: Google UMP (User Messaging Platform) via the AppLovin CMP
 * bridge, falling back to a non-personalized default when unsupported.
 *
 * IMPORTANT: the caller MUST await this before MobileAds.initialize / ad loads.
 * Initializing first and prompting later is the defect this function exists to prevent.
 */
actual suspend fun requestPlatformAdConsent(): Boolean = suspendCancellableCoroutine { cont ->
    try {
        val activity = AndroidAdManager.currentActivityRef?.get()
        if (activity == null) {
            android.util.Log.w("Fitter_Privacy", "No activity; treating consent as denied")
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        android.util.Log.w("Fitter_Privacy", "UMP consent form error: ${formError.message}")
                    }
                    val canRequest = consentInformation.canRequestAds()
                    android.util.Log.i("Fitter_Privacy", "UMP consent resolved. canRequestAds=$canRequest")
                    try {
                        com.applovin.sdk.AppLovinPrivacySettings.setHasUserConsent(canRequest, activity)
                    } catch (t: Throwable) {
                        android.util.Log.w("Fitter_Privacy", "AppLovin consent sync failed: ${t.message}")
                    }
                    cont.resume(canRequest)
                }
            },
            { requestError ->
                android.util.Log.w("Fitter_Privacy", "UMP consent request error: ${requestError.message}")
                val canRequest = consentInformation.canRequestAds()
                try {
                    com.applovin.sdk.AppLovinPrivacySettings.setHasUserConsent(canRequest, activity)
                } catch (_: Throwable) {}
                cont.resume(canRequest)
            }
        )
    } catch (t: Throwable) {
        // Fail closed: an unexpected CMP failure must never silently become consent.
        android.util.Log.w("Fitter_Privacy", "CMP exception: ${t.message}")
        cont.resume(false)
    }
}

actual fun getCurrentTimeString(): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date())
}

actual fun getCurrentDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
}

actual fun getCurrentEpochMillis(): Long = System.currentTimeMillis()

actual fun getLastSevenDays(): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    val cal = Calendar.getInstance()
    val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val sdfDay = SimpleDateFormat("EEE", Locale.getDefault())
    
    val todayKey = sdfKey.format(Date())
    
    // Start 6 days ago
    cal.add(Calendar.DAY_OF_YEAR, -6)
    
    for (i in 0..6) {
        val date = cal.time
        val key = sdfKey.format(date)
        val dayLabel = when (key) {
            todayKey -> "Today"
            else -> {
                val tempCal = Calendar.getInstance()
                tempCal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterdayKey = sdfKey.format(tempCal.time)
                if (key == yesterdayKey) "Yest" else sdfDay.format(date)
            }
        }
        list.add(Pair(key, dayLabel))
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return list
}

actual fun compressImage(imageBytes: ByteArray): ByteArray {
    try {
        // Parse EXIF orientation to detect vertical/portrait rotation
        val inputStream = ByteArrayInputStream(imageBytes)
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return imageBytes

        // Rotate the bitmap if orientation is sideways
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = rotatedBitmap
            }
        }

        val width = bitmap.width
        val height = bitmap.height
        val maxDimension = 768
        
        val (targetWidth, targetHeight) = if (width > maxDimension || height > maxDimension) {
            if (width > height) {
                val ratio = height.toFloat() / width
                Pair(maxDimension, (maxDimension * ratio).toInt())
            } else {
                val ratio = width.toFloat() / height
                Pair((maxDimension * ratio).toInt(), maxDimension)
            }
        } else {
            Pair(width, height)
        }
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val compressedBytes = outputStream.toByteArray()
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        bitmap.recycle()
        
        return compressedBytes
    } catch (e: Exception) {
        return imageBytes
    }
}

private val androidAdManager by lazy {
    com.fitter.app.ads.AndroidAdManager { appContext }
}

actual fun getPlatformAdManager(): com.fitter.app.ads.AdManager = androidAdManager

actual fun syncPlatformMealReminders(
    enabled: Boolean,
    reminders: List<com.fitter.app.notifications.MealReminder>
) {
    if (::appContext.isInitialized) {
        com.fitter.app.notifications.MealReminderReceiver.syncAlarms(appContext, enabled, reminders)
    }
}

