package com.fitter.app.ui.screens.review

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.fitter.app.getCurrentTimeString
import com.fitter.app.ui.screens.review.components.EditableFoodItem
import com.fitter.app.ui.screens.review.components.MacroGridCard
import com.fitter.app.ui.screens.review.components.WeightInputPill
import com.fitter.app.ui.theme.*
import com.fitter.shared.api.FoodDatabase
import com.fitter.shared.api.FoodDbEntry
import com.fitter.shared.api.NutritionClient
import com.fitter.shared.model.NutritionResponse
import kotlinx.coroutines.launch

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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardBackground)
                                    .padding(vertical = 4.dp)
                            ) {
                                // Row 1: Name Pill (Clickable to swap)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                        .clickable { activeSwapIndex = index }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
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
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "🔍",
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "Swap",
                                                fontSize = 11.sp,
                                                color = PrimaryAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Row 2: Macros (Left) & Controls (Right)
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
                                        fontSize = 12.sp,
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
