package com.fitter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = BgColor,
    surface = CardBackground,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = TextColor,
    onSurface = TextColor
)

@Composable
fun FitterTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
