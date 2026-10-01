package com.fitcal.app.ui.components

import kotlin.math.abs
import kotlin.math.round

// G2: stored in inches; VLM API expects inches (analyzeMealImage)
const val CM_PER_INCH = 2.54f

/**
 * Converts plate diameter from inches (API/storage model) to centimeters (UI display).
 */
fun plateSizeInchesToCm(inches: Float): Float {
    return inches * CM_PER_INCH
}

/**
 * Converts plate diameter from centimeters (UI display) to inches (API/storage model).
 */
fun plateSizeCmToInches(cm: Float): Float {
    return cm / CM_PER_INCH
}

/**
 * Formats plate size in centimeters with one decimal place for user-facing display.
 * Avoids platform-specific String.format so it works identically across all KMP targets.
 */
fun plateSizeInchesToCmString(inches: Float): String {
    val cm = plateSizeInchesToCm(inches)
    val rounded = round(cm * 10f).toInt()
    val intPart = rounded / 10
    val fracPart = abs(rounded % 10)
    return "$intPart.$fracPart"
}
