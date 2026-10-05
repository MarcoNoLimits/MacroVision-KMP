package com.fitcal.shared.auth

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureSessionManagerTest {

    private class MemoryStore : SecureStringStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private class FakeLegacy(var session: UserSession?) : SessionManager {
        var deleted = false
        override suspend fun saveSession(session: UserSession) { this.session = session }
        override suspend fun loadSession(): UserSession = session ?: throw NoSessionFoundException()
        override suspend fun deleteSession() { deleted = true; session = null }
    }

    private fun session(token: String) = UserSession(
        accessToken = token,
        refreshToken = "refresh-$token",
        expiresIn = 3600,
        tokenType = "bearer",
        user = null,
    )

    @Test
    fun savedSessionRoundTrips() = runBlocking {
        val manager = SecureSessionManager(MemoryStore())
        manager.saveSession(session("abc"))
        val loaded = manager.loadSession()
        assertEquals("abc", loaded.accessToken)
        assertEquals("refresh-abc", loaded.refreshToken)
    }

    @Test
    fun missingSessionThrowsNoSessionFound() = runBlocking {
        val manager = SecureSessionManager(MemoryStore())
        assertFailsWith<NoSessionFoundException> { manager.loadSession() }
        assertNull(manager.loadSessionOrNull())
    }

    @Test
    fun plaintextSessionIsMovedIntoSecureStoreOnce() = runBlocking {
        val store = MemoryStore()
        val legacy = FakeLegacy(session("legacy"))
        val manager = SecureSessionManager(store, legacy)

        assertEquals("legacy", manager.loadSession().accessToken)
        assertTrue(legacy.deleted, "plaintext copy must be removed after migration")
        assertTrue(store.values.containsKey(SecureSessionManager.KEY_SESSION))
        assertEquals("legacy", manager.loadSession().accessToken)
    }

    @Test
    fun deleteRemovesSession() = runBlocking {
        val manager = SecureSessionManager(MemoryStore())
        manager.saveSession(session("abc"))
        manager.deleteSession()
        assertNull(manager.loadSessionOrNull())
    }
}
