package com.fitcal.app

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import com.fitcal.app.privacy.PrivacyConsent
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.AppTrackingTransparency.ATTrackingManager
import platform.AppTrackingTransparency.AuthorizationStatus
import platform.AppTrackingTransparency.ATRequestTrackingAuthorization
import platform.AppTrackingTransparency.ATTrackingAuthorizationStatusAuthorized
import platform.Foundation.NSBundle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.posix.memcpy

object PlatformConfig {
    var gatewayUrl: String = ""
    var supabaseUrl: String = ""
    var supabaseAnonKey: String = ""
    var googleWebClientId: String = ""
    val adManager = com.fitcal.app.ads.IosAdManager()
}


actual val gatewayUrl: String get() = PlatformConfig.gatewayUrl
actual val supabaseUrl: String get() = PlatformConfig.supabaseUrl
actual val supabaseAnonKey: String get() = PlatformConfig.supabaseAnonKey
actual val isDebugBuild: Boolean = true
actual val googleWebClientId: String get() = PlatformConfig.googleWebClientId

// compose-auth only signs in with Google natively on Android; iOS offers Sign in with Apple.
actual val isGoogleSignInAvailable: Boolean get() = false
actual val isAppleSignInAvailable: Boolean get() = true

@OptIn(com.russhwolf.settings.ExperimentalSettingsImplementation::class)
actual fun createSecureStringStore(): com.fitcal.shared.auth.SecureStringStore? {
    val keychain = com.russhwolf.settings.KeychainSettings(service = "com.fitcal.app.auth")
    return object : com.fitcal.shared.auth.SecureStringStore {
        override fun get(key: String): String? = keychain.getStringOrNull(key)
        override fun put(key: String, value: String) = keychain.putString(key, value)
        override fun remove(key: String) = keychain.remove(key)
    }
}

actual fun savePreference(key: String, value: String) {
    NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
}

actual fun loadPreference(key: String, defaultValue: String): String {
    return NSUserDefaults.standardUserDefaults.stringForKey(key) ?: defaultValue
}

/**
 * Builds the consent storage backed by NSUserDefaults.
 *
 * iOS never wired initPrivacyConsentStore(), which under the previous design
 * meant consent silently reset to "not accepted" on every launch — trapping the
 * user in the consent gate. PrivacyConsent now lazily calls this factory, so the
 * platform hook is present on both platforms regardless of entry-point ordering.
 */
actual fun createConsentStorage(): PrivacyConsent.ConsentStorage {
    return object : PrivacyConsent.ConsentStorage {
        override fun getBoolean(key: String, default: Boolean): Boolean =
            if (NSUserDefaults.standardUserDefaults.objectForKey(key) == null) default
            else NSUserDefaults.standardUserDefaults.boolForKey(key)

        override fun putBoolean(key: String, value: Boolean) {
            NSUserDefaults.standardUserDefaults.setBool(value, forKey = key)
        }

        override fun getLong(key: String, default: Long): Long =
            if (NSUserDefaults.standardUserDefaults.objectForKey(key) == null) default
            else NSUserDefaults.standardUserDefaults.integerForKey(key)

        override fun putLong(key: String, value: Long) {
            NSUserDefaults.standardUserDefaults.setInteger(value, forKey = key)
        }
    }
}

/** Binds NSUserDefaults into the shared consent store so choices persist across launches. */
actual fun initPrivacyConsentStore() {
    PrivacyConsent.bindStorage(createConsentStorage())
}

/**
 * iOS consent: App Tracking Transparency (ATT).
 *
 * Info.plist declares NSUserTrackingUsageDescription, so iOS REQUIRES the system
 * prompt before any tracking — without this call the app is rejected or the
 * prompt never appears while ads still track. Must be awaited before ad requests.
 *
 * Returns true only when the user grants tracking authorization.
 */
@Suppress("unused", "OPT_IN_USAGE")
actual suspend fun requestPlatformAdConsent(): Boolean = suspendCancellableCoroutine { cont ->
    try {
        val status = ATTrackingManager.trackingAuthorizationStatus
        if (status == ATTrackingAuthorizationStatusAuthorized) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        if (status != AuthorizationStatus.AuthorizationStatusNotDetermined) {
            // Previously denied/restricted — do not re-prompt; report the standing decision.
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        ATTrackingManager.requestTrackingAuthorizationWithCompletionHandler { newStatus ->
            cont.resume(newStatus == ATTrackingAuthorizationStatusAuthorized)
        }
    } catch (t: Throwable) {
        // Fail closed: never treat an ATT failure as permission to track.
        NSLog("FitCal_Privacy: ATT request failed: ${t.message}")
        cont.resume(false)
    }
}

actual fun getCurrentTimeString(): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "hh:mm a"
    }
    return formatter.stringFromDate(NSDate())
}

actual fun getCurrentDateString(): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
    }
    return formatter.stringFromDate(NSDate())
}

actual fun getCurrentEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun getLastSevenDays(): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    val calendar = NSCalendar.currentCalendar
    val formatterKey = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }
    val formatterDay = NSDateFormatter().apply { dateFormat = "EEE" }
    
    val today = NSDate()
    val todayKey = formatterKey.stringFromDate(today)
    
    val yesterday = calendar.dateByAddingUnit(
        NSCalendarUnitDay,
        value = -1,
        toDate = today,
        options = 0
    )!!
    val yesterdayKey = formatterKey.stringFromDate(yesterday)
    
    for (i in -6..0) {
        val date = calendar.dateByAddingUnit(
            NSCalendarUnitDay,
            value = i.toLong(),
            toDate = today,
            options = 0
        )!!
        val key = formatterKey.stringFromDate(date)
        val dayLabel = when (key) {
            todayKey -> "Today"
            yesterdayKey -> "Yest"
            else -> formatterDay.stringFromDate(date)
        }
        list.add(Pair(key, dayLabel))
    }
    return list
}

@OptIn(ExperimentalForeignApi::class)
actual fun compressImage(imageBytes: ByteArray): ByteArray {
    try {
        val nsData = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        val image = UIImage.imageWithData(nsData) ?: return imageBytes
        
        val size = image.size
        val width = size.useContents { width }
        val height = size.useContents { height }
        
        val maxDimension = 768.0
        var targetWidth = width
        var targetHeight = height
        
        if (width > maxDimension || height > maxDimension) {
            if (width > height) {
                val ratio = height / width
                targetWidth = maxDimension
                targetHeight = maxDimension * ratio
            } else {
                val ratio = width / height
                targetWidth = maxDimension * ratio
                targetHeight = maxDimension
            }
        }
        
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
        val resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        if (resizedImage == null) return imageBytes
        
        val compressedData = UIImageJPEGRepresentation(resizedImage, 0.8) ?: return imageBytes
        
        val resultSize = compressedData.length.toInt()
        val resultBytes = ByteArray(resultSize)
        if (resultSize > 0) {
            resultBytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), compressedData.bytes, compressedData.length)
            }
        }
        return resultBytes
    } catch (e: Exception) {
        return imageBytes
    }
}

actual fun getPlatformAdManager(): com.fitcal.app.ads.AdManager = PlatformConfig.adManager

actual fun syncPlatformMealReminders(
    enabled: Boolean,
    reminders: List<com.fitcal.app.notifications.MealReminder>
) {
    val center = platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()
    center.removeAllPendingNotificationRequests()
    if (!enabled) return

    val active = reminders.filter { it.enabled }
    if (active.isEmpty()) return

    center.requestAuthorizationWithOptions(
        options = platform.UserNotifications.UNAuthorizationOptionAlert or
            platform.UserNotifications.UNAuthorizationOptionSound or
            platform.UserNotifications.UNAuthorizationOptionBadge
    ) { granted, _ ->
        if (!granted) return@requestAuthorizationWithOptions
        active.forEach { reminder ->
            val (title, body) = com.fitcal.app.notifications.MealReminderManager.getNotificationContent(reminder.label)
            val content = platform.UserNotifications.UNMutableNotificationContent().apply {
                setTitle(title)
                setBody(body)
            }
            val dateComponents = platform.Foundation.NSDateComponents().apply {
                hour = reminder.hour.toLong()
                minute = reminder.minute.toLong()
            }
            val trigger = platform.UserNotifications.UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = dateComponents,
                repeats = true
            )
            val request = platform.UserNotifications.UNNotificationRequest.requestWithIdentifier(
                identifier = "fitcal_meal_${reminder.id}",
                content = content,
                trigger = trigger
            )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }
}

