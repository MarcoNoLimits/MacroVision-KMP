package com.fitter.app.ui.screens.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitter.app.ui.theme.BorderColor
import com.fitter.app.ui.theme.CardBackground
import com.fitter.app.ui.theme.MutedTextColor
import com.fitter.app.ui.theme.TextColor

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
                        color = Color(0xFF38BDF8),
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
