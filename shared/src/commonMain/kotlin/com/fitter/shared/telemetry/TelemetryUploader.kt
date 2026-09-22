package com.fitter.shared.telemetry

import com.fitter.shared.auth.SupabaseClientFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsEventRow(
    val event_type: String,
    val properties: String? = null,
    val user_id: String? = null,   // auth.uid() — required by migration 0003 insert policy
    val occurred_at: String? = null
)

/**
 * Buffers analytics events and flushes them to Supabase analytics_events table.
 *
 * Phase 6 requirements:
 * - Buffer cap: 500 events (was 50)
 * - user_id included in every row (no longer nullable in DB policy after 0003)
 * - Retry with backoff on flush failure: 1s → 5s → 30s → drop
 * - Thread-safe buffer via Mutex
 */
object TelemetryUploader {

    private const val BUFFER_CAP = 500
    private val mutex = Mutex()
    private val buffer = mutableListOf<AnalyticsEventRow>()
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Injected at app startup (in SupabaseAuthService.ensureSignedIn callback) */
    var userIdProvider: () -> String? = { null }

    /** Lazily resolved — prevents ExceptionInInitializerError in test environments where
     *  SupabaseClientFactory hasn't been configured. */
    var client: SupabaseClient? = null
        get() = field ?: try { SupabaseClientFactory.getOrCreate() } catch (_: Exception) { null }

    /**
     * Record an analytics event.
     * If buffer is at cap, the oldest events are dropped (ring buffer behaviour).
     * @param eventType  e.g. "scan_completed", "premium_purchased"
     * @param properties optional JSON string of event-specific properties
     * @param userId     optional override; falls back to [userIdProvider]
     * @param occurredAt optional ISO timestamp; defaults to server `now()`
     */
    fun recordEvent(
        eventType: String,
        properties: String? = null,
        userId: String? = null,
        occurredAt: String? = null
    ) {
        val resolvedUserId = userId ?: userIdProvider()
        val event = AnalyticsEventRow(
            event_type = eventType,
            properties = properties,
            user_id = resolvedUserId,
            occurred_at = occurredAt
        )
        scope.launch {
            mutex.withLock {
                if (buffer.size >= BUFFER_CAP) {
                    buffer.removeAt(0)  // Drop oldest
                }
                buffer.add(event)
            }
        }
    }

    /** Testing hooks */
    var insertHookForTesting: (suspend (List<AnalyticsEventRow>) -> Unit)? = null
    var retryDelaysForTesting: List<Long>? = null

    suspend fun recordEventSync(
        eventType: String,
        properties: String? = null,
        userId: String? = null,
        occurredAt: String? = null
    ) {
        val resolvedUserId = userId ?: userIdProvider()
        val event = AnalyticsEventRow(
            event_type = eventType,
            properties = properties,
            user_id = resolvedUserId,
            occurred_at = occurredAt
        )
        mutex.withLock {
            if (buffer.size >= BUFFER_CAP) {
                buffer.removeAt(0)
            }
            buffer.add(event)
        }
    }

    suspend fun getBufferSize(): Int = mutex.withLock { buffer.size }
    suspend fun getBufferCopy(): List<AnalyticsEventRow> = mutex.withLock { buffer.toList() }
    suspend fun clearBuffer() = mutex.withLock { buffer.clear() }

    fun resetForTesting() {
        buffer.clear()
        userIdProvider = { null }
        insertHookForTesting = null
        retryDelaysForTesting = null
    }

    /**
     * Flush all buffered events to Supabase with exponential backoff retry.
     * Retry schedule: attempt 1 → immediate, 2 → 1s delay, 3 → 5s delay, 4 → 30s delay → drop.
     */
    fun triggerFlush() {
        scope.launch {
            flushLocked()
        }
    }

    suspend fun flushLocked(maxAttempts: Int = 4) {
        val toFlush = mutex.withLock {
            if (buffer.isEmpty()) return
            val copy = buffer.toList()
            buffer.clear()
            copy
        }

        val delays = retryDelaysForTesting ?: listOf(0L, 1_000L, 5_000L, 30_000L)
        var lastException: Exception? = null

        for (attempt in 0 until maxAttempts) {
            val delayMs = delays.getOrNull(attempt) ?: 30_000L
            if (delayMs > 0) delay(delayMs)

            try {
                val hook = insertHookForTesting
                if (hook != null) {
                    hook(toFlush)
                    return  // Success — done
                }
                val supabase = client
                if (supabase == null) {
                    println("TelemetryUploader: no Supabase client configured, skipping flush")
                    return
                }
                supabase.from("analytics_events").insert(toFlush)
                return  // Success — done
            } catch (e: Exception) {
                lastException = e
                println("TelemetryUploader flush attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
            }
        }

        // All attempts failed — drop batch, log final error
        println("TelemetryUploader: dropped ${toFlush.size} events after $maxAttempts attempts. Last error: ${lastException?.message}")
    }

    // ── Domain-specific telemetry helpers ────────────────────────────────────
    // Thin wrappers over recordEvent() called by AdTelemetryManager,
    // CohortRetentionTracker, and DiagnosticsCrashHook.

    fun trackAdImpression(adFormat: String) {
        recordEvent(
            eventType = "ad_impression",
            properties = """{"format":"$adFormat"}"""
        )
    }

    fun trackAdRevenue(
        adUnitId: String?,
        networkName: String?,
        revenue: Double,
        format: String?,
        placement: String?
    ) {
        recordEvent(
            eventType = "ad_revenue",
            properties = """{"ad_unit_id":"${adUnitId.orEmpty()}","network":"${networkName.orEmpty()}","revenue":$revenue,"format":"${format.orEmpty()}","placement":"${placement.orEmpty()}"}"""
        )
    }

    fun trackCohortRetention(dayNumber: Int, totalActiveDays: Int, dateKey: String) {
        recordEvent(
            eventType = "cohort_retention",
            properties = """{"day_number":$dayNumber,"total_active_days":$totalActiveDays,"date_key":"$dateKey"}"""
        )
    }

    fun trackDiagnostic(level: String, tag: String, message: String, details: String?) {
        // Inline safe JSON escaping without pulling in heavy dependencies
        val escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedDetails = details?.replace("\\", "\\\\")?.replace("\"", "\\\"")
        val detailsJson = if (escapedDetails != null) "\"$escapedDetails\"" else "null"
        recordEvent(
            eventType = "diagnostic",
            properties = """{"level":"$level","tag":"$tag","message":"$escapedMessage","details":$detailsJson}"""
        )
    }
}
