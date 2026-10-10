package com.fitcal.app.ui.screens.monetization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitcal.app.ads.AdManager
import com.fitcal.app.ads.ScanQuotaManager
import com.fitcal.app.telemetry.Analytics
import com.fitcal.app.ui.theme.*

// ADS & PERKS — the quarantine home for every monetization surface.
// Deliberately NOT inside Settings: monetization is core product behavior,
// not a preference. There is intentionally NO opt-out switch for free tier.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonetizationScreen(
    adManager: AdManager,
    scansRemainingToday: Int,
    currentDateKey: String,
    onScansUpdated: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header Row (mirrors SettingsScreen)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, BorderColor, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Ads & Perks",
                style = BrandTypography.ScreenTitle,
                color = TextColor
            )
        }

        // Section 1: Daily Quota
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(RadiusL),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(RadiusL))
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "FREE DAILY ALLOWANCE",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AI Scans Available",
                            style = BrandTypography.SectionTitle,
                            color = TextColor
                        )
                        Text(
                            text = "Free daily allowance: ${ScanQuotaManager.getDailyFreeLimit()} scans",
                            style = BrandTypography.BodySmall,
                            color = MutedTextColor
                        )
                    }
                    Text(
                        text = "$scansRemainingToday left",
                        style = BrandTypography.SectionTitle,
                        color = PrimaryAccent
                    )
                }
            }
        }

        // Section 2: Get More Scans (rewarded video / interstitial)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(RadiusL),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(RadiusL))
                .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "GET MORE SCANS",
                    style = BrandTypography.CardTitle,
                    color = MutedTextColor
                )
                Text(
                    text = "Hit your daily limit? Watch a short sponsored video to earn 2 bonus scans instantly. No payment needed — ads keep FitCal free.",
                    style = BrandTypography.BodySmall,
                    color = TextColor
                )

                OutlinedButton(
                    onClick = {
                        Analytics.track("rewarded_ad_requested", "source" to "perks_screen")
                        adManager.showRewardedScanUnlockAd(
                            onRewarded = {
                                Analytics.track("rewarded_ad_earned", "source" to "perks_screen")
                                ScanQuotaManager.addBonusScans(currentDateKey, 2)
                                onScansUpdated()
                            },
                            onDismissed = {
                                onScansUpdated()
                            }
                        )
                    },
                    shape = RoundedCornerShape(RadiusM),
                    border = BorderStroke(1.dp, PrimaryAccent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Watch Rewarded Ad (+2 Scans)",
                        color = PrimaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}