package com.fitcal.shared.telemetry

import com.fitcal.shared.data.FakeKeyValueStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun testEventCarriesTimestampSessionAndProps() = runBlocking {
        TelemetryUploader.nowMillis = { 1_760_000_000_000L }
        TelemetryUploader.sessionIdProvider = { "session-1" }

        TelemetryUploader.trackSync("scan_succeeded", buildJsonObject { put("latency_ms", 1200) })

        val event = TelemetryUploader.getBufferCopy().single()
        assertEquals("scan_succeeded", event.event)
        assertEquals(1_760_000_000_000L, event.client_ts)
        assertEquals("session-1", event.session_id)
        assertEquals(1200, event.props["latency_ms"]!!.jsonPrimitive.int)
    }

    @Test
    fun testNothingRecordedWhenDisabled() = runBlocking {
        TelemetryUploader.isEnabled = { false }
        TelemetryUploader.trackSync("app_open")
        assertEquals(0, TelemetryUploader.getBufferSize())
    }

    @Test
    fun testBufferCapDropsOldest() = runBlocking {
        repeat(TelemetryUploader.BUFFER_CAP + 5) { i -> TelemetryUploader.trackSync("event_$i") }

        val buffer = TelemetryUploader.getBufferCopy()
        assertEquals(TelemetryUploader.BUFFER_CAP, buffer.size)
        assertEquals("event_5", buffer.first().event)
        assertEquals("event_${TelemetryUploader.BUFFER_CAP + 4}", buffer.last().event)
    }

    @Test
    fun testFlushSendsInBatchesAndClearsBuffer() = runBlocking {
        TelemetryUploader.appVersion = "1.0"
        TelemetryUploader.platform = "android"
        repeat(250) { i -> TelemetryUploader.trackSync("event_$i") }

        val batches = mutableListOf<AnalyticsBatch>()
        TelemetryUploader.sender = { batches.add(it); true }

        assertTrue(TelemetryUploader.flush())
        assertEquals(listOf(100, 100, 50), batches.map { it.events.size })
        assertEquals("1.0", batches.first().app_version)
        assertEquals("android", batches.first().platform)
        assertEquals(0, TelemetryUploader.getBufferSize())
    }

    @Test
    fun testFailedFlushKeepsEventsForNextTime() = runBlocking {
        TelemetryUploader.trackSync("event_a")
        TelemetryUploader.trackSync("event_b")

        TelemetryUploader.sender = { throw RuntimeException("offline") }
        assertFalse(TelemetryUploader.flush())
        assertEquals(2, TelemetryUploader.getBufferSize())

        var delivered = 0
        TelemetryUploader.sender = { delivered += it.events.size; true }
        assertTrue(TelemetryUploader.flush())
        assertEquals(2, delivered)
        assertEquals(0, TelemetryUploader.getBufferSize())
    }

    @Test
    fun testEventsSurviveRestartViaStorage() = runBlocking {
        val storage = FakeKeyValueStorage()
        TelemetryUploader.storage = storage
        TelemetryUploader.trackSync("photo_captured")
        TelemetryUploader.trackSync("scan_started")

        // Simulate process death: in-memory state gone, same storage.
        TelemetryUploader.resetForTesting()
        TelemetryUploader.storage = storage
        TelemetryUploader.trackSync("app_open")

        assertEquals(
            listOf("photo_captured", "scan_started", "app_open"),
            TelemetryUploader.getBufferCopy().map { it.event },
        )
    }

    @Test
    fun testFlushWhileDisabledDropsPendingEvents() = runBlocking {
        val storage = FakeKeyValueStorage()
        TelemetryUploader.storage = storage
        TelemetryUploader.trackSync("app_open")

        TelemetryUploader.isEnabled = { false }
        var sent = false
        TelemetryUploader.sender = { sent = true; true }

        assertTrue(TelemetryUploader.flush())
        assertFalse(sent, "Opted-out events must never be uploaded")
        assertEquals(0, TelemetryUploader.getBufferSize())
    }

    @Test
    fun testDiagnosticMessagesAreEscapedSafely() = runBlocking {
        TelemetryUploader.trackDiagnostic("ERROR", "VLM", "bad \"quote\" \\ slash", details = null)
        withTimeout(2_000) { while (TelemetryUploader.getBufferSize() == 0) delay(10) }
        val props = TelemetryUploader.getBufferCopy().single().props
        assertEquals("bad \"quote\" \\ slash", props["message"]!!.jsonPrimitive.content)
    }
}
