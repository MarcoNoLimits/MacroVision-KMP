package com.fitter.app.ui.screens.review.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitter.app.ui.theme.BorderColor
import com.fitter.app.ui.theme.MutedTextColor
import com.fitter.app.ui.theme.TextColor

// Interactive wrapper model for editable weights
data class EditableFoodItem(
    val id: String,
    val name: String,
    val currentWeightStr: String,
    val calPerGram: Float,
    val proteinPerGram: Float,
    val carbsPerGram: Float,
    val fatPerGram: Float,
    val confidence: String
)

@Composable
fun WeightInputPill(
    weightStr: String,
    onWeightChanged: (String) -> Unit
) {
    var textValue by remember(weightStr) { mutableStateOf(weightStr) }
    Row(
        modifier = Modifier
            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = {
                if (it.all { char -> char.isDigit() }) {
                    textValue = it
                    onWeightChanged(it)
                }
            },
            textStyle = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(36.dp)
        )
        Text(text = "g", fontSize = 12.sp, color = MutedTextColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MacroGridCard(
    title: String,
    value: String,
    label: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedTextColor
                )
                Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MutedTextColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
