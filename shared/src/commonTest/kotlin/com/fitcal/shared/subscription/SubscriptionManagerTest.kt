package com.fitcal.shared.subscription

import com.fitcal.shared.data.FakeKeyValueStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 7 verification: SubscriptionManager unit tests.
 * Proves that local boolean manipulation cannot grant premium authority (server state required),
 * and verifies staleness expiration (> 24h) and the test escape hatch.
 */
class SubscriptionManagerTest {

    private lateinit var storage: FakeKeyValueStorage
    private var simulatedTime: Long = 1757000000000L // arbitrary fixed time

    @BeforeTest
    fun setUp() {
        storage = FakeKeyValueStorage()
        simulatedTime = 1757000000000L
        SubscriptionManager.resetForTesting()
        SubscriptionManager.currentTimeMillisProvider = { simulatedTime }
    }

    @AfterTest
    fun tearDown() {
        SubscriptionManager.resetForTesting()
    }

    @Test
    fun testLocalPremiumBooleanIsIgnoredWhenSourceIsNotServer() {
        // Attacker attempts to tamper with local storage to grant premium
        storage.putString("subscription_fitcal_premium_active", "true")
        storage.putString("subscription_entitlement_source", "local")

        SubscriptionManager.initialize(storage)

        // Must be rejected: local booleans are never accepted as authority
        assertFalse(SubscriptionManager.isPremiumUser(), "Local authority boolean must be ignored")
        assertFalse(SubscriptionManager.hasEntitlement(SubscriptionManager.ENTITLEMENT_FITCAL_PREMIUM))
    }

    @Test
    fun testServerEntitlementIsAcceptedWhenFresh() {
        // Worker / RevenueCat webhook stamped server entitlement 1 hour ago
        val oneHourAgo = simulatedTime - (60 * 60 * 1000L)
        storage.putString("subscription_fitcal_premium_active", "true")
        storage.putString("subscription_entitlement_source", "server")
        storage.putString("subscription_server_confirmed_at_ms", oneHourAgo.toString())

        SubscriptionManager.initialize(storage)

        assertTrue(SubscriptionManager.isPremiumUser(), "Fresh server-confirmed entitlement must be active")
        assertTrue(SubscriptionManager.hasEntitlement(SubscriptionManager.ENTITLEMENT_FITCAL_PREMIUM))
        assertTrue(SubscriptionManager.hasEntitlement(SubscriptionManager.ENTITLEMENT_FITTER_PREMIUM))
    }

    @Test
    fun testServerEntitlementExpiresWhenStale() {
        // Server entitlement was confirmed 25 hours ago (> 24h threshold)
        val twentyFiveHoursAgo = simulatedTime - (25 * 60 * 60 * 1000L)
        storage.putString("subscription_fitcal_premium_active", "true")
        storage.putString("subscription_entitlement_source", "server")
        storage.putString("subscription_server_confirmed_at_ms", twentyFiveHoursAgo.toString())

        SubscriptionManager.initialize(storage)

        // Must be treated as false until refreshed
        assertFalse(SubscriptionManager.isPremiumUser(), "Stale server entitlement (>24h) must be inactive")
        assertFalse(SubscriptionManager.hasEntitlement(SubscriptionManager.ENTITLEMENT_FITCAL_PREMIUM))
    }

    @Test
    fun testTestEscapeHatchWorks() {
        SubscriptionManager.initialize(storage)
        assertFalse(SubscriptionManager.isPremiumUser())

        // In tests only: setPremiumStatus injects test source
        SubscriptionManager.setPremiumStatus(true, SubscriptionManager.PRODUCT_MONTHLY)
        assertTrue(SubscriptionManager.isPremiumUser(), "Test escape hatch must work for test suite")

        SubscriptionManager.setPremiumStatus(false)
        assertFalse(SubscriptionManager.isPremiumUser())
    }
}
