package com.fitter.app.ads

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanQuotaManagerTest {

    private val memoryStore = mutableMapOf<String, String>()
    private val testDateToday = "2026-09-05"
    private val testDateTomorrow = "2026-09-06"

    @BeforeTest
    fun setUp() {
        memoryStore.clear()
        ScanQuotaManager.preferenceReader = { key, default ->
            memoryStore[key] ?: default
        }
        ScanQuotaManager.preferenceWriter = { key, value ->
            memoryStore[key] = value
        }
    }

    @AfterTest
    fun tearDown() {
        ScanQuotaManager.resetToDefaults()
        memoryStore.clear()
    }

    @Test
    fun testInitialDailyQuota() {
        assertEquals(3, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(0, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getBonusScans(testDateToday))
        assertEquals(3, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))
    }

    @Test
    fun testConsumeScansDecrementsQuota() {
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(1, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(2, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))

        ScanQuotaManager.consumeScan(testDateToday)
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(3, ScanQuotaManager.getUsedScans(testDateToday))
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
    }

    @Test
    fun testZeroClampingWhenOverConsumed() {
        // Simulate over-consuming past 3 scans
        repeat(5) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(5, ScanQuotaManager.getUsedScans(testDateToday))
        // Remaining scans should clamp to 0 and not become negative
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
    }

    @Test
    fun testRewardedAdBonusScans() {
        // Exhaust initial free daily allowance
        repeat(3) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))

        // Watch rewarded ad (+2 bonus scans)
        ScanQuotaManager.addBonusScans(testDateToday, 2)
        assertEquals(2, ScanQuotaManager.getBonusScans(testDateToday))
        assertEquals(2, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))

        // Consume 1 bonus scan
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(1, ScanQuotaManager.getRemainingScans(testDateToday))
        assertTrue(ScanQuotaManager.hasQuota(testDateToday))

        // Consume last bonus scan
        ScanQuotaManager.consumeScan(testDateToday)
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))
    }

    @Test
    fun testMultiDayQuotaIsolation() {
        // Exhaust quota on today
        repeat(3) {
            ScanQuotaManager.consumeScan(testDateToday)
        }
        assertEquals(0, ScanQuotaManager.getRemainingScans(testDateToday))
        assertFalse(ScanQuotaManager.hasQuota(testDateToday))

        // Tomorrow should have full fresh allowance
        assertEquals(3, ScanQuotaManager.getRemainingScans(testDateTomorrow))
        assertTrue(ScanQuotaManager.hasQuota(testDateTomorrow))
        assertEquals(0, ScanQuotaManager.getUsedScans(testDateTomorrow))
    }
}
