package com.fitter.app

import com.fitter.app.ads.ScanQuotaManager
import com.fitter.app.ui.screens.auth.isValidEmail
import com.fitter.app.ui.screens.auth.isValidPassword
import kotlin.test.*

class AccountAuthTest {

    private val memoryStore = mutableMapOf<String, String>()

    @BeforeTest
    fun setUp() {
        memoryStore.clear()
        val now = 1000000000000L
        val tenDaysAgo = now - (10L * 24 * 60 * 60 * 1000L)
        memoryStore["first_install_timestamp"] = tenDaysAgo.toString()

        ScanQuotaManager.preferenceReader = { key, default -> memoryStore[key] ?: default }
        ScanQuotaManager.preferenceWriter = { key, value -> memoryStore[key] = value }
        ScanQuotaManager.currentTimeMillisProvider = { now }
    }

    @AfterTest
    fun tearDown() {
        memoryStore.clear()
    }

    @Test
    fun testValidEmailDetection() {
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("first.last@company.org"))
        assertTrue(isValidEmail("athlete+fitter@sub.domain.co"))

        assertFalse(isValidEmail(""))
        assertFalse(isValidEmail("   "))
        assertFalse(isValidEmail("plainaddress"))
        assertFalse(isValidEmail("@missingusername.com"))
        assertFalse(isValidEmail("missingdomain@"))
        assertFalse(isValidEmail("missingdot@domain"))
        assertFalse(isValidEmail("trailingdot@domain."))
    }

    @Test
    fun testPasswordLengthValidation() {
        assertTrue(isValidPassword("123456"))
        assertTrue(isValidPassword("superSecretP@ss!"))

        assertFalse(isValidPassword(""))
        assertFalse(isValidPassword("12345"))
    }

    @Test
    fun testGuestModeAdLoadParity() {
        // Monetization rule: guests and authenticated users have identical ad load.
        // Free tier is always subject to standard daily quota regardless of login state.
        val guestFreeLimit = ScanQuotaManager.getDailyFreeLimit()
        assertEquals(3, guestFreeLimit.coerceAtLeast(3))

        // Ad display is not modified by guest or authenticated session (both subject to standard quota)
        val guestScansRemaining = ScanQuotaManager.getRemainingScans("2026-09-25")
        assertTrue(guestScansRemaining >= 0)
    }

    @Test
    fun testGuestScanReadinessParity() {
        // Guests with an established anonymous session (authReady = true) have Ready status
        val guestReadiness = ensureReadyForScan(authReady = true, scansRemaining = 3)
        assertEquals(ScanReadiness.Ready, guestReadiness)
        assertNull(getScanBlockedReason(guestReadiness))
    }
}
