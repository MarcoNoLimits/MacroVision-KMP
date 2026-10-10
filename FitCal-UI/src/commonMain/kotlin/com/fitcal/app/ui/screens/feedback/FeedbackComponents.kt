package com.fitcal.app.ui.screens.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitcal.app.ui.theme.*
import com.fitcal.shared.api.FeedbackSendResult
import kotlinx.coroutines.launch

/** Reasons offered after a thumbs-down. Stored as these ids, never as free text. */
val SCAN_COMPLAINT_REASONS = listOf(
    "wrong_food" to "Wrong food",
    "portion_off" to "Portion size off",
    "missed_item" to "Missed an item",
    "nutrition_off" to "Calories look off",
)

/**
 * "Is this estimate right?" row on the result screen. A thumbs-up is sent at once;
 * a thumbs-down first asks which part was wrong (optional, chips only).
 */
@Composable
fun ScanAccuracyPrompt(
    onRate: (positive: Boolean, reasons: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf("ask") } // ask | reasons | done
    val selected = remember { mutableStateListOf<String>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(RadiusL))
            .border(1.dp, BorderColor, RoundedCornerShape(RadiusL))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state) {
            "ask" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Is this estimate right?",
                    style = BrandTypography.Body,
                    color = TextColor,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        onRate(true, emptyList())
                        state = "done"
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = "Yes, looks right", tint = PrimaryAccent)
                }
                IconButton(onClick = { state = "reasons" }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.ThumbUp,
                        contentDescription = "No, something's wrong",
                        tint = MutedTextColor,
                        modifier = Modifier.rotate(180f),
                    )
                }
            }
            "reasons" -> {
                Text("What was off? (optional)", style = BrandTypography.Body, color = TextColor)
                SCAN_COMPLAINT_REASONS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (id, label) ->
                            FilterChip(
                                selected = id in selected,
                                onClick = { if (id in selected) selected.remove(id) else selected.add(id) },
                                label = { Text(label, style = BrandTypography.BodySmall) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        onRate(false, selected.toList())
                        state = "done"
                    },
                    shape = RoundedCornerShape(RadiusM),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) {
                    Text("Send", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> Text(
                "Thanks! This helps us improve scans. You can still fix anything above before logging.",
                style = BrandTypography.BodySmall,
                color = MutedTextColor,
            )
        }
    }
}

/** Settings → Send feedback. Free text plus an optional reply address. */
@Composable
fun FeedbackDialog(
    defaultEmail: String?,
    onSend: suspend (kind: String, message: String, email: String?) -> FeedbackSendResult,
    onDismiss: () -> Unit,
) {
    val kinds = listOf("bug" to "Problem", "idea" to "Idea", "other" to "Other")
    var kind by remember { mutableStateOf("bug") }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(defaultEmail.orEmpty()) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FeedbackSendResult?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = CardBackground,
        title = {
            Text(
                if (result == FeedbackSendResult.Sent) "Thank you!" else "Send feedback",
                style = BrandTypography.SectionTitle,
                color = TextColor,
            )
        },
        text = {
            if (result == FeedbackSendResult.Sent) {
                Text(
                    "We read every message. If you left an email, we may reply there.",
                    style = BrandTypography.Body,
                    color = TextColor,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        kinds.forEach { (id, label) ->
                            FilterChip(
                                selected = kind == id,
                                onClick = { kind = id },
                                label = { Text(label, style = BrandTypography.BodySmall) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { if (it.length <= 2000) message = it },
                        label = { Text(if (kind == "bug") "What went wrong?" else "Tell us more") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("Email for a reply (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "We only receive what you type here, never your photos or meal log. Please leave out health details.",
                        style = BrandTypography.BodySmall,
                        color = MutedTextColor,
                    )
                    when (result) {
                        FeedbackSendResult.RateLimited -> Text(
                            "You've sent a lot of feedback today. Please try again tomorrow.",
                            style = BrandTypography.BodySmall,
                            color = DangerColor,
                        )
                        FeedbackSendResult.Failed -> Text(
                            "Couldn't send. Check your connection and try again.",
                            style = BrandTypography.BodySmall,
                            color = DangerColor,
                        )
                        else -> Unit
                    }
                }
            }
        },
        confirmButton = {
            if (result == FeedbackSendResult.Sent) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    shape = RoundedCornerShape(RadiusM),
                ) { Text("Done", color = Color.White) }
            } else {
                Button(
                    onClick = {
                        sending = true
                        scope.launch {
                            result = onSend(kind, message.trim(), email.ifBlank { null })
                            sending = false
                        }
                    },
                    enabled = message.isNotBlank() && !sending,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    shape = RoundedCornerShape(RadiusM),
                ) { Text(if (sending) "Sending…" else "Send", color = Color.White) }
            }
        },
        dismissButton = {
            if (result != FeedbackSendResult.Sent) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !sending,
                    shape = RoundedCornerShape(RadiusM),
                    border = BorderStroke(1.dp, BorderColor),
                ) { Text("Cancel", color = TextColor) }
            }
        },
    )
}
