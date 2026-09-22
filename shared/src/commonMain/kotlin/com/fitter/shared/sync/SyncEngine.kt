package com.fitter.shared.sync

import com.fitter.shared.auth.SupabaseClientFactory
import com.fitter.shared.data.KeyValueStorage
import com.fitter.shared.model.LoggedMeal
import com.fitter.shared.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
sealed class OutboxItem {
    @Serializable
    data class UpsertMeal(val meal: LoggedMeal) : OutboxItem()

    @Serializable
    data class DeleteMeal(val mealId: String) : OutboxItem()

    @Serializable
    data class UpsertProfile(val profile: UserProfile) : OutboxItem()

    @Serializable
    data class UpsertWater(val date: String, val amountMl: Int) : OutboxItem()
}

/**
 * Supabase row for the meals table.
 * [eaten_at] is the canonical timestamptz; [timestamp] and [date] are derived client-side.
 */
@Serializable
data class SupabaseMealRow(
    val id: String,
    val name: String,
    val calories: Int,
    val protein_g: Float,
    val carbs_g: Float,
    val fat_g: Float,
    val eaten_at: String? = null,     // timestamptz ISO string from server
    val photo_url: String? = null,
    val deleted_at: String? = null
)

/**
 * Supabase row for the profiles table.
 * [user_id] is included so PostgREST can match the RLS with check (user_id = auth.uid()).
 */
@Serializable
data class SupabaseProfileRow(
    val user_id: String? = null,      // included for RLS; set by PostgREST default if null
    val weight: Float? = null,
    val height: Float? = null,
    val cal_goal: Int? = null,
    val protein_goal: Int? = null,
    val carbs_goal: Int? = null,
    val fat_goal: Int? = null,
    val age: Int? = null,
    val gender: String? = null,
    val goal_type: String? = null,
    val default_plate_size: Float? = null
)

/**
 * Supabase row for the water_intake table.
 * [user_id] is included so PostgREST can satisfy RLS with check (user_id = auth.uid()).
 */
@Serializable
data class SupabaseWaterRow(
    val user_id: String? = null,      // included for RLS; set by PostgREST default if null
    val day: String,
    val amount_ml: Int
)

class SyncEngine(
    private val storage: KeyValueStorage,
    private val client: SupabaseClient = SupabaseClientFactory.getOrCreate(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun enqueue(item: OutboxItem) {
        mutex.withLock {
            val current = loadOutboxLocked()
            val updated = current + item
            saveOutboxLocked(updated)
        }
        triggerFlush()
    }

    fun triggerFlush() {
        scope.launch {
            flushOutbox()
        }
    }

    suspend fun flushOutbox() {
        mutex.withLock {
            val items = loadOutboxLocked()
            if (items.isEmpty()) return@withLock

            val remaining = mutableListOf<OutboxItem>()
            for (item in items) {
                val success = processItem(item)
                if (!success) {
                    remaining.add(item)
                }
            }
            saveOutboxLocked(remaining)
        }
    }

    private suspend fun processItem(item: OutboxItem): Boolean {
        return try {
            when (item) {
                is OutboxItem.UpsertMeal -> {
                    // Build ISO 8601 eaten_at from meal.date + meal.timestamp
                    // e.g. "2026-09-05" + "02:30 PM" → approximated to noon if unparseable
                    val eatenAt = buildEatenAt(item.meal.date, item.meal.timestamp)
                    val row = SupabaseMealRow(
                        id = item.meal.id,
                        name = item.meal.name,
                        calories = item.meal.calories,
                        protein_g = item.meal.protein,
                        carbs_g = item.meal.carbs,
                        fat_g = item.meal.fat,
                        eaten_at = eatenAt
                    )
                    client.from("meals").upsert(row)
                    true
                }
                is OutboxItem.DeleteMeal -> {
                    // Tombstone soft-delete: set deleted_at = now()
                    client.from("meals").update(
                        buildJsonObject {
                            put("deleted_at", "now()")
                        }
                    ) {
                        filter {
                            eq("id", item.mealId)
                        }
                    }
                    true
                }
                is OutboxItem.UpsertProfile -> {
                    val row = SupabaseProfileRow(
                        // user_id left null — PostgREST applies auth.uid() default
                        weight = item.profile.weight,
                        height = item.profile.height,
                        cal_goal = item.profile.calGoal,
                        protein_goal = item.profile.proteinGoal,
                        carbs_goal = item.profile.carbsGoal,
                        fat_goal = item.profile.fatGoal,
                        age = item.profile.age,
                        gender = item.profile.gender,
                        goal_type = item.profile.goalType,
                        default_plate_size = item.profile.defaultPlateSize
                    )
                    client.from("profiles").upsert(row)
                    true
                }
                is OutboxItem.UpsertWater -> {
                    val row = SupabaseWaterRow(
                        // user_id left null — PostgREST applies auth.uid() default from migration 0003
                        day = item.date,
                        amount_ml = item.amountMl
                    )
                    client.from("water_intake").upsert(row)
                    true
                }
            }
        } catch (e: Exception) {
            println("SyncEngine failed to process item: ${e.message}")
            false
        }
    }

    private fun loadOutboxLocked(): List<OutboxItem> {
        return try {
            val raw = storage.getString(KEY_OUTBOX, "[]")
            if (raw.isBlank() || raw == "[]") emptyList()
            else json.decodeFromString(ListSerializer(OutboxItem.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveOutboxLocked(items: List<OutboxItem>) {
        try {
            val raw = json.encodeToString(ListSerializer(OutboxItem.serializer()), items)
            storage.putString(KEY_OUTBOX, raw)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val KEY_OUTBOX = "fitter_sync_outbox"

        /**
         * Build an ISO 8601 datetime string from a date key and display timestamp.
         * date: "2026-09-05", timestamp: "02:30 PM" → "2026-09-05T14:30:00Z"
         * Falls back to noon if timestamp is unparseable.
         */
        fun buildEatenAt(date: String, timestamp: String): String {
            return try {
                // Parse "hh:mm a" format
                val parts = timestamp.trim().split(" ")
                val timeParts = parts[0].split(":")
                var hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                val isPm = parts.getOrNull(1)?.uppercase() == "PM"
                if (isPm && hour != 12) hour += 12
                if (!isPm && hour == 12) hour = 0
                "${date}T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:00Z"
            } catch (e: Exception) {
                // Fallback to noon
                "${date}T12:00:00Z"
            }
        }
    }
}
