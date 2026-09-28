package com.fitter.shared.auth

import com.fitter.shared.telemetry.TelemetryUploader
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class SupabaseAuthService(
    private val client: SupabaseClient = SupabaseClientFactory.getOrCreate()
) {
    constructor(url: String, anonKey: String) : this(SupabaseClientFactory.getOrCreate(url, anonKey))

    open val authState: Flow<UserSession?> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session
            else -> null
        }
    }

    open fun getUserId(): String? {
        return client.auth.currentUserOrNull()?.id
    }

    open fun getCurrentUserEmail(): String? {
        return client.auth.currentUserOrNull()?.email
    }

    open fun isGuest(): Boolean {
        val user = client.auth.currentUserOrNull() ?: return true
        return user.email.isNullOrBlank()
    }

    open fun currentSession(): UserSession? {
        return client.auth.currentSessionOrNull()
    }

    open suspend fun signInWithEmail(email: String, pass: String): String {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = pass
        }
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Sign-in succeeded but no user ID returned")
        TelemetryUploader.userIdProvider = { userId }
        return userId
    }

    open suspend fun signUpWithEmail(email: String, pass: String): String {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = pass
        }
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Sign-up succeeded but no user ID returned")
        TelemetryUploader.userIdProvider = { userId }
        return userId
    }

    open suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (_: Throwable) {
            // Ignore signOut failure if network unavailable
        }
        // Immediately restore anonymous session for guest mode when online
        try {
            val userId = signInAnonymously()
            TelemetryUploader.userIdProvider = { userId }
        } catch (_: Throwable) {
            // Offline guest fallback
        }
    }

    open suspend fun signInAnonymously(): String {
        client.auth.signInAnonymously()
        return client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Anonymous sign-in succeeded but no user ID returned")
    }


    /**
     * Ensures a valid Supabase JWT exists before any server call.
     *
     * Phase 6 addition: wires TelemetryUploader.userIdProvider on success so telemetry
     * rows always carry the correct auth.uid().
     *
     * Phase 10 addition: supports bounded backoff retry.
     *
     * Returns the user_id string on success.
     * Throws on authentication failure — NEVER falls back to "anon-local-user".
     *
     * @param maxAttempts Number of attempts before throwing the final error (default 1).
     * @param backoffsMs Millisecond delays between consecutive attempts.
     * @param onAttemptFailed Optional listener invoked when an individual attempt fails.
     */
    suspend fun ensureSignedIn(
        maxAttempts: Int = 1,
        backoffsMs: List<Long> = emptyList(),
        onAttemptFailed: ((attempt: Int, error: Throwable) -> Unit)? = null
    ): String {
        val existingId = getUserId()
        if (!existingId.isNullOrBlank()) {
            // Already signed in — wire telemetry provider (idempotent)
            TelemetryUploader.userIdProvider = { existingId }
            return existingId
        }

        var lastException: Throwable? = null
        for (attempt in 1..maxAttempts.coerceAtLeast(1)) {
            try {
                val userId = signInAnonymously()
                TelemetryUploader.userIdProvider = { userId }
                return userId
            } catch (e: Throwable) {
                lastException = e
                onAttemptFailed?.invoke(attempt, e)
                if (attempt < maxAttempts) {
                    val delayMs = backoffsMs.getOrNull(attempt - 1) ?: 1000L
                    if (delayMs > 0) {
                        kotlinx.coroutines.delay(delayMs)
                    }
                }
            }
        }

        throw lastException ?: IllegalStateException("ensureSignedIn failed after $maxAttempts attempts")
    }
}

