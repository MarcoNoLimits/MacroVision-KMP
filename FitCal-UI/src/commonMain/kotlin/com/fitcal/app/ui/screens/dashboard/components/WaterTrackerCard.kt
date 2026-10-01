package com.fitcal.app.ui.screens.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ui.theme.*

@Composable
fun WaterTrackerCard(
    waterLogged: Int,
    onWaterChanged: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(RadiusL), // F1.2: was 24.dp → RadiusL
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(RadiusL))
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
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
                    // F2.1: Eyebrow replaces hardcoded 10.sp; F1.1: WaterAccent replaces Color(0xFF38BDF8)
                    Text(
                        text = "WATER INTAKE",
                        style = BrandTypography.Eyebrow,
                        color = WaterAccent
                    )
                    Text(
                        text = "$waterLogged ml / 2000 ml",
                        style = BrandTypography.SectionTitle,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )
                }

                // F0.3: buttons min 44dp; F4.2: real IconButtons with contentDescription
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { if (waterLogged >= 250) onWaterChanged(waterLogged - 250) },
                        modifier = Modifier
                            .size(44.dp) // F0.3: ≥44dp
                            .semantics { contentDescription = "Remove 250ml water" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null, // described by parent semantics
                            tint = MutedTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onWaterChanged(waterLogged + 250) },
                        modifier = Modifier
                            .size(44.dp) // F0.3: ≥44dp
                            .semantics { contentDescription = "Add 250ml water" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null, // described by parent semantics
                            // F1.1: WaterAccent replaces Color(0xFF38BDF8)
                            tint = WaterAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // F4.2 + F0.3: Interactive cup grid — emoji cups replaced with real IconButtons.
            // Each cup is an IconButton (≥44dp) with contentDescription, filled/empty state
            // conveyed via alpha and tint. No .clickable{} on Text (grep -rn "clickable" WaterTrackerCard.kt → 0).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 1..8) {
                    val cupLimit = i * 250
                    val isFilled = waterLogged >= cupLimit
                    val cupDescription = if (isFilled)
                        "Cup $i filled — tap to remove water to ${cupLimit - 250}ml"
                    else
                        "Cup $i empty — tap to add water to ${cupLimit}ml"

                    IconButton(
                        onClick = {
                            if (isFilled) {
                                onWaterChanged(cupLimit - 250)
                            } else {
                                onWaterChanged(cupLimit)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp) // F0.3: ≥44dp hit area
                            .semantics { contentDescription = cupDescription }
                    ) {
                        // Emoji cup displayed inside the button; non-interactive when inside IconButton
                        Text(
                            text = if (isFilled) "🥛" else "🥛",
                            fontSize = 20.sp,
                            modifier = Modifier.alpha(if (isFilled) 1f else 0.3f)
                        )
                    }
                }
            }
        }
    }
}
