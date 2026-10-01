package com.fitcal.app.telemetry

import com.fitcal.app.data.PreferenceKeyValueStorage
import com.fitcal.app.getCurrentEpochMillis
import com.fitcal.shared.data.KeyValueStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Diagnostic log levels for runtime telemetry and observability.
 */
enum class DiagnosticLevel {
    DEBUG, INFO, WARN, ERROR, FATAL
}

/**
 * Encapsulates a structured diagnostic or crash event.
 */
@Serializable
data class DiagnosticEntry(
    val timestamp: Long,
    val level: DiagnosticLevel,
    val tag: String,
    val message: String,
    val details: String? = null,
    val exceptionClass: String? = null
)

/**
 * Lightweight crash & diagnostic telemetry hook.
 * Records events in-memory in a low-overhead circular buffer and persists fatal/error
 * events to KeyValueStorage for post-production analysis and debugging.
 */
object DiagnosticsCrashHook {

    private const val KEY_PERSISTED_ERRORS = "diagnostics_persisted_errors"
    private const val KEY_FATAL_CRASH_COUNT = "diagnostics_fatal_crash_count"
    private const val KEY_TOTAL_ERROR_COUNT = "diagnostics_total_error_count"

    private const val MAX_IN_MEMORY_ENTRIES = 100
    private const val MAX_PERSISTED_ERRORS = 25

    var storageProvider: () -> KeyValueStorage = { PreferenceKeyValueStorage() }
    var currentTimeMillisProvider: () -> Long = { getCurrentEpochMillis() }

    private val inMemoryEntries = mutableListOf<DiagnosticEntry>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun resetForTesting() {
        storageProvider = { PreferenceKeyValueStorage() }
        currentTimeMillisProvider = { getCurrentEpochMillis() }
        inMemoryEntries.clear()
    }

    /**
     * Records a diagnostic event. If the event is [DiagnosticLevel.ERROR] or [DiagnosticLevel.FATAL],
     * it is automatically written to persistent KeyValueStorage.
     */
    fun log(
        level: DiagnosticLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        details: String? = null
    ) {
        val timestamp = currentTimeMillisProvider()
        val exceptionClass = throwable?.let { it::class.simpleName ?: "Throwable" }
        val extractedDetails = details ?: throwable?.let {
            val trace = it.stackTraceToString()
            if (trace.length > 500) trace.take(500) + "... [truncated]" else trace
        }

        val entry = DiagnosticEntry(
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = message,
            details = extractedDetails,
            exceptionClass = exceptionClass
        )

        // In-memory buffer update
        if (inMemoryEntries.size >= MAX_IN_MEMORY_ENTRIES) {
            inMemoryEntries.removeAt(0)
        }
        inMemoryEntries.add(entry)

        // Persistent sink for errors & fatal events
        if (level == DiagnosticLevel.ERROR || level == DiagnosticLevel.FATAL) {
            persistErrorEntry(entry)
            com.fitcal.shared.telemetry.TelemetryUploader.trackDiagnostic(
                level = level.name,
                tag = tag,
                message = message,
                details = extractedDetails
            )
            val storage = storageProvider()
            val currentErrors = storage.getInt(KEY_TOTAL_ERROR_COUNT, 0)
            storage.putInt(KEY_TOTAL_ERROR_COUNT, currentErrors + 1)

            if (level == DiagnosticLevel.FATAL) {
                val currentFatal = storage.getInt(KEY_FATAL_CRASH_COUNT, 0)
                storage.putInt(KEY_FATAL_CRASH_COUNT, currentFatal + 1)
            }
        }
    }

    /**
     * Specialized logger for Vision-Language Model (VLM) failover errors.
     */
    fun logVlmError(provider: String, message: String, throwable: Throwable? = null) {
        log(
            level = DiagnosticLevel.ERROR,
            tag = "VLM",
            message = "Provider '$provider' failed: $message",
            throwable = throwable
        )
    }

    /**
     * Specialized logger for Ad Mediation & SDK failures (MAX / AdMob).
     */
    fun logAdError(sdk: String, format: String, errorCode: String?, message: String) {
        log(
            level = DiagnosticLevel.WARN,
            tag = "AD_SDK",
            message = "[$sdk][$format] Code: ${errorCode ?: "N/A"}, Error: $message"
        )
    }

    /**
     * Specialized logger for unhandled exceptions or fatal application crashes.
     */
    fun recordFatalCrash(tag: String, message: String, throwable: Throwable? = null) {
        log(
            level = DiagnosticLevel.FATAL,
            tag = tag,
            message = message,
            throwable = throwable
        )
    }

    /**
     * Returns recent in-memory log entries up to [limit].
     */
    fun getRecentLogs(limit: Int = 50): List<DiagnosticEntry> {
        val safeLimit = limit.coerceIn(1, inMemoryEntries.size.coerceAtLeast(1))
        return inMemoryEntries.takeLast(safeLimit)
    }

    /**
     * Retrieves all persisted error entries from KeyValueStorage.
     */
    fun getPersistedErrors(): List<DiagnosticEntry> {
        val storage = storageProvider()
        val rawJson = storage.getString(KEY_PERSISTED_ERRORS, "")
        if (rawJson.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<DiagnosticEntry>>(rawJson)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Returns the cumulative count of fatal crashes recorded in persistent storage.
     */
    fun getFatalCrashCount(): Int {
        val storage = storageProvider()
        return storage.getInt(KEY_FATAL_CRASH_COUNT, 0)
    }

    /**
     * Returns the cumulative count of errors recorded in persistent storage.
     */
    fun getErrorCount(): Int {
        val storage = storageProvider()
        return storage.getInt(KEY_TOTAL_ERROR_COUNT, 0)
    }

    /**
     * Returns the most recent diagnostic entry, checking in-memory first and then persistent storage.
     */
    fun getLastError(): DiagnosticEntry? {
        return inMemoryEntries.lastOrNull() ?: getPersistedErrors().lastOrNull()
    }

    /**
     * Clears in-memory buffer and resets persistent diagnostic storage.
     */
    fun clearLogs() {
        inMemoryEntries.clear()
        val storage = storageProvider()
        storage.remove(KEY_PERSISTED_ERRORS)
        storage.remove(KEY_FATAL_CRASH_COUNT)
        storage.remove(KEY_TOTAL_ERROR_COUNT)
    }

    private fun persistErrorEntry(entry: DiagnosticEntry) {
        try {
            val storage = storageProvider()
            val existing = getPersistedErrors().toMutableList()
            if (existing.size >= MAX_PERSISTED_ERRORS) {
                existing.removeAt(0)
            }
            existing.add(entry)
            val updatedJson = json.encodeToString(existing)
            storage.putString(KEY_PERSISTED_ERRORS, updatedJson)
        } catch (e: Throwable) {
            // Failsafe: avoid crashing the logger
        }
    }
}
