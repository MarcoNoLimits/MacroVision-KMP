package com.fitter.shared.data

import com.fitter.shared.model.LoggedMeal
import kotlinx.coroutines.flow.Flow

interface MealRepository {
    suspend fun getMealsForDate(date: String): List<LoggedMeal>
    suspend fun getAllMeals(): List<LoggedMeal>
    suspend fun saveMeal(meal: LoggedMeal)
    suspend fun deleteMeal(mealId: String)
    suspend fun updateMeal(meal: LoggedMeal)
    fun observeMealsForDate(date: String): Flow<List<LoggedMeal>>
}
