package com.fitter.app.telemetry

import com.fitter.shared.data.KeyValueStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CohortRetentionTrackerTest {

    private class MockKeyValueStorage : KeyValueStorage {
        val map = mutableMapOf<String, String>()

        override fun getString(key: String, defaultValue: String): String =
            map[key] ?: defaultValue

        override fun putString(key: String, value: String) {
            map[key] = value
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            map[key]?.toIntOrNull() ?: defaultValue

        override fun putInt(key: String, value: Int) {
            map[key] = value.toString()
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }

    private lateinit var mockStorage: MockKeyValueStorage
    private var simulatedTimeMillis = 1000000000000L
    private val oneDayMillis = 24L * 60 * 60 * 1000L

    @BeforeTest
    fun setUp() {
        mockStorage = MockKeyValueStorage()
        simulatedTimeMillis = 1000000000000L

        CohortRetentionTracker.storageProvider = { mockStorage }
        CohortRetentionTracker.currentTimeMillisProvider = { simulatedTimeMillis }
        CohortRetentionTracker.currentDateStringProvider = { "2026-09-01" }
    }

    @AfterTest
    fun tearDown() {
        CohortRetentionTracker.resetForTesting()
    }

    @Test
    fun testFirstInstallTimestampInitialization() {
        // First access initializes install timestamp to simulated epoch
        val installTime = CohortRetentionTracker.getFirstInstallTimestamp()
        assertEquals(simulatedTimeMillis, installTime)

        // Advancing time should return original install timestamp
        CohortRetentionTracker.currentTimeMillisProvider = { simulatedTimeMillis + oneDayMillis }
        assertEquals(installTime, CohortRetentionTracker.getFirstInstallTimestamp())
    }

    @Test
    fun testElapsedDaysCalculation() {
        val installTime = CohortRetentionTracker.getFirstInstallTimestamp()

        // Day 0
        assertEquals(0, CohortRetentionTracker.getDaysSinceInstall(installTime))

        // Day 1
        assertEquals(1, CohortRetentionTracker.getDaysSinceInstall(installTime + oneDayMillis))

        // Day 7
        assertEquals(7, CohortRetentionTracker.getDaysSinceInstall(installTime + (7 * oneDayMillis)))

        // Day 30
        assertEquals(30, CohortRetentionTracker.getDaysSinceInstall(installTime + (30 * oneDayMillis)))

        // Day 90
        assertEquals(90, CohortRetentionTracker.getDaysSinceInstall(installTime + (90 * oneDayMillis)))
    }

    @Test
    fun testRecordSessionDeduplicationOnSameDay() {
        CohortRetentionTracker.getFirstInstallTimestamp()

        // Session 1 on Day 0
        CohortRetentionTracker.recordActiveDailySession("2026-09-01", simulatedTimeMillis)
        assertEquals(1, CohortRetentionTracker.getTotalActiveDays())
        assertEquals(listOf("2026-09-01"), CohortRetentionTracker.getActiveDates())

        // Multiple sessions on the same date key
        CohortRetentionTracker.recordActiveDailySession("2026-09-01", simulatedTimeMillis + 3600000L)
        CohortRetentionTracker.recordActiveDailySession("2026-09-01", simulatedTimeMillis + 7200000L)
        assertEquals(1, CohortRetentionTracker.getTotalActiveDays())
        assertEquals(listOf("2026-09-01"), CohortRetentionTracker.getActiveDates())
    }

    @Test
    fun testCohortProgressionDay1ThroughDay90() {
        val installTime = CohortRetentionTracker.getFirstInstallTimestamp()

        // Day 0 (Install Day)
        val statusDay0 = CohortRetentionTracker.recordActiveDailySession("2026-09-01", installTime)
        assertEquals(0, statusDay0.daysSinceInstall)
        assertEquals(1, statusDay0.totalActiveDays)
        assertFalse(statusDay0.isDay1Retained)
        assertFalse(statusDay0.isDay7Retained)

        // Day 1 Session
        val day1Time = installTime + oneDayMillis
        val statusDay1 = CohortRetentionTracker.recordActiveDailySession("2026-09-02", day1Time)
        assertEquals(1, statusDay1.daysSinceInstall)
        assertEquals(2, statusDay1.totalActiveDays)
        assertTrue(statusDay1.isDay1Retained)
        assertTrue(CohortRetentionTracker.isCohortActive(CohortInterval.DAY_1))
        assertTrue(CohortRetentionTracker.isCohortMilestoneReached(CohortInterval.DAY_1))
        assertFalse(statusDay1.isDay7Retained)

        // Day 7 Session
        val day7Time = installTime + (7 * oneDayMillis)
        val statusDay7 = CohortRetentionTracker.recordActiveDailySession("2026-09-08", day7Time)
        assertEquals(7, statusDay7.daysSinceInstall)
        assertEquals(3, statusDay7.totalActiveDays)
        assertTrue(statusDay7.isDay7Retained)
        assertTrue(CohortRetentionTracker.isCohortActive(CohortInterval.DAY_7))
        assertTrue(CohortRetentionTracker.isCohortMilestoneReached(CohortInterval.DAY_7))

        // Day 14 Session
        val day14Time = installTime + (14 * oneDayMillis)
        val statusDay14 = CohortRetentionTracker.recordActiveDailySession("2026-09-15", day14Time)
        assertTrue(statusDay14.isDay14Retained)
        assertTrue(CohortRetentionTracker.isCohortActive(CohortInterval.DAY_14))

        // Day 30 Session
        val day30Time = installTime + (30 * oneDayMillis)
        val statusDay30 = CohortRetentionTracker.recordActiveDailySession("2026-10-01", day30Time)
        assertTrue(statusDay30.isDay30Retained)
        assertTrue(CohortRetentionTracker.isCohortActive(CohortInterval.DAY_30))

        // Day 90 Session
        val day90Time = installTime + (90 * oneDayMillis)
        val statusDay90 = CohortRetentionTracker.recordActiveDailySession("2026-11-30", day90Time)
        assertTrue(statusDay90.isDay90Retained)
        assertTrue(CohortRetentionTracker.isCohortActive(CohortInterval.DAY_90))
        assertTrue(CohortRetentionTracker.isCohortMilestoneReached(CohortInterval.DAY_90))
        assertEquals(6, statusDay90.totalActiveDays)
    }

    @Test
    fun testRetentionStatusReport() {
        val installTime = simulatedTimeMillis
        mockStorage.putString("first_install_timestamp", installTime.toString())

        // User logs in on Day 1 and Day 7
        CohortRetentionTracker.recordActiveDailySession("2026-09-02", installTime + oneDayMillis)
        CohortRetentionTracker.recordActiveDailySession("2026-09-08", installTime + (7 * oneDayMillis))

        val status = CohortRetentionTracker.getRetentionStatus(installTime + (8 * oneDayMillis))
        assertEquals(8, status.daysSinceInstall)
        assertEquals(2, status.totalActiveDays)
        assertEquals("2026-09-08", status.lastActiveDate)
        assertTrue(status.isDay1Retained)
        assertTrue(status.isDay7Retained)
        assertFalse(status.isDay14Retained)
        assertFalse(status.isDay30Retained)
        assertFalse(status.isDay90Retained)
        assertTrue(status.activeCohortsReached.contains(CohortInterval.DAY_1))
        assertTrue(status.activeCohortsReached.contains(CohortInterval.DAY_7))
    }
}
