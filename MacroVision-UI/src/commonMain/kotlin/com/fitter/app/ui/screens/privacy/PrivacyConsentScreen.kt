package com.fitter.app.ui.screens.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.fitter.app.privacy.PrivacyConsent
import com.fitter.app.ui.theme.BgColor
import com.fitter.app.ui.theme.BorderColor
import com.fitter.app.ui.theme.CardBackground
import com.fitter.app.ui.theme.DangerBorder
import com.fitter.app.ui.theme.DangerSoft
import com.fitter.app.ui.theme.DangerTextStrong
import com.fitter.app.ui.theme.InfoPanel
import com.fitter.app.ui.theme.MutedTextColor
import com.fitter.app.ui.theme.PrimaryAccent
import com.fitter.app.ui.theme.SurfaceTint
import com.fitter.app.ui.theme.TextColor
import com.fitter.app.getCurrentTimeString

/**
 * First-launch privacy & consent gate.
 *
 * Legal rationale — this screen is a control, not decoration:
 * - Meal photos + nutrition logs are health-adjacent personal data (GDPR Art. 9),
 *   so processing requires explicit, affirmative consent that is freely given.
 * - Consent must be granular and withdrawable (Art. 4(11), Art. 7(3)).
 * - Bundled acceptance ("you must agree to use the app") is invalid consent.
 *   That's why "Essential only" is a first-class, equal-weight choice here.
 *
 * The app MUST NOT capture a photo, initialize an ad SDK, or contact the gateway
 * before this returns a decision.
 */
@Composable
fun PrivacyConsentScreen(
    onDecisionComplete: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
) {
    // Default OFF. Consent must be an affirmative act — never pre-checked.
    var acceptTerms by remember { mutableStateOf(false) }
    var personalizedAds by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Your data, your call",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextColor,
        )
        Text(
            text = "Fitter estimates nutrition from photos you take. Some of what we " +
                "process is sensitive, so here's exactly what happens and what you control.",
            fontSize = 14.sp,
            color = MutedTextColor,
            lineHeight = 20.sp,
        )

        InfoCard(
            title = "What we collect",
            body = "• Photos of meals you choose to photograph\n" +
                "• Nutrition estimates derived from them\n" +
                "• Your account email, if you create one\n" +
                "• Meal, water and weight entries you log\n\n" +
                "Images are processed by our server and forwarded to third-party AI " +
                "providers (OpenRouter, Google, Groq) to generate estimates.",
        )

        InfoCard(
            title = "Why it's sensitive",
            body = "Diet and nutrition information tied to an identifiable person can be " +
                "treated as health data, which privacy law classes as special-category. " +
                "That is why we ask before processing anything, and why you can withdraw " +
                "consent at any time.",
        )

        InfoCard(
            title = "Your rights",
            body = "• Withdraw consent for personalized ads — Settings → Privacy\n" +
                "• Delete your account and all associated data\n" +
                "• Request a copy of your data\n" +
                "• Object to processing at any time\n\n" +
                "Declining optional processing does not reduce core scanning features " +
                "beyond ad personalization.",
        )

        // Full documents must be one tap away from the consent decision — a link
        // people cannot reach is not informed consent.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextColor,
                    disabledContentColor = MutedTextColor,
                ),
            ) {
                Text("Read Privacy Policy", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onOpenTerms,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextColor,
                    disabledContentColor = MutedTextColor,
                ),
            ) {
                Text("Read Terms", fontSize = 13.sp)
            }
        }

        // ── Optional: personalized ads (off by default) ────────────────────────
        ConsentToggleRow(
            title = "Personalized ads",
            subtitle = "Lets our ad partners show ads based on your activity. " +
                "Turning this off still shows ads, just not targeted. " +
                "Your device will also ask you separately.",
            checked = personalizedAds,
            onCheckedChange = { personalizedAds = it },
            enabled = acceptTerms,
        )

        // ── Required: terms + privacy acceptance ───────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { acceptTerms = !acceptTerms }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = acceptTerms,
                onCheckedChange = { acceptTerms = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = PrimaryAccent,
                    uncheckedColor = BorderColor,
                ),
            )
            Text(
                text = "I accept the Terms of Service and Privacy Policy, and consent to " +
                    "Fitter processing my data (including meal photos) to provide nutrition " +
                    "estimates.",
                fontSize = 13.sp,
                color = TextColor,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 12.dp, end = 8.dp),
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Equal-weight actions: consent must not be bundled or coerced ────────
        Button(
            onClick = {
                PrivacyConsent.recordDecision(
                    acceptTerms = true,
                    adsPersonalized = personalizedAds,
                    timestampMs = System.currentTimeMillis(),
                )
                onDecisionComplete()
            },
            enabled = acceptTerms,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryAccent,
                disabledContainerColor = SurfaceTint,
            ),
        ) {
            Text(
                text = if (personalizedAds) "Accept all and continue" else "Accept and continue",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        OutlinedButton(
            onClick = {
                PrivacyConsent.recordDecision(
                    acceptTerms = true,
                    adsPersonalized = false,
                    timestampMs = System.currentTimeMillis(),
                )
                onDecisionComplete()
            },
            enabled = acceptTerms,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextColor,
                disabledContentColor = MutedTextColor,
            ),
        ) {
            Text("Continue without personalized ads", fontSize = 14.sp)
        }

        // ── Medical disclaimer: shown up-front, not buried in settings ──────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DangerSoft, RoundedCornerShape(12.dp))
                .border(1.dp, DangerBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Text(
                text = "Not medical advice",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DangerTextStrong,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Fitter provides automated estimates only. Values are approximate and " +
                    "not a substitute for a registered dietitian or doctor. Do not use Fitter to " +
                    "manage diabetes, eating disorders, allergies, pregnancy, or any medical " +
                    "condition. Speak to a healthcare professional before changing your diet.",
                fontSize = 12.sp,
                color = DangerTextStrong,
                lineHeight = 17.sp,
            )
        }

        Text(
            text = "Consent recorded ${getCurrentTimeString()}. " +
                "You can withdraw or delete everything from Settings → Privacy.",
            fontSize = 11.sp,
            color = MutedTextColor,
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextColor,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            fontSize = 12.sp,
            color = MutedTextColor,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ConsentToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (enabled) InfoPanel else SurfaceTint,
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextColor,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MutedTextColor,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CardBackground,
                checkedTrackColor = PrimaryAccent,
                uncheckedThumbColor = CardBackground,
                uncheckedTrackColor = BorderColor,
                uncheckedBorderColor = BorderColor,
            ),
        )
    }
}
