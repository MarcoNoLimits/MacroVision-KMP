package com.fitter.shared.data

import com.fitter.shared.auth.SupabaseClientFactory
import com.fitter.shared.model.UserProfile
import com.fitter.shared.sync.OutboxItem
import com.fitter.shared.sync.SupabaseProfileRow
import com.fitter.shared.sync.SupabaseWaterRow
import com.fitter.shared.sync.SyncEngine
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow

class SupabaseUserRepository(
    private val localRepo: LocalUserRepository,
    private val syncEngine: SyncEngine,
    private val client: SupabaseClient = SupabaseClientFactory.getOrCreate()
) : UserRepository {

    override suspend fun getUserProfile(): UserProfile {
        return localRepo.getUserProfile()
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        localRepo.saveUserProfile(profile)
        syncEngine.enqueue(OutboxItem.UpsertProfile(profile))
    }

    override suspend fun getWaterIntake(date: String): Int {
        return localRepo.getWaterIntake(date)
    }

    override suspend fun setWaterIntake(date: String, amountMl: Int) {
        localRepo.setWaterIntake(date, amountMl)
        syncEngine.enqueue(OutboxItem.UpsertWater(date, amountMl))
    }

    override fun observeWaterIntake(date: String): Flow<Int> {
        return localRepo.observeWaterIntake(date)
    }

    suspend fun pullRemote(dateKey: String) {
        try {
            val profiles = client.from("profiles")
                .select()
                .decodeList<SupabaseProfileRow>()

            profiles.firstOrNull()?.let { row ->
                val current = localRepo.getUserProfile()
                val updated = current.copy(
                    weight = row.weight ?: current.weight,
                    height = row.height ?: current.height,
                    calGoal = row.cal_goal ?: current.calGoal,
                    proteinGoal = row.protein_goal ?: current.proteinGoal,
                    carbsGoal = row.carbs_goal ?: current.carbsGoal,
                    fatGoal = row.fat_goal ?: current.fatGoal,
                    age = row.age ?: current.age,
                    gender = row.gender ?: current.gender,
                    goalType = row.goal_type ?: current.goalType,
                    defaultPlateSize = row.default_plate_size ?: current.defaultPlateSize
                )
                localRepo.saveUserProfile(updated)
            }

            val waters = client.from("water_intake")
                .select {
                    filter {
                        eq("day", dateKey)
                    }
                }
                .decodeList<SupabaseWaterRow>()

            waters.firstOrNull()?.let { row ->
                localRepo.setWaterIntake(dateKey, row.amount_ml)
            }
        } catch (e: Exception) {
            println("SupabaseUserRepository pullRemote failed: ${e.message}")
        }
    }
}
