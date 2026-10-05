package com.fitcal.app.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ui.components.BrandLogos
import com.fitcal.app.ui.components.FitCalTextField
import com.fitcal.app.ui.theme.BgColor
import com.fitcal.app.ui.theme.BorderColor
import com.fitcal.app.ui.theme.BrandTypography
import com.fitcal.app.ui.theme.CardBackground
import com.fitcal.app.ui.theme.DangerBorder
import com.fitcal.app.ui.theme.DangerColor
import com.fitcal.app.ui.theme.DangerSoft
import com.fitcal.app.ui.theme.DangerTextStrong
import com.fitcal.app.ui.theme.InputBorder
import com.fitcal.app.ui.theme.MutedTextColor
import com.fitcal.app.ui.theme.PrimaryAccent
import com.fitcal.app.ui.theme.RadiusL
import com.fitcal.app.ui.theme.RadiusM
import com.fitcal.app.ui.theme.RadiusS
import com.fitcal.app.ui.theme.SurfaceTint
import com.fitcal.app.ui.theme.TextColor
import com.fitcal.shared.auth.AuthMessages
import com.fitcal.shared.auth.EmailSignUpStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Server calls behind the auth screen. Each throws on failure; the screen shows a friendly message. */
class AuthActions(
    val signIn: suspend (email: String, password: String) -> Unit,
    val startSignUp: suspend (email: String, password: String) -> EmailSignUpStart,
    val completeSignUp: suspend (email: String, code: String, password: String) -> Unit,
    val requestPasswordReset: suspend (email: String) -> Unit,
    val completePasswordReset: suspend (email: String, code: String, newPassword: String) -> Unit,
)

/** Google/Apple buttons. Each provider runs its own native sheet; results come back via [error]/[inProgress]. */
class SocialSignIn(
    val showGoogle: Boolean,
    val showApple: Boolean,
    val onGoogle: () -> Unit,
    val onApple: () -> Unit,
    val inProgress: Boolean,
    val error: String?,
)

private sealed interface AuthStep {
    data object Form : AuthStep
    data class VerifySignUp(val email: String, val password: String) : AuthStep
    data class ForgotPassword(val email: String) : AuthStep
    data class ResetPassword(val email: String) : AuthStep
}

private const val RESEND_COOLDOWN_SECONDS = 60

@Composable
fun AuthScreen(
    actions: AuthActions,
    social: SocialSignIn,
    startInSignUp: Boolean = true,
    onClose: () -> Unit,
) {
    var step by remember { mutableStateOf<AuthStep>(AuthStep.Form) }
    var isSignUp by remember { mutableStateOf(startInSignUp) }
    var email by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(
            showBack = step != AuthStep.Form,
            onBack = { step = AuthStep.Form },
            onClose = onClose,
        )
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "auth-step",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (current) {
                    AuthStep.Form -> FormStep(
                        isSignUp = isSignUp,
                        email = email,
                        onEmailChange = { email = it },
                        onToggleMode = { isSignUp = !isSignUp },
                        actions = actions,
                        social = social,
                        onCodeSent = { password -> step = AuthStep.VerifySignUp(email.trim(), password) },
                        onSwitchToSignIn = { isSignUp = false },
                        onForgotPassword = { step = AuthStep.ForgotPassword(email.trim()) },
                        onNotNow = onClose,
                    )
                    is AuthStep.VerifySignUp -> VerifySignUpStep(
                        email = current.email,
                        onVerify = { code -> actions.completeSignUp(current.email, code, current.password) },
                        onResend = { actions.startSignUp(current.email, current.password) },
                        onChangeEmail = { step = AuthStep.Form },
                    )
                    is AuthStep.ForgotPassword -> ForgotPasswordStep(
                        initialEmail = current.email,
                        onSend = { address ->
                            actions.requestPasswordReset(address)
                            email = address
                            step = AuthStep.ResetPassword(address)
                        },
                    )
                    is AuthStep.ResetPassword -> ResetPasswordStep(
                        email = current.email,
                        onReset = { code, newPassword -> actions.completePasswordReset(current.email, code, newPassword) },
                        onResend = { actions.requestPasswordReset(current.email) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(showBack: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        }
        Spacer(Modifier.weight(1f))
        CircleIconButton(Icons.Default.Close, "Close", onClose)
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(CardBackground, CircleShape)
            .border(1.dp, BorderColor, CircleShape),
    ) {
        Icon(icon, contentDescription = description, tint = TextColor, modifier = Modifier.size(20.dp))
    }
}

// ── Step 1: sign in / create account ─────────────────────────────────────────

@Composable
private fun FormStep(
    isSignUp: Boolean,
    email: String,
    onEmailChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    actions: AuthActions,
    social: SocialSignIn,
    onCodeSent: (password: String) -> Unit,
    onSwitchToSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onNotNow: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }

    val checks = checkPassword(password)
    val emailError = submitted && !isValidEmail(email)
    val passwordError = submitted && if (isSignUp) !checks.isAcceptable else password.isEmpty()
    val busy = loading || social.inProgress

    fun submit() {
        submitted = true
        error = null
        info = null
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) return
        if (isSignUp && !checks.isAcceptable) return
        if (!isSignUp && password.isEmpty()) return
        focusManager.clearFocus()
        loading = true
        scope.launch {
            try {
                if (isSignUp) {
                    when (actions.startSignUp(cleanEmail, password)) {
                        EmailSignUpStart.CodeSent -> onCodeSent(password)
                        EmailSignUpStart.Completed -> Unit
                        EmailSignUpStart.EmailAlreadyRegistered -> {
                            onSwitchToSignIn()
                            password = ""
                            submitted = false
                            info = AuthMessages.EMAIL_TAKEN
                        }
                    }
                } else {
                    actions.signIn(cleanEmail, password)
                }
            } catch (t: Throwable) {
                error = AuthMessages.forError(t)
            } finally {
                loading = false
            }
        }
    }

    Header(
        icon = Icons.Default.Lock,
        title = if (isSignUp) "Keep your meals safe" else "Welcome back",
        subtitle = if (isSignUp) {
            "Create a free account. Everything you've logged so far comes with you."
        } else {
            "Sign in to sync your meals on this device."
        },
    )

    if (isSignUp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Benefit("Your meal history is backed up")
            Benefit("Sync across all your devices")
            Benefit("+1 free AI scan every day")
        }
    }

    if (social.showGoogle || social.showApple) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (social.showApple) {
                SocialButton(
                    text = "Continue with Apple",
                    logo = BrandLogos.Apple,
                    container = Color.Black,
                    content = Color.White,
                    enabled = !busy,
                    onClick = social.onApple,
                )
            }
            if (social.showGoogle) {
                SocialButton(
                    text = "Continue with Google",
                    logo = BrandLogos.Google,
                    container = Color.White,
                    content = Color(0xFF1F1F1F),
                    border = BorderStroke(1.dp, Color(0xFF747775)),
                    enabled = !busy,
                    onClick = social.onGoogle,
                )
            }
            if (social.inProgress) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryAccent)
                    Text("Signing you in…", style = BrandTypography.BodySmall, color = MutedTextColor)
                }
            }
            social.error?.let { MessageBanner(it, isError = true) }
        }
        OrDivider()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FitCalTextField(
            value = email,
            onValueChange = {
                onEmailChange(it.trim())
                error = null
            },
            label = "Email",
            keyboardType = KeyboardType.Email,
            isError = emailError,
            supportingText = if (emailError) "Enter a valid email address" else null,
            enabled = !busy,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            autofillType = ContentType.EmailAddress,
            modifier = Modifier.fillMaxWidth(),
        )
        FitCalTextField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            label = "Password",
            keyboardType = KeyboardType.Password,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { ShowHideToggle(showPassword) { showPassword = !showPassword } },
            isError = passwordError,
            supportingText = when {
                passwordError && !isSignUp -> "Enter your password"
                passwordError -> "Your password doesn't meet the requirements below"
                else -> null
            },
            enabled = !busy,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
            autofillType = if (isSignUp) ContentType.NewPassword else ContentType.Password,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isSignUp && password.isNotEmpty()) {
            PasswordStrength(checks)
        }
        if (!isSignUp) {
            TextButton(onClick = onForgotPassword, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                Text("Forgot password?", style = BrandTypography.BodySmall, color = PrimaryAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    info?.let { MessageBanner(it, isError = false) }
    error?.let { MessageBanner(it, isError = true) }

    PrimaryButton(
        text = if (isSignUp) "Create account" else "Sign in",
        loading = loading,
        enabled = !busy,
        onClick = { submit() },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isSignUp) "Already have an account?" else "New to FitCal?",
            style = BrandTypography.BodySmall,
            color = MutedTextColor,
        )
        TextButton(
            onClick = {
                onToggleMode()
                error = null
                info = null
                submitted = false
            },
            enabled = !busy,
        ) {
            Text(
                text = if (isSignUp) "Sign in" else "Create an account",
                style = BrandTypography.BodySmall,
                color = PrimaryAccent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    TextButton(onClick = onNotNow, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Not now — keep using FitCal as a guest", style = BrandTypography.BodySmall, color = MutedTextColor)
    }
}

// ── Step 2: confirm the email with a code ────────────────────────────────────

@Composable
private fun VerifySignUpStep(
    email: String,
    onVerify: suspend (code: String) -> Unit,
    onResend: suspend () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Header(
        icon = Icons.Default.Email,
        title = "Check your email",
        subtitle = "We sent a 6-digit code to $email. Enter it to finish creating your account.",
    )
    CodeEntry(submitLabel = "Verify email", onSubmit = onVerify, onResend = onResend)
    TextButton(onClick = onChangeEmail, modifier = Modifier.fillMaxWidth()) {
        Text("Use a different email", style = BrandTypography.BodySmall, color = MutedTextColor)
    }
}

// ── Step 3: request a password reset ─────────────────────────────────────────

@Composable
private fun ForgotPasswordStep(initialEmail: String, onSend: suspend (email: String) -> Unit) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var email by remember { mutableStateOf(initialEmail) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val emailError = submitted && !isValidEmail(email)

    fun send() {
        submitted = true
        error = null
        if (!isValidEmail(email)) return
        focusManager.clearFocus()
        loading = true
        scope.launch {
            try {
                onSend(email.trim())
            } catch (t: Throwable) {
                error = AuthMessages.forError(t)
            } finally {
                loading = false
            }
        }
    }

    Header(
        icon = Icons.Default.Lock,
        title = "Reset your password",
        subtitle = "Enter the email you signed up with. If it has an account, we'll send a 6-digit code.",
    )
    FitCalTextField(
        value = email,
        onValueChange = {
            email = it.trim()
            error = null
        },
        label = "Email",
        keyboardType = KeyboardType.Email,
        isError = emailError,
        supportingText = if (emailError) "Enter a valid email address" else null,
        enabled = !loading,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { send() }),
        autofillType = ContentType.EmailAddress,
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { MessageBanner(it, isError = true) }
    PrimaryButton(text = "Send code", loading = loading, enabled = !loading, onClick = { send() })
}

// ── Step 4: enter the code and a new password ────────────────────────────────

@Composable
private fun ResetPasswordStep(
    email: String,
    onReset: suspend (code: String, newPassword: String) -> Unit,
    onResend: suspend () -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val checks = checkPassword(newPassword)

    Header(
        icon = Icons.Default.Email,
        title = "Choose a new password",
        subtitle = "If $email has a FitCal account, we sent it a 6-digit code.",
    )
    FitCalTextField(
        value = newPassword,
        onValueChange = { newPassword = it },
        label = "New password",
        keyboardType = KeyboardType.Password,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { ShowHideToggle(showPassword) { showPassword = !showPassword } },
        imeAction = ImeAction.Next,
        autofillType = ContentType.NewPassword,
        modifier = Modifier.fillMaxWidth(),
    )
    if (newPassword.isNotEmpty()) PasswordStrength(checks)
    CodeEntry(
        submitLabel = "Set new password",
        canSubmit = checks.isAcceptable,
        blockedReason = "Choose a password that meets the requirements above.",
        onSubmit = { code -> onReset(code, newPassword) },
        onResend = onResend,
    )
}

// ── Shared pieces ────────────────────────────────────────────────────────────

@Composable
private fun CodeEntry(
    submitLabel: String,
    onSubmit: suspend (code: String) -> Unit,
    onResend: suspend () -> Unit,
    canSubmit: Boolean = true,
    blockedReason: String? = null,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var resendCountdown by remember { mutableIntStateOf(RESEND_COOLDOWN_SECONDS) }
    var resendRound by remember { mutableIntStateOf(0) }

    LaunchedEffect(resendRound) {
        resendCountdown = RESEND_COOLDOWN_SECONDS
        while (resendCountdown > 0) {
            delay(1_000)
            resendCountdown--
        }
    }

    fun submit() {
        error = null
        info = null
        if (code.length != OTP_LENGTH) {
            error = "Enter the 6-digit code from the email."
            return
        }
        if (!canSubmit) {
            error = blockedReason
            return
        }
        focusManager.clearFocus()
        loading = true
        scope.launch {
            try {
                onSubmit(code)
            } catch (t: Throwable) {
                error = AuthMessages.forError(t)
                code = ""
            } finally {
                loading = false
            }
        }
    }

    OtpInput(
        value = code,
        onValueChange = {
            code = it
            error = null
            if (it.length == OTP_LENGTH && canSubmit) submit()
        },
        isError = error != null,
        enabled = !loading,
        focusRequester = focusRequester,
        onDone = { submit() },
    )
    info?.let { MessageBanner(it, isError = false) }
    error?.let { MessageBanner(it, isError = true) }
    PrimaryButton(text = submitLabel, loading = loading, enabled = !loading, onClick = { submit() })

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Didn't get it?", style = BrandTypography.BodySmall, color = MutedTextColor)
        TextButton(
            onClick = {
                scope.launch {
                    error = null
                    try {
                        onResend()
                        info = "We sent a new code."
                        code = ""
                        resendRound++
                    } catch (t: Throwable) {
                        error = AuthMessages.forError(t)
                    }
                }
            },
            enabled = resendCountdown == 0 && !loading,
        ) {
            Text(
                text = if (resendCountdown > 0) "Resend in ${resendCountdown}s" else "Resend code",
                style = BrandTypography.BodySmall,
                color = if (resendCountdown > 0) MutedTextColor else PrimaryAccent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    Text(
        text = "Check your spam folder if it doesn't arrive within a minute.",
        style = BrandTypography.Micro,
        color = MutedTextColor,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeOtp(it)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics {
                contentType = ContentType.SmsOtpCode
                contentDescription = "6-digit code, ${value.length} of $OTP_LENGTH entered"
            },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(OTP_LENGTH) { index ->
                    val digit = value.getOrNull(index)
                    val isActive = enabled && index == value.length
                    val borderColor = when {
                        isError -> DangerColor
                        isActive -> PrimaryAccent
                        digit != null -> TextColor.copy(alpha = 0.4f)
                        else -> InputBorder
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.82f)
                            .background(CardBackground, RoundedCornerShape(RadiusS))
                            .border(if (isActive || isError) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(RadiusS)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit?.toString() ?: "",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextColor,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Header(icon: ImageVector, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(PrimaryAccent.copy(alpha = 0.12f), RoundedCornerShape(RadiusM)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(28.dp))
        }
        Text(title, style = BrandTypography.ScreenTitle, color = TextColor)
        Text(subtitle, style = BrandTypography.Body, color = MutedTextColor)
    }
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(22.dp).background(PrimaryAccent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(text, style = BrandTypography.Body, color = TextColor)
    }
}

@Composable
private fun SocialButton(
    text: String,
    logo: ImageVector,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    border: BorderStroke? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RadiusM),
        border = border ?: BorderStroke(1.dp, container),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = 0.6f),
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(logo, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
        Text("or use email", style = BrandTypography.Micro, color = MutedTextColor)
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
    }
}

@Composable
private fun ShowHideToggle(visible: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.padding(end = 4.dp)) {
        Text(
            text = if (visible) "Hide" else "Show",
            style = BrandTypography.BodySmall,
            color = PrimaryAccent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PasswordStrength(checks: PasswordChecks) {
    val barColor = when (checks.passedCount) {
        3 -> PrimaryAccent
        2 -> Color(0xFFF59E0B)
        else -> DangerColor
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (index < checks.passedCount) barColor else SurfaceTint, RoundedCornerShape(2.dp)),
                )
            }
        }
        Requirement("At least $MIN_PASSWORD_LENGTH characters", checks.hasMinLength)
        Requirement("Upper- and lowercase letters", checks.hasUpperAndLower)
        Requirement("At least one number", checks.hasDigit)
    }
}

@Composable
private fun Requirement(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = if (met) "Met" else "Not met",
            tint = if (met) PrimaryAccent else InputBorder,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = BrandTypography.BodySmall, color = if (met) TextColor else MutedTextColor)
    }
}

@Composable
private fun MessageBanner(message: String, isError: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isError) DangerSoft else PrimaryAccent.copy(alpha = 0.1f), RoundedCornerShape(RadiusS))
            .border(1.dp, if (isError) DangerBorder else PrimaryAccent.copy(alpha = 0.35f), RoundedCornerShape(RadiusS))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.Warning else Icons.Default.Check,
            contentDescription = null,
            tint = if (isError) DangerColor else PrimaryAccent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = BrandTypography.BodySmall,
            color = if (isError) DangerTextStrong else TextColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PrimaryButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RadiusM),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryAccent,
            disabledContainerColor = PrimaryAccent.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(text, style = BrandTypography.CardTitle, color = Color.White)
        }
    }
}

/** Compact card inviting a guest to create an account; used after logging a meal and when scans run out. */
@Composable
fun SignInSuggestionCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PrimaryAccent.copy(alpha = 0.08f), RoundedCornerShape(RadiusL))
            .border(1.dp, PrimaryAccent.copy(alpha = 0.3f), RoundedCornerShape(RadiusL))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = BrandTypography.SectionTitle, color = TextColor)
        Text(body, style = BrandTypography.BodySmall, color = MutedTextColor)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(RadiusM),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 120.dp),
            ) {
                Text(actionLabel, style = BrandTypography.BodySmall, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Not now", style = BrandTypography.BodySmall, color = MutedTextColor)
                }
            }
        }
    }
}
