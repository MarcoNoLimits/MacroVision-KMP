package com.fitcal.shared.telemetry

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 6 verification: TelemetryUploader unit tests.
 * Covers user_id attachment, buffer cap of 500, flush-success, and flush-failure-retry with backoff.
 */
class TelemetryUploaderTest {

    @BeforeTest
    fun setUp() {
        TelemetryUploader.resetForTesting()
    }

    @AfterTest
    fun tearDown() {
        TelemetryUploader.resetForTesting()
    }

    @Test
    fun testUserIdAttachedFromAuthSession() = runBlocking {
        TelemetryUploader.userIdProvider = { "auth-user-uuid-123" }

        TelemetryUploader.recordEventSync(
            eventType = "scan_completed",
            properties = """{"duration_ms": 1200}"""
        )

        val buffered = TelemetryUploader.getBufferCopy()
        assertEquals(1, buffered.size)
        assertEquals("scan_completed", buffered[0].event_type)
        assertEquals("auth-user-uuid-123", buffered[0].user_id)
    }

    @Test
    fun testBufferCapAt500DropsOldest() = runBlocking {
        TelemetryUploader.userIdProvider = { "test-user" }

        // Insert 505 events (cap is 500)
        repeat(505) { i ->
            TelemetryUploader.recordEventSync(
                eventType = "event_$i",
                properties = null
            )
        }

        assertEquals(500, TelemetryUploader.getBufferSize())
        val buffer = TelemetryUploader.getBufferCopy()
        // The first 5 events (0..4) should have been dropped
        assertEquals("event_5", buffer.first().event_type)
        assertEquals("event_504", buffer.last().event_type)
    }

    @Test
    fun testFlushSuccessClearsBuffer() = runBlocking {
        TelemetryUploader.userIdProvider = { "user-abc" }
        TelemetryUploader.recordEventSync("event_a")
        TelemetryUploader.recordEventSync("event_b")
        TelemetryUploader.recordEventSync("event_c")

        var deliveredCount = 0
        TelemetryUploader.insertHookForTesting = { events ->
            deliveredCount = events.size
        }

        TelemetryUploader.flushLocked(maxAttempts = 1)

        assertEquals(3, deliveredCount)
        assertEquals(0, TelemetryUploader.getBufferSize(), "Buffer should be empty after successful flush")
    }

    @Test
    fun testFlushFailureRetriesAndEventuallySucceeds() = runBlocking {
        TelemetryUploader.userIdProvider = { "user-abc" }
        TelemetryUploader.recordEventSync("retry_event")

        // Fast zero-delay schedule for unit test
        TelemetryUploader.retryDelaysForTesting = listOf(0L, 0L, 0L, 0L)

        var attempts = 0
        TelemetryUploader.insertHookForTesting = {
            attempts++
            if (attempts < 3) {
                throw RuntimeException("Simulated transient network failure on attempt $attempts")
            }
            // Attempt 3 succeeds
        }

        TelemetryUploader.flushLocked(maxAttempts = 4)

        assertEquals(3, attempts, "Should have retried until attempt 3 succeeded")
        assertEquals(0, TelemetryUploader.getBufferSize(), "Buffer should be empty after successful retry")
    }

    @Test
    fun testFlushCompleteFailureDropsBatchWithoutCrashing() = runBlocking {
        TelemetryUploader.userIdProvider = { "user-abc" }
        TelemetryUploader.recordEventSync("failing_event")

        TelemetryUploader.retryDelaysForTesting = listOf(0L, 0L, 0L, 0L)

        var attempts = 0
        TelemetryUploader.insertHookForTesting = {
            attempts++
            throw RuntimeException("Permanent network outage")
        }

        // Must not throw an unhandled exception
        TelemetryUploader.flushLocked(maxAttempts = 4)

        assertEquals(4, attempts, "Should have exhausted all 4 attempts")
        assertEquals(0, TelemetryUploader.getBufferSize(), "Buffer should be cleared after max attempts exceeded")
    }
}
