package com.fitter.app

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
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
    var openRouterApiKey: String = ""
    var geminiApiKey: String = ""
    var groqApiKey: String = ""
}

actual val openRouterApiKey: String get() = PlatformConfig.openRouterApiKey
actual val geminiApiKey: String get() = PlatformConfig.geminiApiKey
actual val groqApiKey: String get() = PlatformConfig.groqApiKey

actual fun savePreference(key: String, value: String) {
    NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
}

actual fun loadPreference(key: String, defaultValue: String): String {
    return NSUserDefaults.standardUserDefaults.stringForKey(key) ?: defaultValue
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
