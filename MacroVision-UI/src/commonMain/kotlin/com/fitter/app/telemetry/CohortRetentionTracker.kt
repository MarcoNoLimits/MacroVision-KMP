package com.fitter.app.telemetry

import com.fitter.app.data.PreferenceKeyValueStorage
import com.fitter.app.getCurrentDateString
import com.fitter.app.getCurrentEpochMillis
import com.fitter.shared.data.KeyValueStorage

/**
 * Standard user retention cohort intervals.
 */
enum class CohortInterval(val days: Int, val label: String) {
    DAY_1(1, "Day 1"),
    DAY_7(7, "Day 7"),
    DAY_14(14, "Day 14"),
    DAY_30(30, "Day 30"),
    DAY_90(90, "Day 90");

    companion object {
        fun fromDays(days: Int): CohortInterval? = entries.firstOrNull { it.days == days }
    }
}

/**
 * Summary of user retention state and cohort progression.
 */
data class RetentionStatus(
    val daysSinceInstall: Int,
    val totalActiveDays: Int,
    val activeCohortsReached: Set<CohortInterval>,
    val isDay1Retained: Boolean,
    val isDay7Retained: Boolean,
    val isDay14Retained: Boolean,
    val isDay30Retained: Boolean,
    val isDay90Retained: Boolean,
    val lastActiveDate: String
)

/**
 * Tracks user retention cohort intervals (Day 1, Day 7, Day 14, Day 30, Day 90)
 * relative to `first_install_timestamp` and records active daily sessions in KeyValueStorage.
 */
object CohortRetentionTracker {

    private const val KEY_FIRST_INSTALL_TIMESTAMP = "first_install_timestamp"
    private const val KEY_ACTIVE_DATES = "retention_active_dates"
    private const val KEY_TOTAL_ACTIVE_DAYS = "retention_total_active_days"
    private const val KEY_LAST_ACTIVE_DATE = "retention_last_active_date"
    private const val PREFIX_COHORT_ACTIVE = "retention_cohort_active_"
    private const val PREFIX_COHORT_REACHED = "retention_cohort_reached_"

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000L

    var storageProvider: () -> KeyValueStorage = { PreferenceKeyValueStorage() }
    var currentTimeMillisProvider: () -> Long = { getCurrentEpochMillis() }
    var currentDateStringProvider: () -> String = { getCurrentDateString() }

    fun resetForTesting() {
        storageProvider = { PreferenceKeyValueStorage() }
        currentTimeMillisProvider = { getCurrentEpochMillis() }
        currentDateStringProvider = { getCurrentDateString() }
    }

    /**
     * Retrieves or initializes the first install timestamp epoch in milliseconds.
     * Shares the key with ScanQuotaManager for unified install tracking.
     */
    fun getFirstInstallTimestamp(): Long {
        val storage = storageProvider()
        val saved = storage.getString(KEY_FIRST_INSTALL_TIMESTAMP, "")
        if (saved.isNotEmpty()) {
            return saved.toLongOrNull() ?: currentTimeMillisProvider()
        }
        val now = currentTimeMillisProvider()
        storage.putString(KEY_FIRST_INSTALL_TIMESTAMP, now.toString())
        return now
    }

    /**
     * Calculates the elapsed days since the user first installed the app.
     */
    fun getDaysSinceInstall(nowMillis: Long = currentTimeMillisProvider()): Int {
        val installTime = getFirstInstallTimestamp()
        val diff = (nowMillis - installTime).coerceAtLeast(0L)
        return (diff / MILLIS_PER_DAY).toInt()
    }

    /**
     * Records an active daily session for the specified [dateKey].
     * Deduplicates multiple launches on the same calendar day.
     * Evaluates whether cohort intervals (Day 1, Day 7, Day 14, Day 30, Day 90)
     * were active or reached.
     */
    fun recordActiveDailySession(
        dateKey: String = currentDateStringProvider(),
        nowMillis: Long = currentTimeMillisProvider()
    ): RetentionStatus {
        val storage = storageProvider()
        val currentDatesRaw = storage.getString(KEY_ACTIVE_DATES, "")
        val activeDates = if (currentDatesRaw.isBlank()) {
            mutableSetOf()
        } else {
            currentDatesRaw.split(",").filter { it.isNotBlank() }.toMutableSet()
        }

        val daysSinceInstall = getDaysSinceInstall(nowMillis)

        if (!activeDates.contains(dateKey)) {
            activeDates.add(dateKey)
            storage.putString(KEY_ACTIVE_DATES, activeDates.joinToString(","))
            val currentTotalDays = storage.getInt(KEY_TOTAL_ACTIVE_DAYS, 0)
            storage.putInt(KEY_TOTAL_ACTIVE_DAYS, currentTotalDays + 1)
            storage.putString(KEY_LAST_ACTIVE_DATE, dateKey)

            com.fitter.shared.telemetry.TelemetryUploader.trackCohortRetention(
                dayNumber = daysSinceInstall,
                totalActiveDays = currentTotalDays + 1,
                dateKey = dateKey
            )

            // Evaluate cohort intervals
            for (cohort in CohortInterval.entries) {
                // Exact cohort retention check
                if (daysSinceInstall == cohort.days) {
                    storage.putString("$PREFIX_COHORT_ACTIVE${cohort.days}", "true")
                }
                // Milestone reached check
                if (daysSinceInstall >= cohort.days) {
                    storage.putString("$PREFIX_COHORT_REACHED${cohort.days}", "true")
                }
            }
        }

        return getRetentionStatus(nowMillis)
    }

    /**
     * Checks if the user was active on the exact cohort interval day (e.g. Day 1, Day 7, etc.).
     */
    fun isCohortActive(cohort: CohortInterval): Boolean {
        val storage = storageProvider()
        return storage.getString("$PREFIX_COHORT_ACTIVE${cohort.days}", "false") == "true"
    }

    /**
     * Checks if the user has reached or exceeded the specified cohort milestone and was active.
     */
    fun isCohortMilestoneReached(cohort: CohortInterval): Boolean {
        val storage = storageProvider()
        return storage.getString("$PREFIX_COHORT_REACHED${cohort.days}", "false") == "true"
    }

    /**
     * Returns the total count of unique active calendar days recorded.
     */
    fun getTotalActiveDays(): Int {
        val storage = storageProvider()
        return storage.getInt(KEY_TOTAL_ACTIVE_DAYS, 0)
    }

    /**
     * Returns the chronological list of all recorded active date keys (e.g. "2026-09-05").
     */
    fun getActiveDates(): List<String> {
        val storage = storageProvider()
        val raw = storage.getString(KEY_ACTIVE_DATES, "")
        if (raw.isBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    /**
     * Returns a full status report of user retention and cohort performance.
     */
    fun getRetentionStatus(nowMillis: Long = currentTimeMillisProvider()): RetentionStatus {
        val storage = storageProvider()
        val daysSinceInstall = getDaysSinceInstall(nowMillis)
        val totalActiveDays = getTotalActiveDays()
        val lastActiveDate = storage.getString(KEY_LAST_ACTIVE_DATE, "")

        val reachedCohorts = CohortInterval.entries.filter { isCohortMilestoneReached(it) }.toSet()

        return RetentionStatus(
            daysSinceInstall = daysSinceInstall,
            totalActiveDays = totalActiveDays,
            activeCohortsReached = reachedCohorts,
            isDay1Retained = isCohortActive(CohortInterval.DAY_1) || isCohortMilestoneReached(CohortInterval.DAY_1),
            isDay7Retained = isCohortActive(CohortInterval.DAY_7) || isCohortMilestoneReached(CohortInterval.DAY_7),
            isDay14Retained = isCohortActive(CohortInterval.DAY_14) || isCohortMilestoneReached(CohortInterval.DAY_14),
            isDay30Retained = isCohortActive(CohortInterval.DAY_30) || isCohortMilestoneReached(CohortInterval.DAY_30),
            isDay90Retained = isCohortActive(CohortInterval.DAY_90) || isCohortMilestoneReached(CohortInterval.DAY_90),
            lastActiveDate = lastActiveDate
        )
    }
}
