package com.fitcal.shared.data

import com.fitcal.shared.data.SupabaseMealRepository.Companion.parseEatenAt
import com.fitcal.shared.data.SupabaseMealRepository.Companion.nextDay
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 5 verification: proves that pullRemote maps eaten_at correctly.
 * Uses parseEatenAt() and nextDay() helpers directly (pure functions, no DB needed).
 */
class SupabaseMealRepositoryTest {

    @Test
    fun testParseEatenAt_threeDistinctTimestamps() {
        // Three rows with different eaten_at values
        val fixture = listOf(
            "2026-09-05T08:15:00Z",
            "2026-09-05T13:45:00Z",
            "2026-09-05T21:00:00Z"
        )

        val results = fixture.map { parseEatenAt(it, "2026-09-05") }

        // All dates must match the eaten_at date part
        assertEquals("2026-09-05", results[0].first, "Morning date")
        assertEquals("2026-09-05", results[1].first, "Afternoon date")
        assertEquals("2026-09-05", results[2].first, "Evening date")

        // Times must differ (not all "12:00 PM")
        val times = results.map { it.second }
        val uniqueTimes = times.toSet()
        assertEquals(3, uniqueTimes.size, "All three eaten_at times should produce distinct display strings, got: $times")

        // Verify specific time format
        assertEquals("08:15 AM", results[0].second, "Morning time")
        assertEquals("01:45 PM", results[1].second, "Afternoon time")
        assertEquals("09:00 PM", results[2].second, "Evening time")
    }

    @Test
    fun testParseEatenAt_nullFallsBackToNoon() {
        val (date, time) = parseEatenAt(null, "2026-09-05")
        assertEquals("2026-09-05", date)
        assertEquals("12:00 PM", time)
    }

    @Test
    fun testParseEatenAt_blankFallsBackToNoon() {
        val (date, time) = parseEatenAt("", "2026-09-07")
        assertEquals("2026-09-07", date)
        assertEquals("12:00 PM", time)
    }

    @Test
    fun testParseEatenAt_midnight() {
        // Midnight: hour=0 → 12:xx AM
        val (date, time) = parseEatenAt("2026-09-05T00:30:00Z", "2026-09-05")
        assertEquals("2026-09-05", date)
        assertEquals("12:30 AM", time)
    }

    @Test
    fun testParseEatenAt_noon() {
        // Noon: hour=12 → 12:xx PM
        val (date, time) = parseEatenAt("2026-09-05T12:00:00Z", "2026-09-05")
        assertEquals("2026-09-05", date)
        assertEquals("12:00 PM", time)
    }

    @Test
    fun testParseEatenAt_dateFromServerDiffersFromFallback() {
        // eaten_at on the 6th but fallback dateKey is the 5th — server date wins
        val (date, time) = parseEatenAt("2026-09-06T09:00:00Z", "2026-09-05")
        assertEquals("2026-09-06", date, "Server date should win over fallback dateKey")
        assertEquals("09:00 AM", time)
    }

    @Test
    fun testNextDay_midMonth() {
        assertEquals("2026-09-06", nextDay("2026-09-05"))
    }

    @Test
    fun testNextDay_endOfMonth() {
        assertEquals("2026-09-01", nextDay("2026-08-31"))
    }

    @Test
    fun testNextDay_endOfYear() {
        assertEquals("2027-01-01", nextDay("2026-12-31"))
    }

    @Test
    fun testNextDay_leapYear() {
        assertEquals("2024-03-01", nextDay("2024-02-29"))
    }

    @Test
    fun testBuildEatenAt_pm() {
        val result = com.fitcal.shared.sync.SyncEngine.buildEatenAt("2026-09-05", "02:30 PM")
        assertEquals("2026-09-05T14:30:00Z", result)
    }

    @Test
    fun testBuildEatenAt_am() {
        val result = com.fitcal.shared.sync.SyncEngine.buildEatenAt("2026-09-05", "08:15 AM")
        assertEquals("2026-09-05T08:15:00Z", result)
    }

    @Test
    fun testBuildEatenAt_midnight() {
        val result = com.fitcal.shared.sync.SyncEngine.buildEatenAt("2026-09-05", "12:00 AM")
        assertEquals("2026-09-05T00:00:00Z", result)
    }
}
