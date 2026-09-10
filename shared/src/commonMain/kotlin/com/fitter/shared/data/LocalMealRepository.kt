package com.fitter.shared.data

import com.fitter.shared.model.LoggedMeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class LocalMealRepository(
    private val storage: KeyValueStorage,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }
) : MealRepository {

    private val mutex = Mutex()
    private val _mealsFlow = MutableStateFlow<List<LoggedMeal>>(emptyList())
    private var isInitialized = false

    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            mutex.withLock {
                if (!isInitialized) {
                    val loaded = withContext(Dispatchers.Default) {
                        try {
                            val raw = storage.getString(KEY_LOGGED_MEALS, "[]")
                            if (raw.isBlank() || raw == "[]") {
                                emptyList()
                            } else {
                                json.decodeFromString(ListSerializer(LoggedMeal.serializer()), raw)
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    _mealsFlow.value = loaded
                    isInitialized = true
                }
            }
        }
    }

    private suspend fun persistLocked(meals: List<LoggedMeal>) {
        _mealsFlow.value = meals
        withContext(Dispatchers.Default) {
            try {
                val serialized = json.encodeToString(ListSerializer(LoggedMeal.serializer()), meals)
                storage.putString(KEY_LOGGED_MEALS, serialized)
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getMealsForDate(date: String): List<LoggedMeal> {
        ensureInitialized()
        return mutex.withLock {
            _mealsFlow.value.filter { it.date == date }
        }
    }

    override suspend fun getAllMeals(): List<LoggedMeal> {
        ensureInitialized()
        return mutex.withLock {
            _mealsFlow.value.toList()
        }
    }

    override suspend fun saveMeal(meal: LoggedMeal) {
        ensureInitialized()
        mutex.withLock {
            val updated = _mealsFlow.value + meal
            persistLocked(updated)
        }
    }

    override suspend fun deleteMeal(mealId: String) {
        ensureInitialized()
        mutex.withLock {
            val updated = _mealsFlow.value.filterNot { it.id == mealId }
            persistLocked(updated)
        }
    }

    override suspend fun updateMeal(meal: LoggedMeal) {
        ensureInitialized()
        mutex.withLock {
            val updated = _mealsFlow.value.map { if (it.id == meal.id) meal else it }
            persistLocked(updated)
        }
    }

    override fun observeMealsForDate(date: String): Flow<List<LoggedMeal>> {
        return _mealsFlow.map { list -> list.filter { it.date == date } }
    }

    companion object {
        const val KEY_LOGGED_MEALS = "logged_meals"
    }
}
