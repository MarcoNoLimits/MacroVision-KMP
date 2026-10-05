package com.fitcal.shared.auth

import io.github.jan.supabase.auth.exception.AuthErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthMessagesTest {

    @Test
    fun unknownEmailAndWrongPasswordReadTheSame() {
        assertEquals(AuthMessages.forCode(AuthErrorCode.InvalidCredentials), AuthMessages.forCode(AuthErrorCode.UserNotFound))
        assertEquals(AuthMessages.INVALID_CREDENTIALS, AuthMessages.forCode(AuthErrorCode.InvalidCredentials))
    }

    @Test
    fun rateLimitsGetSpecificMessages() {
        assertEquals(AuthMessages.RATE_LIMITED, AuthMessages.forCode(AuthErrorCode.OverRequestRateLimit))
        assertEquals(AuthMessages.EMAIL_SEND_LIMIT, AuthMessages.forCode(AuthErrorCode.OverEmailSendRateLimit))
    }

    @Test
    fun expiredCodeAndTakenEmailAreExplained() {
        assertEquals(AuthMessages.BAD_CODE, AuthMessages.forCode(AuthErrorCode.OtpExpired))
        assertEquals(AuthMessages.EMAIL_TAKEN, AuthMessages.forCode(AuthErrorCode.EmailExists))
    }

    @Test
    fun unexpectedErrorsNeverLeakRawText() {
        assertEquals(AuthMessages.GENERIC, AuthMessages.forError(IllegalStateException("stack trace and secrets")))
        assertEquals(AuthMessages.GENERIC, AuthMessages.forCode(null))
    }
}
