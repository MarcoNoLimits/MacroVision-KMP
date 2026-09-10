package com.fitter.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitter.app.ads.AdBanner
import com.fitter.app.ads.AdManager
import com.fitter.app.ads.ScanQuotaManager
import com.fitter.app.ui.components.calculateBmr
import com.fitter.app.ui.theme.*
import com.fitter.shared.model.UserProfile

@Composable
fun SettingsScreen(
    profile: UserProfile,
    adManager: AdManager,
    playAdDuringScan: Boolean,
    scansRemainingToday: Int,
    currentDateKey: String,
    onTogglePlayAd: (Boolean) -> Unit,
    onScansUpdated: () -> Unit,
    onSave: (UserProfile) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    var heightStr by remember { mutableStateOf(profile.height.toString()) }
    var weightStr by remember { mutableStateOf(profile.weight.toString()) }
    var ageStr by remember { mutableStateOf(profile.age.toString()) }
    
    var genderOption by remember { mutableStateOf(profile.gender) }
    var goalOption by remember { mutableStateOf(profile.goalType) }

    var calGoalStr by remember { mutableStateOf(profile.calGoal.toString()) }
    var proteinGoalStr by remember { mutableStateOf(profile.proteinGoal.toString()) }
    var carbsGoalStr by remember { mutableStateOf(profile.carbsGoal.toString()) }
    var fatGoalStr by remember { mutableStateOf(profile.fatGoal.toString()) }
    var plateSize by remember { mutableStateOf(profile.defaultPlateSize) }

    var isError by remember { mutableStateOf(false) }

    // Dynamic suggested targets based on Mifflin-St Jeor Formula
    val suggestedCal = remember(weightStr, heightStr, ageStr, genderOption, goalOption) {
        val w = weightStr.toFloatOrNull() ?: 70f
        val h = heightStr.toFloatOrNull() ?: 175f
        val a = ageStr.toIntOrNull() ?: 25
        val baseBmr = if (genderOption == "Male") {
            (10f * w) + (6.25f * h) - (5f * a) + 5f
        } else {
            (10f * w) + (6.25f * h) - (5f * a) - 161f
        }
        val maintenance = (baseBmr * 1.2f).toInt()
        when (goalOption) {
            "Lose Weight" -> (maintenance - 500).coerceAtLeast(1200)
            "Gain Muscle" -> maintenance + 300
            else -> maintenance
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Settings Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, BorderColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Back",
                    tint = TextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Goals & Parameters",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextColor
            )
        }

        // Section 1: Body Parameters Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "BODY PARAMETERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTextColor,
                    letterSpacing = 1.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it },
                        label = { Text("Height (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ageStr,
                        onValueChange = { ageStr = it },
                        label = { Text("Age (yrs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Gender selection chips
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gender", fontSize = 11.sp, color = MutedTextColor, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Male", "Female").forEach { g ->
                                val selected = genderOption == g
                                Box(
                                    modifier = Modifier
                                        .background(if (selected) PrimaryAccent else Color(0xFFF1F5F9), CircleShape)
                                        .clickable { genderOption = g }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = g,
                                        fontSize = 12.sp,
                                        color = if (selected) Color.White else TextColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Goal Type Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "FITNESS GOAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTextColor,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Lose Weight", "Maintain", "Gain Muscle").forEach { gType ->
                        val selected = goalOption == gType
                        Box(
                            modifier = Modifier
                                .background(if (selected) PrimaryAccent else Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                .clickable { goalOption = gType }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = gType,
                                fontSize = 12.sp,
                                color = if (selected) Color.White else TextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                // Suggestion Prompt Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                        .clickable {
                            // Apply suggested macro split (30% Protein, 45% Carbs, 25% Fat)
                            calGoalStr = suggestedCal.toString()
                            proteinGoalStr = ((suggestedCal * 0.30f) / 4f).toInt().toString()
                            carbsGoalStr = ((suggestedCal * 0.45f) / 4f).toInt().toString()
                            fatGoalStr = ((suggestedCal * 0.25f) / 9f).toInt().toString()
                        }
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "METABOLIC SUGGESTION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryAccent,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Based on BMR, your recommended intake is $suggestedCal kcal.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                        Text(
                            text = "👉 Tap here to automatically apply Calorie & Macro splits (30% Protein, 45% Carbs, 25% Fat).",
                            fontSize = 11.sp,
                            color = MutedTextColor
                        )
                    }
                }
            }
        }

        // Section: Plate Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PLATE SETTINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTextColor,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Default Plate Size: $plateSize inches",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )

                Slider(
                    value = plateSize,
                    onValueChange = { plateSize = kotlin.math.round(it * 2.0f) / 2.0f },
                    valueRange = 6.0f..12.0f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = PrimaryAccent,
                        inactiveTrackColor = BorderColor,
                        thumbColor = PrimaryAccent
                    )
                )
            }
        }

        // Section 3: Daily Target Goals Override
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "CUSTOM TARGETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTextColor,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = calGoalStr,
                    onValueChange = { calGoalStr = it },
                    label = { Text("Calories Budget (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = proteinGoalStr,
                        onValueChange = { proteinGoalStr = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsGoalStr,
                        onValueChange = { carbsGoalStr = it },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatGoalStr,
                        onValueChange = { fatGoalStr = it },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section 4: Monetization & Ad Settings
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "AD MONETIZATION & DAILY QUOTA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTextColor,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Play Ad During Meal Scan",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                        Text(
                            text = "Plays an ad while food photo is analyzed in background",
                            fontSize = 12.sp,
                            color = MutedTextColor
                        )
                    }
                    Switch(
                        checked = playAdDuringScan,
                        onCheckedChange = { onTogglePlayAd(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryAccent
                        )
                    )
                }

                HorizontalDivider(color = BorderColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daily AI Scans Available",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                        Text(
                            text = "Free daily allowance: ${ScanQuotaManager.getDailyFreeLimit()} scans",
                            fontSize = 12.sp,
                            color = MutedTextColor
                        )
                    }
                    Text(
                        text = "$scansRemainingToday scans",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryAccent
                    )
                }

                OutlinedButton(
                    onClick = {
                        adManager.showRewardedScanUnlockAd(
                            onRewarded = {
                                ScanQuotaManager.addBonusScans(currentDateKey, 2)
                                onScansUpdated()
                            },
                            onDismissed = {
                                onScansUpdated()
                            }
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PrimaryAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Watch Rewarded Ad (+2 Scans)",
                        color = PrimaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Provider: Google AdMob (Official Test Units)",
                    fontSize = 10.sp,
                    color = MutedTextColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Validation Error Message
        if (isError) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Please enter valid numeric goals and parameters.",
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Medium
                )
            }
        }



        // Save Button
        Button(
            onClick = {
                val weight = weightStr.toFloatOrNull()
                val height = heightStr.toFloatOrNull()
                val age = ageStr.toIntOrNull()
                val calories = calGoalStr.toIntOrNull()
                val protein = proteinGoalStr.toIntOrNull()
                val carbs = carbsGoalStr.toIntOrNull()
                val fat = fatGoalStr.toIntOrNull()

                if (weight != null && height != null && age != null && calories != null && protein != null && carbs != null && fat != null &&
                    weight > 0 && height > 0 && age > 0 && calories > 0 && protein > 0 && carbs > 0 && fat > 0
                ) {
                    isError = false

                    onSave(
                        UserProfile(
                            weight = weight,
                            height = height,
                            age = age,
                            gender = genderOption,
                            goalType = goalOption,
                            calGoal = calories,
                            proteinGoal = protein,
                            carbsGoal = carbs,
                            fatGoal = fat,
                            defaultPlateSize = plateSize
                        )
                    )
                } else {
                    isError = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "Save Configurations",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
