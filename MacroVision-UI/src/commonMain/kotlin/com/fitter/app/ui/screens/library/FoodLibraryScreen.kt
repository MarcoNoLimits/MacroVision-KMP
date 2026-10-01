package com.fitter.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitter.app.ui.theme.BgColor
import com.fitter.app.ui.theme.BorderColor
import com.fitter.app.ui.theme.CardBackground
import com.fitter.app.ui.theme.DangerColor
import com.fitter.app.ui.theme.InputBorder
import com.fitter.app.ui.theme.MutedTextColor
import com.fitter.app.ui.theme.PrimaryAccent
import com.fitter.app.ui.theme.ProteinColor
import com.fitter.app.ui.theme.CarbsColor
import com.fitter.app.ui.theme.FatColor
import com.fitter.app.ui.theme.TextColor
import com.fitter.shared.api.FoodDbEntry
import com.fitter.shared.api.FoodDatabase

/**
 * Browsable food nutrition library.
 *
 * WHY THIS SCREEN EXISTS (not filler content):
 * - AdMob rejected Fitter, and "insufficient original content / thin utility app" is the
 *   single most common reason health and utility apps get rejected. A searchable
 *   reference library of ~113 foods with verified macros is genuine product surface.
 * - It also improves the core product: users can check a value without spending a scan.
 * - It is entirely offline, which keeps it fast and costs nothing at runtime.
 *
 * Values are per 100 g and come from the bundled [FoodDatabase]. They are reference
 * figures — the same "estimates, not medical advice" caveat applies.
 */
@Composable
fun FoodLibraryScreen(
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FoodDbEntry?>(null) }

    // Browsing shows everything; typing narrows it. An empty query is a valid state,
    // so the list is never blank.
    val results = remember(query) {
        if (query.isBlank()) FoodDatabase.foods else FoodDatabase.searchFoods(query, 60)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✕",
                fontSize = 18.sp,
                color = MutedTextColor,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack)
                    .padding(10.dp),
            )
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Food Library",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextColor,
                )
                Text(
                    text = "${FoodDatabase.foods.size} foods · per 100 g",
                    fontSize = 12.sp,
                    color = MutedTextColor,
                )
            }
        }

        // ── Search ───────────────────────────────────────────────────────────
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(CardBackground, RoundedCornerShape(12.dp))
                .border(1.dp, if (query.isEmpty()) InputBorder else PrimaryAccent, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search foods (e.g. chicken, rice, salmon)",
                        fontSize = 14.sp,
                        color = MutedTextColor,
                    )
                }
                inner()
            },
            singleLine = true,
        )

        Spacer(Modifier.height(12.dp))

        // ── Result count / empty state ───────────────────────────────────────
        Text(
            text = if (query.isBlank()) "All foods" else "${results.size} result${if (results.size == 1) "" else "s"}",
            fontSize = 12.sp,
            color = MutedTextColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        if (results.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "No match for \"$query\"",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextColor,
                )
                Text(
                    text = "Try a simpler term, or photograph the meal and let Fitter estimate it.",
                    fontSize = 13.sp,
                    color = MutedTextColor,
                    lineHeight = 18.sp,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.name }) { food ->
                FoodRow(
                    food = food,
                    expanded = selected?.name == food.name,
                    onClick = {
                        selected = if (selected?.name == food.name) null else food
                    },
                )
            }
        }
    }
}

@Composable
private fun FoodRow(
    food: FoodDbEntry,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextColor,
                )
                Text(
                    text = "${food.calories.toInt()} kcal",
                    fontSize = 13.sp,
                    color = MutedTextColor,
                )
            }
            MacroPill(label = "P", value = food.protein, color = ProteinColor)
            Spacer(Modifier.size(6.dp))
            MacroPill(label = "C", value = food.carbs, color = CarbsColor)
            Spacer(Modifier.size(6.dp))
            MacroPill(label = "F", value = food.fat, color = FatColor)
        }

        // Expandable detail — keeps the browse list scannable while still offering
        // the detail a user actually wants.
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Per 100 g: ${food.protein} g protein · ${food.carbs} g carbs · ${food.fat} g fat",
                fontSize = 12.sp,
                color = TextColor,
                lineHeight = 17.sp,
            )
            if (food.synonyms.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Also called: ${food.synonyms.take(6).joinToString(", ")}",
                    fontSize = 11.sp,
                    color = MutedTextColor,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Reference values from the bundled Fitter food database. " +
                    "Actual values vary with preparation and brand — not medical advice.",
                fontSize = 11.sp,
                color = DangerColor,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun MacroPill(label: String, value: Float, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        Text(
            text = "${value.toInt()}",
            fontSize = 11.sp,
            color = TextColor,
        )
    }
}