package com.fitter.app

import kotlin.test.*

class ScanGateTest {

    @Test
    fun testAuthPendingWhenAuthReadyIsNull() {
        val readiness = ensureReadyForScan(authReady = null, scansRemaining = 5)
        assertEquals(ScanReadiness.AuthPending, readiness)
        assertEquals("auth_pending", getScanBlockedReason(readiness))
    }

    @Test
    fun testOfflineWhenAuthReadyIsFalse() {
        val readiness = ensureReadyForScan(authReady = false, scansRemaining = 5, networkOnline = false)
        assertEquals(ScanReadiness.Offline, readiness)
        assertEquals("offline", getScanBlockedReason(readiness))
    }

    @Test
    fun testAuthFailureWhenAuthReadyIsFalseAndOnline() {
        val readiness = ensureReadyForScan(authReady = false, scansRemaining = 5, networkOnline = true)
        assertEquals(ScanReadiness.Ready, readiness)
        assertNull(getScanBlockedReason(readiness))
        assertEquals("auth_failure", getScanBlockedReason(ScanReadiness.AuthFailure))
    }

    @Test
    fun testReadyWhenAuthReadyIsTrue() {
        val readiness = ensureReadyForScan(authReady = true, scansRemaining = 3)
        assertEquals(ScanReadiness.Ready, readiness)
        assertNull(getScanBlockedReason(readiness))
    }

    @Test
    fun testQuotaExhaustedWhenHardBlockedAndNoScansRemaining() {
        val readiness = ensureReadyForScan(authReady = true, scansRemaining = 0, hardBlockQuota = true)
        assertEquals(ScanReadiness.QuotaExhausted, readiness)
        assertEquals("quota_exhausted", getScanBlockedReason(readiness))
    }

    @Test
    fun testFreeTierScanAllowedWhenZeroScansAndNotHardBlocked() {
        // Monetization rule: zero scans remaining does not hard-block; forced interstitial plays during scan
        val readiness = ensureReadyForScan(authReady = true, scansRemaining = 0, hardBlockQuota = false)
        assertEquals(ScanReadiness.Ready, readiness)
        assertNull(getScanBlockedReason(readiness))
    }
}
