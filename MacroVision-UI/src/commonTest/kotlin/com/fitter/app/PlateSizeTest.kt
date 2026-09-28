package com.fitter.app

import com.fitter.app.ui.components.plateSizeCmToInches
import com.fitter.app.ui.components.plateSizeInchesToCm
import com.fitter.app.ui.components.plateSizeInchesToCmString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G2 acceptance test: verifies accurate conversion between plate size in inches (storage/API)
 * and centimeters (UI presentation).
 */
class PlateSizeTest {

    @Test
    fun testPlateSize_roundTripFromInches() {
        val originalInches = 9.0f
        val cm = plateSizeInchesToCm(originalInches)
        assertEquals(22.86f, cm, 0.001f, "9.0 inches should convert to 22.86 cm")

        val backToInches = plateSizeCmToInches(cm)
        assertTrue(
            abs(backToInches - originalInches) < 0.01f,
            "Round trip from 9.0 inches should return to 9.0 inches within 0.01, got $backToInches"
        )
    }

    @Test
    fun testPlateSize_roundTripFromCm() {
        val originalCm = 23.0f
        val inches = plateSizeCmToInches(originalCm)
        assertTrue(
            abs(inches - 9.055f) < 0.01f,
            "23.0 cm should convert to approximately 9.055 inches, got $inches"
        )

        val backToCm = plateSizeInchesToCm(inches)
        assertTrue(
            abs(backToCm - originalCm) < 0.01f,
            "Round trip from 23.0 cm should return to 23.0 cm within 0.01, got $backToCm"
        )
    }

    @Test
    fun testPlateSize_displayFormatting() {
        val formatted9Inches = plateSizeInchesToCmString(9.0f)
        assertEquals("22.9", formatted9Inches, "9.0 inches should format to 22.9 cm")

        val formattedDefaultPlate = plateSizeInchesToCmString(9.055118f)
        assertEquals("23.0", formattedDefaultPlate, "9.055 inches should format to 23.0 cm")
    }
}
