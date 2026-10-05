package com.fitcal.shared.data

import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.UserProfile
import com.fitcal.shared.sync.OutboxItem
import com.fitcal.shared.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountDataManagerTest {

    private lateinit var storage: FakeKeyValueStorage
    private lateinit var meals: LocalMealRepository
    private lateinit var user: LocalUserRepository
    private lateinit var sync: SyncEngine
    private lateinit var manager: AccountDataManager
    private var nextId = 0

    @BeforeTest
    fun setUp() {
        storage = FakeKeyValueStorage()
        meals = LocalMealRepository(storage)
        user = LocalUserRepository(storage)
        sync = SyncEngine(storage)
        nextId = 0
        manager = AccountDataManager(meals, user, sync) { "new-${nextId++}" }
    }

    private fun meal(id: String) = LoggedMeal(
        id = id, name = "Meal $id", calories = 500, protein = 30f, carbs = 50f, fat = 15f,
        timestamp = "12:00 PM", date = "2026-10-06",
    )

    @Test
    fun mergeReKeysGuestMealsAndQueuesOnlyThem() = runBlocking {
        meals.saveMeal(meal("guest-1"))
        meals.saveMeal(meal("guest-2"))
        sync.enqueue(OutboxItem.DeleteMeal("stale-guest-item"))

        manager.mergeGuestMealsIntoCurrentAccount()

        val ids = meals.getAllMeals().map { it.id }
        assertEquals(listOf("new-0", "new-1"), ids)
        assertEquals(2, sync.pendingCount(), "outbox holds exactly the re-keyed meals")
    }

    @Test
    fun clearLocalDataRemovesMealsProfileWaterAndOutbox() = runBlocking {
        meals.saveMeal(meal("m1"))
        user.saveUserProfile(UserProfile(weight = 80f, height = 180f, calGoal = 2200, proteinGoal = 160, carbsGoal = 220, fatGoal = 70))
        user.setWaterIntake("2026-10-05", 1500)
        user.setWaterIntake("2026-10-06", 750)
        sync.enqueue(OutboxItem.UpsertMeal(meal("m1")))

        manager.clearLocalData()

        assertTrue(meals.getAllMeals().isEmpty())
        assertEquals(0, sync.pendingCount())
        assertEquals(0, LocalUserRepository(storage).getWaterIntake("2026-10-05"))
        assertTrue(storage.store.keys.none { it.startsWith("water_intake") || it == LocalUserRepository.KEY_USER_PROFILE })
    }

    @Test
    fun waterDatesAreIndexedOnce() = runBlocking {
        user.setWaterIntake("2026-10-06", 250)
        user.setWaterIntake("2026-10-06", 500)
        assertEquals("2026-10-06", storage.store[LocalUserRepository.KEY_WATER_DATES])
    }
}
