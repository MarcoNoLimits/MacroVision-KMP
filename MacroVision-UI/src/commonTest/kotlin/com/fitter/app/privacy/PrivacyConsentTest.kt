package com.fitter.app.privacy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Consent invariants — these encode legal guarantees, not implementation detail.
 *
 * The most important property: consent must be OFF by default and must never
 * become true without an explicit affirmative act. Every other GDPR right
 * (withdrawal, erasure) depends on that being true.
 */
class PrivacyConsentTest {

    /** In-memory store so the test never touches a real platform preference file. */
    private class FakeStorage : PrivacyConsent.ConsentStorage {
        val map = mutableMapOf<String, Any>()
        override fun getBoolean(key: String, default: Boolean): Boolean =
            map[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) {
            map[key] = value
        }
        override fun getLong(key: String, default: Long): Long = map[key] as? Long ?: default
        override fun putLong(key: String, value: Long) {
            map[key] = value
        }
    }

    private fun fresh(): FakeStorage {
        val s = FakeStorage()
        PrivacyConsent.bindStorage(s)
        return s
    }

    @Test
    fun `defaults to no consent and no personalization`() {
        fresh()
        assertFalse(PrivacyConsent.isPrivacyAccepted())
        assertFalse(PrivacyConsent.areTermsAccepted())
        assertFalse(PrivacyConsent.isAdsPersonalizationEnabled())
        assertFalse(PrivacyConsent.canRequestAds())
    }

    @Test
    fun `accepting without personalization grants ad access but not targeting`() {
        fresh()
        PrivacyConsent.recordDecision(acceptTerms = true, adsPersonalized = false, timestampMs = 1L)

        assertTrue(PrivacyConsent.isPrivacyAccepted())
        assertTrue(PrivacyConsent.canRequestAds())
        // Non-personalized ads remain allowed, targeted ads do not.
        assertFalse(PrivacyConsent.isAdsPersonalizationEnabled())
    }

    @Test
    fun `accepting with personalization enables targeting`() {
        fresh()
        PrivacyConsent.recordDecision(acceptTerms = true, adsPersonalized = true, timestampMs = 1L)

        assertTrue(PrivacyConsent.isPrivacyAccepted())
        assertTrue(PrivacyConsent.isAdsPersonalizationEnabled())
    }

    @Test
    fun `declining terms never grants personalization even if requested`() {
        fresh()
        // Defensive: consent must be freely given, so a false accept cannot
        // silently enable the more invasive processing path.
        PrivacyConsent.recordDecision(acceptTerms = false, adsPersonalized = true, timestampMs = 1L)

        assertFalse(PrivacyConsent.isPrivacyAccepted())
        assertFalse(PrivacyConsent.isAdsPersonalizationEnabled())
        assertFalse(PrivacyConsent.canRequestAds())
    }

    @Test
    fun `withdrawal disables personalization but keeps the app usable`() {
        fresh()
        PrivacyConsent.recordDecision(acceptTerms = true, adsPersonalized = true, timestampMs = 1L)
        PrivacyConsent.revokeOptionalConsent()

        assertFalse(PrivacyConsent.isAdsPersonalizationEnabled())
        // Art. 7(3): withdrawing optional consent must not lock the user out.
        assertTrue(PrivacyConsent.isPrivacyAccepted())
    }

    @Test
    fun `clearAll revokes everything including the gate`() {
        fresh()
        PrivacyConsent.recordDecision(acceptTerms = true, adsPersonalized = true, timestampMs = 42L)
        PrivacyConsent.clearAll()

        assertFalse(PrivacyConsent.isPrivacyAccepted())
        assertFalse(PrivacyConsent.areTermsAccepted())
        assertFalse(PrivacyConsent.isAdsPersonalizationEnabled())
        assertEquals(0L, PrivacyConsent.getConsentTimestamp())
    }

    @Test
    fun `decision survives a new storage binding (persistence contract)`() {
        val first = fresh()
        PrivacyConsent.recordDecision(acceptTerms = true, adsPersonalized = true, timestampMs = 99L)

        // Simulate a process restart: a fresh store instance seeded with the
        // previously persisted values must still report acceptance.
        val restarted = FakeStorage().apply { map.putAll(first.map) }
        PrivacyConsent.bindStorage(restarted)

        assertTrue(PrivacyConsent.isPrivacyAccepted())
        assertTrue(PrivacyConsent.isAdsPersonalizationEnabled())
        assertEquals(99L, PrivacyConsent.getConsentTimestamp())
    }
}
