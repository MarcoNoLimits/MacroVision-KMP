package com.fitter.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

actual val openRouterApiKey: String get() = BuildConfig.OPENROUTER_API_KEY
actual val geminiApiKey: String get() = BuildConfig.GEMINI_API_KEY
actual val groqApiKey: String get() = BuildConfig.GROQ_API_KEY

lateinit var appContext: Context

actual fun savePreference(key: String, value: String) {
    val sharedPref = appContext.getSharedPreferences("macrovision_prefs", Context.MODE_PRIVATE)
    sharedPref.edit().putString(key, value).apply()
}

actual fun loadPreference(key: String, defaultValue: String): String {
    val sharedPref = appContext.getSharedPreferences("macrovision_prefs", Context.MODE_PRIVATE)
    return sharedPref.getString(key, defaultValue) ?: defaultValue
}

actual fun getCurrentTimeString(): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date())
}

actual fun getCurrentDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
}

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
