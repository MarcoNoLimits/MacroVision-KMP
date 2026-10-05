package com.fitcal.app.ads

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountBonusQuotaTest {

    private val prefs = mutableMapOf<String, String>()
    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private var permanent = false

    @BeforeTest
    fun setUp() {
        prefs.clear()
        permanent = false
        ScanQuotaManager.preferenceReader = { key, default -> prefs[key] ?: default }
        ScanQuotaManager.preferenceWriter = { key, value -> prefs[key] = value }
        ScanQuotaManager.currentTimeMillisProvider = { now }
        ScanQuotaManager.isPermanentAccountProvider = { permanent }
    }

    @AfterTest
    fun tearDown() {
        ScanQuotaManager.resetToDefaults()
    }

    private fun installedDaysAgo(days: Long) {
        prefs["first_install_timestamp"] = (now - days * day).toString()
    }

    @Test
    fun guestInWeekOneGetsFive() {
        installedDaysAgo(2)
        assertEquals(5, ScanQuotaManager.getDailyFreeLimit())
    }

    @Test
    fun accountInWeekOneGetsSix() {
        installedDaysAgo(2)
        permanent = true
        assertEquals(6, ScanQuotaManager.getDailyFreeLimit())
    }

    @Test
    fun guestAfterWeekOneGetsThree() {
        installedDaysAgo(10)
        assertEquals(3, ScanQuotaManager.getDailyFreeLimit())
    }

    @Test
    fun accountAfterWeekOneGetsFour() {
        installedDaysAgo(10)
        permanent = true
        assertEquals(4, ScanQuotaManager.getDailyFreeLimit())
        assertEquals(4, ScanQuotaManager.getRemainingScans("2026-10-06"))
    }
}
