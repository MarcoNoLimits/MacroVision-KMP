package com.fitter.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.dp
import com.fitter.app.ads.AdBanner
import com.fitter.app.ui.components.FitterTextField
import com.fitter.app.ui.components.PressableBox
import com.fitter.app.ui.components.calculateBmr
import com.fitter.app.ui.components.plateSizeInchesToCmString
import com.fitter.app.ui.theme.*
import com.fitter.shared.model.UserProfile

private const val CM_PER_INCH = 2.54f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    userEmail: String? = null,
    onNavigateToAuth: () -> Unit = {},
    onSignOut: () -> Unit = {},
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

    Scaffold(
        bottomBar = {
            AdBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .navigationBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp)
            .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Settings Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // F4.1: ArrowBack replaces Close ✕ glyph
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp) // F0.3: ≥44dp
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, BorderColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack, // F4.1
                    contentDescription = "Back",
                    tint = TextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // F2.1: ScreenTitle replaces 22.sp
            Text(
                text = "Goals & Parameters",
                style = BrandTypography.ScreenTitle,
                color = TextColor
            )
        }

        // Section 0: Account Card (A1)
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
                    text = "ACCOUNT",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )

                if (userEmail.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Guest Mode",
                            style = BrandTypography.SectionTitle,
                            color = TextColor
                        )
                        Text(
                            text = "Using Fitter as a guest. Sign in with email to back up and sync your meals and goals across devices — login is optional.",
                            style = BrandTypography.BodySmall,
                            color = TextColor
                        )
                    }

                    Button(
                        onClick = onNavigateToAuth,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        shape = RoundedCornerShape(RadiusM),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .shadow(2.dp, RoundedCornerShape(RadiusM))
                    ) {
                        Text(
                            text = "Sign In / Create Account",
                            style = BrandTypography.CardTitle,
                            color = Color.White
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Signed in as",
                            style = BrandTypography.BodySmall,
                            color = MutedTextColor
                        )
                        Text(
                            text = userEmail,
                            style = BrandTypography.SectionTitle,
                            color = TextColor
                        )
                        Text(
                            text = "Cloud sync active across devices.",
                            style = BrandTypography.BodySmall,
                            color = TextColor
                        )
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(RadiusM),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Sign Out",
                            style = BrandTypography.CardTitle,
                            color = TextColor
                        )
                    }
                }
            }
        }

        // Section 1: Body Parameters Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(RadiusL), // F1.2: was 24.dp → RadiusL
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
                    text = "BODY PARAMETERS",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FitterTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it },
                        label = "Height",
                        unit = "cm",
                        modifier = Modifier.weight(1f)
                    )
                    FitterTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = "Weight",
                        unit = "kg",
                        modifier = Modifier.weight(1f)
                    )
                }

                FitterTextField(
                    value = ageStr,
                    onValueChange = { ageStr = it },
                    label = "Age",
                    unit = "yrs",
                    modifier = Modifier.fillMaxWidth()
                )

                // F4.3: Gender chips — dedicated full-width 50/50 segmented row so labels never wrap
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Gender",
                        style = BrandTypography.BodySmall,
                        color = MutedTextColor,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("Male", "Female").forEach { g ->
                            val selected = genderOption == g
                            FilterChip(
                                selected = selected,
                                onClick = { genderOption = g },
                                label = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = g,
                                            style = BrandTypography.BodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                },
                                // F4.3: selected state conveyed via FilterChip selected param
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryAccent,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceTint, // F1.1: was Color(0xFFF1F5F9)
                                    labelColor = TextColor
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = BorderColor,
                                    selectedBorderColor = PrimaryAccent,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.5.dp,
                                    enabled = true,
                                    selected = selected
                                ),
                                shape = RoundedCornerShape(RadiusS),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp) // F0.3: ≥44dp
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Goal Type Card
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "FITNESS GOAL",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )

                // F4.3: Goal chips — FilterChip with full semantics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Lose Weight", "Maintain", "Gain Muscle").forEach { gType ->
                        val selected = goalOption == gType
                        FilterChip(
                            selected = selected,
                            onClick = { goalOption = gType },
                            label = {
                                Text(
                                    text = gType,
                                    style = BrandTypography.BodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryAccent,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceTint, // F1.1: was Color(0xFFF1F5F9)
                                labelColor = TextColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = BorderColor,
                                selectedBorderColor = PrimaryAccent,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 2.dp,
                                enabled = true,
                                selected = selected
                            ),
                            modifier = Modifier.heightIn(min = 44.dp) // F0.3
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Suggestion Prompt Box — F3.1: PressableBox replaces .clickable
                // F1.1: InfoPanel replaces Color(0xFFF8FAFC)
                PressableBox(
                    onTap = {
                        calGoalStr = suggestedCal.toString()
                        proteinGoalStr = ((suggestedCal * 0.30f) / 4f).toInt().toString()
                        carbsGoalStr = ((suggestedCal * 0.45f) / 4f).toInt().toString()
                        fatGoalStr = ((suggestedCal * 0.25f) / 9f).toInt().toString()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InfoPanel, RoundedCornerShape(RadiusM)) // F1.1+F1.2
                        .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "METABOLIC SUGGESTION",
                            style = BrandTypography.CardTitle,
                            color = PrimaryAccent
                        )
                        Text(
                            text = "Based on BMR, your recommended intake is $suggestedCal kcal.",
                            style = BrandTypography.BodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                        Text(
                            text = "👉 Tap here to automatically apply Calorie & Macro splits (30% Protein, 45% Carbs, 25% Fat).",
                            style = BrandTypography.BodySmall,
                            color = TextColor
                        )
                    }
                }
            }
        }

        // Section: Plate Settings Card
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
                    text = "PLATE SETTINGS",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Default Plate Size",
                        style = BrandTypography.SectionTitle,
                        color = TextColor
                    )
                    Box(
                        modifier = Modifier
                            .background(SurfaceTint, RoundedCornerShape(RadiusM))
                            .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${plateSizeInchesToCmString(plateSize)} cm",
                            style = BrandTypography.SectionTitle,
                            color = PrimaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Slider(
                    value = plateSize,
                    onValueChange = {
                        val cm = it * CM_PER_INCH
                        val snappedCm = kotlin.math.round(cm * 2.0f) / 2.0f
                        plateSize = snappedCm / CM_PER_INCH
                    },
                    valueRange = (16.0f / CM_PER_INCH)..(31.0f / CM_PER_INCH),
                    colors = SliderDefaults.colors(
                        activeTrackColor = PrimaryAccent,
                        inactiveTrackColor = BorderColor,
                        thumbColor = PrimaryAccent
                    ),
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = remember { MutableInteractionSource() },
                            colors = SliderDefaults.colors(thumbColor = PrimaryAccent),
                            modifier = Modifier.shadow(1.dp, CircleShape)
                        )
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "16 cm",
                        style = BrandTypography.Micro,
                        color = MutedTextColor
                    )
                    Text(
                        text = "31 cm",
                        style = BrandTypography.Micro,
                        color = MutedTextColor
                    )
                }
            }
        }

        // Section 3: Daily Target Goals Override
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
                    text = "CUSTOM TARGETS",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )

                FitterTextField(
                    value = calGoalStr,
                    onValueChange = { calGoalStr = it },
                    label = "Calories Budget",
                    unit = "kcal",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FitterTextField(
                        value = proteinGoalStr,
                        onValueChange = { proteinGoalStr = it },
                        label = "Protein",
                        unit = "g",
                        modifier = Modifier.weight(1f)
                    )
                    FitterTextField(
                        value = carbsGoalStr,
                        onValueChange = { carbsGoalStr = it },
                        label = "Carbs",
                        unit = "g",
                        modifier = Modifier.weight(1f)
                    )
                    FitterTextField(
                        value = fatGoalStr,
                        onValueChange = { fatGoalStr = it },
                        label = "Fat",
                        unit = "g",
                        modifier = Modifier.weight(1f)
                    )
                }
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
                    // F1.1: DangerColor replaces Color(0xFFEF4444)
                    tint = DangerColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Please enter valid numeric goals and parameters.",
                    style = BrandTypography.BodySmall,
                    // F1.1: DangerColor
                    color = DangerColor,
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
                            // G2: stored in in.; VLM API expects in. (analyzeMealImage)
                            defaultPlateSize = plateSize
                        )
                    )
                } else {
                    isError = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            shape = RoundedCornerShape(RadiusM), // F1.2: was 16.dp → RadiusM
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(2.dp, RoundedCornerShape(RadiusM))
        ) {
            Text(
                text = "Save Configurations",
                style = BrandTypography.CardTitle,
                color = Color.White
            )
        }
    }
    }
}
