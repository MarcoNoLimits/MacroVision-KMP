package com.fitcal.shared.auth

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/** Platform storage that encrypts values at rest (Android Keystore, iOS Keychain). */
interface SecureStringStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/**
 * Persists the Supabase session (access + refresh token) in [store] instead of plain
 * preferences. A session left by the old plaintext [legacy] manager is moved over once.
 */
class SecureSessionManager(
    private val store: SecureStringStore,
    private val legacy: SessionManager? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        store.put(KEY_SESSION, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession = findSession() ?: throw NoSessionFoundException()

    private suspend fun findSession(): UserSession? {
        val raw = store.get(KEY_SESSION)
        if (raw != null) {
            return runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull()
        }
        val previous = legacy ?: return null
        val migrated = previous.loadSessionOrNull() ?: return null
        saveSession(migrated)
        runCatching { previous.deleteSession() }
        return migrated
    }

    override suspend fun deleteSession() {
        store.remove(KEY_SESSION)
    }

    companion object {
        const val KEY_SESSION = "fitcal_auth_session"
    }
}
