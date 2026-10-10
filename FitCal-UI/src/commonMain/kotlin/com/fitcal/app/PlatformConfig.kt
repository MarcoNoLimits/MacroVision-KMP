package com.fitcal.app

// Gateway URL (Supabase Edge Function — VLM keys stored in Supabase Vault, never in the app)
expect val gatewayUrl: String

// Supabase project credentials (anon key is safe in the app; service_role is NEVER here)
expect val supabaseUrl: String
expect val supabaseAnonKey: String

// Google OAuth "Web application" client ID, used as serverClientId for native Google sign-in.
expect val googleWebClientId: String

// Native social sign-in offered on this platform (Google via Credential Manager on Android,
// Sign in with Apple on iOS).
expect val isGoogleSignInAvailable: Boolean
expect val isAppleSignInAvailable: Boolean

// Encrypted-at-rest storage for the auth session (Android Keystore / iOS Keychain).
expect fun createSecureStringStore(): com.fitcal.shared.auth.SecureStringStore?

expect fun savePreference(key: String, value: String)
expect fun loadPreference(key: String, defaultValue: String): String

expect fun getCurrentTimeString(): String

expect fun getCurrentDateString(): String
expect fun getCurrentEpochMillis(): Long
expect fun getLastSevenDays(): List<Pair<String, String>>

expect fun compressImage(imageBytes: ByteArray): ByteArray

expect val isDebugBuild: Boolean

// App version name and platform tag, attached to every analytics batch.
expect val appVersionName: String
expect val platformName: String

expect fun getPlatformAdManager(): com.fitcal.app.ads.AdManager

/**
 * Crash reporting (Sentry on Android). Enabled only after privacy consent and while
 * the user keeps "Share usage analytics" on; disabling closes the SDK immediately.
 */
expect fun setCrashReportingEnabled(enabled: Boolean)

/** Pseudonymous Supabase user ID, so a crash can be matched to that user's analytics. */
expect fun setCrashReportingUser(userId: String?)

/** Reports a caught exception. No-op while crash reporting is disabled. */
expect fun reportNonFatal(throwable: Throwable, tag: String)

expect fun syncPlatformMealReminders(
    enabled: Boolean,
    reminders: List<com.fitcal.app.notifications.MealReminder>
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
 * Creates a [com.fitcal.app.privacy.PrivacyConsent.ConsentStorage] backed by the
 * platform's persistent preferences. Must be callable without an Activity/Context.
 *
 * Invoked lazily by PrivacyConsent on first read/write so consent works even if
 * initPrivacyConsentStore() is never called from the platform entry point.
 */
internal expect fun createConsentStorage(): com.fitcal.app.privacy.PrivacyConsent.ConsentStorage

