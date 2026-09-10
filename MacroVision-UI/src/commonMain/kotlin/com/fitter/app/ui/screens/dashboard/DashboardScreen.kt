package com.fitter.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fitter.app.ads.AdBanner
import com.fitter.app.getCurrentDateString
import com.fitter.app.ui.components.calculateBmr
import com.fitter.app.ui.components.getAiCoachFeedback
import com.fitter.app.ui.screens.dashboard.components.CalendarStrip
import com.fitter.app.ui.screens.dashboard.components.CircularCalorieProgressRing
import com.fitter.app.ui.screens.dashboard.components.DashboardMacroCard
import com.fitter.app.ui.screens.dashboard.components.WaterTrackerCard
import com.fitter.app.ui.theme.*
import com.fitter.shared.model.LoggedMeal
import com.fitter.shared.model.UserProfile

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
    onDeleteMeal: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

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
    val bmr = remember(profile) {
        calculateBmr(profile)
    }
    // Net Calorie Balance Status
    val activeBurnMultiplier = 1.2f // Sedentary activity standard multiplier
    val maintenanceCal = (bmr * activeBurnMultiplier).toInt()
    val deficitBalance = maintenanceCal - totalCalories

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header Row: Today + Settings Gear Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (selectedDate == getCurrentDateString()) "Today" else "Log History",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
                Text(
                    text = "Fitter Wellness Dashboard",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MutedTextColor
                )
            }

            IconButton(
                onClick = onSettingsClicked,
                modifier = Modifier
                    .size(40.dp)
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

        // Calendar Day Selection Strip (Horizontal slider)
        CalendarStrip(
            selectedDate = selectedDate,
            onDateSelected = onDateSelected
        )

        // Calorie Progress Circular Canvas Ring
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(28.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(28.dp))
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

                // Calorie Target details pane
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "CALORIE STATS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedTextColor,
                        letterSpacing = 1.sp
                    )

                    Column {
                        Text(
                            text = "Consumed",
                            fontSize = 12.sp,
                            color = MutedTextColor
                        )
                        Text(
                            text = "$totalCalories kcal",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextColor
                        )
                    }

                    Column {
                        Text(
                            text = "Daily Budget",
                            fontSize = 12.sp,
                            color = MutedTextColor
                        )
                        Text(
                            text = "${profile.calGoal} kcal",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryAccent
                        )
                    }

                    // Calorie balance indicator based on BMR
                    Column {
                        Text(
                            text = "BMR Deficit",
                            fontSize = 12.sp,
                            color = MutedTextColor
                        )
                        Text(
                            text = if (deficitBalance >= 0) "$deficitBalance kcal deficit" else "${-deficitBalance} kcal surplus",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (deficitBalance >= 0) PrimaryAccent else Color(0xFFEF4444)
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

        // Dynamic AI Coach comments Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🧠", fontSize = 28.sp)
                Column {
                    Text(
                        text = "AI COACH ADVICE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = aiCoachFeedback,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextColor
                    )
                }
            }
        }

        // Hero Camera Preview Card (Viewfinder card)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SNAP AND LOG",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MutedTextColor,
                letterSpacing = 1.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF0F172A))
                    .border(4.dp, Color(0xFFF1F5F9), RoundedCornerShape(32.dp))
                    .clickable { onScanClicked() }
            ) {
                // Background Image simulation
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&q=80&w=1000",
                    contentDescription = "Camera feed simulation",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f
                )

                // Viewfinder brackets
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(24.dp)
                            .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(topStart = 8.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(topEnd = 8.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(24.dp)
                            .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(bottomStart = 8.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(bottomEnd = 8.dp))
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
                    Text(
                        text = "Fitter Active • $scansRemaining left",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Capture CTA Button layout
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .size(68.dp)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color(0xFF0F172A), CircleShape)
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MutedTextColor,
                letterSpacing = 1.sp
            )

            if (filteredMeals.isEmpty()) {
                // Empty State card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(20.dp))
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No meals logged for this day. Tap capture to record nutrition!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MutedTextColor,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                filteredMeals.forEach { meal ->
                    val originalIndex = meals.indexOfFirst { it.id == meal.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, RoundedCornerShape(16.dp))
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
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
                                .background(Color(0xFFF1F5F9), CircleShape)
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = meal.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextColor
                            )
                            Text(
                                text = "${meal.timestamp} | P: ${meal.protein.toInt()}g C: ${meal.carbs.toInt()}g F: ${meal.fat.toInt()}g",
                                fontSize = 11.sp,
                                color = MutedTextColor
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${meal.calories} kcal",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextColor
                            )
                            
                            if (originalIndex != -1) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteMeal(originalIndex) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Adaptive banner on passive tab
        AdBanner(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp))
    }
}
