package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class FrameWebTest {
    @Test fun ownerTextCannotBecomeMarkup() {
        val payload = "<script>alert('private')</script>"
        val page = FrameWeb.render(FrameWeb.State(mapOf("title" to payload, "albumId" to payload,
            "syncError" to payload, "diagnostics" to payload), emptySet(), listOf(FrameWeb.Album(payload,payload))))
        assertFalse(page.contains(payload))
        assertTrue(page.contains("&lt;script&gt;"))
        assertTrue(page.contains("https://github.com/letsteststuff/FrameRevive/releases"))
    }
    @Test fun diagnosticsClassifyErrorsWithoutExportingCredentials() {
        val secret = "https://private.example/?key=secret-cookie"
        assertEquals("HTTP 403", FrameDiagnostics.errorSummary("HTTP 403 $secret"))
        assertEquals("Error recorded (see private controls page)", FrameDiagnostics.errorSummary(secret))
        assertEquals("Timeout", FrameDiagnostics.errorSummary("Request timed out $secret"))
        assertEquals("None", FrameDiagnostics.errorSummary(null))
    }
}
