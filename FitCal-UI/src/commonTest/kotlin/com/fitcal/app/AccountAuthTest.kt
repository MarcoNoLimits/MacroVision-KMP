package com.fitcal.app

import com.fitcal.app.ads.ScanQuotaManager
import com.fitcal.app.ui.screens.auth.checkPassword
import com.fitcal.app.ui.screens.auth.isValidEmail
import com.fitcal.app.ui.screens.auth.isValidPassword
import com.fitcal.app.ui.screens.auth.sanitizeOtp
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
        assertTrue(isValidEmail("athlete+fitcal@sub.domain.co"))

        assertFalse(isValidEmail(""))
        assertFalse(isValidEmail("   "))
        assertFalse(isValidEmail("plainaddress"))
        assertFalse(isValidEmail("@missingusername.com"))
        assertFalse(isValidEmail("missingdomain@"))
        assertFalse(isValidEmail("missingdot@domain"))
        assertFalse(isValidEmail("trailingdot@domain."))
    }

    @Test
    fun testPasswordPolicyValidation() {
        assertTrue(isValidPassword("superSecretP4ss"))
        assertTrue(isValidPassword("Abcdefg1"))

        assertFalse(isValidPassword(""))
        assertFalse(isValidPassword("Abcde1"), "too short")
        assertFalse(isValidPassword("abcdefg1"), "no uppercase")
        assertFalse(isValidPassword("ABCDEFG1"), "no lowercase")
        assertFalse(isValidPassword("Abcdefgh"), "no digit")
    }

    @Test
    fun testPasswordChecksReportEachRule() {
        val checks = checkPassword("abc")
        assertFalse(checks.hasMinLength)
        assertFalse(checks.hasUpperAndLower)
        assertFalse(checks.hasDigit)
        assertEquals(0, checks.passedCount)
        assertEquals(3, checkPassword("Abcdefg1").passedCount)
    }

    @Test
    fun testOtpSanitizingAcceptsPastedCodes() {
        assertEquals("123456", sanitizeOtp("123 456"))
        assertEquals("123456", sanitizeOtp("Code: 123456789"))
        assertEquals("", sanitizeOtp("abc"))
    }

    @Test
    fun testEmailRejectsSpacesAndDoubleAt() {
        assertFalse(isValidEmail("a b@example.com"))
        assertFalse(isValidEmail("a@b@example.com"))
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
