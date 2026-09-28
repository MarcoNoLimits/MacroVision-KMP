package com.fitter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = BgColor,
    surface = CardBackground,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = TextColor,
    onSurface = TextColor
)

// ── F2.1 Brand typography scale ───────────────────────────────────────────────
// One canonical set of named styles for the whole app. Use these instead of
// hardcoding fontSize/fontWeight on every Text(). Material typography tokens
// (headlineSmall, bodyMedium, etc.) stay in use where they already appear;
// BrandTypography bridges the product-specific roles that Material doesn't name.
object BrandTypography {
    /** Section / category eyebrow labels: "BODY PARAMETERS", "WATER INTAKE", etc. */
    val Eyebrow = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )

    /** Card titles / section headers: "BODY PARAMETERS", "FREE DAILY ALLOWANCE", etc. (G4) */
    val CardTitle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )

    /** Three main screen titles converge here: Dashboard "Today", Review "Review Meal", Settings "Goals & Parameters". */
    val ScreenTitle = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )

    /** Card titles, item name emphasis rows. */
    val SectionTitle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )

    /** Standard body copy. */
    val Body = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal
    )

    /** Secondary / supporting body text. */
    val BodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    )

    /** Smallest permitted size for non-monospace text. */
    val Micro = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal
    )

    /** KPI numerals (calorie counts, macro totals). */
    val KpiNumeral = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    /** Diagnostics / error monospace log — minimum 11sp per spec. */
    val Diagnostics = TextStyle(
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
fun FitterTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
