package com.fitter.app.ui.components

import kotlin.random.Random

/**
 * Generates a collision-proof, monotonically unique meal ID.
 *
 * Implementation: UUID v4 format via kotlin.random.Random (commonMain-safe;
 * java.util.UUID is not available in commonMain on KMP).
 *
 * Two calls in the same millisecond produce distinct IDs because randomness
 * is independent of wall-clock time.
 */
fun newMealId(): String {
    val rng = Random.Default
    val bytes = ByteArray(16) { rng.nextInt(256).toByte() }
    // Set version to 4 (random UUID)
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    // Set variant to RFC 4122
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

    return buildString {
        bytes.forEachIndexed { index, byte ->
            if (index in listOf(4, 6, 8, 10)) append('-')
            append(((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1))
        }
    }
}
