package com.fitcal.app.ui.screens.settings

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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ads.AdBanner
import com.fitcal.app.notifications.MealReminder
import com.fitcal.app.notifications.MealReminderManager
import com.fitcal.app.ui.components.FitCalTextField
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.components.calculateBmr
import com.fitcal.app.ui.components.plateSizeInchesToCmString
import com.fitcal.app.privacy.PrivacyConsent
import com.fitcal.app.ui.theme.*
import com.fitcal.shared.model.UserProfile

private const val CM_PER_INCH = 2.54f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    userEmail: String? = null,
    onNavigateToAuth: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSave: (UserProfile) -> Unit,
    onBack: () -> Unit,
    onDeleteAllData: (() -> Unit)? = null,
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
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

    var remindersEnabled by remember { mutableStateOf(MealReminderManager.isMasterEnabled()) }
    var mealReminders by remember { mutableStateOf(MealReminderManager.getReminders()) }
    var editingReminder by remember { mutableStateOf<MealReminder?>(null) }
    var isAddingNewReminder by remember { mutableStateOf(false) }

    // GDPR Art. 7(3) — consent must be as easy to withdraw as to give.
    var personalizedAds by remember { mutableStateOf(PrivacyConsent.isAdsPersonalizationEnabled()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                            text = "Using FitCal as a guest. Sign in with email to back up and sync your meals and goals across devices — login is optional.",
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
                    FitCalTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it },
                        label = "Height",
                        unit = "cm",
                        modifier = Modifier.weight(1f)
                    )
                    FitCalTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = "Weight",
                        unit = "kg",
                        modifier = Modifier.weight(1f)
                    )
                }

                FitCalTextField(
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

                FitCalTextField(
                    value = calGoalStr,
                    onValueChange = { calGoalStr = it },
                    label = "Calories Budget",
                    unit = "kcal",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FitCalTextField(
                        value = proteinGoalStr,
                        onValueChange = { proteinGoalStr = it },
                        label = "Protein",
                        unit = "g",
                        modifier = Modifier.weight(1f)
                    )
                    FitCalTextField(
                        value = carbsGoalStr,
                        onValueChange = { carbsGoalStr = it },
                        label = "Carbs",
                        unit = "g",
                        modifier = Modifier.weight(1f)
                    )
                    FitCalTextField(
                        value = fatGoalStr,
                        onValueChange = { fatGoalStr = it },
                        label = "Fat",
                        unit = "g",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section 4: Meal Scan Reminders Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MEAL SCAN REMINDERS",
                            style = BrandTypography.CardTitle,
                            color = MutedTextColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Daily notifications around meal times to remind you to scan your food.",
                            style = BrandTypography.BodySmall,
                            color = TextColor
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { enabled ->
                            remindersEnabled = enabled
                            MealReminderManager.setMasterEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryAccent
                        )
                    )
                }

                if (remindersEnabled) {
                    mealReminders.forEach { reminder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceTint, RoundedCornerShape(RadiusM))
                                .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PressableBox(
                                onTap = {
                                    isAddingNewReminder = false
                                    editingReminder = reminder
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = reminder.label,
                                        style = BrandTypography.SectionTitle,
                                        color = if (reminder.enabled) TextColor else MutedTextColor
                                    )
                                    Text(
                                        text = "${MealReminderManager.formatTime12Hour(reminder.hour, reminder.minute)} · Tap to change time",
                                        style = BrandTypography.BodySmall,
                                        color = if (reminder.enabled) PrimaryAccent else MutedTextColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Switch(
                                    checked = reminder.enabled,
                                    onCheckedChange = { checked ->
                                        mealReminders = MealReminderManager.updateReminder(
                                            id = reminder.id,
                                            label = reminder.label,
                                            hour = reminder.hour,
                                            minute = reminder.minute,
                                            enabled = checked
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PrimaryAccent
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        mealReminders = MealReminderManager.removeReminder(reminder.id)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove ${reminder.label} reminder",
                                        tint = MutedTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isAddingNewReminder = true
                            editingReminder = MealReminder(
                                id = "new",
                                label = "Afternoon Snack",
                                hour = 16,
                                minute = 0,
                                enabled = true
                            )
                        },
                        shape = RoundedCornerShape(RadiusM),
                        border = BorderStroke(1.dp, PrimaryAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = PrimaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Meal Reminder",
                            color = PrimaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── Privacy & Data (GDPR Arts. 7(3), 15, 17) ───────────────────────────
        // Withdrawable consent + erasure. Both are legally mandatory; without this
        // block the app cannot lawfully claim a valid GDPR compliance posture.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Privacy & Data",
                style = BrandTypography.SectionTitle,
                color = TextColor,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Personalized ads",
                        style = BrandTypography.SectionTitle,
                        color = TextColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (personalizedAds) {
                            "On — ad partners may tailor ads to your activity."
                        } else {
                            "Off — ads are non-personalized. Free tier stays fully functional."
                        },
                        style = BrandTypography.BodySmall,
                        color = MutedTextColor,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = personalizedAds,
                    onCheckedChange = { enabled ->
                        personalizedAds = enabled
                        if (enabled) {
                            PrivacyConsent.recordDecision(
                                acceptTerms = true,
                                adsPersonalized = true,
                                timestampMs = System.currentTimeMillis(),
                            )
                        } else {
                            PrivacyConsent.revokeOptionalConsent()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CardBackground,
                        checkedTrackColor = PrimaryAccent,
                        uncheckedThumbColor = CardBackground,
                        uncheckedTrackColor = BorderColor,
                    ),
                )
            }

            // Legal documents — required by Google Play, the App Store, and AdMob policy review.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenPrivacyPolicy,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderColor),
                ) {
                    Text("Privacy Policy", fontSize = 13.sp, color = TextColor)
                }
                OutlinedButton(
                    onClick = onOpenTerms,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderColor),
                ) {
                    Text("Terms of Service", fontSize = 13.sp, color = TextColor)
                }
            }

            // Erasure — GDPR Art. 17 right to be forgotten.
            if (onDeleteAllData != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerColor),
                ) {
                    Text("Delete my account and data", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Medical disclaimer — visible in settings, not only at first launch.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DangerSoft, RoundedCornerShape(12.dp))
                    .border(1.dp, DangerBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        text = "Not medical advice",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerTextStrong,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "All nutrition values in FitCal are automated estimates and are " +
                            "approximate. They are not medical advice and do not replace a " +
                            "registered dietitian or physician. Do not use FitCal to manage " +
                            "diabetes, eating disorders, allergies, pregnancy, or any medical " +
                            "condition.",
                        fontSize = 12.sp,
                        color = DangerTextStrong,
                        lineHeight = 17.sp,
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
                    MealReminderManager.syncNotifications()
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

    if (showDeleteConfirm && onDeleteAllData != null) {
        DeleteDataConfirmDialog(
            onConfirm = {
                showDeleteConfirm = false
                PrivacyConsent.clearAll()
                onDeleteAllData()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    editingReminder?.let { target ->
        MealReminderEditorDialog(
            initialReminder = target,
            isNew = isAddingNewReminder,
            onDismiss = {
                editingReminder = null
                isAddingNewReminder = false
            },
            onConfirm = { label, hour, minute ->
                mealReminders = if (isAddingNewReminder) {
                    MealReminderManager.addReminder(label = label, hour = hour, minute = minute)
                } else {
                    MealReminderManager.updateReminder(
                        id = target.id,
                        label = label,
                        hour = hour,
                        minute = minute,
                        enabled = target.enabled
                    )
                }
                editingReminder = null
                isAddingNewReminder = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealReminderEditorDialog(
    initialReminder: MealReminder,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (label: String, hour: Int, minute: Int) -> Unit
) {
    var labelText by remember(initialReminder) { mutableStateOf(initialReminder.label) }
    val timePickerState = rememberTimePickerState(
        initialHour = initialReminder.hour.coerceIn(0, 23),
        initialMinute = initialReminder.minute.coerceIn(0, 59),
        is24Hour = false
    )
    val presetLabels = listOf("Breakfast", "Lunch", "Snack", "Dinner")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Text(
                text = if (isNew) "Add Meal Reminder" else "Edit Meal Reminder",
                style = BrandTypography.SectionTitle,
                color = TextColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("Meal Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetLabels.forEach { preset ->
                        FilterChip(
                            selected = labelText.equals(preset, ignoreCase = true),
                            onClick = { labelText = preset },
                            label = {
                                Text(
                                    text = preset,
                                    style = BrandTypography.Micro,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                TimeInput(state = timePickerState)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        labelText.trim().ifEmpty { "Meal" },
                        timePickerState.hour,
                        timePickerState.minute
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(RadiusM)
            ) {
                Text(if (isNew) "Add Reminder" else "Save Time", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Text("Cancel", color = TextColor)
            }
        }
    )
}

/**
 * GDPR Art. 17 confirmation. Erasure is irreversible, so it requires an explicit
 * confirm step distinct from the button that opened it — and the action must not
 * be discoverable by accident (one tap to open, second deliberate tap to execute).
 */
@Composable
private fun DeleteDataConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all data?", color = TextColor) },
        text = {
            Text(
                "This permanently deletes your account, meal photos, nutrition history, " +
                    "water logs and settings from our servers and this device. It cannot be " +
                    "undone. Your Premium subscription will not be refunded.",
                color = TextColor,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                shape = RoundedCornerShape(RadiusM),
            ) {
                Text("Delete everything", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(RadiusM),
                border = BorderStroke(1.dp, BorderColor),
            ) {
                Text("Cancel", color = TextColor)
            }
        },
    )
}

