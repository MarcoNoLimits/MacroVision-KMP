package com.fitcal.shared.telemetry

import com.fitcal.shared.data.KeyValueStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One analytics event as sent to the gateway's POST /v1/events. */
@Serializable
data class AnalyticsEvent(
    val event: String,
    val props: JsonObject = JsonObject(emptyMap()),
    val client_ts: Long = 0L,
    val session_id: String? = null,
    // Local identity so a flush removes exactly the events it sent.
    @Transient val localId: Long = 0L,
)

@Serializable
data class AnalyticsBatch(
    val app_version: String,
    val platform: String,
    val events: List<AnalyticsEvent>,
)

/**
 * Buffers analytics events on the device and uploads them in batches through the
 * gateway (POST /v1/events), which takes the user from the verified JWT.
 *
 * - Events are persisted in [storage], so they survive process death and offline use.
 * - The buffer is capped at [BUFFER_CAP]; the oldest events are dropped first.
 * - A failed upload keeps the events for the next flush instead of dropping them.
 * - Nothing is recorded while [isEnabled] is false (no consent, or the user opted out).
 */
object TelemetryUploader {

    const val BUFFER_CAP = 500
    const val BATCH_SIZE = 100
    private const val STORAGE_KEY = "analytics_pending_events_v1"

    private val mutex = Mutex()
    private val flushMutex = Mutex()
    private val buffer = mutableListOf<AnalyticsEvent>()
    private var loadedFromStorage = false
    private var nextLocalId = 1L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Kept for diagnostics and tests. Not sent: the gateway derives the user from the JWT. */
    var userIdProvider: () -> String? = { null }

    var isEnabled: () -> Boolean = { true }
    var sender: (suspend (AnalyticsBatch) -> Boolean)? = null
    var storage: KeyValueStorage? = null
    var nowMillis: () -> Long = { 0L }
    var sessionIdProvider: () -> String? = { null }
    var appVersion: String = "unknown"
    var platform: String = "unknown"

    /** Records an event. Returns immediately; the write happens on a background coroutine. */
    fun track(event: String, props: JsonObject = JsonObject(emptyMap())) {
        val entry = newEvent(event, props) ?: return
        scope.launch { append(entry) }
    }

    /** Same as [track] but completes the buffer write before returning (tests, shutdown paths). */
    suspend fun trackSync(event: String, props: JsonObject = JsonObject(emptyMap())) {
        val entry = newEvent(event, props) ?: return
        append(entry)
    }

    private fun newEvent(event: String, props: JsonObject): AnalyticsEvent? {
        if (!isEnabled()) return null
        return AnalyticsEvent(
            event = event,
            props = props,
            client_ts = nowMillis(),
            session_id = sessionIdProvider(),
        )
    }

    private suspend fun append(entry: AnalyticsEvent) = mutex.withLock {
        loadIfNeeded()
        buffer.add(entry.copy(localId = nextLocalId++))
        while (buffer.size > BUFFER_CAP) buffer.removeAt(0)
        persist()
    }

    fun triggerFlush() {
        scope.launch { flush() }
    }

    /**
     * Uploads pending events in batches of [BATCH_SIZE].
     * Returns true when the buffer was fully delivered; false leaves the rest for next time.
     */
    suspend fun flush(): Boolean = flushMutex.withLock {
        if (!isEnabled()) {
            clearBuffer()
            return true
        }
        val send = sender ?: return false
        var delivered = true
        while (delivered) {
            val batch = mutex.withLock {
                loadIfNeeded()
                buffer.take(BATCH_SIZE)
            }
            if (batch.isEmpty()) return true
            delivered = try {
                send(AnalyticsBatch(appVersion, platform, batch))
            } catch (e: Exception) {
                println("TelemetryUploader: upload failed, keeping ${batch.size} events: ${e.message}")
                false
            }
            if (delivered) {
                val sentIds = batch.map { it.localId }.toSet()
                mutex.withLock {
                    buffer.removeAll { it.localId in sentIds }
                    persist()
                }
            }
        }
        false
    }

    suspend fun getBufferSize(): Int = mutex.withLock { loadIfNeeded(); buffer.size }
    suspend fun getBufferCopy(): List<AnalyticsEvent> = mutex.withLock { loadIfNeeded(); buffer.toList() }

    /** Drops every pending event (opt-out, account deletion). */
    suspend fun clearBuffer() = mutex.withLock {
        buffer.clear()
        loadedFromStorage = true
        storage?.remove(STORAGE_KEY)
    }

    fun resetForTesting() {
        buffer.clear()
        loadedFromStorage = false
        nextLocalId = 1L
        userIdProvider = { null }
        isEnabled = { true }
        sender = null
        storage = null
        nowMillis = { 0L }
        sessionIdProvider = { null }
        appVersion = "unknown"
        platform = "unknown"
    }

    private fun loadIfNeeded() {
        if (loadedFromStorage) return
        loadedFromStorage = true
        val raw = storage?.getString(STORAGE_KEY, "").orEmpty()
        if (raw.isBlank()) return
        val restored = try {
            json.decodeFromString<List<AnalyticsEvent>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
        // Restored events sort before anything recorded since start-up.
        buffer.addAll(0, restored.map { it.copy(localId = nextLocalId++) })
        while (buffer.size > BUFFER_CAP) buffer.removeAt(0)
    }

    private fun persist() {
        val store = storage ?: return
        if (buffer.isEmpty()) store.remove(STORAGE_KEY)
        else store.putString(STORAGE_KEY, json.encodeToString(buffer.toList()))
    }

    // ── Domain helpers used by AdTelemetryManager, CohortRetentionTracker and
    //    DiagnosticsCrashHook ──────────────────────────────────────────────────

    fun trackAdImpression(adFormat: String) {
        track("ad_impression", buildJsonObject { put("format", adFormat) })
    }

    fun trackAdRevenue(
        adUnitId: String?,
        networkName: String?,
        revenue: Double,
        format: String?,
        placement: String?
    ) {
        track("ad_revenue", buildJsonObject {
            put("ad_unit_id", adUnitId.orEmpty())
            put("network", networkName.orEmpty())
            put("revenue", revenue)
            put("format", format.orEmpty())
            put("placement", placement.orEmpty())
        })
    }

    fun trackCohortRetention(dayNumber: Int, totalActiveDays: Int, dateKey: String) {
        track("cohort_retention", buildJsonObject {
            put("day_number", dayNumber)
            put("total_active_days", totalActiveDays)
            put("date_key", dateKey)
        })
    }

    fun trackDiagnostic(level: String, tag: String, message: String, details: String?) {
        track("diagnostic", buildJsonObject {
            put("level", level)
            put("tag", tag)
            put("message", message.take(300))
            put("details", details?.take(1000))
        })
    }
}
