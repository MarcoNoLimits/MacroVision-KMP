package com.fitcal.app.ui.screens.dashboard

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fitcal.app.ads.AdBanner
import com.fitcal.app.getCurrentDateString
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.components.calculateBmr
import com.fitcal.app.ui.components.getAiCoachFeedback
import com.fitcal.app.ui.screens.dashboard.components.CalendarStrip
import com.fitcal.app.ui.screens.dashboard.components.CircularCalorieProgressRing
import com.fitcal.app.ui.screens.dashboard.components.DashboardMacroCard
import com.fitcal.app.ui.screens.dashboard.components.WaterTrackerCard
import com.fitcal.app.ui.theme.*
import com.fitcal.shared.model.LoggedMeal
import com.fitcal.shared.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    meals: List<LoggedMeal>,
    profile: UserProfile,
    selectedDate: String,
    waterLogged: Int,
    scansRemaining: Int,
    onDateSelected: (String) -> Unit,
    onWaterChanged: (Int) -> Unit,
    onScanClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onMonetizationClicked: () -> Unit,
    onOpenFoodLibrary: () -> Unit = {},
    onDeleteMeal: (LoggedMeal) -> Unit,
    onRestoreMeal: (LoggedMeal) -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // F0.2 — One-undo-slot: stores the deleted meal + its position in filteredMeals.
    // A second delete while a slot is pending replaces it (oldest delete is NOT kept,
    // per spec: "one undo slot only").
    var pendingUndoMeal by remember { mutableStateOf<Pair<LoggedMeal, Int>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Filter meals for the selected date
    val filteredMeals = remember(meals, selectedDate) {
        meals.filter {
            it.date == selectedDate || (it.date.isBlank() && selectedDate == getCurrentDateString())
        }
    }

    // Aggregate values
    val totalCalories = filteredMeals.sumOf { it.calories }
    val totalProtein = filteredMeals.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs = filteredMeals.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat = filteredMeals.sumOf { it.fat.toDouble() }.toFloat()

    val caloriePercent = if (profile.calGoal > 0) ((totalCalories.toFloat() / profile.calGoal) * 100).toInt() else 0
    val proteinPercent = if (profile.proteinGoal > 0) ((totalProtein / profile.proteinGoal) * 100).toInt() else 0
    val carbsPercent = if (profile.carbsGoal > 0) ((totalCarbs / profile.carbsGoal) * 100).toInt() else 0
    val fatPercent = if (profile.fatGoal > 0) ((totalFat / profile.fatGoal) * 100).toInt() else 0

    // Dynamic AI Coach Comments
    val aiCoachFeedback = remember(totalCalories, totalProtein, totalCarbs, totalFat, profile) {
        getAiCoachFeedback(totalProtein, totalCarbs, totalFat, totalCalories, profile)
    }

    // Calculate BMR
    val bmr = remember(profile) { calculateBmr(profile) }
    val activeBurnMultiplier = 1.2f
    val maintenanceCal = (bmr * activeBurnMultiplier).toInt()
    val deficitBalance = maintenanceCal - totalCalories

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Row: Today + Settings Gear Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // F2.1 ScreenTitle replaces hardcoded 28.sp
                    Text(
                        text = if (selectedDate == getCurrentDateString()) "Today" else "Log History",
                        style = BrandTypography.ScreenTitle,
                        color = TextColor
                    )
                    // F2.2 BodySmall (12sp) is fine for MutedTextColor
                    Text(
                        text = "FitCal Wellness Dashboard",
                        style = BrandTypography.BodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MutedTextColor
                    )
                }

                Row {
                    // Food Library — browsable reference content. First-class surface
                    // (not buried in Settings): AdMob rejected earlier builds partly for thin
                    // content, and this is genuine user value independent of scanning.
                    IconButton(
                        onClick = onOpenFoodLibrary,
                        modifier = Modifier
                            .size(44.dp) // F0.3: min 44dp hit area
                            .shadow(1.dp, CircleShape)
                            .background(Color.White, CircleShape)
                            .border(1.dp, BorderColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Food Library",
                            tint = TextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Ads & Perks - monetization is a first-class surface, NOT buried in Settings.
                    // Star icon opens the dedicated Ads & Perks screen (quota, rewarded ads, provider).
                    IconButton(
                        onClick = onMonetizationClicked,
                        modifier = Modifier
                            .size(44.dp) // F0.3: min 44dp hit area
                            .shadow(1.dp, CircleShape)
                            .background(Color.White, CircleShape)
                            .border(1.dp, BorderColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Ads & Perks",
                            tint = TextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onSettingsClicked,
                        modifier = Modifier
                            .size(44.dp) // F0.3: min 44dp hit area
                            .shadow(1.dp, CircleShape)
                            .background(Color.White, CircleShape)
                            .border(1.dp, BorderColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Calendar Day Selection Strip
            CalendarStrip(
                selectedDate = selectedDate,
                onDateSelected = onDateSelected
            )

            // Calorie Progress Circular Ring
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(RadiusXL), // F1.2: was 28.dp → RadiusXL=32
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(RadiusXL))
                    .border(1.dp, BorderColor, RoundedCornerShape(RadiusXL))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CircularCalorieProgressRing(
                        consumed = totalCalories,
                        goal = profile.calGoal
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // F2.1 Eyebrow replaces 10.sp hardcode
                        Text(
                            text = "CALORIE STATS",
                            style = BrandTypography.Eyebrow,
                            color = MutedTextColor
                        )

                        Column {
                            Text(
                                text = "Consumed",
                                style = BrandTypography.BodySmall,
                                color = MutedTextColor
                            )
                            Text(
                                text = "$totalCalories kcal",
                                style = BrandTypography.KpiNumeral,
                                color = TextColor
                            )
                        }

                        Column {
                            Text(
                                text = "Daily Budget",
                                style = BrandTypography.BodySmall,
                                color = MutedTextColor
                            )
                            Text(
                                text = "${profile.calGoal} kcal",
                                style = BrandTypography.KpiNumeral,
                                color = PrimaryAccent
                            )
                        }

                        Column {
                            Text(
                                text = "BMR Deficit",
                                style = BrandTypography.BodySmall,
                                color = MutedTextColor
                            )
                            Text(
                                text = if (deficitBalance >= 0) "$deficitBalance kcal deficit" else "${-deficitBalance} kcal surplus",
                                style = BrandTypography.BodySmall,
                                fontWeight = FontWeight.Bold,
                                // F1.1: was Color(0xFFEF4444) → DangerColor
                                color = if (deficitBalance >= 0) PrimaryAccent else DangerColor
                            )
                        }
                    }
                }
            }

            // Horizontal Macro strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DashboardMacroCard("Protein", "${totalProtein.toInt()}g", "$proteinPercent%", ProteinColor)
                }
                Box(modifier = Modifier.weight(1f)) {
                    DashboardMacroCard("Carbs", "${totalCarbs.toInt()}g", "$carbsPercent%", CarbsColor)
                }
                Box(modifier = Modifier.weight(1f)) {
                    DashboardMacroCard("Fats", "${totalFat.toInt()}g", "$fatPercent%", FatColor)
                }
            }

            // Water Intake Logger
            WaterTrackerCard(
                waterLogged = waterLogged,
                onWaterChanged = onWaterChanged
            )

            // F1.1 + F2.1: AI Coach card — was Color(0xFFF8FAFC)/9.sp → InfoPanel/Eyebrow
            Card(
                colors = CardDefaults.cardColors(containerColor = InfoPanel),
                shape = RoundedCornerShape(RadiusL), // F1.2: was 20.dp → RadiusL=24
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🧠", fontSize = 28.sp) // non-interactive decoration
                    Column {
                        // F2.1: Eyebrow (10sp) replaces 9.sp — 9sp removed per spec
                        Text(
                            text = "AI COACH ADVICE",
                            style = BrandTypography.Eyebrow,
                            color = PrimaryAccent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = aiCoachFeedback,
                            style = BrandTypography.BodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextColor
                        )
                    }
                }
            }

            // Hero Camera Preview Card
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SNAP AND LOG",
                    style = BrandTypography.Eyebrow,
                    color = MutedTextColor
                )
                // F3.1: PressableBox replaces bare .clickable
                PressableBox(
                    onTap = onScanClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(RadiusXL)) // F1.2
                        // F1.1: was Color(0xFF0F172A) → TextColor (same value; token is semantic)
                        .background(TextColor)
                        // F1.1: was Color(0xFFF1F5F9) → SurfaceTint
                        .border(4.dp, SurfaceTint, RoundedCornerShape(RadiusXL))
                ) {
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&q=80&w=1000",
                        contentDescription = "Camera feed simulation",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )

                    // Viewfinder brackets — non-interactive decorations; RadiusXS for bracket corners
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(24.dp)
                                .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(topStart = RadiusXS))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(topEnd = RadiusXS))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .size(24.dp)
                                .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(bottomStart = RadiusXS))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(bottomEnd = RadiusXS))
                        )
                    }

                    // Active Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 20.dp)
                            .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        // F2.1: Micro(11sp) instead of 9.sp; removed sub-10sp from this badge
                        Text(
                            text = "FitCal Active • $scansRemaining left",
                            color = Color.White,
                            style = BrandTypography.Micro,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Capture CTA Button layout — visual only (outer PressableBox handles tap)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                            .size(68.dp)
                            .background(Color.White, CircleShape)
                            // F1.1: was Color(0xFF0F172A) → TextColor token
                            .border(4.dp, TextColor, CircleShape)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PrimaryAccent, CircleShape)
                        )
                    }
                }
            }

            // Logged Meals History List
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "LOGGED HISTORY",
                    style = BrandTypography.Eyebrow,
                    color = MutedTextColor
                )

                if (filteredMeals.isEmpty()) {
                    // F5.1 — Friendly empty state with CTA
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, RoundedCornerShape(RadiusL))
                            .background(Color.White, RoundedCornerShape(RadiusL))
                            .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "🍽️", fontSize = 48.sp) // non-interactive decoration
                            Text(
                                text = "No meals yet",
                                style = BrandTypography.SectionTitle,
                                fontWeight = FontWeight.Bold,
                                color = TextColor,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Snap a photo of your next meal and FitCal will log it for you.",
                                style = BrandTypography.BodySmall,
                                color = MutedTextColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onScanClicked,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                                shape = RoundedCornerShape(RadiusM)
                            ) {
                                Text(
                                    text = "Start Scanning",
                                    style = BrandTypography.Body,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    filteredMeals.forEachIndexed { filteredIndex, meal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(1.dp, RoundedCornerShape(RadiusM))
                                .background(Color.White, RoundedCornerShape(RadiusM))
                                .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val emoji = remember(meal.name) {
                                val lower = meal.name.lowercase()
                                when {
                                    lower.contains("salad") || lower.contains("bowl") || lower.contains("quinoa") || lower.contains("avocado") -> "🥗"
                                    lower.contains("salmon") || lower.contains("fish") || lower.contains("seafood") -> "🐟"
                                    lower.contains("egg") -> "🍳"
                                    lower.contains("steak") || lower.contains("beef") || lower.contains("pork") || lower.contains("meat") -> "🥩"
                                    lower.contains("chicken") || lower.contains("turkey") || lower.contains("poultry") -> "🍗"
                                    lower.contains("fruit") || lower.contains("apple") || lower.contains("banana") -> "🍎"
                                    else -> "🍱"
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    // F1.1: was Color(0xFFF1F5F9) → SurfaceTint
                                    .background(SurfaceTint, CircleShape)
                            ) {
                                Text(text = emoji, fontSize = 24.sp) // non-interactive decoration
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meal.name,
                                    style = BrandTypography.SectionTitle,
                                    color = TextColor
                                )
                                Text(
                                    text = "${meal.timestamp} | P: ${meal.protein.toInt()}g C: ${meal.carbs.toInt()}g F: ${meal.fat.toInt()}g",
                                    // F2.2: Micro (11sp) at MutedTextColor — 11sp >= 12sp threshold? No — spec says nothing below 12sp
                                    // in MutedTextColor. Substitution: use BodySmall (12sp) for this secondary line.
                                    style = BrandTypography.BodySmall,
                                    color = MutedTextColor
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${meal.calories} kcal",
                                    style = BrandTypography.SectionTitle,
                                    color = TextColor
                                )

                                // F0.3: wrap delete icon in IconButton (≥44dp hit area)
                                // F0.2: onDeleteMeal now receives the meal object; undo slot updated
                                IconButton(
                                    onClick = {
                                        val position = filteredIndex
                                        pendingUndoMeal = Pair(meal, position)
                                        onDeleteMeal(meal)
                                        // Auto-dismiss snackbar after 5s; the LaunchedEffect below handles it
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Meal deleted",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short // ~4s — closest to 5s in M3
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                val toRestore = pendingUndoMeal
                                                if (toRestore != null) {
                                                    onRestoreMeal(toRestore.first)
                                                    pendingUndoMeal = null
                                                }
                                            } else {
                                                pendingUndoMeal = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(44.dp) // F0.3: ≥44dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete meal ${meal.name}",
                                        // F1.1: was Color(0xFFEF4444) → DangerColor
                                        tint = DangerColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
