package com.fitter.shared.data

import com.fitter.shared.api.FoodDatabase
import com.fitter.shared.model.LoggedMeal
import com.fitter.shared.model.UserProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeKeyValueStorage : KeyValueStorage {
    val store = mutableMapOf<String, String>()

    override fun getString(key: String, defaultValue: String): String =
        store[key] ?: defaultValue

    override fun putString(key: String, value: String) {
        store[key] = value
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        store[key]?.toIntOrNull() ?: defaultValue

    override fun putInt(key: String, value: Int) {
        store[key] = value.toString()
    }

    override fun remove(key: String) {
        store.remove(key)
    }
}

class RepositoryTest {

    private lateinit var storage: FakeKeyValueStorage
    private lateinit var mealRepo: LocalMealRepository
    private lateinit var userRepo: LocalUserRepository

    @BeforeTest
    fun setUp() {
        storage = FakeKeyValueStorage()
        mealRepo = LocalMealRepository(storage)
        userRepo = LocalUserRepository(storage)
    }

    @Test
    fun testMealRepositorySaveAndFilterByDate() = runBlocking {
        val meal1 = LoggedMeal(
            id = "m1",
            name = "Chicken Salad",
            calories = 350,
            protein = 30f,
            carbs = 10f,
            fat = 8f,
            timestamp = "12:00 PM",
            date = "2026-09-08"
        )
        val meal2 = LoggedMeal(
            id = "m2",
            name = "Protein Shake",
            calories = 200,
            protein = 25f,
            carbs = 5f,
            fat = 2f,
            timestamp = "03:00 PM",
            date = "2026-09-08"
        )
        val meal3 = LoggedMeal(
            id = "m3",
            name = "Oatmeal",
            calories = 300,
            protein = 10f,
            carbs = 50f,
            fat = 5f,
            timestamp = "08:00 AM",
            date = "2026-09-09"
        )

        mealRepo.saveMeal(meal1)
        mealRepo.saveMeal(meal2)
        mealRepo.saveMeal(meal3)

        val todayMeals = mealRepo.getMealsForDate("2026-09-08")
        assertEquals(2, todayMeals.size)

        val tomorrowMeals = mealRepo.getMealsForDate("2026-09-09")
        assertEquals(1, tomorrowMeals.size)
        assertEquals("Oatmeal", tomorrowMeals.first().name)

        val allMeals = mealRepo.getAllMeals()
        assertEquals(3, allMeals.size)
    }

    @Test
    fun testMealRepositoryDeleteAndUpdate() = runBlocking {
        val meal = LoggedMeal(
            id = "m1",
            name = "Chicken Breast",
            calories = 165,
            protein = 31f,
            carbs = 0f,
            fat = 3.6f,
            timestamp = "12:00 PM",
            date = "2026-09-08"
        )
        mealRepo.saveMeal(meal)

        val updated = meal.copy(calories = 200, protein = 38f)
        mealRepo.updateMeal(updated)

        val fetched = mealRepo.getMealsForDate("2026-09-08")
        assertEquals(1, fetched.size)
        assertEquals(200, fetched.first().calories)

        mealRepo.deleteMeal("m1")
        val emptyList = mealRepo.getMealsForDate("2026-09-08")
        assertTrue(emptyList.isEmpty())
    }

    @Test
    fun testUserRepositoryProfileAndWater() = runBlocking {
        val defaultProfile = userRepo.getUserProfile()
        assertEquals(2000, defaultProfile.calGoal)

        val customProfile = defaultProfile.copy(calGoal = 2500, proteinGoal = 180)
        userRepo.saveUserProfile(customProfile)

        val saved = userRepo.getUserProfile()
        assertEquals(2500, saved.calGoal)
        assertEquals(180, saved.proteinGoal)

        userRepo.setWaterIntake("2026-09-08", 1500)
        assertEquals(1500, userRepo.getWaterIntake("2026-09-08"))
        assertEquals(0, userRepo.getWaterIntake("2026-09-09"))
    }

    @Test
    fun testFoodDatabaseIndexedSearch() {
        val exact = FoodDatabase.findClosestFood("chicken breast")
        assertNotNull(exact)
        assertEquals("Chicken Breast", exact.name)

        val prefixResults = FoodDatabase.searchFoods("chick", limit = 5)
        assertTrue(prefixResults.isNotEmpty())
        assertTrue(prefixResults.any { it.name.contains("Chicken", ignoreCase = true) || it.name.contains("Chickpeas", ignoreCase = true) })
    }
}
