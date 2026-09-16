package com.fitter.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.fitter.app.ui.theme.REDUCED_MOTION_ENABLED
import kotlinx.coroutines.launch

/**
 * F3.1 — Reusable press-feedback wrapper.
 *
 * Wraps [content] in a [Box] that scales to [pressScale] on pointer-down using
 * a spring animation and fires [onTap] on pointer-up/release. Respects the
 * [REDUCED_MOTION_ENABLED] compile-time gate: when true, the tap fires instantly
 * with no visual scale change.
 *
 * Substitution note: Compose Multiplatform doesn't expose a stable
 * `PressInteraction`-based Indication that supports both iOS and Android equally
 * in all versions; `detectTapGestures` via `pointerInput` is the closest
 * KMP-safe primitive that gives reliable press/release events with cross-platform
 * behaviour. A `MutableInteractionSource` + `Indication` approach would require
 * platform-specific ripple implementations. The spring parameters (damping = 1.0,
 * stiffness ≈ (1/0.2s)² ≈ 25) approximate the response spec of ~0.2 s.
 *
 * @param onTap      Called when the user lifts their finger (tap confirmed).
 * @param modifier   Additional modifiers applied to the outer Box.
 * @param pressScale Scale factor during press (default 0.97f per spec).
 * @param content    The composable content inside the pressable region.
 */
@Composable
fun PressableBox(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    pressScale: Float = 0.97f,
    content: @Composable BoxScope.() -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .pointerInput(onTap) {
                detectTapGestures(
                    onPress = { _ ->
                        if (!REDUCED_MOTION_ENABLED) {
                            scope.launch {
                                scale.animateTo(
                                    targetValue = pressScale,
                                    animationSpec = spring(
                                        dampingRatio = 1.0f,   // critically damped — no bounce
                                        stiffness = 400f        // ~0.2 s effective response
                                    )
                                )
                            }
                        }
                        val released = tryAwaitRelease()
                        if (!REDUCED_MOTION_ENABLED) {
                            scope.launch {
                                scale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 1.0f,
                                        stiffness = 400f
                                    )
                                )
                            }
                        }
                        if (released) {
                            onTap()
                        }
                    }
                )
            },
        content = content
    )
}
