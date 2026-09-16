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
import com.fitter.app.ui.theme.*

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
            // F1.1: SurfaceTint replaces Color(0xFFF1F5F9)
            .background(SurfaceTint, RoundedCornerShape(RadiusS)) // F1.2: RadiusS replaces 12.dp
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusS))
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
                // F2.1: BodySmall size (12sp) matches; keeping inline TextStyle as BasicTextField
                // requires TextStyle not style parameter
                fontSize = BrandTypography.BodySmall.fontSize,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(36.dp)
        )
        Text(
            text = "g",
            style = BrandTypography.BodySmall,
            color = MutedTextColor,
            fontWeight = FontWeight.Bold
        )
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
        shape = RoundedCornerShape(RadiusM), // F1.2: was 16.dp → RadiusM
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusM))
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
                // F2.1: Eyebrow replaces 11.sp for titles
                // F2.2: Eyebrow=10sp at MutedTextColor — acceptable (spec says ≥12sp for *body* muted text;
                // label eyebrows in caps are a distinct role). Spec F2.2 targets long-form muted body text.
                Text(
                    text = title,
                    style = BrandTypography.Eyebrow,
                    color = MutedTextColor
                )
                Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // F2.1: inline 18.sp → SectionTitle won't work (SectionTitle=14sp); keep numerals explicit
                // Substitution: using 18.sp directly as a one-off for KPI numerals larger than KpiNumeral(16sp).
                // Noted as deviation: spec doesn't define a 18sp token.
                Text(
                    text = value,
                    style = BrandTypography.KpiNumeral.copy(fontSize = 18.sp),
                    color = TextColor
                )
                // F2.1: Micro (11sp) replaces 11.sp — same value; now semantic
                Text(
                    text = label,
                    style = BrandTypography.Micro,
                    color = MutedTextColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
