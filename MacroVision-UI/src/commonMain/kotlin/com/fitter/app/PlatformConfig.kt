package com.fitter.app

// Gateway URL (Cloudflare Worker — no VLM keys here, only the gateway endpoint)
expect val gatewayUrl: String

// Supabase project credentials (anon key is safe in the app; service_role is NEVER here)
expect val supabaseUrl: String
expect val supabaseAnonKey: String

expect fun savePreference(key: String, value: String)
expect fun loadPreference(key: String, defaultValue: String): String

expect fun getCurrentTimeString(): String

expect fun getCurrentDateString(): String
expect fun getCurrentEpochMillis(): Long
expect fun getLastSevenDays(): List<Pair<String, String>>

expect fun compressImage(imageBytes: ByteArray): ByteArray

expect fun getPlatformAdManager(): com.fitter.app.ads.AdManager
