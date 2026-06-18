package com.fitter.app

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.fitter.shared.api.NutritionClient
import com.fitter.shared.api.FailoverNutritionClient
import com.fitter.shared.api.FoodDatabase
import com.fitter.shared.api.FoodDbEntry
import com.fitter.shared.model.FoodItem
import com.fitter.shared.model.NutritionResponse
import com.fitter.shared.model.Totals
import io.ktor.util.encodeBase64
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Type-safe Navigation Destinations
@Serializable
object DashboardDestination

@Serializable
object CameraDestination

@Serializable
data class ResultDestination(val responseJson: String)

@Serializable
object SettingsDestination

// Theme Colors - Soft Premium Light Palette (Tailwind Slate / Emerald)
val BgColor = Color(0xFFF8FAFC)        // Slate 50
val CardBackground = Color(0xFFFFFFFF)  // White
val TextColor = Color(0xFF0F172A)       // Dark Slate (Slate 900)
val MutedTextColor = Color(0xFF64748B)  // Muted Slate (Slate 500)
val BorderColor = Color(0xFFE2E8F0)     // Slate 200

val PrimaryAccent = Color(0xFF10B981)   // Emerald Green (#10B981)
val SecondaryAccent = Color(0xFF64748B) // Slate 500

// Macro Dot colors
val ProteinColor = Color(0xFF34D399)    // Emerald 400
val CarbsColor = Color(0xFF60A5FA)      // Blue 400
val FatColor = Color(0xFFFBBF24)        // Amber 400

@Serializable
data class UserProfile(
    val weight: Float,
    val height: Float,
    val calGoal: Int,
    val proteinGoal: Int,
    val carbsGoal: Int,
    val fatGoal: Int,
    val age: Int = 25,
    val gender: String = "Male",
    val goalType: String = "Maintain",
    val defaultPlateSize: Float = 9.0f
)

@Serializable
data class LoggedMeal(
    val id: String,
    val name: String,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val timestamp: String,
    val date: String = "" // Stored date for multi-day history
)

@Serializable
data class LoggedMealsWrapper(
    val meals: List<LoggedMeal>
)

// Interactive wrapper model for weights
data class EditableFoodItem(
    val id: String,
    val name: String,
    val currentWeightStr: String,
    val calPerGram: Float,
    val proteinPerGram: Float,
    val carbsPerGram: Float,
    val fatPerGram: Float,
    val confidence: String
)

@Composable
fun App() {
    // Keep track of the last captured image bytes to render on ResultScreen
    var lastCapturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    // User profile state loaded from preferences
    val defaultProfile = UserProfile(
        weight = 70f,
        height = 175f,
        calGoal = 2000,
        proteinGoal = 57,
        carbsGoal = 173,
        fatGoal = 78,
        age = 25,
        gender = "Male",
        goalType = "Maintain"
    )
    val profileJson = loadPreference("user_profile", "")
    val userProfile = remember {
        val loaded = try {
            if (profileJson.isNotBlank()) {
                Json.decodeFromString<UserProfile>(profileJson)
            } else defaultProfile
        } catch (e: Exception) {
            defaultProfile
        }
        mutableStateOf(loaded)
    }

    // Logged meals state loaded from preferences
    val mealsJson = loadPreference("logged_meals", "[]")
    val loggedMeals = remember {
        val loadedList = try {
            if (mealsJson.isNotBlank() && mealsJson != "[]") {
                Json.decodeFromString<LoggedMealsWrapper>(mealsJson).meals
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        mutableStateListOf(*loadedList.toTypedArray())
    }

    // Dynamic water state map/loader
    var selectedDateKey by remember { mutableStateOf(getCurrentDateString()) }
    var waterLoggedToday by remember(selectedDateKey) {
        mutableStateOf(loadPreference("water_intake_$selectedDateKey", "0").toInt())
    }

    fun saveProfile(profile: UserProfile) {
        userProfile.value = profile
        try {
            savePreference("user_profile", Json.encodeToString(UserProfile.serializer(), profile))
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun saveMealsList(meals: List<LoggedMeal>) {
        try {
            savePreference(
                "logged_meals",
                Json.encodeToString(LoggedMealsWrapper.serializer(), LoggedMealsWrapper(meals))
            )
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryAccent,
            secondary = SecondaryAccent,
            background = BgColor,
            surface = CardBackground,
            onBackground = TextColor,
            onSurface = TextColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
        ) {
            val navController = rememberNavController()
            
            // Check API key configuration for OpenRouter, Gemini, and Groq.
            val openRouterKey = openRouterApiKey
            val geminiKey = geminiApiKey
            val groqKey = groqApiKey
            val isMockMode = (openRouterKey.isBlank() || openRouterKey == "your_openrouter_api_key_here") &&
                             (geminiKey.isBlank() || geminiKey == "your_gemini_api_key_here") &&
                             (groqKey.isBlank() || groqKey == "your_groq_api_key_here")
            val apiClient = remember(openRouterKey, geminiKey, groqKey) {
                FailoverNutritionClient(openRouterKey, geminiKey, groqKey)
            }

            NavHost(
                navController = navController,
                startDestination = DashboardDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<DashboardDestination> {
                    DashboardScreen(
                        meals = loggedMeals,
                        profile = userProfile.value,
                        selectedDate = selectedDateKey,
                        waterLogged = waterLoggedToday,
                        onDateSelected = { newDate ->
                            selectedDateKey = newDate
                        },
                        onWaterChanged = { newWater ->
                            waterLoggedToday = newWater
                            savePreference("water_intake_$selectedDateKey", newWater.toString())
                        },
                        onScanClicked = {
                            navController.navigate(CameraDestination)
                        },
                        onSettingsClicked = {
                            navController.navigate(SettingsDestination)
                        },
                        onDeleteMeal = { index ->
                            loggedMeals.removeAt(index)
                            saveMealsList(loggedMeals.toList())
                        }
                    )
                }

                composable<CameraDestination> {
                    CameraScreen(
                        apiClient = apiClient,
                        isMockMode = isMockMode,
                        plateSizeInches = userProfile.value.defaultPlateSize,
                        onPhotoCaptured = { bytes ->
                            lastCapturedImageBytes = bytes
                        },
                        onResultObtained = { responseJson ->
                            navController.navigate(ResultDestination(responseJson)) {
                                popUpTo(DashboardDestination) { saveState = false }
                            }
                        },
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable<ResultDestination> { backStackEntry ->
                    val destination = backStackEntry.toRoute<ResultDestination>()
                    val nutritionData = remember(destination.responseJson) {
                        Json.decodeFromString<NutritionResponse>(destination.responseJson)
                    }
                    ResultScreen(
                        apiClient = apiClient,
                        data = nutritionData,
                        isMock = isMockMode,
                        capturedImageBytes = lastCapturedImageBytes,
                        onMealLogged = { mealName, cal, p, c, f ->
                            val newMeal = LoggedMeal(
                                id = "${mealName}_${getCurrentTimeString()}_${loggedMeals.size}",
                                name = mealName,
                                calories = cal,
                                protein = p,
                                carbs = c,
                                fat = f,
                                timestamp = getCurrentTimeString(),
                                date = selectedDateKey
                            )
                            loggedMeals.add(newMeal)
                            saveMealsList(loggedMeals.toList())
                        },
                        onLogAgain = {
                            lastCapturedImageBytes = null
                            navController.navigate(DashboardDestination) {
                                popUpTo(DashboardDestination) { inclusive = true }
                            }
                        }
                    )
                }

                composable<SettingsDestination> {
                    SettingsScreen(
                        profile = userProfile.value,
                        onSave = { updated ->
                            saveProfile(updated)
                            navController.popBackStack()
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// 1. DashboardScreen — Today Intake & Log History
// ----------------------------------------------------
@Composable
fun DashboardScreen(
    meals: List<LoggedMeal>,
    profile: UserProfile,
    selectedDate: String,
    waterLogged: Int,
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
                    text = "MacroVision Wellness Dashboard",
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

        // Hero Camera Preview Card (Replicating viewfinder card in 01-dashboard.html)
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
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
                        text = "MacroVision Active",
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
                    // Meal card history item
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
                        // Emoji icon based on meal name keywords
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
    }
}

// ----------------------------------------------------
// Circular Canvas Ring component
// ----------------------------------------------------
@Composable
fun CircularCalorieProgressRing(
    consumed: Int,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val remaining = goal - consumed
    val isOver = remaining < 0
    val absRemaining = kotlin.math.abs(remaining)
    val fraction = if (goal > 0) (consumed.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f
    val sweepAngle = fraction * 360f

    val primaryColor = if (isOver) Color(0xFFEF4444) else Color(0xFF10B981)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(140.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw Background track circle
            drawCircle(
                color = Color(0xFFF1F5F9),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            // Draw Foreground arc
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$absRemaining",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOver) Color(0xFFEF4444) else TextColor
            )
            Text(
                text = if (isOver) "kcal over" else "kcal left",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isOver) Color(0xFFEF4444).copy(alpha = 0.8f) else MutedTextColor
            )
        }
    }
}

// ----------------------------------------------------
// Calendar Day Selector Strip
// ----------------------------------------------------
@Composable
fun CalendarStrip(
    selectedDate: String,
    onDateSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val days = remember { getLastSevenDays() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        days.forEach { (key, label) ->
            val isSelected = key == selectedDate
            val dayNumber = key.split("-").last()

            Box(
                modifier = Modifier
                    .width(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) PrimaryAccent else Color.White)
                    .border(1.dp, if (isSelected) PrimaryAccent else BorderColor, RoundedCornerShape(16.dp))
                    .clickable { onDateSelected(key) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else MutedTextColor
                    )
                    Text(
                        text = dayNumber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else TextColor
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// Water Tracker Card
// ----------------------------------------------------
@Composable
fun WaterTrackerCard(
    waterLogged: Int,
    onWaterChanged: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WATER INTAKE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8), // Light blue water accent
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "$waterLogged ml / 2000 ml",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Quick Increment Buttons
                    TextButton(
                        onClick = { if (waterLogged >= 250) onWaterChanged(waterLogged - 250) },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("-250ml", fontSize = 11.sp, color = MutedTextColor)
                    }
                    TextButton(
                        onClick = { onWaterChanged(waterLogged + 250) },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+250ml", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                }
            }

            // Interactive cup grid (taps set direct amounts)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 1..8) {
                    val cupLimit = i * 250
                    val isFilled = waterLogged >= cupLimit
                    Text(
                        text = "🥛",
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable {
                                if (isFilled) {
                                    onWaterChanged(cupLimit - 250)
                                } else {
                                    onWaterChanged(cupLimit)
                                }
                            }
                            .shadow(if (isFilled) 2.dp else 0.dp, CircleShape)
                            .alpha(if (isFilled) 1f else 0.3f)
                            .padding(2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardMacroCard(
    title: String,
    value: String,
    percent: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTextColor
                )
                Box(modifier = Modifier.size(5.dp).background(color, CircleShape))
            }
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextColor
            )
            Text(
                text = "$percent of goal",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = MutedTextColor
            )
        }
    }
}

// ----------------------------------------------------
// 2. CameraScreen — Capture and loading state
// ----------------------------------------------------
@Composable
fun CameraScreen(
    apiClient: NutritionClient,
    isMockMode: Boolean,
    plateSizeInches: Float?,
    onPhotoCaptured: (ByteArray) -> Unit,
    onResultObtained: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isAnalyzing && errorMessage == null) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onPhotoCaptured = { imageBytes ->
                    val compressedBytes = compressImage(imageBytes)
                    onPhotoCaptured(compressedBytes)
                    isAnalyzing = true
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
                            text = "MacroVision is calculating macro estimates and identifying ingredients from your capture.",
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
                            text = "Could not analyze image. Try again.",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextColor,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = if (isMockMode && errorText.contains("placeholder")) 
                                "Mock API Key issue." 
                            else 
                                errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedTextColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    onNavigateBack()
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Text("Cancel", color = TextColor)
                            }
                            
                            Button(
                                onClick = {
                                    errorMessage = null
                                    isAnalyzing = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 3. ResultScreen — Nutritional Cards (Mockup Replicated)
// ----------------------------------------------------
@Composable
fun ResultScreen(
    apiClient: NutritionClient,
    data: NutritionResponse,
    isMock: Boolean,
    capturedImageBytes: ByteArray?,
    onMealLogged: (String, Int, Float, Float, Float) -> Unit,
    onLogAgain: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    // Dynamic list of items to allow real-time editing & additions
    val editableItems = remember(data) {
        mutableStateListOf<EditableFoodItem>().apply {
            addAll(data.items.mapIndexed { index, item ->
                EditableFoodItem(
                    id = "${item.item}_$index",
                    name = item.item,
                    currentWeightStr = item.weight_est_g.toString(),
                    calPerGram = if (item.weight_est_g > 0) item.calories.toFloat() / item.weight_est_g else 0f,
                    proteinPerGram = if (item.weight_est_g > 0) item.protein_g / item.weight_est_g else 0f,
                    carbsPerGram = if (item.weight_est_g > 0) item.carbs_g / item.weight_est_g else 0f,
                    fatPerGram = if (item.weight_est_g > 0) item.fat_g / item.weight_est_g else 0f,
                    confidence = item.confidence
                )
            })
        }
    }

    var showAddItemDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var activeSwapIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDbItemDialog by remember { mutableStateOf(false) }

    val isEdited = remember(editableItems.map { it.name }) {
        if (editableItems.size != data.items.size) {
            true
        } else {
            editableItems.zip(data.items).any { (edited, original) ->
                edited.name.trim().lowercase() != original.item.trim().lowercase()
            }
        }
    }

    // Dynamic Calculations
    val totalCalories = editableItems.sumOf { 
        val weight = it.currentWeightStr.toIntOrNull() ?: 0
        (it.calPerGram * weight).toInt()
    }
    val totalProtein = editableItems.sumOf { 
        val weight = it.currentWeightStr.toIntOrNull() ?: 0
        (it.proteinPerGram * weight).toDouble()
    }.toFloat()
    val totalCarbs = editableItems.sumOf { 
        val weight = it.currentWeightStr.toIntOrNull() ?: 0
        (it.carbsPerGram * weight).toDouble()
    }.toFloat()
    val totalFat = editableItems.sumOf { 
        val weight = it.currentWeightStr.toIntOrNull() ?: 0
        (it.fatPerGram * weight).toDouble()
    }.toFloat()

    // Daily Value Targets (FDA reference values matching Avocado Bowl mockup ratios)
    val proteinDailyTarget = 57f
    val carbsDailyTarget = 173f
    val fatDailyTarget = 78f

    val proteinPercent = if (proteinDailyTarget > 0) ((totalProtein / proteinDailyTarget) * 100).toInt() else 0
    val carbsPercent = if (carbsDailyTarget > 0) ((totalCarbs / carbsDailyTarget) * 100).toInt() else 0
    val fatPercent = if (fatDailyTarget > 0) ((totalFat / fatDailyTarget) * 100).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: Review Meal + Round Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Review Meal",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                )
                if (isMock) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(PrimaryAccent.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("MOCK", style = MaterialTheme.typography.labelSmall, color = PrimaryAccent)
                    }
                }
            }
            
            // X close button matching the mockup
            IconButton(
                onClick = onLogAgain,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, BorderColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Main Premium Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(32.dp))
                .background(CardBackground, shape = RoundedCornerShape(32.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(32.dp))
        ) {
            Column {
                // Banner Image at the top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(Color(0xFFF1F5F9))
                ) {
                    if (capturedImageBytes != null) {
                        AsyncImage(
                            model = capturedImageBytes,
                            contentDescription = "Meal Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        // Fallback to beautiful mockup salad image
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&q=80&w=1000",
                            contentDescription = "Fallback Salad",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }

                // Ingredients list below the image
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "DETECTED INGREDIENTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MutedTextColor,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        editableItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                            .clickable { activeSwapIndex = index }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextColor
                                                ),
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "🔍 Swap",
                                                fontSize = 11.sp,
                                                color = PrimaryAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    val itemWeight = item.currentWeightStr.toIntOrNull() ?: 0
                                    val itemCalories = (item.calPerGram * itemWeight).toInt()
                                    val itemProtein = (item.proteinPerGram * itemWeight)
                                    val itemCarbs = (item.carbsPerGram * itemWeight)
                                    val itemFat = (item.fatPerGram * itemWeight)
                                    Text(
                                        text = "$itemCalories kcal  •  P: ${itemProtein.toInt()}g  C: ${itemCarbs.toInt()}g  F: ${itemFat.toInt()}g",
                                        fontSize = 12.sp,
                                        color = MutedTextColor,
                                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WeightInputPill(
                                        weightStr = item.currentWeightStr,
                                        onWeightChanged = { newWeight ->
                                            editableItems[index] = item.copy(currentWeightStr = newWeight)
                                        }
                                    )

                                    IconButton(
                                        onClick = {
                                            editableItems.removeAt(index)
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color(0xFFFEF2F2), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add Item Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showAddDbItemDialog = true }
                            .padding(top = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = PrimaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add Item from Database",
                            color = PrimaryAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }


                // Middle Section: Macro Breakdown (Slate background grid)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "CALCULATED MACROS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MutedTextColor,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    MacroGridCard("Calories", "$totalCalories", "kcal", PrimaryAccent)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    MacroGridCard("Protein", "${totalProtein.toInt()}g", "$proteinPercent%", ProteinColor)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    MacroGridCard("Carbs", "${totalCarbs.toInt()}g", "$carbsPercent%", CarbsColor)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    MacroGridCard("Fats", "${totalFat.toInt()}g", "$fatPercent%", FatColor)
                                }
                            }
                        }
                    }
                }

                // Bottom Section: Action Buttons
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isEdited) {
                        var isRecalculating by remember { mutableStateOf(false) }
                        val coroutineScope = rememberCoroutineScope()
                        var recalculateError by remember { mutableStateOf<String?>(null) }

                        Button(
                            onClick = {
                                isRecalculating = true
                                coroutineScope.launch {
                                    try {
                                        val itemsList = editableItems.map { 
                                            Pair(it.name, it.currentWeightStr.toIntOrNull() ?: 100)
                                        }
                                        val response = apiClient.recalculateMealNutrition(itemsList)
                                        
                                        editableItems.clear()
                                        editableItems.addAll(response.items.mapIndexed { index, item ->
                                            EditableFoodItem(
                                                id = "${item.item}_$index",
                                                name = item.item,
                                                currentWeightStr = item.weight_est_g.toString(),
                                                calPerGram = if (item.weight_est_g > 0) item.calories.toFloat() / item.weight_est_g else 0f,
                                                proteinPerGram = if (item.weight_est_g > 0) item.protein_g / item.weight_est_g else 0f,
                                                carbsPerGram = if (item.weight_est_g > 0) item.carbs_g / item.weight_est_g else 0f,
                                                fatPerGram = if (item.weight_est_g > 0) item.fat_g / item.weight_est_g else 0f,
                                                confidence = item.confidence
                                            )
                                        })
                                        recalculateError = null
                                    } catch (e: Exception) {
                                        recalculateError = e.message ?: "Recalculation failed"
                                    } finally {
                                        isRecalculating = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (isRecalculating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = "Recalculate with AI ⚡",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        if (recalculateError != null) {
                            Text(
                                text = recalculateError!!,
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Button(
                        onClick = { 
                            onMealLogged(data.meal_name, totalCalories, totalProtein, totalCarbs, totalFat)
                            showSuccessDialog = true 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Log Meal to Dashboard",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onLogAgain,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Recapture",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MutedTextColor
                        )
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // dialog 1: Add Item Custom Dialog
    // ----------------------------------------------------
    if (showAddItemDialog) {
        var newItemName by remember { mutableStateOf("") }
        var newItemWeight by remember { mutableStateOf("") }
        var newItemCalories by remember { mutableStateOf("") }
        var newItemProtein by remember { mutableStateOf("") }
        var newItemCarbs by remember { mutableStateOf("") }
        var newItemFat by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddItemDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Add Custom Ingredient",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("Ingredient Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newItemWeight,
                            onValueChange = { newItemWeight = it },
                            label = { Text("Weight (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newItemCalories,
                            onValueChange = { newItemCalories = it },
                            label = { Text("Calories") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newItemProtein,
                            onValueChange = { newItemProtein = it },
                            label = { Text("Protein (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newItemCarbs,
                            onValueChange = { newItemCarbs = it },
                            label = { Text("Carbs (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newItemFat,
                            onValueChange = { newItemFat = it },
                            label = { Text("Fat (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showAddItemDialog = false }) {
                            Text("Cancel", color = MutedTextColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val weight = newItemWeight.toIntOrNull() ?: 100
                                val calories = newItemCalories.toIntOrNull() ?: 0
                                val protein = newItemProtein.toFloatOrNull() ?: 0f
                                val carbs = newItemCarbs.toFloatOrNull() ?: 0f
                                val fat = newItemFat.toFloatOrNull() ?: 0f
                                
                                val item = EditableFoodItem(
                                    id = "${newItemName}_${editableItems.size}",
                                    name = newItemName.ifBlank { "Custom Item" },
                                    currentWeightStr = weight.toString(),
                                    calPerGram = calories.toFloat() / weight,
                                    proteinPerGram = protein / weight,
                                    carbsPerGram = carbs / weight,
                                    fatPerGram = fat / weight,
                                    confidence = "high"
                                )
                                editableItems.add(item)
                                showAddItemDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                        ) {
                            Text("Add", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // dialog 2: Success Dialog
    // ----------------------------------------------------
    if (showSuccessDialog) {
        Dialog(onDismissRequest = { 
            showSuccessDialog = false
            onLogAgain()
        }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .background(PrimaryAccent.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = PrimaryAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Meal Logged!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    Text(
                        text = "Your nutrition data has been successfully updated on the dashboard.",
                        fontSize = 14.sp,
                        color = MutedTextColor,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { 
                            showSuccessDialog = false
                            onLogAgain()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go to Dashboard", color = Color.White)
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // dialog 3: Swap Item from Database Dialog
    // ----------------------------------------------------
    activeSwapIndex?.let { swapIndex ->
        var swapSearchQuery by remember { mutableStateOf("") }
        val swapFilteredFoods = remember(swapSearchQuery) {
            if (swapSearchQuery.isBlank()) {
                FoodDatabase.foods
            } else {
                FoodDatabase.foods.filter {
                    it.name.contains(swapSearchQuery, ignoreCase = true) ||
                    it.synonyms.any { syn -> syn.contains(swapSearchQuery, ignoreCase = true) }
                }
            }
        }
        
        Dialog(onDismissRequest = { activeSwapIndex = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Swap with Database Food",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    
                    OutlinedTextField(
                        value = swapSearchQuery,
                        onValueChange = { swapSearchQuery = it },
                        label = { Text("Search Food Database") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Box(modifier = Modifier.height(200.dp).fillMaxWidth()) {
                        val listState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(listState),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            swapFilteredFoods.forEach { food ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val currentItem = editableItems[swapIndex]
                                            editableItems[swapIndex] = currentItem.copy(
                                                name = food.name,
                                                calPerGram = (food.calories / 100.0).toFloat(),
                                                proteinPerGram = food.protein / 100.0f,
                                                carbsPerGram = food.carbs / 100.0f,
                                                fatPerGram = food.fat / 100.0f
                                            )
                                            activeSwapIndex = null
                                        }
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = food.name,
                                            fontSize = 14.sp,
                                            color = TextColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Per 100g: ${food.calories.toInt()} kcal | P: ${food.protein.toInt()}g C: ${food.carbs.toInt()}g F: ${food.fat.toInt()}g",
                                            fontSize = 11.sp,
                                            color = MutedTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { activeSwapIndex = null }) {
                            Text("Cancel", color = MutedTextColor)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // dialog 4: Add Item from Database Dialog
    // ----------------------------------------------------
    if (showAddDbItemDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var selectedFoodEntry by remember { mutableStateOf<FoodDbEntry?>(null) }
        var weightStr by remember { mutableStateOf("100") }
        
        val filteredFoods = remember(searchQuery) {
            if (searchQuery.isBlank()) {
                FoodDatabase.foods
            } else {
                FoodDatabase.foods.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.synonyms.any { syn -> syn.contains(searchQuery, ignoreCase = true) }
                }
            }
        }
        
        Dialog(onDismissRequest = { showAddDbItemDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Add Item from Database",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it 
                            selectedFoodEntry = null
                        },
                        label = { Text("Search Food Database") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    selectedFoodEntry?.let { entry ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrimaryAccent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Selected: ${entry.name} (P: ${entry.protein}g, C: ${entry.carbs}g, F: ${entry.fat}g per 100g)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryAccent
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.height(150.dp).fillMaxWidth()) {
                        val listState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(listState),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredFoods.forEach { food ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (selectedFoodEntry == food) PrimaryAccent.copy(alpha = 0.2f) 
                                            else Color(0xFFF1F5F9), 
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedFoodEntry = food }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = food.name,
                                        fontSize = 14.sp,
                                        color = TextColor,
                                        fontWeight = if (selectedFoodEntry == food) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = { Text("Weight (grams)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showAddDbItemDialog = false }) {
                            Text("Cancel", color = MutedTextColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val food = selectedFoodEntry
                                val weight = weightStr.toIntOrNull()
                                if (food != null && weight != null && weight > 0) {
                                    val newItem = EditableFoodItem(
                                        id = "${food.name}_${editableItems.size}_${getCurrentTimeString()}",
                                        name = food.name,
                                        currentWeightStr = weight.toString(),
                                        calPerGram = (food.calories / 100.0).toFloat(),
                                        proteinPerGram = food.protein / 100.0f,
                                        carbsPerGram = food.carbs / 100.0f,
                                        fatPerGram = food.fat / 100.0f,
                                        confidence = "high"
                                    )
                                    editableItems.add(newItem)
                                    showAddDbItemDialog = false
                                }
                            },
                            enabled = selectedFoodEntry != null && (weightStr.toIntOrNull() ?: 0) > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                        ) {
                            Text("Add", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 4. SettingsScreen — Weight, Height & Goals settings
// ----------------------------------------------------
@Composable
fun SettingsScreen(
    profile: UserProfile,
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

// Helper Algorithms
fun calculateBmr(profile: UserProfile): Float {
    return if (profile.gender == "Male") {
        (10f * profile.weight) + (6.25f * profile.height) - (5f * profile.age) + 5f
    } else {
        (10f * profile.weight) + (6.25f * profile.height) - (5f * profile.age) - 161f
    }
}

fun getAiCoachFeedback(p: Float, c: Float, f: Float, cal: Int, profile: UserProfile): String {
    if (cal == 0) {
        return "Coach says: Log your first meal to get personalized daily nutritional feedback!"
    }
    
    val pPct = if (profile.proteinGoal > 0) (p / profile.proteinGoal) else 0f
    val cPct = if (profile.carbsGoal > 0) (c / profile.carbsGoal) else 0f
    val fPct = if (profile.fatGoal > 0) (f / profile.fatGoal) else 0f
    
    return when {
        pPct < 0.4f -> "Coach says: Your protein intake is low today ($p g). Try adding Greek yogurt, eggs, or lean salmon to feed your muscles!"
        fPct > 0.9f -> "Coach says: You are close to your fats limit today ($f g). Keep subsequent meals lean, focusing on vegetables and complex carbs."
        cPct > 0.9f -> "Coach says: Carbs limit reached ($c g). Swap simple starches for proteins and fibers for the rest of today."
        cal > profile.calGoal -> "Coach says: You've exceeded your daily calorie goal. Try to focus on lean proteins and hydration tomorrow."
        else -> "Coach says: Excellent macro distribution! You are keeping a balanced intake today. Keep it up!"
    }
}

@Composable
fun WeightInputPill(
    weightStr: String,
    onWeightChanged: (String) -> Unit
) {
    var textValue by remember(weightStr) { mutableStateOf(weightStr) }
    Row(
        modifier = Modifier
            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = {
                if (it.all { char -> char.isDigit() }) {
                    textValue = it
                    onWeightChanged(it)
                }
            },
            textStyle = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(36.dp)
        )
        Text(text = "g", fontSize = 12.sp, color = MutedTextColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MacroGridCard(
    title: String,
    value: String,
    label: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTextColor
                )
                Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MutedTextColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

fun getMockJson(): String = """
{
  "meal_name": "Grilled Salmon & Quinoa Pilaf",
  "items": [
    {
      "item": "Grilled Salmon Fillet",
      "weight_est_g": 180,
      "calories": 360,
      "protein_g": 39.0,
      "carbs_g": 0.0,
      "fat_g": 22.0,
      "confidence": "high"
    },
    {
      "item": "Quinoa Pilaf",
      "weight_est_g": 120,
      "calories": 140,
      "protein_g": 5.0,
      "carbs_g": 26.0,
      "fat_g": 2.0,
      "confidence": "high"
    },
    {
      "item": "Roasted Asparagus",
      "weight_est_g": 100,
      "calories": 35,
      "protein_g": 2.2,
      "carbs_g": 4.1,
      "fat_g": 1.2,
      "confidence": "medium"
    },
    {
      "item": "Lemon Butter Sauce",
      "weight_est_g": 15,
      "calories": 75,
      "protein_g": 0.1,
      "carbs_g": 0.5,
      "fat_g": 8.3,
      "confidence": "medium"
    }
  ],
  "totals": {
    "calories": 610,
    "protein_g": 46.3,
    "carbs_g": 30.6,
    "fat_g": 33.7
  },
  "estimation_notes": "Estimated portion sizes are based on visual analysis. Salmon is a source of lean protein and Omega-3 fats. Asparagus and quinoa provide fiber and slow-release carbohydrates."
}
""".trimIndent()

