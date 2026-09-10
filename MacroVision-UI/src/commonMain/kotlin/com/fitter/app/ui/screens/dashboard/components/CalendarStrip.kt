package com.fitter.app.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitter.app.getLastSevenDays
import com.fitter.app.ui.theme.BorderColor
import com.fitter.app.ui.theme.MutedTextColor
import com.fitter.app.ui.theme.PrimaryAccent
import com.fitter.app.ui.theme.TextColor

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
