package com.fitter.app.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-agnostic Composable for rendering an AdMob banner.
 */
@Composable
expect fun AdBanner(modifier: Modifier = Modifier)
