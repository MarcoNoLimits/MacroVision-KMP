package com.fitcal.app.ui.screens.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.fitcal.app.getCurrentEpochMillis
import com.fitcal.app.getCurrentTimeString
import com.fitcal.app.telemetry.Analytics
import com.fitcal.app.telemetry.summarizeCorrections
import com.fitcal.app.ui.components.FitCalTextField
import com.fitcal.app.ui.components.PressableBox
import com.fitcal.app.ui.screens.review.components.EditableFoodItem
import com.fitcal.app.ui.screens.review.components.MacroGridCard
import com.fitcal.app.ui.screens.review.components.WeightInputPill
import com.fitcal.app.ui.theme.*
import com.fitcal.app.ui.screens.auth.SignInSuggestionCard
import com.fitcal.app.ui.screens.feedback.ScanAccuracyPrompt
import com.fitcal.shared.api.FoodDatabase
import com.fitcal.shared.api.FoodDbEntry
import com.fitcal.shared.api.NutritionClient
import com.fitcal.shared.model.NutritionResponse
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(
    apiClient: NutritionClient,
    data: NutritionResponse,
    isMock: Boolean,
    capturedImageBytes: ByteArray?,
    onMealLogged: (String, Int, Float, Float, Float) -> Unit,
    onLogAgain: () -> Unit,
    shouldSuggestSignIn: () -> Boolean = { false },
    onSignInSuggestionShown: () -> Unit = {},
    onSignInSuggestionDismissed: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onRateEstimate: (positive: Boolean, reasons: List<String>) -> Unit = { _, _ -> },
) {
    val scrollState = rememberScrollState()

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

    // Analytics: what the user changed before logging, and whether they logged at all.
    val reviewOpenedAt = remember(data) { getCurrentEpochMillis() }
    var swapCount by remember(data) { mutableStateOf(0) }
    var libraryAddCount by remember(data) { mutableStateOf(0) }
    var customAddCount by remember(data) { mutableStateOf(0) }
    var recalcCount by remember(data) { mutableStateOf(0) }
    var mealLogged by remember(data) { mutableStateOf(false) }
    val currentItemCount by rememberUpdatedState(editableItems.size)
    DisposableEffect(data) {
        onDispose {
            if (!mealLogged) {
                Analytics.track(
                    "review_abandoned",
                    "items_ai" to data.items.size,
                    "items_final" to currentItemCount,
                    "review_seconds" to (getCurrentEpochMillis() - reviewOpenedAt) / 1000,
                )
            }
        }
    }

    val isEdited = remember(editableItems.map { it.name }) {
        if (editableItems.size != data.items.size) {
            true
        } else {
            editableItems.zip(data.items).any { (edited, original) ->
                edited.name.trim().lowercase() != original.item.trim().lowercase()
            }
        }
    }

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
        // ── Estimate disclaimer ────────────────────────────────────────────────
        // Must sit immediately above the numbers, not buried at the bottom of a
        // screen the user scrolls past. Presenting estimated calories with no
        // adjacent caveat is the core medical-liability exposure.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DangerSoft, RoundedCornerShape(RadiusXS))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = DangerTextStrong,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI-generated estimates. Portion sizes are approximate and may be " +
                    "significantly off — not medical advice. Verify with a kitchen scale for " +
                    "accurate tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = DangerTextStrong,
            )
        }

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // F2.1: ScreenTitle replaces headlineSmall override (≈24sp matches)
                Text(
                    text = "Review Meal",
                    style = BrandTypography.ScreenTitle,
                    color = TextColor
                )
                if (isMock) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(PrimaryAccent.copy(alpha = 0.1f), shape = RoundedCornerShape(RadiusXS))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("MOCK", style = MaterialTheme.typography.labelSmall, color = PrimaryAccent)
                    }
                }
            }

            IconButton(
                onClick = onLogAgain,
                modifier = Modifier
                    .size(44.dp) // F0.3: ≥44dp
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
                .shadow(12.dp, RoundedCornerShape(RadiusXL)) // F1.2
                .background(CardBackground, shape = RoundedCornerShape(RadiusXL))
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusXL))
        ) {
            Column {
                // Banner Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = RadiusXL, topEnd = RadiusXL)) // F1.2
                        // F1.1: SurfaceTint replaces Color(0xFFF1F5F9)
                        .background(SurfaceTint)
                ) {
                    if (capturedImageBytes != null) {
                        AsyncImage(
                            model = capturedImageBytes,
                            contentDescription = "Meal Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&q=80&w=1000",
                            contentDescription = "Fallback Salad",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Ingredients list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "DETECTED INGREDIENTS",
                        style = BrandTypography.Eyebrow, // F2.1
                        color = MutedTextColor,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        editableItems.forEachIndexed { index, item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardBackground)
                                    .padding(vertical = 4.dp)
                            ) {
                                // F4.2: Swap affordance — Icons.Default.Search replaces 🔍 emoji
                                // F3.1: PressableBox replaces .clickable on the swap row
                                PressableBox(
                                    onTap = { activeSwapIndex = index },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // F1.1: SurfaceTint replaces Color(0xFFF1F5F9)
                                        .background(SurfaceTint, RoundedCornerShape(RadiusS)) // F1.2
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = item.name,
                                            style = BrandTypography.Body.copy(fontWeight = FontWeight.Bold),
                                            color = TextColor,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // F4.2: Icons.Default.Search replaces "🔍" emoji text
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Swap food item",
                                                tint = PrimaryAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Swap",
                                                style = BrandTypography.Micro,
                                                color = PrimaryAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val itemWeight = item.currentWeightStr.toIntOrNull() ?: 0
                                    val itemCalories = (item.calPerGram * itemWeight).toInt()
                                    val itemProtein = (item.proteinPerGram * itemWeight)
                                    val itemCarbs = (item.carbsPerGram * itemWeight)
                                    val itemFat = (item.fatPerGram * itemWeight)

                                    Text(
                                        text = "$itemCalories kcal  •  P: ${itemProtein.toInt()}g C: ${itemCarbs.toInt()}g F: ${itemFat.toInt()}g",
                                        // F2.2: BodySmall (12sp) at MutedTextColor — safe (≥12sp threshold)
                                        style = BrandTypography.BodySmall,
                                        color = MutedTextColor,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )

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

                                        // F0.3: IconButton size 44dp instead of 32dp
                                        IconButton(
                                            onClick = {
                                                editableItems.removeAt(index)
                                            },
                                            modifier = Modifier
                                                .size(44.dp) // F0.3: ≥44dp; was 32dp
                                                // F1.1: DangerSoft replaces Color(0xFFFEF2F2)
                                                .background(DangerSoft, CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete ingredient ${item.name}",
                                                // F1.1: DangerColor replaces Color(0xFFEF4444)
                                                tint = DangerColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add Item Button — F3.1: PressableBox replaces .clickable
                    PressableBox(
                        onTap = { showAddDbItemDialog = true },
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add ingredient from database",
                                tint = PrimaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Item from Database",
                                color = PrimaryAccent,
                                style = BrandTypography.SectionTitle
                            )
                        }
                    }
                }

                // Macro Breakdown Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // F1.1: InfoPanel replaces Color(0xFFF8FAFC)
                        .background(InfoPanel)
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "CALCULATED MACROS",
                            style = BrandTypography.Eyebrow, // F2.1
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

                // Action Buttons
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (data.items.isNotEmpty()) {
                        ScanAccuracyPrompt(onRate = onRateEstimate)
                    }

                    if (isEdited) {
                        var isRecalculating by remember { mutableStateOf(false) }
                        val coroutineScope = rememberCoroutineScope()
                        var recalculateError by remember { mutableStateOf<String?>(null) }

                        Button(
                            onClick = {
                                isRecalculating = true
                                recalcCount++
                                val recalcStartedAt = getCurrentEpochMillis()
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
                                        Analytics.track(
                                            "recalculate_succeeded",
                                            "items" to itemsList.size,
                                            "latency_ms" to getCurrentEpochMillis() - recalcStartedAt,
                                        )
                                    } catch (e: Exception) {
                                        Analytics.track(
                                            "recalculate_failed",
                                            "latency_ms" to getCurrentEpochMillis() - recalcStartedAt,
                                            "error" to (e::class.simpleName ?: "Exception"),
                                        )
                                        recalculateError = e.message ?: "Recalculation failed"
                                    } finally {
                                        isRecalculating = false
                                    }
                                }
                            },
                            // F1.1: PrimaryAccent (emerald) replaces Color(0xFF3B82F6) blue
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                            shape = RoundedCornerShape(RadiusM), // F1.2
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (isRecalculating) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = "Recalculate with AI ⚡",
                                    style = BrandTypography.SectionTitle,
                                    color = Color.White
                                )
                            }
                        }

                        if (recalculateError != null) {
                            Text(
                                text = recalculateError!!,
                                // F1.1: DangerColor; F2.2: BodySmall (12sp) at DangerColor safe
                                style = BrandTypography.BodySmall,
                                color = DangerColor,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Button(
                        onClick = {
                            val corrections = summarizeCorrections(
                                original = data.items,
                                final = editableItems.map { it.name to (it.currentWeightStr.toIntOrNull() ?: 0) },
                            )
                            Analytics.track(
                                "meal_logged",
                                "edited" to corrections.edited,
                                "items_ai" to corrections.itemsAi,
                                "items_final" to corrections.itemsFinal,
                                "items_removed" to corrections.itemsRemoved,
                                "items_added" to corrections.itemsAdded,
                                "items_swapped" to swapCount,
                                "library_adds" to libraryAddCount,
                                "custom_adds" to customAddCount,
                                "weights_changed" to corrections.weightsChanged,
                                "weight_change_pct" to corrections.weightChangePct,
                                "low_confidence_items" to corrections.lowConfidenceItems,
                                "recalculations" to recalcCount,
                                "review_seconds" to (getCurrentEpochMillis() - reviewOpenedAt) / 1000,
                            )
                            mealLogged = true
                            onMealLogged(data.meal_name, totalCalories, totalProtein, totalCarbs, totalFat)
                            showSuccessDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        shape = RoundedCornerShape(RadiusM),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Log Meal to Dashboard",
                            style = BrandTypography.SectionTitle,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onLogAgain,
                        shape = RoundedCornerShape(RadiusM),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Recapture",
                            style = BrandTypography.SectionTitle,
                            color = MutedTextColor
                        )
                    }
                }
            }
        }
    }

    // ── Dialog 1: Add Custom Ingredient ──────────────────────────────────────
    // F3.2: AnimatedVisibility on dialog card entrance
    if (showAddItemDialog) {
        var newItemName by remember { mutableStateOf("") }
        var newItemWeight by remember { mutableStateOf("") }
        var newItemCalories by remember { mutableStateOf("") }
        var newItemProtein by remember { mutableStateOf("") }
        var newItemCarbs by remember { mutableStateOf("") }
        var newItemFat by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddItemDialog = false }) {
            AnimatedVisibility(
                visible = true,
                enter = if (!REDUCED_MOTION_ENABLED) fadeIn() + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = spring(stiffness = 300f)
                ) else fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(RadiusL),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Add Custom Ingredient",
                            style = BrandTypography.ScreenTitle,
                            color = TextColor
                        )
                        FitCalTextField(
                            value = newItemName,
                            onValueChange = { newItemName = it },
                            label = "Ingredient Name",
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FitCalTextField(
                                value = newItemWeight,
                                onValueChange = { newItemWeight = it },
                                label = "Weight",
                                unit = "g",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            FitCalTextField(
                                value = newItemCalories,
                                onValueChange = { newItemCalories = it },
                                label = "Calories",
                                unit = "kcal",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FitCalTextField(
                                value = newItemProtein,
                                onValueChange = { newItemProtein = it },
                                label = "Protein",
                                unit = "g",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            FitCalTextField(
                                value = newItemCarbs,
                                onValueChange = { newItemCarbs = it },
                                label = "Carbs",
                                unit = "g",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            FitCalTextField(
                                value = newItemFat,
                                onValueChange = { newItemFat = it },
                                label = "Fat",
                                unit = "g",
                                keyboardType = KeyboardType.Number,
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
                                    customAddCount++
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
    }

    // ── Dialog 2: Success Dialog (F5.2 + F3.2) ───────────────────────────────
    if (showSuccessDialog) {
        // F5.2: check-icon animated scale 0→1 with spring
        val checkScale = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            if (!REDUCED_MOTION_ENABLED) { // F4.4: gate
                checkScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.6f, // slight bounce for delight
                        stiffness = 400f     // ~0.25s effective response
                    )
                )
            } else {
                checkScale.snapTo(1f)
            }
        }

        Dialog(onDismissRequest = {
            showSuccessDialog = false
            onLogAgain()
        }) {
            // F3.2: AnimatedVisibility fade+scaleIn on success dialog
            AnimatedVisibility(
                visible = true,
                enter = if (!REDUCED_MOTION_ENABLED) fadeIn() + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = spring(stiffness = 300f)
                ) else fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(RadiusL),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
                        .padding(4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // F5.2: spring-animated check icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer {
                                    scaleX = checkScale.value
                                    scaleY = checkScale.value
                                }
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
                            style = BrandTypography.ScreenTitle,
                            color = TextColor
                        )
                        Text(
                            text = "Your nutrition data has been successfully updated on the dashboard.",
                            style = BrandTypography.BodySmall,
                            color = MutedTextColor,
                            textAlign = TextAlign.Center
                        )
                        var showSuggestion by remember { mutableStateOf(shouldSuggestSignIn()) }
                        if (showSuggestion) {
                            LaunchedEffect(Unit) { onSignInSuggestionShown() }
                            SignInSuggestionCard(
                                title = "Keep your meals safe",
                                body = "Create a free account to back up your history and get +1 free AI scan every day.",
                                actionLabel = "Create account",
                                onAction = {
                                    showSuccessDialog = false
                                    onCreateAccount()
                                },
                                onDismiss = {
                                    showSuggestion = false
                                    onSignInSuggestionDismissed()
                                },
                            )
                        }
                        Button(
                            onClick = {
                                showSuccessDialog = false
                                onLogAgain()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go to Dashboard", color = Color.White, style = BrandTypography.Body)
                        }
                    }
                }
            }
        }
    }

    // ── Dialog 3: Swap Item from Database ────────────────────────────────────
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
            // F3.2: AnimatedVisibility entrance
            AnimatedVisibility(
                visible = true,
                enter = if (!REDUCED_MOTION_ENABLED) fadeIn() + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = spring(stiffness = 300f)
                ) else fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(RadiusL),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Swap with Database Food",
                            style = BrandTypography.ScreenTitle,
                            color = TextColor
                        )
                        FitCalTextField(
                            value = swapSearchQuery,
                            onValueChange = { swapSearchQuery = it },
                            label = "Search Food Database",
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier.fillMaxWidth()
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
                                    // F3.1: PressableBox replaces .clickable on food rows
                                    PressableBox(
                                        onTap = {
                                            val currentItem = editableItems[swapIndex]
                                            swapCount++
                                            editableItems[swapIndex] = currentItem.copy(
                                                name = food.name,
                                                calPerGram = (food.calories / 100.0).toFloat(),
                                                proteinPerGram = food.protein / 100.0f,
                                                carbsPerGram = food.carbs / 100.0f,
                                                fatPerGram = food.fat / 100.0f
                                            )
                                            activeSwapIndex = null
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // F1.1: SurfaceTint replaces Color(0xFFF1F5F9)
                                            .background(SurfaceTint, RoundedCornerShape(RadiusXS))
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = food.name,
                                                style = BrandTypography.SectionTitle,
                                                color = TextColor
                                            )
                                            Text(
                                                text = "Per 100g: ${food.calories.toInt()} kcal | P: ${food.protein.toInt()}g C: ${food.carbs.toInt()}g F: ${food.fat.toInt()}g",
                                                style = BrandTypography.BodySmall,
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
    }

    // ── Dialog 4: Add Item from Database ─────────────────────────────────────
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
            // F3.2: AnimatedVisibility entrance
            AnimatedVisibility(
                visible = true,
                enter = if (!REDUCED_MOTION_ENABLED) fadeIn() + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = spring(stiffness = 300f)
                ) else fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(RadiusL),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Add Item from Database",
                            style = BrandTypography.ScreenTitle,
                            color = TextColor
                        )
                        FitCalTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                selectedFoodEntry = null
                            },
                            label = "Search Food Database",
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier.fillMaxWidth()
                        )
                        selectedFoodEntry?.let { entry ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PrimaryAccent.copy(alpha = 0.1f), RoundedCornerShape(RadiusS))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Selected: ${entry.name} (P: ${entry.protein}g, C: ${entry.carbs}g, F: ${entry.fat}g per 100g)",
                                    style = BrandTypography.BodySmall,
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
                                    // F3.1: PressableBox replaces .clickable on food rows
                                    PressableBox(
                                        onTap = { selectedFoodEntry = food },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (selectedFoodEntry == food) PrimaryAccent.copy(alpha = 0.2f)
                                                else SurfaceTint, // F1.1
                                                RoundedCornerShape(RadiusXS)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = food.name,
                                            style = BrandTypography.SectionTitle,
                                            color = TextColor,
                                            fontWeight = if (selectedFoodEntry == food) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                        FitCalTextField(
                            value = weightStr,
                            onValueChange = { weightStr = it },
                            label = "Weight",
                            unit = "g",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth()
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
                                        libraryAddCount++
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
}
