package com.fitter.app.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitter.app.ui.theme.*

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
            .shadow(1.dp, RoundedCornerShape(RadiusM)) // F1.2: was 16.dp → RadiusM
            .background(Color.White, RoundedCornerShape(RadiusM))
            // F1.1: SurfaceTint replaces Color(0xFFF1F5F9)
            .border(1.dp, SurfaceTint, RoundedCornerShape(RadiusM))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // F2.1: Eyebrow replaces 10.sp inline
                Text(
                    text = title,
                    style = BrandTypography.Eyebrow,
                    color = MutedTextColor
                )
                Box(modifier = Modifier.size(5.dp).background(color, CircleShape))
            }
            // F2.1: KpiNumeral replaces 16.sp inline
            Text(
                text = value,
                style = BrandTypography.KpiNumeral,
                color = TextColor
            )
            // F2.1: BodySmall (12sp) replaces 9.sp — raises it above the 12sp min threshold
            // Note: 9.sp eliminated per spec; BodySmall is the closest permitted size for muted text
            Text(
                text = "$percent of goal",
                style = BrandTypography.BodySmall,
                color = MutedTextColor
            )
        }
    }
}
