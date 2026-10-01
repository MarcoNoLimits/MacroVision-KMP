package com.fitcal.app.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fitcal.app.ui.components.FitCalTextField
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Validates whether an email string contains user and domain parts with @ and . symbols.
 */
fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    if (trimmed.isEmpty()) return false
    val atIndex = trimmed.indexOf('@')
    if (atIndex <= 0 || atIndex == trimmed.length - 1) return false
    val domainPart = trimmed.substring(atIndex + 1)
    return domainPart.contains('.') && !domainPart.startsWith('.') && !domainPart.endsWith('.')
}

/**
 * Validates minimum password length (must be at least 6 characters per standard auth policy).
 */
fun isValidPassword(password: String): Boolean {
    return password.length >= 6
}

@Composable
fun AuthScreen(
    onSignIn: suspend (email: String, pass: String) -> Unit,
    onSignUp: suspend (email: String, pass: String) -> Unit,
    onContinueAsGuest: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun handleSubmit() {
        errorMessage = null
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) {
            errorMessage = "Please enter a valid email address."
            return
        }
        if (!isValidPassword(password)) {
            errorMessage = "Password must be at least 6 characters long."
            return
        }

        isLoading = true
        coroutineScope.launch {
            try {
                if (isSignUp) {
                    onSignUp(cleanEmail, password)
                } else {
                    onSignIn(cleanEmail, password)
                }
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Authentication failed. Please try again."
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, BorderColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = if (isSignUp) "Create Account" else "Sign In",
                style = BrandTypography.ScreenTitle,
                color = TextColor
            )
        }

        // Mode Selector: Sign In vs Create Account
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceTint, RoundedCornerShape(RadiusM))
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PressableBox(
                onTap = {
                    isSignUp = false
                    errorMessage = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .background(
                            if (!isSignUp) CardBackground else Color.Transparent,
                            RoundedCornerShape(RadiusS)
                        )
                        .then(
                            if (!isSignUp) Modifier.shadow(1.dp, RoundedCornerShape(RadiusS))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In",
                        style = BrandTypography.SectionTitle,
                        color = if (!isSignUp) PrimaryAccent else MutedTextColor
                    )
                }
            }

            PressableBox(
                onTap = {
                    isSignUp = true
                    errorMessage = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .background(
                            if (isSignUp) CardBackground else Color.Transparent,
                            RoundedCornerShape(RadiusS)
                        )
                        .then(
                            if (isSignUp) Modifier.shadow(1.dp, RoundedCornerShape(RadiusS))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create Account",
                        style = BrandTypography.SectionTitle,
                        color = if (isSignUp) PrimaryAccent else MutedTextColor
                    )
                }
            }
        }

        // Form Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(RadiusL),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(RadiusL))
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "EMAIL & PASSWORD",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )

                FitCalTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = "Email Address",
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.fillMaxWidth()
                )

                FitCalTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = "Password",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Inline validation/API error banner
                if (errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DangerSoft, RoundedCornerShape(RadiusS))
                            .border(1.dp, DangerBorder, RoundedCornerShape(RadiusS))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = DangerColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorMessage ?: "",
                            style = BrandTypography.BodySmall,
                            color = DangerTextStrong,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Submit Action
                Button(
                    onClick = { handleSubmit() },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        disabledContainerColor = PrimaryAccent.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(RadiusM),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(2.dp, RoundedCornerShape(RadiusM))
                ) {
                    Text(
                        text = if (isLoading) "Please wait…" else if (isSignUp) "Create Account" else "Sign In",
                        style = BrandTypography.CardTitle,
                        color = Color.White
                    )
                }
            }
        }

        // Guest escape card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(RadiusL),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(RadiusL))
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "GUEST MODE",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )

                Text(
                    text = "Login is optional. Guest mode gives you full access to meal scanning and macro tracking with the exact same ad experience.",
                    style = BrandTypography.BodySmall,
                    color = TextColor
                )

                OutlinedButton(
                    onClick = onContinueAsGuest,
                    shape = RoundedCornerShape(RadiusM),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Continue as Guest",
                        style = BrandTypography.CardTitle,
                        color = TextColor
                    )
                }
            }
        }
    }
}
