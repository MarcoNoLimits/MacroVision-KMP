package com.fitter.app.ui.screens.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitter.app.CameraPreview
import com.fitter.app.ads.AdManager
import com.fitter.app.compressImage
import com.fitter.app.ui.components.getMockJson
import com.fitter.app.ui.theme.*
import com.fitter.shared.api.NutritionClient
import com.fitter.shared.model.NutritionResponse
import com.fitter.app.telemetry.DiagnosticsCrashHook
import io.ktor.util.encodeBase64
import com.fitter.app.ScanReadiness
import com.fitter.app.getScanBlockedReason
import com.fitter.shared.telemetry.TelemetryUploader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun CameraScreen(
    apiClient: NutritionClient,
    isMockMode: Boolean,
    plateSizeInches: Float?,
    adManager: AdManager,
    playAdDuringScan: Boolean,
    onScanConsumed: () -> Unit,
    onPhotoCaptured: (ByteArray) -> Unit,
    onResultObtained: (String) -> Unit,
    onNavigateBack: () -> Unit,
    scanReadiness: ScanReadiness = ScanReadiness.Ready
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scanReadiness) {
        val reason = getScanBlockedReason(scanReadiness)
        if (reason != null) {
            TelemetryUploader.recordEvent(
                eventType = "scan_blocked_reason",
                properties = """{"reason":"$reason"}"""
            )
        }
    }

    if (scanReadiness != ScanReadiness.Ready) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (scanReadiness) {
                is ScanReadiness.AuthPending -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = PrimaryAccent)
                        Text(
                            text = "Connecting…",
                            style = BrandTypography.SectionTitle,
                            color = TextColor
                        )
                        Text(
                            text = "Securing session before scanning",
                            style = BrandTypography.BodySmall,
                            color = MutedTextColor
                        )
                    }
                }
                is ScanReadiness.Offline -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Offline",
                            tint = FatColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Offline Mode",
                            style = BrandTypography.SectionTitle,
                            color = TextColor
                        )
                        Text(
                            text = "No connection available. Camera scan is disabled until network is restored.",
                            style = BrandTypography.BodySmall,
                            color = MutedTextColor,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                            shape = RoundedCornerShape(RadiusM)
                        ) {
                            Text("Back to Dashboard", color = Color.White)
                        }
                    }
                }
                is ScanReadiness.QuotaExhausted -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Daily Limit Reached",
                            style = BrandTypography.SectionTitle,
                            color = TextColor
                        )
                        Text(
                            text = "You have used all your scans for today.",
                            style = BrandTypography.BodySmall,
                            color = MutedTextColor,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                            shape = RoundedCornerShape(RadiusM)
                        ) {
                            Text("Back to Dashboard", color = Color.White)
                        }
                    }
                }
                else -> Unit
            }
        }
        return
    }

    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentCapturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    // F3.3: isCancelled guards the result callback so a cancelled state doesn't later navigate
    var isCancelled by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        if (!isAnalyzing && errorMessage == null) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onPhotoCaptured = { imageBytes ->
                    isCancelled = false
                    val compressedBytes = compressImage(imageBytes)
                    currentCapturedBytes = compressedBytes
                    onPhotoCaptured(compressedBytes)
                    isAnalyzing = true
                    errorMessage = null
                    onScanConsumed()

                    if (playAdDuringScan) {
                        var apiResultJson: String? = null
                        var isAdFinished = false
                        var apiError: String? = null

                        // 1. Kick off AI analysis in background
                        coroutineScope.launch {
                            try {
                                val responseJson = if (isMockMode) {
                                    delay(2000)
                                    getMockJson()
                                } else {
                                    val base64 = compressedBytes.encodeBase64()
                                    val response = apiClient.analyzeMealImage(base64, plateSizeInches)
                                    Json.encodeToString(NutritionResponse.serializer(), response)
                                }
                                apiResultJson = responseJson
                                if (isAdFinished && !isCancelled) {
                                    onResultObtained(responseJson)
                                }
                            } catch (e: Exception) {
                                apiError = e.message ?: "Unknown API Error"
                                DiagnosticsCrashHook.logVlmError("CameraScreen", apiError!!, e)
                                if (isAdFinished && !isCancelled) {
                                    errorMessage = apiError
                                    isAnalyzing = false
                                }
                            }
                        }

                        // 2. Play Ad while scan is processing
                        adManager.showScanProcessingAd {
                            isAdFinished = true
                            if (!isCancelled) {
                                if (apiResultJson != null) {
                                    onResultObtained(apiResultJson!!)
                                } else if (apiError != null) {
                                    errorMessage = apiError
                                    isAnalyzing = false
                                }
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            try {
                                val responseJson = if (isMockMode) {
                                    delay(2000)
                                    getMockJson()
                                } else {
                                    val base64 = compressedBytes.encodeBase64()
                                    val response = apiClient.analyzeMealImage(base64, plateSizeInches)
                                    Json.encodeToString(NutritionResponse.serializer(), response)
                                }
                                if (!isCancelled) onResultObtained(responseJson)
                            } catch (e: Exception) {
                                val err = e.message ?: "Unknown API Error"
                                DiagnosticsCrashHook.logVlmError("CameraScreen", err, e)
                                if (!isCancelled) {
                                    errorMessage = err
                                    isAnalyzing = false
                                }
                            }
                        }
                    }
                },
                onCancel = onNavigateBack
            )
        }

        // F3.2: AnimatedVisibility wraps the analyzing card (fade + scaleIn 0.98→1)
        AnimatedVisibility(
            visible = isAnalyzing && REDUCED_MOTION_ENABLED.not(), // F4.4: gate
            enter = fadeIn() + scaleIn(
                initialScale = 0.98f,
                animationSpec = spring(stiffness = 300f)
            ),
            exit = fadeOut() + scaleOut(targetScale = 0.98f)
        ) {
            AnalyzingCard()
        }
        // When REDUCED_MOTION_ENABLED, show without animation
        if (REDUCED_MOTION_ENABLED && isAnalyzing) {
            AnalyzingCard()
        }

        // F3.3: "Analyzing meal in background…" chip shown while ad plays
        // Native ad overlay can't be intercepted with Compose; we show a persistent
        // non-interactive chip and a Cancel button below the analyzing card.
        if (isAnalyzing && playAdDuringScan) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Background analysis chip
                    Surface(
                        color = SurfaceTint, // F1.1
                        shape = RoundedCornerShape(RadiusM), // F1.2
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "Analyzing meal in background…",
                            style = BrandTypography.BodySmall,
                            color = TextColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // F3.3: Cancel affordance — tapping closes the ad path, discards in-flight request
                    OutlinedButton(
                        onClick = {
                            isCancelled = true // guard: result callback won't fire
                            isAnalyzing = false
                            currentCapturedBytes = null
                            onNavigateBack()
                        },
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text("Cancel", color = TextColor, style = BrandTypography.BodySmall)
                    }
                }
            }
        }

        // F3.2: AnimatedVisibility wraps the error card
        AnimatedVisibility(
            visible = errorMessage != null && !REDUCED_MOTION_ENABLED, // F4.4
            enter = fadeIn() + scaleIn(
                initialScale = 0.98f,
                animationSpec = spring(stiffness = 300f)
            ),
            exit = fadeOut()
        ) {
            errorMessage?.let { errorText ->
                ErrorCard(
                    errorText = errorText,
                    isMockMode = isMockMode,
                    onRetake = {
                        errorMessage = null
                        currentCapturedBytes = null
                        isAnalyzing = false
                    },
                    onResend = if (currentCapturedBytes != null) {
                        {
                            errorMessage = null
                            isAnalyzing = true
                            isCancelled = false
                            coroutineScope.launch {
                                try {
                                    val bytes = currentCapturedBytes!!
                                    val responseJson = if (isMockMode) {
                                        delay(2000)
                                        getMockJson()
                                    } else {
                                        val base64 = bytes.encodeBase64()
                                        val response = apiClient.analyzeMealImage(base64, plateSizeInches)
                                        Json.encodeToString(NutritionResponse.serializer(), response)
                                    }
                                    onResultObtained(responseJson)
                                } catch (e: Exception) {
                                    val err = e.message ?: "Unknown API Error"
                                    DiagnosticsCrashHook.logVlmError("CameraScreen", err, e)
                                    errorMessage = err
                                    isAnalyzing = false
                                }
                            }
                        }
                    } else null,
                    onCancel = {
                        errorMessage = null
                        currentCapturedBytes = null
                        isAnalyzing = false
                        onNavigateBack()
                    }
                )
            }
        }
        // Reduced motion: show error without animation
        if (REDUCED_MOTION_ENABLED && errorMessage != null) {
            errorMessage?.let { errorText ->
                ErrorCard(
                    errorText = errorText,
                    isMockMode = isMockMode,
                    onRetake = {
                        errorMessage = null
                        currentCapturedBytes = null
                        isAnalyzing = false
                    },
                    onResend = if (currentCapturedBytes != null) {
                        {
                            errorMessage = null
                            isAnalyzing = true
                            isCancelled = false
                            coroutineScope.launch {
                                try {
                                    val bytes = currentCapturedBytes!!
                                    val responseJson = if (isMockMode) {
                                        delay(2000)
                                        getMockJson()
                                    } else {
                                        val base64 = bytes.encodeBase64()
                                        val response = apiClient.analyzeMealImage(base64, plateSizeInches)
                                        Json.encodeToString(NutritionResponse.serializer(), response)
                                    }
                                    onResultObtained(responseJson)
                                } catch (e: Exception) {
                                    val err = e.message ?: "Unknown API Error"
                                    DiagnosticsCrashHook.logVlmError("CameraScreen", err, e)
                                    errorMessage = err
                                    isAnalyzing = false
                                }
                            }
                        }
                    } else null,
                    onCancel = {
                        errorMessage = null
                        currentCapturedBytes = null
                        isAnalyzing = false
                        onNavigateBack()
                    }
                )
            }
        }
    }
}

// Extracted composable to avoid duplication between animated and reduced-motion paths
@Composable
private fun AnalyzingCard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(RadiusL), // F1.2
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
                .shadow(16.dp, RoundedCornerShape(RadiusL))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                CircularProgressIndicator(
                    color = PrimaryAccent,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Analyzing Meal...",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextColor
                )
                Text(
                    text = "Fitter is calculating macro estimates and identifying ingredients from your capture.",
                    style = BrandTypography.BodySmall,
                    color = MutedTextColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    errorText: String,
    isMockMode: Boolean,
    onRetake: () -> Unit,
    onResend: (() -> Unit)?,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(RadiusL), // F1.2
            modifier = Modifier
                .fillMaxWidth()
                // F1.1: DangerColor replaces Color(0xFFEF4444)
                .border(1.dp, DangerColor.copy(alpha = 0.5f), RoundedCornerShape(RadiusL))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = DangerColor, // F1.1
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Could not analyze image",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextColor,
                    textAlign = TextAlign.Center
                )

                // Monospace Diagnostics Log Box — F1.1 all raw hex → semantic tokens
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DangerSoft, RoundedCornerShape(RadiusS)) // F1.1+F1.2
                        .border(1.dp, DangerBorder, RoundedCornerShape(RadiusS))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "API DIAGNOSTICS LOG",
                            style = BrandTypography.Eyebrow,
                            color = DangerTextStrong // F1.1
                        )
                        Text(
                            text = if (isMockMode && errorText.contains("placeholder"))
                                "Mock API Key issue."
                            else
                                errorText,
                            // F2.1: Diagnostics style (11sp monospace) — kept at 11sp per spec exemption
                            style = BrandTypography.Diagnostics,
                            color = DangerTextDeep, // F1.1
                            modifier = Modifier
                                .heightIn(max = 100.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onRetake,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text("Retake Photo", color = TextColor, style = BrandTypography.BodySmall)
                        }

                        if (onResend != null) {
                            Button(
                                onClick = onResend,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                            ) {
                                Text("Resend Photo", color = Color.White, style = BrandTypography.BodySmall)
                            }
                        }
                    }

                    // F3.3: Cancel is present in error card
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text("Cancel", color = TextColor, style = BrandTypography.BodySmall)
                    }
                }
            }
        }
    }
}
