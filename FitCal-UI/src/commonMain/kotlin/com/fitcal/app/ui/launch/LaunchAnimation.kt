package com.fitcal.app.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.fitcal.app.ui.theme.CarbsColor
import com.fitcal.app.ui.theme.FatColor
import com.fitcal.app.ui.theme.PrimaryAccent
import com.fitcal.app.ui.theme.ProteinColor
import com.fitcal.app.ui.theme.REDUCED_MOTION_ENABLED
import com.fitcal.app.ui.theme.TextColor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Size the mark is drawn at on the Android 12+ system splash (icon without
// background: 288dp box, 108-unit viewport). Starting here makes the hand-off
// from the system splash to this overlay frame-identical on most devices.
private val SplashMarkBox = 288.dp

// Macro F geometry in the 108-unit launcher viewport; mirrors
// res/drawable/ic_launcher_foreground.xml.
private const val STEM_X = 34f
private const val TOP_Y = 28f
private const val BAR = 13f
private const val STEM_H = 52f
private const val TOP_W = 42f
private const val MID_Y = 48f
private const val MID_W = 30f
private const val DOT_X = 72f
private const val DOT_Y = 74f
private const val DOT_R = 6f

/**
 * Cold-start transition from the launcher icon into the app.
 *
 * Draws [content] underneath a slate-900 overlay holding the macro F, plays the
 * "macros refill" beat (arms retract into the stem, spring back out, protein dot
 * pops), then lifts the mark and fades the overlay while the app settles from
 * 1.04x to 1x. Plays once per launch (survives rotation via rememberSaveable)
 * and is skipped entirely when [REDUCED_MOTION_ENABLED] is set.
 */
@Composable
fun LaunchAnimationHost(content: @Composable () -> Unit) {
    var finished by rememberSaveable { mutableStateOf(REDUCED_MOTION_ENABLED) }

    val topArm = remember { Animatable(1f) }     // fraction of TOP_W beyond the stem
    val midArm = remember { Animatable(1f) }     // fraction of MID_W beyond the stem
    val dot = remember { Animatable(1f) }        // dot scale
    val markLift = remember { Animatable(0f) }   // 0 = resting, 1 = gone
    val overlay = remember { Animatable(1f) }    // overlay alpha
    val contentScale = remember { Animatable(if (finished) 1f else 1.04f) }

    if (!finished) {
        LaunchedEffect(Unit) {
            delay(120) // hold the splash frame so the hand-off reads as one image

            // Retract: arms slide into the stem, dot shrinks away.
            coroutineScope {
                val retract = tween<Float>(160, easing = FastOutSlowInEasing)
                launch { topArm.animateTo(0f, retract) }
                launch { midArm.animateTo(0f, retract) }
                launch { dot.animateTo(0f, retract) }
            }

            // Refill: arms spring back out, staggered, then the protein dot pops.
            coroutineScope {
                val refill = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
                launch { topArm.animateTo(1f, refill) }
                launch { delay(70); midArm.animateTo(1f, refill) }
                launch { delay(160); dot.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
            }

            delay(80)

            // Exit: mark lifts and fades, overlay fades, app settles into place.
            coroutineScope {
                launch { markLift.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
                launch { delay(60); overlay.animateTo(0f, tween(340, easing = FastOutSlowInEasing)) }
                launch { delay(60); contentScale.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) }
            }
            finished = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                }
        ) {
            content()
        }

        if (!finished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = overlay.value }
                    .background(TextColor),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(SplashMarkBox)
                        .graphicsLayer {
                            val lift = markLift.value
                            alpha = 1f - lift
                            scaleX = 1f - 0.15f * lift
                            scaleY = 1f - 0.15f * lift
                            translationY = -48.dp.toPx() * lift
                        }
                ) {
                    drawMacroF(topArm.value, midArm.value, dot.value)
                }
            }
        }
    }
}

/** The macro F, drawn in the 108-unit launcher viewport scaled to this canvas. */
private fun DrawScope.drawMacroF(topArm: Float, midArm: Float, dotScale: Float) {
    val u = size.minDimension / 108f
    val r = CornerRadius(BAR / 2f * u)

    fun bar(color: Color, x: Float, y: Float, w: Float, h: Float) = drawRoundRect(
        color = color,
        topLeft = Offset(x * u, y * u),
        size = Size(w * u, h * u),
        cornerRadius = r
    )

    // Same paint order as ic_launcher_foreground.xml: stem, top arm, middle arm
    // (over the stem), then the stem cap over the top joint. Arms grow from stem
    // width (BAR) to their full length.
    bar(PrimaryAccent, STEM_X, TOP_Y, BAR, STEM_H)
    bar(CarbsColor, STEM_X, TOP_Y, BAR + (TOP_W - BAR) * topArm.coerceAtLeast(0f), BAR)
    bar(FatColor, STEM_X, MID_Y, BAR + (MID_W - BAR) * midArm.coerceAtLeast(0f), BAR)
    bar(PrimaryAccent, STEM_X, TOP_Y, BAR, BAR)

    if (dotScale > 0f) {
        drawCircle(
            color = ProteinColor,
            radius = DOT_R * u * dotScale,
            center = Offset(DOT_X * u, DOT_Y * u)
        )
    }
}
