package com.fitter.app.ui.theme

import androidx.compose.ui.unit.dp

// ── F1.2 Corner-radius design tokens ─────────────────────────────────────────
// Five canonical radii; all RoundedCornerShape() calls outside ui/theme/ must
// reference one of these. Intermediate values (20dp, 28dp) are collapsed into
// the nearest token.
val RadiusXS = 8.dp   // chips, pills, tiny surfaces, viewfinder brackets
val RadiusS  = 12.dp  // rows, inputs, small cards
val RadiusM  = 16.dp  // buttons, macro cards, standard rows
val RadiusL  = 24.dp  // card containers, dialogs, water card
val RadiusXL = 32.dp  // hero card only; ReviewScreen image card top corners
