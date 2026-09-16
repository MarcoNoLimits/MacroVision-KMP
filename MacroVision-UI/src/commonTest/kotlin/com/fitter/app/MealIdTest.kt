package com.fitter.app

import com.fitter.app.ui.components.newMealId
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * F0.1 acceptance test: proves two meals created "at the same time" get distinct ids.
 *
 * Since newMealId() is purely random (UUID v4 via kotlin.random.Random) and
 * not derived from system time, even 10,000 same-tick calls must all be distinct.
 */
class MealIdTest {

    @Test
    fun testNewMealId_twoCallsProduceDistinctIds() {
        val id1 = newMealId()
        val id2 = newMealId()
        assertNotEquals(id1, id2, "Two meal IDs generated in the same call must be distinct")
    }

    @Test
    fun testNewMealId_thousandCallsAllDistinct() {
        val ids = (1..1000).map { newMealId() }.toSet()
        assertTrue(
            ids.size == 1000,
            "1000 generated meal IDs must all be unique; got ${ids.size} distinct values"
        )
    }

    @Test
    fun testNewMealId_formatIsUuidShaped() {
        val id = newMealId()
        // UUID v4: 8-4-4-4-12 hex groups separated by dashes, 36 chars total
        val uuidRegex = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE
        )
        assertTrue(uuidRegex.matches(id), "Generated ID '$id' does not match UUID v4 format")
    }

    @Test
    fun testNewMealId_isStableString() {
        val id = newMealId()
        assertTrue(id.isNotBlank(), "Generated meal ID must not be blank")
        assertTrue(id.length == 36, "UUID-shaped meal ID must be 36 characters, got ${id.length}")
    }
}
