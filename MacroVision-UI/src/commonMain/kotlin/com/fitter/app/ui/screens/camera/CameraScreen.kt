package com.fitter.app.ui.screens.camera

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
import androidx.compose.ui.unit.sp
import com.fitter.app.CameraPreview
import com.fitter.app.ads.AdManager
import com.fitter.app.compressImage
import com.fitter.app.ui.components.getMockJson
import com.fitter.app.ui.theme.BorderColor
import com.fitter.app.ui.theme.CardBackground
import com.fitter.app.ui.theme.MutedTextColor
import com.fitter.app.ui.theme.PrimaryAccent
import com.fitter.app.ui.theme.TextColor
import com.fitter.shared.api.NutritionClient
import com.fitter.shared.model.NutritionResponse
import io.ktor.util.encodeBase64
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
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentCapturedBytes by remember { mutableStateOf<ByteArray?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isAnalyzing && errorMessage == null) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onPhotoCaptured = { imageBytes ->
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
                                if (isAdFinished) {
                                    onResultObtained(responseJson)
                                }
                            } catch (e: Exception) {
                                apiError = e.message ?: "Unknown API Error"
                                if (isAdFinished) {
                                    errorMessage = apiError
                                    isAnalyzing = false
                                }
                            }
                        }

                        // 2. Play Ad while scan is processing
                        adManager.showScanProcessingAd {
                            isAdFinished = true
                            if (apiResultJson != null) {
                                onResultObtained(apiResultJson)
                            } else if (apiError != null) {
                                errorMessage = apiError
                                isAnalyzing = false
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            try {
                                val responseJson = if (isMockMode) {
                                    // Simulate API delay
                                    delay(2000)
                                    getMockJson()
                                } else {
                                    val base64 = compressedBytes.encodeBase64()
                                    val response = apiClient.analyzeMealImage(base64, plateSizeInches)
                                    Json.encodeToString(NutritionResponse.serializer(), response)
                                }
                                onResultObtained(responseJson)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Unknown API Error"
                                isAnalyzing = false
                            }
                        }
                    }
                },
                onCancel = onNavigateBack
            )
        }

        // Loading state with modern Vision Active style
        if (isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                        .shadow(16.dp, RoundedCornerShape(24.dp))
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Error Card UI
        errorMessage?.let { errorText ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Text(
                            text = "Could not analyze image",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextColor,
                            textAlign = TextAlign.Center
                        )
                        
                        // Monospace Diagnostics Log Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "API DIAGNOSTICS LOG",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB91C1C),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (isMockMode && errorText.contains("placeholder")) 
                                        "Mock API Key issue." 
                                    else 
                                        errorText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF7F1D1D),
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
                                // Retake Photo Button
                                OutlinedButton(
                                    onClick = {
                                        errorMessage = null
                                        currentCapturedBytes = null
                                        isAnalyzing = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, BorderColor)
                                ) {
                                    Text("Retake Photo", color = TextColor)
                                }
                                
                                // Resend Photo Button
                                if (currentCapturedBytes != null) {
                                    Button(
                                        onClick = {
                                            errorMessage = null
                                            isAnalyzing = true
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
                                                    errorMessage = e.message ?: "Unknown API Error"
                                                    isAnalyzing = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                                    ) {
                                        Text("Resend Photo", color = Color.White)
                                    }
                                }
                            }
                            
                            // Cancel / Go Back Button
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    currentCapturedBytes = null
                                    isAnalyzing = false
                                    onNavigateBack()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Text("Cancel", color = TextColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
