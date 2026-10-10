package com.fitcal.app.telemetry

import com.fitcal.app.appVersionName
import com.fitcal.app.data.PreferenceKeyValueStorage
import com.fitcal.app.getCurrentEpochMillis
import com.fitcal.app.platformName
import com.fitcal.app.setCrashReportingEnabled
import com.fitcal.app.privacy.PrivacyConsent
import com.fitcal.app.ui.components.newMealId
import com.fitcal.shared.data.KeyValueStorage
import com.fitcal.shared.telemetry.AnalyticsBatch
import com.fitcal.shared.telemetry.TelemetryUploader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Product analytics entry point for the UI layer. Event catalogue: ANALYTICS.md.
 *
 * Rules:
 * - Nothing is recorded before the privacy notice is accepted, or after the user
 *   turns off "Share usage analytics" in Settings.
 * - Properties describe behaviour (counts, durations, screens, error classes).
 *   Never send meal names, nutrition values, weights, email or free text.
 */
object Analytics {

    private const val KEY_OPTED_OUT = "analytics_opted_out_v1"
    private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L

    var storageProvider: () -> KeyValueStorage = { PreferenceKeyValueStorage() }
    var nowMillis: () -> Long = { getCurrentEpochMillis() }

    private var sessionId: String = newMealId()
    private var backgroundedAt: Long? = null

    /** Wires the uploader. Call once at app start; [sender] uploads one batch. */
    fun init(sender: suspend (AnalyticsBatch) -> Boolean) {
        TelemetryUploader.storage = storageProvider()
        TelemetryUploader.isEnabled = ::isActive
        TelemetryUploader.sender = sender
        TelemetryUploader.nowMillis = nowMillis
        TelemetryUploader.sessionIdProvider = { sessionId }
        TelemetryUploader.appVersion = appVersionName
        TelemetryUploader.platform = platformName
        onPrivacyStateChanged()
    }

    /** Call whenever consent or the opt-out changes: starts or stops crash reporting to match. */
    fun onPrivacyStateChanged() {
        setCrashReportingEnabled(isActive())
    }

    /** User-facing switch (Settings → Privacy). On by default; legitimate interest, Art. 6(1)(f). */
    fun isUserOptedIn(): Boolean = storageProvider().getString(KEY_OPTED_OUT, "") != "true"

    fun setUserOptedIn(enabled: Boolean) {
        storageProvider().putString(KEY_OPTED_OUT, if (enabled) "false" else "true")
        if (enabled) track("analytics_opted_in")
        onPrivacyStateChanged()
        // A flush while disabled drops whatever is still queued on the device.
        TelemetryUploader.triggerFlush()
    }

    fun isActive(): Boolean = PrivacyConsent.isPrivacyAccepted() && isUserOptedIn()

    fun track(event: String, vararg props: Pair<String, Any?>) {
        TelemetryUploader.track(event, toJson(props.toMap()))
    }

    fun screen(name: String) = track("screen_view", "screen" to name)

    fun flush() = TelemetryUploader.triggerFlush()

    /** App moved to the foreground. A gap of 30+ minutes starts a new session. */
    fun onForeground() {
        val wentAwayAt = backgroundedAt ?: return
        backgroundedAt = null
        val awayMs = nowMillis() - wentAwayAt
        if (awayMs >= SESSION_TIMEOUT_MS) sessionId = newMealId()
        track("app_foreground", "away_s" to awayMs / 1000, "new_session" to (awayMs >= SESSION_TIMEOUT_MS))
    }

    /** App moved to the background: the best moment to upload. */
    fun onBackground() {
        backgroundedAt = nowMillis()
        track("app_background")
        flush()
    }

    fun resetForTesting() {
        storageProvider = { PreferenceKeyValueStorage() }
        nowMillis = { getCurrentEpochMillis() }
        sessionId = newMealId()
        backgroundedAt = null
    }

    internal fun currentSessionIdForTesting(): String = sessionId

    internal fun toJson(map: Map<String, Any?>): JsonObject =
        JsonObject(map.mapValues { (_, v) -> toJsonElement(v) })

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Enum<*> -> JsonPrimitive(value.name.lowercase())
        is Collection<*> -> JsonArray(value.map(::toJsonElement))
        else -> JsonPrimitive(value.toString())
    }
}
