package com.fitter.app.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.fitter.app.getLastSevenDays
import com.fitter.app.ui.components.PressableBox
import com.fitter.app.ui.theme.*

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

            // F3.1: PressableBox replaces .clickable on calendar day boxes
            PressableBox(
                onTap = { onDateSelected(key) },
                modifier = Modifier
                    .width(60.dp)
                    // F0.3: height at least 44dp for touch target compliance
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(RadiusM)) // F1.2: was 16.dp → RadiusM
                    .background(if (isSelected) PrimaryAccent else Color.White)
                    .border(1.dp, if (isSelected) PrimaryAccent else BorderColor, RoundedCornerShape(RadiusM))
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // F2.1: Eyebrow (10sp) replaces inline 10.sp — same value; token makes intent explicit
                    Text(
                        text = label,
                        style = BrandTypography.Eyebrow,
                        color = if (isSelected) Color.White else MutedTextColor
                    )
                    Text(
                        text = dayNumber,
                        style = BrandTypography.KpiNumeral,
                        color = if (isSelected) Color.White else TextColor
                    )
                }
            }
        }
    }
}
