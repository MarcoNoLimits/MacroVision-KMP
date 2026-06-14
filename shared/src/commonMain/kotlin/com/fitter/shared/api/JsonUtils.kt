package com.fitter.shared.api

internal fun cleanJson(raw: String): String {
    var cleaned = raw.trim()
    if (cleaned.startsWith("```")) {
        val lines = cleaned.lines()
        if (lines.isNotEmpty() && lines.first().startsWith("```")) {
            cleaned = lines.drop(1).joinToString("\n")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substringBeforeLast("```")
        }
    }
    return cleaned.trim()
}
