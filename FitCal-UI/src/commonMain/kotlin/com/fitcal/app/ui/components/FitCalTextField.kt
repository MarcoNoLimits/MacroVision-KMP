package com.fitcal.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.ui.theme.BrandTypography
import com.fitcal.app.ui.theme.CardBackground
import com.fitcal.app.ui.theme.InputBorder
import com.fitcal.app.ui.theme.MutedTextColor
import com.fitcal.app.ui.theme.PrimaryAccent
import com.fitcal.app.ui.theme.RadiusS
import com.fitcal.app.ui.theme.TextColor

/**
 * G1: Canonical branded text field for all input surfaces.
 * Wraps OutlinedTextFieldDefaults with FitCal brand styling, 56dp minimum touch target,
 * 12dp squircle corners (RadiusS), crisp 1.5dp focused border (no drop-shadow pop),
 * and optional external unit readout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitCalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Number,
    unit: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PrimaryAccent,
        unfocusedBorderColor = InputBorder,
        focusedLabelColor = PrimaryAccent,
        unfocusedLabelColor = MutedTextColor,
        cursorColor = PrimaryAccent,
        focusedTextColor = TextColor,
        unfocusedTextColor = TextColor,
        focusedContainerColor = CardBackground,
        unfocusedContainerColor = CardBackground
    )

    val field = @Composable { fieldModifier: Modifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextColor
            ),
            cursorBrush = SolidColor(PrimaryAccent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            modifier = fieldModifier
                .semantics(mergeDescendants = true) {}
                .padding(top = 8.dp)
                .defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight
                )
                .heightIn(min = 56.dp),
            decorationBox = @Composable { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    visualTransformation = visualTransformation,
                    innerTextField = innerTextField,
                    label = {
                        Text(
                            text = label,
                            style = BrandTypography.BodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    singleLine = true,
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = RoundedCornerShape(RadiusS),
                            focusedBorderThickness = 1.5.dp,
                            unfocusedBorderThickness = 1.dp
                        )
                    }
                )
            }
        )
    }

    if (unit != null) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            field(Modifier.weight(1f))
            Text(
                text = unit,
                style = BrandTypography.BodySmall,
                color = MutedTextColor,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        field(modifier)
    }
}

/**
 * Internal numeric field wrapper for compact pill inputs (G1 + G6).
 * Encapsulates BasicTextField to isolate raw text inputs within FitCalTextField.kt.
 */
@Composable
fun FitCalWeightField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextColor,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
