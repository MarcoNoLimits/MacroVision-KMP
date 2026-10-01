package com.fitter.app.ui.screens.privacy

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitter.app.ui.theme.BgColor
import com.fitter.app.ui.theme.CardBackground
import com.fitter.app.ui.theme.MutedTextColor
import com.fitter.app.ui.theme.SurfaceTint
import com.fitter.app.ui.theme.TextColor

/**
 * In-app Privacy Policy and Terms of Service.
 *
 * WHY THIS EXISTS (not cosmetic):
 * - Google AdMob **rejected the publisher application** largely on policy grounds.
 *   Both Google Play and the App Store require a publicly reachable privacy policy
 *   URL, and reviewers check for in-app disclosure of data collection and consent.
 * - GDPR Arts. 12–14 require the notice to be given in a concise, intelligible form.
 *   Burying it on a website the user never visits is not sufficient.
 *
 * This screen is the canonical, always-available version of the notice. Keep the
 * hosted URL and this text in sync — if they diverge, that is itself a compliance
 * failure.
 *
 * @param onClose invoked by the back affordance.
 */
@Composable
fun PrivacyPolicyScreen(
    onClose: () -> Unit,
    showTermsFirst: Boolean = false,
) {
    val scroll = rememberScrollState()

    Surface(color = BgColor, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PolicyHeader(title = if (showTermsFirst) "Terms of Service" else "Privacy Policy", onClose = onClose)

            if (showTermsFirst) TermsContent() else PrivacyPolicyContent()

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Questions about this document? Contact us at privacy@fitter.app",
                fontSize = 12.sp,
                color = MutedTextColor,
            )
            Text(
                text = "Last updated: February 2026",
                fontSize = 12.sp,
                color = MutedTextColor,
            )
        }
    }
}

@Composable
private fun PolicyHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "✕",
            fontSize = 18.sp,
            color = MutedTextColor,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceTint, RoundedCornerShape(12.dp))
                .clickable(onClick = onClose)
                .wrapContentSize(Alignment.Center),
        )
    }
}

@Composable
private fun PrivacyPolicyContent() {
    PolicySection("What Fitter is") {
        "Fitter is a mobile application that estimates the nutritional content of a meal " +
            "from a photograph you take. It also tracks water intake, weight, and logged " +
            "meals. It is an informational tool only and is not a medical device."
    }

    PolicySection("What we collect") {
        "• Meal photographs you choose to capture\n" +
            "• Nutrition estimates generated from those photographs\n" +
            "• Account email address, if you choose to create an account\n" +
            "• Water intake, weight, and meal log entries you record\n" +
            "• Technical data: device identifiers, crash logs, and crash diagnostics\n" +
            "• Advertising identifiers and ad interaction events (see Advertising below)"
    }

    PolicySection("Why this is sensitive information") {
        "Dietary and nutrition information linked to an identifiable person can be " +
            "classified as health data, which privacy law treats as a special category " +
            "requiring your explicit consent. That is why Fitter asks before processing " +
            "anything and never processes data before you accept."
    }

    PolicySection("Legal bases for processing (GDPR)") {
        "• **Consent** (Art. 6(1)(a)) — meal photo analysis and personalized advertising\n" +
            "• **Contract** (Art. 6(1)(b)) — providing the app's core functionality\n" +
            "• **Legitimate interests** (Art. 6(1)(f)) — crash diagnostics and security, " +
            "balanced against your rights\n\n" +
            "For special-category processing we rely on your explicit consent (Art. 9(2)(a))."
    }

    PolicySection("Who processes your data") {
        "Our servers run on Cloudflare. Photographs are forwarded to third-party AI " +
            "providers — OpenRouter, Google, and Groq — solely to generate estimates. " +
            "Account data is stored in Supabase. Payments are handled by RevenueCat and " +
            "the relevant app store; we never see your full card details."
    }

    PolicySection("International transfers") {
        "Some providers process data outside the European Economic Area. Where that " +
            "occurs, transfers are covered by Standard Contractual Clauses and a transfer " +
            "impact assessment."
    }

    PolicySection("Advertising") {
        "Fitter is supported by advertising. We use Google AdMob and, where enabled, " +
            "AppLovin MAX.\n\n" +
            "**Personalized ads are OFF by default.** If you opt in — and separately " +
            "approve the device-level prompt — our ad partners may use your activity to " +
            "show more relevant ads. You can turn personalized ads off at any time in " +
            "Settings → Privacy without losing any core feature.\n\n" +
            "European users are shown a consent platform before any ad request is made."
    }

    PolicySection("How long we keep data") {
        "Meal photographs and nutrition logs are retained until you delete them or " +
            "close your account. Cached analysis results are retained for up to 30 days. " +
            "Crash and diagnostic logs are retained for up to 90 days."
    }

    PolicySection("Your rights") {
        "• Withdraw consent at any time (Settings → Privacy)\n" +
            "• Delete your account and all associated data\n" +
            "• Request a copy of the data we hold about you\n" +
            "• Correct inaccurate information\n" +
            "• Object to processing, and restrict it\n" +
            "• Lodge a complaint with your supervisory authority"
    }

    PolicySection("Children") {
        "Fitter is not directed at children under 13 (or the minimum age of digital " +
            "consent in your jurisdiction). We do not knowingly collect data from " +
            "children. If you believe a child has provided us data, contact us and we " +
            "will delete it."
    }

    PolicySection("Security") {
        "Data is encrypted in transit using TLS. All access is authenticated with " +
            "short-lived tokens. Ad SDKs are initialized only after consent is resolved, " +
            "and cloud backups of app data are disabled."
    }

    PolicySection("Your choices") {
        "Declining personalized ads leaves every core feature of Fitter fully available. " +
            "You may use the app as a guest without creating an account."
    }
}

@Composable
private fun TermsContent() {
    PolicySection("Acceptance of terms") {
        "By using Fitter you agree to these terms. If you do not agree, do not use the " +
            "app. These terms were last updated in February 2026."
    }

    PolicySection("Fitter is not medical advice") {
        "Fitter provides **automated estimates only**. Portion sizes inferred from a " +
            "photograph are approximate and may be significantly wrong. Nothing in Fitter " +
            "is medical advice, diagnosis, or treatment.\n\n" +
            "Do not use Fitter to manage diabetes, eating disorders, allergies, pregnancy, " +
            "or any medical condition. Consult a registered dietitian or physician before " +
            "changing your diet, especially if you have a health condition, are pregnant " +
            "or breastfeeding, or are taking medication."
    }

    PolicySection("Subscriptions") {
        "Fitter Premium is available as a monthly or annual subscription, billed through " +
            "the app store you purchased from. Prices are shown in your store's currency.\n\n" +
            "Subscriptions renew automatically unless cancelled at least 24 hours before " +
            "the end of the current period. You can cancel in your store's subscription " +
            "settings; refunds are handled by the store under its own policy."
    }

    PolicySection("Acceptable use") {
        "You agree not to:\n" +
            "• Upload photographs of other people without their consent\n" +
            "• Reverse engineer, resell, or redistribute the app\n" +
            "• Use the app to harass, harm, or defraud anyone\n" +
            "• Attempt to circumvent quota limits or premium access"
    }

    PolicySection("Content and results") {
        "Analysis results are generated by automated machine-learning models and may " +
            "contain errors. Fitter's food database is approximate. You remain responsible " +
            "for the decisions you make based on the information provided."
    }

    PolicySection("Third-party services") {
        "Fitter relies on third-party services including Cloudflare, Supabase, OpenRouter, " +
            "Google, Groq, AppLovin, and RevenueCat. Their terms also apply to you in " +
            "relation to their services."
    }

    PolicySection("Limitation of liability") {
        "To the maximum extent permitted by law, Fitter is provided \"as is\" without " +
            "warranty of any kind. We are not liable for indirect or consequential " +
            "losses arising from use of the app, including dietary decisions made in " +
            "reliance on estimated values. Nothing here excludes liability that cannot " +
            "lawfully be excluded."
    }

    PolicySection("Changes to these terms") {
        "We may update these terms. Material changes will be announced in the app " +
            "before they take effect."
    }
}

@Composable
private fun PolicySection(title: String, body: () -> String) {
    PolicySection(title = title, body = body())
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
        Text(text = body, fontSize = 13.sp, color = MutedTextColor, lineHeight = 19.sp)
    }
}