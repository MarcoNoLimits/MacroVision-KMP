package com.fitcal.app.ui.screens.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcal.app.privacy.AgeGate
import com.fitcal.app.ui.theme.BgColor
import com.fitcal.app.ui.theme.BorderColor
import com.fitcal.app.ui.theme.DangerBorder
import com.fitcal.app.ui.theme.DangerSoft
import com.fitcal.app.ui.theme.DangerTextStrong
import com.fitcal.app.ui.theme.MutedTextColor
import com.fitcal.app.ui.theme.PrimaryAccent
import com.fitcal.app.ui.theme.TextColor

/**
 * Age + eating-disorder attestation, shown after consent and before the app opens.
 *
 * Store context:
 * - Play **Families policy**: FitCal is declared 18+ only. The gate makes that
 *   declaration truthful rather than an unchecked box in a store listing.
 * - AdMob: health/nutrition apps are treated as adult content; a visible age gate
 *   plus an eating-disorder notice is the standard mitigation.
 * - Regulators have fined calorie-tracking apps for encouraging disordered eating.
 *
 * The two checkboxes are separate on purpose: someone may be 18+ and still want the
 * safety notice acknowledged, and bundling them would recreate the consent-bundling
 * problem the privacy screen was built to avoid.
 */
@Composable
fun AgeGateScreen(
    onComplete: (disorderCautionAccepted: Boolean) -> Unit,
) {
    var isAdult by remember { mutableStateOf(false) }
    var cautionAccepted by remember { mutableStateOf(false) }

    val canContinue = isAdult && cautionAccepted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Before you continue",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextColor,
        )
        Text(
            text = "FitCal is intended for adults. It produces calorie and macro estimates, " +
                "which are not appropriate for everyone.",
            fontSize = 14.sp,
            color = MutedTextColor,
            lineHeight = 20.sp,
        )

        // ── Requirement 1: 18+ ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isAdult) DangerSoft.copy(alpha = 0.4f) else BgColor,
                    RoundedCornerShape(12.dp),
                )
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                .clickable { isAdult = !isAdult }
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = isAdult,
                onCheckedChange = { isAdult = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = PrimaryAccent,
                    uncheckedColor = BorderColor,
                ),
            )
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    text = "I am 18 years of age or older",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextColor,
                )
                Text(
                    text = "FitCal is not directed at children or teens. If you are under 18, " +
                        "please stop here and consult a healthcare professional instead.",
                    fontSize = 12.sp,
                    color = MutedTextColor,
                    lineHeight = 17.sp,
                )
            }
        }

        // ── Requirement 2: eating-disorder safety ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (cautionAccepted) DangerSoft.copy(alpha = 0.4f) else BgColor,
                    RoundedCornerShape(12.dp),
                )
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                .clickable { cautionAccepted = !cautionAccepted }
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = cautionAccepted,
                onCheckedChange = { cautionAccepted = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = PrimaryAccent,
                    uncheckedColor = BorderColor,
                ),
            )
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    text = "I understand the calorie-risk warning",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextColor,
                )
                Text(
                    text = "If you have or are recovering from an eating disorder, or if " +
                        "calorie tracking has affected your wellbeing, please stop using FitCal " +
                        "and speak to a healthcare professional.",
                    fontSize = 12.sp,
                    color = MutedTextColor,
                    lineHeight = 17.sp,
                )
            }
        }

        // ── Resource signposting (both stores expect this) ───────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DangerSoft, RoundedCornerShape(12.dp))
                .border(1.dp, DangerBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Need help?",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DangerTextStrong,
            )
            Text(
                text = "FitCal is not a substitute for professional care. If you are struggling " +
                    "with your relationship to food, contact your national eating-disorder or " +
                    "mental-health helpline, or speak to a doctor or registered dietitian.",
                fontSize = 12.sp,
                color = DangerTextStrong,
                lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { onComplete(cautionAccepted) },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryAccent,
                disabledContainerColor = BorderColor,
            ),
        ) {
            Text(
                text = "Continue",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (canContinue) TextColor else MutedTextColor,
            )
        }

        if (!isAdult) {
            Text(
                text = "FitCal cannot be used by anyone under 18. This is a store-policy " +
                    "requirement and a safety limit.",
                fontSize = 12.sp,
                color = DangerTextStrong,
                lineHeight = 17.sp,
            )
        }
    }
}