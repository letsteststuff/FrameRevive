package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class GoogleSessionCookiesTest {
    private fun line(domain: String = ".google.com", expiry: Long = 9999, value: String = "fixture") =
        "$domain\tTRUE\t/\tTRUE\t$expiry\tSID\t$value"
    @Test fun retainsOnlyRelevantGoogleCookies() {
        val result = GoogleSessionCookies.parse(listOf(line(), line("photos.fife.usercontent.google.com"), line(".youtube.com"), line(".google.com.evil.example")).joinToString("\n"), 1000)
        assertEquals(listOf(".google.com", "photos.fife.usercontent.google.com"), result.map { it.domain })
    }
    @Test fun supportsHttpOnlyAndSessionCookies() {
        assertEquals("SID", GoogleSessionCookies.parse("# Netscape HTTP Cookie File\n#HttpOnly_" + line(expiry = 0), 1000).single().name)
    }
    @Test fun duplicateCookieEntriesUseTheLastValueLikeABrowserCookieJar() {
        val cookies = GoogleSessionCookies.parse(line(value="old") + "\n" + line(value="new"),1000)
        assertEquals(1,cookies.size)
        assertEquals("new",cookies.single().value)
    }
    @Test fun expiredAndMalformedValuesAreDiscarded() {
        val text = listOf(line(expiry=900), line(value="bad;injection"), line(value="valid")).joinToString("\n")
        assertEquals("valid", GoogleSessionCookies.parse(text, 1000).single().value)
    }
    @Test fun jsonIsRejectedWithoutLeakingItsContents() {
        try { GoogleSessionCookies.parse("{\"secret\":\"fixture-secret\"}",1000); fail("Expected invalid format") }
        catch (e: IllegalArgumentException) { assertFalse(e.message!!.contains("fixture-secret")) }
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedExportIsRejected() {
        GoogleSessionCookies.parse("x".repeat(200001),1000)
    }
}
