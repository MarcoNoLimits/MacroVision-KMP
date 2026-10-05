package com.fitcal.app.ui.screens.auth

/**
 * Validates whether an email string contains user and domain parts with @ and . symbols.
 */
fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    val atIndex = trimmed.indexOf('@')
    if (atIndex <= 0 || atIndex == trimmed.length - 1 || trimmed.lastIndexOf('@') != atIndex) return false
    val domainPart = trimmed.substring(atIndex + 1)
    return domainPart.contains('.') && !domainPart.startsWith('.') && !domainPart.endsWith('.')
}

/** Mirrors the server password policy: 8+ characters with upper- and lowercase letters and a digit. */
data class PasswordChecks(
    val hasMinLength: Boolean,
    val hasUpperAndLower: Boolean,
    val hasDigit: Boolean,
) {
    val passedCount: Int get() = listOf(hasMinLength, hasUpperAndLower, hasDigit).count { it }
    val isAcceptable: Boolean get() = hasMinLength && hasUpperAndLower && hasDigit
}

const val MIN_PASSWORD_LENGTH = 8

fun checkPassword(password: String) = PasswordChecks(
    hasMinLength = password.length >= MIN_PASSWORD_LENGTH,
    hasUpperAndLower = password.any { it.isUpperCase() } && password.any { it.isLowerCase() },
    hasDigit = password.any { it.isDigit() },
)

fun isValidPassword(password: String): Boolean = checkPassword(password).isAcceptable

/** Keeps only digits and caps the length, so a pasted "123 456" becomes "123456". */
fun sanitizeOtp(input: String, length: Int = OTP_LENGTH): String = input.filter { it.isDigit() }.take(length)

const val OTP_LENGTH = 6
