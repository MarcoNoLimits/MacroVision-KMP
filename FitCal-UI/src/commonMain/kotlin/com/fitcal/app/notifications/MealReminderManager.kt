package com.fitcal.app.notifications

import com.fitcal.app.getCurrentEpochMillis
import com.fitcal.app.loadPreference
import com.fitcal.app.savePreference
import com.fitcal.app.syncPlatformMealReminders
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MealReminder(
    val id: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
)

/**
 * Manages user-configurable meal-time reminders to motivate scanning meals throughout the day.
 * Defaults to 3 daily reminders around Breakfast (08:00), Lunch (12:30), and Dinner (19:00),
 * while allowing the user to add more, remove any, toggle individual reminders, or customize times/labels.
 */
object MealReminderManager {

    private const val KEY_MASTER_ENABLED = "meal_reminders_master_enabled"
    private const val KEY_REMINDERS_JSON = "meal_reminders_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val DEFAULT_REMINDERS = listOf(
        MealReminder(id = "breakfast", label = "Breakfast", hour = 8, minute = 0, enabled = true),
        MealReminder(id = "lunch", label = "Lunch", hour = 12, minute = 30, enabled = true),
        MealReminder(id = "dinner", label = "Dinner", hour = 19, minute = 0, enabled = true)
    )

    var preferenceReader: (key: String, defaultValue: String) -> String = { key, default ->
        loadPreference(key, default)
    }

    var preferenceWriter: (key: String, value: String) -> Unit = { key, value ->
        savePreference(key, value)
    }

    var platformSyncHook: (enabled: Boolean, reminders: List<MealReminder>) -> Unit = { enabled, reminders ->
        syncPlatformMealReminders(enabled, reminders)
    }

    fun resetToDefaults() {
        preferenceReader = { key, default -> loadPreference(key, default) }
        preferenceWriter = { key, value -> savePreference(key, value) }
        platformSyncHook = { enabled, reminders -> syncPlatformMealReminders(enabled, reminders) }
    }

    fun isMasterEnabled(): Boolean {
        return preferenceReader(KEY_MASTER_ENABLED, "true").toBooleanStrictOrNull() ?: true
    }

    fun setMasterEnabled(enabled: Boolean) {
        preferenceWriter(KEY_MASTER_ENABLED, enabled.toString())
        syncNotifications()
    }

    fun getReminders(): List<MealReminder> {
        val raw = preferenceReader(KEY_REMINDERS_JSON, "")
        if (raw.isBlank()) {
            return DEFAULT_REMINDERS
        }
        return try {
            json.decodeFromString<List<MealReminder>>(raw)
        } catch (_: Exception) {
            DEFAULT_REMINDERS
        }
    }

    fun saveReminders(reminders: List<MealReminder>): List<MealReminder> {
        val sorted = reminders
            .map {
                it.copy(
                    label = it.label.trim().ifEmpty { "Meal" },
                    hour = it.hour.coerceIn(0, 23),
                    minute = it.minute.coerceIn(0, 59)
                )
            }
            .sortedBy { it.hour * 60 + it.minute }
        preferenceWriter(KEY_REMINDERS_JSON, json.encodeToString(sorted))
        syncNotifications()
        return sorted
    }

    fun addReminder(label: String, hour: Int, minute: Int): List<MealReminder> {
        val current = getReminders().toMutableList()
        val id = "meal_${getCurrentEpochMillis()}_${current.size}"
        current.add(
            MealReminder(
                id = id,
                label = label.trim().ifEmpty { "Snack" },
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                enabled = true
            )
        )
        return saveReminders(current)
    }

    fun updateReminder(
        id: String,
        label: String,
        hour: Int,
        minute: Int,
        enabled: Boolean
    ): List<MealReminder> {
        val updated = getReminders().map { existing ->
            if (existing.id == id) {
                existing.copy(
                    label = label.trim().ifEmpty { existing.label },
                    hour = hour.coerceIn(0, 23),
                    minute = minute.coerceIn(0, 59),
                    enabled = enabled
                )
            } else {
                existing
            }
        }
        return saveReminders(updated)
    }

    fun removeReminder(id: String): List<MealReminder> {
        val filtered = getReminders().filterNot { it.id == id }
        return saveReminders(filtered)
    }

    fun syncNotifications() {
        try {
            platformSyncHook(isMasterEnabled(), getReminders())
        } catch (_: Throwable) {
            // Safe no-op in headless/test environments
        }
    }

    fun formatTime12Hour(hour: Int, minute: Int): String {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val period = if (h < 12) "AM" else "PM"
        val displayHour = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        val hourPad = displayHour.toString().padStart(2, '0')
        val minPad = m.toString().padStart(2, '0')
        return "$hourPad:$minPad $period"
    }

    fun getNotificationContent(label: String): Pair<String, String> {
        val cleanLabel = label.trim().ifEmpty { "Meal" }
        val title = "Time for $cleanLabel! 📸"
        val body = "Snap a quick photo of your ${cleanLabel.lowercase()} to track your calories & macros in seconds."
        return Pair(title, body)
    }
}
