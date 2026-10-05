package com.fitcal.shared.auth

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.exceptions.HttpRequestException

/**
 * User-facing text for auth failures. Never shows raw server text, and never reveals
 * whether an email has an account when signing in (wrong password and unknown email
 * read the same).
 */
object AuthMessages {
    const val GENERIC = "Something went wrong. Please try again."
    const val NETWORK = "No internet connection. Check your connection and try again."
    const val WEAK_PASSWORD =
        "Choose a stronger password: at least 8 characters with upper- and lowercase letters and a number."
    const val INVALID_CREDENTIALS = "Email or password is incorrect."
    const val EMAIL_TAKEN = "This email already has a FitCal account. Sign in instead."
    const val BAD_CODE = "That code is incorrect or has expired. Request a new one."
    const val RATE_LIMITED = "Too many attempts. Please wait a minute and try again."
    const val EMAIL_SEND_LIMIT = "We couldn't send another email right now. Please try again in a few minutes."
    const val METHOD_UNAVAILABLE = "This sign-in method isn't available right now. Please try another one."

    fun forError(error: Throwable): String = when (error) {
        is AuthWeakPasswordException -> WEAK_PASSWORD
        is AuthRestException -> forCode(error.errorCode)
        is HttpRequestException -> NETWORK
        else -> GENERIC
    }

    fun forCode(code: AuthErrorCode?): String = when (code) {
        AuthErrorCode.InvalidCredentials, AuthErrorCode.UserNotFound -> INVALID_CREDENTIALS
        AuthErrorCode.EmailNotConfirmed -> "Please confirm your email first. Check your inbox for the code."
        AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> EMAIL_TAKEN
        AuthErrorCode.WeakPassword -> WEAK_PASSWORD
        AuthErrorCode.SamePassword -> "Your new password must be different from your current one."
        AuthErrorCode.OtpExpired -> BAD_CODE
        AuthErrorCode.OverRequestRateLimit -> RATE_LIMITED
        AuthErrorCode.OverEmailSendRateLimit -> EMAIL_SEND_LIMIT
        AuthErrorCode.IdentityAlreadyExists -> "That account is already connected to another FitCal user."
        AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed -> "Please check your email address."
        AuthErrorCode.ManualLinkingDisabled,
        AuthErrorCode.EmailProviderDisabled,
        AuthErrorCode.ProviderDisabled,
        AuthErrorCode.SignupDisabled,
        AuthErrorCode.OauthProviderNotSupported -> METHOD_UNAVAILABLE
        AuthErrorCode.UserBanned -> "This account can't be used. Please contact support."
        AuthErrorCode.RequestTimeout -> NETWORK
        else -> GENERIC
    }
}
