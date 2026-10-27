package com.framerevive.app

/** Export categories, never raw Google errors that can contain private URLs or credentials. */
object FrameDiagnostics {
    fun errorSummary(error: String?): String {
        if (error.isNullOrBlank()) return "None"
        Regex("HTTP\\s+([1-5][0-9]{2})", RegexOption.IGNORE_CASE).find(error)?.let { return "HTTP ${it.groupValues[1]}" }
        val lower = error.lowercase()
        return when {
            "timed out" in lower || "timeout" in lower || "respond in time" in lower -> "Timeout"
            "network" in lower || "could not be reached" in lower -> "Network unavailable"
            "session" in lower || "sign-in" in lower || "sign in" in lower -> "Session needs attention"
            "decode" in lower || "image" in lower -> "Image unavailable"
            else -> "Error recorded (see private controls page)"
        }
    }
}
