package com.fitter.app.ui.screens.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitter.app.ui.theme.MutedTextColor
import com.fitter.app.ui.theme.TextColor

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
