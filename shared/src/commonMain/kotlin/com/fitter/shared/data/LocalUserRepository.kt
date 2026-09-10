package com.fitter.shared.data

import com.fitter.shared.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class LocalUserRepository(
    private val storage: KeyValueStorage,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }
) : UserRepository {

    private val mutex = Mutex()
    private val defaultProfile = UserProfile(
        weight = 70.0f,
        height = 175.0f,
        calGoal = 2000,
        proteinGoal = 150,
        carbsGoal = 200,
        fatGoal = 65,
        age = 25,
        gender = "Male",
        goalType = "Maintain",
        defaultPlateSize = 9.0f
    )

    private val _profileFlow = MutableStateFlow<UserProfile?>(null)
    private val _waterIntakeMap = MutableStateFlow<Map<String, Int>>(emptyMap())

    override suspend fun getUserProfile(): UserProfile {
        return mutex.withLock {
            _profileFlow.value ?: loadProfileFromStorageLocked()
        }
    }

    private suspend fun loadProfileFromStorageLocked(): UserProfile {
        val loaded = withContext(Dispatchers.Default) {
            try {
                val raw = storage.getString(KEY_USER_PROFILE, "")
                if (raw.isNotBlank()) {
                    json.decodeFromString<UserProfile>(raw)
                } else {
                    defaultProfile
                }
            } catch (e: Exception) {
                defaultProfile
            }
        }
        _profileFlow.value = loaded
        return loaded
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        mutex.withLock {
            _profileFlow.value = profile
            withContext(Dispatchers.Default) {
                try {
                    val raw = json.encodeToString(UserProfile.serializer(), profile)
                    storage.putString(KEY_USER_PROFILE, raw)
                } catch (_: Exception) {
                }
            }
        }
    }

    override suspend fun getWaterIntake(date: String): Int {
        return mutex.withLock {
            val cached = _waterIntakeMap.value[date]
            if (cached != null) {
                cached
            } else {
                val stored = withContext(Dispatchers.Default) {
                    storage.getString(waterKey(date), "0").toIntOrNull() ?: 0
                }
                _waterIntakeMap.value = _waterIntakeMap.value + (date to stored)
                stored
            }
        }
    }

    override suspend fun setWaterIntake(date: String, amountMl: Int) {
        mutex.withLock {
            _waterIntakeMap.value = _waterIntakeMap.value + (date to amountMl)
            withContext(Dispatchers.Default) {
                storage.putString(waterKey(date), amountMl.toString())
            }
        }
    }

    override fun observeWaterIntake(date: String): Flow<Int> {
        return _waterIntakeMap.map { map -> map[date] ?: (storage.getString(waterKey(date), "0").toIntOrNull() ?: 0) }
    }

    private fun waterKey(date: String) = "water_intake_$date"

    companion object {
        const val KEY_USER_PROFILE = "user_profile"
    }
}
