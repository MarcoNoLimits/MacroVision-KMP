package com.fitter.app.ui.screens.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitter.app.ui.theme.*

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

    // F1.1: DangerColor / PrimaryAccent replace Color(0xFFEF4444) / Color(0xFF10B981)
    val primaryColor = if (isOver) DangerColor else PrimaryAccent

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(140.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // F1.1: SurfaceTint replaces Color(0xFFF1F5F9)
            drawCircle(
                color = SurfaceTint,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
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
            // F2.1: KpiNumeral (16sp Bold Mono) replaces inline 24.sp
            Text(
                text = "$absRemaining",
                style = BrandTypography.KpiNumeral,
                color = if (isOver) DangerColor else TextColor // F1.1
            )
            // F2.1: Eyebrow (10sp) replaces 10.sp inline
            Text(
                text = if (isOver) "kcal over" else "kcal left",
                style = BrandTypography.Eyebrow,
                fontWeight = FontWeight.Medium,
                color = if (isOver) DangerColor.copy(alpha = 0.8f) else MutedTextColor // F1.1
            )
        }
    }
}
