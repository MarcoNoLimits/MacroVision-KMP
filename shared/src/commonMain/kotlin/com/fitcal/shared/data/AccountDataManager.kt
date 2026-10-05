package com.fitcal.shared.data

import com.fitcal.shared.sync.OutboxItem
import com.fitcal.shared.sync.SyncEngine

/**
 * Keeps on-device data consistent when the signed-in account changes.
 *
 * Upgrading a guest in place keeps the same user ID, so nothing needs to happen. These
 * cover the two cases where the user ID changes: switching into an existing account, and
 * signing out of one.
 */
class AccountDataManager(
    private val meals: LocalMealRepository,
    private val user: LocalUserRepository,
    private val syncEngine: SyncEngine,
    private val newId: () -> String,
) {
    /**
     * Moves this device's guest meals into the account that was just signed into.
     * Meal IDs are a global key and the guest's rows on the server still belong to the
     * old anonymous user, so the meals are re-keyed before upload. The account's own
     * profile and water history win; they are pulled from the server afterwards.
     */
    suspend fun mergeGuestMealsIntoCurrentAccount() {
        syncEngine.clearOutbox()
        val rekeyed = meals.reassignIds(newId)
        syncEngine.enqueueAll(rekeyed.map { OutboxItem.UpsertMeal(it) })
    }

    /** Tries to upload pending changes and returns how many are still unsynced. */
    suspend fun syncAndCountPending(): Int {
        syncEngine.flushOutbox()
        return syncEngine.pendingCount()
    }

    /** Removes this account's data from the device. Call [syncAndCountPending] first. */
    suspend fun clearLocalData() {
        meals.clearAll()
        user.clearAll()
        syncEngine.clearOutbox()
    }
}
