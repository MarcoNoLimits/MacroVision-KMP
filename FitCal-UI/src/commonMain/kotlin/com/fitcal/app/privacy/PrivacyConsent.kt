package com.fitcal.app.privacy

import com.fitcal.app.createConsentStorage

/**
 * Privacy consent + ad-personalization state.
 *
 * Legal basis: meal photos and nutrition logs are health-adjacent personal data
 * (GDPR Art. 9). Nothing that leaves the device may run before the user has
 * affirmatively accepted. Ads are gated on the same signal so we never serve
 * personalized ads without consent (ePrivacy Art. 5(3) / Google Play EU policy).
 *
 * All state is local and non-identifying. Persisting the user's choice is itself
 * a legal requirement, so we never silently reset it.
 */
object PrivacyConsent {

    private const val KEY_PRIVACY_ACCEPTED = "privacy_consent_accepted_v1"
    private const val KEY_ADS_PERSONALIZATION = "ads_personalization_enabled_v1"
    private const val KEY_TERMS_ACCEPTED = "terms_accepted_v1"
    private const val KEY_CONSENT_TIMESTAMP = "privacy_consent_timestamp_v1"

    // Storage is injected from PlatformConfig's expect/actual preference helpers.
    private var storage: ConsentStorage? = null

    fun bindStorage(store: ConsentStorage) {
        storage = store
    }

    /**
     * Lazily binds the platform store on first use.
     *
     * Both actuals delegate to a preference API that does not require an Activity
     * or Context (SharedPreferences via applicationContext on Android,
     * NSUserDefaults on iOS), so this is safe to call from any thread. Doing it
     * lazily means a missing or reordered initPrivacyConsentStore() call can no
     * longer strand the user in the consent gate forever — the previous design
     * silently returned the default (false) on every read and never persisted.
     */
    private fun store(): ConsentStorage {
        storage?.let { return it }
        val bound = createConsentStorage()
        storage = bound
        return bound
    }

    private fun get(key: String, default: Boolean): Boolean =
        store().getBoolean(key, default)

    private fun set(key: String, value: Boolean) {
        store().putBoolean(key, value)
    }

    /** True once the user has accepted the privacy notice and terms. Gates the whole app. */
    fun isPrivacyAccepted(): Boolean = get(KEY_PRIVACY_ACCEPTED, false)

    fun areTermsAccepted(): Boolean = get(KEY_TERMS_ACCEPTED, false)

    /**
     * Whether personalized (targeted) ads may be requested.
     *
     * Distinct from "show ads": a user may keep free-tier ads while declining
     * personalization. Platform CMP/ATT signals are ANDed with this local choice
     * by the ad platform code, so declining here always wins.
     */
    fun isAdsPersonalizationEnabled(): Boolean = get(KEY_ADS_PERSONALIZATION, false)

    /** True when ad *requests* may be made at all (consent given, or user declined only personalization). */
    fun canRequestAds(): Boolean = isPrivacyAccepted()

    /**
     * Runs [block] against the bound consent storage, or returns [fallback] if no
     * storage is available yet.
     *
     * Used by sibling privacy helpers (e.g. AgeGate) that need the same persistent
     * store without each re-implementing the lazy-init dance.
     */
internal fun runIfBound(fallback: Boolean = false, block: (ConsentStorage) -> Boolean): Boolean =
    try {
        block(store())
    } catch (_: Throwable) {
        fallback
    }

fun getConsentTimestamp(): Long =
        store().getLong(KEY_CONSENT_TIMESTAMP, 0L)

    /**
     * Records the user's decision from the consent sheet.
     *
     * @param acceptTerms must be true to reach the app at all.
     * @param adsPersonalized must be an explicit affirmative opt-in.
     */
    fun recordDecision(acceptTerms: Boolean, adsPersonalized: Boolean, timestampMs: Long) {
        set(KEY_TERMS_ACCEPTED, acceptTerms)
        set(KEY_PRIVACY_ACCEPTED, acceptTerms)
        set(KEY_ADS_PERSONALIZATION, acceptTerms && adsPersonalized)
        store().putLong(KEY_CONSENT_TIMESTAMP, timestampMs)
    }

    /**
     * Revoke all non-essential processing (GDPR Art. 7(3) withdrawal).
     *
     * Called by the "delete my data" flow and by the in-app opt-out switch.
     * The app continues to work with ads restricted to non-personalized.
     */
    fun revokeOptionalConsent() {
        set(KEY_ADS_PERSONALIZATION, false)
    }

    /**
     * Full erasure hook. Clears every consent artifact so a reinstall/account
     * deletion leaves no traceable processing record on device.
     */
    fun clearAll() {
        set(KEY_PRIVACY_ACCEPTED, false)
        set(KEY_TERMS_ACCEPTED, false)
        set(KEY_ADS_PERSONALIZATION, false)
        store().putLong(KEY_CONSENT_TIMESTAMP, 0L)
    }

    /** Minimal storage contract so this object stays platform-agnostic and unit-testable. */
    interface ConsentStorage {
        fun getBoolean(key: String, default: Boolean): Boolean
        fun putBoolean(key: String, value: Boolean)
        fun getLong(key: String, default: Long): Long
        fun putLong(key: String, value: Long)
    }
}
