package com.fitter.app

expect val openRouterApiKey: String
expect val geminiApiKey: String
expect val groqApiKey: String

expect fun savePreference(key: String, value: String)
expect fun loadPreference(key: String, defaultValue: String): String

expect fun getCurrentTimeString(): String

expect fun getCurrentDateString(): String
expect fun getLastSevenDays(): List<Pair<String, String>>

expect fun compressImage(imageBytes: ByteArray): ByteArray

