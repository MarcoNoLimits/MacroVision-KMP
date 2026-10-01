package com.fitcal.shared.data

import com.fitcal.shared.auth.SupabaseClientFactory
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.sync.OutboxItem
import com.fitcal.shared.sync.SupabaseMealRow
import com.fitcal.shared.sync.SyncEngine
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow

class SupabaseMealRepository(
    private val localRepo: LocalMealRepository,
    private val syncEngine: SyncEngine,
    private val client: SupabaseClient = SupabaseClientFactory.getOrCreate()
) : MealRepository {

    override suspend fun getMealsForDate(date: String): List<LoggedMeal> {
        return localRepo.getMealsForDate(date)
    }

    override suspend fun getAllMeals(): List<LoggedMeal> {
        return localRepo.getAllMeals()
    }

    override suspend fun saveMeal(meal: LoggedMeal) {
        localRepo.saveMeal(meal)
        syncEngine.enqueue(OutboxItem.UpsertMeal(meal))
    }

    override suspend fun deleteMeal(mealId: String) {
        localRepo.deleteMeal(mealId)
        syncEngine.enqueue(OutboxItem.DeleteMeal(mealId))
    }

    override suspend fun updateMeal(meal: LoggedMeal) {
        localRepo.updateMeal(meal)
        syncEngine.enqueue(OutboxItem.UpsertMeal(meal))
    }

    override fun observeMealsForDate(date: String): Flow<List<LoggedMeal>> {
        return localRepo.observeMealsForDate(date)
    }

    /**
     * Phase 5 fix: date-scoped bounded pullRemote.
     *
     * Queries only rows where [dateKey] falls within the day boundaries (UTC):
     *   eaten_at >= <dateKey>T00:00:00Z AND eaten_at < <nextDay>T00:00:00Z
     *
     * Maps [SupabaseMealRow.eaten_at] to the local [LoggedMeal] fields:
     *   - [LoggedMeal.date]      ← date part of eaten_at (YYYY-MM-DD)
     *   - [LoggedMeal.timestamp] ← time part of eaten_at (hh:mm a display string)
     *
     * Bounded: limit(500) to prevent full-table scans.
     */
    suspend fun pullRemote(dateKey: String) {
        try {
            val dayStart = "${dateKey}T00:00:00Z"
            val dayEnd = nextDay(dateKey)?.let { "${it}T00:00:00Z" }

            val rows = client.from("meals")
                .select {
                    filter {
                        exact("deleted_at", null)
                        gte("eaten_at", dayStart)
                        if (dayEnd != null) {
                            lt("eaten_at", dayEnd)
                        }
                    }
                    limit(500)
                    order("eaten_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<SupabaseMealRow>()

            for (row in rows) {
                val (derivedDate, derivedTime) = parseEatenAt(row.eaten_at, dateKey)
                val meal = LoggedMeal(
                    id = row.id,
                    name = row.name,
                    calories = row.calories,
                    protein = row.protein_g,
                    carbs = row.carbs_g,
                    fat = row.fat_g,
                    timestamp = derivedTime,
                    date = derivedDate
                )
                localRepo.saveMeal(meal)
            }
        } catch (e: Exception) {
            println("SupabaseMealRepository pullRemote failed: ${e.message}")
        }
    }

    companion object {
        /**
         * Parse an ISO 8601 string into (date, displayTime).
         * "2026-09-05T14:30:00Z" → ("2026-09-05", "02:30 PM")
         * Falls back to (dateKey, "12:00 PM") if unparseable.
         */
        fun parseEatenAt(eatenAt: String?, fallbackDate: String): Pair<String, String> {
            if (eatenAt.isNullOrBlank()) return Pair(fallbackDate, "12:00 PM")
            return try {
                val datePart = eatenAt.substring(0, 10)   // "YYYY-MM-DD"
                val timePart = eatenAt.substring(11, 16)  // "HH:MM"
                val hour = timePart.substring(0, 2).toInt()
                val minute = timePart.substring(3, 5).toInt()
                val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                val amPm = if (hour < 12) "AM" else "PM"
                val displayTime = "${displayHour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPm"
                Pair(datePart, displayTime)
            } catch (e: Exception) {
                Pair(fallbackDate, "12:00 PM")
            }
        }

        /**
         * Returns the next calendar day as a "YYYY-MM-DD" string.
         * "2026-09-05" → "2026-09-06"
         * Returns null if dateKey is unparseable.
         */
        fun nextDay(dateKey: String): String? {
            return try {
                val year = dateKey.substring(0, 4).toInt()
                val month = dateKey.substring(5, 7).toInt()
                val day = dateKey.substring(8, 10).toInt()
                // Simple next-day logic (handles month/year rollover)
                val daysInMonth = daysInMonth(year, month)
                if (day < daysInMonth) {
                    "${year}-${month.toString().padStart(2, '0')}-${(day + 1).toString().padStart(2, '0')}"
                } else if (month < 12) {
                    "${year}-${(month + 1).toString().padStart(2, '0')}-01"
                } else {
                    "${year + 1}-01-01"
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun daysInMonth(year: Int, month: Int): Int {
            return when (month) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                else -> 30
            }
        }
    }
}
