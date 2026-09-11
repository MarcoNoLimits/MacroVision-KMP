package com.fitter.app.telemetry

import com.fitter.shared.api.FailoverNutritionClient
import com.fitter.shared.data.KeyValueStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticsCrashHookTest {

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

    @BeforeTest
    fun setUp() {
        mockStorage = MockKeyValueStorage()
        simulatedTimeMillis = 1000000000000L
        DiagnosticsCrashHook.storageProvider = { mockStorage }
        DiagnosticsCrashHook.currentTimeMillisProvider = { simulatedTimeMillis }
        DiagnosticsCrashHook.clearLogs()
    }

    @AfterTest
    fun tearDown() {
        DiagnosticsCrashHook.clearLogs()
        DiagnosticsCrashHook.resetForTesting()
        FailoverNutritionClient.onErrorHook = null
        FailoverNutritionClient.onFatalHook = null
    }

    @Test
    fun testInitialDiagnosticState() {
        assertEquals(0, DiagnosticsCrashHook.getRecentLogs().size)
        assertEquals(0, DiagnosticsCrashHook.getPersistedErrors().size)
        assertEquals(0, DiagnosticsCrashHook.getFatalCrashCount())
        assertEquals(0, DiagnosticsCrashHook.getErrorCount())
    }

    @Test
    fun testInMemoryBuffering() {
        DiagnosticsCrashHook.log(DiagnosticLevel.INFO, "UI", "User navigated to Camera")
        DiagnosticsCrashHook.log(DiagnosticLevel.DEBUG, "CACHE", "Cache hit for meal")
        DiagnosticsCrashHook.log(DiagnosticLevel.WARN, "NETWORK", "High latency detected: 450ms")

        val recent = DiagnosticsCrashHook.getRecentLogs()
        assertEquals(3, recent.size)
        assertEquals("UI", recent[0].tag)
        assertEquals("CACHE", recent[1].tag)
        assertEquals("NETWORK", recent[2].tag)

        // Non-error logs should not increment persisted error count
        assertEquals(0, DiagnosticsCrashHook.getErrorCount())
        assertEquals(0, DiagnosticsCrashHook.getPersistedErrors().size)
    }

    @Test
    fun testVlmErrorLoggingAndPersistence() {
        val testException = IllegalStateException("Gemini quota 429 rate limit exceeded")
        DiagnosticsCrashHook.logVlmError("Gemini-Flash", testException.message!!, testException)

        assertEquals(1, DiagnosticsCrashHook.getErrorCount())
        assertEquals(0, DiagnosticsCrashHook.getFatalCrashCount())

        val persisted = DiagnosticsCrashHook.getPersistedErrors()
        assertEquals(1, persisted.size)
        val entry = persisted.first()
        assertEquals(DiagnosticLevel.ERROR, entry.level)
        assertEquals("VLM", entry.tag)
        assertTrue(entry.message.contains("Gemini-Flash"))
        assertTrue(entry.message.contains("429 rate limit"))
        assertEquals("IllegalStateException", entry.exceptionClass)
        assertNotNull(entry.details)
    }

    @Test
    fun testAdErrorLogging() {
        DiagnosticsCrashHook.logAdError("MAX", "INTERSTITIAL", "204", "No ad fill")

        val recent = DiagnosticsCrashHook.getRecentLogs()
        assertEquals(1, recent.size)
        val entry = recent.first()
        assertEquals(DiagnosticLevel.WARN, entry.level)
        assertEquals("AD_SDK", entry.tag)
        assertTrue(entry.message.contains("MAX"))
        assertTrue(entry.message.contains("No ad fill"))
    }

    @Test
    fun testFatalCrashLogging() {
        val oomError = RuntimeException("Out of memory on bitmap decode")
        DiagnosticsCrashHook.recordFatalCrash("CAMERA_PIPELINE", "Fatal decoding failure", oomError)

        assertEquals(1, DiagnosticsCrashHook.getFatalCrashCount())
        assertEquals(1, DiagnosticsCrashHook.getErrorCount())

        val persisted = DiagnosticsCrashHook.getPersistedErrors()
        assertEquals(1, persisted.size)
        val entry = persisted.first()
        assertEquals(DiagnosticLevel.FATAL, entry.level)
        assertEquals("CAMERA_PIPELINE", entry.tag)
        assertEquals("RuntimeException", entry.exceptionClass)
    }

    @Test
    fun testPersistedErrorsSurviveInMemoryClear() {
        DiagnosticsCrashHook.log(DiagnosticLevel.ERROR, "AUTH", "API Token Expired")
        assertEquals(1, DiagnosticsCrashHook.getPersistedErrors().size)

        // Re-initialize manager simulating app restart
        DiagnosticsCrashHook.resetForTesting()
        DiagnosticsCrashHook.storageProvider = { mockStorage }

        // In-memory buffer starts empty, but persisted errors are safely retrieved
        assertEquals(0, DiagnosticsCrashHook.getRecentLogs().size)
        val persisted = DiagnosticsCrashHook.getPersistedErrors()
        assertEquals(1, persisted.size)
        assertEquals("AUTH", persisted.first().tag)
    }

    @Test
    fun testFailoverNutritionClientHookIntegration() {
        // Wire hooks as App.kt does
        FailoverNutritionClient.onErrorHook = { provider, error ->
            DiagnosticsCrashHook.logVlmError(provider, error.message ?: "Unknown", error)
        }
        FailoverNutritionClient.onFatalHook = { tag, error ->
            DiagnosticsCrashHook.recordFatalCrash(tag, error.message ?: "Pipeline down", error)
        }

        // Simulate provider failover error
        val providerError = RuntimeException("OpenRouter timeout 504")
        FailoverNutritionClient.onErrorHook?.invoke("OpenRouter", providerError)

        assertEquals(1, DiagnosticsCrashHook.getErrorCount())
        val lastError = DiagnosticsCrashHook.getLastError()
        assertNotNull(lastError)
        assertEquals("VLM", lastError.tag)
        assertTrue(lastError.message.contains("OpenRouter"))

        // Simulate complete pipeline failure
        val fatalError = Exception("All configured APIs failed")
        FailoverNutritionClient.onFatalHook?.invoke("FailoverNutritionClient", fatalError)

        assertEquals(1, DiagnosticsCrashHook.getFatalCrashCount())
        assertEquals(2, DiagnosticsCrashHook.getErrorCount())
    }
}
