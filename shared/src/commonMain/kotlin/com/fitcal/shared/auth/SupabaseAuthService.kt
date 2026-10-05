package com.fitcal.shared.auth

import com.fitcal.shared.telemetry.TelemetryUploader
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.IDTokenProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Result of starting an email sign-up from a guest session. */
sealed interface EmailSignUpStart {
    /** A 6-digit code was emailed; finish with [SupabaseAuthService.completeEmailSignUp]. */
    data object CodeSent : EmailSignUpStart

    /** The project auto-confirms emails, so the account is already complete. */
    data object Completed : EmailSignUpStart

    /** The email belongs to an existing account; the user should sign in instead. */
    data object EmailAlreadyRegistered : EmailSignUpStart
}

/** How a Google/Apple ID token was applied. */
enum class IdTokenOutcome {
    /** Attached to the current guest user: same user ID, all data kept. */
    LINKED,

    /** The identity already had an account; the session switched to it. */
    SIGNED_IN_TO_EXISTING,
}

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

    open fun isAnonymousUser(): Boolean {
        return client.auth.currentUserOrNull()?.isAnonymous == true
    }

    /** A real account the user can sign back into: a confirmed email or a linked Google/Apple identity. */
    open fun isPermanentUser(): Boolean {
        val user = client.auth.currentUserOrNull() ?: return false
        if (user.isAnonymous == true) return false
        if (user.emailConfirmedAt != null) return true
        return user.identities.orEmpty().any { it.provider != "email" }
    }

    open fun currentSession(): UserSession? {
        return client.auth.currentSessionOrNull()
    }

    open suspend fun signInWithEmail(email: String, pass: String): String {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = pass
        }
        return onSignedIn()
    }

    /**
     * Upgrades the current guest to an email account in place, keeping the same user ID so
     * every meal, quota counter and setting stays attached. Supabase only lets a password be
     * set once the email is verified, so this normally sends a code first.
     */
    open suspend fun startEmailSignUp(email: String, password: String): EmailSignUpStart {
        ensureSignedIn()
        val updated = try {
            client.auth.updateUser { this.email = email }
        } catch (e: AuthRestException) {
            if (e.errorCode == AuthErrorCode.EmailExists || e.errorCode == AuthErrorCode.UserAlreadyExists) {
                return EmailSignUpStart.EmailAlreadyRegistered
            }
            throw e
        }
        if (updated.email.equals(email, ignoreCase = true) && updated.emailConfirmedAt != null) {
            client.auth.updateUser { this.password = password }
            onSignedIn()
            return EmailSignUpStart.Completed
        }
        return EmailSignUpStart.CodeSent
    }

    open suspend fun completeEmailSignUp(email: String, code: String, password: String): String {
        client.auth.verifyEmailOtp(type = OtpType.Email.EMAIL_CHANGE, email = email, token = code.trim())
        client.auth.updateUser { this.password = password }
        return onSignedIn()
    }

    open suspend fun requestPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email)
    }

    /** Verifies the recovery code (which signs the user in) and sets the new password. */
    open suspend fun completePasswordReset(email: String, code: String, newPassword: String): String {
        client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code.trim())
        client.auth.updateUser { this.password = newPassword }
        return onSignedIn()
    }

    /**
     * Applies a native Google/Apple ID token. A guest gets the identity linked in place; if
     * that identity already owns an account, the session switches to that account instead.
     */
    open suspend fun signInWithIdToken(
        provider: IDTokenProvider,
        idToken: String,
        nonce: String?,
    ): IdTokenOutcome {
        if (isAnonymousUser()) {
            try {
                client.auth.linkIdentityWithIdToken(provider, idToken) { this.nonce = nonce }
                onSignedIn()
                return IdTokenOutcome.LINKED
            } catch (e: AuthRestException) {
                if (e.errorCode != AuthErrorCode.IdentityAlreadyExists) throw e
            }
        }
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = provider
            this.nonce = nonce
        }
        onSignedIn()
        return IdTokenOutcome.SIGNED_IN_TO_EXISTING
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

    private fun onSignedIn(): String {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Sign-in succeeded but no user ID returned")
        TelemetryUploader.userIdProvider = { userId }
        return userId
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
