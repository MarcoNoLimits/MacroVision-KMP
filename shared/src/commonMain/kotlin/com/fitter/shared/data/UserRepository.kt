package com.fitter.shared.data

import com.fitter.shared.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUserProfile(): UserProfile
    suspend fun saveUserProfile(profile: UserProfile)
    suspend fun getWaterIntake(date: String): Int
    suspend fun setWaterIntake(date: String, amountMl: Int)
    fun observeWaterIntake(date: String): Flow<Int>
}
