package com.fitter.app

// Gateway URL (Supabase Edge Function — VLM keys stored in Supabase Vault, never in the app)
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

expect val isDebugBuild: Boolean

expect fun getPlatformAdManager(): com.fitter.app.ads.AdManager

expect fun syncPlatformMealReminders(
    enabled: Boolean,
    reminders: List<com.fitter.app.notifications.MealReminder>
)

/**
 * Requests platform-level advertising consent (Android CMP / iOS ATT) and
 * reports the resulting system-level signal.
 *
 * MUST be awaited before any ad request. Returns true when the system grants
 * personalized-ad permission; false means restricted/non-personalized only.
 *
 * This is the OS/store-level signal. It is ANDed with the user's in-app
 * PrivacyConsent choice — either one denying personalization is final.
 */
expect suspend fun requestPlatformAdConsent(): Boolean

/**
 * Binds the platform preference store into [PrivacyConsent] so consent
 * decisions persist across launches. Called once during app composition.
 *
 * Optional: PrivacyConsent lazily calls the platform storage factory itself, so
 * this is only an early-warm convenience, not a correctness requirement.
 */
expect fun initPrivacyConsentStore()

/**
 * Creates a [com.fitter.app.privacy.PrivacyConsent.ConsentStorage] backed by the
 * platform's persistent preferences. Must be callable without an Activity/Context.
 *
 * Invoked lazily by PrivacyConsent on first read/write so consent works even if
 * initPrivacyConsentStore() is never called from the platform entry point.
 */
internal expect fun createConsentStorage(): com.fitter.app.privacy.PrivacyConsent.ConsentStorage

