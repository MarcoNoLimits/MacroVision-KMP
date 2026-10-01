package com.fitcal.app.notifications

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MealReminderManagerTest {

    private val memoryStore = mutableMapOf<String, String>()
    private var syncedMasterEnabled: Boolean? = null
    private var syncedReminders: List<MealReminder> = emptyList()

    @BeforeTest
    fun setUp() {
        memoryStore.clear()
        syncedMasterEnabled = null
        syncedReminders = emptyList()

        MealReminderManager.preferenceReader = { key, default ->
            memoryStore[key] ?: default
        }
        MealReminderManager.preferenceWriter = { key, value ->
            memoryStore[key] = value
        }
        MealReminderManager.platformSyncHook = { enabled, reminders ->
            syncedMasterEnabled = enabled
            syncedReminders = reminders
        }
    }

    @AfterTest
    fun tearDown() {
        MealReminderManager.resetToDefaults()
        memoryStore.clear()
    }

    @Test
    fun testDefaultThreeMealsLoadedInitially() {
        assertTrue(MealReminderManager.isMasterEnabled())
        val reminders = MealReminderManager.getReminders()
        assertEquals(3, reminders.size)
        assertEquals("Breakfast", reminders[0].label)
        assertEquals(8, reminders[0].hour)
        assertEquals(0, reminders[0].minute)
        assertEquals("Lunch", reminders[1].label)
        assertEquals(12, reminders[1].hour)
        assertEquals(30, reminders[1].minute)
        assertEquals("Dinner", reminders[2].label)
        assertEquals(19, reminders[2].hour)
        assertEquals(0, reminders[2].minute)
    }

    @Test
    fun testAddAndSortMealReminders() {
        val updated = MealReminderManager.addReminder("Afternoon Snack", 15, 45)
        assertEquals(4, updated.size)
        // Sorted chronologically: 08:00, 12:30, 15:45, 19:00
        assertEquals("Afternoon Snack", updated[2].label)
        assertEquals(15, updated[2].hour)
        assertEquals(45, updated[2].minute)
        assertEquals(true, syncedMasterEnabled)
        assertEquals(4, syncedReminders.size)
    }

    @Test
    fun testUpdateAndRemoveMealReminders() {
        val initial = MealReminderManager.getReminders()
        val lunchId = initial[1].id

        // Change Lunch to 13:15
        val afterEdit = MealReminderManager.updateReminder(
            id = lunchId,
            label = "Late Lunch",
            hour = 13,
            minute = 15,
            enabled = true
        )
        assertEquals("Late Lunch", afterEdit[1].label)
        assertEquals(13, afterEdit[1].hour)
        assertEquals(15, afterEdit[1].minute)

        // Remove Breakfast so user only has 2 reminders
        val afterRemove = MealReminderManager.removeReminder(initial[0].id)
        assertEquals(2, afterRemove.size)
        assertEquals("Late Lunch", afterRemove[0].label)
        assertEquals("Dinner", afterRemove[1].label)
    }

    @Test
    fun testMasterToggleAndFormatting() {
        MealReminderManager.setMasterEnabled(false)
        assertFalse(MealReminderManager.isMasterEnabled())
        assertEquals(false, syncedMasterEnabled)

        assertEquals("08:00 AM", MealReminderManager.formatTime12Hour(8, 0))
        assertEquals("12:30 PM", MealReminderManager.formatTime12Hour(12, 30))
        assertEquals("07:00 PM", MealReminderManager.formatTime12Hour(19, 0))
        assertEquals("12:05 AM", MealReminderManager.formatTime12Hour(0, 5))
    }
}
