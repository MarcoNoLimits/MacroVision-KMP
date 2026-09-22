package com.fitter.shared.auth

import com.fitter.shared.telemetry.TelemetryUploader
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseAuthService(
    private val client: SupabaseClient = SupabaseClientFactory.getOrCreate()
) {
    constructor(url: String, anonKey: String) : this(SupabaseClientFactory.getOrCreate(url, anonKey))

    val authState: Flow<UserSession?> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session
            else -> null
        }
    }

    fun getUserId(): String? {
        return client.auth.currentUserOrNull()?.id
    }

    fun currentSession(): UserSession? {
        return client.auth.currentSessionOrNull()
    }

    suspend fun signInAnonymously(): String {
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
     * Returns the user_id string on success.
     * Throws on authentication failure — NEVER falls back to "anon-local-user".
     * Callers (App.kt) should catch and handle gracefully (show offline mode / retry).
     */
    suspend fun ensureSignedIn(): String {
        val existingId = getUserId()
        if (!existingId.isNullOrBlank()) {
            // Already signed in — wire telemetry provider (idempotent)
            TelemetryUploader.userIdProvider = { existingId }
            return existingId
        }

        // Attempt anonymous sign-in
        val userId = signInAnonymously()
        // Wire telemetry immediately after obtaining a valid user_id
        TelemetryUploader.userIdProvider = { userId }
        return userId
        // Note: if signInAnonymously() throws, the exception propagates to App.kt
        // which handles it gracefully (prints error, proceeds with null userId, shows offline state)
    }
}
